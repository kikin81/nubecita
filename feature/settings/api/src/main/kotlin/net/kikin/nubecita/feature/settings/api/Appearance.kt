package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Appearance screen (a Settings sub-route).
 *
 * Pushed onto MainShell's inner back stack from the Settings "Appearance" row
 * via `onNavigateTo(Appearance)`. Tagged `adaptiveDialog()` in
 * `SettingsNavigationModule` so it presents full-screen on phone and coalesces
 * into the Settings dialog (content-swap) on tablet — same as Feed preferences.
 *
 * A top-level Settings row rather than a child of any hub: it is where every
 * future look-and-feel option lands (custom themes first), so it earns its own
 * entry rather than nesting under an existing one.
 */
@Serializable
data object Appearance : NavKey
