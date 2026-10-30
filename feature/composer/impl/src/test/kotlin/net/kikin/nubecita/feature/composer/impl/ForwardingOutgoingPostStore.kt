package net.kikin.nubecita.feature.composer.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.posting.OutgoingPost
import net.kikin.nubecita.core.posting.OutgoingPostStore
import net.kikin.nubecita.core.posting.PostDraft
import net.kikin.nubecita.core.posting.PostingRepository

/**
 * Records each [PostDraft] the composer hands off and forwards it to [postingRepository]
 * straight away, so tests can keep asserting the exact `createPost` arguments
 * (langs, audience, quote, external, reply refs) the draft produces.
 * [Dispatchers.Unconfined] runs the forward eagerly, before [submit] returns.
 */
internal class ForwardingOutgoingPostStore(
    private val postingRepository: PostingRepository,
) : OutgoingPostStore {
    val drafts = mutableListOf<PostDraft>()

    override val posts: StateFlow<ImmutableList<OutgoingPost>> = MutableStateFlow(persistentListOf())

    override fun submit(draft: PostDraft): String {
        drafts += draft
        CoroutineScope(Dispatchers.Unconfined).launch {
            postingRepository.createPost(
                text = draft.text,
                attachments = draft.attachments,
                replyTo = draft.replyTo,
                langs = draft.langs,
                audience = draft.audience,
                quote = draft.quote,
                external = draft.external,
                video = draft.video,
            )
        }
        return "local-${drafts.size}"
    }

    override fun retry(localId: String) = Unit

    override fun discard(localId: String) = Unit
}
