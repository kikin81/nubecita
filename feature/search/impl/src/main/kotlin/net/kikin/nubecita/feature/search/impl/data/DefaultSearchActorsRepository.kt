package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.actor.SearchActorsRequest
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.data.models.ActorUi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SearchActorsRepository] backed by the atproto-kotlin
 * SDK's [ActorService]. Mirrors [DefaultSearchPostsRepository]: same
 * IO dispatcher routing, same `CancellationException`-aware error
 * handling, same Timber failure-log shape, same up-front limit-range
 * guard.
 *
 * Sends `q` + `cursor` + `limit` and projects
 * [ActorService.searchActors] response via a local
 * [ProfileView.toActorUi] extension (declared in this file to keep
 * the mapper colocated with its single consumer; promote to
 * `:data:models` or `:core:posting` if a third consumer appears).
 *
 * `displayName` is normalized: blank strings collapse to `null` to
 * preserve the [ActorUi] contract that "no display name" means null,
 * not empty string.
 */
@Singleton
internal class DefaultSearchActorsRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SearchActorsRepository {
        override suspend fun searchActors(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchActorsPage> {
            // Fail fast on misuse rather than forwarding to the server and
            // surfacing an opaque 400. The atproto lexicon for
            // `app.bsky.actor.searchActors` allows 1..100; SEARCH_ACTORS_PAGE_LIMIT
            // is the default but production callers (the search VM in vrba.7)
            // may eventually want to vary it.
            require(limit in 1..100) {
                "limit must be in 1..100 (atproto lexicon range), got $limit"
            }
            return withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ActorService(client).searchActors(
                            SearchActorsRequest(
                                q = query,
                                cursor = cursor,
                                limit = limit.toLong(),
                            ),
                        )
                    Result.success(
                        SearchActorsPage(
                            items = response.actors.map { it.toActorUi() }.toImmutableList(),
                            nextCursor = response.cursor,
                        ),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).e(t, "searchActors(q=%s, cursor=%s) failed: %s", query, cursor, t.javaClass.name)
                    Result.failure(t)
                }
            }
        }

        private companion object {
            const val TAG = "SearchActorsRepo"
        }
    }

private fun ProfileView.toActorUi(): ActorUi =
    ActorUi(
        did = did.raw,
        handle = handle.raw,
        // Normalize blank → null per ActorUi's contract; the boundary
        // says null means "no display name" so consumers don't have to
        // re-check `.isBlank()` to render the handle fallback.
        displayName = displayName?.takeIf { it.isNotBlank() },
        avatarUrl = avatar?.raw,
    )
