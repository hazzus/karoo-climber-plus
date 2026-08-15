package com.hazzus.karooclimber.data

import kotlin.math.roundToInt

/** Minimum spacing between retained live-trace samples, meters (downsampling). */
private const val TRACE_STEP_M = 10.0

/** Bucket (m) for numberless-climb completion dedup, absorbing per-frame position noise. */
private const val SPAN_DEDUP_BUCKET_M = 10.0

/**
 * Builds the overlay [ClimbUiState] from the fresh navigation route (upcoming climbs)
 * and Karoo's native CLIMB stream ([ClimbInfo], the live active climb).
 *
 * Detection is the stream: a non-null [ClimbInfo] means a climb has started (matches
 * the stock climber by construction). On that transition we snapshot the climb's route
 * span **once** from the current DTD [progress] (climbStart = progress − distanceFromBottom)
 * and freeze it — so the silhouette is stable and no longer trusts per-frame DTD.
 * Thereafter the rider is placed from the stream's distanceFromBottom/distanceToTop.
 * Upcoming climbs and the approach state come from the navigation climbs list + progress.
 * No completed-climb tracking: only climbs ahead.
 *
 * With no route loaded, a live CLIMB stream is still surfaced as a "predicted" climb
 * (see [routelessClimb]): coordinates relative to the climb, no upcoming list, and a
 * null profile so the view draws a constant-grade wedge.
 *
 * During a reroute ([RouteData.rerouting]) the whole state switches to a rider-relative
 * frame and every climb is drawn as a wedge (no absolute profile to slice). Karoo's reroute
 * climbs list is anchored to the point where the reroute was computed — not the moving rider
 * — and stays static until Karoo recomputes, so we progress the distance ourselves: [progress]
 * still advances 1:1 with the rider (routeDistance is fixed, DTD falls as you move), so
 * `moved = progress − progressAtAnchor`, re-anchored only when Karoo re-bases the list. If
 * the rider is actually on a climb during the reroute the live CLIMB stream drives it via the
 * route-less path (stream is frame-independent), so the climb is never skipped. The normal
 * absolute-frame logic resumes on rejoin.
 */
class ClimbStateBuilder {

    private var routeKey: String? = null
    private var frozen: ClimbData? = null
    private var frozenNumber: Int? = null

    /** True when [frozen] was built route-less (relative coords), so onClimb re-snapshots. */
    private var frozenRouteless = false

    /**
     * Absolute route position of a route-less/reroute climb's bottom (progress −
     * distanceFromBottom at detection). The rendered span starts at 0, so this recovers a
     * distinct completion-dedup key from the route start — the same absolute (start, end)
     * routed climbs already dedup on. Null for routed climbs ([ClimbData.startDistance] is
     * already absolute there) and when no progress was available (pure route-less).
     */
    private var frozenAbsStart: Double? = null

    /**
     * Furthest point reached up the current climb (ridden distance from its bottom, m)
     * and the elevation gained there. Tracked per active climb so a completion records
     * what was ACTUALLY ridden and its real grade — not the full planned span that was
     * frozen at detection (which over-reports a climb the rider bailed out of).
     */
    private var climbRidden = 0.0
    private var climbElevGained = 0.0

    /**
     * Live trace of the ridden portion of the current wedge climb (no route elevation
     * profile): real (distance, elevation-from-bottom) samples in the active climb's
     * coordinate frame, downsampled to one per [TRACE_STEP_M]. [tracedProfile] is the
     * composed silhouette (bottom anchor + samples + line to the known top), recomputed
     * only on an actual append so its object identity is stable between frames.
     */
    private val tracedPoints = mutableListOf<ElevationProfile.Point>()
    private var tracedProfile: ElevationProfile? = null

    /**
     * Reroute progression: the [progress] captured when the current reroute climbs list was
     * first seen (its anchor), and that list's start distances — so a Karoo recompute (all
     * starts re-based) re-anchors, while a prefix-drop (rider passed the nearest climb) does
     * not. Null anchor = no reroute in progress.
     */
    private var rerouteAnchorProgress: Double? = null
    private var rerouteStarts: List<Int> = emptyList()

    /** Climbs finished this ride, in completion order. */
    private val completed = mutableListOf<CompletedClimb>()

    /** Dedupe by climb number (preferred) and by span (fallback when number is null). */
    private val recordedNumbers = mutableSetOf<Int>()
    private val recordedSpans = mutableSetOf<Pair<Double, Double>>()

    /** Snapshot of completed climbs for the extension to persist. */
    val completedClimbs: List<CompletedClimb> get() = completed.toList()

