package net.kikin.nubecita.feature.profile.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Settings screen.
 *
 * Lives alongside [Profile] in `:feature:profile:api` rather than its own
 * `:feature:settings:api` module — Settings is a sub-route of the You tab
 * and shares the profile feature's epic (see
 * add-adaptive-navigation-shell/design.md D6). It will graduate to its own
 * module if and when growth justifies a split.
 */
@Serializable
data object Settings : NavKey
