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
 * attempt persistence and emit `NavigateToLogin`. Two layers drive the
 * actual transition: the screen Composable's effect collector calls
 * `replaceTo(Login)` as a failsafe (works even if the persist throws),
 * and `MainActivity`'s `combine(sessionState, hasSeenOnboarding)`
 * collector also fires on the flag flip. `DefaultNavigator.replaceTo`
 * is idempotent on a single-entry stack with the same target key, so
 * the race is safe.
 *
 * Carries no arguments today; if a per-user onboarding variant is ever
 * introduced, the consumer-side `hiltViewModel()` call site will need to
 * switch to assisted injection.
 */
@Serializable data object Onboarding : NavKey
