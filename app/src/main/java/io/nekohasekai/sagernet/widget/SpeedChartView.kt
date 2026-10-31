package io.nekohasekai.sagernet.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.R
import io.nekohasekai.sagernet.ktx.dp2px

class SpeedChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp2px(2).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowDotHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var accentColor: Int = Color.RED
    private val maxDataPoints = 30
    private var displayMaxSpeed = 100 * 1024L // 100 KB/s 基础量程

    // 平滑补间数组
    private val currentRatios = FloatArray(maxDataPoints)
    private val targetRatios = FloatArray(maxDataPoints)
    private val startRatios = FloatArray(maxDataPoints)
    private var transitionAnimator: ValueAnimator? = null

    private val path = Path()
    private val fillPath = Path()
    private var gradient: LinearGradient? = null

    init {
        resolveAccentColor()
    }

    private fun resolveAccentColor() {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
        accentColor = typedValue.data
        updatePaints()
    }

    fun updateAccentColor(newColor: Int) {
        if (accentColor != newColor) {
            accentColor = newColor
            updatePaints()
            updateGradient(height)
            invalidate()
        }
    }

    private fun updatePaints() {
        linePaint.color = accentColor
        glowDotPaint.color = accentColor
        glowDotHaloPaint.color = accentColor.withAlpha(0.25f)
    }

    fun setSpeedData(history: List<Long>, animate: Boolean = true) {
        val rawPoints = ArrayList<Long>(maxDataPoints)
        val slice = history.takeLast(maxDataPoints)
        while (rawPoints.size < maxDataPoints - slice.size) {
            rawPoints.add(0L)
        }
        rawPoints.addAll(slice)

        val peak = rawPoints.maxOrNull() ?: 0L
        displayMaxSpeed = when {
            peak > (50 * 1024 * 1024L) -> 100 * 1024 * 1024L
            peak > (10 * 1024 * 1024L) -> 50 * 1024 * 1024L
            peak > (2 * 1024 * 1024L) -> 10 * 1024 * 1024L
            peak > (500 * 1024L) -> 2 * 1024 * 1024L
            peak > (100 * 1024L) -> 500 * 1024L
            else -> 100 * 1024L
        }

        val maxVal = displayMaxSpeed.toFloat()
        for (i in 0 until maxDataPoints) {
            targetRatios[i] = (rawPoints[i].toFloat() / maxVal).coerceIn(0f, 1f)
        }

        if (!animate || visibility != VISIBLE) {
            System.arraycopy(targetRatios, 0, currentRatios, 0, maxDataPoints)
            invalidate()
            return
        }

        System.arraycopy(currentRatios, 0, startRatios, 0, maxDataPoints)
        transitionAnimator?.cancel()
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                for (i in 0 until maxDataPoints) {
                    currentRatios[i] = startRatios[i] + (targetRatios[i] - startRatios[i]) * fraction
                }
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradient(h)
    }

    private fun updateGradient(h: Int) {
        if (h > 0) {
            gradient = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(accentColor.withAlpha(0.36f), accentColor.withAlpha(0.08f), Color.TRANSPARENT),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP,
            )
            fillPaint.shader = gradient
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val stepX = w / (maxDataPoints - 1)
        val usableHeight = h * 0.82f
        val bottomMargin = dp2px(2).toFloat()

        path.reset()
        fillPath.reset()

        var prevX = 0f
        var prevY = h - (currentRatios[0] * usableHeight) - bottomMargin

        path.moveTo(prevX, prevY)
        fillPath.moveTo(prevX, h)
        fillPath.lineTo(prevX, prevY)

        for (i in 1 until maxDataPoints) {
            val x = i * stepX
            val y = h - (currentRatios[i] * usableHeight) - bottomMargin

            val controlX = (prevX + x) / 2f
            path.cubicTo(controlX, prevY, controlX, y, x, y)
            fillPath.cubicTo(controlX, prevY, controlX, y, x, y)

            prevX = x
            prevY = y
        }

        fillPath.lineTo(w, h)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        // 绘制最新点的发光呼吸圆点
        val lastX = (maxDataPoints - 1) * stepX
        val lastY = h - (currentRatios[maxDataPoints - 1] * usableHeight) - bottomMargin
        canvas.drawCircle(lastX, lastY, dp2px(5).toFloat(), glowDotHaloPaint)
        canvas.drawCircle(lastX, lastY, dp2px(2).toFloat(), glowDotPaint)
    }

    override fun onDetachedFromWindow() {
        transitionAnimator?.cancel()
        transitionAnimator = null
        super.onDetachedFromWindow()
    }

    private fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }
}
