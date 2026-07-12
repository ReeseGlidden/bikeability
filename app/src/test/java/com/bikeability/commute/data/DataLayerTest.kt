package com.bikeability.commute.data

import com.bikeability.commute.domain.EngineParams
import com.bikeability.commute.domain.aggregateWindow
import com.bikeability.commute.domain.mergeEndpoints
import com.bikeability.commute.domain.samplesInWindow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Parses a captured real Open-Meteo two-point 15-minutely response (see
 * resources/openmeteo_two_points.json) and runs it through the full
 * DTO → samples → aggregate → merge pipeline.
 */
class DataLayerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(): List<ForecastResponse> {
        val raw = javaClass.classLoader!!.getResource("openmeteo_two_points.json")!!.readText()
        return json.decodeFromString(raw)
    }

    @Test
    fun `real two-point response parses into two 8-day 15-minutely blocks`() {
        val responses = load()
        assertEquals(2, responses.size)
        responses.forEach { r ->
            val samples = r.toSamples()
            assertEquals(8 * 96, samples.size)
            samples.forEach { s ->
                assertTrue("humidity in range", s.relHumidityPct in 0.0..100.0)
                assertTrue("cloud in range", s.cloudCoverPct in 0.0..100.0)
                assertTrue("wind non-negative", s.ambientWindMs >= 0.0)
            }
            // 15-minute spacing between consecutive buckets.
            assertEquals(15, java.time.Duration.between(samples[0].time, samples[1].time).toMinutes())
        }
    }

    @Test
    fun `pipeline produces a merged window result from real data`() {
        val responses = load()
        val params = EngineParams()
        val home = responses[0].toSamples()
        val work = responses[1].toSamples()
        val date = home.first().time.toLocalDate()

        val start = LocalTime.of(7, 15)
        val end = LocalTime.of(8, 15)
        val homeWindow = samplesInWindow(home, date, start, end)
        // A one-hour window over 15-minutely data holds four overlapping
        // buckets: 7:15, 7:30, 7:45, and 8:00 (the 8:00 bucket reaches 8:15).
        assertEquals(4, homeWindow.size)
        val homeResult = aggregateWindow(homeWindow, params)
        val workResult = aggregateWindow(samplesInWindow(work, date, start, end), params)
        assertNotNull(homeResult)
        assertNotNull(workResult)

        val merged = mergeEndpoints(homeResult!!, workResult!!, params)
        assertTrue(merged.worstHour.time.toLocalDate() == date)
        assertTrue(merged.worstHour.time.hour in 7..8)
        // Merged precip must be at least each endpoint's.
        assertTrue(merged.peakProbPct >= homeResult.peakProbPct)
        assertTrue(merged.peakProbPct >= workResult.peakProbPct)
    }
}
