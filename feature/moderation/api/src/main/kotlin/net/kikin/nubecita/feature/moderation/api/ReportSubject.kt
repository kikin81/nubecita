package net.kikin.nubecita.feature.moderation.api

import kotlinx.serialization.Serializable

/**
 * Discriminates between the two kinds of subject the Report dialog can
 * target.
 *
 * The wire-format `com.atproto.moderation.createReport.subject` is a
 * union of `com.atproto.repo.strongRef` (post) and
 * `com.atproto.admin.defs#repoRef` (account); this sealed type mirrors
 * that union at the navigation layer.
 *
 * All fields are plain `String` (not the atproto SDK's generated
 * `AtUri` / `Cid` / `Did` value classes and not typealiases) — matching
 * the codebase's convention for nav-layer wire data (`PostDetailRoute.postUri`,
 * `PostUi.id`, `PostUi.cid`). Consumers in `:feature:moderation:impl`
 * wrap to lexicon-typed values at the XRPC boundary.
 */
@Serializable
sealed interface ReportSubject {
    /**
     * Report a specific post by its strong reference.
     *
     * @param uri the post's AT URI (e.g.
     *   `at://did:plc:.../app.bsky.feed.post/3kxyz`).
     * @param cid the post record's content-addressed CID, paired with
     *   [uri] to form the `StrongRef` the lexicon's createReport
     *   subject union expects.
     */
    @Serializable
    data class Post(
        val uri: String,
        val cid: String,
    ) : ReportSubject

    /**
     * Report an account by its DID.
     *
     * @param did the account's decentralized identifier (e.g.
     *   `did:plc:abcdefghijklmnopqrstuvwx`). Resolves to a `RepoRef`
     *   for the createReport subject union.
     */
    @Serializable
    data class Account(
        val did: String,
    ) : ReportSubject
}
