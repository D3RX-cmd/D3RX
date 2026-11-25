package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RecipeEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RecipeTypeConverters::class)
abstract class SimmerDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        @Volatile
        private var instance: SimmerDatabase? = null

        fun getInstance(context: Context): SimmerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SimmerDatabase::class.java,
                    "simmer_recipes.db"
                ).build().also { instance = it }
            }
        }
    }
}
