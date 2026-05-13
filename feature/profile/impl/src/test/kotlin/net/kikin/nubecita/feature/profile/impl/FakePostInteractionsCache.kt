package net.kikin.nubecita.feature.profile.impl

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.data.models.PostUi
import java.util.concurrent.atomic.AtomicInteger

internal class FakePostInteractionsCache : PostInteractionsCache {
    val toggleLikeCalls = AtomicInteger(0)
    val toggleRepostCalls = AtomicInteger(0)
    val seedCalls = AtomicInteger(0)
    val clearCalls = AtomicInteger(0)

    val lastToggleLikeArgs: MutableList<Pair<String, String>> = mutableListOf()
    val lastToggleRepostArgs: MutableList<Pair<String, String>> = mutableListOf()
    val lastSeedPosts: MutableList<List<PostUi>> = mutableListOf()

    var nextToggleLikeResult: Result<Unit> = Result.success(Unit)
    var nextToggleRepostResult: Result<Unit> = Result.success(Unit)

    private val _state = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())
    override val state: StateFlow<PersistentMap<String, PostInteractionState>> = _state.asStateFlow()

    fun emit(map: PersistentMap<String, PostInteractionState>) {
        _state.value = map
    }

    override fun seed(posts: List<PostUi>) {
        seedCalls.incrementAndGet()
        lastSeedPosts += posts
    }

    override suspend fun toggleLike(
        postUri: String,
        postCid: String,
    ): Result<Unit> {
        toggleLikeCalls.incrementAndGet()
        lastToggleLikeArgs += postUri to postCid
        return nextToggleLikeResult
    }

    override suspend fun toggleRepost(
        postUri: String,
        postCid: String,
    ): Result<Unit> {
        toggleRepostCalls.incrementAndGet()
        lastToggleRepostArgs += postUri to postCid
        return nextToggleRepostResult
    }

    override fun clear() {
        clearCalls.incrementAndGet()
        _state.value = persistentMapOf()
    }
}
