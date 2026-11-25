package com.example.ui.cookbook

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Recipe
import com.example.data.model.RecipeSortOption
import com.example.data.model.RecipeSourceType
import com.example.data.model.SourceFilter
import com.example.data.model.SyncInfo
import com.example.data.model.UiState
import com.example.data.remote.ImportedRecipeDraft
import com.example.data.remote.NetworkMonitor
import com.example.data.remote.RecipeRepository
import com.example.data.remote.RecipeStreamPayload
import com.example.data.remote.RecipeWebImporter
import com.example.ui.components.IngredientScaler
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookbookFilterState(
    val searchQuery: String = "",
    val ingredientFilters: Set<String> = emptySet(),
    val matchAllIngredients: Boolean = true,
    val selectedTag: String? = null,
    val sourceFilter: SourceFilter = SourceFilter.ALL,
    val sortOption: RecipeSortOption = RecipeSortOption.RECENTLY_UPDATED
)

data class CookbookUiModel(
    val allRecipes: List<Recipe>,
    val filteredRecipes: List<Recipe>,
    val allTags: List<String>,
    val suggestedIngredients: List<String>,
    val filterState: CookbookFilterState
)

class CookbookViewModel(
    private val repository: RecipeRepository,
    private val webImporter: RecipeWebImporter,
    private val networkMonitor: NetworkMonitor,
    private val currentUserId: String
) : ViewModel() {

    private val _filterState = MutableStateFlow(CookbookFilterState())
    val filterState: StateFlow<CookbookFilterState> = _filterState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _syncMeta = MutableStateFlow(Pair(false, false))

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = true
    )

    val syncInfo: StateFlow<SyncInfo> = combine(isOnline, _syncMeta) { online, meta ->
        SyncInfo(
            isOnline = online,
            isFromCache = meta.first,
            hasPendingWrites = meta.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SyncInfo()
    )

    private val recipesStream: StateFlow<UiState<List<Recipe>>> = repository
        .observeRecipes(currentUserId)
        .map<RecipeStreamPayload, UiState<List<Recipe>>> { payload ->
            _syncMeta.value = payload.isFromCache to payload.hasPendingWrites
            UiState.Success(payload.recipes)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing recipes", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load recipes"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val uiState: StateFlow<UiState<CookbookUiModel>> = combine(
        recipesStream,
        _filterState
    ) { state, filters ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(state.message)
            is UiState.Success -> {
                val recipes = state.data
                val filtered = applyFiltersAndSort(recipes, filters)
                val allTags = recipes.flatMap { it.tags }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }
                val suggestedIngredients = IngredientScaler.extractTopPantryIngredients(recipes)
                UiState.Success(
                    CookbookUiModel(
                        allRecipes = recipes,
                        filteredRecipes = filtered,
                        allTags = allTags,
                        suggestedIngredients = suggestedIngredients,
                        filterState = filters
                    )
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun addIngredientFilter(ingredient: String) {
        val clean = ingredient.trim()
        if (clean.isEmpty()) return
        _filterState.update { state ->
            val exists = state.ingredientFilters.any { it.equals(clean, ignoreCase = true) }
            if (exists) state else state.copy(ingredientFilters = state.ingredientFilters + clean)
        }
    }

    fun toggleIngredientFilter(ingredient: String) {
        val clean = ingredient.trim()
        if (clean.isEmpty()) return
        _filterState.update { state ->
            val match = state.ingredientFilters.firstOrNull { it.equals(clean, ignoreCase = true) }
            val updated = if (match != null) {
                state.ingredientFilters - match
            } else {
                state.ingredientFilters + clean
            }
            state.copy(ingredientFilters = updated)
        }
    }

    fun setMatchAllIngredients(matchAll: Boolean) {
        _filterState.update { it.copy(matchAllIngredients = matchAll) }
    }

    fun selectTag(tag: String?) {
        _filterState.update { state ->
            val next = if (state.selectedTag.equals(tag, ignoreCase = true)) null else tag
            state.copy(selectedTag = next)
        }
    }

    fun selectSourceFilter(filter: SourceFilter) {
        _filterState.update { it.copy(sourceFilter = filter) }
    }

    fun selectSortOption(option: RecipeSortOption) {
        _filterState.update { it.copy(sortOption = option) }
    }

    fun clearAllFilters() {
        _filterState.update {
            it.copy(
                searchQuery = "",
                ingredientFilters = emptySet(),
                selectedTag = null,
                sourceFilter = SourceFilter.ALL
            )
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch {
            val result = repository.toggleFavorite(recipe.id, !recipe.favorite, isOnline.value)
            result.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Couldn't update favorite."
            }
        }
    }

    fun toggleOfflinePinned(recipe: Recipe) {
        viewModelScope.launch {
            val next = !recipe.offlinePinned
            val result = repository.toggleOfflinePinned(recipe.id, next, isOnline.value)
            result.onSuccess {
                _statusMessage.value = if (next) {
                    "Pinned for offline kitchen viewing."
                } else {
                    "Removed offline pin."
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Couldn't update offline pin."
            }
        }
    }

    fun updateRating(recipe: Recipe, newRating: Long) {
        viewModelScope.launch {
            val result = repository.updateRating(recipe.id, newRating, isOnline.value)
            result.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Couldn't update rating."
            }
        }
    }

    fun deleteRecipe(recipeId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteRecipe(recipeId, isOnline.value)
            result.onSuccess {
                _statusMessage.value = "Recipe deleted."
                onDeleted()
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to delete recipe."
            }
        }
    }

    fun saveRecipe(
        existingId: String?,
        recipe: Recipe,
        onSaved: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = if (existingId.isNullOrBlank()) {
                repository.createRecipe(recipe, isOnline.value)
            } else {
                repository.updateRecipe(existingId, recipe, isOnline.value).map { existingId }
            }
            _isSaving.value = false
            result.onSuccess { savedId ->
                _statusMessage.value = if (isOnline.value) {
                    "Recipe saved and synced to cloud."
                } else {
                    "Saved offline — will sync automatically when online."
                }
                onSaved(savedId)
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Couldn't save recipe."
            }
        }
    }

    fun importFromUrl(
        url: String,
        onSuccess: (ImportedRecipeDraft) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isOnline.value) {
            onError("You're offline. Connect to the internet to fetch a recipe from a webpage.")
            return
        }
        viewModelScope.launch {
            _isImporting.value = true
            val result = webImporter.importFromUrl(url)
            _isImporting.value = false
            result.onSuccess(onSuccess).onFailure { err ->
                onError(err.localizedMessage ?: "Failed to fetch recipe from URL.")
            }
        }
    }

    fun findRecipeById(recipeId: String): Recipe? {
        val current = uiState.value as? UiState.Success ?: return null
        return current.data.allRecipes.firstOrNull { it.id == recipeId }
    }

    private fun applyFiltersAndSort(
        recipes: List<Recipe>,
        filters: CookbookFilterState
    ): List<Recipe> {
        val query = filters.searchQuery.trim().lowercase()
        val filtered = recipes.filter { recipe ->
            val matchesSource = when (filters.sourceFilter) {
                SourceFilter.ALL -> true
                SourceFilter.OFFLINE -> recipe.offlinePinned
                SourceFilter.FAVORITES -> recipe.favorite
                SourceFilter.PERSONAL -> recipe.parsedSourceType == RecipeSourceType.PERSONAL
                SourceFilter.ONLINE -> recipe.parsedSourceType == RecipeSourceType.ONLINE
            }

            val matchesTag = filters.selectedTag == null ||
                recipe.tags.any { it.equals(filters.selectedTag, ignoreCase = true) }

            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                recipe.title.lowercase().contains(query) ||
                    recipe.description.lowercase().contains(query) ||
                    recipe.notes.lowercase().contains(query) ||
                    recipe.tags.any { it.lowercase().contains(query) } ||
                    recipe.ingredients.any { it.lowercase().contains(query) }
            }

            val matchesIngredients = if (filters.ingredientFilters.isEmpty()) {
                true
            } else if (filters.matchAllIngredients) {
                filters.ingredientFilters.all { ing ->
                    val needle = ing.lowercase()
                    recipe.ingredients.any { line -> line.lowercase().contains(needle) }
                }
            } else {
                filters.ingredientFilters.any { ing ->
                    val needle = ing.lowercase()
                    recipe.ingredients.any { line -> line.lowercase().contains(needle) }
                }
            }

            matchesSource && matchesTag && matchesQuery && matchesIngredients
        }

        return when (filters.sortOption) {
            RecipeSortOption.RECENTLY_UPDATED -> filtered.sortedByDescending {
                it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)
            }
            RecipeSortOption.NEWEST -> filtered.sortedByDescending {
                it.createdAt ?: Timestamp(0, 0)
            }
            RecipeSortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            RecipeSortOption.QUICKEST -> filtered.sortedWith(
                compareBy<Recipe> { it.totalMinutes }.thenBy { it.title.lowercase() }
            )
            RecipeSortOption.HIGHEST_RATED -> filtered.sortedWith(
                compareByDescending<Recipe> { it.rating }
                    .thenByDescending { it.favorite }
                    .thenBy { it.title.lowercase() }
            )
            RecipeSortOption.FAVORITES_FIRST -> filtered.sortedWith(
                compareByDescending<Recipe> { it.favorite }
                    .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            )
        }
    }

    private companion object {
        const val TAG = "CookbookVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
