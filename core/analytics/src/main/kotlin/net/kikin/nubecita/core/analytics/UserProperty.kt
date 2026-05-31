package net.kikin.nubecita.core.analytics

/**
 * A typed, PII-free GA4 user property.
 *
 * User properties are string-valued in Firebase, so each variant exposes a
 * neutral wire [name] (snake_case, ≤24 chars) and a [value] derived from a typed
 * enum / boolean. v1 sets 3 of GA4's 25-property budget. Identifying values
 * (the PDS host, follower identities, etc.) are bucketed/booleanized before they
 * can reach a property — e.g. the self-hosting host becomes a bare boolean.
 */
sealed interface UserProperty {
    val name: String
    val value: String
}

/** Resolved theme the user is viewing. */
enum class ThemePreference(
    val wire: String,
) {
    Light("light"),
    Dark("dark"),
    System("system"),
}

/** `theme_preference` — light / dark / system. */
data class Theme(
    val preference: ThemePreference,
) : UserProperty {
    override val name: String = "theme_preference"
    override val value: String = preference.wire
}

/**
 * `is_self_hosted` — whether the account lives on a non-Bluesky PDS. The host
 * itself is never sent, only this boolean.
 */
data class SelfHosted(
    val isSelfHosted: Boolean,
) : UserProperty {
    override val name: String = "is_self_hosted"
    override val value: String = isSelfHosted.toString()
}

/** `notifications_enabled` — whether push notifications are currently enabled. */
data class NotificationsEnabled(
    val enabled: Boolean,
) : UserProperty {
    override val name: String = "notifications_enabled"
    override val value: String = enabled.toString()
}
