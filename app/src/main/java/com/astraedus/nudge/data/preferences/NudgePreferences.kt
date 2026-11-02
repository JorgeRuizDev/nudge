package com.astraedus.nudge.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astraedus.nudge.data.export.ExportedSettings
import com.astraedus.nudge.domain.ContentFilterAllowlist
import com.astraedus.nudge.domain.ContentFilterBlocklist
import com.astraedus.nudge.domain.ContentFilterUserList
import com.astraedus.nudge.domain.nuke.NukeKeyKind
import com.astraedus.nudge.domain.nuke.NukeState
import com.astraedus.nudge.domain.webview.AppIncrementalState
import com.astraedus.nudge.domain.webview.PauseWebviewConfig
import com.astraedus.nudge.domain.webview.PauseWebviewSite
import com.astraedus.nudge.domain.webview.PauseWebviewTag
import com.astraedus.nudge.domain.webview.PauseWebviewResolver
import com.astraedus.nudge.domain.webview.PauseWebviewSelectionMode
import com.astraedus.nudge.domain.webview.PauseWebviewResetStrategy
import org.json.JSONArray
import org.json.JSONObject
import com.astraedus.nudge.service.GlobalEnabledProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nudge_prefs")

@Singleton
class NudgePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) : GlobalEnabledProvider {

    private object Keys {
        val GLOBAL_ENABLED = booleanPreferencesKey("global_enabled")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DEBUG_LOGGING_ENABLED = booleanPreferencesKey("debug_logging_enabled")
        val CONTENT_FILTER_ENABLED = booleanPreferencesKey("content_filter_enabled")
        val CONTENT_FILTER_MODE = stringPreferencesKey("content_filter_mode")
        val CONTENT_FILTER_STRICT_KEYWORDS = booleanPreferencesKey("content_filter_strict_keywords")
        val CONTENT_FILTER_ALLOWLIST = stringSetPreferencesKey("content_filter_allowlist")
        val CONTENT_FILTER_BLOCKLIST = stringSetPreferencesKey("content_filter_blocklist")
        val CUSTOM_DELAY_TITLES = stringPreferencesKey("custom_delay_titles")
        val CUSTOM_DELAY_SUBTITLES = stringPreferencesKey("custom_delay_subtitles")
        val CUSTOM_HARD_BLOCK_MESSAGES = stringPreferencesKey("custom_hard_block_messages")
        val STRICT_MODE_ENABLED = booleanPreferencesKey("strict_mode_enabled")
        val STRICT_MODE_CHALLENGE_LENGTH = intPreferencesKey("strict_mode_challenge_length")
        val EMERGENCY_PASS_ENABLED = booleanPreferencesKey("emergency_pass_enabled")
        val EMERGENCY_PASS_USAGE = stringPreferencesKey("emergency_pass_usage")
        val BOUNCE_CHECK_IN_ENABLED = booleanPreferencesKey("bounce_check_in_enabled")
        val PIP_ESCAPE_PROMPTED = stringPreferencesKey("pip_escape_prompted")
        val PROTECTION_DEGRADED = booleanPreferencesKey("protection_degraded")
        val PROTECTION_ALERT_SHOWN_AT = longPreferencesKey("protection_alert_shown_at")

        // Nuke Mode (docs/architecture/nuke-mode.md). DEVICE-LOCAL, like the emergency-pass
        // ledger: never exported, never importable (pinned by ImportedSettingsWriteContractTest).
        val NUKE_ACTIVE = booleanPreferencesKey("nuke_active")
        val NUKE_PACKAGES = stringSetPreferencesKey("nuke_packages")
        val NUKE_KEY_HASH = stringPreferencesKey("nuke_key_hash")
        val NUKE_KEY_KIND = stringPreferencesKey("nuke_key_kind")
        val NUKE_INTRO_SEEN = booleanPreferencesKey("nuke_intro_seen")
        val PAUSE_WEBVIEW_CONFIG_JSON = stringPreferencesKey("pause_webview_config_json")
        val PAUSE_WEBVIEW_APP_INCREMENTS_JSON = stringPreferencesKey("pause_webview_app_increments_json")
    }

