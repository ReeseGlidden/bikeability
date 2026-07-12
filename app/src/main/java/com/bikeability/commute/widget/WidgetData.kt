package com.bikeability.commute.widget

import com.bikeability.commute.config.WindowCfg
import com.bikeability.commute.domain.WindowResult
import com.bikeability.commute.domain.cToF
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Everything the Glance renderer needs, pre-formatted and JSON-serializable. */
@Serializable
data class WidgetData(
    val dateLabel: String,      // "Fri Jul 11"
    val updatedLabel: String,   // "6:52 AM"
    val morning: WindowUi? = null,
    val evening: WindowUi? = null,
    val week: List<DayChip> = emptyList(),
    val days: List<DayDetail> = emptyList(), // today + 6, for the forecast screen
    val stale: Boolean = false,
    val message: String? = null, // shown when there are no rows (unconfigured / no cache)
)

/** One card on the forecast screen: a full day at commute-window resolution. */
@Serializable
data class DayDetail(
    val title: String, // "Today · Fri Jul 11", "Tomorrow · Sat Jul 12", "Mon Jul 14"
    val morning: WindowUi? = null,
    val evening: WindowUi? = null,
)

/**
 * One weekday in the slim week strip: morning/evening severity names, or null
 * when the forecast doesn't cover that day (past, or beyond the horizon).
 */
@Serializable
data class DayChip(
    val label: String, // "M", "Tu", ...
    val morning: String? = null,
    val evening: String? = null,
)

@Serializable
data class WindowUi(
    val windowLabel: String,    // "MORNING"
    val rangeLabel: String,     // "7:15–8:15"
    val airTempF: Int,
    val feelsLikeF: Int,
    val categoryLabel: String,
    val severity: String,       // Severity.name
    val pictograph: String,     // Pictograph.name
    val peakProbPct: Int,
    val peakRateMmHr: Double,
    val worstAtLabel: String,   // "7:15 AM"
    // Calibration breakdown, display-ready °F: absolute base + delta terms.
    val bdBaseF: Double,
    val bdHumidityF: Double,
    val bdWindF: Double,
    val bdSolarF: Double,
    val bdConstantF: Double,
)

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dateFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

fun formatDateLabel(dt: LocalDateTime): String = dt.format(dateFmt)
fun formatTimeLabel(dt: LocalDateTime): String = dt.format(timeFmt)

fun dayTitle(date: java.time.LocalDate, today: java.time.LocalDate): String {
    val base = date.format(dateFmt)
    return when (date) {
        today -> "Today · $base"
        today.plusDays(1) -> "Tomorrow · $base"
        else -> base
    }
}

private fun rangeLabel(w: WindowCfg): String {
    fun short(s: String): String {
        val t = LocalTime.parse(s)
        return if (t.minute == 0) "${((t.hour + 11) % 12) + 1}" else "${((t.hour + 11) % 12) + 1}:%02d".format(t.minute)
    }
    return "${short(w.start)}–${short(w.end)}"
}

fun WindowResult.toUi(windowLabel: String, window: WindowCfg): WindowUi {
    val bd = worstHour.breakdown
    return WindowUi(
        windowLabel = windowLabel,
        rangeLabel = rangeLabel(window),
        airTempF = worstHour.airTempF.roundToInt(),
        feelsLikeF = worstHour.feelsLikeF.roundToInt(),
        categoryLabel = worstHour.category.label,
        severity = severity.name,
        pictograph = pictograph.name,
        peakProbPct = peakProbPct.roundToInt(),
        peakRateMmHr = peakRateMmHr,
        worstAtLabel = formatTimeLabel(worstHour.time),
        bdBaseF = cToF(bd.airTempC),
        bdHumidityF = bd.humidityTermC * 1.8,
        bdWindF = bd.windTermC * 1.8,
        bdSolarF = bd.solarTermC * 1.8,
        bdConstantF = bd.constantC * 1.8,
    )
}
