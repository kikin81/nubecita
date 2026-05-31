package net.kikin.nubecita.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.Main
import net.kikin.nubecita.Splash
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.api.NewChat
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.login.api.Login
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import net.kikin.nubecita.feature.postdetail.api.PostDeepLinkKey
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.settings.api.Settings
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute

/**
 * Map a Navigation 3 [NavKey] to the stable, PII-free [AnalyticsScreen] it
 * represents, or `null` when the route is deliberately **not** tracked.
 *
 * Only the route *kind* is reported — never instance args (a handle, DID,
 * post URI, query, …). Both `Profile(handle = null)` (own profile) and
 * `Profile(handle = "alice")` map to [AnalyticsScreen.Profile], so the wire
 * `screen_name` is identical and *whose* profile is structurally dropped.
 * Navigating between two profiles still emits two `screen_view` events
 * (each a real destination view) — [TrackScreenViews] keys its de-dupe on
 * the `NavKey`, not on this enum. See [AnalyticsScreen]'s KDoc for why the
 * wire names are a fixed, closed set.
 *
 * ## Why a trailing `else` instead of a true exhaustive `when`
 *
 * The design intent (nubecita-049f.2) is that a new/renamed route can't
 * silently go untracked. We get that for **renames and removals for free**:
 * every tracked route is referenced by its concrete type below, so renaming
 * or deleting one breaks this `when` at compile time.
 *
 * A truly exhaustive `when` with no `else` is **not** achievable here: every
 * `NavKey` is an independent `data object` / `data class` scattered across
 * the per-feature `:api` modules, and `androidx…NavKey` is a plain (non-
 * sealed) interface — Kotlin can't form a cross-module sealed set to check
 * exhaustiveness against. So a brand-**new** route falls through `else ->
 * null` (untracked, never crashes — analytics is fire-and-forget). The
 * coverage net for that case is `NavKeyAnalyticsTest`, which pins every
 * current route → screen pair and must be updated when a route is added.
 *
 * `net.kikin.nubecita.feature.moderation.api.Report` is intentionally absent:
 * it is a don't-track route AND `:feature:moderation:api` is not on `:app`'s
 * compile classpath (only `implementation`-scoped inside other `:impl`
 * modules), so it is covered by the `else` branch rather than referenced.
 */
internal fun NavKey.toAnalyticsScreenOrNull(): AnalyticsScreen? =
    when (this) {
        // --- Outer shell (MainNavigation) ---
        // Transient / container routes — the inner MainShell display tracks
        // the real screen while Main is on the outer stack.
        Splash, Main -> null
        Login -> AnalyticsScreen.Login
        Onboarding -> AnalyticsScreen.Onboarding

        // --- Inner shell (MainShell): top-level tabs ---
        Feed -> AnalyticsScreen.Feed
        Search -> AnalyticsScreen.Search
        NotificationsTab -> AnalyticsScreen.Notifications
        Chats -> AnalyticsScreen.Chats

        // --- Inner shell: sub-routes (data classes → `is`, args dropped) ---
        is Profile -> AnalyticsScreen.Profile
        is PostDetailRoute -> AnalyticsScreen.PostDetail
        is ComposerRoute -> AnalyticsScreen.Composer
        is EditProfile -> AnalyticsScreen.EditProfile
        Settings -> AnalyticsScreen.Settings
        is Chat -> AnalyticsScreen.ChatThread
        is MediaViewerRoute -> AnalyticsScreen.MediaViewer

        // --- Deliberately NOT tracked (no AnalyticsScreen value by design) ---
        NewChat -> null // recipient picker, not a destination screen
        is VideoPlayerRoute -> null // no AnalyticsScreen value; left untracked
        is PostDeepLinkKey -> null // transport only; converted before reaching a back stack

        // New / unknown route → untracked. See KDoc + NavKeyAnalyticsTest.
        else -> null
    }

/**
 * Host-layer screen-view tracker. Place inside a `NavDisplay` host
 * (`MainNavigation`, `MainShell`) and pass the back stack's top [NavKey];
 * it maps that key to an [AnalyticsScreen] and emits a `screen_view`.
 *
 * The [LaunchedEffect] is keyed on the [NavKey] itself. Every [NavKey] in
 * this project is a value-equal `@Serializable data object` / `data class`,
 * so a recomposition that leaves the top route structurally unchanged does
 * **not** restart the effect — no duplicate log. But navigating to a
 * genuinely different destination — including a different instance of the
 * same screen *kind*, e.g. `Profile(A)` → `Profile(B)` or one thread to
 * another — changes the key and logs each as its own view (the wire
 * `screen_name` stays the same; only the count differs). Re-entering a
 * screen after leaving it (Feed → Profile → Feed) logs again too.
 *
 * Untracked routes (mapping returns `null`) emit nothing. This must stay at
 * the Composable host layer and never move into a ViewModel — consistent
 * with `LocalMainShellNavState` / `LocalTabReTapSignal` (see CLAUDE.md).
 */
@Composable
internal fun TrackScreenViews(
    topRoute: NavKey?,
    analytics: AnalyticsClient,
) {
    LaunchedEffect(topRoute) {
        val screen = topRoute?.toAnalyticsScreenOrNull()
        if (screen != null) analytics.logScreen(screen)
    }
}
