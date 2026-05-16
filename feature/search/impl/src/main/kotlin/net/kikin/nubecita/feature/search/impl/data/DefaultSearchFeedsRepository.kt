package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.feed.GeneratorView
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SearchFeedsRepository] backed by the atproto-kotlin SDK's
 * raw [io.github.kikin81.atproto.runtime.XrpcClient.query] entry point.
 *
 * The underlying RPC — `app.bsky.unspecced.getPopularFeedGenerators` —
 * lives in Bluesky's `unspecced` namespace, which has no DNS-published
 * lexicon DID. `npx lex install` (the kikinlex pipeline) can't resolve
 * unspecced NSIDs, so there is no generated service class for this
 * surface today. Instead, this repo:
 *
 *  1. Defines the request + response as private `@Serializable` data
 *     classes co-located in this file (the schema mirrors the upstream
 *     lexicon at lexicons/app/bsky/unspecced/getPopularFeedGenerators.json).
 *  2. Calls [XrpcClient.query] directly with those serializers + the
 *     literal NSID string.
 *  3. Reuses the generated [GeneratorView] type from the already-shipped
 *     `app.bsky.feed.defs#generatorView` lexicon for the response items
 *     — `GeneratorView` itself ships in atproto-kotlin's :models module
 *     because the public `app.bsky.feed.getFeedGenerator` lookup depends
 *     on it.
 *
 * Mirrors [DefaultSearchPostsRepository] / [DefaultSearchActorsRepository]
 * otherwise: same IO dispatcher routing, same `CancellationException`-aware
 * error handling, same Timber failure-log shape, same up-front
 * limit-range guard.
 *
 * Tracking the upstream lexicon-architecture gap:
 * `github.com/kikin81/atproto-kotlin/issues/108`. When/if that lands a
 * generated `UnspeccedService.getPopularFeedGenerators`, this repo
 * collapses to the same shape as `DefaultSearchActorsRepository` (drop
 * the hand-written DTOs, swap in the generated request/response).
 */
@Singleton
internal class DefaultSearchFeedsRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SearchFeedsRepository {
        override suspend fun searchFeeds(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchFeedsPage> {
            require(limit in 1..100) {
                "limit must be in 1..100 (atproto lexicon range), got $limit"
            }
            return withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        client.query(
                            nsid = NSID,
                            params =
                                GetPopularFeedGeneratorsRequest(
                                    query = query.ifBlank { null },
                                    cursor = cursor,
                                    limit = limit.toLong(),
                                ),
                            paramsSerializer = GetPopularFeedGeneratorsRequest.serializer(),
                            responseSerializer = GetPopularFeedGeneratorsResponse.serializer(),
                        )
                    Result.success(
                        SearchFeedsPage(
                            items = response.feeds.map { it.toFeedGeneratorUi() }.toImmutableList(),
                            nextCursor = response.cursor,
                        ),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).e(
                        t,
                        "getPopularFeedGenerators(q=%s, cursor=%s) failed: %s",
                        query,
                        cursor,
                        t.javaClass.name,
                    )
                    Result.failure(t)
                }
            }
        }

        private companion object {
            const val NSID = "app.bsky.unspecced.getPopularFeedGenerators"
            const val TAG = "SearchFeedsRepo"
        }
    }

/**
 * Hand-written mirror of the `app.bsky.unspecced.getPopularFeedGenerators`
 * params block (lexicons/app/bsky/unspecced/getPopularFeedGenerators.json,
 * upstream Bluesky). Nullable fields are dropped from the query string at
 * the [io.github.kikin81.atproto.runtime.XrpcClient.appendQueryParams]
 * encoding boundary — JsonNull means "not set" rather than "explicit
 * null", which matches the lexicon's optional-param semantics.
 */
@Serializable
private data class GetPopularFeedGeneratorsRequest(
    val query: String? = null,
    val cursor: String? = null,
    val limit: Long? = null,
)

/**
 * Hand-written response mirror. `feeds` reuses the already-generated
 * [GeneratorView] type (from `app.bsky.feed.defs#generatorView`) so we
 * don't re-derive its schema — only the response-envelope shape is
 * declared here.
 */
@Serializable
private data class GetPopularFeedGeneratorsResponse(
    val feeds: List<GeneratorView> = emptyList(),
    val cursor: String? = null,
)

private fun GeneratorView.toFeedGeneratorUi(): FeedGeneratorUi =
    FeedGeneratorUi(
        uri = uri.raw,
        displayName = displayName,
        creatorHandle = creator.handle.raw,
        // Normalize blank → null per the same rule used in
        // DefaultSearchActorsRepository — empty display name means
        // "no display name", not an empty string the UI has to fall
        // back from.
        creatorDisplayName = creator.displayName?.takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() },
        avatarUrl = avatar?.raw,
        likeCount = likeCount ?: 0L,
    )
