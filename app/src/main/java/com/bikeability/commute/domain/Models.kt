package com.bikeability.commute.domain

import java.time.LocalDateTime

/** Comfort category, driven by feels-like (not air temp). Ordered cold → hot. */
enum class Category(val label: String) {
    TOO_COLD("Too cold"),
    GLOVES("Gloves"),
    JACKET("Jacket"),
    IDEAL("Ideal"),
    SHORTS("Shorts"),
    TOO_HOT("Too hot"),
}

/** Bikeability severity. Ordinal order matters: maxOf() picks the worse one. */
enum class Severity { GREEN, YELLOW, RED }

enum class Pictograph { SUNNY, PARTLY_CLOUDY, CLOUDY, RAIN }

enum class WindCombine {
    QUADRATURE,
    MAX,

    /**
     * Worst-case: evaluate AT under both QUADRATURE (more wind) and MAX
     * (less wind) and keep whichever lands further from the ideal pivot.
     * Equivalent to "less wind relief when hot, more wind chill when cold".
     */
    WORST_CASE,
}

/**
 * Gear-line thresholds in °F, each named for the decision it drives:
 * below [jacket] a jacket is needed, at/above [shorts] you ride in shorts
 * and change at work, etc. Between [jacket] and [shorts] is the IDEAL band.
 */
data class TempBoundsF(
    val tooCold: Double = 35.0,
    val gloves: Double = 45.0,
    val jacket: Double = 55.0,
    val shorts: Double = 68.0,
    val tooHot: Double = 82.0,
)

data class EngineParams(
    val selfSpeedMph: Double = 16.0,
    val windCombine: WindCombine = WindCombine.QUADRATURE,
    val solarGainK: Double = 0.08,
    /** Balance point: the worst hour is the one furthest from this feels-like. */
    val idealPivotF: Double = IDEAL_PIVOT_F,
    val tempBoundsF: TempBoundsF = TempBoundsF(),
    val yellowProbPct: Double = 20.0,
    val redRateMmHr: Double = 0.3,
)

/** One forecast bucket (15-minutely), metric units, for a single location. */
data class WeatherSample(
    val time: LocalDateTime,
    val tempC: Double,
    val relHumidityPct: Double,
    val ambientWindMs: Double,
    val shortwaveWm2: Double,
    val precipRateMmHr: Double,
    val precipProbPct: Double,
    val cloudCoverPct: Double,
)

/**
 * Steadman AT split into its additive terms (°C) so the calibration readout
 * can show which knob to nudge.
 */
data class AtBreakdown(
    val airTempC: Double,
    val humidityTermC: Double,
    val windTermC: Double,
    val solarTermC: Double,
) {
    val constantC: Double get() = STEADMAN_CONSTANT_C
    val apparentTempC: Double
        get() = airTempC + humidityTermC + windTermC + solarTermC + constantC
}

data class WorstHour(
    val time: LocalDateTime,
    val airTempF: Double,
    val feelsLikeF: Double,
    /** Same bucket, self-speed 0 — how it feels stopped at a light. */
    val stoppedFeelsLikeF: Double,
    val category: Category,
    val breakdown: AtBreakdown,
)

data class WindowResult(
    val worstHour: WorstHour,
    val peakProbPct: Double,
    val peakRateMmHr: Double,
    val meanCloudCoverPct: Double,
    val pictograph: Pictograph,
    val severity: Severity,
)
