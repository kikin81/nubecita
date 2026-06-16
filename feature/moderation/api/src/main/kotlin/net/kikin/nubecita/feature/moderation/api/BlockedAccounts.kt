package net.kikin.nubecita.feature.moderation.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the blocked-accounts management screen
 * (`nubecita-oftc.17`). A full-screen list reached from Settings → Moderation;
 * rendered by `:feature:moderation:impl`'s `@MainShell` entry provider. No
 * arguments — the list is the viewer's own blocks.
 */
@Serializable
data object BlockedAccounts : NavKey
