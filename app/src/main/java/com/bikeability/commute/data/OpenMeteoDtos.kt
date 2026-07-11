package com.bikeability.commute.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val hourly: HourlyBlock,
)

/**
 * Open-Meteo returns hourly data as parallel arrays. Fields the API can't
 * compute for an hour come back as JSON null, hence the nullable elements.
 */
@Serializable
data class HourlyBlock(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature2m: List<Double?>,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: List<Double?>,
    @SerialName("precipitation") val precipitation: List<Double?>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Double?>? = null,
    @SerialName("cloud_cover") val cloudCover: List<Double?>,
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double?>,
    @SerialName("shortwave_radiation") val shortwaveRadiation: List<Double?>,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?>? = null,
)
