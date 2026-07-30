package com.hazzus.karooclimber.overlay

import com.hazzus.karooclimber.settings.OverlayAnchor
import com.hazzus.karooclimber.settings.Settings

/** Anchor for a panel of [size]; the full screen is always bottom (keeps the status bar visible). */
fun Settings.anchorFor(size: ViewModeMachine.PanelSize): OverlayAnchor = when (size) {
    ViewModeMachine.PanelSize.CHIP -> chipAnchor
    ViewModeMachine.PanelSize.EXPANDED -> drawerAnchor
    ViewModeMachine.PanelSize.FULL -> OverlayAnchor.BOTTOM
}

/**
 * Tap-to-cycle display mode state:
 *   BASE -> [ALT ->] PREVIEW(x1) -> PREVIEW(x2) -> ... -> BASE
 *
 * BASE renders the settings' base display mode; ALT is the other base rendering
 * (full vs remaining) and is included whenever the caller passes includeAlt.
 * Preview windows where `remaining < X` are skipped; if remaining drops below
 * the active window mid-ride, the mode auto-reverts to BASE.
 * Also tracks the collapsed(chip)/expanded state (gesture toggle).
 */
class ViewModeMachine {

    sealed interface Mode {
        data object Base : Mode

        /** Alternate base rendering (remaining-only), used with BaseMode.BOTH. */
        data object Alt : Mode

        /** Show only the next [windowMeters] of the climb. */
        data class Preview(val windowMeters: Double) : Mode
    }

    /** Overlay window size: permanent chip, drawer, or full screen. */
    enum class PanelSize { CHIP, EXPANDED, FULL }

    var mode: Mode = Mode.Base
        private set

    var size: PanelSize = PanelSize.CHIP
        private set

    /** Cycle to the next applicable mode. [windowsMeters] ascending, [remainingMeters] to top. */
    fun next(includeAlt: Boolean, windowsMeters: List<Double>, remainingMeters: Double) {
        val applicable = windowsMeters.filter { it < remainingMeters }
        mode = when (val m = mode) {
            is Mode.Base ->
                if (includeAlt) Mode.Alt
                else applicable.firstOrNull()?.let { Mode.Preview(it) } ?: Mode.Base
            is Mode.Alt -> applicable.firstOrNull()?.let { Mode.Preview(it) } ?: Mode.Base
            is Mode.Preview -> {
                val nextWindow = applicable.firstOrNull { it > m.windowMeters }
                if (nextWindow != null) Mode.Preview(nextWindow) else Mode.Base
            }
        }
    }

    /** Auto-revert rule: active preview window no longer smaller than what remains. */
    fun onProgress(remainingMeters: Double) {
        val m = mode
        if (m is Mode.Preview && remainingMeters <= m.windowMeters) {
            mode = Mode.Base
        }
    }

    /** One step up: chip -> drawer -> full screen. */
    fun expand() {
        size = when (size) {
            PanelSize.CHIP -> PanelSize.EXPANDED
            else -> PanelSize.FULL
        }
    }

    /** One step down: full screen -> drawer -> chip. */
    fun collapse() {
        size = when (size) {
            PanelSize.FULL -> PanelSize.EXPANDED
            else -> PanelSize.CHIP
        }
    }

    /** New climb became active: reset mode and, if [expand], pop the drawer open. */
    fun onNewClimb(expand: Boolean = true) {
        mode = Mode.Base
        if (expand) size = PanelSize.EXPANDED
    }

    /** Active climb topped/lost: back to the permanent chip. */
    fun onClimbEnded() {
        mode = Mode.Base
        size = PanelSize.CHIP
    }
}
