package com.hazzus.karooclimber

import android.util.Log
import com.hazzus.karooclimber.data.ClimbInfo
import com.hazzus.karooclimber.data.ClimbStateBuilder
import com.hazzus.karooclimber.data.ClimbStreamRepo
import com.hazzus.karooclimber.data.ClimbUiState
import com.hazzus.karooclimber.data.NavigationRepo
import com.hazzus.karooclimber.data.ProgressRepo
import com.hazzus.karooclimber.data.RideHistoryRepo
import com.hazzus.karooclimber.data.progressAlongRoute
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private lateinit var climbStreamRepo: ClimbStreamRepo
    private lateinit var settingsRepo: SettingsRepo
    private lateinit var rideHistoryRepo: RideHistoryRepo
    private lateinit var overlay: OverlayController
    private val builder = ClimbStateBuilder()

    /** Size last mirrored to DataStore, to skip redundant writes. */
    private var lastPersistedCount = 0

    private val rideActive = MutableStateFlow(false)
    private val imperial = MutableStateFlow(false)
    private val consumerIds = mutableListOf<String>()

    /** Latest field values of streamed system data types (keyed by dataTypeId). */
    private val sysValues = MutableStateFlow<Map<String, Map<String, Double>>>(emptyMap())
    private val sysConsumers = mutableMapOf<String, String>() // dataTypeId -> consumerId

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        navigationRepo = NavigationRepo(karooSystem)
        progressRepo = ProgressRepo(karooSystem)
        climbStreamRepo = ClimbStreamRepo(karooSystem)
        settingsRepo = SettingsRepo(applicationContext)
        rideHistoryRepo = RideHistoryRepo(applicationContext)
        overlay = OverlayController(this)

        // Seed the builder from persistence so a mid-ride service restart keeps
        // climbs already completed this ride.
        scope.launch {
            val restored = rideHistoryRepo.load()
            builder.restore(restored)
            lastPersistedCount = restored.size
        }

        karooSystem.connect { connected ->
            Log.i(TAG, "Karoo system connected=$connected")
        }
        navigationRepo.start()

        consumerIds += karooSystem.addConsumer<RideState> { state ->
            val active = state is RideState.Recording || state is RideState.Paused
            // ride ended: drop the frozen active climb so the next ride starts clean
            if (!active && rideActive.value) {
                builder.reset()
                lastPersistedCount = 0
                scope.launch { rideHistoryRepo.clear() }
            }
            rideActive.value = active
        }
        consumerIds += karooSystem.addConsumer<UserProfile> { profile ->
            imperial.value =
                profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
        }

        // The DISTANCE_TO_DESTINATION and CLIMB streams (~1 Hz for a whole ride) are
        // only needed while the overlay is on and a ride is recording — release both
        // consumers whenever that's not true.
        scope.launch {
            combine(settingsRepo.settings, rideActive) { settings, riding ->
                settings.overlayEnabled && riding
            }
                .distinctUntilChanged()
                .collect { needed ->
                    if (needed) {
                        progressRepo.start()
                        climbStreamRepo.start()
                    } else {
                        progressRepo.stop()
                        climbStreamRepo.stop()
                    }
                }
        }

        scope.launch {
            combine(
                navigationRepo.route,
                progressRepo.distanceToDestination,
                climbStreamRepo.climbInfo,
                settingsRepo.settings,
                rideActive,
            ) { route, dtd, info, settings, riding ->
                computeState(route, dtd, info, settings, riding)
            }.collect { (state, settings) ->
                when (state) {
                    is ClimbUiState.Shown ->
                        overlay.show(state, settings, imperial.value, sysValues.value)
                    is ClimbUiState.Hidden -> overlay.hide()
                }
                // Mirror any newly completed climbs to DataStore.
                val completed = builder.completedClimbs
                if (completed.size != lastPersistedCount) {
                    rideHistoryRepo.save(completed)
                    lastPersistedCount = completed.size
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
    }

    private fun computeState(
        route: com.hazzus.karooclimber.data.RouteData?,
        dtd: Double?,
        info: ClimbInfo?,
        settings: Settings,
        riding: Boolean,
    ): Pair<ClimbUiState, Settings> {
        if (!settings.overlayEnabled || !riding) return ClimbUiState.Hidden to settings
        val progress = progressAlongRoute(route, dtd)
        return builder.build(route, progress, info, settings.triggerDistanceM) to settings
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
        climbStreamRepo.stop()
        scope.cancel()
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClimberExtension"
    }
}
