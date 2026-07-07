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

    fun start() {
        if (consumerId != null) return
        consumerId = karooSystem.addConsumer<OnNavigationState> { event ->
            _route.value = event.state.toRouteData()
        }
    }

    fun stop() {
        consumerId?.let { karooSystem.removeConsumer(it) }
        consumerId = null
        _route.value = null
    }

    private fun OnNavigationState.NavigationState.toRouteData(): RouteData? = when (this) {
        is OnNavigationState.NavigationState.Idle -> null
        is OnNavigationState.NavigationState.NavigatingRoute -> RouteData(
            routeDistance = routeDistance,
            profile = ElevationProfile.fromPolyline(routeElevationPolyline),
            climbs = climbs.map { it.toClimbData() },
            routeKey = "route:$name:${routePolyline.hashCode()}:$reversed",
        )
        is OnNavigationState.NavigationState.NavigatingToDestination -> RouteData(
            routeDistance = null,
            profile = ElevationProfile.fromPolyline(elevationPolyline),
            climbs = climbs.map { it.toClimbData() },
            routeKey = "dest:${destination.id}:${polyline.hashCode()}",
        )
    }

    private fun OnNavigationState.NavigationState.Climb.toClimbData() = ClimbData(
        startDistance = startDistance,
        length = length,
        avgGrade = grade,
        totalElevation = totalElevation,
    )
}
