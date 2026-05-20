package net.kikin.nubecita.feature.onboarding.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the first-launch onboarding flow.
 *
 * Lives in `:feature:onboarding:api` so the outer-nav decision in
 * `MainActivity` can depend on the key alone — never on
 * `:feature:onboarding:impl`. Reached only when
 * `UserPreferencesRepository.hasSeenOnboarding` is `false` AND the user
 * is signed out; both "Skip" and "Get started" persist the flag and let
 * `MainActivity`'s reactive routing drive the `replaceTo(Login)`
 * transition (the screen Composable doesn't navigate itself).
 *
 * Carries no arguments today; if a per-user onboarding variant is ever
 * introduced, the consumer-side `hiltViewModel()` call site will need to
 * switch to assisted injection.
 */
@Serializable data object Onboarding : NavKey
