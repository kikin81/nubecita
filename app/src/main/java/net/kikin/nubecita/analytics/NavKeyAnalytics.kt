package net.kikin.nubecita.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.Main
import net.kikin.nubecita.Splash
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.feature.bookmarks.api.Bookmarks
import net.kikin.nubecita.feature.chats.api.AddGroupMembers
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.ChatSettings
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.api.GroupDetails
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview
import net.kikin.nubecita.feature.chats.api.GroupJoinRequests
import net.kikin.nubecita.feature.chats.api.ManageJoinLink
import net.kikin.nubecita.feature.chats.api.NewChat
import net.kikin.nubecita.feature.chats.api.NewGroup
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.api.FeedView
import net.kikin.nubecita.feature.feeds.api.Feeds
import net.kikin.nubecita.feature.login.api.Login
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.moderation.api.Block
import net.kikin.nubecita.feature.moderation.api.BlockedAccounts
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.moderation.api.SafetyAndReporting
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import net.kikin.nubecita.feature.paywall.api.PaywallRoute
import net.kikin.nubecita.feature.paywall.api.PaywallSuccessRoute
import net.kikin.nubecita.feature.postdetail.api.PostDeepLinkKey
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Hashtag
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.settings.api.About
import net.kikin.nubecita.feature.settings.api.AboutLicenses
import net.kikin.nubecita.feature.settings.api.Appearance
import net.kikin.nubecita.feature.settings.api.ContentFilters
import net.kikin.nubecita.feature.settings.api.FeedPreferences
import net.kikin.nubecita.feature.settings.api.MediaAndAnimations
import net.kikin.nubecita.feature.settings.api.Moderation
import net.kikin.nubecita.feature.settings.api.Settings
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import net.kikin.nubecita.feature.videos.api.VideoFeed

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
 * coverage net for that case is `NavKeyAnalyticsTest`, which scans the
 * runtime classpath for every `NavKey` and fails until a new route is either
 * mapped here or listed as deliberately untracked (nubecita-ccvy.1).
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
        Bookmarks -> AnalyticsScreen.Bookmarks
        is Chat -> AnalyticsScreen.ChatThread
        is MediaViewerRoute -> AnalyticsScreen.MediaViewer
        is VideoFeed -> AnalyticsScreen.VideoFeed
        is FeedView -> AnalyticsScreen.FeedView
        Feeds -> AnalyticsScreen.Feeds
        is Hashtag -> AnalyticsScreen.Hashtag
        is PaywallRoute -> AnalyticsScreen.Paywall
        BlockedAccounts -> AnalyticsScreen.BlockedAccounts
        SafetyAndReporting -> AnalyticsScreen.SafetyAndReporting

        // --- Inner shell: settings sub-pages ---
        Appearance -> AnalyticsScreen.SettingsAppearance
        ContentFilters -> AnalyticsScreen.SettingsContentFilters
        FeedPreferences -> AnalyticsScreen.SettingsFeedPreferences
        MediaAndAnimations -> AnalyticsScreen.SettingsMediaAndAnimations
        Moderation -> AnalyticsScreen.SettingsModeration
        About -> AnalyticsScreen.About
        AboutLicenses -> AnalyticsScreen.AboutLicenses

        // --- Inner shell: group chats ---
        NewGroup -> AnalyticsScreen.NewGroup
        is GroupDetails -> AnalyticsScreen.GroupDetails
        is AddGroupMembers -> AnalyticsScreen.GroupAddMembers
        is GroupJoinRequests -> AnalyticsScreen.GroupJoinRequests
        is ManageJoinLink -> AnalyticsScreen.GroupJoinLink
        is GroupJoinPreview -> AnalyticsScreen.GroupJoinPreview
        ChatSettings -> AnalyticsScreen.ChatSettings

        // --- Deliberately NOT tracked (no AnalyticsScreen value by design) ---
        NewChat -> null // recipient picker, not a destination screen
        is VideoPlayerRoute -> null // no AnalyticsScreen value; left untracked
        is PostDeepLinkKey -> null // transport only; converted before reaching a back stack
        PaywallSuccessRoute -> null // celebration, not a tracked destination; nubecita-ykpc
        is Report -> null // dialog over the current screen, not a destination
        is Block -> null // confirmation dialog over the current screen

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
