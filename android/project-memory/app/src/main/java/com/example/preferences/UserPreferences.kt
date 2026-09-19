package com.example.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.projectMemoryPreferences by preferencesDataStore("project_memory_preferences")

data class UserPreferences(val activeProjectId: String? = null, val darkTheme: Boolean = true)

class UserPreferencesRepository(private val context: Context) {
  private object Keys {
    val ActiveProject = stringPreferencesKey("active_project_id")
    val DarkTheme = booleanPreferencesKey("dark_theme")
  }
  val values: Flow<UserPreferences> = context.projectMemoryPreferences.data.map { preferences ->
    UserPreferences(preferences[Keys.ActiveProject], preferences[Keys.DarkTheme] ?: true)
  }
  suspend fun setActiveProject(projectId: String?) = context.projectMemoryPreferences.edit { preferences ->
    if (projectId == null) preferences.remove(Keys.ActiveProject) else preferences[Keys.ActiveProject] = projectId
  }
  suspend fun setDarkTheme(enabled: Boolean) = context.projectMemoryPreferences.edit { it[Keys.DarkTheme] = enabled }
}
