package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    
    fun getSetting(key: String, fallback: String? = null): Flow<String?> {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { preferences ->
            preferences[prefKey] ?: fallback
        }
    }

    suspend fun setSetting(key: String, value: String?) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(prefKey)
            } else {
                preferences[prefKey] = value
            }
        }
    }
}