    /**
     * The master toggle.
     *
     * `distinctUntilChanged` is load-bearing, not tidiness. DataStore's `data` flow re-emits the
     * WHOLE preferences snapshot on every write to ANY key, and `map` happily republishes the same
     * Boolean each time. `NudgeAccessibilityService` collects this and calls
     * `NudgeMonitorService.sync` on each emission, and `sync` -> `start` -> `onStartCommand` ->
     * `startForeground` is a notification post — so before this line, every unrelated preference
     * write in the app (including the one `ProtectionCheck` makes on every watchdog cycle) posted
     * the ongoing notification again and took a `NotificationManagerService` wakelock for it. That
     * is one of the three causes of [#63](https://github.com/astraedus/nudge/issues/63), and it is
     * the one no amount of care inside the service could have fixed.
     *
     * Nothing may depend on this re-emitting an unchanged value: that was never a signal, only a
     * side effect of which key someone else happened to write.
     */
    override val isGlobalEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.GLOBAL_ENABLED] ?: true }
        .distinctUntilChanged()

    suspend fun setGlobalEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GLOBAL_ENABLED] = enabled
        }
    }

    /** Same reason as [isGlobalEnabled]: this pair drives the monitor service's existence. */
    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.ONBOARDING_COMPLETE] ?: false }
        .distinctUntilChanged()

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] = complete
        }
    }

    val isDebugLoggingEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.DEBUG_LOGGING_ENABLED] ?: false }

    suspend fun setDebugLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEBUG_LOGGING_ENABLED] = enabled
        }
    }

    /** Generic "Content Filter" master switch. Opt-in: defaults to false. */
    val contentFilterEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.CONTENT_FILTER_ENABLED] ?: false }

    suspend fun setContentFilterEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONTENT_FILTER_ENABLED] = enabled
        }
    }

    /** Block mode applied to content-filter hits. One of BlockMode names. Defaults to HARD_BLOCK. */
    val contentFilterMode: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.CONTENT_FILTER_MODE] ?: "HARD_BLOCK" }

    suspend fun setContentFilterMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONTENT_FILTER_MODE] = mode
        }
    }

    /**
     * Opt-in sub-toggle of the content filter that also matches ambiguous keyword terms
     * found in a URL's search query. Defaults to false so the general userbase is
     * unaffected; only matters when [contentFilterEnabled] is also on.
     */
    val contentFilterStrictKeywords: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.CONTENT_FILTER_STRICT_KEYWORDS] ?: false }

    suspend fun setContentFilterStrictKeywords(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONTENT_FILTER_STRICT_KEYWORDS] = enabled
        }
    }

    /**
     * "Allowed words & sites": the user's exceptions to strict keyword matching (issue #78), stored
     * in canonical form ([ContentFilterUserList.normalize]). They only ever excuse a strict-layer
     * hit; see [ContentFilterAllowlist] for what they can never override.
     *
     * Adding an entry WEAKENS the filter, so the screen routes additions through the Strict Mode
     * gate ([com.astraedus.nudge.domain.lock.ContentFilterAllowlistWeakening]); these writers do not
     * gate, exactly like every other setter here.
     */
    val contentFilterAllowlist: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[Keys.CONTENT_FILTER_ALLOWLIST] ?: emptySet() }
        .distinctUntilChanged()

    /** Add one entry, normalised; invalid input is dropped and the [ContentFilterUserList.MAX_ENTRIES] cap holds. */
    suspend fun addContentFilterAllowlistEntry(raw: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CONTENT_FILTER_ALLOWLIST] ?: emptySet()
            prefs[Keys.CONTENT_FILTER_ALLOWLIST] =
                ContentFilterUserList.withEntry(current, raw).toSet()
        }
    }

    /** Remove one entry. Always allowed: removing an exception can only block more. */
    suspend fun removeContentFilterAllowlistEntry(entry: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONTENT_FILTER_ALLOWLIST] =
                (prefs[Keys.CONTENT_FILTER_ALLOWLIST] ?: emptySet()) - entry
        }
    }

    /**
     * "Blocked words & sites": the user's own additions to the content filter, stored in canonical
     * form ([ContentFilterUserList.normalize]). Live whenever the content filter is on; deliberately
     * NOT behind [contentFilterStrictKeywords], because that toggle ships off and a user's own word
     * would then look broken.
     *
     * Removing an entry WEAKENS the filter, so the screen routes removals through the Strict Mode
     * gate ([com.astraedus.nudge.domain.lock.ContentFilterBlocklistWeakening]); these writers do not
     * gate, exactly like every other setter here.
     */
    val contentFilterBlocklist: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[Keys.CONTENT_FILTER_BLOCKLIST] ?: emptySet() }
        .distinctUntilChanged()

    /** Add one entry, normalised; invalid input is dropped and the [ContentFilterUserList.MAX_ENTRIES] cap holds. */
    suspend fun addContentFilterBlocklistEntry(raw: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CONTENT_FILTER_BLOCKLIST] ?: emptySet()
            prefs[Keys.CONTENT_FILTER_BLOCKLIST] =
                ContentFilterUserList.withEntry(current, raw).toSet()
        }
    }

    /** Remove one entry. Removing a block weakens the filter; the caller owns the Strict Mode gate. */
    suspend fun removeContentFilterBlocklistEntry(entry: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONTENT_FILTER_BLOCKLIST] =
                (prefs[Keys.CONTENT_FILTER_BLOCKLIST] ?: emptySet()) - entry
        }
    }

    /**
     * User-edited overlay messages, one per line. Empty string ("") means "use the
     * built-in defaults" — resolved via [com.astraedus.nudge.ui.overlay.NudgeMessages.resolvePool].
     */
    val customDelayTitles: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.CUSTOM_DELAY_TITLES] ?: "" }

    suspend fun setCustomDelayTitles(value: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_DELAY_TITLES] = value
        }
    }

    val customDelaySubtitles: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.CUSTOM_DELAY_SUBTITLES] ?: "" }

    suspend fun setCustomDelaySubtitles(value: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_DELAY_SUBTITLES] = value
        }
    }

    val customHardBlockMessages: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.CUSTOM_HARD_BLOCK_MESSAGES] ?: "" }

    suspend fun setCustomHardBlockMessages(value: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_HARD_BLOCK_MESSAGES] = value
        }
    }

    /**
     * Strict Mode ("commitment lock") master switch. Opt-in: defaults to false. While on, any
     * action that WEAKENS protection — including turning this off — requires passing the unlock
     * challenge. Strengthening protection is always free.
     */
    val isStrictModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.STRICT_MODE_ENABLED] ?: false }

    suspend fun setStrictModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STRICT_MODE_ENABLED] = enabled
        }
    }

    /**
     * Strict Mode challenge difficulty: number of raw characters the user must type to unlock.
     * Defaults to [com.astraedus.nudge.domain.lock.StrictModeChallenge.DEFAULT_LENGTH] (24).
     */
    val strictModeChallengeLength: Flow<Int> = context.dataStore.data
        .map { prefs ->
            prefs[Keys.STRICT_MODE_CHALLENGE_LENGTH]
                ?: com.astraedus.nudge.domain.lock.StrictModeChallenge.DEFAULT_LENGTH
        }

    suspend fun setStrictModeChallengeLength(length: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STRICT_MODE_CHALLENGE_LENGTH] = length
        }
    }

    /**
     * Master toggle for the "2-minute daily pass" emergency escape hatch. Defaults to true (the
     * escape hatch is available out of the box); users can disable it entirely. Independent of Strict
     * Mode, which hides the pass button while it is on.
     */
    val emergencyPassEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.EMERGENCY_PASS_ENABLED] ?: true }

    suspend fun setEmergencyPassEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EMERGENCY_PASS_ENABLED] = enabled
        }
    }

    /**
     * The "Bro. wtf." bounce check-in (docs/architecture/bounce-check-in.md). Default OFF: it is a
     * notification nobody asked for until they switch it on.
     *
     * `distinctUntilChanged` for the same reason as [isGlobalEnabled]: the accessibility service
     * collects this, and DataStore re-emits on every write to any key.
     *
     * Carried by a backup with the other settings. It is not a protection setting, so importing it
     * is not weakening and needs no gate; a restore that turns it on simply restores the nudge the
     * user had already opted into.
     */
    val bounceCheckInEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.BOUNCE_CHECK_IN_ENABLED] ?: false }
        .distinctUntilChanged()

    suspend fun setBounceCheckInEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.BOUNCE_CHECK_IN_ENABLED] = enabled }
    }

    /**
     * Serialized emergency-pass usage ledger (`pkg=epochMillis;…`), holding the pass's last use for
     * the rolling 24h GLOBAL lockout. Empty string = never used. A legacy per-app ledger migrates
     * transparently — [com.astraedus.nudge.domain.emergency.EmergencyPass.globalLastUsed] reads the
     * MAX timestamp across entries. A fresh use collapses it to the single global entry.
     */
    val emergencyPassUsage: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.EMERGENCY_PASS_USAGE] ?: "" }

    suspend fun setEmergencyPassUsage(raw: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EMERGENCY_PASS_USAGE] = raw
        }
    }

    /**
     * Record that the emergency pass was used at [now]. The lockout is GLOBAL (across every app), so
     * this overwrites the ledger with a single global entry — no read-modify-merge needed.
     */
    suspend fun recordEmergencyPassUsed(now: Long) {
        setEmergencyPassUsage(
            com.astraedus.nudge.domain.emergency.EmergencyPass.serialize(
                com.astraedus.nudge.domain.emergency.EmergencyPass.recordGlobal(now)
            )
        )
    }

    /**
     * Serialized `;`-separated set of packages we have already shown the picture-in-picture escape
     * explainer for (issue #19). Empty string = none. Parsed via
     * [com.astraedus.nudge.domain.pip.PipEscapeLedger.parse].
     *
     * The explainer is one-shot education about a platform limitation Nudge cannot fix in code, so
     * this exists purely to stop it nagging.
     */
    val pipEscapePromptedPackages: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.PIP_ESCAPE_PROMPTED] ?: "" }

    /**
     * Record that the PiP escape explainer has been shown for [packageName]. Read-modify-write under
     * a single `edit` so two apps escaping close together cannot clobber each other's entry.
     */
    suspend fun recordPipEscapePrompted(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = com.astraedus.nudge.domain.pip.PipEscapeLedger.parse(
                prefs[Keys.PIP_ESCAPE_PROMPTED] ?: ""
            )
            prefs[Keys.PIP_ESCAPE_PROMPTED] = com.astraedus.nudge.domain.pip.PipEscapeLedger.serialize(
                com.astraedus.nudge.domain.pip.PipEscapeLedger.record(current, packageName)
            )
        }
    }

    // --- Protection watchdog state (see [com.astraedus.nudge.domain.health.ProtectionWatchdog]) ---

    /**
     * Whether the last watchdog run found protection degraded. Persisted rather than kept in
     * memory on purpose: the failure this exists for is the OS killing our process, so the very
     * state the next run needs is the state a field would have lost.
     */
    val protectionDegraded: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.PROTECTION_DEGRADED] ?: false }

    /** Epoch millis of the last protection alert we posted; 0 = never. Backs the alert cooldown. */
    val protectionAlertShownAt: Flow<Long> = context.dataStore.data
        .map { prefs -> prefs[Keys.PROTECTION_ALERT_SHOWN_AT] ?: 0L }

    /**
     * Records one watchdog verdict. Both values are written under a single `edit` because they are
     * read together as one snapshot — a run that recorded "degraded" but lost the alert timestamp
     * would re-alert on the next run and burn through the cooldown the user is being spared.
     */
    suspend fun recordProtectionCheck(degraded: Boolean, alertShownAtMs: Long?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROTECTION_DEGRADED] = degraded
            alertShownAtMs?.let { prefs[Keys.PROTECTION_ALERT_SHOWN_AT] = it }
        }
    }

    // --- Nuke Mode (see docs/architecture/nuke-mode.md) ---------------------------------------
    //
    // These are DUMB writes. Every gate (what weakens Nuke, what needs the key or the emergency
    // code) lives in `NukePolicy` and is applied by the ViewModels before they get here, the same
    // split Strict Mode uses. The ONE invariant enforced here is the one no caller may break:
    // Nuke is never on without a key, because a key is one of its only two ways out.

    /**
     * The whole persisted Nuke state as one value. `distinctUntilChanged` for the reason
     * [isGlobalEnabled] documents: the accessibility service collects this, and DataStore re-emits
     * every snapshot on every write to ANY key.
     */
    val nukeState: Flow<NukeState> = context.dataStore.data
        .map { prefs ->
            val hash = prefs[Keys.NUKE_KEY_HASH]?.takeIf { it.isNotBlank() }
            NukeState(
                active = (prefs[Keys.NUKE_ACTIVE] ?: false) && hash != null,
                packages = prefs[Keys.NUKE_PACKAGES] ?: emptySet(),
                keyHash = hash,
                keyKind = NukeKeyKind.fromNameOrNull(prefs[Keys.NUKE_KEY_KIND])
            )
        }
        .distinctUntilChanged()

    /** Turn Nuke on or off. Refuses to turn it ON with no key paired (it stays off). */
    suspend fun setNukeActive(active: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NUKE_ACTIVE] = active && !prefs[Keys.NUKE_KEY_HASH].isNullOrBlank()
        }
    }

    /** Add [packages] to the Nuke list. Read-modify-write under one `edit`. */
    suspend fun addNukePackages(packages: Collection<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NUKE_PACKAGES] = (prefs[Keys.NUKE_PACKAGES] ?: emptySet()) +
                packages.filter { it.isNotBlank() }
        }
    }

    /** Remove [packages] from the Nuke list. Read-modify-write under one `edit`. */
    suspend fun removeNukePackages(packages: Collection<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NUKE_PACKAGES] = (prefs[Keys.NUKE_PACKAGES] ?: emptySet()) - packages.toSet()
        }
    }

    /** Pair a key: only its hash is stored, never the payload. */
    suspend fun setNukeKey(keyHash: String, kind: NukeKeyKind) {
        require(keyHash.isNotBlank()) { "a Nuke key hash must not be blank" }
        context.dataStore.edit { prefs ->
            prefs[Keys.NUKE_KEY_HASH] = keyHash
            prefs[Keys.NUKE_KEY_KIND] = kind.name
        }
    }

    /** Unpair the key. Also turns Nuke OFF, in the same transaction: no key, no Nuke. */
    suspend fun clearNukeKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.NUKE_KEY_HASH)
            prefs.remove(Keys.NUKE_KEY_KIND)
            prefs[Keys.NUKE_ACTIVE] = false
        }
    }

    /** Whether the one-time Nuke explainer has been shown. */
    val nukeIntroSeen: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.NUKE_INTRO_SEEN] ?: false }
        .distinctUntilChanged()

    suspend fun setNukeIntroSeen() {
        context.dataStore.edit { prefs -> prefs[Keys.NUKE_INTRO_SEEN] = true }
    }

    /**
     * The redirect app: the one app offered on every block screen instead of the blocked one, or
     * null when none is chosen. Storage rules live in [RedirectAppPref]; WHETHER a saved package may
     * be shown is `RedirectAppPolicy.resolve`'s decision, never this raw value's. Device-local, not
     * exported.
     */
    val redirectAppPackage: Flow<String?> = context.dataStore.data
        .map { prefs -> RedirectAppPref.read(prefs) }
        .distinctUntilChanged()

    /** Choose [packageName] as the redirect app, or clear it with null. */
    suspend fun setRedirectAppPackage(packageName: String?) {
        context.dataStore.edit { prefs -> RedirectAppPref.write(prefs, packageName) }
    }

    // --- Pause Webviews (docs / UI) ------------------------------------------

    val pauseWebviewConfig: Flow<PauseWebviewConfig> = context.dataStore.data
        .map { prefs -> parsePauseWebviewConfig(prefs[Keys.PAUSE_WEBVIEW_CONFIG_JSON]) }
        .distinctUntilChanged()

    suspend fun savePauseWebviewConfig(config: PauseWebviewConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PAUSE_WEBVIEW_CONFIG_JSON] = serializePauseWebviewConfig(config)
        }
    }

    suspend fun updatePauseWebviewTagSelection(siteId: String, tagId: String) {
        context.dataStore.edit { prefs ->
            val current = parsePauseWebviewConfig(prefs[Keys.PAUSE_WEBVIEW_CONFIG_JSON])
            val updatedSites = current.sites.map { site ->
                if (site.id == siteId) site.copy(selectedTagId = tagId) else site
            }
            val updated = current.copy(sites = updatedSites)
            prefs[Keys.PAUSE_WEBVIEW_CONFIG_JSON] = serializePauseWebviewConfig(updated)
        }
    }

    suspend fun updatePauseWebviewSiteSelection(siteId: String) {
        context.dataStore.edit { prefs ->
            val current = parsePauseWebviewConfig(prefs[Keys.PAUSE_WEBVIEW_CONFIG_JSON])
            val updated = current.copy(lastSelectedSiteId = siteId)
            prefs[Keys.PAUSE_WEBVIEW_CONFIG_JSON] = serializePauseWebviewConfig(updated)
        }
    }

    suspend fun advanceRoundRobin() {
        context.dataStore.edit { prefs ->
            val current = parsePauseWebviewConfig(prefs[Keys.PAUSE_WEBVIEW_CONFIG_JSON])
            val updated = PauseWebviewResolver.advanceRoundRobin(current)
            prefs[Keys.PAUSE_WEBVIEW_CONFIG_JSON] = serializePauseWebviewConfig(updated)
        }
    }

    val pauseWebviewAppIncrements: Flow<Map<String, AppIncrementalState>> = context.dataStore.data
        .map { prefs -> parseAppIncrements(prefs[Keys.PAUSE_WEBVIEW_APP_INCREMENTS_JSON]) }
        .distinctUntilChanged()

    suspend fun recordAppFullOpen(packageName: String, nowMs: Long = System.currentTimeMillis()) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val config = parsePauseWebviewConfig(prefs[Keys.PAUSE_WEBVIEW_CONFIG_JSON])
            val increments = parseAppIncrements(prefs[Keys.PAUSE_WEBVIEW_APP_INCREMENTS_JSON]).toMutableMap()
            val current = increments[packageName]
            val updated = PauseWebviewResolver.computeStateAfterFullOpen(current, config, nowMs)
            increments[packageName] = updated
            prefs[Keys.PAUSE_WEBVIEW_APP_INCREMENTS_JSON] = serializeAppIncrements(increments)
        }
    }

    suspend fun resetAppIncrementalSeconds(packageName: String? = null) {
        context.dataStore.edit { prefs ->
            if (packageName == null) {
                prefs.remove(Keys.PAUSE_WEBVIEW_APP_INCREMENTS_JSON)
            } else {
                val increments = parseAppIncrements(prefs[Keys.PAUSE_WEBVIEW_APP_INCREMENTS_JSON]).toMutableMap()
                increments.remove(packageName)
                prefs[Keys.PAUSE_WEBVIEW_APP_INCREMENTS_JSON] = serializeAppIncrements(increments)
            }
        }
    }

    // --- Backup: the settings an export file carries (see [ExportedSettings]) ---------------

    /**
     * The current value of every setting a backup carries.
     *
     * Read through the public Flows above rather than off one raw `Preferences` snapshot, on
     * purpose: those Flows are where each setting's DEFAULT lives, and a second hand-written copy
     * of "`emergencyPassEnabled` defaults to true" here would eventually disagree with them —
     * exporting a value the app never actually used. The cached reads on a user-initiated,
     * already-off-main export are a trade worth making for that.
     */
    suspend fun exportableSettings(): ExportedSettings = ExportedSettings(
        contentFilterEnabled = contentFilterEnabled.first(),
        contentFilterMode = contentFilterMode.first(),
        contentFilterStrictKeywords = contentFilterStrictKeywords.first(),
        contentFilterAllowlist = contentFilterAllowlist.first().sorted(),
        contentFilterBlocklist = contentFilterBlocklist.first().sorted(),
        strictModeEnabled = isStrictModeEnabled.first(),
        strictModeChallengeLength = strictModeChallengeLength.first(),
        emergencyPassEnabled = emergencyPassEnabled.first(),
        bounceCheckInEnabled = bounceCheckInEnabled.first(),
        customDelayTitles = customDelayTitles.first(),
        customDelaySubtitles = customDelaySubtitles.first(),
        customHardBlockMessages = customHardBlockMessages.first()
    )

    /**
     * Applies an imported backup's settings.
     *
     * ALL-OR-NOTHING: every key is written inside ONE `edit` block, which DataStore commits as a
     * single transaction. A half-applied restore is the shape to avoid here — landing
     * `strictModeEnabled = false` but not the challenge length that came with it leaves the user in
     * a state their backup never described, on the app's protection settings.
     *
     * A null field is SKIPPED, not written: the file does not carry that setting, so this device's
     * own value stands. That is what lets a backup from a future Nudge (or a hand-edited partial
     * file) restore what it does carry without blanking what it does not.
     */
    suspend fun applyImportedSettings(settings: ExportedSettings) {
        if (settings.isEmpty) return
        context.dataStore.edit { prefs ->
            settings.contentFilterEnabled?.let { prefs[Keys.CONTENT_FILTER_ENABLED] = it }
            settings.contentFilterMode?.let { prefs[Keys.CONTENT_FILTER_MODE] = it }
            settings.contentFilterStrictKeywords?.let {
                prefs[Keys.CONTENT_FILTER_STRICT_KEYWORDS] = it
            }
            // REPLACES this device's list, like every other setting a backup carries. An import
            // that adds entries is weakening and was gated before this ran (ImportedSettingsWeakening).
            settings.contentFilterAllowlist?.let {
                prefs[Keys.CONTENT_FILTER_ALLOWLIST] = ContentFilterUserList.canonicalize(it).toSet()
            }
            // REPLACES this device's list. An import that REMOVES blocks is weakening and was gated
            // before this ran (ImportedSettingsWeakening).
            settings.contentFilterBlocklist?.let {
                prefs[Keys.CONTENT_FILTER_BLOCKLIST] = ContentFilterUserList.canonicalize(it).toSet()
            }
            settings.strictModeEnabled?.let { prefs[Keys.STRICT_MODE_ENABLED] = it }
            settings.strictModeChallengeLength?.let {
                prefs[Keys.STRICT_MODE_CHALLENGE_LENGTH] = it
            }
            settings.emergencyPassEnabled?.let { prefs[Keys.EMERGENCY_PASS_ENABLED] = it }
            settings.bounceCheckInEnabled?.let { prefs[Keys.BOUNCE_CHECK_IN_ENABLED] = it }
            settings.customDelayTitles?.let { prefs[Keys.CUSTOM_DELAY_TITLES] = it }
            settings.customDelaySubtitles?.let { prefs[Keys.CUSTOM_DELAY_SUBTITLES] = it }
            settings.customHardBlockMessages?.let { prefs[Keys.CUSTOM_HARD_BLOCK_MESSAGES] = it }
        }
    }
}

