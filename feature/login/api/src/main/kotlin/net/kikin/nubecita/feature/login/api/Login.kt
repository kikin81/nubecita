package net.kikin.nubecita.feature.login.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the OAuth login screen.
 *
 * Lives in `:feature:login:api` so cross-feature modules that need to push
 * `Login` onto the back stack can depend on `:feature:login:api` alone —
 * never on `:feature:login:impl`. The key carries no arguments today; if
 * it grows arguments later, the consumer-side `hiltViewModel()` call site
 * will need to switch to assisted injection.
 */
@Serializable
data object Login : NavKey
