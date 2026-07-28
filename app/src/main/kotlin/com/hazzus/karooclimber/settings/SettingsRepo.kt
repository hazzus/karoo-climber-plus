package com.hazzus.karooclimber.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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
        val autoExpand = booleanPreferencesKey("auto_expand")
        /** Legacy single anchor (pre chip/drawer split); read once to migrate. */
        val anchor = stringPreferencesKey("anchor")
        val chipAnchor = stringPreferencesKey("chip_anchor")
        val drawerAnchor = stringPreferencesKey("drawer_anchor")
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
            autoExpand = p[Keys.autoExpand] ?: defaults.autoExpand,
            chipAnchor = p.anchor(Keys.chipAnchor, defaults.chipAnchor),
            drawerAnchor = p.anchor(Keys.drawerAnchor, defaults.drawerAnchor),
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
                autoExpand = p[Keys.autoExpand] ?: defaults.autoExpand,
                chipAnchor = p.anchor(Keys.chipAnchor, defaults.chipAnchor),
                drawerAnchor = p.anchor(Keys.drawerAnchor, defaults.drawerAnchor),
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
            p[Keys.autoExpand] = next.autoExpand
            p[Keys.chipAnchor] = next.chipAnchor.name
            p[Keys.drawerAnchor] = next.drawerAnchor.name
            p[Keys.heightPercent] = next.heightPercent
            p[Keys.opacityPercent] = next.opacityPercent
            p[Keys.demoMode] = next.demoMode
            p[Keys.fullFields] = next.fullFields.joinToString(",") { it.name }
        }
    }

    /** Resolve an anchor, falling back to the legacy single [Keys.anchor] then [default]. */
    private fun Preferences.anchor(key: Preferences.Key<String>, default: OverlayAnchor): OverlayAnchor {
        val legacy = get(Keys.anchor)?.toAnchor()
        return get(key)?.toAnchor() ?: legacy ?: default
    }

    private fun String.toAnchor(): OverlayAnchor? =
        runCatching { OverlayAnchor.valueOf(this) }.getOrNull()

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
