package net.kikin.nubecita.feature.moderation.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Report dialog.
 *
 * The dialog is a sub-route pushed onto whichever top-level tab the user
 * is currently on (Feed PostCard overflow → push onto Feed back stack;
 * ProfileHero overflow → push onto Profile back stack). It is rendered
 * as a Material 3 `ModalBottomSheet` by `:feature:moderation:impl`'s
 * `@MainShell` entry provider.
 *
 * [subject] discriminates between reporting a specific post (in which
 * case both AT URI and CID are needed to construct the `StrongRef`
 * subject union variant for `com.atproto.moderation.createReport`) and
 * reporting an account (in which case only the DID is needed for the
 * `RepoRef` variant).
 *
 * Lives in `:feature:moderation:api` so cross-feature callers
 * (`:feature:feed:impl`, `:feature:profile:impl`) can construct + push
 * the key without depending on `:feature:moderation:impl`'s runtime
 * graph.
 */
@Serializable
data class Report(
    val subject: ReportSubject,
) : NavKey
