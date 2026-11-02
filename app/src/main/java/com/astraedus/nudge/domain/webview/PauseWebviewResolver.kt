package com.astraedus.nudge.domain.webview

import java.time.Instant
import java.time.ZoneId

/**
 * Pure Kotlin resolution logic for Pause Webviews with two levels:
 * Level 1: Website source (Round Robin or Last Selected)
 * Level 2: Custom tags / links per website (memorized per website on device)
 *
 * Fully unit-testable on JVM without Android dependencies.
 */
object PauseWebviewResolver {

    /**
     * Resolves the active site (Level 1) based on the selection strategy.
     */
    fun resolveActiveSite(config: PauseWebviewConfig): PauseWebviewSite? {
        if (config.sites.isEmpty()) return null
        return when (config.selectionMode) {
            PauseWebviewSelectionMode.LAST_SELECTED -> {
                if (config.lastSelectedSiteId != null) {
                    config.sites.firstOrNull { it.id == config.lastSelectedSiteId }
                        ?: config.sites.first()
                } else {
                    config.sites.first()
                }
            }
            PauseWebviewSelectionMode.ROUND_ROBIN -> {
                val index = (config.roundRobinIndex.coerceAtLeast(0)) % config.sites.size
                config.sites[index]
            }
        }
    }

    /**
     * Resolves the active tag (Level 2) for a given site.
     * Uses the memorized [selectedTagId], or falls back to the first available tag.
     */
    fun resolveActiveTag(site: PauseWebviewSite): PauseWebviewTag? {
        if (site.tags.isEmpty()) return null
        if (site.selectedTagId != null) {
            val found = site.tags.firstOrNull { it.id == site.selectedTagId }
            if (found != null) return found
        }
        return site.tags.first()
    }

    /**
     * Resolves the full webview item to display for a pause session.
     */
    fun resolveItem(config: PauseWebviewConfig): PauseWebviewResolvedItem? {
        val site = resolveActiveSite(config) ?: return null
        val tag = resolveActiveTag(site) ?: return null
        return PauseWebviewResolvedItem(
            siteId = site.id,
            siteName = site.name,
            tagId = tag.id,
            tag = tag.tag,
            label = tag.label,
            url = tag.url,
            availableTags = site.tags
        )
    }

    /**
     * Advances the round-robin index to the next website (Level 1).
     */
    fun advanceRoundRobin(config: PauseWebviewConfig): PauseWebviewConfig {
        if (config.sites.isEmpty()) return config
        val nextIndex = (config.roundRobinIndex + 1) % config.sites.size
        return config.copy(roundRobinIndex = nextIndex)
    }

    /**
     * Checks whether an app's accumulated incremental display time should be reset.
     */
    fun isResetTriggered(
        appState: AppIncrementalState?,
        config: PauseWebviewConfig,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (appState == null || appState.accumulatedSeconds <= 0) return false
        if (appState.lastAccessTimestampMs <= 0L) return false
        if (nowMs < appState.lastAccessTimestampMs) return false

        return when (config.resetStrategy) {
            PauseWebviewResetStrategy.INACTIVITY_COOLDOWN -> {
                val cooldownMs = config.inactivityCooldownMinutes.toLong() * 60_000L
                (nowMs - appState.lastAccessTimestampMs) >= cooldownMs
            }
            PauseWebviewResetStrategy.TIME_OF_DAY -> {
                val nowZdt = Instant.ofEpochMilli(nowMs).atZone(zoneId)
                val hour = config.resetHourOfDay.coerceIn(0, 23)
                val minute = config.resetMinuteOfDay.coerceIn(0, 59)
                val resetToday = nowZdt.toLocalDate().atTime(hour, minute).atZone(zoneId)
                val mostRecentResetMs = if (!nowZdt.isBefore(resetToday)) {
                    resetToday.toInstant().toEpochMilli()
                } else {
                    resetToday.minusDays(1).toInstant().toEpochMilli()
                }
                appState.lastAccessTimestampMs < mostRecentResetMs
            }
        }
    }

    /**
     * Computes the effective pause delay for an app, factoring in accumulated incremental time
     * and reset checks.
     */
    fun computeEffectiveDelay(
        baseDelaySeconds: Int,
        appState: AppIncrementalState?,
        config: PauseWebviewConfig,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        val safeBase = baseDelaySeconds.coerceAtLeast(0)
        if (config.incrementalSeconds <= 0) return safeBase

        val effectiveAccumulated = if (isResetTriggered(appState, config, nowMs, zoneId)) {
            0
        } else {
            (appState?.accumulatedSeconds ?: 0).coerceAtLeast(0)
        }

        val cappedIncremental = effectiveAccumulated.coerceAtMost(config.maxIncrementalSeconds.coerceAtLeast(0))
        return safeBase + cappedIncremental
    }

    /**
     * Computes the new [AppIncrementalState] after the user waits out the pause and enters the app
     * (a full open).
     */
    fun computeStateAfterFullOpen(
        appState: AppIncrementalState?,
        config: PauseWebviewConfig,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AppIncrementalState {
        val resetTriggered = isResetTriggered(appState, config, nowMs, zoneId)
        val currentAccumulated = if (resetTriggered) {
            0
        } else {
            (appState?.accumulatedSeconds ?: 0).coerceAtLeast(0)
        }

        val increment = config.incrementalSeconds.coerceAtLeast(0)
        val maxCap = config.maxIncrementalSeconds.coerceAtLeast(0)
        val newAccumulated = (currentAccumulated + increment).coerceAtMost(maxCap)

        return AppIncrementalState(
            accumulatedSeconds = newAccumulated,
            lastAccessTimestampMs = nowMs,
            lastResetTimestampMs = if (resetTriggered) nowMs else (appState?.lastResetTimestampMs ?: 0L)
        )
    }
}
