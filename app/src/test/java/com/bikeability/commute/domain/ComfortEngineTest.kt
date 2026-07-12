package com.bikeability.commute.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ComfortEngineTest {

    private val params = EngineParams() // spec defaults: 16 mph, quadrature, solarGainK 0.08

    private fun sample(
        time: LocalDateTime = LocalDateTime.of(2026, 7, 10, 7, 0),
        tempC: Double = 20.0,
        rh: Double = 50.0,
        windMs: Double = 0.0,
        shortwave: Double = 0.0,
        precipRate: Double = 0.0,
        precipProb: Double = 0.0,
        cloud: Double = 0.0,
    ) = WeatherSample(time, tempC, rh, windMs, shortwave, precipRate, precipProb, cloud)

    // ---- §2.9 golden case 1: warm, humid, sunny, light wind ----

    @Test
    fun `golden warm humid sunny`() {
        val bd = apparentTemperature(24.0, 60.0, 3.0, 700.0, params)
        assertEquals(7.756, effectiveWindMs(16.0, 3.0, WindCombine.QUADRATURE), 0.001)
        assertEquals(17.85, vapourPressureHpa(24.0, 60.0), 0.01)
        assertEquals(22.74, bd.apparentTempC, 0.01)
        val feelsF = cToF(bd.apparentTempC)
        assertEquals(72.9, feelsF, 0.1)
        assertEquals(Category.SHORTS, categoryFor(feelsF, params.tempBoundsF))
    }

    @Test
    fun `golden case 1 shade check - sun adds about 4F`() {
        val sun = cToF(apparentTemperature(24.0, 60.0, 3.0, 700.0, params).apparentTempC)
        val shade = cToF(apparentTemperature(24.0, 60.0, 3.0, 0.0, params).apparentTempC)
        assertEquals(69.0, shade, 0.1)
        assertEquals(3.9, sun - shade, 0.2)
    }

    // ---- §2.9 golden case 2: cold, breezy winter morning ----

    @Test
    fun `golden cold breezy morning`() {
        val bd = apparentTemperature(3.0, 70.0, 5.0, 100.0, params)
        assertEquals(8.727, effectiveWindMs(16.0, 5.0, WindCombine.QUADRATURE), 0.001)
        assertEquals(5.30, vapourPressureHpa(3.0, 70.0), 0.01)
        assertEquals(-5.22, bd.apparentTempC, 0.01)
        val feelsF = cToF(bd.apparentTempC)
        assertEquals(22.6, feelsF, 0.1)
        assertEquals(Category.TOO_COLD, categoryFor(feelsF, params.tempBoundsF))
    }

    // ---- effective wind ----

    @Test
    fun `calm day effective wind is exactly self speed`() {
        assertEquals(16.0 * MPH_TO_MS, effectiveWindMs(16.0, 0.0, WindCombine.QUADRATURE), 1e-9)
    }

    @Test
    fun `light ambient wind barely moves effective wind`() {
        // 8 mph ambient at 16 mph self → ~17.9 mph effective
        val effectiveMph = effectiveWindMs(16.0, 8.0 * MPH_TO_MS, WindCombine.QUADRATURE) / MPH_TO_MS
        assertEquals(17.9, effectiveMph, 0.05)
    }

    @Test
    fun `max combine mode takes the larger of self and ambient`() {
        assertEquals(10.0, effectiveWindMs(16.0, 10.0, WindCombine.MAX), 1e-9)
        assertEquals(16.0 * MPH_TO_MS, effectiveWindMs(16.0, 2.0, WindCombine.MAX), 1e-9)
    }

    // ---- stopped feels-like and worst-case wind ----

    @Test
    fun `stopped feels-like drops the self-generated airflow`() {
        // Golden case 2 conditions: riding feels ~22.6 F, stopped only ambient 5 m/s.
        val stopped = cToF(apparentTemperatureStopped(3.0, 70.0, 5.0, 100.0, params).apparentTempC)
        assertEquals(27.4, stopped, 0.2)
        // Golden case 1: hot sun with little wind relief when stopped.
        val hotStopped = cToF(apparentTemperatureStopped(24.0, 60.0, 3.0, 700.0, params).apparentTempC)
        assertEquals(80.4, hotStopped, 0.2)
    }

    @Test
    fun `aggregate includes the stopped feels-like for the worst bucket`() {
        val result = aggregateWindow(listOf(sample(tempC = 24.0, rh = 60.0, windMs = 3.0, shortwave = 700.0)), params)!!
        // Stopped is hotter than riding in warm sun.
        assertTrue(result.worstHour.stoppedFeelsLikeF > result.worstHour.feelsLikeF)
    }

    @Test
    fun `worst-case wind takes less relief when hot and full chill when cold`() {
        val worst = params.copy(windCombine = WindCombine.WORST_CASE)

        // Hot: MAX combine (less wind, hotter AT) is further from the pivot.
        val hotWorst = apparentTemperature(24.0, 60.0, 3.0, 700.0, worst)
        val hotMax = apparentTemperature(24.0, 60.0, 3.0, 700.0, params.copy(windCombine = WindCombine.MAX))
        assertEquals(hotMax.apparentTempC, hotWorst.apparentTempC, 1e-9)
        assertTrue(hotWorst.apparentTempC > apparentTemperature(24.0, 60.0, 3.0, 700.0, params).apparentTempC)

        // Cold: QUADRATURE (more wind, colder AT) is further from the pivot.
        val coldWorst = apparentTemperature(3.0, 70.0, 5.0, 100.0, worst)
        val coldQuad = apparentTemperature(3.0, 70.0, 5.0, 100.0, params)
        assertEquals(coldQuad.apparentTempC, coldWorst.apparentTempC, 1e-9)
    }

    // ---- categories and severity ----

    @Test
    fun `category gear lines`() {
        // Each threshold is a gear decision line: below jacket → jacket needed,
        // at/above shorts → shorts needed; jacket..shorts is the ideal band.
        val b = params.tempBoundsF
        assertEquals(Category.TOO_COLD, categoryFor(34.9, b))
        assertEquals(Category.GLOVES, categoryFor(35.0, b))
        assertEquals(Category.JACKET, categoryFor(45.0, b))
        assertEquals(Category.IDEAL, categoryFor(55.0, b))
        assertEquals(Category.IDEAL, categoryFor(60.0, b))
        assertEquals(Category.SHORTS, categoryFor(68.0, b))
        assertEquals(Category.TOO_HOT, categoryFor(82.0, b))
    }

    @Test
    fun `ideal balance point is configurable and shifts worst-hour selection`() {
        // Feels-like ≈ 58.6 °F (20 °C air) vs ≈ 72.6 °F (26 °C air), calm, no sun.
        val coolHour = sample(time = LocalDateTime.of(2026, 7, 10, 7, 0), tempC = 20.0)
        val warmHour = sample(time = LocalDateTime.of(2026, 7, 10, 8, 0), tempC = 26.0)

        // Default 60 °F pivot: the warm hour is further away.
        val default = aggregateWindow(listOf(coolHour, warmHour), params)!!
        assertEquals(8, default.worstHour.time.hour)

        // Someone who runs hot pivots at 72 °F: now the cool hour is the outlier.
        val runsHot = aggregateWindow(listOf(coolHour, warmHour), params.copy(idealPivotF = 72.0))!!
        assertEquals(7, runsHot.worstHour.time.hour)
    }

    @Test
    fun `severity ladder`() {
        assertEquals(Severity.GREEN, tempSeverity(Category.IDEAL))
        assertEquals(Severity.GREEN, tempSeverity(Category.JACKET))
        assertEquals(Severity.YELLOW, tempSeverity(Category.GLOVES))
        assertEquals(Severity.YELLOW, tempSeverity(Category.SHORTS))
        assertEquals(Severity.RED, tempSeverity(Category.TOO_COLD))
        assertEquals(Severity.RED, tempSeverity(Category.TOO_HOT))
    }

    @Test
    fun `precip gates`() {
        assertEquals(Severity.GREEN, precipSeverity(19.9, 0.0, params))
        assertEquals(Severity.YELLOW, precipSeverity(20.0, 0.0, params))
        assertEquals(Severity.YELLOW, precipSeverity(90.0, 0.29, params))
        assertEquals(Severity.RED, precipSeverity(0.0, 0.3, params))
    }

    // ---- pictograph ----

    @Test
    fun `pictograph from rain gate then cloud bands`() {
        assertEquals(Pictograph.RAIN, pictographFor(0.3, 0.0, params))
        assertEquals(Pictograph.SUNNY, pictographFor(0.0, 24.9, params))
        assertEquals(Pictograph.PARTLY_CLOUDY, pictographFor(0.0, 25.0, params))
        assertEquals(Pictograph.PARTLY_CLOUDY, pictographFor(0.0, 60.0, params))
        assertEquals(Pictograph.CLOUDY, pictographFor(0.0, 60.1, params))
    }

    // ---- window slicing ----

    @Test
    fun `window 715 to 815 overlaps exactly the four 15-minute buckets inside it`() {
        val date = LocalDate.of(2026, 7, 10)
        val buckets = (0 until 24 * 4).map { i ->
            sample(time = LocalDateTime.of(2026, 7, 10, 0, 0).plusMinutes(i * 15L))
        }
        val inWindow = samplesInWindow(buckets, date, LocalTime.of(7, 15), LocalTime.of(8, 15))
        assertEquals(
            listOf("07:15", "07:30", "07:45", "08:00"),
            inWindow.map { "%02d:%02d".format(it.time.hour, it.time.minute) },
        )
    }

    @Test
    fun `bucket width is configurable for coarser data`() {
        val date = LocalDate.of(2026, 7, 10)
        val hours = (5..10).map { h -> sample(time = LocalDateTime.of(2026, 7, 10, h, 0)) }
        val inWindow =
            samplesInWindow(hours, date, LocalTime.of(7, 15), LocalTime.of(8, 15), bucketMinutes = 60)
        assertEquals(listOf(7, 8), inWindow.map { it.time.hour })
    }

    @Test
    fun `next occurrence is today while ahead and tomorrow once passed`() {
        val now = LocalDateTime.of(2026, 7, 10, 8, 0)
        assertEquals(
            LocalDateTime.of(2026, 7, 10, 16, 40),
            nextOccurrence(now, LocalTime.of(16, 40)),
        )
        assertEquals(
            LocalDateTime.of(2026, 7, 11, 6, 55),
            nextOccurrence(now, LocalTime.of(6, 55)),
        )
        // Exactly now → tomorrow (strictly after).
        assertEquals(
            LocalDateTime.of(2026, 7, 11, 8, 0),
            nextOccurrence(now, LocalTime.of(8, 0)),
        )
    }

    @Test
    fun `week strip shows current workweek on weekdays and next week from Saturday`() {
        // Wed Jul 8 2026 → Mon Jul 6 .. Fri Jul 10 (current week)
        val wednesday = LocalDateTime.of(2026, 7, 8, 12, 0)
        assertEquals(
            (6..10).map { LocalDate.of(2026, 7, it) },
            workweekDates(wednesday),
        )
        // Mon Jul 6 → same week
        assertEquals(LocalDate.of(2026, 7, 6), workweekDates(LocalDateTime.of(2026, 7, 6, 6, 0)).first())
        // Sat Jul 11 and Sun Jul 12 → Mon Jul 13 .. Fri Jul 17 (upcoming week)
        val nextWeek = (13..17).map { LocalDate.of(2026, 7, it) }
        assertEquals(nextWeek, workweekDates(LocalDateTime.of(2026, 7, 11, 8, 0)))
        assertEquals(nextWeek, workweekDates(LocalDateTime.of(2026, 7, 12, 8, 0)))
    }

    @Test
    fun `widget shows today until the planning cutover then tomorrow`() {
        val cutover = LocalTime.of(19, 0)
        // Mid-day (even after the morning window passed): still today.
        assertEquals(
            LocalDate.of(2026, 7, 10),
            resolveDisplayDate(LocalDateTime.of(2026, 7, 10, 14, 0), cutover),
        )
        // At and after 7 PM: tomorrow.
        assertEquals(
            LocalDate.of(2026, 7, 11),
            resolveDisplayDate(LocalDateTime.of(2026, 7, 10, 19, 0), cutover),
        )
        assertEquals(
            LocalDate.of(2026, 7, 11),
            resolveDisplayDate(LocalDateTime.of(2026, 7, 10, 23, 30), cutover),
        )
    }

    // ---- aggregation ----

    @Test
    fun `empty window yields null`() {
        assertNull(aggregateWindow(emptyList(), params))
    }

    @Test
    fun `worst hour is the one furthest from 60F either direction`() {
        // Hot afternoon: later hotter hour must win.
        val mild = sample(time = LocalDateTime.of(2026, 7, 10, 17, 0), tempC = 24.0, shortwave = 400.0)
        val hot = sample(time = LocalDateTime.of(2026, 7, 10, 18, 0), tempC = 33.0, shortwave = 500.0)
        val hotResult = aggregateWindow(listOf(mild, hot), params)!!
        assertEquals(18, hotResult.worstHour.time.hour)
        assertTrue(hotResult.worstHour.feelsLikeF > IDEAL_PIVOT_F)

        // Cold morning: the colder hour must win.
        val cold = sample(time = LocalDateTime.of(2026, 1, 10, 7, 0), tempC = 0.0)
        val lessCold = sample(time = LocalDateTime.of(2026, 1, 10, 8, 0), tempC = 5.0)
        val coldResult = aggregateWindow(listOf(cold, lessCold), params)!!
        assertEquals(7, coldResult.worstHour.time.hour)
    }

    @Test
    fun `peak precip is max across the window not the worst hour's value`() {
        val dryButCold = sample(tempC = -5.0, precipRate = 0.0, precipProb = 0.0)
        val wetButMild = sample(
            time = LocalDateTime.of(2026, 7, 10, 8, 0),
            tempC = 15.0, precipRate = 1.2, precipProb = 90.0,
        )
        val result = aggregateWindow(listOf(dryButCold, wetButMild), params)!!
        assertEquals(1.2, result.peakRateMmHr, 1e-9)
        assertEquals(90.0, result.peakProbPct, 1e-9)
        assertEquals(Pictograph.RAIN, result.pictograph)
    }

    @Test
    fun `row severity is max of temp and precip severities`() {
        // Ideal temp but heavy rain → red row ("Ideal · rain 90%").
        val idealWet = sample(tempC = 18.0, rh = 60.0, precipRate = 1.2, precipProb = 90.0)
        val result = aggregateWindow(listOf(idealWet), params)!!
        assertEquals(Category.IDEAL, result.worstHour.category)
        assertEquals(Severity.RED, result.severity)
    }

    @Test
    fun `all-ideal window stays green and unremarkable`() {
        // ~20-21 °C air with the 16 mph wind floor lands feels-like around 59-61 °F.
        val hours = listOf(
            sample(tempC = 20.0, rh = 50.0),
            sample(time = LocalDateTime.of(2026, 7, 10, 8, 0), tempC = 21.0, rh = 50.0),
        )
        val result = aggregateWindow(hours, params)!!
        assertEquals(Category.IDEAL, result.worstHour.category)
        assertEquals(Severity.GREEN, result.severity)
    }

    // ---- endpoint merge ----

    @Test
    fun `merge takes worse worst-hour and max precip from either endpoint`() {
        val home = aggregateWindow(
            listOf(sample(tempC = 0.0, precipProb = 10.0, cloud = 20.0)), params,
        )!!
        val work = aggregateWindow(
            listOf(sample(tempC = 10.0, precipProb = 40.0, precipRate = 0.1, cloud = 80.0)), params,
        )!!
        val merged = mergeEndpoints(home, work, params)
        // Home's 0 °C hour is further from 60 °F than work's 10 °C hour.
        assertEquals(home.worstHour, merged.worstHour)
        assertEquals(40.0, merged.peakProbPct, 1e-9)
        assertEquals(0.1, merged.peakRateMmHr, 1e-9)
        assertEquals(80.0, merged.meanCloudCoverPct, 1e-9)
        assertEquals(Pictograph.CLOUDY, merged.pictograph)
        assertEquals(Severity.RED, merged.severity) // home's too-cold wins
    }

    @Test
    fun `merged severity is at least each endpoint's severity`() {
        val calm = aggregateWindow(listOf(sample(tempC = 17.0)), params)!!
        val rainy = aggregateWindow(listOf(sample(tempC = 17.0, precipRate = 0.5, precipProb = 80.0)), params)!!
        assertEquals(Severity.RED, mergeEndpoints(calm, rainy, params).severity)
        assertEquals(Severity.RED, mergeEndpoints(rainy, calm, params).severity)
    }
}
