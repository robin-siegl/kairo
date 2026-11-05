package dev.robinsiegl.kairo

import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchAnimationTest {
    @Test
    fun splashWithoutAnIconFadesWithoutCrashing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val surface = View(instrumentation.targetContext)
            launchExitAnimation(surface, null).apply {
                start()
                end()
            }
            assertEquals(0f, surface.alpha)
        }
    }

    @Test
    fun splashWithAnIconKeepsTheLogoAnimation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val surface = View(instrumentation.targetContext)
            val icon = View(instrumentation.targetContext)
            launchExitAnimation(surface, icon).apply {
                start()
                end()
            }
            assertEquals(0f, surface.alpha)
            assertEquals(1.12f, icon.scaleX)
            assertEquals(1.12f, icon.scaleY)
        }
    }
}
