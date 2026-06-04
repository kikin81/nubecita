package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ReasonRepost
import io.github.kikin81.atproto.app.bsky.feed.ReplyRef
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.feedmapping.applyModeration
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi
import timber.log.Timber

/**
 * Project a [PostView] into a moderated [PostUi]: map it, then apply the
 * precomputed content-moderation decision against the viewer's [prefs].
 * [dropFiltered] `true` (the timeline leaf) drops a hard-filtered post (returns
 * null); `false` (reply-context root/parent) keeps it but covers its media.
 */
private fun PostView.toModeratedPostUi(
    prefs: ModerationPrefs,
    viewerDid: String?,
    dropFiltered: Boolean,
): PostUi? = toPostUiCore()?.applyModeration(labels, viewerDid, prefs, dropFiltered)

/**
 * Maps a [FeedViewPost] (the wire model returned by `app.bsky.feed.getTimeline`)
 * to the UI-ready [PostUi].
 *
 * Delegates the per-post projection (record decode, embed dispatch,
 * author / viewer mapping) to `:core:feed-mapping`'s [toPostUiCore], then
 * layers in the feed-specific `repostedBy` overlay sourced from
 * [FeedViewPost.reason] (which lives at the per-feed-entry level, not
 * on [PostView] itself).
 *
 * Returns `null` when the embedded `post.record` JSON cannot be decoded
 * as a well-formed `app.bsky.feed.post` record (missing required `text`
 * / `createdAt`, type-incompatible value). The repository's `mapNotNull`
 * filter then drops the entry. The function MUST NOT throw — every
 * spec-conforming `FeedViewPost` produces a non-null `PostUi`.
 */
fun FeedViewPost.toPostUiOrNull(
    prefs: ModerationPrefs = ModerationPrefs.DEFAULT,
    viewerDid: String? = null,
): PostUi? {
    // dropFiltered = true: a hard-filtered timeline post is removed from the feed.
    val core = post.toModeratedPostUi(prefs, viewerDid, dropFiltered = true) ?: return null
    val repostedBy = (reason as? ReasonRepost)?.by?.let { it.displayName ?: it.handle.raw }
    return if (repostedBy != null) core.copy(repostedBy = repostedBy) else core
}

/**
 * Maps a [FeedViewPost] to the renderable [FeedItemUi] sealed type — the
 * entry-point used by the repository and any future consumer that wants
 * cluster-vs-single rendering shape.
 *
 * Production semantics:
 * - `reply == null` → returns [FeedItemUi.Single].
 * - `reply.parent` is a `PostView` AND `reply.root` is a `PostView` AND
 *   both project successfully → returns [FeedItemUi.ReplyCluster] with
 *   `hasEllipsis = grandparentAuthor != null && grandparentAuthor.did != root.author.did`.
 * - `reply.parent` is `BlockedPost` / `NotFoundPost` / `Unknown` →
 *   returns [FeedItemUi.Single] and emits a `Timber.w` log so the
 *   fallback frequency is visible in dev builds (production tree is no-op).
 * - Leaf record cannot be projected (malformed JSON, unparseable
 *   createdAt) → returns `null`; same contract as [toPostUiOrNull].
 *
 * Designed to subsume `nubecita-im8`'s "Replying to @handle" header by
 * rendering the parent post inline — the context is implicit. Detailed
 * rationale in the openspec change `add-feed-cross-author-thread-cluster`.
 */
fun FeedViewPost.toFeedItemUiOrNull(
    prefs: ModerationPrefs = ModerationPrefs.DEFAULT,
    viewerDid: String? = null,
): FeedItemUi? {
    val leaf = toPostUiOrNull(prefs, viewerDid) ?: return null
    val replyRef = reply ?: return FeedItemUi.Single(leaf)

    val parentPostView = replyRef.parent as? PostView
    if (parentPostView == null) {
        Timber.w(
            "Reply parent is non-PostView (likely BlockedPost/NotFoundPost) for leaf=${leaf.id}; falling back to Single rendering",
        )
        return FeedItemUi.Single(leaf)
    }
    val rootPostView =
        replyRef.root as? PostView ?: run {
            Timber.w("Reply root is non-PostView for leaf=${leaf.id}; falling back to Single rendering")
            return FeedItemUi.Single(leaf)
        }

    // dropFiltered = false: reply context stays in the cluster; only its media
    // is covered if filtered, so the conversation thread remains intact.
    val parent = parentPostView.toModeratedPostUi(prefs, viewerDid, dropFiltered = false) ?: return FeedItemUi.Single(leaf)
    val root = rootPostView.toModeratedPostUi(prefs, viewerDid, dropFiltered = false) ?: return FeedItemUi.Single(leaf)

    return FeedItemUi.ReplyCluster(
        root = root,
        parent = parent,
        leaf = leaf,
        hasEllipsis = replyRef.hasEllipsisRelativeToRoot(rootAuthorDid = root.author.did),
    )
}

/**
 * Heuristic used to decide whether a `ThreadFold` ("View full thread")
 * indicator goes between root and parent.
 *
 * The lexicon doesn't expose a precise count of intermediate posts, but
 * `replyRef.grandparentAuthor` (the author of the post one level above
 * `parent`) gives us a useful signal: if it's non-null AND distinct
 * from `root.author.did`, then there's at least one post (the
 * grandparent) sitting between root and parent — i.e., the chain is not
 * `root → parent → leaf` but rather `root → ... → grandparent → parent → leaf`.
 *
 * False negatives are possible (the lexicon may not always populate
 * `grandparentAuthor` reliably for very deep threads), but the failure
 * mode is "no fold rendered when there should be one" which only loses
 * a hint, not correctness. False positives are not possible from this
 * heuristic alone.
 */
