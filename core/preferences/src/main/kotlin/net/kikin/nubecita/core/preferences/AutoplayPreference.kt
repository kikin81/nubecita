package net.kikin.nubecita.core.preferences

/**
 * Whether inline feed videos start playing on their own, as persisted by
 * [UserPreferencesRepository].
 *
 * Requested because autoplay burns cellular data with no way to opt out
 * (epic `nubecita-sf5x`). [WIFI_ONLY] is the point of the three-way rather
 * than a plain on/off: it answers the data concern without giving up
 * autoplay on the connection where it costs nothing.
 *
 * - [ALWAYS] — autoplay regardless of connection. The default, and the
 *   app's behaviour before this setting existed, so an existing install
 *   sees no change on upgrade.
 * - [WIFI_ONLY] — autoplay only on an unmetered connection. "Metered" is
 *   the OS's own judgement (`ConnectivityManager.isActiveNetworkMetered`),
 *   which is broader and more honest than "is it wifi": a metered hotspot
 *   counts as cellular, which is what the user actually means.
 * - [NEVER] — never autoplay. Videos still *play*; they wait for a tap.
 *   Off means on demand, not unavailable.
 *
 * This governs incidental playback while scrolling. It deliberately does
 * **not** gate the full-screen Videos tab — opening that is an explicit
 * choice to watch video, and silencing it would make the tab useless
 * rather than off.
 *
 * GIF autoplay is a separate boolean rather than a fourth constant here:
 * an animated GIF is far cheaper than a video, so keeping GIFs while
 * stopping video is a combination worth expressing.
 *
 * Persisted by [Enum.name]. An absent or unrecognized stored value maps to
 * [ALWAYS] — load-bearing, not defensive padding, since it is what lets
 * this build read a value written by a newer one (a future
 * `WIFI_AND_CHARGING`, say) and degrade instead of throwing. Mirrors
 * [ThemePreference]; keep the fallback intact when adding constants.
 */
enum class AutoplayPreference {
    ALWAYS,
    WIFI_ONLY,
    NEVER,
}
