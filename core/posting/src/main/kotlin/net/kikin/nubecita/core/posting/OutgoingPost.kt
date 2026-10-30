package net.kikin.nubecita.core.posting

import android.net.Uri
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import net.kikin.nubecita.data.models.PostUi

/**
 * Everything [PostingRepository.createPost] needs to submit one post, captured
 * when the composer hands the post to [OutgoingPostStore]. Kept on the
 * [OutgoingPost] so a failed send can be retried without reopening the composer.
 *
 * @property replyToUri The parent post's AT URI when this is a reply, for
 *   surfaces that place the reply under its parent. Matches [replyTo]'s parent.
 * @property ownedCopyUris App-owned copies of shared or pasted media
 *   ([SharedMediaStore]) referenced by [attachments]. The store deletes them once
 *   the post is sent or discarded; the composer no longer owns them after handoff.
 */
data class PostDraft(
    val text: String,
    val attachments: List<ComposerAttachment>,
    val replyTo: ReplyRefs?,
    val replyToUri: String?,
    val langs: List<String>? = null,
    val audience: PostAudience = PostAudience.DEFAULT,
    val quote: StrongRef? = null,
    val external: LinkPreview? = null,
    val video: ComposerVideoEmbed? = null,
    val ownedCopyUris: List<Uri> = emptyList(),
)

/**
 * One post the signed-in user submitted from this process, from the moment the
 * composer hands it off until it's discarded or the process dies.
 *
 * @property localId Stable client-side id, assigned at submit. Survives retries
 *   and the transition to [OutgoingPostStatus.Sent], so list keys don't change.
 * @property post What to render. A local projection of [draft] while sending;
 *   once sent, its `id` is the real AT URI, and it's replaced by the server's
 *   copy as soon as the AppView returns it.
 */
data class OutgoingPost(
    val localId: String,
    val draft: PostDraft,
    val post: PostUi,
    val status: OutgoingPostStatus,
) {
    val replyToUri: String? get() = draft.replyToUri
}

sealed interface OutgoingPostStatus {
    data object Sending : OutgoingPostStatus

    /** The record exists on the PDS at [uri]. */
    data class Sent(
        val uri: String,
    ) : OutgoingPostStatus

    data class Failed(
        val error: ComposerError,
    ) : OutgoingPostStatus
}
