package com.bikeability.commute.data

import com.bikeability.commute.config.AppConfig
import com.bikeability.commute.domain.WeatherSample
import java.time.LocalDateTime

/** 15-minutely precipitation arrives as mm-per-bucket; ×4 makes it mm/h. */
private const val BUCKETS_PER_HOUR = 4.0

class WeatherRepository(private val client: OpenMeteoClient = OpenMeteoClient()) {

    /** Fetch both endpoints in one request → (home samples, work samples). */
    suspend fun fetchBoth(config: AppConfig): Pair<List<WeatherSample>, List<WeatherSample>> {
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
 * Zip Open-Meteo's parallel arrays into engine samples. Buckets with a
 * missing core reading are dropped; a missing precip probability (common at
 * the forecast horizon) degrades to 0 rather than losing the bucket.
 */
fun ForecastResponse.toSamples(): List<WeatherSample> {
    val s = series
    return s.time.indices.mapNotNull { i ->
        val tempC = s.temperature2m.getOrNull(i) ?: return@mapNotNull null
        val rh = s.relativeHumidity2m.getOrNull(i) ?: return@mapNotNull null
        val wind = s.windSpeed10m.getOrNull(i) ?: return@mapNotNull null
        val shortwave = s.shortwaveRadiation.getOrNull(i) ?: 0.0
        val precipMmPerBucket = s.precipitation.getOrNull(i) ?: 0.0
        val prob = s.precipitationProbability?.getOrNull(i) ?: 0.0
        val cloud = s.cloudCover.getOrNull(i) ?: 0.0
        WeatherSample(
            time = LocalDateTime.parse(s.time[i]),
            tempC = tempC,
            relHumidityPct = rh,
            ambientWindMs = wind,
            shortwaveWm2 = shortwave,
            precipRateMmHr = precipMmPerBucket * BUCKETS_PER_HOUR,
            precipProbPct = prob,
            cloudCoverPct = cloud,
        )
    }
}
