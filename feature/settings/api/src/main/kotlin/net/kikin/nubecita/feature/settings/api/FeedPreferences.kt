package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Feed preferences screen (a Settings
 * sub-route).
 *
 * Pushed onto MainShell's inner back stack from the Settings "Feed preferences"
 * row via `onNavigateTo(FeedPreferences)`. Tagged `adaptiveDialog()` in
 * `SettingsNavigationModule` so it presents full-screen on phone and coalesces
 * into the Settings dialog (content-swap) on tablet — same as Content filters.
 *
 * A top-level Settings row rather than a child of the Moderation hub: these are
 * feed *display* preferences (what appears in your timeline), while Moderation
 * covers content labels and blocked accounts. See `nubecita-1fmx.2` decision D2.
 */
@Serializable
data object FeedPreferences : NavKey
