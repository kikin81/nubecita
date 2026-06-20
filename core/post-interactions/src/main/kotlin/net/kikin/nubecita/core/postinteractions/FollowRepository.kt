package net.kikin.nubecita.core.postinteractions

/**
 * Follow / unfollow write surface against the authenticated user's PDS.
 *
 * Headless — the interface returns plain [Result] outcomes and never touches
 * UI state. Optimistic toggling and error surfacing are the caller's job.
 *
 * Lives in `:core/post-interactions` alongside [LikeRepostRepository]; both
 * `:feature:profile:impl` (profile Follow button) and `:feature:chats:impl`
 * (group-details per-member Follow) consume this single implementation.
 *
 * Operations map onto AT Protocol primitives:
 *
 * - [follow] — `com.atproto.repo.createRecord` with a freshly built
 *   `app.bsky.graph.follow` record whose `subject` is [subjectDid]. The
 *   returned at-uri (raw) is the AT-URI of the newly-created record; the
 *   caller persists it so a later [unfollow] can target the right record.
 * - [unfollow] — `com.atproto.repo.deleteRecord` against the supplied
 *   AT-URI. The repo (DID) and rkey are parsed from the URI; the collection
 *   is fixed.
 *
 * String types (not `AtUri` / `StrongRef`) match the profile + chat consumer
 * contracts. The implementation redacts PII: the followed account's DID
 * ([subjectDid]) and the viewer-DID-bearing [unfollow] URI are never logged
 * — only the error identity is, mirroring [LikeRepostRepository].
 */
interface FollowRepository {
    /** Create an `app.bsky.graph.follow` record for [subjectDid]; returns the new record's at-uri (raw). */
    suspend fun follow(subjectDid: String): Result<String>

    /** Delete the `app.bsky.graph.follow` record at [followUri]. */
    suspend fun unfollow(followUri: String): Result<Unit>
}