    /** Drop the frozen active climb (e.g. a new ride). Route changes reset it inline. */
    fun reset() {
        routeKey = null
        clearFrozen()
        clearRerouteAnchor()
        completed.clear()
        recordedNumbers.clear()
        recordedSpans.clear()
    }

    /**
     * Seed completed climbs from persistence on service restart, so an already-recorded
     * climb isn't recorded again. Dedupe sets are rebuilt by climb number where present.
     *
     * NOTE: a restored [CompletedClimb] carries no route span (the model has no
     * start/end), so restored entries with a null climbNumber cannot be span-deduped —
     * acceptable, since a null-number climb re-detected after a restart is a rare edge.
     */
    fun restore(list: List<CompletedClimb>) {
        completed.clear()
        completed.addAll(list)
        recordedNumbers.clear()
        recordedSpans.clear()
        for (c in list) c.climbNumber?.let { recordedNumbers.add(it) }
    }

    /**
     * Record the just-finished [frozen] climb — routed, route-less, or ridden during a reroute.
     * Uses the tracked ridden distance + elevation gained ([climbRidden]/[climbElevGained])
     * so the entry reflects what was actually ridden and its real grade — falling back to
     * the frozen span only if no on-climb frame was ever measured. Dedupes on climb number
     * when known, else on the absolute span: route-less/reroute climbs take theirs from
     * [frozenAbsStart] (their rendered [ClimbData.startDistance] is a relative 0), bucketed
     * so a brief GPS flap that re-detects the SAME climb doesn't record it twice.
     */
    private fun recordFrozen() {
        val climb = frozen ?: return
        val number = frozenNumber
        if (number != null) {
            if (!recordedNumbers.add(number)) return
        } else {
            val start = frozenAbsStart ?: climb.startDistance
            if (!recordedSpans.add(spanKeyOf(start, climb.length))) return
        }
        val ridden = if (climbRidden > 0.0) climbRidden else climb.length
        val elevGained = if (climbRidden > 0.0) climbElevGained else climb.totalElevation
        completed.add(
            CompletedClimb(
                length = ridden,
                avgGrade = if (ridden > 0.0) elevGained / ridden * 100.0 else climb.avgGrade,
                totalElevation = elevGained,
                climbNumber = number,
            ),
        )
    }

    /**
     * Dedup key for a numberless climb: (start, end) bucketed to [SPAN_DEDUP_BUCKET_M] so
     * per-frame noise in progress − distanceFromBottom can't defeat dedup of the same climb.
     */
    private fun spanKeyOf(start: Double, length: Double): Pair<Double, Double> {
        fun bucket(v: Double) = (v / SPAN_DEDUP_BUCKET_M).roundToInt() * SPAN_DEDUP_BUCKET_M
        return bucket(start) to bucket(start + length)
    }

    private fun clearFrozen() {
        frozen = null
        frozenNumber = null
        frozenRouteless = false
        frozenAbsStart = null
        climbRidden = 0.0
        climbElevGained = 0.0
        clearTrace()
    }

    private fun clearTrace() {
        tracedPoints.clear()
        tracedProfile = null
    }

    private fun clearRerouteAnchor() {
        rerouteAnchorProgress = null
        rerouteStarts = emptyList()
    }

    /**
     * Distance the rider has moved since the current reroute anchor. Karoo's reroute climbs
     * list is anchored where the reroute was computed and stays static as the rider moves;
     * [progress] still advances 1:1 with the rider, so `moved = progress − anchorProgress`.
     * The anchor is (re)set whenever the list is freshly re-based — any start not seen in the
     * previous frame — but a prefix-drop (rider passed the nearest climb, the rest unchanged)
     * keeps the anchor so the progression stays continuous. Returns 0 without a DTD [progress].
     */
    private fun rerouteMoved(sorted: List<ClimbData>, progress: Double?): Double {
        val starts = sorted.map { it.startDistance.roundToInt() }
        val reBased = starts.any { it !in rerouteStarts }
        if (rerouteAnchorProgress == null || reBased) rerouteAnchorProgress = progress
        rerouteStarts = starts
        val anchor = rerouteAnchorProgress
        return if (progress != null && anchor != null) (progress - anchor).coerceAtLeast(0.0) else 0.0
    }

