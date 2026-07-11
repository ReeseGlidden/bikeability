package com.bikeability.commute.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the real Glance widget — through the actual RemoteViews translation,
 * which is where layout bugs like the 10-children-per-container truncation
 * live — and writes PNGs to build/reports/widget-screenshots/ for visual
 * inspection. Run via: ./gradlew :app:testDebugUnitTest --tests '*Screenshot*'
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class WidgetScreenshotTest {

    private val summerDay = WidgetData(
        dateLabel = "Fri Jul 11",
        updatedLabel = "6:52 AM",
        morning = WindowUi(
            windowLabel = "MORNING", rangeLabel = "7:15–8:15",
            airTempF = 58, feelsLikeF = 61, categoryLabel = "Ideal",
            severity = "GREEN", pictograph = "SUNNY",
            peakProbPct = 0, peakRateMmHr = 0.0, worstAtLabel = "7:15 AM",
            bdBaseF = 58.0, bdHumidityF = 4.1, bdWindF = -9.2, bdSolarF = 2.9, bdConstantF = -7.7,
        ),
        evening = WindowUi(
            windowLabel = "EVENING", rangeLabel = "5:00–6:00",
            airTempF = 79, feelsLikeF = 84, categoryLabel = "Too hot",
            severity = "RED", pictograph = "RAIN",
            peakProbPct = 90, peakRateMmHr = 1.2, worstAtLabel = "5:00 PM",
            bdBaseF = 79.0, bdHumidityF = 11.0, bdWindF = -9.6, bdSolarF = 3.6, bdConstantF = -7.7,
        ),
        week = listOf(
            DayChip("M", "GREEN", "YELLOW"),
            DayChip("Tu", "GREEN", "RED"),
            DayChip("W", "YELLOW", "RED"),
            DayChip("Th", "GREEN", "YELLOW"),
            DayChip("F", "GREEN", "GREEN"),
        ),
    )

    @Test
    fun `summer day with full week strip`() {
        val file = renderToPng(summerDay, "summer-week-strip")
        assertTrue("screenshot written", file.length() > 0)
    }

    @Test
    fun `shoulder season day`() {
        val data = WidgetData(
            dateLabel = "Tue Oct 20",
            updatedLabel = "6:40 AM",
            morning = WindowUi(
                windowLabel = "MORNING", rangeLabel = "7:15–8:15",
                airTempF = 44, feelsLikeF = 38, categoryLabel = "Gloves",
                severity = "YELLOW", pictograph = "PARTLY_CLOUDY",
                peakProbPct = 10, peakRateMmHr = 0.0, worstAtLabel = "7:15 AM",
                bdBaseF = 44.0, bdHumidityF = 3.1, bdWindF = -10.8, bdSolarF = 1.4, bdConstantF = -7.7,
            ),
            evening = WindowUi(
                windowLabel = "EVENING", rangeLabel = "5:00–6:00",
                airTempF = 55, feelsLikeF = 51, categoryLabel = "Jacket",
                severity = "GREEN", pictograph = "CLOUDY",
                peakProbPct = 15, peakRateMmHr = 0.1, worstAtLabel = "6:00 PM",
                bdBaseF = 55.0, bdHumidityF = 3.9, bdWindF = -9.9, bdSolarF = 0.6, bdConstantF = -7.7,
            ),
            week = listOf(
                DayChip("M", "YELLOW", "GREEN"),
                DayChip("Tu", "YELLOW", "GREEN"),
                DayChip("W", "RED", "YELLOW"),
                DayChip("Th", "YELLOW", "GREEN"),
                DayChip("F", "GREEN", "GREEN"),
            ),
        )
        val file = renderToPng(data, "shoulder-week-strip")
        assertTrue(file.length() > 0)
    }

    @Test
    fun `compact size drops the worst-at line but keeps the strip`() {
        val file = renderToPng(summerDay, "summer-compact", DpSize(320.dp, 130.dp))
        assertTrue(file.length() > 0)
    }

    @Test
    fun `stale forecast with partially covered week`() {
        val data = summerDay.copy(
            stale = true,
            week = summerDay.week.mapIndexed { i, d ->
                if (i < 2) d.copy(morning = null, evening = null) else d
            },
        )
        val file = renderToPng(data, "stale-partial-week")
        assertTrue(file.length() > 0)
    }

    @Test
    fun `unconfigured shows setup message`() {
        val data = WidgetData(
            dateLabel = "Fri Jul 11",
            updatedLabel = "6:52 AM",
            message = "Tap to set home & work locations",
        )
        val file = renderToPng(data, "unconfigured")
        assertTrue(file.length() > 0)
    }

    private fun renderToPng(
        data: WidgetData?,
        name: String,
        size: DpSize = DpSize(320.dp, 180.dp),
    ): File = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remoteViews = GlanceRemoteViews()
            .compose(context = context, size = size) { WidgetContent(data) }
            .remoteViews

        val parent = FrameLayout(context)
        val view = remoteViews.apply(context, parent)

        val density = context.resources.displayMetrics.density
        val widthPx = (size.width.value * density).toInt()
        val heightPx = (size.height.value * density).toInt()
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val dir = File("build/reports/widget-screenshots").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }
}
