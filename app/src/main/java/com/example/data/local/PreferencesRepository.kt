package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.simmerDataStore: DataStore<Preferences> by preferencesDataStore(name = "simmer_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val themeMode: Flow<ThemeMode> = context.simmerDataStore.data.map { prefs ->
        ThemeMode.fromRaw(prefs[Keys.THEME_MODE])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.simmerDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }
}
