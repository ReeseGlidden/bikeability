package com.bikeability.commute.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bikeability.commute.R
import com.bikeability.commute.settings.SettingsActivity

class CommuteWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetContent(WidgetStateRepo.decode(prefs[WidgetStateRepo.KEY_DATA]))
        }
    }
}

private val white = ColorProvider(Color.White)
private val dim = ColorProvider(Color(0xB3FFFFFF))
private val faint = ColorProvider(Color(0x80FFFFFF))

@Composable
private fun WidgetContent(data: WidgetData?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionStartActivity<SettingsActivity>()),
    ) {
        Header(data)
        Spacer(GlanceModifier.height(6.dp))
        when {
            data == null -> CenteredMessage("Loading forecast…")
            data.morning == null && data.evening == null ->
                CenteredMessage(data.message ?: "No forecast data")
            else -> {
                data.morning?.let { WindowRow(it) }
                Spacer(GlanceModifier.height(6.dp))
                data.evening?.let { WindowRow(it) }
            }
        }
    }
}

@Composable
private fun Header(data: WidgetData?) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = data?.dateLabel ?: "Commute",
            style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = data?.let { (if (it.stale) "stale · " else "updated ") + it.updatedLabel } ?: "",
            style = TextStyle(color = if (data?.stale == true) ColorProvider(Color(0xFFFFB74D)) else faint, fontSize = 11.sp),
        )
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = TextStyle(color = dim, fontSize = 13.sp))
    }
}

@Composable
private fun WindowRow(w: WindowUi) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(severityBackground(w.severity)))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.width(64.dp)) {
            Text(
                w.windowLabel,
                style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            Text(w.rangeLabel, style = TextStyle(color = dim, fontSize = 11.sp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                provider = ImageProvider(pictographDrawable(w.pictograph)),
                contentDescription = w.pictograph,
                modifier = GlanceModifier.size(26.dp),
            )
            Text(formatRate(w.peakRateMmHr), style = TextStyle(color = dim, fontSize = 10.sp))
            Text("${w.peakProbPct}%", style = TextStyle(color = dim, fontSize = 10.sp))
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${w.airTempF}°",
                style = TextStyle(color = white, fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(6.dp))
            Column {
                Text("feels ${w.feelsLikeF}°", style = TextStyle(color = dim, fontSize = 12.sp))
                Text(
                    w.categoryLabel,
                    style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Text("worst @ ${w.worstAtLabel}", style = TextStyle(color = faint, fontSize = 10.sp))
            }
        }
    }
}

private fun formatRate(mm: Double): String = "%.1fmm".format(mm)

private fun severityBackground(severity: String): Int = when (severity) {
    "RED" -> R.drawable.row_red
    "YELLOW" -> R.drawable.row_yellow
    else -> R.drawable.row_green
}

private fun pictographDrawable(picto: String): Int = when (picto) {
    "RAIN" -> R.drawable.ic_rain
    "CLOUDY" -> R.drawable.ic_cloudy
    "PARTLY_CLOUDY" -> R.drawable.ic_partly_cloudy
    else -> R.drawable.ic_sunny
}
