package net.kikin.nubecita.core.postinteractions.internal

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
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
            TODO("Tasks 8, 9, 10: toggleLike happy + failure + single-flight paths")
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
