package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.common.session.SessionClearable
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import net.kikin.nubecita.core.postinteractions.PendingState
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.ThreadMuteRepository
import net.kikin.nubecita.core.profile.PinnedPostRepository
import net.kikin.nubecita.data.models.PostUi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [PostInteractionsCache]. See the interface
 * KDoc for the full contract.
 *
 * Holds:
 * - `_state`: the canonical state map. Exposed read-only via [state].
 * - `likeJobs` / `repostJobs`: single-flight guards keyed on postUri.
 *   Cleared when a network call resolves (in `finally`).
 *
 * Coroutine scope: writes are launched on the application-scoped
 * dispatcher (`@ApplicationScope CoroutineScope`) so they survive the
 * screen that initiated them leaving the back stack. Awaits the launched
 * job before returning so the calling VM gets the Result it can route.
 */
@Singleton
internal class DefaultPostInteractionsCache
    @Inject
    constructor(
        private val likeRepostRepository: LikeRepostRepository,
        private val bookmarkRepository: BookmarkRepository,
        private val threadMuteRepository: ThreadMuteRepository,
        private val pinnedPostRepository: PinnedPostRepository,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : PostInteractionsCache,
        SessionClearable {
        private val _state = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())
        private val likeJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
        private val repostJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
        private val bookmarkJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
        private val threadMuteJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

        // One profile record holds the pin, so at most one pin write at a time.
        private val pinJob =
            java.util.concurrent.atomic
                .AtomicReference<kotlinx.coroutines.Job?>(null)

        override val state: StateFlow<PersistentMap<String, PostInteractionState>> = _state.asStateFlow()

        override fun seed(posts: List<PostUi>) {
            _state.update { current ->
                posts.fold(current) { acc, post ->
                    val existing = acc[post.id]
                    val merged =
                        when {
                            // Deletion outranks everything, including in-flight
                            // writes. The appview keeps serving a deleted post
                            // briefly, so without this the next refresh would
                            // seed it straight back into view.
                            existing?.isDeleted == true -> existing.withWireReplyCount(post).withWireOverlays(post)
                            // In-flight optimistic state takes precedence over wire data.
                            existing?.pendingLikeWrite == PendingState.Pending ||
                                existing?.pendingRepostWrite == PendingState.Pending ||
                                existing?.pendingBookmarkWrite == PendingState.Pending -> existing.withWireReplyCount(post).withWireOverlays(post)
                            // Cached likeUri non-null but wire says null: assume appview lag,
                            // preserve cache. Same for repost / bookmark.
                            existing != null &&
                                existing.viewerLikeUri != null &&
                                post.viewer.likeUri == null -> existing.withWireReplyCount(post).withWireOverlays(post)
                            existing != null &&
                                existing.viewerRepostUri != null &&
                                post.viewer.repostUri == null -> existing.withWireReplyCount(post).withWireOverlays(post)
                            existing != null &&
                                existing.isBookmarked &&
                                !post.viewer.isBookmarked -> existing.withWireReplyCount(post).withWireOverlays(post)
                            else ->
                                PostInteractionState(
                                    viewerLikeUri = post.viewer.likeUri,
                                    viewerRepostUri = post.viewer.repostUri,
                                    likeCount = post.stats.likeCount.toLong(),
                                    repostCount = post.stats.repostCount.toLong(),
                                    isBookmarked = post.viewer.isBookmarked,
                                    bookmarkCount = post.stats.bookmarkCount.toLong(),
                                    replyCount = post.stats.replyCount.toLong(),
                                    replyCountFloor = existing?.replyCountFloor?.liveAbove(post),
                                    threadMuteOverride = existing?.threadMuteOverride,
                                    pinOverride = existing?.pinOverride,
                                ).withWireOverlays(post)
                        }
                    acc.putting(post.id, merged)
                }
            }
        }

        override suspend fun toggleLike(
            postUri: String,
            postCid: String,
        ): Result<Unit> {
            // Single-flight: if a like call is already in flight for this postUri,
            // return synthetic success without firing anything.
            if (likeJobs[postUri]?.isActive == true) {
                return Result.success(Unit)
            }

            val before = _state.value[postUri] ?: PostInteractionState()
            val optimistic =
                before.copy(
                    viewerLikeUri = if (before.viewerLikeUri == null) PENDING_LIKE_SENTINEL else null,
                    likeCount = (before.likeCount + if (before.viewerLikeUri == null) 1 else -1).coerceAtLeast(0),
                    pendingLikeWrite = PendingState.Pending,
                )
            _state.update { it.putting(postUri, optimistic) }

            val job =
                applicationScope.async {
                    val callResult =
                        runCatchingCancellable {
                            if (before.viewerLikeUri == null) {
                                likeRepostRepository.like(StrongRef(uri = AtUri(postUri), cid = Cid(postCid))).getOrThrow()
                            } else {
                                likeRepostRepository.unlike(AtUri(before.viewerLikeUri)).getOrThrow()
                                null
                            }
                        }

                    callResult.fold(
                        onSuccess = { newLikeUri: AtUri? ->
                            _state.update {
                                it.putting(
                                    postUri,
                                    optimistic.copy(
                                        viewerLikeUri = newLikeUri?.raw,
                                        pendingLikeWrite = PendingState.None,
                                    ),
                                )
                            }
                            Result.success(Unit)
                        },
                        onFailure = { throwable ->
                            _state.update { it.putting(postUri, before) }
                            Result.failure(throwable)
                        },
                    )
                }
            likeJobs[postUri] = job
            return try {
                job.await()
            } finally {
                likeJobs.remove(postUri)
            }
        }

        override suspend fun toggleRepost(
            postUri: String,
            postCid: String,
        ): Result<Unit> {
            if (repostJobs[postUri]?.isActive == true) {
                return Result.success(Unit)
            }

            val before = _state.value[postUri] ?: PostInteractionState()
            val optimistic =
                before.copy(
                    viewerRepostUri = if (before.viewerRepostUri == null) PENDING_REPOST_SENTINEL else null,
                    repostCount = (before.repostCount + if (before.viewerRepostUri == null) 1 else -1).coerceAtLeast(0),
                    pendingRepostWrite = PendingState.Pending,
                )
            _state.update { it.putting(postUri, optimistic) }

            val job =
                applicationScope.async {
                    val callResult =
                        runCatchingCancellable {
                            if (before.viewerRepostUri == null) {
                                likeRepostRepository.repost(StrongRef(uri = AtUri(postUri), cid = Cid(postCid))).getOrThrow()
                            } else {
                                likeRepostRepository.unrepost(AtUri(before.viewerRepostUri)).getOrThrow()
                                null
                            }
                        }

                    callResult.fold(
                        onSuccess = { newRepostUri: AtUri? ->
                            _state.update {
                                it.putting(
                                    postUri,
                                    optimistic.copy(
                                        viewerRepostUri = newRepostUri?.raw,
                                        pendingRepostWrite = PendingState.None,
                                    ),
                                )
                            }
                            Result.success(Unit)
                        },
                        onFailure = { throwable ->
                            _state.update { it.putting(postUri, before) }
                            Result.failure(throwable)
                        },
                    )
                }
            repostJobs[postUri] = job
            return try {
                job.await()
            } finally {
                repostJobs.remove(postUri)
            }
        }

        override suspend fun toggleBookmark(
            postUri: String,
            postCid: String,
        ): Result<Unit> {
            if (bookmarkJobs[postUri]?.isActive == true) {
                return Result.success(Unit)
            }

            val before = _state.value[postUri] ?: PostInteractionState()
            // Both create and delete take the post's own StrongRef — there is no
            // separate bookmark-record URI (see BookmarkRepository).
            val ref = StrongRef(uri = AtUri(postUri), cid = Cid(postCid))
            val optimistic =
                before.copy(
                    isBookmarked = !before.isBookmarked,
                    bookmarkCount = (before.bookmarkCount + if (before.isBookmarked) -1 else 1).coerceAtLeast(0),
                    pendingBookmarkWrite = PendingState.Pending,
                )
            _state.update { it.putting(postUri, optimistic) }

            val job =
                applicationScope.async {
                    runCatchingCancellable {
                        if (before.isBookmarked) {
                            bookmarkRepository.unbookmark(ref).getOrThrow()
                        } else {
                            bookmarkRepository.bookmark(ref).getOrThrow()
                        }
                    }.fold(
                        onSuccess = {
                            _state.update {
                                it.putting(postUri, optimistic.copy(pendingBookmarkWrite = PendingState.None))
                            }
                            Result.success(Unit)
                        },
                        onFailure = { throwable ->
                            _state.update { it.putting(postUri, before) }
                            Result.failure(throwable)
                        },
                    )
                }
            bookmarkJobs[postUri] = job
            return try {
                job.await()
            } finally {
                bookmarkJobs.remove(postUri)
            }
        }

        override suspend fun togglePin(
            postUri: String,
            postCid: String,
        ): Result<Boolean?> {
            if (pinJob.get()?.isActive == true) return Result.success(null)
            val target = _state.value[postUri]?.isPinned != true

            // Flip this post, and (when pinning) clear every other pinned post,
            // atomically. A post absent from the cache is not inserted — a
            // default entry would zero its counts on merge — so it simply
            // isn't flipped; every surface seeds before it renders.
            var before: Map<String, PostInteractionState> = emptyMap()
            _state.update { map ->
                val affected = map.filter { (id, state) -> id == postUri || (target && state.isPinned) }
                before = affected
                affected.entries.fold(map) { acc, (id, state) ->
                    val pinned = id == postUri && target
                    acc.putting(id, state.copy(isPinned = pinned, pinOverride = pinned))
                }
            }

            val job =
                applicationScope.async {
                    runCatchingCancellable {
                        if (target) {
                            pinnedPostRepository.pin(uri = postUri, cid = postCid).getOrThrow()
                        } else {
                            pinnedPostRepository.unpin(uri = postUri).getOrThrow()
                        }
                    }.fold(
                        onSuccess = { Result.success<Boolean?>(target) },
                        onFailure = { throwable ->
                            _state.update { map ->
                                before.entries.fold(map) { acc, (id, previous) ->
                                    val now = acc[id] ?: previous
                                    acc.putting(id, now.copy(isPinned = previous.isPinned, pinOverride = previous.pinOverride))
                                }
                            }
                            Result.failure(throwable)
                        },
                    )
                }
            pinJob.set(job)
            return try {
                job.await()
            } finally {
                pinJob.compareAndSet(job, null)
            }
        }

        override suspend fun toggleThreadMute(postUri: String): Result<Boolean> {
            val current = _state.value[postUri] ?: PostInteractionState()
            val threadKey = current.threadRootUri ?: postUri
            if (threadMuteJobs[threadKey]?.isActive == true) {
                return Result.success(current.isThreadMuted)
            }
            val target = !current.isThreadMuted

            // Snapshot the thread's posts and flip them all in one atomic update.
            var before: Map<String, PostInteractionState> = emptyMap()
            _state.update { map ->
                val inThread = map.filter { (id, state) -> (state.threadRootUri ?: id) == threadKey }
                before = inThread
                inThread.entries.fold(map) { acc, (id, state) ->
                    acc.putting(id, state.copy(isThreadMuted = target, threadMuteOverride = target))
                }
            }

            val job =
                applicationScope.async {
                    runCatchingCancellable {
                        if (target) {
                            threadMuteRepository.mute(threadKey).getOrThrow()
                        } else {
                            threadMuteRepository.unmute(threadKey).getOrThrow()
                        }
                    }.fold(
                        onSuccess = { Result.success(target) },
                        onFailure = { throwable ->
                            // Restore only the thread-mute fields, so a like or
                            // bookmark that landed meanwhile is not rolled back too.
                            _state.update { map ->
                                before.entries.fold(map) { acc, (id, previous) ->
                                    val now = acc[id] ?: previous
                                    acc.putting(
                                        id,
                                        now.copy(
                                            isThreadMuted = previous.isThreadMuted,
                                            threadMuteOverride = previous.threadMuteOverride,
                                        ),
                                    )
                                }
                            }
                            Result.failure(throwable)
                        },
                    )
                }
            threadMuteJobs[threadKey] = job
            return try {
                job.await()
            } finally {
                threadMuteJobs.remove(threadKey)
            }
        }

        override fun markDeleted(postUri: String) {
            _state.update { current ->
                val existing = current[postUri] ?: PostInteractionState()
                current.putting(postUri, existing.copy(isDeleted = true))
            }
        }

        override fun noteReplySent(parentUri: String) {
            _state.update { current ->
                val existing = current[parentUri] ?: return@update current
                val shown = maxOf(existing.replyCount, existing.replyCountFloor ?: 0)
                current.putting(parentUri, existing.copy(replyCountFloor = shown + 1))
            }
        }

        override fun noteReplyDiscarded(parentUri: String) {
            _state.update { current ->
                val existing = current[parentUri] ?: return@update current
                val floor = existing.replyCountFloor ?: return@update current
                current.putting(parentUri, existing.copy(replyCountFloor = (floor - 1).takeIf { it > existing.replyCount }))
            }
        }

        override fun clear() {
            _state.value = persistentMapOf()
        }

        // SessionClearable — delegates to clear() so sign-out and the
        // PostInteractionsCache.clear() contract stay in sync. The
        // override is `suspend` to match the interface; the body itself
        // is a synchronous StateFlow assignment.
        override suspend fun clearSession() = clear()

        /**
         * Test-only seam: directly inserts [state] for [postUri] without going through
         * `seed(List<PostUi>)`. Type-safe alternative to reaching `_state` via reflection.
         * Do NOT call from production code.
         */
        @org.jetbrains.annotations.TestOnly
        internal fun putForTest(
            postUri: String,
            state: PostInteractionState,
        ) {
            _state.update { it.putting(postUri, state) }
        }
    }

