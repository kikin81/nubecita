package net.kikin.nubecita.feature.notifications.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Notifications top-level tab.
 *
 * Lives in `:feature:notifications:api` so cross-feature modules that need
 * to push `NotificationsTab` onto the back stack can depend on
 * `:feature:notifications:api` alone — never on `:feature:notifications:impl`.
 *
 * The key carries no arguments today; if it grows arguments later (e.g., a
 * pre-selected filter), the consumer-side `hiltViewModel()` call site will
 * need to switch to assisted injection.
 */
@Serializable
data object NotificationsTab : NavKey
