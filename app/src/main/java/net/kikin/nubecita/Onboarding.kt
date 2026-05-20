@file:Suppress("ktlint:standard:filename")

package net.kikin.nubecita

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Outer-shell route for the first-launch onboarding flow. Reached only
 * when [net.kikin.nubecita.core.preferences.UserPreferencesRepository.hasSeenOnboarding]
 * is `false` AND the user is signed out; both "Skip" and "Get started"
 * land the user on the `Login` route and persist the seen flag so
 * subsequent launches go straight to Login (or [Main] if signed in).
 *
 * The actual screen lives in `:feature:onboarding:impl` (filed as
 * `nubecita-lo3f.3`). Until that module exists, `MainNavigation` renders
 * an inline placeholder for this key.
 */
@Serializable data object Onboarding : NavKey
