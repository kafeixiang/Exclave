package io.nekohasekai.sagernet.widget

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.utils.Theme

class CupertinoDock @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val waveView: WaveView
    private val upSpeedText: TextView
    private val downSpeedText: TextView
    private val fabGlow: View
    private val blurContainer: View
    val dockPanel: View
    val fabContainer: View
    val speedLayout: View
    val fab: FloatingActionButton
    private var isConnected = false
    private var accentColor = Color.RED
    private var isPanelHidden = false

    private var pulseAnimator: ObjectAnimator
    private var fabScaleAnimator: ObjectAnimator
    private var colorAnimator: ValueAnimator? = null

    init {
        clipChildren = false
        clipToPadding = false
        LayoutInflater.from(context).inflate(R.layout.layout_cupertino_dock, this, true)
        waveView = findViewById(R.id.wave_view)
        upSpeedText = findViewById(R.id.up_speed)
        downSpeedText = findViewById(R.id.down_speed)
        fabGlow = findViewById(R.id.fab_glow)
        blurContainer = findViewById(R.id.blur_container)
        dockPanel = findViewById(R.id.dock_panel)
        fabContainer = findViewById(R.id.fab_container)
        speedLayout = findViewById(R.id.speed_layout)
        fab = findViewById(R.id.fab)

        pulseAnimator = ObjectAnimator.ofFloat(fabGlow, "alpha", 0.15f, 0.65f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }

        fabScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(
            fab,
            PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.25f),
            PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.25f)
        ).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
    }

    fun hidePanel() {
        if (isPanelHidden) return
        isPanelHidden = true
        dockPanel.animate()
            .translationY(dockPanel.height.toFloat().coerceAtLeast(200f))
            .setDuration(220)
            .start()
        fabContainer.animate()
            .translationY(30f)
            .setDuration(220)
            .start()
    }

    fun showPanel() {
        if (!isPanelHidden) return
        isPanelHidden = false
        dockPanel.animate()
            .translationY(0f)
            .setDuration(220)
            .start()
        fabContainer.animate()
            .translationY(0f)
            .setDuration(220)
            .start()
    }

    private fun startColorCycling() {
        colorAnimator?.cancel()
        val hsv = FloatArray(3)
        Color.colorToHSV(accentColor, hsv)

        colorAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val offset = animator.animatedValue as Float
                val currentHsv = hsv.copyOf()
                currentHsv[0] = (hsv[0] + offset) % 360f
                val color = Color.HSVToColor(currentHsv)
                ViewCompat.setBackgroundTintList(fabGlow, ColorStateList.valueOf(color))
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
        // Convert Bytes/sec to KB/s for WaveView
        val combinedRateKB = (txRate + rxRate).toFloat() / 1024f
        waveView.updateSpeed(combinedRateKB)
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

        updateFabColors()

        if (isConnected) {
            pulseAnimator.start()
            fabScaleAnimator.start()
            startColorCycling()
        } else {
            pulseAnimator.cancel()
            fabScaleAnimator.cancel()
            colorAnimator?.cancel()
            fab.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
        }
    }

    fun setAccentColor(color: Int) {
        accentColor = color
        waveView.setAccentColor(color)
        updateFabColors()
    }

    private fun updateFabColors() {
        val isNight = Theme.usingNightMode()
        val isAccentLight = ColorUtils.calculateLuminance(accentColor) > 0.65

        if (isConnected) {
            fab.setImageResource(R.drawable.ic_service_connected)
            fab.supportBackgroundTintList = ColorStateList.valueOf(accentColor)
            val iconColor = if (isAccentLight) Color.BLACK else Color.WHITE
            fab.supportImageTintList = ColorStateList.valueOf(iconColor)
            fabGlow.visibility = VISIBLE
        } else {
            fab.setImageResource(R.drawable.ic_service_idle)
            val bgTint = if (isNight) Color.argb(255, 44, 44, 46) else Color.WHITE
            val iconColor = if (!isNight && isAccentLight) Color.argb(255, 28, 28, 30) else accentColor
            fab.supportBackgroundTintList = ColorStateList.valueOf(bgTint)
            fab.supportImageTintList = ColorStateList.valueOf(iconColor)
            fabGlow.visibility = GONE
        }
        ViewCompat.setBackgroundTintList(
            fabGlow,
            ColorStateList.valueOf(accentColor)
        )
    }
}
