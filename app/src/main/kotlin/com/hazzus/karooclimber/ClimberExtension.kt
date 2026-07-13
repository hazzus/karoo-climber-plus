package com.hazzus.karooclimber

import android.util.Log
import com.hazzus.karooclimber.data.ClimbEngine
import com.hazzus.karooclimber.data.ClimbUiState
import com.hazzus.karooclimber.data.NavigationRepo
import com.hazzus.karooclimber.data.ProgressRepo
import com.hazzus.karooclimber.data.progressAlongRoute
import com.hazzus.karooclimber.debug.DemoData
import com.hazzus.karooclimber.overlay.OverlayController
import com.hazzus.karooclimber.settings.Settings
import com.hazzus.karooclimber.settings.SettingsRepo
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Extension service bound by Karoo OS. Hosts the KarooSystemService connection,
 * the data layer and the overlay controller.
 */
class ClimberExtension : KarooExtension("karoo-climber", BuildConfig.VERSION_NAME) {

    lateinit var karooSystem: KarooSystemService
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var navigationRepo: NavigationRepo
    private lateinit var progressRepo: ProgressRepo
    private lateinit var settingsRepo: SettingsRepo
    private lateinit var overlay: OverlayController
    private val engine = ClimbEngine()

    private val rideActive = MutableStateFlow(false)
    private val imperial = MutableStateFlow(false)
    private val demoProgress = MutableStateFlow(0.0)
    private val consumerIds = mutableListOf<String>()

    /** Latest field values of streamed system data types (keyed by dataTypeId). */
    private val sysValues = MutableStateFlow<Map<String, Map<String, Double>>>(emptyMap())
    private val sysConsumers = mutableMapOf<String, String>() // dataTypeId -> consumerId

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        navigationRepo = NavigationRepo(karooSystem)
        progressRepo = ProgressRepo(karooSystem)
        settingsRepo = SettingsRepo(applicationContext)
        overlay = OverlayController(this)

        karooSystem.connect { connected ->
            Log.i(TAG, "Karoo system connected=$connected")
        }
        navigationRepo.start()

        consumerIds += karooSystem.addConsumer<RideState> { state ->
            val active = state is RideState.Recording || state is RideState.Paused
            // ride ended: forget completed climbs so re-riding the route starts at 0/N
            if (!active && rideActive.value) engine.reset()
            rideActive.value = active
        }
        consumerIds += karooSystem.addConsumer<UserProfile> { profile ->
            imperial.value =
                profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
        }

        // The DISTANCE_TO_DESTINATION stream (~1 Hz for a whole ride) is only read
        // by computeState when the overlay is on, a ride is recording and demo mode
        // isn't short-circuiting it — release the consumer whenever that's not true.
        scope.launch {
            combine(settingsRepo.settings, rideActive) { settings, riding ->
                settings.overlayEnabled && riding && !settings.demoMode
            }
                .distinctUntilChanged()
                .collect { needed ->
                    if (needed) progressRepo.start() else progressRepo.stop()
                }
        }

        scope.launch {
            combine(
                navigationRepo.route,
                progressRepo.distanceToDestination,
                settingsRepo.settings,
                rideActive,
                demoProgress,
            ) { route, dtd, settings, riding, demoAt ->
                computeState(route, dtd, settings, riding, demoAt)
            }.collect { (state, settings) ->
                when (state) {
                    is ClimbUiState.Shown ->
                        overlay.show(state, settings, imperial.value, sysValues.value)
                    is ClimbUiState.Hidden -> overlay.hide()
                }
            }
        }

        // Stream the system data types configured as full-view fields — but only
        // while the FULL climb panel that draws them is on screen; the rest of
        // the ride the consumers are torn down and cost nothing.
        scope.launch {
            combine(settingsRepo.settings, overlay.fieldsVisible) { settings, visible ->
                if (!visible) {
                    emptySet()
                } else {
                    settings.fullFields
                        .flatMap { listOfNotNull(it.dataTypeId, it.extraDataTypeId) }
                        .toSet()
                }
            }
                .distinctUntilChanged()
                .collect { reconcileSysStreams(it) }
        }

        // Demo mode: loop synthetic progress along the demo route so the overlay
        // can be inspected on the desk without riding. collectLatest cancels the
        // ticker whenever demo mode is off, so real rides get no 1 Hz wakeups.
        scope.launch {
            settingsRepo.settings
                .map { it.demoMode }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    while (enabled) {
                        // loop the whole demo route so all chip/list/climb states show
                        val next = demoProgress.value + DEMO_SPEED_MPS
                        if (next > 4900.0) {
                            engine.reset() // same routeKey each loop — drop completed climbs
                            demoProgress.value = 0.0
                        } else {
                            demoProgress.value = next
                        }
                        delay(1000)
                    }
                }
        }
    }

    private fun computeState(
        route: com.hazzus.karooclimber.data.RouteData?,
        dtd: Double?,
        settings: Settings,
        riding: Boolean,
        demoAt: Double,
    ): Pair<ClimbUiState, Settings> {
        if (!settings.overlayEnabled) return ClimbUiState.Hidden to settings

        // Demo mode short-circuits real data
        if (settings.demoMode) {
            val state = engine.update(DemoData.route(), demoAt, settings.triggerDistanceM)
            return state to settings
        }

        if (!riding) return ClimbUiState.Hidden to settings
        val progress = progressAlongRoute(route, dtd)
        return engine.update(route, progress, settings.triggerDistanceM) to settings
    }

    /** Start/stop OnStreamState consumers to match the configured field set. */
    private fun reconcileSysStreams(wanted: Set<String>) {
        (sysConsumers.keys - wanted).forEach { typeId ->
            sysConsumers.remove(typeId)?.let { karooSystem.removeConsumer(it) }
            sysValues.value = sysValues.value - typeId
        }
        (wanted - sysConsumers.keys).forEach { typeId ->
            sysConsumers[typeId] = karooSystem.addConsumer<OnStreamState>(
                OnStreamState.StartStreaming(typeId),
            ) { event ->
                val values = (event.state as? StreamState.Streaming)?.dataPoint?.values
                sysValues.value = if (!values.isNullOrEmpty()) {
                    sysValues.value + (typeId to values)
                } else {
                    sysValues.value - typeId
                }
            }
        }
    }

    override fun onDestroy() {
        overlay.hide()
        consumerIds.forEach { karooSystem.removeConsumer(it) }
        sysConsumers.values.forEach { karooSystem.removeConsumer(it) }
        navigationRepo.stop()
        progressRepo.stop()
        scope.cancel()
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClimberExtension"
        private const val DEMO_SPEED_MPS = 15.0
    }
}
