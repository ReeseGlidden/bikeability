package com.bikeability.commute.settings

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bikeability.commute.config.AppConfig
import com.bikeability.commute.config.BikeCfg
import com.bikeability.commute.config.ConfigStore
import com.bikeability.commute.config.FeelsLikeCfg
import com.bikeability.commute.config.LocationCfg
import com.bikeability.commute.config.PrecipThresholdsCfg
import com.bikeability.commute.config.RefreshCfg
import com.bikeability.commute.config.TempThresholdsCfg
import com.bikeability.commute.config.ThresholdsCfg
import com.bikeability.commute.config.WindowCfg
import com.bikeability.commute.config.WindowsCfg
import com.bikeability.commute.widget.RefreshScheduler
import com.bikeability.commute.widget.WidgetStateRepo
import com.bikeability.commute.widget.WindowUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var loaded by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(FormState()) }
    val widgetData by WidgetStateRepo.flow(context).collectAsState(initial = null)

    LaunchedEffect(Unit) {
        form = FormState.from(ConfigStore.read(context))
        loaded = true
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (!loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Commute Weather", style = MaterialTheme.typography.headlineSmall)

            SectionTitle("Locations")
            EndpointEditor("Home", form.home, { form = form.copy(home = it) }, snackbar)
            EndpointEditor("Work", form.work, { form = form.copy(work = it) }, snackbar)

            SectionTitle("Commute windows")
            WindowEditor("Morning", form.morningStart, form.morningEnd) { s, e ->
                form = form.copy(morningStart = s, morningEnd = e)
            }
            WindowEditor("Evening", form.eveningStart, form.eveningEnd) { s, e ->
                form = form.copy(eveningStart = s, eveningEnd = e)
            }

            SectionTitle("Bike")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Cruising speed (mph)", form.selfSpeedMph, Modifier.weight(1f)) {
                    form = form.copy(selfSpeedMph = it)
                }
                OutlinedButton(
                    onClick = {
                        form = form.copy(
                            windCombine = if (form.windCombine == "quadrature") "max" else "quadrature",
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) { Text("Wind: ${form.windCombine}") }
            }

            SectionTitle("Feels-like calibration")
            NumField("Solar gain K (sun weighting)", form.solarGainK) {
                form = form.copy(solarGainK = it)
            }

            SectionTitle("Comfort thresholds (°F feels-like)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Too cold below", form.tooCold, Modifier.weight(1f)) { form = form.copy(tooCold = it) }
                NumField("Gloves below", form.gloves, Modifier.weight(1f)) { form = form.copy(gloves = it) }
                NumField("Jacket below", form.jacket, Modifier.weight(1f)) { form = form.copy(jacket = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Ideal (balance)", form.ideal, Modifier.weight(1f)) { form = form.copy(ideal = it) }
                NumField("Shorts above", form.shorts, Modifier.weight(1f)) { form = form.copy(shorts = it) }
                NumField("Too hot above", form.tooHot, Modifier.weight(1f)) { form = form.copy(tooHot = it) }
            }
            Text(
                "\"Ideal\" is the balance point the worst hour is measured against; the others are gear lines.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("Precipitation gates")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Yellow ≥ prob %", form.yellowProbPct, Modifier.weight(1f)) {
                    form = form.copy(yellowProbPct = it)
                }
                NumField("Red ≥ rate mm/h", form.redRateMmHr, Modifier.weight(1f)) {
                    form = form.copy(redRateMmHr = it)
                }
            }

            SectionTitle("Refresh")
            NumField("Interval (minutes, min 15)", form.refreshMinutes) {
                form = form.copy(refreshMinutes = it)
            }

            Button(
                onClick = {
                    scope.launch {
                        val config = form.toConfigOrNull()
                        if (config == null) {
                            snackbar.showSnackbar("Check fields — some values didn't parse")
                        } else {
                            ConfigStore.write(context, config)
                            RefreshScheduler.scheduleAll(context, config)
                            RefreshScheduler.refreshNow(context)
                            snackbar.showSnackbar("Saved — refreshing forecast")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save & refresh") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Calibration readout (worst hour)")
            val data = widgetData
            if (data?.morning == null && data?.evening == null) {
                Text(
                    "No forecast computed yet — add the widget or save to trigger a refresh.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                data.morning?.let { BreakdownCard(it) }
                data.evening?.let { BreakdownCard(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EndpointEditor(
    name: String,
    loc: EndpointForm,
    onChange: (EndpointForm) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = loc.address,
                onValueChange = { onChange(loc.copy(address = it)) },
                label = { Text("$name address") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val hit = geocode(context, loc.address)
                        if (hit == null) {
                            snackbar.showSnackbar("No match for \"${loc.address}\"")
                        } else {
                            onChange(loc.copy(lat = "%.5f".format(hit.first), lon = "%.5f".format(hit.second)))
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterVertically),
            ) { Text("Find") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = loc.lat,
                onValueChange = { onChange(loc.copy(lat = it)) },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = loc.lon,
                onValueChange = { onChange(loc.copy(lon = it)) },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun WindowEditor(name: String, start: String, end: String, onChange: (String, String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = start,
            onValueChange = { onChange(it, end) },
            label = { Text("$name start (HH:MM)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = end,
            onValueChange = { onChange(start, it) },
            label = { Text("$name end (HH:MM)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
}

@Composable
private fun NumField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
private fun BreakdownCard(w: WindowUi) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            "${w.windowLabel} — feels ${w.feelsLikeF}° (${w.categoryLabel}) @ ${w.worstAtLabel}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "base %.1f°  humidity %+.1f°  wind %+.1f°  sun %+.1f°  const %+.1f°"
                .format(w.bdBaseF, w.bdHumidityF, w.bdWindF, w.bdSolarF, w.bdConstantF),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private suspend fun geocode(context: Context, query: String): Pair<Double, Double>? =
    withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context).getFromLocationName(query, 1)
                ?.firstOrNull()
                ?.let { it.latitude to it.longitude }
        }.getOrNull()
    }

data class EndpointForm(
    val label: String = "",
    val address: String = "",
    val lat: String = "",
    val lon: String = "",
)

data class FormState(
    val home: EndpointForm = EndpointForm(label = "Home"),
    val work: EndpointForm = EndpointForm(label = "Work"),
    val morningStart: String = "07:15",
    val morningEnd: String = "08:15",
    val eveningStart: String = "17:00",
    val eveningEnd: String = "18:00",
    val selfSpeedMph: String = "16.0",
    val windCombine: String = "quadrature",
    val solarGainK: String = "0.08",
    val tooCold: String = "35",
    val gloves: String = "45",
    val jacket: String = "55",
    val ideal: String = "60",
    val shorts: String = "68",
    val tooHot: String = "82",
    val yellowProbPct: String = "20",
    val redRateMmHr: String = "0.3",
    val refreshMinutes: String = "60",
) {
    fun toConfigOrNull(): AppConfig? {
        fun num(s: String): Double? = s.trim().toDoubleOrNull()
        fun time(s: String): String? =
            runCatching { LocalTime.parse(s.trim()) }.getOrNull()?.toString()

        // Ideal must sit inside the jacket..shorts band, and gear lines must be ordered.
        val bounds = listOf(num(tooCold), num(gloves), num(jacket), num(ideal), num(shorts), num(tooHot))
        if (bounds.any { it == null } || bounds.filterNotNull() != bounds.filterNotNull().sorted()) return null

        return AppConfig(
            home = LocationCfg(num(home.lat) ?: return null, num(home.lon) ?: return null, home.label.ifBlank { "Home" }),
            work = LocationCfg(num(work.lat) ?: return null, num(work.lon) ?: return null, work.label.ifBlank { "Work" }),
            windows = WindowsCfg(
                morning = WindowCfg(time(morningStart) ?: return null, time(morningEnd) ?: return null),
                evening = WindowCfg(time(eveningStart) ?: return null, time(eveningEnd) ?: return null),
            ),
            bike = BikeCfg(
                selfSpeedMph = num(selfSpeedMph) ?: return null,
                windCombine = windCombine,
            ),
            feelsLike = FeelsLikeCfg(solarGainK = num(solarGainK) ?: return null),
            thresholds = ThresholdsCfg(
                tempF = TempThresholdsCfg(bounds[0]!!, bounds[1]!!, bounds[2]!!, bounds[3]!!, bounds[4]!!, bounds[5]!!),
                precip = PrecipThresholdsCfg(
                    yellowProbPct = num(yellowProbPct) ?: return null,
                    redRateMmHr = num(redRateMmHr) ?: return null,
                ),
            ),
            refresh = RefreshCfg(intervalMinutes = num(refreshMinutes)?.toInt() ?: return null),
        )
    }

    companion object {
        fun from(c: AppConfig): FormState = FormState(
            home = EndpointForm(c.home.label, "", if (c.home.isSet) c.home.lat.toString() else "", if (c.home.isSet) c.home.lon.toString() else ""),
            work = EndpointForm(c.work.label, "", if (c.work.isSet) c.work.lat.toString() else "", if (c.work.isSet) c.work.lon.toString() else ""),
            morningStart = c.windows.morning.start,
            morningEnd = c.windows.morning.end,
            eveningStart = c.windows.evening.start,
            eveningEnd = c.windows.evening.end,
            selfSpeedMph = c.bike.selfSpeedMph.toString(),
            windCombine = c.bike.windCombine,
            solarGainK = c.feelsLike.solarGainK.toString(),
            tooCold = c.thresholds.tempF.tooCold.toString(),
            gloves = c.thresholds.tempF.gloves.toString(),
            jacket = c.thresholds.tempF.jacket.toString(),
            ideal = c.thresholds.tempF.ideal.toString(),
            shorts = c.thresholds.tempF.shorts.toString(),
            tooHot = c.thresholds.tempF.tooHot.toString(),
            yellowProbPct = c.thresholds.precip.yellowProbPct.toString(),
            redRateMmHr = c.thresholds.precip.redRateMmHr.toString(),
            refreshMinutes = c.refresh.intervalMinutes.toString(),
        )
    }
}
