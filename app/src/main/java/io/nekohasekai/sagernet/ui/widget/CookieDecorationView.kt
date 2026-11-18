package io.nekohasekai.sagernet.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed
import kotlin.math.min

/**
 * Material 3 Expressive 12-sided cookie decoration shape matching FlClash `MaterialShapes.cookie12Sided`.
 * Features 12 soft scalloped lobes with zero sharp corners.
 */
class CookieDecorationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cookie12Polygon: RoundedPolygon by lazy {
        val rotateNeg90 = Matrix().apply { postRotate(-90f) }
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            radius = 1f,
            innerRadius = 0.8f,
            rounding = CornerRounding(0.5f),
            centerX = 0f,
            centerY = 0f
        ).transformed(rotateNeg90)
    }

    private val baseShapePath = Path()
    private val drawPath = Path()
    private val transformMatrix = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var progress: Float = 0f
    private var animator: ValueAnimator? = null

    @ColorInt
    private var baseColor: Int = 0

    @ColorInt
    private var activeColor: Int = 0

    init {
        cookie12Polygon.toPath(baseShapePath)
        updateColors()
    }

    fun updateColors() {
        val accent = io.nekohasekai.sagernet.utils.Theme.getAccentColor(context)
        baseColor = ColorUtils.setAlphaComponent(accent, (255 * 0.08f).toInt())
        activeColor = ColorUtils.setAlphaComponent(accent, (255 * 0.15f).toInt())
        paint.color = ColorUtils.blendARGB(baseColor, activeColor, progress.coerceIn(0f, 1f))
        invalidate()
    }

    fun setRunning(running: Boolean, animate: Boolean = true) {
        val target = if (running) 1f else 0f
        if (!animate) {
            animator?.cancel()
            progress = target
            updateDrawPath()
            invalidate()
            return
        }
        if (progress == target && animator?.isRunning != true) return

        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { va ->
                progress = va.animatedValue as Float
                updateDrawPath()
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateDrawPath()
    }

    private fun updateDrawPath() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = min(w, h) / 2f
        val cx = w / 2f
        val cy = h / 2f

        // FlClash: scale = 1 + 0.08 * progress, rotate = progress * 30 degrees
        val scale = radius * (1f + 0.08f * progress)
        val rotation = progress * 30f

        transformMatrix.reset()
        transformMatrix.postScale(scale, scale)
        transformMatrix.postRotate(rotation)
        transformMatrix.postTranslate(cx, cy)

        drawPath.reset()
        baseShapePath.transform(transformMatrix, drawPath)

        paint.color = ColorUtils.blendARGB(baseColor, activeColor, progress.coerceIn(0f, 1f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!drawPath.isEmpty) {
            canvas.drawPath(drawPath, paint)
        }
    }
}
