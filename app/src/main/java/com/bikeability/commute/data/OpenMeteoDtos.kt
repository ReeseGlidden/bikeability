package com.bikeability.commute.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    @SerialName("minutely_15") val series: TimeSeriesBlock,
)

/**
 * Open-Meteo returns 15-minutely data as parallel arrays. In regions without
 * a native 15-minute model the values are interpolated from hourly — a
 * graceful floor, never worse than hourly. Fields the API can't compute for
 * a bucket come back as JSON null, hence the nullable elements.
 */
@Serializable
data class TimeSeriesBlock(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature2m: List<Double?>,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: List<Double?>,
    @SerialName("precipitation") val precipitation: List<Double?>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Double?>? = null,
    @SerialName("cloud_cover") val cloudCover: List<Double?>,
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double?>,
    @SerialName("shortwave_radiation") val shortwaveRadiation: List<Double?>,
)
