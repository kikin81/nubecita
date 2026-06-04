package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Content filters screen (a Settings
 * sub-route).
 *
 * Pushed onto MainShell's inner back stack from the Settings "Content filters"
 * row via `onNavigateTo(ContentFilters)`. Tagged `adaptiveDialog()` in
 * `SettingsNavigationModule` so it presents full-screen on phone and coalesces
 * into the Settings dialog (content-swap) on tablet — same as About.
 */
@Serializable
data object ContentFilters : NavKey
