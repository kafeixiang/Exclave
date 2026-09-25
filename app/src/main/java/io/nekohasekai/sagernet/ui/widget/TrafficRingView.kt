package io.nekohasekai.sagernet.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.R
import androidx.core.graphics.toColorInt

class TrafficRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var accentColor: Int = "#FF2D55".toColorInt()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3.5f)
        strokeCap = Paint.Cap.ROUND
    }

    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(11f)
        isFakeBoldText = true
    }

    private val rectF = RectF()
    private var currentProgress = 0f // 0.0f .. 1.0f
    private var targetProgress = 0f
    private var pulseFraction = 0f
    private var isPulsing = false

    private var progressAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    init {
        resolveAccentColor()
        updatePaints()
    }

    private fun resolveAccentColor() {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(R.attr.colorAccent, typedValue, true)) {
            accentColor = typedValue.data
        }
    }

    private fun updatePaints() {
        trackPaint.color = Color.argb(40, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        progressPaint.color = accentColor
        textPaint.color = accentColor
    }

    /**
     * 设置流量使用百分占比（0.0f - 1.0f）
     */
    fun setProgressPercent(percent: Float, animate: Boolean = true) {
        val clamped = percent.coerceIn(0f, 1f)
        if (targetProgress == clamped) return
        targetProgress = clamped

        progressAnimator?.cancel()
        if (animate) {
            progressAnimator = ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
                duration = 800
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    currentProgress = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            currentProgress = targetProgress
            invalidate()
        }
    }

    /**
     * 触发连接/断开时的单次脉冲扩张过渡波
     */
    fun triggerConnectionPulse() {
        pulseAnimator?.cancel()
        isPulsing = true
        pulseFraction = 0f
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                pulseFraction = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) return

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = (width.coerceAtMost(height) / 2f) - dpToPx(8f)

        rectF.set(centerX - baseRadius, centerY - baseRadius, centerX + baseRadius, centerY + baseRadius)

        // 1. 绘制灰色底轨圆环
        canvas.drawCircle(centerX, centerY, baseRadius, trackPaint)

        // 2. 绘制流量百分占比进度弧（从 -90° 顶部顺时针绘制）
        val sweepAngle = currentProgress * 360f
        if (sweepAngle > 0f) {
            canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)
        }

        // 3. 连接/断开时的单次扩散脉冲波（非一直旋转）
        if (isPulsing && pulseFraction < 1f) {
            val pulseRadius = baseRadius + pulseFraction * dpToPx(8f)
            val alpha = ((1.0f - pulseFraction) * 180).toInt().coerceIn(0, 255)
            pulsePaint.color = Color.argb(alpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            canvas.drawCircle(centerX, centerY, pulseRadius, pulsePaint)
        }

        // 4. 绘制中心百分比文本 (例如 45%)
        val percentInt = (currentProgress * 100).toInt()
        val percentText = "$percentInt%"
        val textY = centerY - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(percentText, centerX, textY, textPaint)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
        pulseAnimator?.cancel()
    }
}