private fun ReplyRef.hasEllipsisRelativeToRoot(rootAuthorDid: String): Boolean {
    val gp = grandparentAuthor ?: return false
    return gp.did.raw != rootAuthorDid
}

/**
 * Page-level projection that runs after the per-entry pass and groups
 * consecutive same-author self-reply runs into [FeedItemUi.SelfThreadChain]
 * entries.
 *
 * The strict link rule (`add-feed-same-author-thread-chain` design
 * Decision 1, captured in the `feature-feed` capability spec): two
 * consecutive entries `e[i-1]` and `e[i]` link into a chain iff —
 *
 * 1. `e[i].reply != null`
 * 2. `e[i].reply.parent` is a [PostView] (not `BlockedPost` / `NotFoundPost`
 *    / open-union `Unknown`)
 * 3. `e[i].reply.parent.author.did == e[i].post.author.did`
 * 4. `e[i].reply.parent.uri == e[i-1].post.uri` — the link is unbroken in
 *    the wire response (skip-ahead chains are rejected)
 * 5. neither `e[i-1]` nor `e[i]` carries a `ReasonRepost`
 *
 * A chain is a maximal run of linked entries; minimum size is 2. Non-
 * linked entries route through the existing per-entry projection
 * ([FeedItemUi.Single] / [FeedItemUi.ReplyCluster]) unchanged.
 *
 * Page-boundary chain merging (extending a chain across two pages) is
 * NOT done here — that lives in `FeedViewModel` so it can read the
 * existing tail of `feedItems`. The mapper stays a pure per-page
 * function.
 */
fun List<FeedViewPost>.toFeedItemsUi(
    prefs: ModerationPrefs = ModerationPrefs.DEFAULT,
    viewerDid: String? = null,
): ImmutableList<FeedItemUi> {
    if (isEmpty()) return persistentListOf()

    val out = mutableListOf<FeedItemUi>()
    val pendingChain = mutableListOf<PostUi>()
    var pendingChainPrev: FeedViewPost? = null

    fun flushChain() {
        when (pendingChain.size) {
            0 -> Unit
            1 -> {
                // Solo chain — fall back to the per-entry projection so a
                // wire entry whose `reply.parent` is a (non-chained)
                // PostView still surfaces as a ReplyCluster (cross-author
                // reply context). The chain accumulator buffered the post
                // hoping it would link to a successor; since it didn't,
                // re-project from the wire to recover the canonical shape.
                pendingChainPrev?.toFeedItemUiOrNull(prefs, viewerDid)?.let { out += it }
            }
            else -> out += FeedItemUi.SelfThreadChain(posts = pendingChain.toImmutableList())
        }
        pendingChain.clear()
        pendingChainPrev = null
    }

    for (wire in this) {
        // malformed record OR hard-filtered post — skipped (dropped from feed).
        val postUi = wire.toPostUiOrNull(prefs, viewerDid) ?: continue
        val prev = pendingChainPrev
        if (prev != null && linksTo(prev = prev, next = wire)) {
            pendingChain += postUi
            pendingChainPrev = wire
        } else {
            flushChain()
            pendingChain += postUi
            pendingChainPrev = wire
        }
    }
    flushChain()
    return out.toImmutableList()
}

/**
 * The strict link-rule predicate from
 * `add-feed-same-author-thread-chain` design Decision 1. Encapsulated
 * here so the page-level pass and the VM's page-boundary merge can
 * share the exact same definition — diverging interpretations would be
 * a silent bug.
 */
fun linksTo(
    prev: FeedViewPost,
    next: FeedViewPost,
): Boolean {
    // 1 + 5: next must be a non-reposted reply; prev must also not be reposted.
    if (next.reason is ReasonRepost) return false
    if (prev.reason is ReasonRepost) return false
    val replyRef = next.reply ?: return false
    // 2: parent must be a real PostView (not Blocked/NotFound/Unknown)
    val parent = replyRef.parent as? PostView ?: return false
    // 3: same author DID
    if (parent.author.did.raw != next.post.author.did.raw) return false
    // 4: parent URI matches prev's post URI (strict — no skip-ahead)
    if (parent.uri.raw != prev.post.uri.raw) return false
    return true
}

/**
 * Variant of [linksTo] for the page-boundary merge case, where the
 * "previous" entry is the existing feedItems' tail-leaf [PostUi] (not a
 * wire [FeedViewPost]). The wire-level `ReasonRepost` check on prev
 * collapses to `prev.repostedBy != null` — the per-entry projection
 * sets that field iff the wire entry carried a `ReasonRepost`. The
 * remaining checks (next-side reason, reply.parent shape, same author
 * DID, parent URI matching) read off the next-side wire entry exactly
 * as in [linksTo].
 */
fun PostUi.linksToWire(next: FeedViewPost): Boolean {
    if (this.repostedBy != null) return false
    if (next.reason is ReasonRepost) return false
    val replyRef = next.reply ?: return false
    val parent = replyRef.parent as? PostView ?: return false
    if (parent.author.did.raw != next.post.author.did.raw) return false
    if (parent.uri.raw != this.id) return false
    return true
}
