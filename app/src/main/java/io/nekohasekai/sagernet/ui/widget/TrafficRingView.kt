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
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.R

class TrafficRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var downloadColor: Int = 0xFF007AFF.toInt()
    private var uploadColor: Int = 0xFFFF9500.toInt()
    private var textColor: Int = Color.BLACK

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3.5f)
        color = Color.argb(38, 128, 128, 128)
    }

    private val downloadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(4f)
        strokeCap = Paint.Cap.ROUND
    }

    private val uploadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(4f)
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

    private var currentDownloadAngle = 0f
    private var currentUploadAngle = 0f
    private var targetDownloadAngle = 0f
    private var targetUploadAngle = 0f

    private var centerText: String = "0%"

    private var pulseFraction = 0f
    private var isPulsing = false

    private var progressAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    init {
        resolveColors()
        updatePaints()
    }

    private fun resolveColors() {
        downloadColor = try {
            ContextCompat.getColor(context, R.color.cupertino_blue)
        } catch (_: Exception) {
            0xFF007AFF.toInt()
        }

        uploadColor = try {
            ContextCompat.getColor(context, R.color.cupertino_orange)
        } catch (_: Exception) {
            0xFFFF9500.toInt()
        }

        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
            textColor = typedValue.data
        }
    }

    private fun updatePaints() {
        downloadPaint.color = downloadColor
        uploadPaint.color = uploadColor
        textPaint.color = textColor
    }

    /**
     * 设置上传与下载流量数据，圆环上分别展示下载（蓝色）与上传（橙色）扇区
     */
    fun setTrafficData(
        uploadBytes: Long,
        downloadBytes: Long,
        totalUsedBytes: Long,
        totalQuotaBytes: Long? = null,
        animate: Boolean = false
    ) {
        val totalBytes = uploadBytes + downloadBytes

        // 计算圆环总体进度占 360 度的角度 (0f .. 360f)
        val overallSweep: Float = if (totalQuotaBytes != null && totalQuotaBytes > 0) {
            val progress = (totalUsedBytes.toFloat() / totalQuotaBytes.toFloat()).coerceIn(0f, 1f)
            centerText = "${(progress * 100).toInt()}%"
            progress * 360f
        } else {
            if (totalUsedBytes > 0 || totalBytes > 0) {
                val dlPct = if (totalBytes > 0) {
                    ((downloadBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
                } else 100
                centerText = "$dlPct%"
                360f
            } else {
                centerText = "0%"
                0f
            }
        }

        if (overallSweep <= 0f || (uploadBytes <= 0L && downloadBytes <= 0L && totalUsedBytes <= 0L)) {
            targetDownloadAngle = 0f
            targetUploadAngle = 0f
        } else if (totalBytes <= 0L) {
            // 如果仅有订阅已用数据但无本地分项统计，默认 85% 下行、15% 上行比例展示
            targetDownloadAngle = overallSweep * 0.85f
            targetUploadAngle = overallSweep * 0.15f
        } else {
            // 真实分项统计比例
            val dlRatio = downloadBytes.toFloat() / totalBytes.toFloat()
            val ulRatio = uploadBytes.toFloat() / totalBytes.toFloat()

            // 为极小的值保留保底可见角度 (至少 6 度)
            val minVisibleAngle = 6f
            var dlAngle = dlRatio * overallSweep
            var ulAngle = ulRatio * overallSweep

            if (uploadBytes > 0L && ulAngle < minVisibleAngle && overallSweep > minVisibleAngle * 2) {
                ulAngle = minVisibleAngle
                dlAngle = overallSweep - minVisibleAngle
            } else if (downloadBytes > 0L && dlAngle < minVisibleAngle && overallSweep > minVisibleAngle * 2) {
                dlAngle = minVisibleAngle
                ulAngle = overallSweep - minVisibleAngle
            }

            targetDownloadAngle = dlAngle
            targetUploadAngle = ulAngle
        }

        progressAnimator?.cancel()
        if (animate) {
            val startDl = currentDownloadAngle
            val startUl = currentUploadAngle
            progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 350
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val frac = anim.animatedValue as Float
                    currentDownloadAngle = startDl + (targetDownloadAngle - startDl) * frac
                    currentUploadAngle = startUl + (targetUploadAngle - startUl) * frac
                    invalidate()
                }
                start()
            }
        } else {
            currentDownloadAngle = targetDownloadAngle
            currentUploadAngle = targetUploadAngle
            invalidate()
        }
    }

    /**
     * 兼容接口：设置单个进度（默认当作下载进度）
     */
    fun setProgressPercent(percent: Float, animate: Boolean = false) {
        val clamped = percent.coerceIn(0f, 1f)
        targetDownloadAngle = clamped * 360f
        targetUploadAngle = 0f
        centerText = "${(clamped * 100).toInt()}%"

        progressAnimator?.cancel()
        if (animate) {
            val startDl = currentDownloadAngle
            progressAnimator = ValueAnimator.ofFloat(startDl, targetDownloadAngle).apply {
                duration = 350
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    currentDownloadAngle = anim.animatedValue as Float
                    currentUploadAngle = 0f
                    invalidate()
                }
                start()
            }
        } else {
            currentDownloadAngle = targetDownloadAngle
            currentUploadAngle = 0f
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

        // 1. 绘制底层灰色轨道圆环
        canvas.drawCircle(centerX, centerY, baseRadius, trackPaint)

        val dl = currentDownloadAngle
        val ul = currentUploadAngle
        val hasBoth = dl > 4f && ul > 4f
        val gap = if (hasBoth) 4f else 0f

        // 2. 绘制下载流量弧（蓝色，顺时针从顶部 -90° 开始）
        if (dl > 0f) {
            val sweepDl = if (hasBoth) (dl - gap).coerceAtLeast(1f) else dl
            canvas.drawArc(rectF, -90f, sweepDl, false, downloadPaint)
        }

        // 3. 绘制上传流量弧（橙色，接在下载弧之后）
        if (ul > 0f) {
            val startUl = if (hasBoth) -90f + dl else -90f + dl
            val sweepUl = if (hasBoth) (ul - gap).coerceAtLeast(1f) else ul
            canvas.drawArc(rectF, startUl, sweepUl, false, uploadPaint)
        }

        // 4. 连接/断开脉冲波
        if (isPulsing && pulseFraction < 1f) {
            val pulseRadius = baseRadius + pulseFraction * dpToPx(8f)
            val alpha = ((1.0f - pulseFraction) * 160).toInt().coerceIn(0, 255)
            pulsePaint.color = Color.argb(alpha, Color.red(downloadColor), Color.green(downloadColor), Color.blue(downloadColor))
            canvas.drawCircle(centerX, centerY, pulseRadius, pulsePaint)
        }

        // 5. 绘制中心百分比文本
        val textY = centerY - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(centerText, centerX, textY, textPaint)
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
