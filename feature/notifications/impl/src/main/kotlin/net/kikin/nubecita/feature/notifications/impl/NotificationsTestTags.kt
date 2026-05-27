package net.kikin.nubecita.feature.notifications.impl

/**
 * Compose `testTag` constants for the Notifications surface.
 *
 * Loaded by [NotificationsScreen] and consumed by instrumentation /
 * screenshot diffing. Mirrors `:feature:feed:impl/FeedTestTags`'s style:
 * tag values are short, stable, and lowercase-kebab so they're robust
 * across rename refactors.
 */
internal object NotificationsTestTags {
    /** Top-level LazyColumn — anchor for scroll assertions and pagination probes. */
    const val LIST = "notifications-list"

    /** FilterChip strip above the LazyColumn — anchor for chip-selection probes. */
    const val FILTER_CHIP_ROW = "notifications-filter-chip-row"

    /** Retry button shown inside [NotificationsScreenViewState.InitialError]. */
    const val ERROR_RETRY = "notifications-error-retry"

    /** Container of the empty-state composable. */
    const val EMPTY_STATE = "notifications-empty-state"

    /** Modal bottom sheet that surfaces the full actor list for aggregated rows. */
    const val ACTOR_LIST_SHEET = "notifications-actor-list-sheet"
}
