package com.hazzus.karooclimber.data

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnNavigationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Subscribes to [OnNavigationState] and exposes the normalized [RouteData]
 * for either route or destination navigation (null when idle).
 */
class NavigationRepo(private val karooSystem: KarooSystemService) {

    private val _route = MutableStateFlow<RouteData?>(null)
    val route: StateFlow<RouteData?> = _route

    private var consumerId: String? = null
    private val climbCache = ClimbListCache()

    fun start() {
        if (consumerId != null) return
        consumerId = karooSystem.addConsumer<OnNavigationState> { event ->
            _route.value = event.state.toRouteData()
        }
    }

    fun stop() {
        consumerId?.let { karooSystem.removeConsumer(it) }
        consumerId = null
        climbCache.clear()
        _route.value = null
    }

    private fun OnNavigationState.NavigationState.toRouteData(): RouteData? = when (this) {
        is OnNavigationState.NavigationState.Idle -> {
            climbCache.clear()
            null
        }
        is OnNavigationState.NavigationState.NavigatingRoute -> {
            val routeKey = "route:$name:${routePolyline.hashCode()}:$reversed"
            RouteData(
                routeDistance = routeDistance,
                profile = ElevationProfile.fromPolyline(routeElevationPolyline),
                climbs = climbCache.reconcile(routeKey, climbs.map { it.toClimbData() }),
                routeKey = routeKey,
            )
        }
        is OnNavigationState.NavigationState.NavigatingToDestination -> {
            val routeKey = "dest:${destination.id}:${polyline.hashCode()}"
            RouteData(
                routeDistance = null,
                profile = ElevationProfile.fromPolyline(elevationPolyline),
                climbs = climbCache.reconcile(routeKey, climbs.map { it.toClimbData() }),
                routeKey = routeKey,
            )
        }
    }

    private fun OnNavigationState.NavigationState.Climb.toClimbData() = ClimbData(
        startDistance = startDistance,
        length = length,
        avgGrade = grade,
        totalElevation = totalElevation,
    )
}

/**
 * Karoo OS re-emits [OnNavigationState] during a ride with climbs removed from
 * the `climbs` list once the rider reaches them (and may report an empty list
 * mid-reroute). Keep the fullest list seen for the current route and ignore
 * shrunk updates, so climb count and progress stay stable for the whole ride.
 */
class ClimbListCache {
    private var key: String? = null
    private var climbs: List<ClimbData> = emptyList()

    fun reconcile(routeKey: String, incoming: List<ClimbData>): List<ClimbData> {
        if (routeKey != key) {
            key = routeKey
            climbs = incoming
        } else if (incoming.size > climbs.size) {
            climbs = incoming
        }
        return climbs
    }

    fun clear() {
        key = null
        climbs = emptyList()
    }
}
