package com.bikeability.commute.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.configDataStore by preferencesDataStore(name = "config")

object ConfigStore {
    private val KEY_CONFIG = stringPreferencesKey("config_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun flow(context: Context): Flow<AppConfig> =
        context.configDataStore.data.map { prefs -> decode(prefs[KEY_CONFIG]) }

    suspend fun read(context: Context): AppConfig = flow(context).first()

    suspend fun write(context: Context, config: AppConfig) {
        context.configDataStore.edit { prefs ->
            prefs[KEY_CONFIG] = json.encodeToString(AppConfig.serializer(), config)
        }
    }

    private fun decode(raw: String?): AppConfig =
        raw?.let {
            runCatching { json.decodeFromString(AppConfig.serializer(), it) }.getOrNull()
        } ?: AppConfig()
}
