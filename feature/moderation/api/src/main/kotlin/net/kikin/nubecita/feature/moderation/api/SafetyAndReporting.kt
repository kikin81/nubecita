package net.kikin.nubecita.feature.moderation.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the global Safety & Reporting hub.
 * Reached from Settings → Safety & reporting or Moderation → Safety & reporting.
 * Rendered by `:feature:moderation:impl`'s `@MainShell` entry provider.
 */
@Serializable
data object SafetyAndReporting : NavKey
