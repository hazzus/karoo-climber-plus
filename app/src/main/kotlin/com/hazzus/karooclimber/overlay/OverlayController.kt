package com.hazzus.karooclimber.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import com.hazzus.karooclimber.ClimberExtension
import com.hazzus.karooclimber.R
import com.hazzus.karooclimber.data.ClimbUiState
import com.hazzus.karooclimber.settings.OverlayAnchor
import com.hazzus.karooclimber.settings.Settings
import io.hammerhead.karooext.models.PerformHardwareAction
import io.hammerhead.karooext.models.SystemNotification
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Owns the floating overlay window (ki2 pattern): SYSTEM_ALERT_WINDOW permission
 * check, foreground-service keepalive, WindowManager add/update/remove.
 *
 * All methods must be called from the main thread.
 */
class OverlayController(private val service: ClimberExtension) {

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val notificationManager =
        service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var view: ClimbOverlayView? = null
    private var permissionNotified = false
    private var lastSettings: Settings? = null
    private var appliedParams: WindowManager.LayoutParams? = null
    private var lastHasActive = false

    val isShowing: Boolean get() = view != null

    /**
     * True while the FULL climb panel — the only place system data fields are
     * drawn — is on screen. The extension keeps OnStreamState consumers alive
     * only then, so hidden fields cost no stream traffic.
     */
    val fieldsVisible = MutableStateFlow(false)

    /** Show (or update) the overlay for [state] with current [settings]. */
    fun show(
        state: ClimbUiState.Shown,
        settings: Settings,
        imperial: Boolean,
        sysValues: Map<String, Map<String, Double>>,
    ) {
        lastSettings = settings
        lastHasActive = state.active != null
        if (!AndroidSettings.canDrawOverlays(service)) {
            notifyPermissionNeeded()
            return
        }
        val current = view
        if (current == null) {
            startForeground()
            val created = ClimbOverlayView(service)
            view = created
            // no relayout hook yet: the view is not attached to the window manager
            created.update(state, settings, imperial, sysValues)
            val params = layoutParams(created, settings)
            windowManager.addView(created, params)
            appliedParams = params
            created.onRelayoutNeeded = {
                lastSettings?.let { relayout(it) }
                refreshFieldsVisible()
            }
            created.onHardwareKeyPassthrough = { keyCode ->
                hardwareActionFor(keyCode)?.let { service.karooSystem.dispatch(it) }
            }
        } else {
            current.update(state, settings, imperial, sysValues)
            applyLayoutIfChanged(current, settings)
        }
        refreshFieldsVisible()
    }

