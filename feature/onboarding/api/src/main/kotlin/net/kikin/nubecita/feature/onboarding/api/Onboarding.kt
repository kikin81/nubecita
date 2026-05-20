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
 * is signed out; both "Skip" and "Get started" trigger the VM to
 * persist the flag, which `MainActivity`'s `combine` collector observes
 * and uses to drive the `replaceTo(Login)` transition. The screen
 * Composable does NOT navigate itself — single source of truth for
 * post-onboarding navigation avoids a double-`replaceTo` race that
 * would clear+re-add the Login entry on every transition.
 *
 * Carries no arguments today; if a per-user onboarding variant is ever
 * introduced, the consumer-side `hiltViewModel()` call site will need to
 * switch to assisted injection.
 */
@Serializable data object Onboarding : NavKey
