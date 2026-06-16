package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Moderation hub — a settings sub-screen
 * that groups the moderation tools (Content filters, Blocked accounts), keeping
 * the main Settings page lean. Pushed onto MainShell's inner back stack from the
 * Settings "Moderation" row; rendered by `:feature:settings:impl`.
 */
@Serializable
data object Moderation : NavKey
