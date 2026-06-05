package net.kikin.nubecita.core.posts.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.posts.PostNotFoundException
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.data.models.PostUi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor fake of [PostRepository] that returns deterministic,
 * network-free posts so the fullscreen video player and the media-viewer
 * gallery render offline.
 *
 * It deliberately mirrors the appview's `app.bsky.feed.getPosts` behaviour: that
 * XRPC resolves only **DID-based** at-uris, so a **handle-based** at-uri — the
 * shape a deep link produces (`at://<handle>/app.bsky.feed.post/<rkey>`, see
 * `PostDeepLinkKey.toPostDetailRoute`) — surfaces as [PostNotFoundException].
 * This is what reproduces the deep-link media-gallery "Post not found" bug
 * offline: the post-detail thread still loads (the thread fake accepts handle
 * URIs, like the appview's `getPostThread`), but tapping media re-reads the post
 * via `getPost` and 404s unless the caller passes the canonical DID-based id.
 *
 * Selected over the production module by AGP source-set merging — see the
 * sibling bench `PostRepositoryModule` for the variant-split rationale.
 */
@Singleton
internal class BenchFakePostRepository
    @Inject
    constructor(
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PostRepository {
        override suspend fun getPost(uri: String): Result<PostUi> =
            withContext(ioDispatcher) {
                if (!uri.isDidAtUri()) {
                    // Handle-based URI (deep-link form) — the appview's getPosts
                    // can't resolve it. Surfaces as MediaViewerError.NotFound.
                    return@withContext Result.failure(PostNotFoundException(uri))
                }
                val post = if (uri == benchGalleryPost.id) benchGalleryPost else benchVideoPost
                Result.success(post)
            }
    }
