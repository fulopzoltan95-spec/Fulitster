package com.example.app.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class ThemeStore(private val context: Context) {
    private val THEME_KEY = stringPreferencesKey("theme_key")

    val themeFlow: Flow<ThemeOption> =
        context.dataStore.data.map { prefs ->
            ThemeOption.fromKey(prefs[THEME_KEY])
        }

    suspend fun setTheme(option: ThemeOption) {
        context.dataStore.edit { prefs ->
            prefs[THEME_KEY] = option.key
        }
    }
}
