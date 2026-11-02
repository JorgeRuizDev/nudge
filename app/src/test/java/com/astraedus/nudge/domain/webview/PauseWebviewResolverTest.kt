package com.astraedus.nudge.domain.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PauseWebviewResolverTest {

    private val zoneId = ZoneId.of("UTC")

    private val tagWikiRandom = PauseWebviewTag("tag_wiki_random", "RANDOM", "Wikipedia Random", "https://en.wikipedia.org/wiki/Special:Random")
    private val tagWikiToday = PauseWebviewTag("tag_wiki_today", "TODAY", "Artículos de hoy", "https://en.m.wikipedia.org/wiki/Special:FeedItem/featured")

    private val siteWiki = PauseWebviewSite(
        id = "site_wiki",
        name = "Wikipedia",
        tags = listOf(tagWikiRandom, tagWikiToday),
        selectedTagId = "tag_wiki_random"
    )

    private val tagWordToday = PauseWebviewTag("tag_word_today", "TODAY", "Word of the Day", "https://en.m.wiktionary.org/wiki/Wiktionary:Word_of_the_day")
    private val tagWordRandom = PauseWebviewTag("tag_word_random", "RANDOM", "Random Word", "https://en.m.wiktionary.org/wiki/Special:Random")

    private val siteWord = PauseWebviewSite(
        id = "site_word",
        name = "Daily Word",
        tags = listOf(tagWordToday, tagWordRandom),
        selectedTagId = "tag_word_today"
    )

    @Test
    fun `resolveItem returns null when sites empty`() {
        val config = PauseWebviewConfig(sites = emptyList())
        assertNull(PauseWebviewResolver.resolveItem(config))
    }

    @Test
    fun `resolveActiveSite round robin cycles through sites`() {
        var config = PauseWebviewConfig(
            sites = listOf(siteWiki, siteWord),
            selectionMode = PauseWebviewSelectionMode.ROUND_ROBIN,
            roundRobinIndex = 0
        )

        assertEquals(siteWiki, PauseWebviewResolver.resolveActiveSite(config))

        config = PauseWebviewResolver.advanceRoundRobin(config)
        assertEquals(1, config.roundRobinIndex)
        assertEquals(siteWord, PauseWebviewResolver.resolveActiveSite(config))

        config = PauseWebviewResolver.advanceRoundRobin(config)
        assertEquals(0, config.roundRobinIndex)
        assertEquals(siteWiki, PauseWebviewResolver.resolveActiveSite(config))
    }

    @Test
    fun `resolveActiveSite last selected returns selected site or first site`() {
        val configWithSelection = PauseWebviewConfig(
            sites = listOf(siteWiki, siteWord),
            selectionMode = PauseWebviewSelectionMode.LAST_SELECTED,
            lastSelectedSiteId = "site_word"
        )
        assertEquals(siteWord, PauseWebviewResolver.resolveActiveSite(configWithSelection))

        val configWithUnknownSelection = configWithSelection.copy(lastSelectedSiteId = "unknown")
        assertEquals(siteWiki, PauseWebviewResolver.resolveActiveSite(configWithUnknownSelection))

        val configWithNullSelection = configWithSelection.copy(lastSelectedSiteId = null)
        assertEquals(siteWiki, PauseWebviewResolver.resolveActiveSite(configWithNullSelection))
    }

    @Test
    fun `resolveActiveTag memorizes chosen Level 2 tag per site`() {
        // Memorized tag is tagWikiToday
        val siteWithToday = siteWiki.copy(selectedTagId = "tag_wiki_today")
        assertEquals(tagWikiToday, PauseWebviewResolver.resolveActiveTag(siteWithToday))

        // Memorized tag is tagWikiRandom
        val siteWithRandom = siteWiki.copy(selectedTagId = "tag_wiki_random")
        assertEquals(tagWikiRandom, PauseWebviewResolver.resolveActiveTag(siteWithRandom))

        // When no memorized tag, defaults to first tag
        val siteWithNoSelection = siteWiki.copy(selectedTagId = null)
        assertEquals(tagWikiRandom, PauseWebviewResolver.resolveActiveTag(siteWithNoSelection))
    }

    @Test
    fun `resolveItem returns full two-level resolved item`() {
        val config = PauseWebviewConfig(
            sites = listOf(siteWiki.copy(selectedTagId = "tag_wiki_today"), siteWord),
            selectionMode = PauseWebviewSelectionMode.ROUND_ROBIN,
            roundRobinIndex = 0
        )

        val resolved = PauseWebviewResolver.resolveItem(config)
        assertNotNull(resolved)
        assertEquals("site_wiki", resolved!!.siteId)
        assertEquals("Wikipedia", resolved.siteName)
        assertEquals("tag_wiki_today", resolved.tagId)
        assertEquals("TODAY", resolved.tag)
        assertEquals("Artículos de hoy", resolved.label)
        assertEquals("https://en.m.wikipedia.org/wiki/Special:FeedItem/featured", resolved.url)
        assertEquals(2, resolved.availableTags.size)
    }

    @Test
    fun `inactivity cooldown reset triggers only after cooldown duration`() {
        val config = PauseWebviewConfig(
            resetStrategy = PauseWebviewResetStrategy.INACTIVITY_COOLDOWN,
            inactivityCooldownMinutes = 60 // 1 hour
        )

        val lastAccess = 1_000_000L
        val appState = AppIncrementalState(
            accumulatedSeconds = 20,
            lastAccessTimestampMs = lastAccess
        )

        // 30 minutes later (not elapsed)
        assertFalse(
            PauseWebviewResolver.isResetTriggered(
                appState,
                config,
                nowMs = lastAccess + 30 * 60_000L,
                zoneId = zoneId
            )
        )

        // Exactly 60 minutes later (elapsed)
        assertTrue(
            PauseWebviewResolver.isResetTriggered(
                appState,
                config,
                nowMs = lastAccess + 60 * 60_000L,
                zoneId = zoneId
            )
        )

        // 90 minutes later (elapsed)
        assertTrue(
            PauseWebviewResolver.isResetTriggered(
                appState,
                config,
                nowMs = lastAccess + 90 * 60_000L,
                zoneId = zoneId
            )
        )
    }

    @Test
    fun `time of day reset triggers with custom hour and minute`() {
        // Custom reset time: 04:30 AM
        val config = PauseWebviewConfig(
            resetStrategy = PauseWebviewResetStrategy.TIME_OF_DAY,
            resetHourOfDay = 4,
            resetMinuteOfDay = 30
        )

        fun toEpoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
            return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }

        // Access yesterday at 23:00, now is today at 04:29 (before 04:30 -> not reset)
        val lastAccess1 = toEpoch(2026, 11, 1, 23, 0)
        val now1 = toEpoch(2026, 11, 2, 4, 29)
        val state1 = AppIncrementalState(accumulatedSeconds = 15, lastAccessTimestampMs = lastAccess1)
        assertFalse(PauseWebviewResolver.isResetTriggered(state1, config, now1, zoneId))

        // Access yesterday at 23:00, now is today at 04:31 (after 04:30 -> reset!)
        val now2 = toEpoch(2026, 11, 2, 4, 31)
        assertTrue(PauseWebviewResolver.isResetTriggered(state1, config, now2, zoneId))

        // Access today at 04:35, now is today at 05:00 (both after 04:30 -> not reset)
        val lastAccess2 = toEpoch(2026, 11, 2, 4, 35)
        val now3 = toEpoch(2026, 11, 2, 5, 0)
        val state2 = AppIncrementalState(accumulatedSeconds = 15, lastAccessTimestampMs = lastAccess2)
        assertFalse(PauseWebviewResolver.isResetTriggered(state2, config, now3, zoneId))

        // Access today at 02:00, now at 04:31 on same day -> reset!
        val lastAccess3 = toEpoch(2026, 11, 2, 2, 0)
        val now4 = toEpoch(2026, 11, 2, 4, 31)
        val state3 = AppIncrementalState(accumulatedSeconds = 15, lastAccessTimestampMs = lastAccess3)
        assertTrue(PauseWebviewResolver.isResetTriggered(state3, config, now4, zoneId))
    }

    @Test
    fun `computeEffectiveDelay applies accumulated seconds up to cap`() {
        val config = PauseWebviewConfig(
            incrementalSeconds = 5,
            maxIncrementalSeconds = 20,
            resetStrategy = PauseWebviewResetStrategy.INACTIVITY_COOLDOWN,
            inactivityCooldownMinutes = 60
        )

        val appState = AppIncrementalState(
            accumulatedSeconds = 15,
            lastAccessTimestampMs = 1000L
        )

        // Not reset: base (10) + accumulated (15) = 25
        val delay = PauseWebviewResolver.computeEffectiveDelay(
            baseDelaySeconds = 10,
            appState = appState,
            config = config,
            nowMs = 2000L,
            zoneId = zoneId
        )
        assertEquals(25, delay)

        // With accumulated exceeding max cap
        val highState = appState.copy(accumulatedSeconds = 50)
        val cappedDelay = PauseWebviewResolver.computeEffectiveDelay(
            baseDelaySeconds = 10,
            appState = highState,
            config = config,
            nowMs = 2000L,
            zoneId = zoneId
        )
        // base (10) + max cap (20) = 30
        assertEquals(30, cappedDelay)

        // When reset triggered: resets accumulated to 0
        val resetDelay = PauseWebviewResolver.computeEffectiveDelay(
            baseDelaySeconds = 10,
            appState = appState,
            config = config,
            nowMs = 1000L + 120 * 60_000L, // 2 hours later
            zoneId = zoneId
        )
        assertEquals(10, resetDelay)
    }

    @Test
    fun `computeStateAfterFullOpen increments seconds and records access timestamp`() {
        val config = PauseWebviewConfig(
            incrementalSeconds = 5,
            maxIncrementalSeconds = 30,
            resetStrategy = PauseWebviewResetStrategy.INACTIVITY_COOLDOWN,
            inactivityCooldownMinutes = 60
        )

        val initial = AppIncrementalState(
            accumulatedSeconds = 10,
            lastAccessTimestampMs = 1000L
        )

        val updated = PauseWebviewResolver.computeStateAfterFullOpen(
            appState = initial,
            config = config,
            nowMs = 2000L,
            zoneId = zoneId
        )
        assertEquals(15, updated.accumulatedSeconds)
        assertEquals(2000L, updated.lastAccessTimestampMs)

        // Reaching cap
        val nearCap = initial.copy(accumulatedSeconds = 28)
        val capped = PauseWebviewResolver.computeStateAfterFullOpen(
            appState = nearCap,
            config = config,
            nowMs = 3000L,
            zoneId = zoneId
        )
        assertEquals(30, capped.accumulatedSeconds) // capped at 30
    }
}
