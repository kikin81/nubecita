package net.kikin.nubecita.core.posts.internal

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.OutgoingPost
import net.kikin.nubecita.core.posting.OutgoingPostStatus
import net.kikin.nubecita.core.posting.OutgoingPostStore
import net.kikin.nubecita.core.posting.PostDraft
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.SharedMediaStore
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.core.profile.ActorProfileRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Clock

internal class DefaultOutgoingPostStore
    @Inject
    constructor(
        private val postingRepository: PostingRepository,
        private val postRepository: PostRepository,
        private val actorProfileRepository: ActorProfileRepository,
        private val sessionStateProvider: SessionStateProvider,
        private val sharedMediaStore: SharedMediaStore,
        @param:ApplicationScope private val scope: CoroutineScope,
        private val clock: Clock,
    ) : OutgoingPostStore {
        private val _posts = MutableStateFlow<ImmutableList<OutgoingPost>>(persistentListOf())
        override val posts: StateFlow<ImmutableList<OutgoingPost>> = _posts.asStateFlow()

        /** The signed-in user's profile, fetched once per DID for avatar and display name. */
        private var viewerAuthor: AuthorUi? = null

        override fun submit(draft: PostDraft): String {
            val localId = UUID.randomUUID().toString()
            val entry =
                OutgoingPost(
                    localId = localId,
                    draft = draft,
                    post = draft.toLocalPost(localId, author = currentAuthor()),
                    status = OutgoingPostStatus.Sending,
                )
            _posts.update { (listOf(entry) + it).toImmutableList() }
            scope.launch { resolveViewerAuthor() }
            send(localId)
            return localId
        }

        override fun retry(localId: String) {
            val entry = find(localId) ?: return
            if (entry.status !is OutgoingPostStatus.Failed) return
            replace(localId) { it.copy(status = OutgoingPostStatus.Sending) }
            send(localId)
        }

        override fun discard(localId: String) {
            val entry = find(localId) ?: return
            if (entry.status !is OutgoingPostStatus.Failed) return
            _posts.update { list -> list.filterNot { it.localId == localId }.toImmutableList() }
            deleteOwnedCopies(entry.draft)
        }

        private fun send(localId: String) {
            val draft = find(localId)?.draft ?: return
            scope.launch {
                val result =
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
                result.fold(
                    onSuccess = { uri ->
                        replace(localId) {
                            it.copy(
                                post = it.post.copy(id = uri.raw),
                                status = OutgoingPostStatus.Sent(uri.raw),
                            )
                        }
                        trimSent()
                        deleteOwnedCopies(draft)
                        fetchServerCopy(localId, uri.raw)
                    },
                    onFailure = { throwable ->
                        Timber.tag(TAG).w(throwable, "createPost() failed for an outgoing post")
                        val error = throwable as? ComposerError ?: ComposerError.RecordCreationFailed(throwable)
                        replace(localId) { it.copy(status = OutgoingPostStatus.Failed(error)) }
                    },
                )
            }
        }

        /**
         * Swaps the local projection for the AppView's copy (real CID, facets,
         * embeds). The AppView indexes a new record about a second after
         * `createRecord` returns, so the first read can miss.
         */
        private suspend fun fetchServerCopy(
            localId: String,
            uri: String,
        ) {
            for (backoff in SERVER_COPY_BACKOFF_MS) {
                delay(backoff)
                val server = postRepository.getPost(uri).getOrNull() ?: continue
                replace(localId) { it.copy(post = server) }
                return
            }
            Timber.tag(TAG).d("Server copy not indexed yet; keeping the local post")
        }

        private fun currentAuthor(): AuthorUi {
            val session = sessionStateProvider.state.value as? SessionState.SignedIn
            viewerAuthor?.takeIf { it.did == session?.did }?.let { return it }
            val handle = session?.handle.orEmpty()
            return AuthorUi(did = session?.did.orEmpty(), handle = handle, displayName = handle, avatarUrl = null)
        }

        private suspend fun resolveViewerAuthor() {
            val did = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did ?: return
            if (viewerAuthor?.did == did) return
            val profile = actorProfileRepository.fetchProfile(did).getOrNull() ?: return
            val author =
                AuthorUi(
                    did = profile.did,
                    handle = profile.handle,
                    displayName = profile.displayName ?: profile.handle,
                    avatarUrl = profile.avatarUrl,
                )
            viewerAuthor = author
            // Only local projections carry the placeholder author; a server copy already has the real one.
            _posts.update { list ->
                list
                    .map { entry ->
                        if (entry.post.cid.isEmpty() && entry.post.author.did == did) {
                            entry.copy(post = entry.post.copy(author = author))
                        } else {
                            entry
                        }
                    }.toImmutableList()
            }
        }

        private fun trimSent() {
            _posts.update { list ->
                var sent = 0
                list
                    .filter { it.status !is OutgoingPostStatus.Sent || ++sent <= OutgoingPostStore.MAX_SENT }
                    .toImmutableList()
            }
        }

        private fun deleteOwnedCopies(draft: PostDraft) {
            if (draft.ownedCopyUris.isEmpty()) return
            scope.launch { draft.ownedCopyUris.forEach { sharedMediaStore.delete(it) } }
        }

        private fun find(localId: String): OutgoingPost? = _posts.value.firstOrNull { it.localId == localId }

        private fun replace(
            localId: String,
            transform: (OutgoingPost) -> OutgoingPost,
        ) {
            _posts.update { list ->
                list.map { if (it.localId == localId) transform(it) else it }.toImmutableList()
            }
        }

        private fun PostDraft.toLocalPost(
            localId: String,
            author: AuthorUi,
        ): PostUi =
            PostUi(
                id = "$LOCAL_ID_PREFIX$localId",
                // Empty until the server copy arrives; marks the post as a local projection.
                cid = "",
                author = author,
                createdAt = clock.now(),
                text = text,
                facets = persistentListOf(),
                embed = localEmbed(),
                stats = PostStatsUi(),
                viewer = ViewerStateUi(isOwnPost = true),
                repostedBy = null,
            )

        /** Images render from their local URIs; other embeds appear once the server copy loads. */
        private fun PostDraft.localEmbed(): EmbedUi =
            if (attachments.isEmpty()) {
                EmbedUi.Empty
            } else {
                EmbedUi.Images(
                    items =
                        attachments
                            .map { ImageUi(fullsizeUrl = it.uri.toString(), thumbUrl = null, altText = it.alt.ifBlank { null }, aspectRatio = null) }
                            .toImmutableList(),
                )
            }

        private companion object {
            const val TAG = "OutgoingPostStore"
            const val LOCAL_ID_PREFIX = "local:"
            val SERVER_COPY_BACKOFF_MS = longArrayOf(500L, 1_000L, 2_000L)
        }
    }
