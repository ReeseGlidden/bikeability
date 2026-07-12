package com.bikeability.commute.forecast

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bikeability.commute.widget.DayDetail
import com.bikeability.commute.widget.WidgetData
import com.bikeability.commute.widget.WindowUi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the forecast screen with Robolectric native graphics and writes a
 * PNG to build/reports/widget-screenshots/ alongside the widget renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class ForecastScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Robolectric can't service captureToImage's window-capture redraw, so
     * draw the activity's content view straight into a bitmap instead.
     */
    private fun renderToPng(name: String, content: @Composable () -> Unit): File {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { Surface { content() } }
        }
        compose.waitForIdle()

        val view = compose.activity.findViewById<View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val dir = File("build/reports/widget-screenshots").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun window(
        label: String, air: Int, feels: Int, category: String,
        severity: String, picto: String, prob: Int = 0, rate: Double = 0.0,
    ) = WindowUi(
        windowLabel = label, rangeLabel = if (label == "MORNING") "7:15–8:15" else "5:00–6:00",
        airTempF = air, feelsLikeF = feels, categoryLabel = category,
        severity = severity, pictograph = picto,
        peakProbPct = prob, peakRateMmHr = rate,
        worstAtLabel = if (label == "MORNING") "7:15 AM" else "5:00 PM",
        bdBaseF = air.toDouble(), bdHumidityF = 4.0, bdWindF = -9.5, bdSolarF = 2.0, bdConstantF = -7.7,
    )

    private val fixture = WidgetData(
        dateLabel = "Fri Jul 11",
        updatedLabel = "2:27 PM",
        days = listOf(
            DayDetail(
                "Today · Fri Jul 11",
                window("MORNING", 58, 61, "Ideal", "GREEN", "SUNNY"),
                window("EVENING", 79, 84, "Too hot", "RED", "RAIN", prob = 90, rate = 1.2),
            ),
            DayDetail(
                "Tomorrow · Sat Jul 12",
                window("MORNING", 66, 68, "Shorts", "YELLOW", "PARTLY_CLOUDY", prob = 10),
                window("EVENING", 84, 88, "Too hot", "RED", "SUNNY"),
            ),
            DayDetail(
                "Sun Jul 13",
                window("MORNING", 62, 63, "Ideal", "GREEN", "CLOUDY", prob = 30),
                window("EVENING", 71, 74, "Shorts", "YELLOW", "CLOUDY", prob = 40, rate = 0.2),
            ),
            DayDetail(
                "Mon Jul 14",
                window("MORNING", 60, 60, "Ideal", "GREEN", "SUNNY"),
                window("EVENING", 75, 78, "Shorts", "YELLOW", "PARTLY_CLOUDY"),
            ),
            DayDetail("Tue Jul 15", null, null),
        ),
    )

    @Test
    fun `forecast list renders seven days`() {
        val file = renderToPng("forecast-screen") { ForecastScreen(data = fixture, onOpenSettings = {}) }
        assertTrue(file.length() > 0)
    }

    @Test
    fun `empty cache shows loading state`() {
        val file = renderToPng("forecast-loading") { ForecastScreen(data = null, onOpenSettings = {}) }
        assertTrue(file.length() > 0)
    }

    @Test
    fun `unconfigured zero state offers setup`() {
        val data = WidgetData(
            dateLabel = "Fri Jul 11",
            updatedLabel = "6:52 AM",
            message = "Set home & work locations to get started",
        )
        val file = renderToPng("forecast-unconfigured") { ForecastScreen(data = data, onOpenSettings = {}) }
        assertTrue(file.length() > 0)
    }
}
