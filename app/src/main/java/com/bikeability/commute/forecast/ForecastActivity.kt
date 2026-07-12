package com.bikeability.commute.forecast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bikeability.commute.settings.SettingsActivity
import com.bikeability.commute.widget.RefreshScheduler
import com.bikeability.commute.widget.WidgetStateRepo

class ForecastActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    val data by WidgetStateRepo.flow(this).collectAsState(initial = null)
                    // Converge to fresh in the background; the cache renders instantly.
                    LaunchedEffect(Unit) { RefreshScheduler.refreshNow(this@ForecastActivity) }
                    ForecastScreen(
                        data = data,
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                    )
                }
            }
        }
    }
}