    fun hide() {
        view?.let {
            runCatching { windowManager.removeView(it) }
            view = null
        }
        appliedParams = null
        lastHasActive = false
        fieldsVisible.value = false
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    /** Recompute [fieldsVisible]; called on every show() tick and panel resize. */
    private fun refreshFieldsVisible() {
        fieldsVisible.value =
            view?.panelSize == ViewModeMachine.PanelSize.FULL && lastHasActive
    }

    /** Re-apply layout params (panel size switch, settings change). */
    fun relayout(settings: Settings) {
        val v = view ?: return
        if (!v.isAttachedToWindow) return
        applyLayoutIfChanged(v, settings)
    }

    /** Relayouts through WindowManager are costly — skip when nothing changed. */
    private fun applyLayoutIfChanged(v: ClimbOverlayView, settings: Settings) {
        val params = layoutParams(v, settings)
        val last = appliedParams
        if (last != null &&
            last.width == params.width && last.height == params.height &&
            last.flags == params.flags && last.gravity == params.gravity &&
            last.x == params.x && last.y == params.y &&
            last.alpha == params.alpha
        ) {
            return
        }
        windowManager.updateViewLayout(v, params)
        appliedParams = params
    }

    private fun layoutParams(v: ClimbOverlayView, settings: Settings): WindowManager.LayoutParams {
        val metrics = service.resources.displayMetrics
        val size = v.panelSize
        val width: Int
        val height: Int
        when (size) {
            ViewModeMachine.PanelSize.CHIP -> {
                width = (metrics.widthPixels * CHIP_WIDTH_FRACTION).toInt()
                height = (metrics.heightPixels * CHIP_HEIGHT_FRACTION).toInt()
            }
            ViewModeMachine.PanelSize.EXPANDED -> {
                width = metrics.widthPixels
                height = metrics.heightPixels * settings.heightPercent / 100
            }
            ViewModeMachine.PanelSize.FULL -> {
                // 92%: keep the Karoo status bar visible
                width = metrics.widthPixels
                height = (metrics.heightPixels * FULL_HEIGHT_FRACTION).toInt()
            }
        }
        // the chip stays non-focusable so hardware buttons keep their native actions;
        // bigger panels take key focus so the bottom-left button collapses them
        val focusFlag =
            if (size == ViewModeMachine.PanelSize.CHIP) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                0
            }
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            focusFlag
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = when {
                // full screen sits at the bottom so the status bar stays visible
                size == ViewModeMachine.PanelSize.FULL -> Gravity.BOTTOM
                settings.anchor == OverlayAnchor.TOP -> Gravity.TOP
                else -> Gravity.BOTTOM
            } or Gravity.CENTER_HORIZONTAL
            // top-anchored panels start below the Karoo status bar — the same
            // strip FULL keeps visible by stopping at 92%
            if (size != ViewModeMachine.PanelSize.FULL &&
                settings.anchor == OverlayAnchor.TOP
            ) {
                y = (metrics.heightPixels * (1 - FULL_HEIGHT_FRACTION)).toInt()
            }
            // keep the bottom chip clear of Karoo's native drawers; at the top
            // the status-bar inset is enough
            if (size == ViewModeMachine.PanelSize.CHIP &&
                settings.anchor == OverlayAnchor.BOTTOM
            ) {
                y = (metrics.heightPixels * CHIP_EDGE_OFFSET_FRACTION).toInt()
            }
            alpha = settings.opacityPercent / 100f
        }
    }

    /** Karoo hardware key -> effect replaying it, ki2's KarooKey mapping (BACK is ours). */
    private fun hardwareActionFor(keyCode: Int): PerformHardwareAction? = when (keyCode) {
        KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> PerformHardwareAction.TopLeftPress
        KeyEvent.KEYCODE_NAVIGATE_NEXT -> PerformHardwareAction.TopRightPress
        KeyEvent.KEYCODE_NAVIGATE_IN -> PerformHardwareAction.BottomRightPress
        else -> null
    }

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS declared; degraded is fine pre-33 grant
    private fun startForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Climb overlay",
            NotificationManager.IMPORTANCE_MIN,
        )
        notificationManager.createNotificationChannel(channel)
        val notification = Notification.Builder(service, CHANNEL_ID)
            .setOngoing(true)
            .setContentTitle("Climber+")
            .setContentText("Climb overlay active")
            .setSmallIcon(R.drawable.ic_climber)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        service.startForeground(NOTIFICATION_ID, notification)
    }

    private fun notifyPermissionNeeded() {
        if (permissionNotified) return
        permissionNotified = true
        service.karooSystem.dispatch(
            SystemNotification(
                id = "climber-overlay-permission",
                message = "Permission needed for climb overlay",
                subText = "Allow Climber+ to draw over other apps",
                style = SystemNotification.Style.ERROR,
                actionIntent = AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            ),
        )
    }

    companion object {
        private const val CHANNEL_ID = "climber-overlay"
        private const val NOTIFICATION_ID = 0x201
        private const val CHIP_WIDTH_FRACTION = 0.45
        private const val CHIP_HEIGHT_FRACTION = 0.09
        private const val CHIP_EDGE_OFFSET_FRACTION = 0.02
        private const val FULL_HEIGHT_FRACTION = 0.92
    }
}
