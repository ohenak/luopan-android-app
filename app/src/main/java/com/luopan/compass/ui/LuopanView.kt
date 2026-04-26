package com.luopan.compass.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * Stub placeholder for the Luopan dial View.
 *
 * Task 4.1 replaces this stub with the full six-ring Canvas implementation
 * (TSPEC §6.1 — ring geometry pre-computation, sector label rendering,
 * gold tick mark, pinch-to-zoom, long-press for ring visibility sheet).
 *
 * The public API is complete so that [LuopanFragment] can wire all observers
 * without changes in Task 4.1. Each setter stores the value and calls [invalidate].
 */
class LuopanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var bearingDeg: Float = 0f
    private var zoomScale: Float = 1f
    private var ringVisible: BooleanArray = BooleanArray(6) { true }
    private var lockActive: Boolean = false
    private var displayXiangBearing: Float? = null

    /**
     * Updates the current bearing (degrees) and redraws.
     * Task 4.1 will rotate the ring assembly by -bearingDeg (FSPEC Flow 2, BR-11).
     */
    fun setBearingDeg(deg: Float) {
        bearingDeg = deg
        invalidate()
    }

    /**
     * Updates the zoom scale and redraws.
     * Task 4.1 will apply scale around dial center (FSPEC Flow 6).
     */
    fun setZoomScale(scale: Float) {
        zoomScale = scale
        invalidate()
    }

    /**
     * Updates all ring visibility flags at once and redraws.
     * [visible] is a 6-element BooleanArray indexed 0-based (Ring 1 = 0).
     * Task 4.1 skips drawing hidden rings (FSPEC Flow 5).
     */
    fun setRingVisible(visible: BooleanArray) {
        ringVisible = visible.clone()
        invalidate()
    }

    /**
     * Updates the 坐向 lock state and redraws.
     *
     * V3-F01 fix: receives [displayXiangBearing] (display-reference bearing, not stored
     * True North bearing) so the gold tick mark can be positioned for display (TSPEC §6.2).
     * When [active] is false, the tick mark is not drawn.
     *
     * Task 4.1 will draw the gold tick mark at [displayXiangBearing] on Ring 5 (TSPEC §6.1.4).
     */
    fun setLockState(active: Boolean, displayXiangBearing: Float?) {
        lockActive = active
        this.displayXiangBearing = displayXiangBearing
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // Stub: fill with the Luopan background colour.
        // Task 4.1 replaces this with full six-ring Canvas rendering (TSPEC §6.1.2).
        canvas.drawColor(0xFF2C0E0E.toInt())
    }
}
