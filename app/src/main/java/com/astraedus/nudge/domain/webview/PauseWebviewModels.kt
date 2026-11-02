package com.astraedus.nudge.domain.webview

/**
 * Level 2: A custom tag / link under a website.
 * E.g. tag = "RANDOM", label = "Wikipedia Random", url = "https://en.wikipedia.org/wiki/Special:Random"
 * E.g. tag = "TODAY", label = "Artículos de hoy", url = "https://en.m.wikipedia.org/wiki/Special:FeedItem/featured"
 */
data class PauseWebviewTag(
    val id: String,
    val tag: String,
    val label: String = "",
    val url: String
)

/**
 * Level 1: The website source.
 * E.g. name = "Wikipedia", tags = [RANDOM, TODAY], selectedTagId = "wiki_random"
 * E.g. name = "Daily Word", tags = [TODAY, RANDOM], selectedTagId = "word_today"
 *
 * [selectedTagId] memorizes which Level 2 tag is selected for this website on this device.
 */
data class PauseWebviewSite(
    val id: String,
    val name: String,
    val tags: List<PauseWebviewTag> = emptyList(),
    val selectedTagId: String? = null
)

/**
 * Resolved active selection ready to display in the webview overlay.
 */
data class PauseWebviewResolvedItem(
    val siteId: String,
    val siteName: String,
    val tagId: String,
    val tag: String,
    val label: String,
    val url: String,
    val availableTags: List<PauseWebviewTag>
)

enum class PauseWebviewSelectionMode {
    ROUND_ROBIN,
    LAST_SELECTED
}

enum class PauseWebviewResetStrategy {
    TIME_OF_DAY,        // Reset at a specific time of day (e.g. 00:00 midnight, 04:00 AM, or custom)
    INACTIVITY_COOLDOWN // Reset if the app hasn't been accessed for N minutes
}

data class AppIncrementalState(
    val accumulatedSeconds: Int = 0,
    val lastAccessTimestampMs: Long = 0L,
    val lastResetTimestampMs: Long = 0L
)

data class PauseWebviewConfig(
    val sites: List<PauseWebviewSite> = emptyList(),
    val selectionMode: PauseWebviewSelectionMode = PauseWebviewSelectionMode.ROUND_ROBIN,
    val lastSelectedSiteId: String? = null,
    val roundRobinIndex: Int = 0,
    val incrementalSeconds: Int = 0,
    val maxIncrementalSeconds: Int = 120,
    val resetStrategy: PauseWebviewResetStrategy = PauseWebviewResetStrategy.TIME_OF_DAY,
    val resetHourOfDay: Int = 0, // 0 = midnight, 4 = 4:00 AM, etc.
    val resetMinuteOfDay: Int = 0, // 0..59 custom reset minute
    val inactivityCooldownMinutes: Int = 120, // 2 hours (120 mins)
    val autoProceedOnZero: Boolean = false
) {
    companion object {
        val DEFAULT_SITES = listOf(
            PauseWebviewSite(
                id = "site_wikipedia",
                name = "Wikipedia",
                tags = listOf(
                    PauseWebviewTag(
                        id = "wiki_random",
                        tag = "RANDOM",
                        label = "Wikipedia Random",
                        url = "https://en.wikipedia.org/wiki/Special:Random"
                    ),
                    PauseWebviewTag(
                        id = "wiki_today",
                        tag = "TODAY",
                        label = "Artículos de hoy",
                        url = "https://en.m.wikipedia.org/wiki/Special:FeedItem/featured"
                    )
                ),
                selectedTagId = "wiki_random"
            ),
            PauseWebviewSite(
                id = "site_daily_word",
                name = "Daily Word",
                tags = listOf(
                    PauseWebviewTag(
                        id = "word_today",
                        tag = "TODAY",
                        label = "Word of the Day",
                        url = "https://en.m.wiktionary.org/wiki/Wiktionary:Word_of_the_day"
                    ),
                    PauseWebviewTag(
                        id = "word_random",
                        tag = "RANDOM",
                        label = "Random Entry",
                        url = "https://en.m.wiktionary.org/wiki/Special:RandomInCategory/English_lemmas"
                    )
                ),
                selectedTagId = "word_today"
            )
        )
    }
}
