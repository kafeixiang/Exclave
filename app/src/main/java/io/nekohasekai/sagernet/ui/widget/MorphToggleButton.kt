package io.nekohasekai.sagernet.ui.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed
import kotlin.math.min

/**
 * Material 3 Expressive start/stop toggle button matching FlClash `_StartToggle`.
 * Morphs between a circle (stopped) and a 9-sided cookie (running), rotating by 40 degrees.
 */
class MorphToggleButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val circlePolygon: RoundedPolygon by lazy {
        RoundedPolygon.circle(
            numVertices = 8,
            radius = 1f,
            centerX = 0f,
            centerY = 0f
        )
    }

    private val cookie9Polygon: RoundedPolygon by lazy {
        val rotateNeg90 = Matrix().apply { postRotate(-90f) }
        RoundedPolygon.star(
            numVerticesPerRadius = 9,
            radius = 1f,
            innerRadius = 0.8f,
            rounding = CornerRounding(0.5f),
            centerX = 0f,
            centerY = 0f
        ).transformed(rotateNeg90)
    }

    private val morph: Morph by lazy {
        Morph(circlePolygon, cookie9Polygon)
    }

    private val shapePath = Path()
    private val transformedShapePath = Path()
    private val transformMatrix = Matrix()

    private val playPath = Path()
    private val pauseBar1 = RectF()
    private val pauseBar2 = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private var progress: Float = 0f
    private var morphAnimator: ValueAnimator? = null
    private var pressScale: Float = 1f

    private var isRunningState: Boolean = false
    private var isServiceEnabled: Boolean = true

    @ColorInt
    private var primaryColor: Int = 0

    @ColorInt
    private var disabledColor: Int = 0

    init {
        isClickable = true
        isFocusable = true
        updateThemeColors()
    }

    fun updateThemeColors() {
        primaryColor = io.nekohasekai.sagernet.utils.Theme.getAccentColor(context)
        val surfaceTone = resolveColor(context, com.google.android.material.R.attr.colorSurfaceVariant)
        disabledColor = ColorUtils.setAlphaComponent(surfaceTone, 180)
        shadowPaint.color = ColorUtils.setAlphaComponent(primaryColor, 76)
        invalidate()
    }

    private fun resolveColor(context: Context, @AttrRes attr: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) {
            tv.data
        } else {
            0xFF007AFF.toInt()
        }
    }

    fun setRunning(running: Boolean, animate: Boolean = true) {
        isRunningState = running
        val target = if (running) 1f else 0f
        if (!animate) {
            morphAnimator?.cancel()
            progress = target
            invalidate()
            return
        }
        if (progress == target && morphAnimator?.isRunning != true) return

        morphAnimator?.cancel()
        morphAnimator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { va ->
                progress = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setServiceEnabled(enabled: Boolean) {
        if (isServiceEnabled != enabled) {
            isServiceEnabled = enabled
            invalidate()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !isServiceEnabled) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressScale = 0.93f
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                pressScale = 1f
                invalidate()
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressScale = 1f
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val baseRadius = min(w, h) / 2f * 0.88f * pressScale

        // 1. Shadow for running state
        if (progress > 0.05f && isServiceEnabled) {
            shadowPaint.alpha = (76 * progress).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy + baseRadius * 0.08f, baseRadius * 0.98f, shadowPaint)
        }

        // 2. Morph Shape Path
        shapePath.reset()
        morph.toPath(progress, shapePath)

        // Rotation = progress * 40 degrees (2 * PI / 9)
        val rotationDeg = progress * 40f

        transformMatrix.reset()
        transformMatrix.postScale(baseRadius, baseRadius)
        transformMatrix.postRotate(rotationDeg)
        transformMatrix.postTranslate(cx, cy)

        transformedShapePath.reset()
        shapePath.transform(transformMatrix, transformedShapePath)

        // 3. Draw Morph Background
        bgPaint.color = if (isServiceEnabled) primaryColor else disabledColor
        canvas.drawPath(transformedShapePath, bgPaint)

        // 4. Draw Center Glyph (Play triangle when stopped, Pause bars when running)
        val glyphSize = baseRadius * 0.38f
        canvas.save()
        canvas.translate(cx, cy)

        if (progress < 0.5f) {
            // Stopped state: Play Triangle pointing right
            val alpha = ((1f - progress * 2f) * 255).toInt().coerceIn(0, 255)
            glyphPaint.alpha = alpha

            // Slightly offset triangle to the right for optical centering
            val offsetX = glyphSize * 0.12f
            playPath.reset()
            playPath.moveTo(-glyphSize * 0.6f + offsetX, -glyphSize * 0.75f)
            playPath.lineTo(glyphSize * 0.75f + offsetX, 0f)
            playPath.lineTo(-glyphSize * 0.6f + offsetX, glyphSize * 0.75f)
            playPath.close()

            canvas.drawPath(playPath, glyphPaint)
        } else {
            // Running state: Two Pause Bars
            val alpha = (((progress - 0.5f) * 2f) * 255).toInt().coerceIn(0, 255)
            glyphPaint.alpha = alpha

            val barW = glyphSize * 0.32f
            val barH = glyphSize * 1.3f
            val spacing = glyphSize * 0.28f
            val cornerRadius = glyphSize * 0.12f

            pauseBar1.set(-spacing / 2f - barW, -barH / 2f, -spacing / 2f, barH / 2f)
            pauseBar2.set(spacing / 2f, -barH / 2f, spacing / 2f + barW, barH / 2f)

            canvas.drawRoundRect(pauseBar1, cornerRadius, cornerRadius, glyphPaint)
            canvas.drawRoundRect(pauseBar2, cornerRadius, cornerRadius, glyphPaint)
        }
        canvas.restore()
    }
}
