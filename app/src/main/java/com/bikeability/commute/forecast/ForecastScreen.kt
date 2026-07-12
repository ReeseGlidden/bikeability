package com.bikeability.commute.forecast

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeability.commute.widget.DayDetail
import com.bikeability.commute.widget.WidgetData
import com.bikeability.commute.widget.WindowUi
import com.bikeability.commute.widget.pictographDrawable

private val white = Color.White
private val dim = Color(0xB3FFFFFF)
private val faint = Color(0x80FFFFFF)

@Composable
fun ForecastScreen(data: WidgetData?, onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Edge-to-edge is enforced on target SDK 35; keep the
                    // title clear of the status bar.
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Commute Weather", style = MaterialTheme.typography.titleLarge)
                    data?.let {
                        Text(
                            (if (it.stale) "stale · " else "updated ") + it.updatedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.stale) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        },
    ) { padding ->
        if (data == null || data.days.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (data?.message != null) {
                        // Zero state: not configured yet (or no data and no cache).
                        Text(
                            data.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onOpenSettings) { Text("Set up locations") }
                    } else {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading forecast…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(data.days) { day -> DayCard(day) }
            }
        }
    }
}

@Composable
private fun DayCard(day: DayDetail) {
    Column {
        Text(
            day.title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (day.morning == null && day.evening == null) {
            Text(
                "No forecast yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            day.morning?.let { WindowCard(it) }
            if (day.morning != null && day.evening != null) Spacer(Modifier.height(6.dp))
            day.evening?.let { WindowCard(it) }
        }
    }
}

/** Same anatomy and tints as a widget row, in regular Compose. */
@Composable
private fun WindowCard(w: WindowUi) {
    val tint = severityColor(w.severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                // Severity tint feathers out into neutral slate before the
                // right edge, with a slight diagonal tilt (same as the widget).
                drawRect(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to tint,
                            0.12f to tint,
                            0.9f to slate,
                            1f to slate,
                        ),
                        start = Offset(0f, size.height * 0.25f),
                        end = Offset(size.width * 0.88f, size.height * 0.85f),
                    ),
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(72.dp)) {
            Text(w.windowLabel, color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(w.rangeLabel, color = dim, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(pictographDrawable(w.pictograph)),
                contentDescription = w.pictograph,
                modifier = Modifier.size(26.dp),
            )
            Text("%.1fmm · %d%%".format(w.peakRateMmHr, w.peakProbPct), color = dim, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        Text("${w.airTempF}°", color = white, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("feels ${w.feelsLikeF}°", color = dim, fontSize = 12.sp)
            Text(w.categoryLabel, color = white, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("worst @ ${w.worstAtLabel}", color = faint, fontSize = 10.sp)
        }
    }
}

private val slate = Color(0xFF262B30)

private fun severityColor(severity: String): Color = when (severity) {
    "RED" -> Color(0xFF5C2323)
    "YELLOW" -> Color(0xFF5C4E1A)
    else -> Color(0xFF1E4D33)
}
