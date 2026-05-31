package net.kikin.nubecita.core.analytics

/**
 * Debug-only validator for GA4 event / param / user-property names.
 *
 * [FirebaseAnalyticsClient] calls [requireValid] under `BuildConfig.DEBUG` so a
 * malformed name fails loudly in debug builds and unit tests instead of being
 * silently dropped by Firebase in production. The rules mirror GA4's documented
 * limits:
 * - names are snake_case starting with a letter (`[a-z][a-z0-9_]*`);
 * - event/param names are ≤40 chars, user-property names are ≤24 chars;
 * - names must not use a reserved prefix (`firebase_`, `google_`, `ga_`);
 * - event names must not collide with a reserved event name.
 *
 * See https://support.google.com/analytics/answer/13316687 and
 * https://support.google.com/analytics/answer/9267744.
 */
object AnalyticsValidator {
    const val MAX_EVENT_NAME_LENGTH: Int = 40
    const val MAX_PARAM_NAME_LENGTH: Int = 40
    const val MAX_USER_PROPERTY_NAME_LENGTH: Int = 24

    private val NAME_PATTERN = Regex("^[a-z][a-z0-9_]*$")

    private val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")

    /** GA4-reserved event names that may not be used for custom events. */
    private val RESERVED_EVENT_NAMES =
        setOf(
            "ad_activeview",
            "ad_click",
            "ad_exposure",
            "ad_impression",
            "ad_query",
            "ad_reward",
            "adunit_exposure",
            "app_clear_data",
            "app_exception",
            "app_install",
            "app_remove",
            "app_update",
            "app_upgrade",
            "dynamic_link_app_open",
            "dynamic_link_app_update",
            "dynamic_link_first_open",
            "error",
            "first_open",
            "first_visit",
            "in_app_purchase",
            "notification_dismiss",
            "notification_foreground",
            "notification_open",
            "notification_receive",
            "os_update",
            "screen_view",
            "session_start",
            "user_engagement",
        )

    /** Throws [IllegalArgumentException] if [event]'s name or any param key is invalid. */
    fun requireValid(event: AnalyticsEvent) {
        requireValidEventName(event.name)
        event.params.keys.forEach(::requireValidParamName)
    }

    /** Throws [IllegalArgumentException] if [property]'s name is invalid. */
    fun requireValid(property: UserProperty) {
        requireValidUserPropertyName(property.name)
    }

    fun requireValidEventName(name: String) {
        requireWellFormed(name, MAX_EVENT_NAME_LENGTH, "event")
        require(name !in RESERVED_EVENT_NAMES) {
            "Analytics event name '$name' is GA4-reserved"
        }
    }

    fun requireValidParamName(name: String) {
        requireWellFormed(name, MAX_PARAM_NAME_LENGTH, "param")
    }

    fun requireValidUserPropertyName(name: String) {
        requireWellFormed(name, MAX_USER_PROPERTY_NAME_LENGTH, "user property")
    }

    private fun requireWellFormed(
        name: String,
        maxLength: Int,
        kind: String,
    ) {
        require(NAME_PATTERN.matches(name)) {
            "Analytics $kind name '$name' must be snake_case starting with a letter ([a-z][a-z0-9_]*)"
        }
        require(name.length <= maxLength) {
            "Analytics $kind name '$name' exceeds the $maxLength-char limit"
        }
        require(RESERVED_PREFIXES.none(name::startsWith)) {
            "Analytics $kind name '$name' uses a reserved prefix ($RESERVED_PREFIXES)"
        }
    }
}
