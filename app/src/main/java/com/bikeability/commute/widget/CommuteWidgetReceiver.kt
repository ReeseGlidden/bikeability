package com.bikeability.commute.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.bikeability.commute.config.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommuteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommuteWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleFromConfig(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Also fires after reboot — re-ensures the schedules exist.
        scheduleFromConfig(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshScheduler.cancelAll(context)
    }

    /** Config lives in DataStore, so hold the receiver open while reading it. */
    private fun scheduleFromConfig(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RefreshScheduler.scheduleAll(context, ConfigStore.read(context))
                RefreshScheduler.refreshNow(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
