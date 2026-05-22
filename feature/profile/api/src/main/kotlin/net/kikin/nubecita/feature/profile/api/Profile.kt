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
 * The [handle] field is named for the common case but accepts either an
 * AT Protocol handle (`alice.bsky.social`) or a DID (`did:plc:abc...`).
 * `ProfileViewModel.resolveActor` passes it opaquely to
 * `ProfileRepository.fetchHeader(actor)`, which delegates to the
 * `getProfile` XRPC — that endpoint accepts either form. The
 * deep-link handler likewise emits `Profile(handle = ...)` for both
 * shapes without translating between them (see nubecita-kf6k.2 for
 * the matcher contract).
 *
 * Lives in `:feature:profile:api` so cross-feature modules that need to
 * push `Profile` onto the back stack can depend on `:feature:profile:api`
 * alone — never on `:feature:profile:impl`.
 */
@Serializable
data class Profile(
    val handle: String? = null,
) : NavKey
