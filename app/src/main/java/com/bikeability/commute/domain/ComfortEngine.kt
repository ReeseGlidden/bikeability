package com.bikeability.commute.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

const val MPH_TO_MS = 0.44704
const val IDEAL_PIVOT_F = 60.0
const val STEADMAN_CONSTANT_C = -4.25

fun cToF(c: Double): Double = c * 9.0 / 5.0 + 32.0

/**
 * Effective cycling airspeed (m/s): quadrature of self-generated speed and
 * ambient wind. Calm day → exactly self-speed; wind adds sublinearly.
 */
fun effectiveWindMs(selfSpeedMph: Double, ambientMs: Double, mode: WindCombine): Double {
    val selfMs = selfSpeedMph * MPH_TO_MS
    return when (mode) {
        WindCombine.QUADRATURE, WindCombine.WORST_CASE -> sqrt(selfMs * selfMs + ambientMs * ambientMs)
        WindCombine.MAX -> max(selfMs, ambientMs)
    }
}

/** Water vapour pressure (hPa) from air temp (°C) and relative humidity (%). */
fun vapourPressureHpa(tempC: Double, relHumidityPct: Double): Double =
    (relHumidityPct / 100.0) * 6.105 * exp(17.27 * tempC / (237.7 + tempC))

/**
 * Steadman Apparent Temperature, radiation-inclusive form, with the effective
 * cycling airspeed in both the convective term and the solar-damping denominator.
 *
 * WORST_CASE mode evaluates both wind combines and keeps the AT further from
 * the ideal pivot: quadrature always yields more wind than max, so this is
 * "full wind chill when cold, least wind relief when hot" — no threshold.
 */
fun apparentTemperature(
    tempC: Double,
    relHumidityPct: Double,
    ambientWindMs: Double,
    shortwaveWm2: Double,
    params: EngineParams,
): AtBreakdown {
    if (params.windCombine == WindCombine.WORST_CASE) {
        val quad = steadman(tempC, relHumidityPct, ambientWindMs, shortwaveWm2, params, WindCombine.QUADRATURE)
        val maxed = steadman(tempC, relHumidityPct, ambientWindMs, shortwaveWm2, params, WindCombine.MAX)
        return maxOf(quad, maxed, compareBy { abs(cToF(it.apparentTempC) - params.idealPivotF) })
    }
    return steadman(tempC, relHumidityPct, ambientWindMs, shortwaveWm2, params, params.windCombine)
}

/** Feels-like for the same conditions while stopped: no self-generated airflow. */
fun apparentTemperatureStopped(
    tempC: Double,
    relHumidityPct: Double,
    ambientWindMs: Double,
    shortwaveWm2: Double,
    params: EngineParams,
): AtBreakdown =
    steadman(tempC, relHumidityPct, ambientWindMs, shortwaveWm2, params.copy(selfSpeedMph = 0.0), WindCombine.QUADRATURE)

private fun steadman(
    tempC: Double,
    relHumidityPct: Double,
    ambientWindMs: Double,
    shortwaveWm2: Double,
    params: EngineParams,
    mode: WindCombine,
): AtBreakdown {
    val ws = effectiveWindMs(params.selfSpeedMph, ambientWindMs, mode)
    val e = vapourPressureHpa(tempC, relHumidityPct)
    val q = params.solarGainK * shortwaveWm2
    return AtBreakdown(
        airTempC = tempC,
        humidityTermC = 0.348 * e,
        windTermC = -0.70 * ws,
        solarTermC = 0.70 * (q / (ws + 10.0)),
    )
}

fun categoryFor(feelsLikeF: Double, bounds: TempBoundsF): Category = when {
    feelsLikeF < bounds.tooCold -> Category.TOO_COLD
    feelsLikeF < bounds.gloves -> Category.GLOVES
    feelsLikeF < bounds.jacket -> Category.JACKET
    feelsLikeF < bounds.shorts -> Category.IDEAL
    feelsLikeF < bounds.tooHot -> Category.SHORTS
    else -> Category.TOO_HOT
}

fun tempSeverity(category: Category): Severity = when (category) {
    Category.IDEAL, Category.JACKET -> Severity.GREEN
    Category.GLOVES, Category.SHORTS -> Severity.YELLOW
    Category.TOO_COLD, Category.TOO_HOT -> Severity.RED
}

fun precipSeverity(peakProbPct: Double, peakRateMmHr: Double, params: EngineParams): Severity = when {
    peakRateMmHr >= params.redRateMmHr -> Severity.RED
    peakProbPct >= params.yellowProbPct -> Severity.YELLOW
    else -> Severity.GREEN
}

