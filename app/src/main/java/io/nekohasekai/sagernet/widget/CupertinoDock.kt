package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat

class CupertinoDock @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val waveView: WaveView
    private val upSpeedText: TextView
    private val downSpeedText: TextView
    private val fabGlow: View
    private val blurContainer: View
    val speedLayout: View
    val fab: FloatingActionButton
    private var isConnected = false
    private var accentColor = android.graphics.Color.RED

    private lateinit var pulseAnimator: android.animation.ObjectAnimator
    private lateinit var fabScaleAnimator: android.animation.ObjectAnimator
    private var colorAnimator: android.animation.ValueAnimator? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_cupertino_dock, this, true)
        waveView = findViewById(R.id.wave_view)
        upSpeedText = findViewById(R.id.up_speed)
        downSpeedText = findViewById(R.id.down_speed)
        fabGlow = findViewById<View>(R.id.fab_glow)
        blurContainer = findViewById<View>(R.id.blur_container)
        speedLayout = findViewById<View>(R.id.speed_layout)
        fab = findViewById(R.id.fab)

        pulseAnimator = android.animation.ObjectAnimator.ofFloat(fabGlow, "alpha", 0.15f, 0.65f).apply {
            duration = 1000
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
        }

        fabScaleAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            fab,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.25f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.25f)
        ).apply {
            duration = 1200
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
        }

        applyFrostedEffect()

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun applyFrostedEffect() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            blurContainer.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    120f, 120f, android.graphics.Shader.TileMode.DECAL
                )
            )
        }
    }

    private fun startColorCycling() {
        colorAnimator?.cancel()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accentColor, hsv)
        
        colorAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 6000
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val offset = animator.animatedValue as Float
                val currentHsv = hsv.copyOf()
                currentHsv[0] = (hsv[0] + offset) % 360f
                val color = android.graphics.Color.HSVToColor(currentHsv)
                androidx.core.view.ViewCompat.setBackgroundTintList(fabGlow, android.content.res.ColorStateList.valueOf(color))
            }
            start()
        }
    }

    fun updateTraffic(txRate: Long, rxRate: Long) {
        upSpeedText.text = context.getString(
            R.string.speed, FormatFileSizeCompat.formatFileSize(context, txRate, DataStore.useIECUnit)
        )
        downSpeedText.text = context.getString(
            R.string.speed, FormatFileSizeCompat.formatFileSize(context, rxRate, DataStore.useIECUnit)
        )
        // Normalize speed for WaveView (0-50 range)
        val combinedRate = (txRate + rxRate).toFloat() / 1024f / 1024f // MB/s
        waveView.updateSpeed(5f + combinedRate * 8f)
    }

    fun changeState(state: BaseService.State) {
        val newState = state == BaseService.State.Connected
        if (newState != isConnected) {
            // Animate icon rotation on state change
            fab.animate()
                .rotation(if (newState) 360f else 0f)
                .setDuration(600)
                .withEndAction { 
                    fab.rotation = 0f // Reset for next time and ensure stability
                }
                .start()
        }
        isConnected = newState
        waveView.setConnected(isConnected)

        if (isConnected) {
            fab.setImageResource(R.drawable.ic_paper_plane_up)
            fab.supportBackgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
            fab.supportImageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            fabGlow.visibility = View.VISIBLE
            pulseAnimator.start()
            fabScaleAnimator.start()
            startColorCycling()
        } else {
            fab.setImageResource(R.drawable.ic_service_idle)
            fab.supportBackgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            fab.supportImageTintList = android.content.res.ColorStateList.valueOf(accentColor)
            pulseAnimator.cancel()
            fabScaleAnimator.cancel()
            colorAnimator?.cancel()
            fabGlow.visibility = View.GONE
            fab.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
            androidx.core.view.ViewCompat.setBackgroundTintList(fabGlow, android.content.res.ColorStateList.valueOf(accentColor))
        }
    }

    fun setAccentColor(color: Int) {
        accentColor = color
        waveView.setAccentColor(color)
        if (isConnected) {
            fab.supportBackgroundTintList = android.content.res.ColorStateList.valueOf(color)
            fab.supportImageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        } else {
            fab.supportBackgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            fab.supportImageTintList = android.content.res.ColorStateList.valueOf(color)
        }
        androidx.core.view.ViewCompat.setBackgroundTintList(fabGlow, android.content.res.ColorStateList.valueOf(color))
    }
}
