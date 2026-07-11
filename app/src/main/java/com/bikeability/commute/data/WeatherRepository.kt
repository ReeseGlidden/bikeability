package com.bikeability.commute.data

import com.bikeability.commute.config.AppConfig
import com.bikeability.commute.domain.HourSample
import java.time.LocalDateTime

class WeatherRepository(private val client: OpenMeteoClient = OpenMeteoClient()) {

    /** Fetch both endpoints in one request → (home samples, work samples). */
    suspend fun fetchBoth(config: AppConfig): Pair<List<HourSample>, List<HourSample>> {
        val responses = client.fetch(
            listOf(
                config.home.lat to config.home.lon,
                config.work.lat to config.work.lon,
            ),
        )
        if (responses.size < 2) {
            throw OpenMeteoException("Expected 2 forecast blocks, got ${responses.size}")
        }
        return responses[0].toSamples() to responses[1].toSamples()
    }
}

/**
 * Zip Open-Meteo's parallel hourly arrays into engine samples. Hours with a
 * missing core reading are dropped; a missing precip probability (common at
 * the forecast horizon) degrades to 0 rather than losing the hour.
 */
fun ForecastResponse.toSamples(): List<HourSample> {
    val h = hourly
    return h.time.indices.mapNotNull { i ->
        val tempC = h.temperature2m.getOrNull(i) ?: return@mapNotNull null
        val rh = h.relativeHumidity2m.getOrNull(i) ?: return@mapNotNull null
        val wind = h.windSpeed10m.getOrNull(i) ?: return@mapNotNull null
        val shortwave = h.shortwaveRadiation.getOrNull(i) ?: 0.0
        val precip = h.precipitation.getOrNull(i) ?: 0.0
        val prob = h.precipitationProbability?.getOrNull(i) ?: 0.0
        val cloud = h.cloudCover.getOrNull(i) ?: 0.0
        HourSample(
            time = LocalDateTime.parse(h.time[i]),
            tempC = tempC,
            relHumidityPct = rh,
            ambientWindMs = wind,
            shortwaveWm2 = shortwave,
            precipMm = precip,
            precipProbPct = prob,
            cloudCoverPct = cloud,
        )
    }
}