fun pictographFor(peakRateMmHr: Double, meanCloudCoverPct: Double, params: EngineParams): Pictograph = when {
    peakRateMmHr >= params.redRateMmHr -> Pictograph.RAIN
    meanCloudCoverPct < 25.0 -> Pictograph.SUNNY
    meanCloudCoverPct <= 60.0 -> Pictograph.PARTLY_CLOUDY
    else -> Pictograph.CLOUDY
}

/**
 * Forecast buckets [t, t+bucket) that overlap the clock window [start, end)
 * on [date]. No interpolation.
 */
fun samplesInWindow(
    samples: List<WeatherSample>,
    date: LocalDate,
    start: LocalTime,
    end: LocalTime,
    bucketMinutes: Long = 15,
): List<WeatherSample> {
    val windowStart = LocalDateTime.of(date, start)
    val windowEnd = LocalDateTime.of(date, end)
    return samples.filter { it.time < windowEnd && it.time.plusMinutes(bucketMinutes) > windowStart }
}

/**
 * The date a window should be computed for: today while the window hasn't
 * fully passed, otherwise tomorrow (a 9 PM glance should show tomorrow
 * morning, not this morning's history).
 */
fun resolveWindowDate(now: LocalDateTime, windowEnd: LocalTime): LocalDate =
    if (now.toLocalTime() < windowEnd) now.toLocalDate() else now.toLocalDate().plusDays(1)

/** The next time [time] occurs strictly after [now] — today if still ahead, else tomorrow. */
fun nextOccurrence(now: LocalDateTime, time: LocalTime): LocalDateTime {
    val todayAt = LocalDateTime.of(now.toLocalDate(), time)
    return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
}

/**
 * The Mon–Fri the week strip previews: the current workweek on weekdays,
 * flipping to the upcoming week from Saturday morning.
 */
fun workweekDates(now: LocalDateTime): List<LocalDate> {
    val today = now.toLocalDate()
    val monday = if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) {
        today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    } else {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    return (0L..4L).map { monday.plusDays(it) }
}

/**
 * §2.5–2.7: worst hour (feels-like furthest from the ideal balance point,
 * either direction), peak precip over the window, pictograph, and row severity.
 */
fun aggregateWindow(samples: List<WeatherSample>, params: EngineParams): WindowResult? {
    if (samples.isEmpty()) return null

    val worst = samples
        .map { s ->
            val bd = apparentTemperature(s.tempC, s.relHumidityPct, s.ambientWindMs, s.shortwaveWm2, params)
            s to bd
        }
        .maxBy { (_, bd) -> abs(cToF(bd.apparentTempC) - params.idealPivotF) }
        .let { (s, bd) ->
            val feelsF = cToF(bd.apparentTempC)
            val stopped = apparentTemperatureStopped(s.tempC, s.relHumidityPct, s.ambientWindMs, s.shortwaveWm2, params)
            WorstHour(
                time = s.time,
                airTempF = cToF(s.tempC),
                feelsLikeF = feelsF,
                stoppedFeelsLikeF = cToF(stopped.apparentTempC),
                category = categoryFor(feelsF, params.tempBoundsF),
                breakdown = bd,
            )
        }

    val peakProb = samples.maxOf { it.precipProbPct }
    val peakRate = samples.maxOf { it.precipRateMmHr }
    val meanCloud = samples.sumOf { it.cloudCoverPct } / samples.size

    return WindowResult(
        worstHour = worst,
        peakProbPct = peakProb,
        peakRateMmHr = peakRate,
        meanCloudCoverPct = meanCloud,
        pictograph = pictographFor(peakRate, meanCloud, params),
        severity = maxOf(
            tempSeverity(worst.category),
            precipSeverity(peakProb, peakRate, params),
        ),
    )
}

/**
 * §2.8: merge the home and work results for one window, field-wise and
 * conservatively — worse worst-hour, max precip, worse cloud, worse severity.
 */
fun mergeEndpoints(a: WindowResult, b: WindowResult, params: EngineParams): WindowResult {
    val worst = maxOf(a.worstHour, b.worstHour, compareBy { abs(it.feelsLikeF - params.idealPivotF) })
    val peakProb = max(a.peakProbPct, b.peakProbPct)
    val peakRate = max(a.peakRateMmHr, b.peakRateMmHr)
    val worseCloud = max(a.meanCloudCoverPct, b.meanCloudCoverPct)
    return WindowResult(
        worstHour = worst,
        peakProbPct = peakProb,
        peakRateMmHr = peakRate,
        meanCloudCoverPct = worseCloud,
        pictograph = pictographFor(peakRate, worseCloud, params),
        severity = maxOf(a.severity, b.severity),
    )
}
