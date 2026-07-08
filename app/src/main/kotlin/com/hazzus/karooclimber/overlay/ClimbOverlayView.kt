package com.hazzus.karooclimber.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.hazzus.karooclimber.R
import com.hazzus.karooclimber.data.ClimbData
import com.hazzus.karooclimber.data.ClimbUiState
import com.hazzus.karooclimber.data.ElevationProfile
import com.hazzus.karooclimber.overlay.ViewModeMachine.PanelSize
import com.hazzus.karooclimber.palette.GradePalette
import com.hazzus.karooclimber.palette.GradePalettes
import com.hazzus.karooclimber.settings.BaseMode
import com.hazzus.karooclimber.settings.ClimbField
import com.hazzus.karooclimber.settings.Settings
import kotlin.math.abs
import kotlin.math.min

/**
 * Canvas-drawn climb overlay with three sizes:
 *  - CHIP (permanent while a route is loaded): climbs counter / countdown / live grade
 *  - EXPANDED drawer: climb profile, or the next-3-climbs list when idle
 *  - FULL screen: profile + 2x2 data fields, or the full scrollable climbs list
 *
 * Gestures: tap = expand chip / cycle modes; swipe up = grow; swipe down = shrink;
 * vertical drag scrolls the full list.
 */
@SuppressLint("ViewConstructor")
class ClimbOverlayView(context: Context) : View(context) {

    private val machine = ViewModeMachine()

    private var state: ClimbUiState.Shown? = null
    private var settings: Settings = Settings()
    private var palette: GradePalette = GradePalettes.byId(settings.paletteId)
    private var imperial: Boolean = false
    private var activeKey: Pair<Double, Double>? = null
    private var listScrollPx = 0f
    private var sysValues: Map<String, Double> = emptyMap()

    /** Controller hook: window size must change. */
    var onRelayoutNeeded: (() -> Unit)? = null

    val panelSize: PanelSize get() = machine.size

