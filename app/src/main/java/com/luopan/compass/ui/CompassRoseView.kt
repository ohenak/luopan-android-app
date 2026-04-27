package com.luopan.compass.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CompassRoseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var headingDeg: Float = 0f
    private var confidence: String = "POOR"

    private val rosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFC8A020.toInt() // brand gold
    }
    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }
    private val southPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2D6E8A.toInt() // brand teal
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC8A020.toInt() // brand gold
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC8A020.toInt() // brand gold
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val needlePath = Path()
    private val outerRect = RectF()

    fun setHeading(deg: Float) {
        headingDeg = deg
        invalidate()
    }

    fun setConfidence(conf: String) {
        confidence = conf
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.85f

        // Rotate the entire compass card (ring + needle) together
        canvas.save()
        canvas.rotate(-headingDeg, cx, cy)
        drawBezel(canvas, cx, cy, radius)
        drawCardinalTicks(canvas, cx, cy, radius)
        drawNeedle(canvas, cx, cy, radius * 0.7f)
        canvas.restore()

    }

    private fun drawBezel(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        outerRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawOval(outerRect, rosePaint)
    }

    private fun drawCardinalTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val cardinals = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
        val innerR = radius * 0.88f
        val labelR = radius * 1.08f
        for ((label, angle) in cardinals) {
            val rad = Math.toRadians(angle.toDouble())
            val x1 = cx + innerR * sin(rad).toFloat()
            val y1 = cy - innerR * cos(rad).toFloat()
            val x2 = cx + radius * sin(rad).toFloat()
            val y2 = cy - radius * cos(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
            val lx = cx + labelR * sin(rad).toFloat()
            val ly = cy - labelR * cos(rad).toFloat() + textPaint.textSize / 3
            canvas.drawText(label, lx, ly, if (label == "N") northPaint.also { it.textSize = 36f; it.textAlign = Paint.Align.CENTER } else textPaint)
        }
        // Minor ticks every 10°
        for (deg in 0 until 360 step 10) {
            if (deg % 90 == 0) continue
            val rad = Math.toRadians(deg.toDouble())
            val innerR2 = radius * 0.93f
            val x1 = cx + innerR2 * sin(rad).toFloat()
            val y1 = cy - innerR2 * cos(rad).toFloat()
            val x2 = cx + radius * sin(rad).toFloat()
            val y2 = cy - radius * cos(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, length: Float) {
        needlePath.reset()
        val tipY = cy - length
        val baseWidth = length * 0.08f
        needlePath.moveTo(cx, tipY)
        needlePath.lineTo(cx - baseWidth, cy)
        needlePath.lineTo(cx + baseWidth, cy)
        needlePath.close()
        canvas.drawPath(needlePath, northPaint)

        // South half
        val southPath = Path()
        southPath.moveTo(cx, cy + length * 0.6f)
        southPath.lineTo(cx - baseWidth, cy)
        southPath.lineTo(cx + baseWidth, cy)
        southPath.close()
        canvas.drawPath(southPath, southPaint)
    }
}
