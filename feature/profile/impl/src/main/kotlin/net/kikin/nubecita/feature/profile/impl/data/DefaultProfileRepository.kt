package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.graph.Follow
import io.github.kikin81.atproto.com.atproto.repo.CreateRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.DeleteRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Nsid
import io.github.kikin81.atproto.runtime.RecordKey
import io.github.kikin81.atproto.runtime.encodeRecord
import io.github.kikin81.atproto.runtime.parseOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

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
 *
 * The follow/unfollow write path mirrors
 * `:core:post-interactions/internal/DefaultLikeRepostRepository` —
 * same `RepoService.createRecord` + `deleteRecord` shape, same
 * redaction discipline (the AT-URI carries the viewer's DID and is
 * never logged).
 */
internal class DefaultProfileRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
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

        override suspend fun follow(subjectDid: String): Result<String> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val record =
                        encodeRecord(
                            record = Follow(createdAt = nowDatetime(), subject = Did(subjectDid)),
                            type = FOLLOW_NSID,
                        )
                    val response =
                        RepoService(client).createRecord(
                            CreateRecordRequest(
                                collection = Nsid(FOLLOW_NSID),
                                repo = AtIdentifier(viewerDid),
                                record = record,
                            ),
                        )
                    response.uri.raw
                }.onFailure { throwable ->
                    // `subjectDid` is PII (the followed account's DID); withhold
                    // it from the log, same policy as DefaultLikeRepostRepository.
                    Timber.tag(TAG).e(throwable, "follow failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun unfollow(followUri: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val (repo, rkey) = parseFollowUri(AtUri(followUri))
                    val client = xrpcClientProvider.authenticated()
                    RepoService(client).deleteRecord(
                        DeleteRecordRequest(
                            collection = Nsid(FOLLOW_NSID),
                            repo = repo,
                            rkey = rkey,
                        ),
                    )
                    Unit
                }.onFailure { throwable ->
                    // `followUri` carries the viewer's DID — keep it out of the
                    // log surface for the same reason DefaultLikeRepostRepository
                    // redacts its delete URIs.
                    Timber.tag(TAG).e(throwable, "unfollow failed: %s", throwable.javaClass.name)
                }
            }

        private fun currentViewerDid(): String {
            val signedIn =
                sessionStateProvider.state.value as? SessionState.SignedIn
                    ?: throw NoSessionException()
            return signedIn.did
        }

        private fun nowDatetime(): Datetime = Datetime(Clock.System.now().toString())

        // We use `parseOrNull` + a local require() rather than upstream
        // `parse()` because parse()'s IllegalArgumentException message
        // includes the raw URI, which carries the viewer's DID. Same
        // redaction policy as DefaultLikeRepostRepository.parseAtUri.
        private fun parseFollowUri(uri: AtUri): Pair<AtIdentifier, RecordKey> {
            val parts =
                requireNotNull(uri.parseOrNull()) {
                    "follow AT URI is not structurally valid"
                }
            val rkey =
                requireNotNull(parts.rkey) {
                    "follow AT URI must be at://<repo>/app.bsky.graph.follow/<rkey>"
                }
            return parts.repo to rkey
        }

        private companion object {
            const val TAG = "ProfileRepository"
            const val FOLLOW_NSID = "app.bsky.graph.follow"
        }
    }
