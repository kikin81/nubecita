package net.kikin.nubecita.core.postinteractions.internal

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.data.models.PostUi

/**
 * Test double for [PostInteractionsCache] used by
 * [DefaultPostInteractionHandlerTest]. Tracks call counts and supports a
 * [CompletableDeferred] latch so tests can hold toggleLike / toggleRepost
 * suspended to exercise the per-URI double-tap guard.
 */
internal class FakePostInteractionsCacheForHandler : PostInteractionsCache {
    var toggleLikeCalls: Int = 0
    var toggleRepostCalls: Int = 0

    val lastToggleLikeArgs: MutableList<Pair<String, String>> = mutableListOf()
    val lastToggleRepostArgs: MutableList<Pair<String, String>> = mutableListOf()

    var nextToggleLikeResult: Result<Unit> = Result.success(Unit)
    var nextToggleRepostResult: Result<Unit> = Result.success(Unit)

    /**
     * When non-null, [toggleLike] awaits this deferred before returning,
     * allowing the test to hold the call suspended so a second [onLike]
     * tap arrives while the first is in flight.
     */
    var toggleLikeLatch: CompletableDeferred<Unit>? = null
    var toggleRepostLatch: CompletableDeferred<Unit>? = null

    private val _state = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())
    override val state: StateFlow<PersistentMap<String, PostInteractionState>> = _state.asStateFlow()

    override fun seed(posts: List<PostUi>) = Unit

    override suspend fun toggleLike(
        postUri: String,
        postCid: String,
    ): Result<Unit> {
        toggleLikeLatch?.await()
        toggleLikeCalls++
        lastToggleLikeArgs += postUri to postCid
        return nextToggleLikeResult
    }

    override suspend fun toggleRepost(
        postUri: String,
        postCid: String,
    ): Result<Unit> {
        toggleRepostLatch?.await()
        toggleRepostCalls++
        lastToggleRepostArgs += postUri to postCid
        return nextToggleRepostResult
    }

    var toggleBookmarkCalls: Int = 0
    val lastToggleBookmarkArgs: MutableList<Pair<String, String>> = mutableListOf()
    var nextToggleBookmarkResult: Result<Unit> = Result.success(Unit)
    var toggleBookmarkLatch: CompletableDeferred<Unit>? = null

    override suspend fun toggleBookmark(
        postUri: String,
        postCid: String,
    ): Result<Unit> {
        toggleBookmarkLatch?.await()
        toggleBookmarkCalls++
        lastToggleBookmarkArgs += postUri to postCid
        return nextToggleBookmarkResult
    }

    override fun clear() {
        _state.value = persistentMapOf()
    }
}