    fun build(
        route: RouteData?,
        progress: Double?,
        info: ClimbInfo?,
        triggerDistance: Double,
    ): ClimbUiState {
        // No navigation: still surface a predicted climb straight from the CLIMB stream.
        if (route == null) {
            if (info == null) {
                recordFrozen() // a route-less climb that just topped out is still recorded
                clearFrozen()
                return ClimbUiState.Hidden
            }
            return routelessClimb(info, progress)?.let { (active, riderPos) ->
                ClimbUiState.Shown(
                    climbs = emptyList(),
                    progress = riderPos,
                    profile = null,
                    active = active,
                    completed = completed.toList(),
                    tracedProfile = tracedProfile,
                )
            } ?: ClimbUiState.Hidden
        }

        if (route.routeKey != routeKey) {
            routeKey = route.routeKey
            clearFrozen()
            clearRerouteAnchor()
        }

        val sorted = route.climbs.sortedBy { it.startDistance }

        // Off-route (a reroute is active): the climbs list is re-based to distances AHEAD of
        // the anchor point where Karoo computed the reroute (rejoinDistance is set), not
        // absolute route positions, and the fixed elevation profile no longer lines up. The
        // list stays static as the rider moves, so we progress it ourselves ([rerouteMoved]):
        // each climb sits at startDistance − moved, and every silhouette is a constant-grade
        // wedge (null profile). Absolute-frame logic resumes automatically on rejoin.
        if (route.rerouting) {
            val moved = rerouteMoved(sorted, progress)
            // On a climb during the reroute: the CLIMB stream is frame-independent, so surface
            // the active climb straight from it (wedge, relative coords) — the route-less path —
            // rather than the approach-only list below, which skips the climb the moment the
            // rider passes its start. The upcoming list is hidden while climbing (its anchor-
            // frame distances can't be placed against the stream's aggregate climb).
            if (info != null) {
                routelessClimb(info, progress)?.let { (active, riderPos) ->
                    return ClimbUiState.Shown(
                        climbs = emptyList(),
                        progress = riderPos,
                        profile = null,
                        active = active,
                        completed = completed.toList(),
                        tracedProfile = tracedProfile,
                    )
                }
                // stream present but not yet anchorable -> fall through to the list
            } else {
                // climb topped out / none active -> record it (off-route climbs count) then drop
                recordFrozen()
                clearFrozen()
            }
            val ahead = sorted.filter { moved < it.endDistance }
            if (ahead.isEmpty()) return ClimbUiState.Hidden
            return ClimbUiState.Shown(
                climbs = ahead,
                progress = moved,
                profile = null,
                active = approaching(route.copy(profile = null), ahead, moved, triggerDistance),
                completed = completed.toList(),
            )
        }
        clearRerouteAnchor() // on route or rejoined: forget the reroute anchor

        // On a climb: the stream drives everything once anchored.
        if (info != null) {
            onClimb(route, progress, info)?.let { (active, riderPos) ->
                val ahead = sorted.filter { riderPos < it.endDistance }
                return ClimbUiState.Shown(
                    climbs = ahead,
                    progress = riderPos,
                    profile = route.profile,
                    active = active,
                    completed = completed.toList(),
                    // null when route.profile != null (we never traced): the real slice suffices
                    tracedProfile = tracedProfile,
                )
            }
            // Couldn't anchor yet (no DTD frame) -> fall through and wait.
        } else {
            // Stream ended while routed: the active climb just topped out -> record it.
            recordFrozen()
            clearFrozen()
        }

        // Not on a climb: show the climbs ahead (+ approach), or hide if none.
        if (progress == null) return ClimbUiState.Hidden
        val ahead = sorted.filter { progress < it.endDistance }
        if (ahead.isEmpty()) return ClimbUiState.Hidden
        return ClimbUiState.Shown(
            climbs = ahead,
            progress = progress,
            profile = route.profile,
            active = approaching(route, ahead, progress, triggerDistance),
            completed = completed.toList(),
        )
    }