internal fun serializePauseWebviewConfig(config: PauseWebviewConfig): String {
    val json = JSONObject()
    val sitesArray = JSONArray()
    config.sites.forEach { site ->
        val siteObj = JSONObject()
        siteObj.put("id", site.id)
        siteObj.put("name", site.name)
        if (site.selectedTagId != null) {
            siteObj.put("selectedTagId", site.selectedTagId)
        }
        val tagsArray = JSONArray()
        site.tags.forEach { tag ->
            val tagObj = JSONObject()
            tagObj.put("id", tag.id)
            tagObj.put("tag", tag.tag)
            tagObj.put("label", tag.label)
            tagObj.put("url", tag.url)
            tagsArray.put(tagObj)
        }
        siteObj.put("tags", tagsArray)
        sitesArray.put(siteObj)
    }
    json.put("sites", sitesArray)
    json.put("selectionMode", config.selectionMode.name)
    if (config.lastSelectedSiteId != null) {
        json.put("lastSelectedSiteId", config.lastSelectedSiteId)
    }
    json.put("roundRobinIndex", config.roundRobinIndex)
    json.put("incrementalSeconds", config.incrementalSeconds)
    json.put("maxIncrementalSeconds", config.maxIncrementalSeconds)
    json.put("resetStrategy", config.resetStrategy.name)
    json.put("resetHourOfDay", config.resetHourOfDay)
    json.put("resetMinuteOfDay", config.resetMinuteOfDay)
    json.put("inactivityCooldownMinutes", config.inactivityCooldownMinutes)
    json.put("autoProceedOnZero", config.autoProceedOnZero)
    return json.toString()
}

