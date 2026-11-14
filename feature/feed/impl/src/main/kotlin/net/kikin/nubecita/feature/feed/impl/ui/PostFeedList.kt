package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.core.feedcache.CachedFeedRow
import net.kikin.nubecita.core.feedcache.FeedAnchor
import net.kikin.nubecita.core.posttranslation.ui.PostTranslationsHandle
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.OutgoingPostUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.quotedRecord
import net.kikin.nubecita.designsystem.component.BlockedPostCard
import net.kikin.nubecita.designsystem.component.MediaCover
import net.kikin.nubecita.designsystem.component.NotFoundPostCard
import net.kikin.nubecita.designsystem.component.NubecitaPullToRefreshBox
import net.kikin.nubecita.designsystem.component.OutgoingPostCard
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.designsystem.component.ThreadCluster
import net.kikin.nubecita.feature.feed.impl.FeedTestTags
import net.kikin.nubecita.feature.feed.impl.video.FeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feed.impl.video.mostVisibleVideoTarget

/**
 * Reusable post-list composable for the home feed and (soon) the
 * co-located custom-feed view.
 *
 * Renders a [NubecitaPullToRefreshBox] wrapping a [LazyColumn] of
 * [FeedItemUi] entries — singles, reply clusters, same-author thread
 * chains, and tombstone variants (blocked / not-found). Owns the
 * per-list NSFW reveal state ([rememberSaveable]) and the scroll-gated
 * video-bind [LaunchedEffect] when a [coordinator] is present.
 *
 * Appending is Paging's job now: reading an index near the end of
 * [items] is what asks for the next page, so the hand-rolled
 * `snapshotFlow` threshold trigger this used to own is gone. Keeping it
 * would have been a second, racing source of load requests.
 *
 * Call sites pass hoisted per-card Surface tokens ([cardColor] /
 * [cardShape]) so the [LazyColumn] items lambda never re-subscribes to
 * [androidx.compose.material3.MaterialTheme] per visible item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostFeedList(
    items: LazyPagingItems<CachedFeedRow>,
    isAppending: Boolean,
    isRefreshing: Boolean,
    listState: LazyListState,
    callbacks: PostCallbacks,
    onRefresh: () -> Unit,
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
    // Whether inline video may start on its own, already resolved against the
    // connection by AutoplayPolicy. Defaulted true so previews, screenshot
    // fixtures and the no-coordinator call paths keep describing the ordinary
    // autoplaying feed without restating it.
    videoAutoplayEnabled: Boolean = true,
    // Whether this feed page is currently active and settled in the pager.
    // Inactive pages during horizontal swiping unbind from the video coordinator
    // to preserve the single-player invariant and avoid video/audio collisions.
    isPageActive: Boolean = true,
    // Optional leading list item (e.g. the Discover "Trending Videos" carousel).
    // A single header item above the posts — it never touches the tuned post-item
    // rendering below; null on non-Discover feeds.
    header: (@Composable () -> Unit)? = null,
    // Persisting the reader's place, for the surfaces that do it. Null on a
    // feed with no position to keep — a generator refreshes on open, so an
    // anchor there would be written and never read.
    anchoring: FeedAnchoring? = null,
    // The seam's state and actions. Defaulted so the surfaces that cannot
    // produce a seam — a generator feed never catches up (D14) — say nothing
    // about it.
    isFillingSeam: Boolean = false,
    onFillSeam: (position: Int) -> Unit = {},
    onJumpToNewest: () -> Unit = {},
    // Posts the user just submitted (Following only), above the timeline.
    outgoingPosts: ImmutableList<OutgoingPostUi> = persistentListOf(),
    onRetryOutgoing: (localId: String) -> Unit = {},
    onDiscardOutgoing: (localId: String) -> Unit = {},
    /**
     * Per-post translation state, or null on a surface that does not
     * translate (and in previews / screenshot fixtures, which pass nothing).
     */
    translations: PostTranslationsHandle? = null,
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
    // Bring a just-submitted post into view: the composer closes back onto a
    // feed that may be scrolled down. Scroll once per outgoing post the list
    // hasn't shown before; saveable, so the feed leaving composition while the
    // composer is on top (Compact) doesn't make an old post look new.
    var shownOutgoing by rememberSaveable { mutableStateOf(listOf<String>()) }
    val newestOutgoing = outgoingPosts.firstOrNull()?.localId
    LaunchedEffect(newestOutgoing) {
        if (newestOutgoing != null && newestOutgoing !in shownOutgoing) {
            shownOutgoing = outgoingPosts.map { it.localId }
            listState.animateScrollToItem(0)
        }
    }
    // How many rows sit above the paged items. The restore scrolls by index,
    // and this is the one place that knows what this list puts in front of
    // them — which is why the anchoring lives here rather than in the screen.
    val leadingItems = (if (header != null) 1 else 0) + outgoingPosts.size
    if (anchoring != null) {
        RememberReadingPosition(listState = listState, items = items, leadingItems = leadingItems, anchoring = anchoring)
    }
    // A header can arrive after the rows: the Discover carousel loads on its
    // own request, and usually loses that race to the posts. It is inserted
    // above the first visible row, and LazyListState follows that row's key
    // into the new layout — so a list resting at the top ends up one row down
    // with the header parked above the viewport, there but unseen until the
    // reader scrolls up (nubecita-3duc).
    //
    // So a list that was resting at the top stays at the top. Only that one: a
    // reader who has scrolled is looking at something, and a header arriving
    // above them is not a reason to take it away.
    //
    // A SideEffect rather than a LaunchedEffect because of when each runs. This
    // one runs as the composition that adds the header is applied, before that
    // composition is measured — the last moment `listState` still describes the
    // list without the header. A LaunchedEffect body runs after the measure,
    // by which time the list has already followed the key and "was it at the
    // top?" can no longer be told apart from "is it one row down?".
    val hasHeader = header != null
    val headerArrival = remember { HeaderArrival(hadHeader = hasHeader) }
    SideEffect {
        val arrived = hasHeader && !headerArrival.hadHeader
        headerArrival.hadHeader = hasHeader
        if (arrived && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            // Overrides the key-following for the next measure only.
            listState.requestScrollToItem(0)
        }
    }

    // Videos on posts shown as CONTEXT — a reply cluster's root and parent,
    // and every chain post but the last. Only an item's leaf joins the
    // autoplay coordinator (one player per item), so these render the
    // static poster with a play badge and open their own post's video on
    // tap. Before this they got no slot at all, and PostCard renders
    // nothing without one: the video simply vanished (nubecita-2e8h).
    // Built once per list, not per item; the post comes in as an argument.
    val contextVideoSlot: @Composable (PostUi, EmbedUi.Video, MediaCover?) -> Unit =
        remember(onVideoTap, coordinator) {
            { post, video, cover ->
                PostCardVideoEmbed(
                    video = video,
                    onTap = onVideoTap?.let { tap -> { tap(post.id) } },
                    cover = cover,
                    // Same split as the leaf's autoplay-off branch: no badge
                    // in the no-coordinator preview path.
                    showPlayAffordance = coordinator != null,
                )
            }
        }
    val contextQuotedVideoSlot: @Composable (PostUi, QuotedEmbedUi.Video) -> Unit =
        remember(onVideoTap, coordinator) {
            { post, quotedVideo ->
                val quotedUri = post.embed.quotedRecord?.uri
                PostCardVideoEmbed(
                    quotedVideo = quotedVideo,
                    onTap = if (quotedUri != null) onVideoTap?.let { tap -> { tap(quotedUri) } } else null,
                    showPlayAffordance = coordinator != null,
                )
            }
        }

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
        ) {
            if (header != null) {
                item(key = "feed-header", contentType = "feed-header") {
                    header()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            items(
                items = outgoingPosts,
                key = { "outgoing:${it.localId}" },
                contentType = { "outgoing" },
            ) { item ->
                Column {
                    OutgoingPostCard(
                        item = item,
                        onRetry = { onRetryOutgoing(item.localId) },
                        onDiscard = { onDiscardOutgoing(item.localId) },
                        callbacks = callbacks,
                        onImageClick = { index -> onImageTap(item.post, index) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            items(
                count = items.itemCount,
                key = items.itemKey { it.key },
                contentType =
                    items.itemContentType { row ->
                        when (row) {
                            is CachedFeedRow.Seam -> "seam"
                            is CachedFeedRow.Item ->
                                when (row.item) {
                                    is FeedItemUi.Single -> "single"
                                    is FeedItemUi.ReplyCluster -> "cluster"
                                    is FeedItemUi.SelfThreadChain -> "chain"
                                    is FeedItemUi.Blocked -> "blocked"
                                    is FeedItemUi.NotFound -> "notfound"
                                }
                        }
                    },
            ) { index ->
                // Null only with placeholders enabled, which this Pager does
                // not use. Skipping rather than rendering a blank keeps the
                // smart-cast below narrow.
                val row = items[index] ?: return@items
                // The seam is a row of the projection like any other, so it
                // arrives here rather than being drawn between items: its
                // place in the list is the place the cache recorded it.
                val item =
                    when (row) {
                        is CachedFeedRow.Seam -> {
                            FeedSeamRow(
                                exhausted = row.exhausted,
                                isFilling = isFillingSeam,
                                onFill = { onFillSeam(row.position) },
                                onJumpToNewest = onJumpToNewest,
                            )
                            return@items
                        }
                        is CachedFeedRow.Item -> row.item
                    }
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
                    remember(leaf.id, coordinator, onVideoTap, videoAutoplayEnabled) {
                        val onParentVideoTap: (() -> Unit)? =
                            onVideoTap?.let { tap -> { tap(leaf.id) } }
                        val slot: @Composable (EmbedUi.Video, MediaCover?) -> Unit = { video, cover ->
                            if (coordinator != null && videoAutoplayEnabled) {
                                PostCardVideoEmbed(
                                    video = video,
                                    postId = leaf.id,
                                    coordinator = coordinator,
                                    onTap = onParentVideoTap,
                                    cover = cover,
                                )
                            } else {
                                // Autoplay off (or no coordinator at all, in
                                // previews). The badge is drawn only in the
                                // former case: with no coordinator this is a
                                // preview/screenshot render of the ordinary
                                // autoplaying feed, and a badge there would
                                // describe a state the user never sees.
                                PostCardVideoEmbed(
                                    video = video,
                                    onTap = onParentVideoTap,
                                    cover = cover,
                                    showPlayAffordance = coordinator != null,
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
                    remember(quotedVideoUri, coordinator, onVideoTap, videoAutoplayEnabled) {
                        if (quotedVideoUri == null) {
                            null
                        } else {
                            val onQuotedVideoTap: (() -> Unit)? =
                                onVideoTap?.let { tap -> { tap(quotedVideoUri) } }
                            val slot: @Composable (QuotedEmbedUi.Video) -> Unit = { qVideo ->
                                if (coordinator != null && videoAutoplayEnabled) {
                                    PostCardVideoEmbed(
                                        quotedVideo = qVideo,
                                        postId = quotedVideoUri,
                                        coordinator = coordinator,
                                        onTap = onQuotedVideoTap,
                                    )
                                } else {
                                    // Same split as the parent-video slot: the
                                    // badge marks autoplay-off, not the
                                    // no-coordinator preview path.
                                    PostCardVideoEmbed(
                                        quotedVideo = qVideo,
                                        onTap = onQuotedVideoTap,
                                        showPlayAffordance = coordinator != null,
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
                            Column {
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
                                    translation = translations?.stateOf(item.post),
                                    onTranslate = { translations?.translate(item.post) },
                                    onToggleTranslation = { translations?.toggleOriginal(item.post.id) },
                                )
                                SuppressedRepliesRow(
                                    count = item.suppressedReplyCount,
                                    onClick = { callbacks.onTap(item.post) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
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
                            contextVideoEmbedSlot = contextVideoSlot,
                            contextQuotedVideoEmbedSlot = contextQuotedVideoSlot,
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
                            footer = {
                                SuppressedRepliesRow(
                                    count = item.suppressedReplyCount,
                                    onClick = { callbacks.onTap(item.leaf) },
                                )
                            },
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
                        // video binding). Non-leaf chain posts get the
                        // context slot instead: a poster that opens their
                        // own video, never autoplaying.
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
                                        videoEmbedSlot =
                                            if (isLeaf) {
                                                videoSlot
                                            } else {
                                                { video, cover -> contextVideoSlot(chainPost, video, cover) }
                                            },
                                        quotedVideoEmbedSlot =
                                            if (isLeaf) {
                                                quotedVideoSlot
                                            } else {
                                                { quoted -> contextQuotedVideoSlot(chainPost, quoted) }
                                            },
                                        onImageClick = { idx -> onImageTap(chainPost, idx) },
                                        onQuotedImageClick = onQuotedImageTap,
                                        animateLikeTap = chainPost.id == lastLikeTapPostUri,
                                        animateRepostTap = chainPost.id == lastRepostTapPostUri,
                                        isMediaRevealed = chainPost.id in revealedMedia,
                                        onRevealMedia = { revealedMedia = revealedMedia.adding(chainPost.id) },
                                    )
                                }
                                SuppressedRepliesRow(
                                    count = item.suppressedReplyCount,
                                    onClick = { callbacks.onTap(item.posts.last()) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
        // Recomputed only when the loaded snapshot itself changes. For ReplyCluster
        // entries, only the leaf is registered — root + parent posts in a
        // cluster receive videoEmbedSlot = null and don't participate in
        // the coordinator (design D3).
        // The loaded window, not a state copy of it: `itemSnapshotList` is a
        // new instance per generation, so `remember` recomputes exactly when
        // the pages change.
        val snapshot = items.itemSnapshotList
        val postsById =
            remember(snapshot) {
                buildMap {
                    snapshot.items.forEach { row ->
                        // A seam carries no post, so it is not a video target.
                        val item = (row as? CachedFeedRow.Item)?.item ?: return@forEach
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
        // Keyed on the autoplay flag so flipping the setting restarts the
        // effect. Off: unbind immediately and never bind again, so a video
        // already playing stops the moment the user turns autoplay off rather
        // than at the next scroll settle. Back on: the snapshotFlow re-emits
        // the current scroll state at once, so a resting feed rebinds without
        // waiting for the user to scroll.
        LaunchedEffect(listState, coordinator, videoAutoplayEnabled, isPageActive) {
            if (!videoAutoplayEnabled || !isPageActive) {
                coordinator.bindMostVisibleVideo(null)
                return@LaunchedEffect
            }
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

/**
 * Whether the list had a header the last time it composed, so the composition
 * that first adds one can be told from every later one. Plain mutable state on
 * purpose: it is written from a `SideEffect` and never read during composition,
 * so there is nothing for a snapshot state to notify.
 */
private class HeaderArrival(
    var hadHeader: Boolean,
)

/**
 * What a surface needs to keep a reader's place: where to restore to, a way to
 * report a new one, and a way to say the restore has been applied.
 *
 * Grouped rather than passed as three parameters so the whole feature is one
 * nullable argument — a surface that does not persist a position passes `null`
 * and gets no collectors at all.
 */
@Immutable
internal data class FeedAnchoring(
    val restoreTo: FeedAnchor?,
    val onAnchorChanged: (itemKey: String, offset: Int) -> Unit,
    val onRestoreApplied: () -> Unit,
)

/**
 * Records where the reader stopped, and puts them back there once.
 *
 * **Recording** reads the first visible row's LazyColumn key rather than its
 * index: the key is the item's own identity, so no arithmetic over header and
 * outgoing rows is needed and nothing drifts when their count changes. It is
 * debounced, so a fling writes once when it settles instead of ~100 times on
 * the way — this is a database write on the scroll path, and the reader's
 * place only means anything once they stop. Rows that are not paged items
 * (the header, an outgoing post, the append spinner) are skipped: they are not
 * a place in the feed.
 *
 * **Restoring** waits for the anchored row to actually be in the loaded
 * window. Seeding the `Pager` makes that the first page fetched, but "fetched"
 * and "present in the snapshot" are different moments, so the effect re-runs
 * as pages arrive and does nothing until the row is there.
 *
 * Both collectors live in the composition and die with it, which is why the
 * ViewModel never sees `LazyListState` (D9).
 */
@Composable
private fun RememberReadingPosition(
    listState: LazyListState,
    items: LazyPagingItems<CachedFeedRow>,
    leadingItems: Int,
    anchoring: FeedAnchoring,
) {
    val onAnchorChanged by rememberUpdatedState(anchoring.onAnchorChanged)
    LaunchedEffect(listState) {
        snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            (first?.key as? String) to listState.firstVisibleItemScrollOffset
        }.debounce(ANCHOR_SETTLE_MS)
            .distinctUntilChanged()
            .collect { (key, offset) ->
                if (key == null || key in NON_ITEM_KEYS || key.startsWith(OUTGOING_KEY_PREFIX)) return@collect
                // Nor is the seam: it is removed once its range is filled, so
                // anchoring on it would leave the reader pointing at a row
                // that is about to stop existing.
                if (key.startsWith(CachedFeedRow.SEAM_KEY_PREFIX)) return@collect
                onAnchorChanged(key, offset)
            }
    }

    val restoreTo = anchoring.restoreTo
    val snapshot = items.itemSnapshotList
    val onRestoreApplied by rememberUpdatedState(anchoring.onRestoreApplied)
    LaunchedEffect(restoreTo, snapshot) {
        if (restoreTo == null) return@LaunchedEffect
        val index = snapshot.items.indexOfFirst { it.key == restoreTo.itemKey }
        // Not loaded yet. The next snapshot re-runs this.
        if (index < 0) return@LaunchedEffect
        listState.scrollToItem(index + leadingItems, restoreTo.offset)
        onRestoreApplied()
    }
}

/** Rows that are not a place in the feed, so never an anchor. */
private val NON_ITEM_KEYS = setOf("feed-header", "appending")
private const val OUTGOING_KEY_PREFIX = "outgoing:"

/**
 * How long the list has to be still before the place is written.
 *
 * Long enough that a fling writes once rather than per settle-frame, short
 * enough that a reader who stops and immediately backgrounds the app still
 * has their place saved.
 */
private const val ANCHOR_SETTLE_MS = 400L
