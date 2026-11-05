package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Translation screen (a Settings
 * sub-route).
 *
 * Its own row rather than a child of Content filters or Media and animations.
 * Content filters decide what a reader sees at all; this decides what language
 * they see it in, and it carries a storage surface — language packs measured at
 * ~45MB each — that has no business hiding under something else.
 */
@Serializable
data object Translation : NavKey
