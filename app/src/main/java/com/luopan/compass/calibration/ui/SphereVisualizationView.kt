package com.luopan.compass.calibration.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt

class SphereVisualizationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val NEIGHBORHOOD_COS_THRESHOLD = 0.966f // cos(15°)
        private const val DENSITY_THRESHOLD = 3
    }

    // Pre-allocated Paint objects (no allocation in onDraw)
    private val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(80, 100, 100, 100)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ovalRect = RectF()

    // Normalized samples stored as (nx, ny, nz) triples
    private val samples = mutableListOf<FloatArray>()
    // Projected (px, py) in view coordinates
    private val projected = mutableListOf<FloatArray>()

    // Density cache: parallel to samples list
    private val densityCache = mutableListOf<Int>()
    private var densityCacheDirty = true

    @Synchronized
    fun addSample(x: Float, y: Float, z: Float) {
        val len = sqrt(x*x + y*y + z*z)
        if (len < 1e-6f) return
        samples.add(floatArrayOf(x/len, y/len, z/len))
        projected.add(FloatArray(2))
        densityCache.add(0)
        densityCacheDirty = true
        postInvalidate()
    }

    @Synchronized
    fun clearSamples() {
        samples.clear()
        projected.clear()
        densityCache.clear()
        densityCacheDirty = true
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) * 0.85f

        // Draw wireframe
        ovalRect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawOval(ovalRect, wirePaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, wirePaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, wirePaint)

        synchronized(this) {
            val n = samples.size
            if (n == 0) return

            // Project samples: simple orthographic (x=right, y=up, z=toward viewer)
            for (i in 0 until n) {
                val s = samples[i]
                projected[i][0] = cx + s[0] * r
                projected[i][1] = cy - s[1] * r
            }

            // Recompute density if dirty
            if (densityCacheDirty) {
                for (i in 0 until n) {
                    var count = 0
                    val si = samples[i]
                    for (j in 0 until n) {
                        if (i == j) continue
                        val sj = samples[j]
                        val dot = si[0]*sj[0] + si[1]*sj[1] + si[2]*sj[2]
                        if (dot >= NEIGHBORHOOD_COS_THRESHOLD) count++
                    }
                    densityCache[i] = count
                }
                densityCacheDirty = false
            }

            // Draw dots
            for (i in 0 until n) {
                val density = densityCache[i]
                dotPaint.color = if (density >= DENSITY_THRESHOLD) {
                    Color.rgb(0, 200, 80)  // green
                } else {
                    Color.rgb(80, 80, 160)  // grey-blue
                }
                canvas.drawCircle(projected[i][0], projected[i][1], 4f, dotPaint)
            }
        }
    }
}
