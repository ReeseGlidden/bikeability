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
import com.bikeability.commute.domain.WindowResult
import com.bikeability.commute.domain.aggregateWindow
import com.bikeability.commute.domain.mergeEndpoints
import com.bikeability.commute.domain.resolveWindowDate
import com.bikeability.commute.domain.samplesInWindow
import com.bikeability.commute.domain.workweekDates
import java.time.DayOfWeek
import java.time.LocalDate
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
                    week = weekChips(config.windows.morning, config.windows.evening, homeSamples, workSamples, engine, now),
                    days = dayDetails(config.windows.morning, config.windows.evening, homeSamples, workSamples, engine, now),
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
        val date = resolveWindowDate(now, LocalTime.parse(window.end))
        return mergedResult(window, date, homeSamples, workSamples, engine)?.toUi(label, window)
    }

    private fun weekChips(
        morning: WindowCfg,
        evening: WindowCfg,
        homeSamples: List<HourSample>,
        workSamples: List<HourSample>,
        engine: EngineParams,
        now: LocalDateTime,
    ): List<DayChip> = workweekDates(now).map { date ->
        DayChip(
            label = DAY_LABELS.getValue(date.dayOfWeek),
            morning = mergedResult(morning, date, homeSamples, workSamples, engine)?.severity?.name,
            evening = mergedResult(evening, date, homeSamples, workSamples, engine)?.severity?.name,
        )
    }

    private fun dayDetails(
        morning: WindowCfg,
        evening: WindowCfg,
        homeSamples: List<HourSample>,
        workSamples: List<HourSample>,
        engine: EngineParams,
        now: LocalDateTime,
    ): List<DayDetail> {
        val today = now.toLocalDate()
        return (0L..6L).map { offset ->
            val date = today.plusDays(offset)
            DayDetail(
                title = dayTitle(date, today),
                morning = mergedResult(morning, date, homeSamples, workSamples, engine)?.toUi("MORNING", morning),
                evening = mergedResult(evening, date, homeSamples, workSamples, engine)?.toUi("EVENING", evening),
            )
        }
    }

    private fun mergedResult(
        window: WindowCfg,
        date: LocalDate,
        homeSamples: List<HourSample>,
        workSamples: List<HourSample>,
        engine: EngineParams,
    ): WindowResult? {
        val start = LocalTime.parse(window.start)
        val end = LocalTime.parse(window.end)
        val home = aggregateWindow(samplesInWindow(homeSamples, date, start, end), engine)
        val work = aggregateWindow(samplesInWindow(workSamples, date, start, end), engine)
        return when {
            home != null && work != null -> mergeEndpoints(home, work, engine)
            else -> home ?: work
        }
    }

    private companion object {
        val DAY_LABELS = mapOf(
            DayOfWeek.MONDAY to "M",
            DayOfWeek.TUESDAY to "Tu",
            DayOfWeek.WEDNESDAY to "W",
            DayOfWeek.THURSDAY to "Th",
            DayOfWeek.FRIDAY to "F",
            DayOfWeek.SATURDAY to "Sa",
            DayOfWeek.SUNDAY to "Su",
        )
    }
}
