package dev.jacobv.undertow

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.jacobv.undertow.data.Prefs
import dev.jacobv.undertow.data.StatsStore
import dev.jacobv.undertow.data.TargetApp
import dev.jacobv.undertow.service.ScrollWatcherService

private val Accent = Color(0xFF4FC3F7)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val stats = StatsStore(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                Surface(Modifier.fillMaxSize()) {
                    HomeScreen(
                        prefs = prefs,
                        stats = stats,
                        openAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    prefs: Prefs,
    stats: StatsStore,
    openAccessibilitySettings: () -> Unit,
) {
    // Re-read service state and stats every time the app comes back to the foreground.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val serviceOn = remember(refresh) { ScrollWatcherService.running }
    val today = remember(refresh) { stats.today() }
    var thresholdMin by remember { mutableIntStateOf(prefs.thresholdMinutes) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Undertow", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Catches you before the feed pulls you under",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (serviceOn) "● Watching" else "○ Not running",
                        color = if (serviceOn) Accent else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (!serviceOn) {
                    Text(
                        "Undertow needs the accessibility permission to see scroll " +
                            "events in other apps. Enable \"Undertow scroll watcher\" in " +
                            "the list, then come back.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = openAccessibilitySettings) {
                        Text("Open accessibility settings")
                    }
                } else {
                    Text(
                        "Scroll sessions in your chosen apps are being timed. " +
                            "Everything stays on this device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Interrupt after", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = thresholdMin.toFloat(),
                onValueChange = {
                    thresholdMin = it.toInt().coerceIn(1, 15)
                    prefs.thresholdMinutes = thresholdMin
                },
                valueRange = 1f..15f,
                steps = 13,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text("$thresholdMin min", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "of continuous scrolling (a minute of stillness resets the clock)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        Text("Watched apps", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        TargetApp.entries.forEach { app ->
            var enabled by remember { mutableStateOf(prefs.isEnabled(app)) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        prefs.setEnabled(app, it)
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Today", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                if (today.isEmpty()) {
                    Text(
                        "No doomscrolling recorded yet today.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    today.entries.forEachIndexed { i, (app, day) ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${day.doomMs / 60_000} min scrolled · " +
                                "${day.interrupts} interrupts · " +
                                "walked away ${day.walkedAway}×",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
