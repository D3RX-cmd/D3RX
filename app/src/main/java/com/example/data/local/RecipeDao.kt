package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeRecipesForUser(userId: String): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    suspend fun getRecipesForUserOnce(userId: String): List<RecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(recipes: List<RecipeEntity>)

    @Query("DELETE FROM recipes WHERE userId = :userId AND id NOT IN (:activeIds)")
    suspend fun deleteMissingForUser(userId: String, activeIds: List<String>)

    @Query("DELETE FROM recipes WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Transaction
    suspend fun replaceUserRecipes(userId: String, recipes: List<RecipeEntity>) {
        if (recipes.isEmpty()) {
            deleteAllForUser(userId)
        } else {
            upsertAll(recipes)
            deleteMissingForUser(userId, recipes.map { it.id })
        }
    }
}
