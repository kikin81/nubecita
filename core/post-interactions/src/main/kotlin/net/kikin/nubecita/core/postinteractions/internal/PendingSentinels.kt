package net.kikin.nubecita.core.postinteractions.internal

/**
 * Sentinel AtUri-shaped string written to [PostInteractionState.viewerLikeUri]
 * while a like network call is in flight. The cache writes this on the
 * optimistic flip; on success it's replaced with the real wire-returned
 * AtUri; on failure it's reverted to the pre-tap value.
 *
 * The `at://` prefix is defensive: every real atproto URI starts with
 * `at://`, so the sentinel is syntactically URI-shaped. If a downstream
 * consumer ever bypasses `mergeInteractionState` and passes the value to
 * an `AtUri.parse()` call, the parse succeeds rather than throwing.
 * Belt-and-suspenders against future regressions.
 *
 * Sentinels live in the `internal` package — they are an implementation
 * detail of `DefaultPostInteractionsCache` and `mergeInteractionState`.
 * Consumers MUST NOT compare against these constants directly; the
 * `mergeInteractionState` extension strips them on the way out.
 */
internal const val PENDING_LIKE_SENTINEL: String = "at://pending:optimistic"

/**
 * Sibling of [PENDING_LIKE_SENTINEL] for in-flight repost calls.
 * Distinct value so a diagnostic / logging path that surfaces the
 * sentinel can distinguish like vs repost in flight.
 */
internal const val PENDING_REPOST_SENTINEL: String = "at://pending:optimistic-repost"