/** Keeps the latest wire reply count (the floor's base) when the rest of [this] is preserved. */
private fun PostInteractionState.withWireReplyCount(post: PostUi): PostInteractionState = copy(replyCount = post.stats.replyCount.toLong(), replyCountFloor = replyCountFloor?.liveAbove(post))

/**
 * Refresh the server-sourced overlay fields from [post], honouring any local
 * override: an override is kept until the wire reports the same value, then
 * cleared so the server is the source of truth again.
 */
private fun PostInteractionState.withWireOverlays(post: PostUi): PostInteractionState {
    val (muted, muteOverride) = resolveOverride(wire = post.viewer.isThreadMuted, override = threadMuteOverride)
    val (pinned, pinnedOverride) = resolveOverride(wire = post.viewer.isPinned, override = pinOverride)
    return copy(
        threadRootUri = post.threadRootUri,
        isThreadMuted = muted,
        threadMuteOverride = muteOverride,
        isPinned = pinned,
        pinOverride = pinnedOverride,
    )
}

private fun resolveOverride(
    wire: Boolean,
    override: Boolean?,
): Pair<Boolean, Boolean?> =
    when (override) {
        null, wire -> wire to null
        else -> override to override
    }

/** A floor the wire count has reached no longer does anything; drop it. */
private fun Long.liveAbove(post: PostUi): Long? = takeIf { it > post.stats.replyCount }
