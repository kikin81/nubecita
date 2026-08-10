package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Media and animations screen (a Settings
 * sub-route).
 *
 * Pushed from the Settings "Media and animations" row via
 * `onNavigateTo(MediaAndAnimations)`. Tagged `adaptiveDialog()` in
 * `SettingsNavigationModule` so it presents full-screen on phone and coalesces
 * into the Settings dialog on tablet, like Appearance and Feed preferences.
 *
 * Its own row rather than a child of Appearance: these are data-cost controls,
 * not look-and-feel, and they are where a future "reduce motion" lands
 * (epic `nubecita-sf5x`).
 */
@Serializable
data object MediaAndAnimations : NavKey
