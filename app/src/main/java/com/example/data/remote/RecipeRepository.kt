package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.RecipeDao
import com.example.data.local.RecipeEntity
import com.example.data.local.SimmerDatabase
import com.example.data.model.Recipe
import com.example.data.model.RecipeSourceType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class RecipeStreamPayload(
    val recipes: List<Recipe>,
    val isFromCache: Boolean,
    val hasPendingWrites: Boolean
)

object FirestoreProvider {
    @Volatile
    private var configured = false

    fun getInstance(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        if (!configured) {
            synchronized(this) {
                if (!configured) {
                    try {
                        db.firestoreSettings = firestoreSettings {
                            setLocalCacheSettings(
                                PersistentCacheSettings.newBuilder()
                                    .setSizeBytes(100L * 1024L * 1024L)
                                    .build()
                            )
                        }
                    } catch (e: IllegalStateException) {
                        Log.d("FirestoreProvider", "Firestore settings already initialized: ${e.message}")
                    }
                    configured = true
                }
            }
        }
        return db
    }
}

class RecipeRepository(
    private val db: FirebaseFirestore,
    private val recipeDao: RecipeDao? = null,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirestoreProvider.getInstance(context),
        recipeDao = SimmerDatabase.getInstance(context).recipeDao()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun recipesCollection(userId: String) =
        db.collection("users").document(userId).collection("recipes")

    fun observeRecipes(userId: String): Flow<RecipeStreamPayload> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            throw IllegalStateException("Authenticated user required to observe recipes.")
        }

        // Emit local Room SQLite cache immediately when available for fast offline viewing
        val cached = recipeDao?.let { dao ->
            withContext(Dispatchers.IO) {
                dao.getRecipesForUserOnce(userId).map { it.toDomain() }
            }
        }.orEmpty()
        if (cached.isNotEmpty()) {
            emit(
                RecipeStreamPayload(
                    recipes = cached,
                    isFromCache = true,
                    hasPendingWrites = false
                )
            )
        }

        val path = "users/$userId/recipes"
        emitAll(
            recipesCollection(userId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val recipes = snapshot.toObjects(
                        Recipe::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    recipeDao?.let { dao ->
                        withContext(Dispatchers.IO) {
                            val entities = recipes
                                .filter { it.id.isNotBlank() }
                                .map { RecipeEntity.fromDomain(it, userId) }
                            dao.replaceUserRecipes(userId, entities)
                        }
                    }
                    RecipeStreamPayload(
                        recipes = recipes,
                        isFromCache = snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites()
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getRecipe(userId: String, recipeId: String): Recipe? {
        if (userId.isBlank() || recipeId.isBlank()) return null
        val path = "users/$userId/recipes/$recipeId"
        return try {
            recipesCollection(userId)
                .document(recipeId)
                .get()
                .await()
                .toObject(Recipe::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun createRecipe(recipe: Recipe, isOnline: Boolean): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recipes"
        return try {
            val uid = requireUserId()
            val validated = validateRecipeForRules(recipe)
            val docRef = recipesCollection(uid).document()
            docPath = docRef.path

            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("title", validated.title)
                put("description", validated.description)
                put("sourceType", validated.sourceType)
                validated.sourceUrl?.let { put("sourceUrl", it) }
                validated.sourceName?.let { put("sourceName", it) }
                validated.imageUrl?.let { put("imageUrl", it) }
                put("prepMinutes", validated.prepMinutes)
                put("cookMinutes", validated.cookMinutes)
                put("servings", validated.servings)
                put("ingredients", validated.ingredients)
                put("steps", validated.steps)
                put("tags", validated.tags)
                put("favorite", validated.favorite)
                put("offlinePinned", validated.offlinePinned)
                put("rating", validated.rating)
                put("notes", validated.notes)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val writeTask = docRef.set(payload)
            writeTask.addOnFailureListener { error ->
                handleFirestoreError(error, OperationType.CREATE, docPath)
            }
            if (isOnline) {
                withTimeoutOrNull(3500L) {
                    writeTask.await()
                }
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRecipe(recipeId: String, recipe: Recipe, isOnline: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recipes/$recipeId"
        return try {
            val uid = requireUserId()
            require(recipeId.isNotBlank()) { "Recipe ID must not be blank for update." }
            val validated = validateRecipeForRules(recipe)
            val docRef = recipesCollection(uid).document(recipeId)
            docPath = docRef.path

            val updates = mapOf<String, Any>(
                "title" to validated.title,
                "description" to validated.description,
                "sourceType" to validated.sourceType,
                "sourceUrl" to (validated.sourceUrl ?: FieldValue.delete()),
                "sourceName" to (validated.sourceName ?: FieldValue.delete()),
                "imageUrl" to (validated.imageUrl ?: FieldValue.delete()),
                "prepMinutes" to validated.prepMinutes,
                "cookMinutes" to validated.cookMinutes,
                "servings" to validated.servings,
                "ingredients" to validated.ingredients,
                "steps" to validated.steps,
                "tags" to validated.tags,
                "favorite" to validated.favorite,
                "offlinePinned" to validated.offlinePinned,
                "rating" to validated.rating,
                "notes" to validated.notes,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val writeTask = docRef.update(updates)
            writeTask.addOnFailureListener { error ->
                handleFirestoreError(error, OperationType.UPDATE, docPath)
            }
            if (isOnline) {
                withTimeoutOrNull(3500L) {
                    writeTask.await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(recipeId: String, favorite: Boolean, isOnline: Boolean): Result<Unit> {
        return patchSingleField(recipeId, "favorite", favorite, isOnline)
    }

    suspend fun toggleOfflinePinned(recipeId: String, offlinePinned: Boolean, isOnline: Boolean): Result<Unit> {
        return patchSingleField(recipeId, "offlinePinned", offlinePinned, isOnline)
    }

    suspend fun updateRating(recipeId: String, rating: Long, isOnline: Boolean): Result<Unit> {
        val clamped = rating.coerceIn(0L, 5L)
        return patchSingleField(recipeId, "rating", clamped, isOnline)
    }

    private suspend fun patchSingleField(
        recipeId: String,
        field: String,
        value: Any,
        isOnline: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recipes/$recipeId"
        return try {
            val uid = requireUserId()
            require(recipeId.isNotBlank()) { "Recipe ID must not be blank." }
            val docRef = recipesCollection(uid).document(recipeId)
            docPath = docRef.path
            val writeTask = docRef.update(
                mapOf(
                    field to value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            writeTask.addOnFailureListener { error ->
                handleFirestoreError(error, OperationType.UPDATE, docPath)
            }
            if (isOnline) {
                withTimeoutOrNull(3000L) {
                    writeTask.await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRecipe(recipeId: String, isOnline: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recipes/$recipeId"
        return try {
            val uid = requireUserId()
            require(recipeId.isNotBlank()) { "Recipe ID must not be blank for delete." }
            val docRef = recipesCollection(uid).document(recipeId)
            docPath = docRef.path
            val deleteTask = docRef.delete()
            deleteTask.addOnFailureListener { error ->
                handleFirestoreError(error, OperationType.DELETE, docPath)
            }
            if (isOnline) {
                withTimeoutOrNull(3000L) {
                    deleteTask.await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun validateRecipeForRules(recipe: Recipe): Recipe {
        val cleanTitle = recipe.title.trim().take(140)
        require(cleanTitle.isNotEmpty()) { "Recipe title is required." }

        val cleanIngredients = recipe.ingredients.map { it.trim() }.filter { it.isNotEmpty() }.take(100)
        require(cleanIngredients.isNotEmpty()) { "At least one ingredient is required." }

        val cleanSteps = recipe.steps.map { it.trim() }.filter { it.isNotEmpty() }.take(100)
        require(cleanSteps.isNotEmpty()) { "At least one instruction step is required." }

        val cleanTags = recipe.tags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(20)

        val cleanSourceType = RecipeSourceType.fromRaw(recipe.sourceType).name
        val cleanSourceUrl = recipe.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(1000)
        val cleanSourceName = recipe.sourceName?.trim()?.takeIf { it.isNotEmpty() }?.take(100)
        val cleanImageUrl = recipe.imageUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(1000)

        return recipe.copy(
            title = cleanTitle,
            description = recipe.description.trim().take(1000),
            sourceType = cleanSourceType,
            sourceUrl = cleanSourceUrl,
            sourceName = cleanSourceName,
            imageUrl = cleanImageUrl,
            prepMinutes = recipe.prepMinutes.coerceIn(0L, 1440L),
            cookMinutes = recipe.cookMinutes.coerceIn(0L, 1440L),
            servings = recipe.servings.coerceIn(1L, 100L),
            ingredients = cleanIngredients,
            steps = cleanSteps,
            tags = cleanTags,
            rating = recipe.rating.coerceIn(0L, 5L),
            notes = recipe.notes.trim().take(2000)
        )
    }
}
