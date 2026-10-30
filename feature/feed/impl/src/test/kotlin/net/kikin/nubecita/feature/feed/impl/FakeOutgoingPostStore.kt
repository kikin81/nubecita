package net.kikin.nubecita.feature.feed.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.core.posting.OutgoingPost
import net.kikin.nubecita.core.posting.OutgoingPostStore
import net.kikin.nubecita.core.posting.PostDraft

/** In-memory [OutgoingPostStore]: tests set [posts] directly and read back retries / discards. */
internal class FakeOutgoingPostStore : OutgoingPostStore {
    private val state = MutableStateFlow<ImmutableList<OutgoingPost>>(persistentListOf())
    override val posts: StateFlow<ImmutableList<OutgoingPost>> = state

    val retried = mutableListOf<String>()
    val discarded = mutableListOf<String>()

    fun set(vararg posts: OutgoingPost) {
        state.value = posts.toList().toImmutableList()
    }

    override fun submit(draft: PostDraft): String = error("not used by the feed")

    override fun retry(localId: String) {
        retried += localId
    }

    override fun discard(localId: String) {
        discarded += localId
    }
}
