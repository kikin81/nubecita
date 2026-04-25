@file:Suppress("ktlint:standard:filename")

package net.kikin.nubecita

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Start destination for the back stack on cold start. Renders an empty
 * surface — the system [androidx.core.splashscreen.SplashScreen] sits on
 * top via `setKeepOnScreenCondition` until [SessionStateProvider] resolves.
 * `MainActivity`'s reactive collector then `replaceTo`s [Main] (signed in)
 * or `Login` (signed out).
 */
@Serializable data object Splash : NavKey
