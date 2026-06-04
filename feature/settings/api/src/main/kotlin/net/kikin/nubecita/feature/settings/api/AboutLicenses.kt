package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the open-source licenses screen (a sub-route
 * of [About]). Pushed via `onNavigateTo(AboutLicenses)` from the About screen.
 * Tagged `adaptiveDialog()` so it joins the same Settings/About dialog run on
 * tablet (content-swap) and pushes full-screen on phone.
 */
@Serializable
data object AboutLicenses : NavKey
