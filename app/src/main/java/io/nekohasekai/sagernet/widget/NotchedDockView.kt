package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.dp2pxf
import kotlin.math.sqrt

class NotchedDockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp2px(1).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPath = Path()
    private val strokePath = Path()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp2px(3).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val cradlePath = Path()

    private var customFabCenterX = -1f
    private var customFabCenterY = Float.MIN_VALUE
    private var customFabRadius = -1f

    private val cradleMargin = dp2pxf(6)
    private val rShoulder = dp2pxf(18)
    private val cornerRadius = dp2pxf(24)

    private var isIslandMode = false

    private val rectLeftShoulder = RectF()
    private val rectCradle = RectF()
    private val rectRightShoulder = RectF()
    private val islandBounds = RectF()

    fun setIslandMode(island: Boolean) {
        if (isIslandMode != island) {
            isIslandMode = island
            updatePaths()
            invalidate()
        }
    }

    fun setFabParams(centerX: Float, centerY: Float, radius: Float) {
        if (customFabCenterX != centerX || customFabCenterY != centerY || customFabRadius != radius) {
            customFabCenterX = centerX
            customFabCenterY = centerY
            customFabRadius = radius
            updatePaths()
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePaths()
    }

    private fun fallbackPaths(w: Float, h: Float) {
        strokePath.reset()
        if (isIslandMode) {
            val r = cornerRadius.coerceAtMost(minOf(w / 2f, h / 2f))
            strokePath.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)
        } else {
            strokePath.addRect(0f, 0f, w, h, Path.Direction.CW)
        }
        fillPath.reset()
        fillPath.set(strokePath)
        cradlePath.reset()
    }

    private fun updatePaths() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        try {
            val cx = if (customFabCenterX >= 0f) customFabCenterX else w / 2f
            val cy = if (customFabCenterY != Float.MIN_VALUE) customFabCenterY else 0f
            val rFab = if (customFabRadius > 0f) customFabRadius else dp2pxf(28)
            val rCradle = rFab + cradleMargin

            val d = rCradle + rShoulder
            if (d <= 0f) {
                fallbackPaths(w, h)
                return
            }

            val deltaY = rShoulder - cy
            val distSq = d * d - deltaY * deltaY
            val deltaX = if (distSq > 0f) sqrt(distSq) else d

            val sxLeft = cx - deltaX
            val sxRight = cx + deltaX

            val txLeft = sxLeft + (cx - sxLeft) * (rShoulder / d)
            val tyLeft = rShoulder + (cy - rShoulder) * (rShoulder / d)

            val txRight = sxRight + (cx - sxRight) * (rShoulder / d)
            val tyRight = rShoulder + (cy - rShoulder) * (rShoulder / d)

            // Left shoulder: starts at flat top edge (270°), sweeps clockwise into the notch down to tangent point
            val aLeftT = ((Math.toDegrees(Math.atan2((tyLeft - rShoulder).toDouble(), (txLeft - sxLeft).toDouble())).toFloat()) + 360f) % 360f
            val sweepLeft = (aLeftT - 270f + 360f) % 360f

            // Cradle notch: starts at left tangent, sweeps counter-clockwise downwards through 90° (dipping into dock below FAB) to right tangent
            val aCradleStart = ((Math.toDegrees(Math.atan2((tyLeft - cy).toDouble(), (txLeft - cx).toDouble())).toFloat()) + 360f) % 360f
            val aCradleEnd = ((Math.toDegrees(Math.atan2((tyRight - cy).toDouble(), (txRight - cx).toDouble())).toFloat()) + 360f) % 360f
            var sweepCradle = aCradleEnd - aCradleStart
            if (sweepCradle > 0f) {
                sweepCradle -= 360f
            }

            // Right shoulder: starts at right tangent, sweeps clockwise up to flat top edge (270°)
            val aRightT = ((Math.toDegrees(Math.atan2((tyRight - rShoulder).toDouble(), (txRight - sxRight).toDouble())).toFloat()) + 360f) % 360f
            val sweepRight = (270f - aRightT + 360f) % 360f

            if (!deltaX.isFinite() || !sxLeft.isFinite() || !sxRight.isFinite() ||
                !txLeft.isFinite() || !tyLeft.isFinite() || !txRight.isFinite() || !tyRight.isFinite() ||
                !aLeftT.isFinite() || !sweepLeft.isFinite() ||
                !aCradleStart.isFinite() || !sweepCradle.isFinite() ||
                !aRightT.isFinite() || !sweepRight.isFinite()) {
                fallbackPaths(w, h)
                return
            }

            rectLeftShoulder.set(sxLeft - rShoulder, 0f, sxLeft + rShoulder, 2f * rShoulder)
            rectCradle.set(cx - rCradle, cy - rCradle, cx + rCradle, cy + rCradle)
            rectRightShoulder.set(sxRight - rShoulder, 0f, sxRight + rShoulder, 2f * rShoulder)

            if (isIslandMode) {
                val r = cornerRadius.coerceAtMost(minOf(w / 2f, h / 2f))
                strokePath.reset()
                strokePath.moveTo(0f, r)
                // Top-left corner
                strokePath.quadTo(0f, 0f, r, 0f)
                // Top edge to notch
                strokePath.lineTo(sxLeft.coerceIn(r, w - r), 0f)
                strokePath.arcTo(rectLeftShoulder, 270f, sweepLeft, false)
                strokePath.arcTo(rectCradle, aCradleStart, sweepCradle, false)
                strokePath.arcTo(rectRightShoulder, aRightT, sweepRight, false)
                // Top edge to right corner
                strokePath.lineTo(w - r, 0f)
                // Top-right corner
                strokePath.quadTo(w, 0f, w, r)
                // Right edge
                strokePath.lineTo(w, h - r)
                // Bottom-right corner
                strokePath.quadTo(w, h, w - r, h)
                // Bottom edge
                strokePath.lineTo(r, h)
                // Bottom-left corner
                strokePath.quadTo(0f, h, 0f, h - r)
                strokePath.close()

                cradlePath.reset()
                cradlePath.arcTo(rectCradle, aCradleStart, sweepCradle, false)

                fillPath.reset()
                fillPath.set(strokePath)
            } else {
                val r = cornerRadius.coerceAtMost(minOf(w / 4f, h / 2f))
                strokePath.reset()
                strokePath.moveTo(0f, h)
                strokePath.lineTo(0f, r)
                strokePath.quadTo(0f, 0f, r, 0f)
                strokePath.lineTo(sxLeft.coerceIn(r, w - r), 0f)
                strokePath.arcTo(rectLeftShoulder, 270f, sweepLeft, false)
                strokePath.arcTo(rectCradle, aCradleStart, sweepCradle, false)
                strokePath.arcTo(rectRightShoulder, aRightT, sweepRight, false)
                strokePath.lineTo(w - r, 0f)
                strokePath.quadTo(w, 0f, w, r)
                strokePath.lineTo(w, h)

                cradlePath.reset()
                cradlePath.arcTo(rectCradle, aCradleStart, sweepCradle, false)

                fillPath.reset()
                fillPath.set(strokePath)
                fillPath.lineTo(0f, h)
                fillPath.close()
            }
        } catch (_: Exception) {
            fallbackPaths(w, h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        try {
            if (isIslandMode) {
                fillPaint.color = ContextCompat.getColor(context, R.color.surface_glass_heavy)
                strokePaint.color = ContextCompat.getColor(context, R.color.card_stroke)
                canvas.drawPath(fillPath, fillPaint)
                canvas.drawPath(strokePath, strokePaint)
            } else {
                fillPaint.color = ContextCompat.getColor(context, R.color.glass_dock_bg)
                strokePaint.color = ContextCompat.getColor(context, R.color.glass_dock_stroke)
                canvas.drawPath(fillPath, fillPaint)
                canvas.drawPath(strokePath, strokePaint)
            }
        } catch (_: Exception) {}
    }

}
