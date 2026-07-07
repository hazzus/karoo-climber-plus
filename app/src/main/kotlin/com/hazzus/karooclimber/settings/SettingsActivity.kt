package com.hazzus.karooclimber.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hazzus.karooclimber.palette.GradePalettes
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private lateinit var repo: SettingsRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepo(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    SettingsScreen(
                        repo = repo,
                        modifier = Modifier.padding(padding),
                        canDrawOverlays = { AndroidSettings.canDrawOverlays(this) },
                        requestOverlayPermission = {
                            startActivity(
                                Intent(
                                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    repo: SettingsRepo,
    modifier: Modifier = Modifier,
    canDrawOverlays: () -> Boolean,
    requestOverlayPermission: () -> Unit,
) {
    val settings by repo.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    fun update(transform: (Settings) -> Settings) {
        scope.launch { repo.update(transform) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Climber+", style = MaterialTheme.typography.titleLarge)

        // ---- Overlay ----
        SectionTitle("Overlay")
        SwitchRow("Enabled", settings.overlayEnabled) { on ->
            update { it.copy(overlayEnabled = on) }
        }
        PermissionRow(canDrawOverlays, requestOverlayPermission)

        // ---- Appearance ----
        SectionTitle("Gradient palette")
        GradePalettes.all.forEach { palette ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { update { it.copy(paletteId = palette.id) } },
            ) {
                RadioButton(
                    selected = settings.paletteId == palette.id,
                    onClick = { update { it.copy(paletteId = palette.id) } },
                )
                Text(palette.displayName, Modifier.weight(1f))
                // color band preview strip
                Row {
                    palette.bands.reversed().forEach { (_, color) ->
                        Box(
                            Modifier
                                .size(width = 14.dp, height = 18.dp)
                                .background(Color(color)),
                        )
                    }
                }
            }
        }

        SectionTitle("Base display mode")
        RadioRow("Full climb", settings.baseMode == BaseMode.FULL) {
            update { it.copy(baseMode = BaseMode.FULL) }
        }
        RadioRow("Remaining part only", settings.baseMode == BaseMode.REMAINING) {
            update { it.copy(baseMode = BaseMode.REMAINING) }
        }
        RadioRow("Both (in tap cycle)", settings.baseMode == BaseMode.BOTH) {
            update { it.copy(baseMode = BaseMode.BOTH) }
        }

        // ---- Behavior ----
        SectionTitle("Show before climb: ${settings.triggerDistanceM.toInt()} m")
        Slider(
            value = settings.triggerDistanceM.toFloat(),
            onValueChange = { v ->
                update { it.copy(triggerDistanceM = (v / 50).toInt() * 50.0) }
            },
            valueRange = 100f..2000f,
        )

        SectionTitle("Tap preview windows (km)")
        settings.previewWindowsKm.forEach { window ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${fmt(window)} km", Modifier.weight(1f))
                TextButton(
                    onClick = {
                        update { s ->
                            val next = s.previewWindowsKm - window
                            // keep at least one window
                            if (next.isEmpty()) s else s.copy(previewWindowsKm = next)
                        }
                    },
                    enabled = settings.previewWindowsKm.size > 1,
                ) { Text("✕") }
            }
        }
        var newWindowText by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newWindowText,
                onValueChange = { newWindowText = it },
                placeholder = { Text("km") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                val v = newWindowText.trim().toDoubleOrNull()
                if (v != null && v > 0) {
                    update { s ->
                        s.copy(previewWindowsKm = (s.previewWindowsKm + v).distinct().sorted())
                    }
                    newWindowText = ""
                }
            }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }

        // ---- Position & size ----
        SectionTitle("Position")
        RadioRow("Top", settings.anchor == OverlayAnchor.TOP) {
            update { it.copy(anchor = OverlayAnchor.TOP) }
        }
        RadioRow("Bottom", settings.anchor == OverlayAnchor.BOTTOM) {
            update { it.copy(anchor = OverlayAnchor.BOTTOM) }
        }
        SectionTitle("Height: ${settings.heightPercent}%")
        Slider(
            value = settings.heightPercent.toFloat(),
            onValueChange = { v -> update { it.copy(heightPercent = v.toInt()) } },
            valueRange = 15f..100f,
        )
        SectionTitle("Opacity: ${settings.opacityPercent}%")
        Slider(
            value = settings.opacityPercent.toFloat(),
            onValueChange = { v -> update { it.copy(opacityPercent = v.toInt()) } },
            valueRange = 75f..100f,
        )

        // ---- Full view fields ----
        SectionTitle("Full-screen view fields")
        settings.fullFields.forEachIndexed { index, field ->
            FieldPickerRow(
                label = "Field ${index + 1}",
                selected = field,
            ) { chosen ->
                update { s ->
                    s.copy(fullFields = s.fullFields.toMutableList().also { it[index] = chosen })
                }
            }
        }

        // ---- Debug ----
        SectionTitle("Debug")
        SwitchRow("Demo mode (fake climb, no ride needed)", settings.demoMode) { on ->
            update { it.copy(demoMode = on) }
        }

        HorizontalDivider()
        Text(
            "Palettes adapted from Barberfish (Apache-2.0). " +
                "Overlay window pattern inspired by Ki2 (valterc). " +
                "Built with hammerhead karoo-ext.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FieldPickerRow(
    label: String,
    selected: ClimbField,
    onSelect: (ClimbField) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(selected.label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                ClimbField.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun PermissionRow(canDrawOverlays: () -> Boolean, request: () -> Unit) {
    var granted by remember { mutableStateOf(canDrawOverlays()) }
    LaunchedEffect(Unit) {
        // refresh when returning from the system permission screen
        while (true) {
            granted = canDrawOverlays()
            kotlinx.coroutines.delay(1000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (granted) "Draw-over-apps permission: granted" else "Draw-over-apps permission: MISSING",
            Modifier.weight(1f),
            color = if (granted) Color.Unspecified else MaterialTheme.colorScheme.error,
        )
        if (!granted) {
            Button(onClick = request, shape = RoundedCornerShape(8.dp)) { Text("Grant") }
        }
    }
}

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
