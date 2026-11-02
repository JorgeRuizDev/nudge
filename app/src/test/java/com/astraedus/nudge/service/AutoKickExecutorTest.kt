package com.astraedus.nudge.service

import com.astraedus.nudge.domain.events.ForegroundSignal
import com.astraedus.nudge.domain.logging.NudgeLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoKickExecutorTest {

    private val targetApp = "com.brave.browser_beta"
    private val launcher = "com.sec.android.app.launcher"

    private object NoOpCounterOverlay : CounterOverlayManagerApi {
        var hideCount = 0
        override fun isVisible() = false
        override fun show(label: String) = Unit
        override fun updateCount(sessionCount: Int, dailyTotal: Int) = Unit
        override fun updateLabel(label: String) = Unit
        override fun hide() {
            hideCount++
        }
    }

    @Test
    fun `auto-kick revokes passthrough grant and sitting even when keepPassOnShortTrips is active`() {
        val passthrough = PassthroughManager()
        val tracker = InteractionTracker()
        val cache = CounterCacheRefresher()
        var homeRequested = false
        var kickedKey: String? = null

        val executor = AutoKickExecutor(
            interactionTracker = tracker,
            counterOverlayManager = NoOpCounterOverlay,
            counterCache = cache,
            logger = NudgeLog.NoOp,
            passthroughManager = passthrough,
            onKicked = { kickedKey = it },
            goHome = { homeRequested = true }
        )

        // 1. User earned pass with keepPassOnShortTrips = true
        passthrough.grant(targetApp, keepPassOnShortTrips = true)
        assertTrue(passthrough.isGranted(targetApp))
        assertTrue(passthrough.keepsPassOnShortTrips)

        // 2. Auto-kick triggers
        executor.kick(targetApp, reason = "session_time")

        // 3. Executor policy assertions:
        assertTrue("goHome must be called", homeRequested)
        assertEquals(targetApp, kickedKey)
        assertFalse("Grant must be immediately revoked", passthrough.isGranted(targetApp))
        assertFalse("Sitting must be reset, homeIsAbsence must be cleared", passthrough.keepsPassOnShortTrips)

        // 4. Going home and immediately returning to the app:
        passthrough.onForegroundSignal(ForegroundSignal.Home(launcher), 1000L)
        passthrough.onForegroundSignal(ForegroundSignal.AppWindow(targetApp), 2000L)

        // Must NOT skip evaluation:
        assertFalse(
            "User must not be able to immediately reopen the app without block",
            passthrough.shouldSkipForegroundEvaluation(targetApp)
        )
    }

    @Test
    fun `auto-kick arms cooldown when configured`() {
        val tracker = InteractionTracker()
        val cache = CounterCacheRefresher()
        runBlocking {
            cache.forceRefresh {
                mapOf(targetApp to CounterCacheEntry(autoKickAfterMinutes = 3, autoKickCooldownSeconds = 60))
            }
        }

        val executor = AutoKickExecutor(
            interactionTracker = tracker,
            counterOverlayManager = NoOpCounterOverlay,
            counterCache = cache,
            logger = NudgeLog.NoOp,
            goHome = {}
        )

        executor.kick(targetApp, reason = "test")
        assertTrue("Cooldown should be armed", tracker.isInCooldown(targetApp))
    }
}
