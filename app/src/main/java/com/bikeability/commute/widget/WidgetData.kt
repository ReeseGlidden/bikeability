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
    val stale: Boolean = false,
    val message: String? = null, // shown when there are no rows (unconfigured / no cache)
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
