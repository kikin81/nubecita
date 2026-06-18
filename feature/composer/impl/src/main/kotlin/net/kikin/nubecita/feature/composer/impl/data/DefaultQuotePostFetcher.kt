package net.kikin.nubecita.feature.composer.impl.data

import io.github.kikin81.atproto.app.bsky.embed.ImagesView
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetPostsRequest
import io.github.kikin81.atproto.app.bsky.feed.Post
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.com.atproto.identity.IdentityService
import io.github.kikin81.atproto.com.atproto.identity.ResolveHandleRequest
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.XrpcClient
import io.github.kikin81.atproto.runtime.decodeRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.feature.composer.impl.state.QuotePostUi
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Production [QuotePostFetcher] backed by the atproto-kotlin SDK.
 *
 * Issues a single `app.bsky.feed.getPosts` call for the one URI — the lightest
 * way to resolve a post's `cid`, author, text, and viewer state. Unlike a reply
 * (which needs the thread root), a quote embeds only the post's own strong ref,
 * so no thread traversal is needed.
 *
 * Failure mapping mirrors [DefaultParentFetchSource]:
 * - empty `posts` (deleted / not found / not visible) → [ComposerError.ParentNotFound]
 *   (the screen maps the variant to a generic "post unavailable" string).
 * - [IOException] → [ComposerError.Network].
 * - [NoSessionException] → [ComposerError.Unauthorized].
 * - anything else (decode failure, server 500) → [ComposerError.RecordCreationFailed].
 *
 * Cancellation propagates unchanged.
 */
internal class DefaultQuotePostFetcher
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : QuotePostFetcher {
        override suspend fun fetchQuote(uri: AtUri): Result<QuotePostUi> =
            withContext(dispatcher) {
                Timber.tag(TAG).d("fetchQuote() — rkey=%s", uri.raw.substringAfterLast('/'))
                try {
                    val client = xrpcClientProvider.authenticated()
                    // A pasted bsky.app link yields a HANDLE authority; getPosts looks
                    // records up by DID, so resolve the handle first or it returns empty.
                    val resolvedUri = resolveDidAuthority(client, uri)
                    val response = FeedService(client).getPosts(GetPostsRequest(uris = listOf(resolvedUri)))
                    val post = response.posts.firstOrNull()
                    if (post == null) {
                        Timber.tag(TAG).d("fetchQuote() — post unavailable (empty getPosts result)")
                        Result.failure(ComposerError.ParentNotFound)
                    } else {
                        Result.success(post.toQuotePostUi())
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).w(throwable, "fetchQuote() failed")
                    Result.failure(mapToComposerError(throwable))
                }
            }

        /**
         * `app.bsky.feed.getPosts` resolves records by DID, so a handle-authority
         * at-uri (from a pasted `bsky.app/profile/<handle>/post/…` link) finds
         * nothing. If [uri]'s authority is a handle, resolve it to a DID via
         * `com.atproto.identity.resolveHandle` and rebuild the at-uri; a DID
         * authority is already canonical and returned unchanged. A resolution
         * failure propagates to the caller's catch and surfaces as a load error.
         */
        private suspend fun resolveDidAuthority(
            client: XrpcClient,
            uri: AtUri,
        ): AtUri {
            val authority = uri.raw.removePrefix("at://").substringBefore('/')
            if (authority.isEmpty() || authority.startsWith("did:")) return uri
            val did = IdentityService(client).resolveHandle(ResolveHandleRequest(handle = Handle(authority))).did
            // Literal prefix swap — NOT String.replaceFirst, which resolves to the
            // JDK regex overload (handle dots would be wildcards; DID `$`/`\` would
            // be replacement metachars).
            val path = uri.raw.removePrefix("at://$authority")
            return AtUri("at://${did.raw}$path")
        }

        private fun PostView.toQuotePostUi(): QuotePostUi {
            // Decode the record for the text preview; fall back to empty on a
            // malformed record rather than failing the whole quote (the ref —
            // what submit needs — is valid regardless of the body decode).
            val text =
                runCatching { record.decodeRecord(Post.serializer()).text }.getOrDefault("")
            return QuotePostUi(
                ref = StrongRef(uri = uri, cid = cid),
                authorHandle = author.handle.raw,
                authorDisplayName = author.displayName?.takeIf { it.isNotBlank() },
                text = text,
                avatarUrl = author.avatar?.raw,
                thumbnailUrl =
                    (embed as? ImagesView)
                        ?.images
                        ?.firstOrNull()
                        ?.thumb
                        ?.raw,
                // Server-computed postgate result; fail open when absent.
                canViewerQuote = viewer?.embeddingDisabled != true,
            )
        }

        private fun mapToComposerError(throwable: Throwable): ComposerError =
            when (throwable) {
                is ComposerError -> throwable
                is NoSessionException -> ComposerError.Unauthorized
                is IOException -> ComposerError.Network(throwable)
                else -> ComposerError.RecordCreationFailed(throwable)
            }

        private companion object {
            private const val TAG = "QuotePostFetcher"
        }
    }
