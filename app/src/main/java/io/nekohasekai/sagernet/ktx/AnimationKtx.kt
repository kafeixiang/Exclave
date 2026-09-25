package io.nekohasekai.sagernet.ktx

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

@SuppressLint("ClickableViewAccessibility")
fun View.applyCardPressAnimation() {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(120).start()
            }
            MotionEvent.ACTION_UP -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                v.performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }
        false
    }
}

fun View.applyItemEntryAnimation(position: Int) {
    this.alpha = 0f
    this.translationY = 30f
    this.animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay((position * 25L).coerceAtMost(250L))
        .setDuration(220)
        .setInterpolator(DecelerateInterpolator())
        .start()
}
