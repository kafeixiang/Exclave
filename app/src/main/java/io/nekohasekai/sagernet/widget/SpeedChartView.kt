package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px

class SpeedChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
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

    init {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, typedValue, true)
        accentColor = typedValue.data
        linePaint.color = accentColor
    }

    private val dataPoints = mutableListOf<Long>()
    private val maxDataPoints = 60
    private var maxValue = 1024L * 1024L // 1MB/s initial max

    fun addDataPoint(value: Long) {
        dataPoints.add(value)
        if (dataPoints.size > maxDataPoints) {
            dataPoints.removeAt(0)
        }
        
        // Recalculate max value with some padding and a minimum floor (10KB/s)
        val currentMax = dataPoints.maxOrNull() ?: 0L
        maxValue = maxOf(1024L * 10, currentMax)
        
        invalidate()
    }

    private val path = Path()
    private val fillPath = Path()
    private var gradient: LinearGradient? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(accentColor.withAlpha(0.3f), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.size < 2) return

        val w = width.toFloat()
        val h = height.toFloat()
        val stepX = w / (maxDataPoints - 1)

        path.reset()
        fillPath.reset()

        val startX = w - (dataPoints.size - 1) * stepX
        
        var prevX = startX
        var prevY = h - (dataPoints[0].toFloat() / maxValue.toFloat() * h * 0.8f) - dp2px(4)
        
        path.moveTo(prevX, prevY)
        fillPath.moveTo(prevX, h)
        fillPath.lineTo(prevX, prevY)

        for (i in 1 until dataPoints.size) {
            val x = startX + i * stepX
            val y = h - (dataPoints[i].toFloat() / maxValue.toFloat() * h * 0.8f) - dp2px(4)

            // Smooth curve using cubic bezier
            val controlX = (prevX + x) / 2
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
