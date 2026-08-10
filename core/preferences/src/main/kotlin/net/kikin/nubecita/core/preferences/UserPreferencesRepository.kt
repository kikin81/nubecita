package net.kikin.nubecita.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Reactive view over non-sensitive app-wide user preferences (onboarding
 * status, future settings toggles like adult-content / theme / etc.).
 *
 * Sensitive material — OAuth tokens, DPoP keys — does NOT live here; that
 * stays in `:core:auth`'s encrypted DataStore. This repository is the
 * natural home for global flags that survive sign-out and are safe to
 * read in plaintext on disk.
 */
interface UserPreferencesRepository {
    /**
     * `true` once the user has either completed or skipped the onboarding
     * flow. New installs start at `false`; once flipped to `true` the flag
     * persists across sign-out so existing users don't re-see onboarding
     * after signing out.
     */
    val hasSeenOnboarding: Flow<Boolean>

    /** Persist that onboarding was completed or skipped. Idempotent. */
    suspend fun markOnboardingSeen()

    /**
     * The AT-URI of the feed the user last had selected, remembered across
     * launches so the app restores it instead of always opening the default.
     * Emits `null` until the user has selected a feed at least once.
     */
    val lastSelectedFeedUri: Flow<String?>

    /** Persist the AT-URI of the currently selected feed. */
    suspend fun setLastSelectedFeedUri(uri: String)

    /**
     * The user's theme choice. Defaults to [ThemePreference.DYNAMIC], and falls
     * back to it for any stored value this build can't parse (see
     * [ThemePreference]). Read by the composition root to drive `NubecitaTheme`,
     * and backs the `theme_preference` analytics user property.
     */
    val themePreference: Flow<ThemePreference>

    /** Persist the user's theme choice. */
    suspend fun setThemePreference(preference: ThemePreference)

    /**
     * Whether inline feed videos autoplay. Defaults to
     * [AutoplayPreference.ALWAYS] — the behaviour before this setting existed —
     * and falls back to it for any stored value this build can't parse.
     *
     * This is the user's *intent*, not the answer to "should this video play
     * right now": [AutoplayPreference.WIFI_ONLY] also depends on the current
     * connection. Read `AutoplayPolicy` rather than this flow at a playback
     * call site, so the preference and the network check stay combined in one
     * place.
     */
    val autoplayPreference: Flow<AutoplayPreference>

    /** Persist the user's video-autoplay choice. */
    suspend fun setAutoplayPreference(preference: AutoplayPreference)

    /**
     * Whether inline animated GIFs autoplay. Defaults to `true`, matching the
     * behaviour before this setting existed.
     *
     * Independent of [autoplayPreference] and of the connection: a GIF is
     * cheap enough that gating it on network type would be noise, so this is a
     * plain on/off the user can set once.
     */
    val autoplayGifs: Flow<Boolean>

    /** Persist the user's GIF-autoplay choice. */
    suspend fun setAutoplayGifs(enabled: Boolean)
}
