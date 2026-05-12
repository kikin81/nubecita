package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import timber.log.Timber
import javax.inject.Inject

/**
 * Default [ProfileRepository] backed by the authenticated `XrpcClient`
 * provided by `:core:auth`. Mirrors the structure of
 * `:feature:feed:impl/data/DefaultFeedRepository` — same `runCatching`
 * + Timber error-identity logging pattern.
 *
 * No caching here; the upstream [net.kikin.nubecita.feature.profile.impl.ProfileViewModel]
 * holds the per-tab state and decides when to fetch. Repository
 * stays stateless so future multi-account swaps don't have to
 * invalidate any in-repo cache.
 */
internal class DefaultProfileRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ProfileRepository {
        override suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response = ActorService(client).getProfile(buildGetProfileRequest(actor))
                    response.toProfileHeaderWithViewer()
                }.onFailure { throwable ->
                    // `actor` is a raw DID or handle (PII). Log only the
                    // error identity — matches the redaction discipline
                    // applied to DIDs in `:core:auth/DefaultXrpcClientProvider`.
                    Timber.tag(TAG).e(throwable, "fetchHeader failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun fetchTab(
            actor: String,
            tab: ProfileTab,
            cursor: String?,
            limit: Int,
        ): Result<ProfileTabPage> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getAuthorFeed(
                            buildAuthorFeedRequest(actor, tab, cursor, limit),
                        )
                    ProfileTabPage(
                        items = response.feed.toTabItems(tab),
                        nextCursor = response.cursor,
                    )
                }.onFailure { throwable ->
                    // `actor` is a raw DID or handle (PII); `cursor` is
                    // opaque appview state, also withheld. `tab` is a
                    // closed enum — safe to include for triage.
                    Timber.tag(TAG).e(
                        throwable,
                        "fetchTab(tab=%s) failed: %s",
                        tab,
                        throwable.javaClass.name,
                    )
                }
            }

        private companion object {
            const val TAG = "ProfileRepository"
        }
    }
