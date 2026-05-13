package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
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

        override val state: StateFlow<PersistentMap<String, PostInteractionState>> = _state.asStateFlow()

        override fun seed(posts: List<PostUi>) {
            TODO("Task 11: seed merger rules")
        }

        override suspend fun toggleLike(
            postUri: String,
            postCid: String,
        ): Result<Unit> {
            val before = _state.value[postUri] ?: PostInteractionState()
            val optimistic =
                before.copy(
                    viewerLikeUri = if (before.viewerLikeUri == null) PENDING_LIKE_SENTINEL else null,
                    likeCount = (before.likeCount + if (before.viewerLikeUri == null) 1 else -1).coerceAtLeast(0),
                    pendingLikeWrite = PendingState.Pending,
                )
            _state.update { it.put(postUri, optimistic) }

            val callResult =
                runCatching {
                    if (before.viewerLikeUri == null) {
                        likeRepostRepository.like(StrongRef(uri = AtUri(postUri), cid = Cid(postCid))).getOrThrow()
                    } else {
                        likeRepostRepository.unlike(AtUri(before.viewerLikeUri)).getOrThrow()
                        null
                    }
                }

            return callResult.fold(
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
