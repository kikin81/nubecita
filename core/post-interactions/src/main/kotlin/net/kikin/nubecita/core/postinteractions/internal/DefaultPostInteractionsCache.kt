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
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import net.kikin.nubecita.core.postinteractions.PendingState
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
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
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : PostInteractionsCache {
        private val _state = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())
        private val likeJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

        override val state: StateFlow<PersistentMap<String, PostInteractionState>> = _state.asStateFlow()

        override fun seed(posts: List<PostUi>) {
            _state.update { current ->
                posts.fold(current) { acc, post ->
                    val existing = acc[post.id]
                    val merged =
                        when {
                            // In-flight optimistic state takes precedence over wire data.
                            existing?.pendingLikeWrite == PendingState.Pending ||
                                existing?.pendingRepostWrite == PendingState.Pending -> existing
                            // Cached likeUri non-null but wire says null: assume appview lag,
                            // preserve cache. Same for repost.
                            existing != null &&
                                existing.viewerLikeUri != null &&
                                post.viewer.likeUri == null -> existing
                            existing != null &&
                                existing.viewerRepostUri != null &&
                                post.viewer.repostUri == null -> existing
                            else ->
                                PostInteractionState(
                                    viewerLikeUri = post.viewer.likeUri,
                                    viewerRepostUri = post.viewer.repostUri,
                                    likeCount = post.stats.likeCount.toLong(),
                                    repostCount = post.stats.repostCount.toLong(),
                                )
                        }
                    acc.put(post.id, merged)
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
            _state.update { it.put(postUri, optimistic) }

            val job =
                applicationScope.async {
                    val callResult =
                        runCatching {
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
                                it.put(
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
                            _state.update { it.put(postUri, before) }
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
            TODO("Task 14: toggleRepost mirror")
        }

        override fun clear() {
            TODO("Task 13: clear()")
        }
    }
