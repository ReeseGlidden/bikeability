package com.bikeability.commute.config

import com.bikeability.commute.domain.EngineParams
import com.bikeability.commute.domain.TempBoundsF
import com.bikeability.commute.domain.WindCombine
import kotlinx.serialization.Serializable

@Serializable
data class LocationCfg(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val label: String = "",
) {
    val isSet: Boolean get() = lat != 0.0 || lon != 0.0
}

@Serializable
data class WindowCfg(val start: String, val end: String)

@Serializable
data class WindowsCfg(
    val morning: WindowCfg = WindowCfg("07:15", "08:15"),
    val evening: WindowCfg = WindowCfg("17:00", "18:00"),
)

@Serializable
data class BikeCfg(
    val selfSpeedMph: Double = 16.0,
    val windCombine: String = "worst", // or "quadrature" / "max"
)

@Serializable
data class FeelsLikeCfg(val solarGainK: Double = 0.08)

/**
 * Gear lines plus the ideal balance point. [ideal] is not a band edge: it is
 * the feels-like the worst-hour search measures distance from. [jacket]..[shorts]
 * is the ideal band.
 */
@Serializable
data class TempThresholdsCfg(
    val tooCold: Double = 35.0,
    val gloves: Double = 45.0,
    val jacket: Double = 55.0,
    val ideal: Double = 60.0,
    val shorts: Double = 68.0,
    val tooHot: Double = 82.0,
)

@Serializable
data class PrecipThresholdsCfg(
    val yellowProbPct: Double = 20.0,
    val redRateMmHr: Double = 0.3,
)

@Serializable
data class ThresholdsCfg(
    val tempF: TempThresholdsCfg = TempThresholdsCfg(),
    val precip: PrecipThresholdsCfg = PrecipThresholdsCfg(),
)

@Serializable
data class RefreshCfg(val intervalMinutes: Int = 60)

@Serializable
data class AppConfig(
    val home: LocationCfg = LocationCfg(label = "Home"),
    val work: LocationCfg = LocationCfg(label = "Work"),
    val windows: WindowsCfg = WindowsCfg(),
    val bike: BikeCfg = BikeCfg(),
    val feelsLike: FeelsLikeCfg = FeelsLikeCfg(),
    val thresholds: ThresholdsCfg = ThresholdsCfg(),
    val refresh: RefreshCfg = RefreshCfg(),
) {
    val isConfigured: Boolean get() = home.isSet && work.isSet

    fun toEngineParams(): EngineParams = EngineParams(
        selfSpeedMph = bike.selfSpeedMph,
        windCombine = when (bike.windCombine.lowercase()) {
            "max" -> WindCombine.MAX
            "quadrature" -> WindCombine.QUADRATURE
            else -> WindCombine.WORST_CASE
        },
        solarGainK = feelsLike.solarGainK,
        idealPivotF = thresholds.tempF.ideal,
        tempBoundsF = TempBoundsF(
            tooCold = thresholds.tempF.tooCold,
            gloves = thresholds.tempF.gloves,
            jacket = thresholds.tempF.jacket,
            shorts = thresholds.tempF.shorts,
            tooHot = thresholds.tempF.tooHot,
        ),
        yellowProbPct = thresholds.precip.yellowProbPct,
        redRateMmHr = thresholds.precip.redRateMmHr,
    )
}
