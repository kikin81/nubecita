package net.kikin.nubecita.feature.profile.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Profile screen.
 *
 * A single hybrid NavKey serves both the "You" top-level tab and any
 * cross-tab navigation to another author's profile:
 *
 * - `Profile(handle = null)` — the current authenticated user's own
 *   profile. Used as the top-level destination of the You tab.
 * - `Profile(handle = "alice.bsky.social")` — another user's profile.
 *   Pushed onto the *active* tab's back stack from inside any tab (e.g.,
 *   tapping an author handle in a Feed post) per the design's cross-tab
 *   navigation rule (see openspec/changes/add-adaptive-navigation-shell/design.md D7).
 *
 * Lives in `:feature:profile:api` so cross-feature modules that need to
 * push `Profile` onto the back stack can depend on `:feature:profile:api`
 * alone — never on `:feature:profile:impl` (which does not exist yet;
 * until the profile feature epic lands, `:app` registers a placeholder
 * Composable for this key).
 */
@Serializable
data class Profile(
    val handle: String? = null,
) : NavKey
