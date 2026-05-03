package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.data.LikeRepostRepository
import net.kikin.nubecita.feature.feed.impl.data.TimelinePage
import net.kikin.nubecita.feature.feed.impl.data.dedupeClusterContext
import net.kikin.nubecita.feature.feed.impl.data.linksToWire
import net.kikin.nubecita.feature.feed.impl.share.toShareIntent
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
internal class FeedViewModel
    @Inject
    constructor(
        private val feedRepository: FeedRepository,
        private val likeRepostRepository: LikeRepostRepository,
    ) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState()) {
        override fun handleEvent(event: FeedEvent) {
            when (event) {
                FeedEvent.Load -> load()
                FeedEvent.Refresh -> refresh()
                FeedEvent.LoadMore -> loadMore()
                FeedEvent.Retry -> load()
                FeedEvent.ClearError -> Unit
                is FeedEvent.OnPostTapped -> sendEffect(FeedEffect.NavigateToPost(event.post.id))
                is FeedEvent.OnAuthorTapped -> sendEffect(FeedEffect.NavigateToAuthor(event.authorDid))
                is FeedEvent.OnLikeClicked -> toggleLike(event.post)
                is FeedEvent.OnRepostClicked -> toggleRepost(event.post)
                is FeedEvent.OnReplyClicked -> Unit
                is FeedEvent.OnShareClicked -> sendEffect(FeedEffect.SharePost(event.post.toShareIntent()))
                is FeedEvent.OnShareLongPressed ->
                    sendEffect(FeedEffect.CopyPermalink(event.post.toShareIntent().permalink))
            }
        }

        private fun load() {
            // Allow initial load (from Idle, the default) and Retry from
            // InitialError. Any other status (InitialLoading, Refreshing,
            // Appending) means a fetch is already in flight — second Load
            // is a no-op so the UI can dispatch on every recomposition
            // without N concurrent fetches racing on setState.
            val status = uiState.value.loadStatus
            if (status != FeedLoadStatus.Idle && status !is FeedLoadStatus.InitialError) return
            setState { copy(loadStatus = FeedLoadStatus.InitialLoading) }
            viewModelScope.launch {
                feedRepository
                    .getTimeline(cursor = null)
                    .onSuccess { page -> applyInitialPage(page) }
                    .onFailure { throwable ->
                        setState {
                            copy(loadStatus = FeedLoadStatus.InitialError(throwable.toFeedError()))
                        }
                    }
            }
        }

        private fun refresh() {
            // Refresh and append/initial-load are mutually exclusive per the
            // mvi-foundation spec: dispatching Refresh while another load is
            // in flight is a no-op (drop the event rather than queue, so the
            // back-pressure on a flapping pull-to-refresh gesture stays at the
            // user's wrist).
            if (uiState.value.loadStatus != FeedLoadStatus.Idle) return
            setState { copy(loadStatus = FeedLoadStatus.Refreshing) }
            viewModelScope.launch {
                feedRepository
                    .getTimeline(cursor = null)
                    .onSuccess { page ->
                        // Replace head; cursor becomes the response cursor (may be null
                        // when the entire feed fits in one page).
                        setState {
                            copy(
                                feedItems = page.feedItems.dedupeClusterContext().toImmutableList(),
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                                loadStatus = FeedLoadStatus.Idle,
                            )
                        }
                    }.onFailure { throwable ->
                        // Preserve feedItems on refresh failure; surface as a snackbar.
                        setState { copy(loadStatus = FeedLoadStatus.Idle) }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        private fun loadMore() {
            val current = uiState.value
            // End-of-feed: do nothing. Idempotent — repeat LoadMore once
            // endReached has flipped is a no-op.
            if (current.endReached) return
            // Mutually exclusive with refresh / initial-load: if any fetch is
            // already in flight, drop the LoadMore event. Prevents the
            // overlapping-fetch race where two getTimeline calls both update
            // feedItems + nextCursor and the last writer wins non-deterministically.
            if (current.loadStatus != FeedLoadStatus.Idle) return
            setState { copy(loadStatus = FeedLoadStatus.Appending) }
            viewModelScope.launch {
                feedRepository
                    .getTimeline(cursor = current.nextCursor)
                    .onSuccess { page ->
                        setState {
                            // Page-boundary chain merge: if the existing tail's
                            // last chainable PostUi links (via the strict rule)
                            // to the new page's first wire entry, absorb the
                            // tail into the new page's first item before
                            // appending. Per
                            // `add-feed-same-author-thread-chain` design
                            // Decision 3, arbitrary cursor cuts shouldn't
                            // visually break a self-thread chain.
                            val merge =
                                mergeChainBoundary(
                                    existing = feedItems,
                                    newPageItems = page.feedItems,
                                    newPageWirePosts = page.wirePosts,
                                )
                            // De-dupe by FeedItemUi.key so a server returning a page
                            // that overlaps the current tail (rare but possible during
                            // cursor resyncs) doesn't show the same item twice. The
                            // key is the leaf URI for ReplyCluster / SelfThreadChain
                            // and the post URI for Single — stable across paging.
                            val seen = merge.trimmedExisting.mapTo(HashSet()) { it.key }
                            val merged = (merge.trimmedExisting + merge.pageWithAbsorbedHead.filter { seen.add(it.key) })
                            copy(
                                feedItems = merged.dedupeClusterContext().toImmutableList(),
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                                loadStatus = FeedLoadStatus.Idle,
                            )
                        }
                    }.onFailure { throwable ->
                        // Preserve feedItems AND cursor on append failure so the user
                        // can retry from the same page boundary.
                        setState { copy(loadStatus = FeedLoadStatus.Idle) }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        private fun applyInitialPage(page: TimelinePage) {
            val deduped = page.feedItems.dedupeClusterContext().toImmutableList()
            setState {
                copy(
                    feedItems = deduped,
                    nextCursor = page.nextCursor,
                    endReached = page.nextCursor == null || deduped.isEmpty(),
                    loadStatus = FeedLoadStatus.Idle,
                )
            }
        }

        /**
         * Optimistic like / unlike. Flips `viewer.isLikedByViewer` and adjusts
         * `stats.likeCount` immediately, then fires the repository call. On
         * success, rewrites `viewer.likeUri` (a like creates a new record
         * whose AT URI is unknown until the server responds). On failure,
         * restores the pre-tap snapshot of the affected post and emits a
         * non-sticky [FeedEffect.ShowError]. The post is located by id so a
         * stale [PostUi] reference (from a recomposition that happened while
         * the call was in flight) doesn't desync the rollback target.
         */
        private fun toggleLike(post: PostUi) {
            val currentItems = uiState.value.feedItems
            val snapshot = currentItems.findPost(post.id) ?: return
            val wasLiked = snapshot.viewer.isLikedByViewer
            val optimistic =
                snapshot.copy(
                    viewer = snapshot.viewer.copy(isLikedByViewer = !wasLiked),
                    stats =
                        snapshot.stats.copy(
                            likeCount =
                                (snapshot.stats.likeCount + if (wasLiked) -1 else 1)
                                    .coerceAtLeast(0),
                        ),
                )
            setState { copy(feedItems = feedItems.replacePost(optimistic)) }

            viewModelScope.launch {
                val result =
                    if (wasLiked) {
                        val likeUri = snapshot.viewer.likeUri
                        if (likeUri == null) {
                            // No prior likeUri means we never received the
                            // server-assigned URI from a previous like (the
                            // mapper omits it for never-liked posts, and a
                            // tap-tap-fast race could land here). Treat as a
                            // failure so the optimistic toggle rolls back —
                            // we'd otherwise have no way to call unlike.
                            Result.failure(IllegalStateException("no likeUri to unlike"))
                        } else {
                            likeRepostRepository.unlike(AtUri(likeUri))
                        }
                    } else {
                        likeRepostRepository.like(StrongRef(uri = AtUri(snapshot.id), cid = Cid(snapshot.cid)))
                    }
                result
                    .onSuccess { value ->
                        // Like → store the new likeUri (an AtUri); unlike →
                        // clear it. Re-fetch the post by id so a concurrent
                        // refresh that landed mid-call doesn't lose its
                        // updates.
                        setState {
                            val currentPost = feedItems.findPost(post.id) ?: return@setState this
                            val newViewer =
                                if (wasLiked) {
                                    currentPost.viewer.copy(likeUri = null)
                                } else {
                                    currentPost.viewer.copy(likeUri = (value as AtUri).raw)
                                }
                            copy(feedItems = feedItems.replacePost(currentPost.copy(viewer = newViewer)))
                        }
                    }.onFailure { throwable ->
                        // If the post is still our optimistic write (the
                        // common case — no concurrent refresh / append),
                        // restore the snapshot wholesale. If a concurrent
                        // update landed mid-call, only revert the like-
                        // related fields; the fresh text/author/embed/
                        // stats and the server-canonical likeCount stay
                        // put, since the user's optimistic +1/-1 never
                        // persisted on the server.
                        setState {
                            val currentPost = feedItems.findPost(post.id) ?: return@setState this
                            val rolledBack =
                                if (currentPost === optimistic) {
                                    snapshot
                                } else {
                                    currentPost.copy(
                                        viewer =
                                            currentPost.viewer.copy(
                                                isLikedByViewer = snapshot.viewer.isLikedByViewer,
                                                likeUri = snapshot.viewer.likeUri,
                                            ),
                                    )
                                }
                            copy(feedItems = feedItems.replacePost(rolledBack))
                        }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        /**
         * Optimistic repost / unrepost. Mirrors [toggleLike] in shape — the
         * only differences are which fields move (`isRepostedByViewer`,
         * `repostCount`, `repostUri`) and which repository entry points we
         * call. Kept as a separate function rather than parameterized over
         * the like/repost projection because the combined version is harder
         * to read and the per-toggle handlers are short.
         */
        private fun toggleRepost(post: PostUi) {
            val currentItems = uiState.value.feedItems
            val snapshot = currentItems.findPost(post.id) ?: return
            val wasReposted = snapshot.viewer.isRepostedByViewer
            val optimistic =
                snapshot.copy(
                    viewer = snapshot.viewer.copy(isRepostedByViewer = !wasReposted),
                    stats =
                        snapshot.stats.copy(
                            repostCount =
                                (snapshot.stats.repostCount + if (wasReposted) -1 else 1)
                                    .coerceAtLeast(0),
                        ),
                )
            setState { copy(feedItems = feedItems.replacePost(optimistic)) }

            viewModelScope.launch {
                val result =
                    if (wasReposted) {
                        val repostUri = snapshot.viewer.repostUri
                        if (repostUri == null) {
                            Result.failure(IllegalStateException("no repostUri to unrepost"))
                        } else {
                            likeRepostRepository.unrepost(AtUri(repostUri))
                        }
                    } else {
                        likeRepostRepository.repost(StrongRef(uri = AtUri(snapshot.id), cid = Cid(snapshot.cid)))
                    }
                result
                    .onSuccess { value ->
                        setState {
                            val currentPost = feedItems.findPost(post.id) ?: return@setState this
                            val newViewer =
                                if (wasReposted) {
                                    currentPost.viewer.copy(repostUri = null)
                                } else {
                                    currentPost.viewer.copy(repostUri = (value as AtUri).raw)
                                }
                            copy(feedItems = feedItems.replacePost(currentPost.copy(viewer = newViewer)))
                        }
                    }.onFailure { throwable ->
                        // Mirror [toggleLike]'s rollback policy — restore
                        // the snapshot wholesale only when the optimistic
                        // write is still in place; otherwise revert just
                        // the repost-related fields onto the concurrent
                        // update so fresh non-repost data is preserved.
                        setState {
                            val currentPost = feedItems.findPost(post.id) ?: return@setState this
                            val rolledBack =
                                if (currentPost === optimistic) {
                                    snapshot
                                } else {
                                    currentPost.copy(
                                        viewer =
                                            currentPost.viewer.copy(
                                                isRepostedByViewer = snapshot.viewer.isRepostedByViewer,
                                                repostUri = snapshot.viewer.repostUri,
                                            ),
                                    )
                                }
                            copy(feedItems = feedItems.replacePost(rolledBack))
                        }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        private fun Throwable.toFeedError(): FeedError =
            when (this) {
                is NoSessionException -> FeedError.Unauthenticated
                is IOException -> FeedError.Network
                else -> FeedError.Unknown(cause = message)
            }
    }

/**
 * Result of [mergeChainBoundary]. Two `ImmutableList<FeedItemUi>` fields
 * with named slots — the previous `Pair<ImmutableList, ImmutableList>`
 * shape was a destructuring footgun: a future caller writing
 * `val (page, existing) = mergeChainBoundary(...)` (wrong order) would
 * compile cleanly and silently render the feed out of order. Named
 * fields make the intent unambiguous at every call site.
 */
private data class ChainBoundaryMerge(
    /**
     * Existing `feedItems` with the tail popped if it was absorbed into
     * a chain at the new page's head; otherwise unchanged.
     */
    val trimmedExisting: ImmutableList<FeedItemUi>,
    /**
     * The new page's `feedItems` with its first entry replaced by an
     * absorbing [FeedItemUi.SelfThreadChain] when the merge fired;
     * otherwise unchanged.
     */
    val pageWithAbsorbedHead: ImmutableList<FeedItemUi>,
)

/**
 * Page-boundary chain merge. Given the existing `feedItems` and a
 * freshly-loaded `TimelinePage`, attempt to absorb the existing tail
 * into the new page's first feed item if the strict link rule holds.
 *
 * Caller appends [ChainBoundaryMerge.trimmedExisting] to
 * [ChainBoundaryMerge.pageWithAbsorbedHead]; subsequent dedupe-by-key
 * handles any server-side overlap independently.
 *
 * Merge is rejected (no-op) when:
 * - `existing` is empty or `newPageItems` / `newPageWirePosts` is empty.
 * - The existing tail is a [FeedItemUi.ReplyCluster] (cross-author
 *   clusters don't extend into same-author chains).
 * - The existing tail's last post has `repostedBy != null` (the wire
 *   entry that produced it was a `ReasonRepost` — chain-incompatible).
 * - The new page's first wire entry doesn't link to the tail's last
 *   post via the strict rule (`reply.parent.uri == tail.id`, same
 *   author, no `ReasonRepost` on either side).
 *
 * Chain extension only ever consumes the existing tail + the new page's
 * FIRST item. If the new page already grouped consecutive same-author
 * replies into a chain at its head (the page-internal projection ran in
 * the mapper), the merge prepends the existing tail's posts to that
 * chain. Subsequent new-page entries are unaffected — the strict link
 * rule's adjacency requirement is preserved by chain construction.
 */
private fun mergeChainBoundary(
    existing: ImmutableList<FeedItemUi>,
    newPageItems: ImmutableList<FeedItemUi>,
    newPageWirePosts: ImmutableList<FeedViewPost>,
): ChainBoundaryMerge {
    if (existing.isEmpty() || newPageItems.isEmpty() || newPageWirePosts.isEmpty()) {
        return ChainBoundaryMerge(trimmedExisting = existing, pageWithAbsorbedHead = newPageItems)
    }

    val existingTail = existing.last()
    val tailPosts: List<PostUi> =
        when (existingTail) {
            is FeedItemUi.Single -> listOf(existingTail.post)
            is FeedItemUi.SelfThreadChain -> existingTail.posts
            is FeedItemUi.ReplyCluster ->
                return ChainBoundaryMerge(trimmedExisting = existing, pageWithAbsorbedHead = newPageItems)
        }
    val tailLeafPost = tailPosts.last()

    val firstWire = newPageWirePosts.first()
    if (!tailLeafPost.linksToWire(firstWire)) {
        return ChainBoundaryMerge(trimmedExisting = existing, pageWithAbsorbedHead = newPageItems)
    }

    // Extract the new page's first-item posts to prepend the existing
    // tail to. The page-internal projection might have produced any of:
    // - a Single (the wire entry didn't link to anything within the page)
    // - a SelfThreadChain (the wire entry was the head of a within-page chain)
    // - a ReplyCluster (the wire entry has a `reply.parent` that's a real
    //   PostView; the per-entry mapper eagerly produces a ReplyCluster
    //   regardless of author DID). For chain purposes, only the leaf
    //   matters — the cluster's root/parent context is exactly the post
    //   at the end of the existing tail (alice/N) which we're already
    //   prepending. Adding root/parent again would duplicate it.
    val newFirstItem = newPageItems.first()
    val newFirstPosts: List<PostUi> =
        when (newFirstItem) {
            is FeedItemUi.Single -> listOf(newFirstItem.post)
            is FeedItemUi.ReplyCluster -> listOf(newFirstItem.leaf)
            is FeedItemUi.SelfThreadChain -> newFirstItem.posts
        }
    val absorbedFirst =
        FeedItemUi.SelfThreadChain(
            posts = (tailPosts + newFirstPosts).toImmutableList(),
        )

    return ChainBoundaryMerge(
        trimmedExisting = existing.dropLast(1).toImmutableList(),
        pageWithAbsorbedHead = (listOf<FeedItemUi>(absorbedFirst) + newPageItems.drop(1)).toImmutableList(),
    )
}

/**
 * Locate a [PostUi] by id across all visible feed entries — the lone
 * post inside a [FeedItemUi.Single], any of root / parent / leaf inside
 * a [FeedItemUi.ReplyCluster], or any post inside a
 * [FeedItemUi.SelfThreadChain]. Returns the first match (each post id is
 * globally unique within the feed by construction; the de-dupe pass in
 * the repository enforces this).
 */
private fun ImmutableList<FeedItemUi>.findPost(id: String): PostUi? =
    firstNotNullOfOrNull { item ->
        when (item) {
            is FeedItemUi.Single -> item.post.takeIf { it.id == id }
            is FeedItemUi.ReplyCluster ->
                when (id) {
                    item.root.id -> item.root
                    item.parent.id -> item.parent
                    item.leaf.id -> item.leaf
                    else -> null
                }
            is FeedItemUi.SelfThreadChain -> item.posts.firstOrNull { it.id == id }
        }
    }

/**
 * Replace the post with id == [updated.id] inside the list, preserving
 * the surrounding [FeedItemUi.Single] / [FeedItemUi.ReplyCluster] /
 * [FeedItemUi.SelfThreadChain] shape. No-ops the entries that don't
 * match. The cluster + chain branches swap only the matching slot(s)
 * and reuse untouched references via referential equality so unrelated
 * posts don't trigger Compose recomposition.
 */
private fun ImmutableList<FeedItemUi>.replacePost(updated: PostUi): ImmutableList<FeedItemUi> {
    val targetId = updated.id
    return map { item ->
        when (item) {
            is FeedItemUi.Single ->
                if (item.post.id == targetId) item.copy(post = updated) else item
            is FeedItemUi.ReplyCluster -> {
                val root = if (item.root.id == targetId) updated else item.root
                val parent = if (item.parent.id == targetId) updated else item.parent
                val leaf = if (item.leaf.id == targetId) updated else item.leaf
                if (root === item.root && parent === item.parent && leaf === item.leaf) {
                    item
                } else {
                    item.copy(root = root, parent = parent, leaf = leaf)
                }
            }
            is FeedItemUi.SelfThreadChain -> {
                if (item.posts.none { it.id == targetId }) {
                    item
                } else {
                    val newPosts =
                        item.posts.map { post ->
                            if (post.id == targetId) updated else post
                        }
                    item.copy(posts = newPosts.toImmutableList())
                }
            }
        }
    }.toImmutableList()
}
