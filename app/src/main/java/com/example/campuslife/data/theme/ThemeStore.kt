package com.example.campuslife.data.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme")

/**
 * 明暗主题偏好。
 *
 * 单独一个 DataStore 文件：同一个 Preferences 文件不能建两个 DataStore 实例。
 * [darkTheme] 发出 null 表示用户还没手动切过，此时界面跟随系统设置。
 */
class ThemeStore(private val context: Context) {

    val darkTheme: Flow<Boolean?> = context.themeDataStore.data.map { preferences ->
        preferences[DARK_THEME_KEY]
    }

    suspend fun setDarkTheme(darkTheme: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = darkTheme
        }
    }

    suspend fun snapshot(): Boolean? = darkTheme.first()

    suspend fun restore(darkTheme: Boolean?) {
        context.themeDataStore.edit { preferences ->
            if (darkTheme == null) {
                preferences.remove(DARK_THEME_KEY)
            } else {
                preferences[DARK_THEME_KEY] = darkTheme
            }
        }
    }
}

private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
