package com.bikeability.commute.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bikeability.commute.config.AppConfig
import com.bikeability.commute.config.ConfigStore
import com.bikeability.commute.config.WindowCfg
import com.bikeability.commute.data.WeatherRepository
import com.bikeability.commute.domain.EngineParams
import com.bikeability.commute.domain.HourSample
import com.bikeability.commute.domain.aggregateWindow
import com.bikeability.commute.domain.mergeEndpoints
import com.bikeability.commute.domain.resolveWindowDate
import com.bikeability.commute.domain.samplesInWindow
import java.time.LocalDateTime
import java.time.LocalTime

class RefreshWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val config = ConfigStore.read(context)
        val now = LocalDateTime.now()

        if (!config.isConfigured) {
            WidgetStateRepo.publish(
                context,
                WidgetData(
                    dateLabel = formatDateLabel(now),
                    updatedLabel = formatTimeLabel(now),
                    message = "Tap to set home & work locations",
                ),
            )
            return Result.success()
        }

        return try {
            val (homeSamples, workSamples) = WeatherRepository().fetchBoth(config)
            val engine = config.toEngineParams()
            WidgetStateRepo.publish(
                context,
                WidgetData(
                    dateLabel = formatDateLabel(now),
                    updatedLabel = formatTimeLabel(now),
                    morning = windowUi("MORNING", config.windows.morning, homeSamples, workSamples, engine, now),
                    evening = windowUi("EVENING", config.windows.evening, homeSamples, workSamples, engine, now),
                ),
            )
            Result.success()
        } catch (e: Exception) {
            val cached = WidgetStateRepo.readLast(context)
            if (cached != null && (cached.morning != null || cached.evening != null)) {
                WidgetStateRepo.publish(context, cached.copy(stale = true))
            } else {
                WidgetStateRepo.publish(
                    context,
                    WidgetData(
                        dateLabel = formatDateLabel(now),
                        updatedLabel = formatTimeLabel(now),
                        message = "Couldn't fetch forecast — will retry",
                    ),
                )
            }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun windowUi(
        label: String,
        window: WindowCfg,
        homeSamples: List<HourSample>,
        workSamples: List<HourSample>,
        engine: EngineParams,
        now: LocalDateTime,
    ): WindowUi? {
        val start = LocalTime.parse(window.start)
        val end = LocalTime.parse(window.end)
        val date = resolveWindowDate(now, end)
        val home = aggregateWindow(samplesInWindow(homeSamples, date, start, end), engine)
        val work = aggregateWindow(samplesInWindow(workSamples, date, start, end), engine)
        val merged = when {
            home != null && work != null -> mergeEndpoints(home, work, engine)
            else -> home ?: work
        } ?: return null
        return merged.toUi(label, window)
    }
}