internal fun parsePauseWebviewConfig(raw: String?): PauseWebviewConfig {
    if (raw.isNullOrBlank()) {
        return PauseWebviewConfig(sites = PauseWebviewConfig.DEFAULT_SITES)
    }
    return try {
        val json = JSONObject(raw)
        val sitesList = mutableListOf<PauseWebviewSite>()
        val sitesArray = json.optJSONArray("sites")
        if (sitesArray != null) {
            for (i in 0 until sitesArray.length()) {
                val siteObj = sitesArray.optJSONObject(i) ?: continue
                val id = siteObj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = siteObj.optString("name", "Web")
                val selectedTagId = if (siteObj.isNull("selectedTagId")) null else siteObj.optString("selectedTagId").takeIf { it.isNotBlank() }
                val tagsList = mutableListOf<PauseWebviewTag>()
                val tagsArray = siteObj.optJSONArray("tags")
                if (tagsArray != null) {
                    for (j in 0 until tagsArray.length()) {
                        val tagObj = tagsArray.optJSONObject(j) ?: continue
                        val tagId = tagObj.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val tag = tagObj.optString("tag")
                        val label = tagObj.optString("label")
                        val url = tagObj.optString("url")
                        tagsList.add(PauseWebviewTag(id = tagId, tag = tag, label = label, url = url))
                    }
                }
                sitesList.add(
                    PauseWebviewSite(
                        id = id,
                        name = name,
                        tags = tagsList,
                        selectedTagId = selectedTagId
                    )
                )
            }
        } else {
            val legacyItems = json.optJSONArray("items")
            if (legacyItems != null && legacyItems.length() > 0) {
                val migratedTags = mutableListOf<PauseWebviewTag>()
                for (i in 0 until legacyItems.length()) {
                    val itemObj = legacyItems.optJSONObject(i) ?: continue
                    val id = itemObj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val tag = itemObj.optString("tag")
                    val url = itemObj.optString("url")
                    migratedTags.add(PauseWebviewTag(id = id, tag = tag, label = tag, url = url))
                }
                if (migratedTags.isNotEmpty()) {
                    sitesList.add(
                        PauseWebviewSite(
                            id = "default_migrated_site",
                            name = "Web",
                            tags = migratedTags,
                            selectedTagId = migratedTags.firstOrNull()?.id
                        )
                    )
                }
            }
        }

        val selectionModeStr = json.optString("selectionMode", PauseWebviewSelectionMode.ROUND_ROBIN.name)
        val selectionMode = try {
            PauseWebviewSelectionMode.valueOf(selectionModeStr)
        } catch (_: Exception) {
            PauseWebviewSelectionMode.ROUND_ROBIN
        }
        val lastSelectedSiteId = if (json.isNull("lastSelectedSiteId")) {
            if (json.isNull("lastSelectedId")) null else json.optString("lastSelectedId").takeIf { it.isNotBlank() }
        } else json.optString("lastSelectedSiteId").takeIf { it.isNotBlank() }
        val roundRobinIndex = json.optInt("roundRobinIndex", 0)
        val incrementalSeconds = json.optInt("incrementalSeconds", 0)
        val maxIncrementalSeconds = json.optInt("maxIncrementalSeconds", 120)
        val resetStrategyStr = json.optString("resetStrategy", PauseWebviewResetStrategy.TIME_OF_DAY.name)
        val resetStrategy = try {
            PauseWebviewResetStrategy.valueOf(resetStrategyStr)
        } catch (_: Exception) {
            PauseWebviewResetStrategy.TIME_OF_DAY
        }
        val resetHourOfDay = json.optInt("resetHourOfDay", 0)
        val resetMinuteOfDay = json.optInt("resetMinuteOfDay", 0)
        val inactivityCooldownMinutes = json.optInt("inactivityCooldownMinutes", 120)
        val autoProceedOnZero = json.optBoolean("autoProceedOnZero", false)

        PauseWebviewConfig(
            sites = if (sitesList.isEmpty() && !json.has("sites") && !json.has("items")) PauseWebviewConfig.DEFAULT_SITES else sitesList,
            selectionMode = selectionMode,
            lastSelectedSiteId = lastSelectedSiteId,
            roundRobinIndex = roundRobinIndex,
            incrementalSeconds = incrementalSeconds,
            maxIncrementalSeconds = maxIncrementalSeconds,
            resetStrategy = resetStrategy,
            resetHourOfDay = resetHourOfDay,
            resetMinuteOfDay = resetMinuteOfDay,
            inactivityCooldownMinutes = inactivityCooldownMinutes,
            autoProceedOnZero = autoProceedOnZero
        )
    } catch (_: Exception) {
        PauseWebviewConfig(sites = PauseWebviewConfig.DEFAULT_SITES)
    }
}

internal fun serializeAppIncrements(increments: Map<String, AppIncrementalState>): String {
    val json = JSONObject()
    increments.forEach { (pkg, state) ->
        val obj = JSONObject()
        obj.put("accumulatedSeconds", state.accumulatedSeconds)
        obj.put("lastAccessTimestampMs", state.lastAccessTimestampMs)
        obj.put("lastResetTimestampMs", state.lastResetTimestampMs)
        json.put(pkg, obj)
    }
    return json.toString()
}

internal fun parseAppIncrements(raw: String?): Map<String, AppIncrementalState> {
    if (raw.isNullOrBlank()) return emptyMap()
    return try {
        val json = JSONObject(raw)
        val result = mutableMapOf<String, AppIncrementalState>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val obj = json.optJSONObject(pkg) ?: continue
            result[pkg] = AppIncrementalState(
                accumulatedSeconds = obj.optInt("accumulatedSeconds", 0),
                lastAccessTimestampMs = obj.optLong("lastAccessTimestampMs", 0L),
                lastResetTimestampMs = obj.optLong("lastResetTimestampMs", 0L)
            )
        }
        result
    } catch (_: Exception) {
        emptyMap()
    }
}
