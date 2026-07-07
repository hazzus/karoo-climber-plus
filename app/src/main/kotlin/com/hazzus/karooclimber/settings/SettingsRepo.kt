package com.hazzus.karooclimber.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepo(private val context: Context) {

    private object Keys {
        val overlayEnabled = booleanPreferencesKey("overlay_enabled")
        val paletteId = stringPreferencesKey("palette_id")
        val baseMode = stringPreferencesKey("base_mode")
        val previewWindowsKm = stringPreferencesKey("preview_windows_km")
        val triggerDistanceM = doublePreferencesKey("trigger_distance_m")
        val anchor = stringPreferencesKey("anchor")
        val heightPercent = intPreferencesKey("height_percent")
        val opacityPercent = intPreferencesKey("opacity_percent")
        val demoMode = booleanPreferencesKey("demo_mode")
        val fullFields = stringPreferencesKey("full_fields")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val defaults = Settings()
        Settings(
            overlayEnabled = p[Keys.overlayEnabled] ?: defaults.overlayEnabled,
            paletteId = p[Keys.paletteId] ?: defaults.paletteId,
            baseMode = p[Keys.baseMode]?.let { runCatching { BaseMode.valueOf(it) }.getOrNull() }
                ?: defaults.baseMode,
            previewWindowsKm = p[Keys.previewWindowsKm]?.parseWindows() ?: defaults.previewWindowsKm,
            triggerDistanceM = p[Keys.triggerDistanceM] ?: defaults.triggerDistanceM,
            anchor = p[Keys.anchor]?.let { runCatching { OverlayAnchor.valueOf(it) }.getOrNull() }
                ?: defaults.anchor,
            heightPercent = (p[Keys.heightPercent] ?: defaults.heightPercent).coerceIn(15, 100),
            opacityPercent = (p[Keys.opacityPercent] ?: defaults.opacityPercent).coerceIn(75, 100),
            demoMode = p[Keys.demoMode] ?: defaults.demoMode,
            fullFields = p[Keys.fullFields]?.parseFields() ?: defaults.fullFields,
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val defaults = Settings()
            val current = Settings(
                overlayEnabled = p[Keys.overlayEnabled] ?: defaults.overlayEnabled,
                paletteId = p[Keys.paletteId] ?: defaults.paletteId,
                baseMode = p[Keys.baseMode]?.let { runCatching { BaseMode.valueOf(it) }.getOrNull() }
                    ?: defaults.baseMode,
                previewWindowsKm = p[Keys.previewWindowsKm]?.parseWindows()
                    ?: defaults.previewWindowsKm,
                triggerDistanceM = p[Keys.triggerDistanceM] ?: defaults.triggerDistanceM,
                anchor = p[Keys.anchor]?.let { runCatching { OverlayAnchor.valueOf(it) }.getOrNull() }
                    ?: defaults.anchor,
                heightPercent = p[Keys.heightPercent] ?: defaults.heightPercent,
                opacityPercent = p[Keys.opacityPercent] ?: defaults.opacityPercent,
                demoMode = p[Keys.demoMode] ?: defaults.demoMode,
                fullFields = p[Keys.fullFields]?.parseFields() ?: defaults.fullFields,
            )
            val next = transform(current)
            p[Keys.overlayEnabled] = next.overlayEnabled
            p[Keys.paletteId] = next.paletteId
            p[Keys.baseMode] = next.baseMode.name
            p[Keys.previewWindowsKm] = next.previewWindowsKm.joinToString(",")
            p[Keys.triggerDistanceM] = next.triggerDistanceM
            p[Keys.anchor] = next.anchor.name
            p[Keys.heightPercent] = next.heightPercent
            p[Keys.opacityPercent] = next.opacityPercent
            p[Keys.demoMode] = next.demoMode
            p[Keys.fullFields] = next.fullFields.joinToString(",") { it.name }
        }
    }

    private fun String.parseWindows(): List<Double>? =
        split(',')
            .mapNotNull { it.trim().toDoubleOrNull() }
            .filter { it > 0 }
            .sorted()
            .takeIf { it.isNotEmpty() }

    private fun String.parseFields(): List<ClimbField>? =
        split(',')
            .mapNotNull { name -> runCatching { ClimbField.valueOf(name.trim()) }.getOrNull() }
            .takeIf { it.size == 4 }
}
