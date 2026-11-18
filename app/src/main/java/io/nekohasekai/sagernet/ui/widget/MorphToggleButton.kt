package io.nekohasekai.sagernet.ui.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.utils.Theme
import kotlin.math.min

/**
 * Material 3 Expressive start/stop toggle button matching FlClash `_StartToggle`
 * combined with Exclave's Liquid Glass idle aesthetic:
 * - Stopped: translucent frosted glass circle with specular rim & accent play triangle.
 * - Connecting: accent-tinted translucent glass circle (center glyph hidden for spinner).
 * - Running: morphs into a 9-sided scalloped cookie (`cookie9Sided`) rotated 40 degrees,
 *   filled with theme accent color, white pause bars, and a matching cookie-shaped soft shadow.
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
    private val shadowPath = Path()
    private val transformMatrix = Matrix()

    private val playPath = Path()
    private val pauseBar1 = RectF()
    private val pauseBar2 = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var progress: Float = 0f
    private var morphAnimator: ValueAnimator? = null
    private var pressScale: Float = 1f

    private var isRunningState: Boolean = false
    private var isConnectingState: Boolean = false
    private var useAccentFill: Boolean = false

    @ColorInt
    private var primaryColor: Int = 0

    @ColorInt
    private var glassBgColor: Int = 0

    @ColorInt
    private var glassStrokeColor: Int = 0

    private var glassStrokeWidth: Float = 0f
    private val density: Float = resources.displayMetrics.density

    init {
        isClickable = true
        isFocusable = true
        updateThemeColors()
    }

    fun updateThemeColors() {
        primaryColor = Theme.getAccentColor(context)
        glassBgColor = Theme.getCardBackgroundColor(context, false)
        glassStrokeColor = Theme.getCardStrokeColor(context, false)
        glassStrokeWidth = try {
            resources.getDimension(R.dimen.card_stroke_width)
        } catch (_: Exception) {
            1.5f * density
        }
        strokePaint.strokeWidth = glassStrokeWidth
        shadowPaint.color = ColorUtils.setAlphaComponent(primaryColor, 64)
        shadowPaint.maskFilter = BlurMaskFilter(4.5f * density, BlurMaskFilter.Blur.NORMAL)
        invalidate()
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

    fun setConnecting(connecting: Boolean) {
        if (isConnectingState != connecting) {
            isConnectingState = connecting
            invalidate()
        }
    }

    fun setUseAccentFill(useAccent: Boolean) {
        if (useAccentFill != useAccent) {
            useAccentFill = useAccent
            invalidate()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return super.onTouchEvent(event)

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
        val baseRadius = min(w, h) / 2f * 0.84f * pressScale
        val t = progress.coerceIn(0f, 1f)

        // 1. Build Morph Shape Path (circle at 0f -> cookie9Sided at 1f)
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

        // 2. Soft drop shadow conforming strictly to the morphed cookie path (NEVER a circle)
        if (t > 0.05f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            shadowPaint.color = ColorUtils.setAlphaComponent(primaryColor, (64 * t).toInt().coerceIn(0, 255))
            shadowPath.set(transformedShapePath)
            shadowPath.offset(0f, 2.5f * density)
            canvas.drawPath(shadowPath, shadowPaint)
        }

        // 3. Draw Background Fill
        // Initial cold-start stopped state: transparent frosted glass.
        // Once started (and for all subsequent states): solid theme accent color.
        val idleFill = when {
            useAccentFill -> primaryColor
            isConnectingState -> ColorUtils.compositeColors(
                ColorUtils.setAlphaComponent(primaryColor, 40),
                glassBgColor
            )
            else -> glassBgColor
        }
        bgPaint.color = ColorUtils.blendARGB(idleFill, primaryColor, t)
        canvas.drawPath(transformedShapePath, bgPaint)

        // 4. Draw Glass Rim Stroke (only for initial transparent glass state)
        val strokeAlphaFactor = if (useAccentFill) 0f else (1f - t)
        if (strokeAlphaFactor > 0.01f) {
            val baseStrokeColor = if (isConnectingState) primaryColor else glassStrokeColor
            val targetAlpha = (Color.alpha(baseStrokeColor) * strokeAlphaFactor).toInt().coerceIn(0, 255)
            strokePaint.color = ColorUtils.setAlphaComponent(baseStrokeColor, targetAlpha)
            canvas.drawPath(transformedShapePath, strokePaint)
        }

        // 5. Draw Center Glyph (hidden while connecting spinner is shown)
        if (isConnectingState) return

        val glyphSize = baseRadius * 0.38f
        canvas.save()
        canvas.translate(cx, cy)

        if (progress < 0.5f) {
            // Stopped state: Accent-colored Play Triangle on transparent glass, or White on solid accent
            val alpha = ((1f - progress * 2f) * 255).toInt().coerceIn(0, 255)
            val playColor = if (useAccentFill) Color.WHITE else primaryColor
            glyphPaint.color = ColorUtils.setAlphaComponent(playColor, alpha)

            // Slightly offset triangle to the right for optical centering
            val offsetX = glyphSize * 0.12f
            playPath.reset()
            playPath.moveTo(-glyphSize * 0.6f + offsetX, -glyphSize * 0.75f)
            playPath.lineTo(glyphSize * 0.75f + offsetX, 0f)
            playPath.lineTo(-glyphSize * 0.6f + offsetX, glyphSize * 0.75f)
            playPath.close()

            canvas.drawPath(playPath, glyphPaint)
        } else {
            // Running state: White Pause Bars on accent cookie
            val alpha = (((progress - 0.5f) * 2f) * 255).toInt().coerceIn(0, 255)
            glyphPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, alpha)

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
