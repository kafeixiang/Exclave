package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import kotlin.math.abs
import kotlin.math.sin

class WaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()
    private var flowOffset = 0f
    private val speedBuffer = FloatArray(40) { 5f }
    private var isConnected = false
    private var accentColor = Color.RED

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // For shadow/glow
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

    fun updateSpeed(speed: Float) {
        System.arraycopy(speedBuffer, 1, speedBuffer, 0, speedBuffer.size - 1)
        speedBuffer[speedBuffer.size - 1] = speed.coerceIn(5f, 50f)
    }

    private fun updateWave() {
        flowOffset += if (isConnected) 0.12f else 0.04f
        if (!isConnected) {
            for (i in speedBuffer.indices) {
                speedBuffer[i] = (speedBuffer[i] * 0.97f).coerceAtLeast(3f)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val widthF = width.toFloat()
        val heightF = height.toFloat()

        strokePaint.color = if (isConnected) accentColor else adjustAlpha(accentColor, 0.4f)
        strokePaint.strokeWidth = if (isConnected) 6f else 3f
        
        // Add subtle glow
        strokePaint.setShadowLayer(if (isConnected) 12f else 0f, 0f, 0f, accentColor)

        val slice = widthF / (speedBuffer.size - 1)
        val centerIdx = speedBuffer.size / 2f

        path.reset()
        for (i in speedBuffer.indices) {
            val x = i * slice
            val normalizedIdx = (i - centerIdx) / centerIdx
            val edgeDampening = 1f - abs(normalizedIdx) 
            
            val sineFreq = if (isConnected) 0.35f else 0.15f
            val sineAmp = if (isConnected) 20f else 5f
            val sineOffset = sin(i * sineFreq + flowOffset) * sineAmp * edgeDampening
            
            val y = heightF / 2f - (speedBuffer[i] / 60f) * heightF / 1.5f + sineOffset

            if (i == 0) path.moveTo(x, y)
            else {
                val prevX = (i - 1) * slice
                val prevNormalizedIdx = (i - 1 - centerIdx) / centerIdx
                val prevEdgeDampening = 1f - abs(prevNormalizedIdx)
                val prevSineOffset = sin((i - 1) * sineFreq + flowOffset) * sineAmp * prevEdgeDampening
                val prevY = heightF / 2f - (speedBuffer[i - 1] / 60f) * heightF / 1.5f + prevSineOffset
                
                path.cubicTo(prevX + slice / 2f, prevY, prevX + slice / 2f, y, x, y)
            }
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun adjustAlpha(@ColorInt color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
