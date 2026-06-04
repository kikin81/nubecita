package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the About screen (a Settings sub-route).
 *
 * Pushed onto MainShell's inner back stack from the Settings "About" row via
 * `onNavigateTo(About)`. Tagged `adaptiveDialog()` in `SettingsNavigationModule`
 * so it presents full-screen on phone and coalesces into the Settings dialog
 * (content-swap) on tablet.
 */
@Serializable
data object About : NavKey
