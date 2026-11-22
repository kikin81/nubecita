package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.graph.GraphService
import io.github.kikin81.atproto.app.bsky.graph.SearchStarterPacksV2Request
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toStarterPackUi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SearchStarterPacksRepository] over the SDK's generated
 * [GraphService.searchStarterPacksV2]. A pack with no usable name is dropped (see
 * [toStarterPackUi]). Cancellation is re-thrown before anything is logged; the query is never
 * logged (it is user-entered content).
 */
@Singleton
internal class DefaultSearchStarterPacksRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SearchStarterPacksRepository {
        override suspend fun searchStarterPacks(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchStarterPacksPage> {
            require(limit in 1..100) {
                "limit must be in 1..100 (atproto lexicon range), got $limit"
            }
            return withContext(dispatcher) {
                try {
                    val response =
                        GraphService(xrpcClientProvider.authenticated())
                            .searchStarterPacksV2(SearchStarterPacksV2Request(q = query, limit = limit.toLong(), cursor = cursor))
                    Result.success(
                        SearchStarterPacksPage(
                            items = response.starterPacks.mapNotNull { it.toStarterPackUi() }.toImmutableList(),
                            nextCursor = response.cursor,
                        ),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "searchStarterPacksV2 failed (cursor=%s): %s", cursor, t.javaClass.name)
                    Result.failure(t)
                }
            }
        }

        private companion object {
            const val TAG = "SearchPacksRepo"
        }
    }
