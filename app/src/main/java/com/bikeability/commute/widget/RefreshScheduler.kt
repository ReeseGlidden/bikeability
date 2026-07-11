package com.bikeability.commute.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bikeability.commute.config.AppConfig
import com.bikeability.commute.domain.nextOccurrence
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object RefreshScheduler {
    private const val PERIODIC_WORK = "weather-refresh"
    private const val ONE_OFF_WORK = "weather-refresh-now"
    private const val PRE_MORNING_WORK = "pre-morning-refresh"
    private const val PRE_EVENING_WORK = "pre-evening-refresh"

    /** Refresh this long before a window opens so the glance is fresh. */
    private const val PRE_WINDOW_LEAD_MINUTES = 20L

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Everything at once: hourly refresh plus a daily fetch just before each
     * window opens — Doze can defer the hourly one overnight, and "fresh at
     * 6:55 AM" is the whole product.
     */
    fun scheduleAll(context: Context, config: AppConfig) {
        schedulePeriodic(context, config.refresh.intervalMinutes)
        schedulePreWindow(context, PRE_MORNING_WORK, config.windows.morning.start)
        schedulePreWindow(context, PRE_EVENING_WORK, config.windows.evening.start)
    }

    private fun schedulePreWindow(context: Context, workName: String, windowStart: String) {
        val target = LocalTime.parse(windowStart).minusMinutes(PRE_WINDOW_LEAD_MINUTES)
        val delay = Duration.between(LocalDateTime.now(), nextOccurrence(LocalDateTime.now(), target))
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            // Re-align to the (possibly changed) window time on every reschedule.
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    fun schedulePeriodic(context: Context, intervalMinutes: Int) {
        val interval = intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_OFF_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC_WORK)
        wm.cancelUniqueWork(PRE_MORNING_WORK)
        wm.cancelUniqueWork(PRE_EVENING_WORK)
    }
}
