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

        val animation = launchExitAnimation(splash, splash.iconView)
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


// Android may omit the icon on launches outside the app launcher.
internal fun launchExitAnimation(surface: View, icon: View?): AnimatorSet {
    val fade = ObjectAnimator.ofFloat(surface, View.ALPHA, 1f, 0f).apply {
        startDelay = if (icon == null) 0 else 140
        duration = 180
    }
    return AnimatorSet().apply {
        playTogether(fade)
        if (icon != null) {
            val motion = PathInterpolator(0.2f, 0f, 0f, 1f)
            val scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.12f).apply {
                duration = 320
                interpolator = motion
            }
            val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.12f).apply {
                duration = 320
                interpolator = motion
            }
            playTogether(scaleX, scaleY, fade)
        }
    }
}
