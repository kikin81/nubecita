package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.quotedRecord
import net.kikin.nubecita.designsystem.component.BlockedPostCard
import net.kikin.nubecita.designsystem.component.MediaCover
import net.kikin.nubecita.designsystem.component.NotFoundPostCard
import net.kikin.nubecita.designsystem.component.NubecitaPullToRefreshBox
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.designsystem.component.ThreadCluster
import net.kikin.nubecita.feature.feed.impl.FeedTestTags
import net.kikin.nubecita.feature.feed.impl.video.FeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feed.impl.video.mostVisibleVideoTarget

private const val PREFETCH_DISTANCE = 5

/**
 * Reusable post-list composable for the home feed and (soon) the
 * co-located custom-feed view.
 *
 * Renders a [NubecitaPullToRefreshBox] wrapping a [LazyColumn] of
 * [FeedItemUi] entries — singles, reply clusters, same-author thread
 * chains, and tombstone variants (blocked / not-found). Owns the
 * per-list NSFW reveal state ([rememberSaveable]), the pagination
 * [snapshotFlow] trigger, and the scroll-gated video-bind
 * [LaunchedEffect] when a [coordinator] is present.
 *
 * Call sites pass hoisted per-card Surface tokens ([cardColor] /
 * [cardShape]) so the [LazyColumn] items lambda never re-subscribes to
 * [androidx.compose.material3.MaterialTheme] per visible item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostFeedList(
    feedItems: ImmutableList<FeedItemUi>,
    isAppending: Boolean,
    isRefreshing: Boolean,
    listState: LazyListState,
    callbacks: PostCallbacks,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onImageTap: (post: PostUi, imageIndex: Int) -> Unit,
    onQuotedImageTap: (quotedPostUri: String, imageIndex: Int) -> Unit,
    // Per-card Surface tokens, hoisted at FeedScreenContent so the
    // LazyColumn items lambda doesn't re-subscribe to LocalColorScheme /
    // LocalShapes per visible item. See FeedScreenContent's `cardColor`
    // / `cardShape` declarations for the rationale.
    cardColor: Color,
    cardShape: Shape,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    lastLikeTapPostUri: String? = null,
    lastRepostTapPostUri: String? = null,
    onVideoTap: ((postUri: String) -> Unit)? = null,
    coordinator: FeedVideoPlayerCoordinator? = null,
) {
    // Per-list reveal state: ids of posts whose covered (NSFW-labelled) media the
    // viewer chose to "Show anyway". Terminates at the screen — the VM never sees
    // it (a Compose-runtime concern, like scroll state). PersistentSet (not a plain
    // Set) keeps the param @Stable; an explicit listSaver round-trips it through a
    // Bundle as an ArrayList — autoSaver can't reliably save a kotlin Set (its
    // concrete impl differs empty-vs-nonempty), which would crash at save time on a
    // config change — so reveals survive rotation.
    var revealedMedia by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toPersistentSet() }),
    ) { mutableStateOf(persistentSetOf<String>()) }
    NubecitaPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        // Offset the refresh indicator below the chip-row topBar (it would
        // otherwise anchor top-center behind it). Same inset the LazyColumn
        // applies as contentPadding for its items. (nubecita-tfbc)
        indicatorPadding = contentPadding,
    ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    // Stable selector for the :benchmark Macrobenchmark
                    // module's FeedScrollBenchmark. Surfaces as a UIAutomator
                    // resource-id via the testTagsAsResourceId flag set on
                    // MainActivity's root semantics modifier.
                    .testTag(FeedTestTags.LIST),
            // contentPadding (NOT Modifier.padding on the parent) — keeps the
            // LazyColumn surface extending behind translucent system bars
            // while pushing the first/last items into the safe area. The
            // pagination snapshotFlow's visibleItemsInfo already accounts
            // for contentPadding so the prefetch threshold is unaffected.
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = feedItems,
                key = { it.key },
                contentType = {
                    when (it) {
                        is FeedItemUi.Single -> "single"
                        is FeedItemUi.ReplyCluster -> "cluster"
                        is FeedItemUi.SelfThreadChain -> "chain"
                        is FeedItemUi.Blocked -> "blocked"
                        is FeedItemUi.NotFound -> "notfound"
                    }
                },
            ) { item ->
                // The video coordinator binds against the leaf URI for
                // Single, ReplyCluster, AND SelfThreadChain — clusters
                // and chains are leaf-only video targets (see
                // ThreadCluster KDoc + design.md decision D3 from m28.3,
                // applied here by symmetry per
                // `add-feed-same-author-thread-chain` task 5.2).
                //
                // Tombstone variants (Blocked / NotFound) carry no
                // renderable post, so they never produce a leaf and
                // never participate in the video coordinator. Render
                // them inline and skip the slot wiring entirely — both
                // composables are stateless, no remember key churn
                // possible. The early `return@items` keeps the leaf
                // smart-cast narrow for the rest of the block.
                when (item) {
                    is FeedItemUi.Blocked -> {
                        BlockedPostCard(onUnblock = null)
                        return@items
                    }
                    is FeedItemUi.NotFound -> {
                        NotFoundPostCard()
                        return@items
                    }
                    is FeedItemUi.Single,
                    is FeedItemUi.ReplyCluster,
                    is FeedItemUi.SelfThreadChain,
                    -> Unit
                }
                val leaf =
                    when (item) {
                        is FeedItemUi.Single -> item.post
                        is FeedItemUi.ReplyCluster -> item.leaf
                        is FeedItemUi.SelfThreadChain -> item.posts.last()
                        // The above tombstone guard cleared these
                        // variants — see the early `return@items`.
                        is FeedItemUi.Blocked, is FeedItemUi.NotFound ->
                            error("Tombstone variants returned early; leaf unreachable")
                    }
                // Hoist the videoEmbedSlot lambda so it's stable across
                // recompositions of this item — without this, every
                // recomposition allocates a fresh closure per video card.
                // Inspection mode (preview / screenshot tests) gets the
                // phase-B static-poster variant so the screen-level
                // previews stay layoutlib-safe.
                // Build the per-leaf tap lambda inside the same remember
                // block that produces videoSlot. The earlier shape derived
                // `onParentVideoTap` outside via `onVideoTap?.let { ... }`,
                // which allocates a fresh lambda on every recomposition;
                // using that lambda as a `remember` key then defeated the
                // stability goal (function values compare by reference, so
                // the key would churn each recomposition).
                val videoSlot: @Composable (EmbedUi.Video, MediaCover?) -> Unit =
                    remember(leaf.id, coordinator, onVideoTap) {
                        val onParentVideoTap: (() -> Unit)? =
                            onVideoTap?.let { tap -> { tap(leaf.id) } }
                        val slot: @Composable (EmbedUi.Video, MediaCover?) -> Unit = { video, cover ->
                            if (coordinator != null) {
                                PostCardVideoEmbed(
                                    video = video,
                                    postId = leaf.id,
                                    coordinator = coordinator,
                                    onTap = onParentVideoTap,
                                    cover = cover,
                                )
                            } else {
                                PostCardVideoEmbed(
                                    video = video,
                                    onTap = onParentVideoTap,
                                    cover = cover,
                                )
                            }
                        }
                        slot
                    }
                // Quoted-post video slot. Bind identity is the QUOTED
                // post's URI (per mostVisibleVideoTarget's
                // videoBindingFor) so the coordinator naturally
                // distinguishes parent vs quoted videos. Slot is null
                // when this item doesn't carry a quoted post (whether
                // top-level EmbedUi.Record or nested inside an
                // EmbedUi.RecordWithMedia.record) — the `quotedRecord`
                // extension covers both shapes via a single source of
                // truth in :data:models. The remember sits at the
                // same call site every recomposition (key flip drops
                // the lambda cleanly).
                val quotedVideoUri = leaf.embed.quotedRecord?.uri
                // Build the quoted-video tap lambda inside the remember
                // (same stability fix as the parent-video slot above).
                val quotedVideoSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? =
                    remember(quotedVideoUri, coordinator, onVideoTap) {
                        if (quotedVideoUri == null) {
                            null
                        } else {
                            val onQuotedVideoTap: (() -> Unit)? =
                                onVideoTap?.let { tap -> { tap(quotedVideoUri) } }
                            val slot: @Composable (QuotedEmbedUi.Video) -> Unit = { qVideo ->
                                if (coordinator != null) {
                                    PostCardVideoEmbed(
                                        quotedVideo = qVideo,
                                        postId = quotedVideoUri,
                                        coordinator = coordinator,
                                        onTap = onQuotedVideoTap,
                                    )
                                } else {
                                    PostCardVideoEmbed(
                                        quotedVideo = qVideo,
                                        onTap = onQuotedVideoTap,
                                    )
                                }
                            }
                            slot
                        }
                    }
                when (item) {
                    is FeedItemUi.Blocked, is FeedItemUi.NotFound ->
                        error("Tombstone variants returned early; render unreachable")
                    is FeedItemUi.Single ->
                        Surface(
                            color = cardColor,
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PostCard(
                                post = item.post,
                                callbacks = callbacks,
                                videoEmbedSlot = videoSlot,
                                quotedVideoEmbedSlot = quotedVideoSlot,
                                onImageClick = { idx -> onImageTap(item.post, idx) },
                                onQuotedImageClick = onQuotedImageTap,
                                animateLikeTap = item.post.id == lastLikeTapPostUri,
                                animateRepostTap = item.post.id == lastRepostTapPostUri,
                                isMediaRevealed = item.post.id in revealedMedia,
                                onRevealMedia = { revealedMedia = revealedMedia.adding(item.post.id) },
                            )
                        }
                    is FeedItemUi.ReplyCluster ->
                        ThreadCluster(
                            root = item.root,
                            parent = item.parent,
                            leaf = item.leaf,
                            callbacks = callbacks,
                            hasEllipsis = item.hasEllipsis,
                            color = cardColor,
                            shape = cardShape,
                            leafVideoEmbedSlot = videoSlot,
                            leafQuotedVideoEmbedSlot = quotedVideoSlot,
                            // Tapping "View full thread" routes to the cluster's
                            // leaf URI — same MVI dispatch a body tap on the
                            // leaf would use. Per CLAUDE.md / m28.5.1 acceptance
                            // criteria: "ThreadFold tap on a m28.3 cluster routes
                            // to PostDetail." The wiring was missed at m28.3
                            // ship time; restoring it here.
                            onFoldTap = { callbacks.onTap(item.leaf) },
                            onImageClick = onImageTap,
                            onQuotedImageClick = onQuotedImageTap,
                            lastLikeTapPostUri = lastLikeTapPostUri,
                            lastRepostTapPostUri = lastRepostTapPostUri,
                            revealedMedia = revealedMedia,
                            onRevealMedia = { id -> revealedMedia = revealedMedia.adding(id) },
                        )
                    is FeedItemUi.SelfThreadChain -> {
                        // Same-author chain: render N PostCards stacked
                        // vertically with avatar-gutter connector flags
                        // wired by index. Per design Decision 5: first
                        // → connectBelow only; middle → both; last →
                        // connectAbove only. Modifier.threadConnector
                        // (PR #77) draws the line through PostCard's
                        // existing connector machinery — no new
                        // :designsystem composable needed.
                        //
                        // Only the leaf post participates in the video
                        // coordinator (matches ReplyCluster's leaf-only
                        // video binding). Non-leaf chain posts get
                        // videoEmbedSlot = null, so any video embed in
                        // those positions collapses cleanly per
                        // PostCard's videoEmbedSlot KDoc.
                        val chainLastIndex = item.posts.lastIndex
                        Surface(
                            color = cardColor,
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                item.posts.forEachIndexed { index, chainPost ->
                                    val isLeaf = index == chainLastIndex
                                    PostCard(
                                        post = chainPost,
                                        callbacks = callbacks,
                                        connectAbove = index > 0,
                                        connectBelow = index < chainLastIndex,
                                        videoEmbedSlot = if (isLeaf) videoSlot else null,
                                        quotedVideoEmbedSlot = if (isLeaf) quotedVideoSlot else null,
                                        onImageClick = { idx -> onImageTap(chainPost, idx) },
                                        onQuotedImageClick = onQuotedImageTap,
                                        animateLikeTap = chainPost.id == lastLikeTapPostUri,
                                        animateRepostTap = chainPost.id == lastRepostTapPostUri,
                                        isMediaRevealed = chainPost.id in revealedMedia,
                                        onRevealMedia = { revealedMedia = revealedMedia.adding(chainPost.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isAppending) {
                item(key = "appending", contentType = "appending") {
                    FeedAppendingIndicator()
                }
            }
        }
    }

    // Pagination trigger — emit exactly once per crossing of the
    // (lastVisibleIndex > feedItems.size - PREFETCH_DISTANCE) threshold.
    // The threshold check lives INSIDE snapshotFlow's lambda so
    // distinctUntilChanged() debounces the *boolean*, not the index;
    // without that, every visible-index change past the threshold would
    // re-fire onLoadMore (10–30/s during scroll). `rememberUpdatedState`
    // lets the long-lived collector read the latest `feedItems` and
    // `onLoadMore` without restarting the LaunchedEffect on every page
    // append (snapshotFlow re-emits when the wrapped State changes).
    val currentFeedItems by rememberUpdatedState(feedItems)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            lastVisible > currentFeedItems.size - PREFETCH_DISTANCE
        }.distinctUntilChanged()
            .collect { pastThreshold ->
                if (pastThreshold) currentOnLoadMore()
            }
    }

    // Scroll-gated bind flow (per design.md Decision 1 + spec). The
    // outer `snapshotFlow` watches `isScrollInProgress` and only when
    // it flips to `false` (scroll has settled) do we run the
    // visibility math — no per-frame `mostVisibleVideoTarget`
    // computation during a fling. `MostVisibleVideoTargetTest`
    // verifies the visibility math; this wiring is too thin to test
    // on its own.
    if (coordinator != null) {
        // Memoize the leafId -> PostUi map across recompositions of
        // PostFeedList (refresh tick, append, like-toggle, etc.).
        // Recomputed only when `feedItems` itself changes. For ReplyCluster
        // entries, only the leaf is registered — root + parent posts in a
        // cluster receive videoEmbedSlot = null and don't participate in
        // the coordinator (design D3).
        val postsById =
            remember(feedItems) {
                buildMap {
                    feedItems.forEach { item ->
                        when (item) {
                            is FeedItemUi.Single -> put(item.post.id, item.post)
                            is FeedItemUi.ReplyCluster -> put(item.leaf.id, item.leaf)
                            is FeedItemUi.SelfThreadChain ->
                                put(item.posts.last().id, item.posts.last())
                            // Tombstone variants carry no renderable post — they're
                            // skipped from the video-coordinator registry so the
                            // most-visible-target lookup never resolves to them.
                            is FeedItemUi.Blocked, is FeedItemUi.NotFound -> Unit
                        }
                    }
                }
            }
        val currentPostsById by rememberUpdatedState(postsById)
        LaunchedEffect(listState, coordinator) {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .filter { scrolling -> !scrolling }
                .map {
                    mostVisibleVideoTarget(
                        layoutInfo = listState.layoutInfo,
                        postsById = currentPostsById,
                    )
                }.distinctUntilChanged()
                .collect { target -> coordinator.bindMostVisibleVideo(target) }
        }
    }
}
