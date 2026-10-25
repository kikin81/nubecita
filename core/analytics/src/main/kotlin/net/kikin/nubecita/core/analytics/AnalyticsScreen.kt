package net.kikin.nubecita.core.analytics

/**
 * Stable, PII-free route name for a `screen_view` event.
 *
 * The [screenName] is the wire value sent as Firebase's `screen_name` param. It
 * is a fixed route identifier — never an instance argument (a handle, DID, post
 * URI, query, etc.). The manual `screen_view` plumbing (nubecita-049f.2) maps
 * each Navigation 3 `NavKey` to one of these enum entries before logging.
 *
 * ### Why a closed enum and not a string derived from the `NavKey`
 *
 * This is deliberately a fixed, small set — not an open string passed in from
 * the navigation layer — for two reasons:
 *
 * 1. **PII firewall.** A `NavKey` carries instance args (`Profile(did=…)`,
 *    `PostDetail(uri=…)`). Deriving the wire name from the key — e.g.
 *    `toString()` or the route template — risks shipping a DID / handle / URI as
 *    the screen name. The enum forces "this is the `Profile` screen" while
 *    structurally dropping *whose* profile.
 * 2. **Stable metric names.** The [screenName] is decoupled from the Kotlin
 *    type name, so renaming a Composable or `NavKey` does **not** silently
 *    rename the GA4 dimension and split the historical metric. The one wire
 *    string each screen reports lives here, in one reviewed place.
 *
 * The cost is a one-line edit when a genuinely new *screen kind* ships — which
 * is a deliberate "what do we want to measure" decision, not churn. The risk
 * that a new screen is silently left untracked is handled at the mapping layer
 * (nubecita-049f.2). `NavKey` is not sealed across modules, so the map can't be
 * a compile-checked exhaustive `when`; instead `:app`'s `NavKeyAnalyticsTest`
 * scans the runtime classpath for every `NavKey` and fails until a new route is
 * either mapped or explicitly listed as untracked (nubecita-ccvy.1).
 */
enum class AnalyticsScreen(
    val screenName: String,
) {
    Feed("feed"),
    Search("search"),
    PostDetail("post_detail"),
    Composer("composer"),
    Profile("profile"),
    EditProfile("edit_profile"),
    Settings("settings"),
    Bookmarks("bookmarks"),
    Chats("chats"),
    ChatThread("chat_thread"),
    Notifications("notifications"),
    MediaViewer("media_viewer"),
    Login("login"),
    Onboarding("onboarding"),
    VideoFeed("video_feed"),
    FeedView("feed_view"),
    Feeds("feeds"),
    Hashtag("hashtag"),
    Paywall("paywall"),
    BlockedAccounts("blocked_accounts"),
    SafetyAndReporting("safety_and_reporting"),
    SettingsAppearance("settings_appearance"),
    SettingsContentFilters("settings_content_filters"),
    SettingsFeedPreferences("settings_feed_preferences"),
    SettingsMediaAndAnimations("settings_media_and_animations"),
    SettingsModeration("settings_moderation"),
    About("about"),
    AboutLicenses("about_licenses"),
    NewGroup("new_group"),
    GroupDetails("group_details"),
    GroupAddMembers("group_add_members"),
    GroupJoinRequests("group_join_requests"),
    GroupJoinLink("group_join_link"),
    GroupJoinPreview("group_join_preview"),
    ChatSettings("chat_settings"),
}