    /**
     * Build the active climb from the stream, snapshotting its frozen route span on the
     * first frame (or when the stream moves to a new climb number). Returns null while we
     * still lack a DTD [progress] to anchor the very first snapshot.
     */
    private fun onClimb(
        route: RouteData,
        progress: Double?,
        info: ClimbInfo,
    ): Pair<ClimbUiState.ActiveClimb, Double>? {
        val distToTop = info.distanceToTop
        val distFromBottom = info.distanceFromBottom
        val number = info.climbNumber

        if (frozen == null || frozenRouteless || (number != null && number != frozenNumber)) {
            // Stream jumped straight to the next climb (no null frame): the previous
            // frozen climb topped out -> record it before overwriting the snapshot.
            // recordFrozen() no-ops on the first climb (frozen null); a route-less climb
            // replaced here (rejoin mid-climb) is recorded as what was ridden.
            recordFrozen()
            // starting a new climb: reset the ridden tracker (clearFrozen isn't called on
            // a straight number advance, only on a null-frame end)
            climbRidden = 0.0
            climbElevGained = 0.0
            clearTrace()
            val pos = progress ?: return null // need DTD once to anchor the snapshot
            val length = (distFromBottom ?: 0.0) + (distToTop ?: 0.0)
            if (length <= 0.0) return null
            val climbStart = (pos - (distFromBottom ?: 0.0)).coerceAtLeast(0.0)
            // Climb gain = gain done from the bottom + gain remaining to the top. Do NOT use
            // the stream's CLIMB_ELEVATION: it's the rider's current altitude, not the climb's
            // gain (real ride: ce=446 m reported for a ~68 m climb -> a bogus 31% avgGrade).
            val totalElevation = (info.elevationFromBottom ?: 0.0) + (info.elevationToTop ?: 0.0)
            frozen = ClimbData(
                startDistance = climbStart,
                length = length,
                avgGrade = totalElevation / length * 100.0,
                totalElevation = totalElevation,
            )
            frozenNumber = number
            frozenRouteless = false
        }
        val climb = frozen!!

        // Rider position along the route, stream-driven (DTD not trusted per-frame).
        val riderPos = when {
            distFromBottom != null -> climb.startDistance + distFromBottom
            distToTop != null -> climb.endDistance - distToTop
            else -> climb.startDistance
        }.coerceIn(climb.startDistance, climb.endDistance)

        // Live-trace the ridden silhouette only when Karoo sent no route profile to slice.
        if (route.profile == null) recordTracePoint(info, climb)

        val active = ClimbUiState.ActiveClimb(
            climb = climb,
            index = (number?.minus(1))?.coerceAtLeast(0) ?: 0,
            total = info.totalClimbs,
            distanceToStart = 0.0,
            distanceToTop = distToTop ?: (climb.endDistance - riderPos).coerceAtLeast(0.0),
            elevationToTop = info.elevationToTop
                ?: elevationToTop(route.profile, climb, riderPos),
        )

        // Track the furthest point ridden up this climb (and the elevation gained there),
        // preferring the stream's own from-bottom measures, so a completion records the
        // actual ridden distance + real grade rather than the planned span.
        val ridden = (info.distanceFromBottom ?: (riderPos - climb.startDistance)).coerceAtLeast(0.0)
        if (ridden >= climbRidden) {
            climbRidden = ridden
            climbElevGained =
                (info.elevationFromBottom ?: (climb.totalElevation - active.elevationToTop))
                    .coerceAtLeast(0.0)
        }
        return active to riderPos
    }

    /**
     * A predicted climb with no route loaded: built straight from the CLIMB stream in
     * climb-relative coordinates (start = 0), no upcoming list, null profile (the view
     * draws a constant-grade wedge). Frozen like [onClimb] for a stable silhouette.
     */
    private fun routelessClimb(info: ClimbInfo, progress: Double?): Pair<ClimbUiState.ActiveClimb, Double>? {
        val distToTop = info.distanceToTop
        val distFromBottom = info.distanceFromBottom
        val number = info.climbNumber

        if (frozen == null || !frozenRouteless || (number != null && number != frozenNumber)) {
            val length = (distFromBottom ?: 0.0) + (distToTop ?: 0.0)
            if (length <= 0.0) return null
            // New climb identity: record the one just finished (off-route climbs count too),
            // then reset the ridden tracker + live trace. All no-op on the very first climb.
            recordFrozen()
            climbRidden = 0.0
            climbElevGained = 0.0
            clearTrace()
            // Climb gain = gain done from the bottom + gain remaining to the top. Do NOT use
            // the stream's CLIMB_ELEVATION: it's the rider's current altitude, not the climb's
            // gain (real ride: ce=446 m reported for a ~68 m climb -> a bogus 31% avgGrade).
            val totalElevation = (info.elevationFromBottom ?: 0.0) + (info.elevationToTop ?: 0.0)
            frozen = ClimbData(
                startDistance = 0.0,
                length = length,
                avgGrade = totalElevation / length * 100.0,
                totalElevation = totalElevation,
            )
            frozenNumber = number
            frozenRouteless = true
            // Absolute climb bottom from the route position, for completion dedup (the span
            // above is relative). Null with no progress (pure route-less) -> falls back to it.
            frozenAbsStart = progress?.let { (it - (distFromBottom ?: 0.0)).coerceAtLeast(0.0) }
        }
        val climb = frozen!!

        val riderPos = when {
            distFromBottom != null -> distFromBottom
            distToTop != null -> climb.length - distToTop
            else -> 0.0
        }.coerceIn(0.0, climb.length)

        // Live-trace the ridden silhouette (wedge climb, no route profile).
        recordTracePoint(info, climb)

        val active = ClimbUiState.ActiveClimb(
            climb = climb,
            index = (number?.minus(1))?.coerceAtLeast(0) ?: 0,
            total = info.totalClimbs,
            distanceToStart = 0.0,
            distanceToTop = distToTop ?: (climb.length - riderPos).coerceAtLeast(0.0),
            elevationToTop = info.elevationToTop
                ?: elevationToTop(null, climb, riderPos),
        )

        // Furthest point ridden (relative coords) so an off-route completion records what was
        // actually ridden and its real grade — mirrors onClimb.
        val ridden = (distFromBottom ?: riderPos).coerceAtLeast(0.0)
        if (ridden >= climbRidden) {
            climbRidden = ridden
            climbElevGained =
                (info.elevationFromBottom ?: (climb.totalElevation - active.elevationToTop))
                    .coerceAtLeast(0.0)
        }
        return active to riderPos
    }

