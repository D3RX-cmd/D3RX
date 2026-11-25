package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.data.model.Recipe
import com.google.firebase.Timestamp
import org.json.JSONArray
import java.util.Date

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val sourceType: String,
    val sourceUrl: String?,
    val sourceName: String?,
    val imageUrl: String?,
    val prepMinutes: Long,
    val cookMinutes: Long,
    val servings: Long,
    val ingredients: List<String>,
    val steps: List<String>,
    val tags: List<String>,
    val favorite: Boolean,
    val offlinePinned: Boolean,
    val rating: Long,
    val notes: String,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?
) {
    fun toDomain(): Recipe = Recipe(
        id = id,
        userId = userId,
        title = title,
        description = description,
        sourceType = sourceType,
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        imageUrl = imageUrl,
        prepMinutes = prepMinutes,
        cookMinutes = cookMinutes,
        servings = servings,
        ingredients = ingredients,
        steps = steps,
        tags = tags,
        favorite = favorite,
        offlinePinned = offlinePinned,
        rating = rating,
        notes = notes,
        createdAt = createdAtMillis?.let { Timestamp(Date(it)) },
        updatedAt = updatedAtMillis?.let { Timestamp(Date(it)) }
    )

    companion object {
        fun fromDomain(recipe: Recipe, fallbackUserId: String): RecipeEntity = RecipeEntity(
            id = recipe.id,
            userId = recipe.userId.ifBlank { fallbackUserId },
            title = recipe.title,
            description = recipe.description,
            sourceType = recipe.sourceType,
            sourceUrl = recipe.sourceUrl,
            sourceName = recipe.sourceName,
            imageUrl = recipe.imageUrl,
            prepMinutes = recipe.prepMinutes,
            cookMinutes = recipe.cookMinutes,
            servings = recipe.servings,
            ingredients = recipe.ingredients,
            steps = recipe.steps,
            tags = recipe.tags,
            favorite = recipe.favorite,
            offlinePinned = recipe.offlinePinned,
            rating = recipe.rating,
            notes = recipe.notes,
            createdAtMillis = recipe.createdAt?.toDate()?.time,
            updatedAtMillis = recipe.updatedAt?.toDate()?.time
        )
    }
}

class RecipeTypeConverters {
    @TypeConverter
    fun fromStringList(list: List<String>): String {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toStringList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index) }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
