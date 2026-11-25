package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class RecipeSourceType(val label: String) {
    PERSONAL("Personal"),
    ONLINE("Online");

    companion object {
        fun fromRaw(raw: String?): RecipeSourceType {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PERSONAL
        }
    }
}

enum class RecipeSortOption(val label: String) {
    RECENTLY_UPDATED("Recently Updated"),
    NEWEST("Newest Added"),
    TITLE_ASC("Title (A–Z)"),
    QUICKEST("Quickest Total Time"),
    HIGHEST_RATED("Highest Rated"),
    FAVORITES_FIRST("Favorites First")
}

enum class SourceFilter(val label: String) {
    ALL("All"),
    OFFLINE("Offline Saved"),
    FAVORITES("Favorites"),
    PERSONAL("My Own"),
    ONLINE("From Online")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromRaw(raw: String?): ThemeMode {
            return entries.firstOrNull { it.name == raw } ?: SYSTEM
        }
    }
}

@Keep
@IgnoreExtraProperties
data class Recipe(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val sourceType: String = RecipeSourceType.PERSONAL.name,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val imageUrl: String? = null,
    val prepMinutes: Long = 10L,
    val cookMinutes: Long = 20L,
    val servings: Long = 4L,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val offlinePinned: Boolean = true,
    val rating: Long = 0L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val totalMinutes: Long
        get() = prepMinutes + cookMinutes

    val parsedSourceType: RecipeSourceType
        get() = RecipeSourceType.fromRaw(sourceType)
}

data class SyncInfo(
    val isOnline: Boolean = true,
    val isFromCache: Boolean = false,
    val hasPendingWrites: Boolean = false
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
