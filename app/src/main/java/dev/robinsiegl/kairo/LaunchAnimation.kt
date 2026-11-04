package dev.robinsiegl.kairo

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.animation.doOnEnd
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Animate Android's existing launch surface once the app is ready to draw. */
@RequiresApi(Build.VERSION_CODES.S)
internal fun ComponentActivity.animateLaunch() {
    splashScreen.setOnExitAnimationListener { splash ->
        if (!ValueAnimator.areAnimatorsEnabled()) {
            splash.remove()
            return@setOnExitAnimationListener
        }

        val icon = splash.iconView
        val motion = PathInterpolator(0.2f, 0f, 0f, 1f)
        val scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.12f).apply {
            duration = 320
            interpolator = motion
        }
        val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.12f).apply {
            duration = 320
            interpolator = motion
        }
        val fade = ObjectAnimator.ofFloat(splash, View.ALPHA, 1f, 0f).apply {
            startDelay = 140
            duration = 180
        }
        val animation = AnimatorSet().apply { playTogether(scaleX, scaleY, fade) }
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                animation.cancel()
            }
        }
        animation.doOnEnd {
            splash.remove()
            lifecycle.removeObserver(observer)
        }
        lifecycle.addObserver(observer)
        animation.start()
    }
}
