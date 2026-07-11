package com.bikeability.commute.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.widgetStateDataStore by preferencesDataStore(name = "widget_state")

/**
 * Publishes computed results to (a) the app-level DataStore, which doubles as
 * the offline cache and feeds the settings calibration readout, and (b) every
 * live widget's Glance state.
 */
object WidgetStateRepo {
    val KEY_DATA = stringPreferencesKey("widget_data_json")
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String?): WidgetData? =
        raw?.let { runCatching { json.decodeFromString(WidgetData.serializer(), it) }.getOrNull() }

    fun flow(context: Context): Flow<WidgetData?> =
        context.widgetStateDataStore.data.map { decode(it[KEY_DATA]) }

    suspend fun readLast(context: Context): WidgetData? = flow(context).first()

    suspend fun publish(context: Context, data: WidgetData) {
        val encoded = json.encodeToString(WidgetData.serializer(), data)
        context.widgetStateDataStore.edit { it[KEY_DATA] = encoded }
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(CommuteWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs -> prefs[KEY_DATA] = encoded }
        }
        CommuteWidget().updateAll(context)
    }
}
