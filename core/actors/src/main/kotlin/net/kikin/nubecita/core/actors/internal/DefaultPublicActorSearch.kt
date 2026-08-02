package net.kikin.nubecita.core.actors.internal

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.SearchActorsTypeaheadRequest
import io.github.kikin81.atproto.oauth.DiscoveryChain
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.actors.AnonymousXrpcClient
import net.kikin.nubecita.core.actors.PublicActorSearch
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.data.models.ActorUi
import timber.log.Timber
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultPublicActorSearch
    @Inject
    constructor(
        @param:AnonymousXrpcClient private val anonymousClient: XrpcClient,
        private val httpClient: HttpClient,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PublicActorSearch {
        // An account's PDS effectively never changes, and a typeahead re-queries
        // the same handles constantly as the user edits, so caching by DID turns
        // a 300-1100ms fetch into nothing for every keystroke after the first.
        // Bounded only by how many distinct accounts one login attempt surfaces.
        private val pdsHostCache = ConcurrentHashMap<String, String>()

        private val discovery by lazy { DiscoveryChain(httpClient) }

        override suspend fun searchTypeahead(
            query: String,
            limit: Int,
        ): Result<List<ActorUi>> {
            require(limit in 1..100) { "limit must be in 1..100, got $limit" }
            return withContext(dispatcher) {
                try {
                    val actors =
                        ActorService(anonymousClient)
                            .searchActorsTypeahead(SearchActorsTypeaheadRequest(q = query, limit = limit.toLong()))
                            .actors
                            .map { it.toActorUi() }
                    Result.success(actors)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).d(t, "public searchTypeahead(q=%s) failed", query)
                    Result.failure(t)
                }
            }
        }

        override suspend fun resolvePdsHost(did: String): String? {
            pdsHostCache[did]?.let { return it.takeUnless { cached -> cached == UNRESOLVED } }
            return withContext(dispatcher) {
                try {
                    // One hop. DiscoveryChain.resolve() would also yield the PDS but
                    // runs the full handle -> DID -> PDS -> auth-server -> metadata
                    // chain, which is four extra round trips per row.
                    val host = URI(discovery.resolvePds(did)).host
                    // Failures are cached too, under a sentinel. Without it every
                    // keystroke re-attempts a 300-1100ms fetch for a DID that just
                    // failed, which is the expensive half of this call repeated for
                    // no new information. Scoped to one sign-in attempt, so a
                    // transient failure is not remembered for long.
                    pdsHostCache[did] = host ?: UNRESOLVED
                    host
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    // Decoration only: the row stays selectable and login still
                    // works, so a failure here is not worth surfacing.
                    Timber.tag(TAG).d(t, "resolvePdsHost(%s) failed", did)
                    pdsHostCache[did] = UNRESOLVED
                    null
                }
            }
        }

        private companion object {
            const val TAG = "PublicActorSearch"

            /** Cache entry meaning "asked, got nothing" — not a real host. */
            const val UNRESOLVED = ""
        }
    }
