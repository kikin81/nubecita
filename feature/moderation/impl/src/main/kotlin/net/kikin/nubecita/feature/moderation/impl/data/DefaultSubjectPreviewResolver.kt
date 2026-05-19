package net.kikin.nubecita.feature.moderation.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.GetProfileRequest
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetPostsRequest
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.feature.moderation.impl.SubjectPreview
import net.kikin.nubecita.feature.moderation.impl.data.internal.GraphemeText
import timber.log.Timber
import javax.inject.Inject

/**
 * Default [SubjectPreviewResolver] backed by the authenticated XRPC client.
 *
 * - Posts: `app.bsky.feed.getPosts` with the single AT URI; the first
 *   (and only) [io.github.kikin81.atproto.app.bsky.feed.PostView] in the
 *   response yields the author handle and the embedded `record.text`
 *   field. The text snippet is truncated to [SNIPPET_GRAPHEMES] graphemes
 *   so multi-paragraph posts don't blow up the header card.
 * - Accounts: `app.bsky.actor.getProfile` returns a
 *   [io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed]; we
 *   take the wire `handle` (lexicon-typed via `Handle.toString()`) and
 *   optional `displayName`.
 *
 * Failure handling mirrors [DefaultModerationRepository] — `runCatching`
 * preserves the underlying throwable for the VM's `Result.exceptionOrNull()`
 * and Timber logs the failure class for triage.
 */
internal class DefaultSubjectPreviewResolver
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SubjectPreviewResolver {
        override suspend fun resolvePost(uri: String): Result<SubjectPreview.Post> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getPosts(
                            GetPostsRequest(uris = listOf(AtUri(uri))),
                        )
                    val post =
                        response.posts.firstOrNull()
                            ?: error("getPosts returned no PostView for the requested URI")
                    val rawText =
                        post.record["text"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                    SubjectPreview.Post(
                        authorHandle = post.author.handle.toString(),
                        authorDisplayName = post.author.displayName,
                        snippet = GraphemeText.truncate(rawText, max = SNIPPET_GRAPHEMES),
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(
                        throwable,
                        "resolvePost failed: %s",
                        throwable.javaClass.name,
                    )
                }
            }

        override suspend fun resolveAccount(did: String): Result<SubjectPreview.Account> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ActorService(client).getProfile(
                            GetProfileRequest(actor = AtIdentifier(did)),
                        )
                    SubjectPreview.Account(
                        handle = response.handle.toString(),
                        displayName = response.displayName,
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(
                        throwable,
                        "resolveAccount failed: %s",
                        throwable.javaClass.name,
                    )
                }
            }

        private companion object {
            const val TAG = "SubjectPreviewResolver"

            /**
             * Snippet length matches social-app's report-dialog post preview
             * (280 graphemes ~ one tweet-ish width). Truncation uses the
             * grapheme-aware helper so emoji / ZWJ sequences don't split
             * across the cut.
             */
            const val SNIPPET_GRAPHEMES = 280
        }
    }
