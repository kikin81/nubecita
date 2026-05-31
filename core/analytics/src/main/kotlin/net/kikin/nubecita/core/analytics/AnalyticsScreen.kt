package net.kikin.nubecita.core.analytics

/**
 * Stable, PII-free route name for a `screen_view` event.
 *
 * The [screenName] is the wire value sent as Firebase's `screen_name` param. It
 * is a fixed route identifier — never an instance argument (a handle, DID, post
 * URI, query, etc.). The manual `screen_view` plumbing (nubecita-049f.2) maps
 * each Navigation 3 `NavKey` to one of these enum entries before logging.
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
    Chats("chats"),
    ChatThread("chat_thread"),
    Notifications("notifications"),
    MediaViewer("media_viewer"),
    Login("login"),
    Onboarding("onboarding"),
}