    // ------------------------------------------------------------------ input

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val s = state ?: return true
                when (machine.size) {
                    PanelSize.CHIP -> resize { machine.expand() }
                    PanelSize.EXPANDED ->
                        if (s.active != null) cycleMode(s) else resize { machine.expand() }
                    PanelSize.FULL -> if (s.active != null) cycleMode(s)
                }
                invalidate()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                // drag scrolls the full-screen climbs list
                if (machine.size == PanelSize.FULL && state?.active == null) {
                    listScrollPx = (listScrollPx + distanceY).coerceIn(0f, maxListScroll())
                    invalidate()
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                if (abs(dy) < SWIPE_MIN_DISTANCE_PX || abs(dy) < abs(e2.x - e1.x)) return false
                val scrollableList = machine.size == PanelSize.FULL && state?.active == null
                if (dy > 0 && velocityY > SWIPE_MIN_VELOCITY) {
                    // swipe down shrinks; in the list it only shrinks from the very top
                    if (scrollableList && listScrollPx > 0f) return false
                    if (machine.size != PanelSize.CHIP) {
                        resize { machine.collapse() }
                        return true
                    }
                } else if (dy < 0 && velocityY < -SWIPE_MIN_VELOCITY) {
                    if (scrollableList) return false
                    if (machine.size != PanelSize.FULL) {
                        resize { machine.expand() }
                        return true
                    }
                }
                return false
            }
        },
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun cycleMode(s: ClimbUiState.Shown) {
        val active = s.active ?: return
        machine.next(
            includeAlt = settings.baseMode == BaseMode.BOTH,
            windowsMeters = settings.previewWindowsKm.map { it * 1000.0 },
            remainingMeters = active.distanceToTop,
        )
    }

    private inline fun resize(change: () -> Unit) {
        change()
        listScrollPx = 0f
        onRelayoutNeeded?.invoke()
        invalidate()
    }

    // ----------------------------------------------------------------- update

    fun update(
        newState: ClimbUiState.Shown,
        newSettings: Settings,
        isImperial: Boolean = imperial,
        newSysValues: Map<String, Double> = sysValues,
    ) {
        val newKey = newState.active?.climb?.let { it.startDistance to it.endDistance }
        if (newKey != activeKey) {
            if (newKey != null) machine.onNewClimb() else machine.onClimbEnded()
            activeKey = newKey
            if (newKey != null) rebuildClimbCache(newState)
            listScrollPx = 0f
            onRelayoutNeeded?.invoke()
        }
        newState.active?.let { machine.onProgress(it.distanceToTop) }
        state = newState
        settings = newSettings
        palette = GradePalettes.byId(newSettings.paletteId)
        imperial = isImperial
        sysValues = newSysValues
        invalidate()
    }

    // -------------------------------------------------- per-climb cached data

    /** Profile geometry and 100 m color chunks, computed once per climb. */
    private var cachePoints: List<ElevationProfile.Point> = emptyList()
    private var cacheSegments: List<ElevationProfile.Segment> = emptyList()
    private var cacheMinElev = 0.0
    private var cacheElevSpan = 10.0

    private fun rebuildClimbCache(s: ClimbUiState.Shown) {
        val climb = s.active?.climb ?: return
        val profile = s.profile
        if (profile != null) {
            cachePoints = profile.slice(climb.startDistance, climb.endDistance)
            cacheSegments =
                profile.segments(climb.startDistance, climb.endDistance, GRADE_CHUNK_METERS)
        } else {
            // no elevation data: constant-grade wedge from the climb summary
            cachePoints = listOf(
                ElevationProfile.Point(climb.startDistance, 0.0),
                ElevationProfile.Point(climb.endDistance, climb.totalElevation),
            )
            cacheSegments = listOf(
                ElevationProfile.Segment(climb.startDistance, climb.endDistance, climb.avgGrade),
            )
        }
        cacheMinElev = cachePoints.minOf { it.elevation }
        cacheElevSpan = (cachePoints.maxOf { it.elevation } - cacheMinElev).coerceAtLeast(10.0)
        pathWindow = null
    }

    // ---------------------------------------------------------------- drawing

    // Karoo's native data-field font (IBM Plex Sans Condensed, shipped in
    // /system/fonts), used for all overlay text at its single Medium weight.
    // Null on other devices -> Paint falls back to the default typeface.
    private val plexCondensed: Typeface? = runCatching {
        Typeface.createFromFile("/system/fonts/IBMPlexSansCondensed-Medium.otf")
    }.getOrNull()

    private val panelBgPaint = Paint().apply { color = Color.BLACK }
    private val approachBgPaint = Paint().apply { color = 0xFF383838.toInt() }
    private val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0121212.toInt() }
    private val fillPaint = Paint()
    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellPaint = Paint().apply { color = 0xFF1C1C1E.toInt() }
    private val pastPaint = Paint().apply { color = 0x99000000.toInt() }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val markerCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = plexCondensed
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDCE8F5.toInt()
        typeface = plexCondensed
    }
    private val rowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111111.toInt()
        typeface = plexCondensed
    }
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = plexCondensed
    }
    private val splicePaint = Paint().apply {
        color = 0x40000000
        strokeWidth = 2f
    }

    // Hammerhead glyphs replacing the → / ↗ text arrows (white fill baked in)
    private val distToIcon: Drawable? = context.getDrawable(R.drawable.ic_dist_to)
    private val elevGainIcon: Drawable? = context.getDrawable(R.drawable.ic_elev_gain)
    private val doneIcon: Drawable? = context.getDrawable(R.drawable.ic_done)

    /**
     * Draws [icon] with its bottom on the text [baseline], scaled to [size] height.
     * Returns the drawn width (0 if the icon failed to load).
     */
    private fun drawIcon(canvas: Canvas, icon: Drawable?, x: Float, baseline: Float, size: Float): Float {
        icon ?: return 0f
        val w = size * icon.intrinsicWidth / icon.intrinsicHeight
        icon.setBounds(x.toInt(), (baseline - size).toInt(), (x + w).toInt(), baseline.toInt())
        icon.draw(canvas)
        return w
    }

    private fun iconWidth(icon: Drawable?, size: Float): Float =
        if (icon == null) 0f else size * icon.intrinsicWidth / icon.intrinsicHeight

    override fun onDraw(canvas: Canvas) {
        val s = state ?: return
        when (machine.size) {
            PanelSize.CHIP -> drawChip(canvas, s)
            PanelSize.EXPANDED ->
                if (s.active != null) drawClimbPanel(canvas, s, withFields = false)
                else drawListPanel(canvas, s, limit = 3, scrollable = false)
            PanelSize.FULL ->
                if (s.active != null) drawClimbPanel(canvas, s, withFields = true)
                else drawListPanel(canvas, s, limit = Int.MAX_VALUE, scrollable = true)
        }
    }

    // ------------------------------------------------------------------- chip

    private fun drawChip(canvas: Canvas, s: ClimbUiState.Shown) {
        val r = height / 2f
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, chipBgPaint)
        val active = s.active
        chipTextPaint.textSize = height * 0.45f
        val baseline = height / 2f - (chipTextPaint.ascent() + chipTextPaint.descent()) / 2f

        if (active != null && active.distanceToStart <= 0) {
            // grade [dist-to icon] distance-to-top; icon 3/4 of the digits'
            // visible height, bottom on the baseline (same as the header)
            val grade = currentGrade(s)?.let { "%.0f%%".format(it) } ?: "--%"
            val dist = formatValue(active.distanceToTop)
            val iconH = chipTextPaint.textSize * 0.54f
            val gap = chipTextPaint.textSize * 0.3f
            chipTextPaint.textAlign = Paint.Align.LEFT
            val total = chipTextPaint.measureText(grade) + gap +
                iconWidth(distToIcon, iconH) + gap + chipTextPaint.measureText(dist)
            var x = (width - total) / 2f
            canvas.drawText(grade, x, baseline, chipTextPaint)
            x += chipTextPaint.measureText(grade) + gap
            x += drawIcon(canvas, distToIcon, x, baseline, iconH) + gap
            canvas.drawText(dist, x, baseline, chipTextPaint)
            return
        }

        val text = if (active == null) {
            "${s.completedCount}/${s.totalCount} climbs"
        } else {
            "IN ${formatValue(active.distanceToStart)}"
        }
        chipTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, width / 2f, baseline, chipTextPaint)
    }

    // ---------------------------------------------------------- climb profile

    private fun drawClimbPanel(canvas: Canvas, s: ClimbUiState.Shown, withFields: Boolean) {
        val active = s.active ?: return
        if (active.distanceToStart > 0) {
            // approach progress bar: black fills left -> right until the climb starts
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), approachBgPaint)
            val fraction =
                1.0 - (active.distanceToStart / settings.triggerDistanceM).coerceIn(0.0, 1.0)
            canvas.drawRect(0f, 0f, (width * fraction).toFloat(), height.toFloat(), panelBgPaint)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), panelBgPaint)
        }

        val pad = width * 0.02f
        val headerH = height * if (withFields) 0.12f else 0.15f
        drawHeader(canvas, s, active, pad, headerH)

        val stripH = height * if (withFields) 0.08f else 0.14f
        if (withFields) {
            val chartBottom = height * 0.47f
            drawProfile(canvas, s, active, RectF(pad, headerH, width - pad, chartBottom))
            drawChunkStrip(canvas, s, active, RectF(pad, chartBottom, width - pad, chartBottom + stripH))
            drawFields(
                canvas,
                s,
                active,
                RectF(pad, chartBottom + stripH + pad, width - pad, height - pad),
            )
        } else {
            val chartBottom = height - pad - stripH
            drawProfile(canvas, s, active, RectF(pad, headerH, width - pad, chartBottom))
            drawChunkStrip(canvas, s, active, RectF(pad, chartBottom, width - pad, height - pad))
        }
    }

    /**
     * Native Climber style strip: grades of the next five 100 m chunks ahead of
     * the rider, each cell colored by its grade.
     */
    /**
     * The 100 m chunks shown in the strip: sliding windows starting exactly at
     * the rider's position, so tiles and band move smoothly with every update
     * (not snapped to a fixed 100 m grid).
     */
    private fun stripChunks(
        s: ClimbUiState.Shown,
        active: ClimbUiState.ActiveClimb,
    ): List<ElevationProfile.Segment> {
        val climb = active.climb
        val profile = s.profile
        val result = ArrayList<ElevationProfile.Segment>(CHUNK_STRIP_CELLS)
        var start = s.progress.coerceAtLeast(climb.startDistance)
        while (result.size < CHUNK_STRIP_CELLS) {
            val end = min(start + GRADE_CHUNK_METERS, climb.endDistance)
            if (end - start < 1.0) break
            val grade = if (profile != null) {
                (profile.elevationAt(end) - profile.elevationAt(start)) / (end - start) * 100.0
            } else {
                climb.avgGrade
            }
            result.add(ElevationProfile.Segment(start, end, grade))
            start = end
        }
        return result
    }

    private fun drawChunkStrip(
        canvas: Canvas,
        s: ClimbUiState.Shown,
        active: ClimbUiState.ActiveClimb,
        area: RectF,
    ) {
        val chunks = stripChunks(s, active)
        if (chunks.isEmpty()) return

        val gap = area.width() * 0.008f
        val cellW = (area.width() - gap * (CHUNK_STRIP_CELLS - 1)) / CHUNK_STRIP_CELLS
        val textSize = min(area.height() * 0.78f, cellW * 0.42f)
        rowTextPaint.textSize = textSize
        rowTextPaint.textAlign = Paint.Align.CENTER

        var x = area.left
        for (chunk in chunks) {
            rowPaint.color = if (chunk.grade < 0 && palette.bands.last().first >= 0.0) {
                GradePalettes.descentColor
            } else {
                palette.colorFor(chunk.grade)
            }
            val cell = RectF(x, area.top, x + cellW, area.bottom - area.height() * 0.08f)
            canvas.drawRoundRect(cell, CELL_CORNER / 2f, CELL_CORNER / 2f, rowPaint)
            canvas.drawText(
                "%.1f".format(chunk.grade),
                cell.centerX(),
                cell.centerY() - (rowTextPaint.ascent() + rowTextPaint.descent()) / 2f,
                rowTextPaint,
            )
            x += cellW + gap
        }
    }

    /** One font size for every panel header, capped by width so left/right parts never collide. */
    private fun headerFontSize(headerH: Float): Float = min(headerH * 0.95f, width * 0.08f)

    private fun drawHeader(
        canvas: Canvas,
        s: ClimbUiState.Shown,
        active: ClimbUiState.ActiveClimb,
        pad: Float,
        headerH: Float,
    ) {
        val big = headerFontSize(headerH)
        val small = min(headerH * 0.4f, width * 0.035f)
        // top-aligned in the header, not vertically centered
        val baseline = big * 1.05f

        textPaint.textSize = big
        textPaint.textAlign = Paint.Align.LEFT
        val title = if (active.distanceToStart > 0) {
            "IN ${formatValue(active.distanceToStart)}"
        } else {
            "TO TOP"
        }
        canvas.drawText(title, pad, baseline, textPaint)
        (machine.mode as? ViewModeMachine.Mode.Preview)?.let { m ->
            subTextPaint.textSize = small
            subTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                "next ${formatDistance(m.windowMeters)}",
                pad,
                baseline + small * 1.3f,
                subTextPaint,
            )
        }

        // unitless values with Hammerhead glyphs, native style; icons are 3/4
        // of the digits' visible height (cap height ~= 0.72 * font size) and
        // sit on the text baseline
        val iconH = big * 0.54f
        val gap = big * 0.25f
        val distText = formatValue(active.distanceToTop)
        val elevText = formatElevationValue(active.elevationToTop)
        textPaint.textAlign = Paint.Align.LEFT
        val total = textPaint.measureText(distText) + gap + iconWidth(distToIcon, iconH) +
            gap * 2.5f + textPaint.measureText(elevText) + gap + iconWidth(elevGainIcon, iconH)
        var x = width - pad - total
        canvas.drawText(distText, x, baseline, textPaint)
        x += textPaint.measureText(distText) + gap
        x += drawIcon(canvas, distToIcon, x, baseline, iconH) + gap * 2.5f
        canvas.drawText(elevText, x, baseline, textPaint)
        x += textPaint.measureText(elevText) + gap
        drawIcon(canvas, elevGainIcon, x, baseline, iconH)
    }

    /** Silhouette path for the current window; rebuilt only when the window moves. */
    private val profilePath = Path()
    private var pathWindow: Pair<Double, Double>? = null
    private var pathChart = RectF()

    private fun drawProfile(
        canvas: Canvas,
        s: ClimbUiState.Shown,
        active: ClimbUiState.ActiveClimb,
        chart: RectF,
    ) {
        val window = displayWindow(s, active)
        val winStart = window.first
        val winEnd = window.second
        if (winEnd <= winStart || cachePoints.isEmpty()) return

        val xScale = chart.width() / (winEnd - winStart)
        fun xOf(d: Double): Float = chart.left + ((d - winStart) * xScale).toFloat()
        fun yOf(elev: Double): Float = chart.bottom -
            ((elev - cacheMinElev) / cacheElevSpan * chart.height() * PROFILE_MAX_FRACTION).toFloat()

        if (pathWindow != window || pathChart != chart) {
            profilePath.rewind()
            profilePath.moveTo(xOf(cachePoints.first().distance), chart.bottom)
            for (p in cachePoints) {
                profilePath.lineTo(xOf(p.distance), yOf(p.elevation))
            }
            profilePath.lineTo(xOf(cachePoints.last().distance), chart.bottom)
            profilePath.close()
            pathWindow = window
            pathChart = RectF(chart)
        }

        // lighter band behind the next 500 m (the strip's range); only once the
        // climb has actually started
        if (active.distanceToStart <= 0) {
            val chunks = stripChunks(s, active)
            if (chunks.isNotEmpty()) {
                val bandStart = s.progress
                val bandEnd = chunks.last().end
                if (bandEnd > winStart && bandStart < winEnd) {
                    canvas.drawRect(
                        xOf(bandStart).coerceAtLeast(chart.left),
                        chart.top,
                        xOf(bandEnd).coerceAtMost(chart.right),
                        chart.bottom,
                        approachBgPaint,
                    )
                }
            }
        }

        canvas.save()
        canvas.clipRect(chart)
        canvas.clipPath(profilePath)
        for (seg in cacheSegments) {
            if (seg.end < winStart || seg.start > winEnd) continue
            fillPaint.color = if (seg.grade < 0 && palette.bands.last().first >= 0.0) {
                GradePalettes.descentColor
            } else {
                palette.colorFor(seg.grade)
            }
            canvas.drawRect(xOf(seg.start), chart.top, xOf(seg.end), chart.bottom, fillPaint)
        }
        if (s.progress > winStart) {
            canvas.drawRect(chart.left, chart.top, xOf(s.progress), chart.bottom, pastPaint)
        }
        canvas.restore()

        if (s.progress in winStart..winEnd) {
            val mx = xOf(s.progress)
            val my = yOf(elevationAtProgress(s, active))
            val r = chart.height() * 0.05f
            canvas.drawCircle(mx, my, r, markerPaint)
            canvas.drawCircle(mx, my, r * 0.45f, markerCenterPaint)
        }
    }

    // ------------------------------------------------------------ data fields

    private fun drawFields(
        canvas: Canvas,
        s: ClimbUiState.Shown,
        active: ClimbUiState.ActiveClimb,
        area: RectF,
    ) {
        val fields = settings.fullFields
        val gap = width * 0.015f
        val cellW = (area.width() - gap) / 2f
        val cellH = (area.height() - gap) / 2f
        for (i in fields.indices.take(4)) {
            val col = i % 2
            val row = i / 2
            val left = area.left + col * (cellW + gap)
            val top = area.top + row * (cellH + gap)
            val cell = RectF(left, top, left + cellW, top + cellH)
            canvas.drawRoundRect(cell, CELL_CORNER, CELL_CORNER, cellPaint)

            val pad = cellW * 0.05f
            subTextPaint.textSize = cellH * 0.22f
            subTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(fields[i].label, cell.right - pad, cell.top + cellH * 0.3f, subTextPaint)

            textPaint.textSize = cellH * 0.45f
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                fieldValue(fields[i], s, active),
                cell.right - pad,
                cell.bottom - cellH * 0.15f,
                textPaint,
            )
        }
    }

    private fun fieldValue(
        field: ClimbField,
        s: ClimbUiState.Shown,
        active: ClimbUiState.ActiveClimb,
    ): String = when (field) {
        ClimbField.DIST_TO_TOP -> formatValue(active.distanceToTop)
        ClimbField.ELEV_TO_TOP -> formatElevationValue(active.elevationToTop)
        ClimbField.CLIMB -> "${active.index + 1}/${s.totalCount}"
        ClimbField.GRADE -> currentGrade(s)?.let { "%.1f".format(it) } ?: "--"
        ClimbField.AVG_GRADE -> "%.1f".format(active.climb.avgGrade)
        ClimbField.LENGTH -> formatValue(active.climb.length)
        else -> field.dataTypeId?.let { sysFieldValue(field, sysValues[it]) } ?: "--"
    }

    /** System stream values arrive in SI units; format per field type. */
    private fun sysFieldValue(field: ClimbField, raw: Double?): String {
        raw ?: return "--"
        return when (field) {
            ClimbField.SPEED, ClimbField.AVG_SPEED ->
                "%.1f".format(raw * if (imperial) 2.23694 else 3.6) // m/s -> mph / km/h
            ClimbField.POWER, ClimbField.POWER_3S, ClimbField.HEART_RATE, ClimbField.CADENCE ->
                "${raw.toInt()}"
            ClimbField.ASCENT -> formatElevationValue(raw)
            ClimbField.DISTANCE -> formatValue(raw)
            else -> "%.1f".format(raw)
        }
    }

    // ------------------------------------------------------------ climbs list

    private fun drawListPanel(canvas: Canvas, s: ClimbUiState.Shown, limit: Int, scrollable: Boolean) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), panelBgPaint)

        val pad = width * 0.02f
        val headerH = height * if (scrollable) 0.1f else 0.15f
        // same style as the climb-panel header: top-aligned
        val big = headerFontSize(headerH)
        val baseline = big * 1.05f
        textPaint.textSize = big
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("CLIMBS ${s.completedCount}/${s.totalCount}", pad, baseline, textPaint)
        s.upcoming.firstOrNull()?.let { next ->
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                "NEXT IN ${formatValue(next.startDistance - s.progress)}",
                width - pad,
                baseline,
                textPaint,
            )
        }

        val rows = if (scrollable) s.climbs else s.upcoming.take(limit)
        if (rows.isEmpty()) {
            subTextPaint.textSize = headerH * 0.35f
            subTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("no climbs ahead", width / 2f, height / 2f, subTextPaint)
            return
        }

        val listTop = headerH + pad
        val gap = height * 0.012f
        val rowH = if (scrollable) height * 0.105f else (height - listTop - pad - 2 * gap) / 3f

        canvas.save()
        canvas.clipRect(0f, listTop, width.toFloat(), height - pad)
        val rowPad = width * 0.045f
        var y = listTop - if (scrollable) listScrollPx else 0f
        for (climb in rows) {
            if (y + rowH > listTop && y < height) {
                drawClimbRow(canvas, s, climb, RectF(rowPad, y, width - rowPad, y + rowH))
            }
            y += rowH + gap
        }
        canvas.restore()

        lastRowsHeight = rows.size * (rowH + gap) - gap
        lastListAreaHeight = height - pad - listTop
    }

    private fun drawClimbRow(canvas: Canvas, s: ClimbUiState.Shown, climb: ClimbData, rect: RectF) {
        val done = s.progress >= climb.endDistance
        rowPaint.color = if (done) 0xFF4A4A4A.toInt() else palette.colorFor(climb.avgGrade)
        canvas.drawRoundRect(rect, CELL_CORNER, CELL_CORNER, rowPaint)

        rowTextPaint.textSize = rect.height() * 0.55f
        rowTextPaint.textAlign = Paint.Align.CENTER
        val cy = rect.centerY() - (rowTextPaint.ascent() + rowTextPaint.descent()) / 2f
        val colW = rect.width() / 3f
        // unitless: to-start always in km (0.7), length in km, grade in %
        if (done) {
            // bottom-aligned with the row text baseline, like the header icons
            val iconH = rect.height() * 0.42f
            val iconW = iconWidth(doneIcon, iconH)
            drawIcon(canvas, doneIcon, rect.left + colW * 0.5f - iconW / 2f, cy, iconH)
        } else {
            val toStart = if (s.progress >= climb.startDistance) {
                "now"
            } else {
                formatKm(climb.startDistance - s.progress)
            }
            canvas.drawText(toStart, rect.left + colW * 0.5f, cy, rowTextPaint)
        }
        canvas.drawText(formatKm(climb.length), rect.left + colW * 1.5f, cy, rowTextPaint)
        canvas.drawText("%.1f".format(climb.avgGrade), rect.left + colW * 2.5f, cy, rowTextPaint)

        // column splices
        val inset = rect.height() * 0.2f
        canvas.drawLine(rect.left + colW, rect.top + inset, rect.left + colW, rect.bottom - inset, splicePaint)
        canvas.drawLine(rect.left + 2 * colW, rect.top + inset, rect.left + 2 * colW, rect.bottom - inset, splicePaint)
    }

    private var lastRowsHeight = 0f
    private var lastListAreaHeight = 0f

    private fun maxListScroll(): Float = (lastRowsHeight - lastListAreaHeight).coerceAtLeast(0f)

    // ---------------------------------------------------------------- helpers

    /** Visible distance range along the route for the current mode. */
    private fun displayWindow(
        s: ClimbUiState.Shown,
        active: ClimbUiState.ActiveClimb,
    ): Pair<Double, Double> {
        val climb = active.climb
        val pos = s.progress.coerceIn(climb.startDistance, climb.endDistance)
        return when (val m = machine.mode) {
            is ViewModeMachine.Mode.Base -> when (settings.baseMode) {
                BaseMode.REMAINING -> pos to climb.endDistance
                BaseMode.FULL, BaseMode.BOTH -> climb.startDistance to climb.endDistance
            }
            is ViewModeMachine.Mode.Alt -> pos to climb.endDistance
            is ViewModeMachine.Mode.Preview ->
                pos to min(pos + m.windowMeters, climb.endDistance)
        }
    }

    private fun elevationAtProgress(s: ClimbUiState.Shown, active: ClimbUiState.ActiveClimb): Double =
        s.profile?.elevationAt(s.progress)
            ?: (active.climb.totalElevation *
                ((s.progress - active.climb.startDistance) / active.climb.length).coerceIn(0.0, 1.0))

    /** Grade of the cached 100 m chunk the rider is currently in. */
    private fun currentGrade(s: ClimbUiState.Shown): Double? {
        val active = s.active ?: return null
        if (active.distanceToStart > 0) return null
        return cacheSegments.firstOrNull { s.progress >= it.start && s.progress < it.end }?.grade
            ?: cacheSegments.lastOrNull()?.grade
    }

    // ------------------------------------------------------------------ units

    private fun formatDistance(meters: Double): String = if (imperial) {
        val miles = meters / 1609.344
        if (miles < 0.1) "${(meters * 3.28084).toInt()} ft" else "%.1f mi".format(miles)
    } else {
        if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000.0)
    }

    private fun formatElevation(meters: Double): String =
        if (imperial) "${(meters * 3.28084).toInt()} ft" else "${meters.toInt()} m"

    /** Unitless distance value (m under 1 km, km with one decimal above; mi imperial). */
    private fun formatValue(meters: Double): String = if (imperial) {
        val miles = meters / 1609.344
        if (miles < 0.1) "${(meters * 3.28084).toInt()}" else "%.1f".format(miles)
    } else {
        if (meters < 1000) "${meters.toInt()}" else "%.1f".format(meters / 1000.0)
    }

    /** Unitless elevation value. */
    private fun formatElevationValue(meters: Double): String =
        if (imperial) "${(meters * 3.28084).toInt()}" else "${meters.toInt()}"

    /** Always km (or mi), one decimal: 700 m -> "0.7". */
    private fun formatKm(meters: Double): String =
        "%.1f".format(if (imperial) meters / 1609.344 else meters / 1000.0)

    companion object {
        /** Leave headroom above the profile inside the chart. */
        private const val PROFILE_MAX_FRACTION = 0.88f

        /** Color chunk resolution along the climb. */
        private const val GRADE_CHUNK_METERS = 100.0

        private const val CELL_CORNER = 8f

        /** Cells in the upcoming-chunks strip (5 x 100 m = 500 m lookahead). */
        private const val CHUNK_STRIP_CELLS = 5

        /** Swipe gesture thresholds. */
        private const val SWIPE_MIN_DISTANCE_PX = 60f
        private const val SWIPE_MIN_VELOCITY = 300f
    }
}
