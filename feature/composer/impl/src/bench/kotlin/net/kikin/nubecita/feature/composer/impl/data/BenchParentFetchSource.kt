package net.kikin.nubecita.feature.composer.impl.data

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.feature.composer.impl.state.ParentPostUi
import javax.inject.Inject

/**
 * Bench-flavor [ParentFetchSource]: resolves the reply target through the bench
 * [PostRepository] fake, so the reply composer shows the post that was actually
 * tapped (feed or post-detail fixture) instead of failing on the network.
 *
 * Bench fixtures don't carry the record's `reply.root`, so every target is
 * treated as a thread root ([ParentPostUi.rootRef] == [ParentPostUi.parentRef]).
 * Bench never submits to a real PDS, so the ref shape only has to be plausible.
 */
internal class BenchParentFetchSource
    @Inject
    constructor(
        private val postRepository: PostRepository,
    ) : ParentFetchSource {
        override suspend fun fetchParent(uri: AtUri): Result<ParentPostUi> =
            postRepository
                .getPost(uri.raw)
                .fold(
                    onSuccess = { Result.success(it.toParentPostUi()) },
                    onFailure = { Result.failure(ComposerError.ParentNotFound) },
                )

        private fun PostUi.toParentPostUi(): ParentPostUi {
            val ref = StrongRef(uri = AtUri(id), cid = Cid(cid))
            return ParentPostUi(
                parentRef = ref,
                rootRef = ref,
                authorHandle = author.handle,
                authorDisplayName = author.displayName.takeIf { it.isNotBlank() },
                text = text,
                avatarUrl = author.avatarUrl,
                thumbnailUrl = (embed as? EmbedUi.Images)?.items?.firstOrNull()?.thumbUrl,
            )
        }
    }
