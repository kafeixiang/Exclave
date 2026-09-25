package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import io.nekohasekai.sagernet.ktx.dp2px
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

class WaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp2px(2).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()
    private var flowOffset = 0f
    // Store recent speed samples in KB/s
    private val speedBuffer = FloatArray(32) { 0f }
    private var isConnected = false
    private var accentColor = Color.RED

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // For glow effect
        post(object : Runnable {
            override fun run() {
                updateWave()
                invalidate()
                postDelayed(this, 16)
            }
        })
    }

    fun setConnected(connected: Boolean) {
        isConnected = connected
    }

    fun setAccentColor(@ColorInt color: Int) {
        accentColor = color
    }

    /**
     * @param speedKB Real-time speed in KB/s
     */
    fun updateSpeed(speedKB: Float) {
        System.arraycopy(speedBuffer, 1, speedBuffer, 0, speedBuffer.size - 1)
        speedBuffer[speedBuffer.size - 1] = speedKB.coerceAtLeast(0f)
    }

    private fun updateWave() {
        val currentSpeedKB = speedBuffer.lastOrNull() ?: 0f
        // Flow speed increases dynamically with higher network speed
        val speedFactor = (log10(1f + currentSpeedKB) / 4f).coerceIn(0f, 1f)
        flowOffset += if (isConnected) (0.08f + speedFactor * 0.12f) else 0.03f

        if (!isConnected) {
            for (i in speedBuffer.indices) {
                speedBuffer[i] = (speedBuffer[i] * 0.92f).coerceAtLeast(0f)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val widthF = width.toFloat()
        val heightF = height.toFloat()

        strokePaint.color = if (isConnected) accentColor else adjustAlpha(accentColor)
        strokePaint.strokeWidth = if (isConnected) dp2px(2).toFloat() else dp2px(1).toFloat()

        // Glow effect
        strokePaint.setShadowLayer(if (isConnected) dp2px(4).toFloat() else 0f, 0f, 0f, accentColor)

        val slice = widthF / (speedBuffer.size - 1)
        val centerIdx = (speedBuffer.size - 1) / 2f

        path.reset()
        for (i in speedBuffer.indices) {
            val x = i * slice
            val normalizedIdx = (i - centerIdx) / centerIdx
            val edgeDampening = 1f - abs(normalizedIdx) * 0.5f // Gentle edge dampening

            val speedKB = speedBuffer[i]
            // Logarithmic speed factor: 0 KB/s -> 0.0, 10 KB/s -> 0.26, 100 KB/s -> 0.50, 1 MB/s -> 0.75, 10 MB/s -> 1.0
            val speedFactor = (log10(1f + speedKB) / 4f).coerceIn(0f, 1.2f)

            // Dynamic wave amplitude & frequency based on real-time speed at point i
            val sineFreq = if (isConnected) (0.25f + speedFactor * 0.25f) else 0.15f
            val sineAmp = if (isConnected) (dp2px(3).toFloat() + speedFactor * dp2px(12).toFloat()) else dp2px(2).toFloat()
            val sineOffset = sin(i * sineFreq + flowOffset) * sineAmp * edgeDampening

            // Baseline Y height fluctuates with real-time speed
            val speedYOffset = (speedFactor * dp2px(6).toFloat()) * edgeDampening
            val y = heightF / 2f - speedYOffset + sineOffset

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                val prevX = (i - 1) * slice
                val prevSpeedKB = speedBuffer[i - 1]
                val prevSpeedFactor = (log10(1f + prevSpeedKB) / 4f).coerceIn(0f, 1.2f)
                val prevNormalizedIdx = (i - 1 - centerIdx) / centerIdx
                val prevEdgeDampening = 1f - abs(prevNormalizedIdx) * 0.5f
                val prevSineFreq = if (isConnected) (0.25f + prevSpeedFactor * 0.25f) else 0.15f
                val prevSineAmp = if (isConnected) (dp2px(3).toFloat() + prevSpeedFactor * dp2px(12).toFloat()) else dp2px(2).toFloat()
                val prevSineOffset = sin((i - 1) * prevSineFreq + flowOffset) * prevSineAmp * prevEdgeDampening
                val prevSpeedYOffset = (prevSpeedFactor * dp2px(6).toFloat()) * prevEdgeDampening
                val prevY = heightF / 2f - prevSpeedYOffset + prevSineOffset

                path.cubicTo(prevX + slice / 2f, prevY, prevX + slice / 2f, y, x, y)
            }
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun adjustAlpha(@ColorInt color: Int, factor: Float = 0.4f): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
