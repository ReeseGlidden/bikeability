package com.bikeability.commute.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import com.bikeability.commute.forecast.ForecastActivity

class CommuteWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    // Two layout buckets: short grids drop the "worst @" line instead of clipping it.
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_COMPACT, SIZE_REGULAR))

    companion object {
        val SIZE_COMPACT = DpSize(250.dp, 110.dp)
        val SIZE_REGULAR = DpSize(250.dp, 170.dp)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetContent(WidgetStateRepo.decode(prefs[WidgetStateRepo.KEY_DATA]))
        }
    }
}

/** Header tap: fetch a fresh forecast without opening settings. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshScheduler.refreshNow(context)
    }
}

private val white = ColorProvider(Color.White)
private val dim = ColorProvider(Color(0xB3FFFFFF))
private val faint = ColorProvider(Color(0x80FFFFFF))

@Composable
internal fun WidgetContent(data: WidgetData?) {
    val compact = LocalSize.current.height < CommuteWidget.SIZE_REGULAR.height
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionStartActivity<ForecastActivity>()),
    ) {
        Header(data)
        Spacer(GlanceModifier.height(6.dp))
        when {
            data == null -> CenteredMessage("Loading forecast…")
            data.morning == null && data.evening == null ->
                CenteredMessage(data.message ?: "No forecast data")
            else -> {
                // Rows are weighted so a short widget compresses them instead
                // of clipping the week strip off the bottom.
                data.morning?.let { WindowRow(it, compact, GlanceModifier.defaultWeight()) }
                Spacer(GlanceModifier.height(6.dp))
                data.evening?.let { WindowRow(it, compact, GlanceModifier.defaultWeight()) }
                if (data.week.isNotEmpty()) {
                    Spacer(GlanceModifier.height(7.dp))
                    WeekStrip(data.week)
                }
            }
        }
    }
}

/**
 * Slim workweek preview. Deliberately low-key — faint labels, translucent
 * chips — so it reads as background context under the two alerting rows.
 *
 * Glance containers allow at most 10 direct children, so the outer Row gets
 * exactly one weighted Box per day (which also divides the width evenly)
 * instead of the days' elements laid out inline.
 */
@Composable
private fun WeekStrip(week: List<DayChip>) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        week.forEach { day ->
            Box(
                modifier = GlanceModifier.defaultWeight(),
                contentAlignment = Alignment.Center,
            ) {
                DayCell(day)
            }
        }
    }
}

@Composable
private fun DayCell(day: DayChip) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(day.label, style = TextStyle(color = faint, fontSize = 10.sp))
        Spacer(GlanceModifier.width(4.dp))
        Column {
            SeverityChip(day.morning)
            Spacer(GlanceModifier.height(2.dp))
            SeverityChip(day.evening)
        }
    }
}

@Composable
private fun SeverityChip(severity: String?) {
    Box(
        modifier = GlanceModifier
            .width(18.dp)
            .height(5.dp)
            .background(ImageProvider(chipDrawable(severity))),
    ) {}
}

@Composable
private fun Header(data: WidgetData?) {
    // The header (full width, including the ↻) refreshes; everything below
    // it inherits the root's open-settings click.
    Row(modifier = GlanceModifier.fillMaxWidth().clickable(actionRunCallback<RefreshAction>())) {
        Text(
            text = data?.dateLabel ?: "Commute",
            style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = data?.let { (if (it.stale) "stale · " else "updated ") + it.updatedLabel } ?: "",
            style = TextStyle(color = if (data?.stale == true) ColorProvider(Color(0xFFFFB74D)) else faint, fontSize = 11.sp),
        )
        Spacer(GlanceModifier.width(5.dp))
        Text("↻", style = TextStyle(color = dim, fontSize = 12.sp))
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
private fun WindowRow(w: WindowUi, compact: Boolean, modifier: GlanceModifier = GlanceModifier) {
    if (compact) CompactWindowRow(w, modifier) else RegularWindowRow(w, modifier)
}

/** One-line variant for short grids: label · icon · temp · feels/category. */
@Composable
private fun CompactWindowRow(w: WindowUi, modifier: GlanceModifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ImageProvider(severityBackground(w.severity)))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            w.windowLabel,
            style = TextStyle(color = white, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.width(64.dp),
        )
        Image(
            provider = ImageProvider(pictographDrawable(w.pictograph)),
            contentDescription = w.pictograph,
            modifier = GlanceModifier.size(20.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            "${w.airTempF}°",
            style = TextStyle(color = white, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            "feels ${w.feelsLikeF}° · ${w.categoryLabel}",
            style = TextStyle(color = dim, fontSize = 12.sp),
        )
    }
}

@Composable
private fun RegularWindowRow(w: WindowUi, modifier: GlanceModifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ImageProvider(severityBackground(w.severity)))
            .padding(horizontal = 10.dp, vertical = 6.dp),
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
            Text(
                "${formatRate(w.peakRateMmHr)} · ${w.peakProbPct}%",
                style = TextStyle(color = dim, fontSize = 10.sp),
            )
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
            }
        }
    }
}

private fun formatRate(mmPerHour: Double): String = "%.1fmm/h".format(mmPerHour)

private fun severityBackground(severity: String): Int = when (severity) {
    "RED" -> R.drawable.row_red
    "YELLOW" -> R.drawable.row_yellow
    else -> R.drawable.row_green
}

private fun chipDrawable(severity: String?): Int = when (severity) {
    "RED" -> R.drawable.chip_red
    "YELLOW" -> R.drawable.chip_yellow
    "GREEN" -> R.drawable.chip_green
    else -> R.drawable.chip_empty
}

internal fun pictographDrawable(picto: String): Int = when (picto) {
    "RAIN" -> R.drawable.ic_rain
    "CLOUDY" -> R.drawable.ic_cloudy
    "PARTLY_CLOUDY" -> R.drawable.ic_partly_cloudy
    else -> R.drawable.ic_sunny
}
