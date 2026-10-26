package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for Settings → Watch: which feeds the phone
 * syncs to the Wear app (up to three; nubecita-jh9c.1).
 *
 * Pushed from the Settings "Watch" row, which only appears while a paired watch
 * has the Wear app. Tagged `adaptiveDialog()` in `SettingsNavigationModule`
 * like the other Settings sub-pages.
 */
@Serializable
data object WatchSettings : NavKey