    /** Approach state: within the trigger window of the next climb but not yet on it. */
    private fun approaching(
        route: RouteData,
        ahead: List<ClimbData>,
        progress: Double,
        triggerDistance: Double,
    ): ClimbUiState.ActiveClimb? {
        val next = ahead.firstOrNull() ?: return null
        if (progress < next.startDistance - triggerDistance || progress >= next.startDistance) {
            return null
        }
        return ClimbUiState.ActiveClimb(
            climb = next,
            index = 0,
            total = ahead.size,
            distanceToStart = (next.startDistance - progress).coerceAtLeast(0.0),
            distanceToTop = (next.endDistance - progress).coerceAtLeast(0.0),
            elevationToTop = elevationToTop(route.profile, next, progress),
        )
    }

    /**
     * Record one live-trace sample for the current wedge climb: a real
     * (distance, elevation-from-bottom) pair in the active climb's coordinate frame.
     * Downsampled to one point per [TRACE_STEP_M]. Recomposes [tracedProfile] only on an
     * actual append, so its object identity stays stable between samples. Skips frames the
     * stream sends without elevationFromBottom (there's no reliable substitute).
     */
    private fun recordTracePoint(info: ClimbInfo, climb: ClimbData) {
        val d = info.distanceFromBottom ?: return
        val e = info.elevationFromBottom ?: return
        val x = (climb.startDistance + d).coerceIn(climb.startDistance, climb.endDistance)
        val last = tracedPoints.lastOrNull()
        if (last != null && x - last.distance < TRACE_STEP_M) return // downsample / drop backward
        // Reject glitchy samples instead of letting one spike the silhouette (blowing up its
        // elevation span → squashed profile, and its segment grades → absurd %): elevation-
        // from-bottom lives in [0, climbTotal]. Then clamp small noise to non-decreasing,
        // since while climbing the gain from the bottom only ever rises.
        val total = climb.totalElevation
        if (total <= 0.0 || e < -1.0 || e > total + 1.0) return
        val y = e.coerceIn(last?.elevation ?: 0.0, total)
        tracedPoints.add(ElevationProfile.Point(x, y))
        tracedProfile = composeTrace(climb)
    }

    /** Bottom anchor + real observations + a straight line to the known top. */
    private fun composeTrace(climb: ClimbData): ElevationProfile? {
        if (tracedPoints.isEmpty()) return null
        val pts = ArrayList<ElevationProfile.Point>(tracedPoints.size + 2)
        pts.add(ElevationProfile.Point(climb.startDistance, 0.0))
        pts.addAll(tracedPoints)
        if (pts.last().distance < climb.endDistance - 1.0) {
            pts.add(ElevationProfile.Point(climb.endDistance, climb.totalElevation))
        }
        return ElevationProfile.fromPoints(pts)
    }

    private fun elevationToTop(profile: ElevationProfile?, climb: ClimbData, pos: Double): Double {
        val onClimbPos = pos.coerceIn(climb.startDistance, climb.endDistance)
        return if (profile != null) {
            (profile.elevationAt(climb.endDistance) - profile.elevationAt(onClimbPos))
                .coerceAtLeast(0.0)
        } else {
            val remainingFraction =
                ((climb.endDistance - onClimbPos) / climb.length).coerceIn(0.0, 1.0)
            climb.totalElevation * remainingFraction
        }
    }
}
