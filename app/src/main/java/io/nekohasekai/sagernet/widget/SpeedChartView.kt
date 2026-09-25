package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
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

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var accentColor: Int = Color.RED
    private val dataPoints = mutableListOf<Long>()
    private val maxDataPoints = 30
    private var displayMaxSpeed = 100 * 1024L // 100 KB/s 基础量程

    init {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
        accentColor = typedValue.data
        linePaint.color = accentColor

        repeat(maxDataPoints) {
            dataPoints.add(0L)
        }
    }

    fun setSpeedData(history: List<Long>) {
        dataPoints.clear()
        dataPoints.addAll(history.takeLast(maxDataPoints))
        while (dataPoints.size < maxDataPoints) {
            dataPoints.add(0, 0L)
        }
        recalculateMax()
        invalidate()
    }



    private fun recalculateMax() {
        val peak = dataPoints.maxOrNull() ?: 0L
        displayMaxSpeed = when {
            peak > (50 * 1024 * 1024L) -> 100 * 1024 * 1024L
            peak > (10 * 1024 * 1024L) -> 50 * 1024 * 1024L
            peak > (2 * 1024 * 1024L) -> 10 * 1024 * 1024L
            peak > (500 * 1024L) -> 2 * 1024 * 1024L
            peak > (100 * 1024L) -> 500 * 1024L
            else -> 100 * 1024L
        }
    }

    private val path = Path()
    private val fillPath = Path()
    private var gradient: LinearGradient? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            gradient = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(accentColor.withAlpha(0.35f), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val stepX = w / (maxDataPoints - 1)

        path.reset()
        fillPath.reset()

        val maxVal = displayMaxSpeed.toFloat()
        val usableHeight = h * 0.85f

        var prevX = 0f
        val firstRatio = (dataPoints[0].toFloat() / maxVal).coerceIn(0f, 1f)
        var prevY = h - (firstRatio * usableHeight) - dp2px(2)

        path.moveTo(prevX, prevY)
        fillPath.moveTo(prevX, h)
        fillPath.lineTo(prevX, prevY)

        for (i in 1 until dataPoints.size) {
            val x = i * stepX
            val ratio = (dataPoints[i].toFloat() / maxVal).coerceIn(0f, 1f)
            val y = h - (ratio * usableHeight) - dp2px(2)

            val controlX = (prevX + x) / 2f
            path.cubicTo(controlX, prevY, controlX, y, x, y)
            fillPath.cubicTo(controlX, prevY, controlX, y, x, y)

            prevX = x
            prevY = y
        }

        fillPath.lineTo(w, h)
        fillPath.close()

        fillPaint.shader = gradient

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }

    private fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }
}
