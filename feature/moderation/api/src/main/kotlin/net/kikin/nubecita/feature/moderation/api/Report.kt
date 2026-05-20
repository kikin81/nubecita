package net.kikin.nubecita.feature.moderation.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.kikin.nubecita.data.models.PostUi

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
 * (`:feature:feed:impl`, `:feature:profile:impl`,
 * `:feature:postdetail:impl`) can construct + push the key without
 * depending on `:feature:moderation:impl`'s runtime graph.
 *
 * # Construction
 *
 * Prefer the [Companion.forPost] / [Companion.forAccount] factories at
 * call sites — host VMs handling a `PostOverflowAction.ReportPost` event
 * collapse to a single line:
 *
 * ```kotlin
 * PostOverflowAction.ReportPost ->
 *     sendEffect(FeedEffect.NavigateTo(Report.forPost(event.post)))
 * ```
 *
 * The factories sit next to the type they construct (single source of
 * truth) and the dep direction stays clean (`:feature:moderation:api`
 * → `:data:models`, never the other way).
 */
@Serializable
data class Report(
    val subject: ReportSubject,
) : NavKey {
    companion object {
        /**
         * Build a `Report` NavKey targeting the given [post]'s
         * `StrongRef` (`uri` + `cid`). Used by every host VM that
         * handles `PostOverflowAction.ReportPost` — Feed, PostDetail,
         * and Profile's Posts/Replies tabs.
         */
        fun forPost(post: PostUi): Report = Report(subject = ReportSubject.Post(uri = post.id, cid = post.cid))

        /**
         * Build a `Report` NavKey targeting an account by [did]. Used
         * by `ProfileViewModel.OnReportAccountRequested` — the
         * ProfileHero overflow's "Report account" row. The DID is the
         * account's decentralized identifier; the report submission
         * resolves it to a `RepoRef` at the XRPC boundary.
         */
        fun forAccount(did: String): Report = Report(subject = ReportSubject.Account(did = did))
    }
}
