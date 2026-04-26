package com.luopan.compass.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.luopan.compass.R
import com.luopan.compass.luopan.RingLabelProvider
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hardware-accelerated six-ring Canvas View rendering the Luopan dial.
 *
 * Design principles (TSPEC §6.1, §10):
 * - All ring geometry (radii, sector centre angles, label [PointF]s, arc paths) is
 *   pre-computed in [onSizeChanged] — zero trigonometric computation in [onDraw].
 * - [Paint] objects are allocated once in [init] — never inside [onDraw].
 * - The CJK typeface is loaded once via the companion [initTypeface].
 * - Hardware acceleration is enabled via [LAYER_TYPE_HARDWARE].
 *
 * Coordinate system convention (TSPEC §6.1.1):
 * - 0° (North) points up — i.e., (cx, cy - r).
 * - Angle increases clockwise (standard compass convention).
 * - Label position for sector centre at `angleDeg`:
 *     x = cx + r·sin(angleRad)
 *     y = cy − r·cos(angleRad)
 *
 * Rotation transform (FSPEC Flow 2, BR-11):
 * - `canvas.rotate(-bearingDeg, cx, cy)` — the entire ring assembly counter-rotates
 *   relative to the device heading so the physically-north sector stays aligned
 *   under the fixed pointer.
 *
 * Pointer drawn in screen space (TSPEC §6.1.2):
 * - [drawFixedPointer] is called AFTER [canvas.restore] so the pointer never rotates.
 * - [POINTER_DRAWN_IN_SCREEN_SPACE] documents and tests this invariant.
 */
class LuopanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // -----------------------------------------------------------------------
    // Companion — typeface and architecture constants
    // -----------------------------------------------------------------------

    companion object {
        /**
         * Architecture invariant: the fixed pointer is drawn outside the rotation
         * transform (in screen space). Verified by [LuopanViewTest.pointer_not_in_ring_rotation_matrix].
         */
        const val POINTER_DRAWN_IN_SCREEN_SPACE = true

        private var cjkTypeface: Typeface = Typeface.DEFAULT

        /**
         * Loads the CJK typeface once. Call from [LuopanFragment.onViewCreated].
         * Falls back to [Typeface.DEFAULT] if the font resource is missing (TSPEC §11.4).
         */
        fun initTypeface(context: Context) {
            val loaded = try {
                ResourcesCompat.getFont(context, R.font.noto_serif_cjk_tc)
            } catch (e: Exception) {
                null
            }
            cjkTypeface = loaded ?: Typeface.DEFAULT
        }
    }

    // -----------------------------------------------------------------------
    // State — mutable, set via public API
    // -----------------------------------------------------------------------

    /**
     * Current device heading in degrees [0°, 360°).
     * The dial rotates by [dialRotationDeg] = -[bearingDeg].
     */
    private var bearingDeg: Float = 0f

    private var zoomScale: Float = 1.0f
    private var ringVisible: BooleanArray = BooleanArray(6) { true }
    private var isLockActiveState: Boolean = false
    private var displayXiangBearingState: Float? = null

    // -----------------------------------------------------------------------
    // Derived state (BR-11)
    // -----------------------------------------------------------------------

    /**
     * Dial rotation applied via [canvas.rotate].
     * Equal to -[bearingDeg] (counter-clockwise when heading is positive).
     * FSPEC Flow 2, BR-11.
     */
    private var dialRotationDeg: Float = 0f

    // -----------------------------------------------------------------------
    // Geometry cache — populated in onSizeChanged, read in onDraw
    // -----------------------------------------------------------------------

    private var cx = 0f
    private var cy = 0f
    private var baseRadius = 0f

    /**
     * Ring outer radii as fractions of [baseRadius].
     * Index 0 = Ring 1 (天池 needle), index 5 = Ring 6 (六十分金).
     * TSPEC §6.1.1.
     */
    private val RING_RADIUS_FRACTIONS = floatArrayOf(
        0.12f,  // Ring 1 — 天池 needle circle
        0.30f,  // Ring 2 — 先天八卦
        0.48f,  // Ring 3 — 後天八卦
        0.62f,  // Ring 4 — 十二地支
        0.78f,  // Ring 5 — 二十四山
        1.00f   // Ring 6 — 六十分金
    )

    // Pre-computed sector label positions (PointF = text centre in canvas coords).
    // Populated in onSizeChanged; one entry per sector per ring.
    private var ring2LabelPositions: Array<PointF> = emptyArray()  // 8 entries
    private var ring3LabelPositions: Array<PointF> = emptyArray()  // 8 entries
    private var ring4LabelPositions: Array<PointF> = emptyArray()  // 12 entries
    private var ring5LabelPositions: Array<PointF> = emptyArray()  // 24 entries
    private var ring6LabelPositions: Array<PointF> = emptyArray()  // 60 entries

    // Pre-computed sector divider angles (in radians, measured from North, clockwise)
    private var ring2SectorAnglesRad: FloatArray = FloatArray(0)
    private var ring3SectorAnglesRad: FloatArray = FloatArray(0)
    private var ring4SectorAnglesRad: FloatArray = FloatArray(0)
    private var ring5SectorAnglesRad: FloatArray = FloatArray(0)
    private var ring6SectorAnglesRad: FloatArray = FloatArray(0)

    // Reusable RectF for arc drawing (allocated once, never in onDraw)
    @Suppress("unused")
    private val ringRect = RectF()

    // Gold tick mark screen position (recomputed in setLockState)
    private var tickInnerPoint = PointF(0f, 0f)
    private var tickOuterPoint = PointF(0f, 0f)

    // -----------------------------------------------------------------------
    // Paint objects — allocated once in init
    // -----------------------------------------------------------------------

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C0E0E")
        style = Paint.Style.FILL
    }

    private val ringBgPaintOdd = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D1010")
        style = Paint.Style.FILL
    }

    private val ringBgPaintEven = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E0A0A")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C9A84C")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val sectorDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C9A84C")
        style = Paint.Style.STROKE
        strokeWidth = 1f
        alpha = 100
    }

    // Text paints — sizes set in onSizeChanged (sp → px conversion)
    private val ring2TextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8C97A")
        textAlign = Paint.Align.CENTER
    }

    private val ring3TextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8C97A")
        textAlign = Paint.Align.CENTER
    }

    private val ring4TextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8C97A")
        textAlign = Paint.Align.CENTER
    }

    private val ring5TextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8C97A")
        textAlign = Paint.Align.CENTER
    }

    private val ring6TextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B89A5A")
        textAlign = Paint.Align.CENTER
    }

    // Needle paints
    private val needleNorthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC2200")
        style = Paint.Style.FILL
    }

    private val needleSouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }

    private val needleShaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val needleCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C0E0E")
        style = Paint.Style.FILL
    }

    // Pointer paint
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C9A84C")
        style = Paint.Style.FILL
    }

    // Gold tick mark paint
    private val goldTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C9A84C")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.BUTT
    }

    // -----------------------------------------------------------------------
    // Gesture detectors
    // -----------------------------------------------------------------------

    /** Callback invoked when pinch-to-zoom changes the scale. */
    var onZoomChanged: ((Float) -> Unit)? = null

    /** Callback invoked when a long-press is detected on the dial. */
    var onLongPressDetected: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (zoomScale * detector.scaleFactor).coerceIn(0.8f, 2.0f)
                zoomScale = newScale
                onZoomChanged?.invoke(newScale)
                invalidate()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                onLongPressDetected?.invoke()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    )

    // -----------------------------------------------------------------------
    // init
    // -----------------------------------------------------------------------

    init {
        // Enable hardware acceleration for GPU-backed Canvas (TSPEC §6.1, §10.1)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // -----------------------------------------------------------------------
    // Touch
    // -----------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    // -----------------------------------------------------------------------
    // Geometry pre-computation (onSizeChanged) — TSPEC §6.1.1
    // -----------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        baseRadius = min(cx, cy)

        @Suppress("DEPRECATION")
        val sp = resources.displayMetrics.scaledDensity  // sp-to-px conversion factor

        // Apply typeface to all text paints now that we have display metrics
        ring2TextPaint.typeface = cjkTypeface
        ring3TextPaint.typeface = cjkTypeface
        ring4TextPaint.typeface = cjkTypeface
        ring5TextPaint.typeface = cjkTypeface
        ring6TextPaint.typeface = cjkTypeface

        // Font sizes: Ring 6=8sp, Ring 5=11sp, Ring 4=12sp, Rings 2–3=14sp
        ring2TextPaint.textSize = 14f * sp
        ring3TextPaint.textSize = 14f * sp
        ring4TextPaint.textSize = 12f * sp
        ring5TextPaint.textSize = 11f * sp
        ring6TextPaint.textSize = 8f * sp

        // Border stroke width in dp
        val dp = resources.displayMetrics.density
        borderPaint.strokeWidth = 1.5f * dp
        sectorDividerPaint.strokeWidth = 0.75f * dp
        goldTickPaint.strokeWidth = 4f * dp
        needleShaftPaint.strokeWidth = 4f * dp

        // Pre-compute label positions for Rings 2–6
        ring2LabelPositions = computeLabelPositions(
            count = 8, degreesPerSector = 45f,
            innerFraction = RING_RADIUS_FRACTIONS[0],
            outerFraction = RING_RADIUS_FRACTIONS[1],
            startOffsetDeg = 22.5f   // Centre of sector 0 = 0° North
        )
        ring3LabelPositions = computeLabelPositions(
            count = 8, degreesPerSector = 45f,
            innerFraction = RING_RADIUS_FRACTIONS[1],
            outerFraction = RING_RADIUS_FRACTIONS[2],
            startOffsetDeg = 22.5f
        )
        ring4LabelPositions = computeLabelPositions(
            count = 12, degreesPerSector = 30f,
            innerFraction = RING_RADIUS_FRACTIONS[2],
            outerFraction = RING_RADIUS_FRACTIONS[3],
            startOffsetDeg = 15f     // Centre of sector 0 (子) = 0° North
        )
        ring5LabelPositions = computeLabelPositions(
            count = 24, degreesPerSector = 15f,
            innerFraction = RING_RADIUS_FRACTIONS[3],
            outerFraction = RING_RADIUS_FRACTIONS[4],
            startOffsetDeg = 7.5f   // Centre of first sector north of 337.5° start
        )
        ring6LabelPositions = computeLabelPositions(
            count = 60, degreesPerSector = 6f,
            innerFraction = RING_RADIUS_FRACTIONS[4],
            outerFraction = RING_RADIUS_FRACTIONS[5],
            startOffsetDeg = 355f   // First sector 庚子 centred at 355° (352–358° → mid 355°)
        )

        // Pre-compute sector divider angles
        ring2SectorAnglesRad = computeSectorBoundaryAnglesRad(8, 45f, 0f)
        ring3SectorAnglesRad = computeSectorBoundaryAnglesRad(8, 45f, 0f)
        ring4SectorAnglesRad = computeSectorBoundaryAnglesRad(12, 30f, 345f)
        ring5SectorAnglesRad = computeSectorBoundaryAnglesRad(24, 15f, 337.5f)
        ring6SectorAnglesRad = computeSectorBoundaryAnglesRad(60, 6f, 352f)

        // Recompute tick mark position if lock is active
        recomputeTickPosition()
    }

    /**
     * Computes [count] label centre positions for a ring band.
     *
     * @param count           Number of sectors.
     * @param degreesPerSector Width of each sector in degrees.
     * @param innerFraction   Inner edge as fraction of [baseRadius].
     * @param outerFraction   Outer edge as fraction of [baseRadius].
     * @param startOffsetDeg  Bearing (degrees from North, clockwise) of the first sector centre.
     */
    private fun computeLabelPositions(
        count: Int,
        degreesPerSector: Float,
        innerFraction: Float,
        outerFraction: Float,
        startOffsetDeg: Float = 0f
    ): Array<PointF> {
        val midFraction = (innerFraction + outerFraction) / 2f
        val midRadius = baseRadius * midFraction
        return Array(count) { i ->
            val angleDeg = (startOffsetDeg + i * degreesPerSector) % 360f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            PointF(
                cx + midRadius * sin(angleRad).toFloat(),
                cy - midRadius * cos(angleRad).toFloat()
            )
        }
    }

    /**
     * Computes sector boundary angles (in radians from North, clockwise)
     * for [count] sectors of [degreesPerSector] width, starting at [startDeg].
     */
    private fun computeSectorBoundaryAnglesRad(
        count: Int,
        degreesPerSector: Float,
        startDeg: Float
    ): FloatArray {
        return FloatArray(count) { i ->
            Math.toRadians(((startDeg + i * degreesPerSector) % 360f).toDouble()).toFloat()
        }
    }

    /**
     * Recomputes the gold tick mark line endpoints based on [displayXiangBearingState].
     * Called from [setLockState] and [onSizeChanged].
     */
    private fun recomputeTickPosition() {
        val tickBearing = displayXiangBearingState ?: return
        if (baseRadius == 0f) return
        val tickAngleRad = Math.toRadians(tickBearing.toDouble())
        val outerR = baseRadius * RING_RADIUS_FRACTIONS[4]  // outer edge of Ring 5
        val innerR = outerR * 0.92f
        tickInnerPoint = PointF(
            cx + (innerR * sin(tickAngleRad)).toFloat(),
            cy - (innerR * cos(tickAngleRad)).toFloat()
        )
        tickOuterPoint = PointF(
            cx + (outerR * sin(tickAngleRad)).toFloat(),
            cy - (outerR * cos(tickAngleRad)).toFloat()
        )
    }

    // -----------------------------------------------------------------------
    // onDraw — TSPEC §6.1.2. MUST complete in ≤16 ms. No allocation or trig.
    // -----------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Apply zoom scale centred on dial centre
        canvas.save()
        canvas.scale(zoomScale, zoomScale, cx, cy)

        // 2. Apply counter-rotation (BR-11: dialRotationDeg = -bearingDeg)
        canvas.save()
        canvas.rotate(dialRotationDeg, cx, cy)

        // 3. Draw background disc
        drawBackground(canvas)

        // 4. Draw ring backgrounds (alternating) and ring borders
        drawRingBands(canvas)

        // 5. Draw labels for visible rings
        if (ringVisible[0]) drawRing1Needle(canvas)
        if (ringVisible[1]) drawRing2Labels(canvas)
        if (ringVisible[2]) drawRing3Labels(canvas)
        if (ringVisible[3]) drawRing4Labels(canvas)
        if (ringVisible[4]) drawRing5Labels(canvas)
        if (ringVisible[5]) drawRing6Labels(canvas)

        // 6. Gold tick mark — drawn inside rotation so it rotates with the dial
        if (isLockActiveState && ringVisible[4]) drawGoldTickMark(canvas)

        canvas.restore()  // undo rotation

        // *** POINTER IS DRAWN AFTER RESTORE — in screen space (not rotated) ***
        // TSPEC §6.1.2, FSPEC Flow 2 step 4: pointer stays fixed at screen top.
        drawFixedPointer(canvas)

        canvas.restore()  // undo zoom scale
    }

    // -----------------------------------------------------------------------
    // Draw helpers
    // -----------------------------------------------------------------------

    private fun drawBackground(canvas: Canvas) {
        canvas.drawCircle(cx, cy, baseRadius, backgroundPaint)
    }

    /**
     * Draws all six ring bands (alternating backgrounds) and ring border circles.
     *
     * Strategy: paint from outermost ring (index 5) inward to innermost (index 0).
     * Each inner fill circle covers the outer one, naturally producing the alternating
     * annulus effect without needing explicit annulus clipping. Then border circles
     * are drawn on top of all fills.
     *
     * Alternating palette: odd ring index → [ringBgPaintOdd] (#3D1010),
     *                      even ring index → [ringBgPaintEven] (#1E0A0A).
     */
    private fun drawRingBands(canvas: Canvas) {
        // Step 1: fill rings from outside-in — each inner circle naturally clips the outer
        for (ringIdx in 5 downTo 0) {
            val outerR = baseRadius * RING_RADIUS_FRACTIONS[ringIdx]
            val bgPaint = if (ringIdx % 2 == 0) ringBgPaintEven else ringBgPaintOdd
            canvas.drawCircle(cx, cy, outerR, bgPaint)
        }

        // Step 2: draw border circles on top of all fills
        for (ringIdx in 0 until 6) {
            val outerR = baseRadius * RING_RADIUS_FRACTIONS[ringIdx]
            canvas.drawCircle(cx, cy, outerR, borderPaint)
        }
    }

    /**
     * Draws Ring 1 (天池) as a centre needle.
     * North tip is red (#CC2200), south tip is white (#F5F5F5).
     * TSPEC §6.1.3.
     */
    private fun drawRing1Needle(canvas: Canvas) {
        val needleRadius = baseRadius * RING_RADIUS_FRACTIONS[0] * 0.85f
        val shaftWidth = baseRadius * RING_RADIUS_FRACTIONS[0] * 0.15f

        // North half (red) — drawn as a filled triangle pointing up
        val northPath = Path().apply {
            moveTo(cx, cy - needleRadius)                    // North tip (up = North)
            lineTo(cx - shaftWidth * 0.5f, cy)              // Left midpoint
            lineTo(cx + shaftWidth * 0.5f, cy)              // Right midpoint
            close()
        }
        canvas.drawPath(northPath, needleNorthPaint)

        // South half (white) — triangle pointing down
        val southPath = Path().apply {
            moveTo(cx, cy + needleRadius)                   // South tip (down = South)
            lineTo(cx - shaftWidth * 0.5f, cy)             // Left midpoint
            lineTo(cx + shaftWidth * 0.5f, cy)             // Right midpoint
            close()
        }
        canvas.drawPath(southPath, needleSouthPaint)

        // Centre pivot circle
        val pivotRadius = shaftWidth * 0.6f
        canvas.drawCircle(cx, cy, pivotRadius, needleCirclePaint)
        canvas.drawCircle(cx, cy, pivotRadius, borderPaint)
    }

    /**
     * Draws Ring 2 (先天八卦) labels — 8 sectors × 45°.
     * Pre-computed positions from [ring2LabelPositions].
     */
    private fun drawRing2Labels(canvas: Canvas) {
        ring2LabelPositions.forEachIndexed { i, pos ->
            val label = RingLabelProvider.ring2Label(i)
            canvas.drawText(label.character, pos.x, pos.y + ring2TextPaint.textSize * 0.35f, ring2TextPaint)
        }
        drawSectorDividers(canvas, 8, ring2SectorAnglesRad,
            RING_RADIUS_FRACTIONS[0], RING_RADIUS_FRACTIONS[1])
    }

    /**
     * Draws Ring 3 (後天八卦) labels — 8 sectors × 45°.
     */
    private fun drawRing3Labels(canvas: Canvas) {
        ring3LabelPositions.forEachIndexed { i, pos ->
            val label = RingLabelProvider.ring3Label(i)
            // Ring 3 has compound labels "☵ 坎 北" — draw first CJK char only in ring
            val shortLabel = label.character.take(1)
            canvas.drawText(shortLabel, pos.x, pos.y + ring3TextPaint.textSize * 0.35f, ring3TextPaint)
        }
        drawSectorDividers(canvas, 8, ring3SectorAnglesRad,
            RING_RADIUS_FRACTIONS[1], RING_RADIUS_FRACTIONS[2])
    }

    /**
     * Draws Ring 4 (十二地支) labels — 12 sectors × 30°.
     */
    private fun drawRing4Labels(canvas: Canvas) {
        ring4LabelPositions.forEachIndexed { i, pos ->
            val label = RingLabelProvider.ring4Label(i)
            canvas.drawText(label.character, pos.x, pos.y + ring4TextPaint.textSize * 0.35f, ring4TextPaint)
        }
        drawSectorDividers(canvas, 12, ring4SectorAnglesRad,
            RING_RADIUS_FRACTIONS[2], RING_RADIUS_FRACTIONS[3])
    }

    /**
     * Draws Ring 5 (二十四山) labels — 24 sectors × 15°.
     */
    private fun drawRing5Labels(canvas: Canvas) {
        ring5LabelPositions.forEachIndexed { i, pos ->
            val label = RingLabelProvider.ring5Label(i)
            canvas.drawText(label.character, pos.x, pos.y + ring5TextPaint.textSize * 0.35f, ring5TextPaint)
        }
        drawSectorDividers(canvas, 24, ring5SectorAnglesRad,
            RING_RADIUS_FRACTIONS[3], RING_RADIUS_FRACTIONS[4])
    }

    /**
     * Draws Ring 6 (六十分金) labels — 60 sectors × 6°.
     * Labels are small (8sp); 2–4 characters each.
     */
    private fun drawRing6Labels(canvas: Canvas) {
        ring6LabelPositions.forEachIndexed { i, pos ->
            val label = RingLabelProvider.ring6Label(i)
            // Ring 6 labels are 4 chars "壬午分金" — draw abbreviated (first 2 chars) to fit
            val shortLabel = label.character.take(2)
            canvas.drawText(shortLabel, pos.x, pos.y + ring6TextPaint.textSize * 0.35f, ring6TextPaint)
        }
        drawSectorDividers(canvas, 60, ring6SectorAnglesRad,
            RING_RADIUS_FRACTIONS[4], RING_RADIUS_FRACTIONS[5])
    }

    /**
     * Draws thin gold sector divider lines for a ring band.
     *
     * @param count      Number of sectors (= number of dividers).
     * @param anglesRad  Pre-computed boundary angles in radians.
     * @param innerFrac  Inner radius fraction.
     * @param outerFrac  Outer radius fraction.
     */
    private fun drawSectorDividers(
        canvas: Canvas,
        count: Int,
        anglesRad: FloatArray,
        innerFrac: Float,
        outerFrac: Float
    ) {
        if (anglesRad.isEmpty()) return
        val innerR = baseRadius * innerFrac
        val outerR = baseRadius * outerFrac
        for (i in 0 until count) {
            val rad = anglesRad[i].toDouble()
            val sinA = sin(rad).toFloat()
            val cosA = cos(rad).toFloat()
            canvas.drawLine(
                cx + innerR * sinA, cy - innerR * cosA,
                cx + outerR * sinA, cy - outerR * cosA,
                sectorDividerPaint
            )
        }
    }

    /**
     * Draws the gold tick mark at [displayXiangBearingState] on Ring 5.
     * Drawn INSIDE the rotation transform so it rotates with the dial.
     * (The tick mark is at a fixed bearing relative to the ring assembly.)
     * TSPEC §6.1.4, V3-F01.
     */
    private fun drawGoldTickMark(canvas: Canvas) {
        if (displayXiangBearingState == null || baseRadius == 0f) return
        canvas.drawLine(
            tickInnerPoint.x, tickInnerPoint.y,
            tickOuterPoint.x, tickOuterPoint.y,
            goldTickPaint
        )
    }

    /**
     * Draws the fixed gold triangle pointer at screen top-centre.
     * Called AFTER [canvas.restore] — pointer is in screen space and does NOT rotate.
     * TSPEC §6.1.2, FSPEC Flow 2 step 4.
     *
     * [POINTER_DRAWN_IN_SCREEN_SPACE] documents this architectural invariant.
     */
    private fun drawFixedPointer(canvas: Canvas) {
        if (cx == 0f) return
        val pointerHeight = baseRadius * zoomScale * 0.06f
        val pointerWidth  = pointerHeight * 0.7f

        // Pointer centred at top of dial: (cx, cy - baseRadius * zoomScale)
        val tipY = cy - baseRadius * zoomScale
        val baseY = tipY + pointerHeight

        val pointerPath = Path().apply {
            moveTo(cx, tipY)
            lineTo(cx - pointerWidth, baseY)
            lineTo(cx + pointerWidth, baseY)
            close()
        }
        canvas.drawPath(pointerPath, pointerPaint)
    }

    // -----------------------------------------------------------------------
    // Public setter API — TSPEC §6.1.7
    // -----------------------------------------------------------------------

    /**
     * Updates the current device heading and redraws.
     * Stores [dialRotationDeg] = -[deg] (BR-11).
     */
    fun setBearingDeg(deg: Float) {
        bearingDeg = deg
        dialRotationDeg = -deg
        invalidate()
    }

    /**
     * Updates the zoom scale (clamped to [0.8, 2.0]) and redraws.
     * FSPEC Flow 6.
     */
    fun setZoomScale(scale: Float) {
        zoomScale = scale.coerceIn(0.8f, 2.0f)
        invalidate()
    }

    /**
     * Updates all six ring visibility flags atomically and redraws.
     * [visible] must have at least 6 elements (0-based: Ring 1 = index 0).
     * FSPEC Flow 5.
     */
    fun setRingVisible(visible: BooleanArray) {
        ringVisible = visible.clone()
        invalidate()
    }

    /**
     * Updates the 坐向 lock state and redraws.
     *
     * V3-F01 fix: [displayXiangBearing] is the display-reference bearing
     * (may be Magnetic North when the user has switched north type), NOT the
     * stored True North bearing. The gold tick mark is positioned using this
     * value so it visually matches the dial position seen by the user.
     *
     * TSPEC §6.1.4.
     */
    fun setLockState(active: Boolean, displayXiangBearing: Float?) {
        isLockActiveState = active
        displayXiangBearingState = displayXiangBearing
        recomputeTickPosition()
        invalidate()
    }

    // -----------------------------------------------------------------------
    // Test-visible state accessors (internal — for Robolectric tests only)
    // -----------------------------------------------------------------------

    /** Returns [dialRotationDeg] = -bearingDeg (BR-11). Visible for testing. */
    internal fun getDialRotationDeg(): Float = dialRotationDeg

    /** Returns the current clamped zoom scale. Visible for testing. */
    internal fun getZoomScale(): Float = zoomScale

    /** Returns whether ring at 0-based [index] is visible. Visible for testing. */
    internal fun isRingVisible(index: Int): Boolean = ringVisible[index]

    /** Returns whether the 坐向 lock is active. Visible for testing. */
    internal fun isLockActive(): Boolean = isLockActiveState

    /** Returns the stored display-reference xiang bearing (V3-F01). Visible for testing. */
    internal fun getDisplayXiangBearing(): Float? = displayXiangBearingState
}
