package net.kikin.nubecita.core.actors.internal

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.actor.SearchActorsRequest
import io.github.kikin81.atproto.app.bsky.actor.SearchActorsTypeaheadRequest
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.ActorSearchPage
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.database.dao.ActorDao
import net.kikin.nubecita.core.database.model.asExternalModel
import net.kikin.nubecita.core.database.model.toCacheEntity
import net.kikin.nubecita.core.profile.canViewerMessage
import net.kikin.nubecita.data.models.ActorUi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [ActorRepository] backed by the atproto-kotlin SDK's
 * [io.github.kikin81.atproto.app.bsky.actor.ActorService], with a
 * write-through cache into [net.kikin.nubecita.core.database.dao.ActorDao].
 *
 * Every successful search (typeahead or full) upserts its actors into the
 * cache with `Clock.System.now()` as `lastSeenAt`. Cache writes are
 * best-effort: a DAO exception is logged at WARN and swallowed so a Room
 * failure never becomes a [Result.failure] to callers. Empty results skip
 * the write entirely. [kotlinx.coroutines.CancellationException] is
 * re-thrown at every catch site — including inside the cache write — so
 * structured concurrency cancels cleanly. `displayName` is normalized:
 * blank collapses to null per the [net.kikin.nubecita.data.models.ActorUi] contract.
 */
@Singleton
internal class DefaultActorRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val actorDao: ActorDao,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ActorRepository {
        override suspend fun searchTypeahead(
            query: String,
            limit: Int,
        ): Result<List<ActorUi>> {
            require(limit in 1..100) { "limit must be in 1..100, got $limit" }
            return withContext(dispatcher) {
                try {
                    val actors =
                        ActorService(xrpcClientProvider.authenticated())
                            .searchActorsTypeahead(SearchActorsTypeaheadRequest(q = query, limit = limit.toLong()))
                            .actors
                            .map { it.toActorUi() }
                    writeThrough(actors)
                    Result.success(actors)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).d(t, "searchTypeahead(q=%s) failed", query)
                    Result.failure(t)
                }
            }
        }

        override suspend fun searchActors(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<ActorSearchPage> {
            require(limit in 1..100) { "limit must be in 1..100, got $limit" }
            return withContext(dispatcher) {
                try {
                    val response =
                        ActorService(xrpcClientProvider.authenticated())
                            .searchActors(
                                SearchActorsRequest(
                                    q = query,
                                    cursor = cursor,
                                    limit = limit.toLong(),
                                ),
                            )
                    val items = response.actors.map { it.toActorUi() }
                    writeThrough(items)
                    Result.success(ActorSearchPage(items.toImmutableList(), response.cursor))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    // Never log the query itself (user-entered content) — it now
                    // reaches Crashlytics breadcrumbs. Keep cursor + error identity.
                    Timber.tag(TAG).w(t, "searchActors failed (cursor=%s): %s", cursor, t.javaClass.name)
                    Result.failure(t)
                }
            }
        }

        override fun getActor(did: String): Flow<ActorUi?> = actorDao.getActor(did).map { it?.asExternalModel() }

        override fun recentActors(
            selfDid: String?,
            limit: Int,
        ): Flow<List<ActorUi>> {
            // Guard the public API: a negative LIMIT is "unbounded" in SQLite, which would
            // read the entire actors cache. Matches the searchActors/searchTypeahead range.
            require(limit in 1..100) { "limit must be in 1..100, got $limit" }
            return actorDao.recentActors(selfDid, limit).map { rows -> rows.map { it.asExternalModel() } }
        }

        /** Best-effort cache population. A cache write failure must never fail the search. */
        private suspend fun writeThrough(actors: List<ActorUi>) {
            if (actors.isEmpty()) return
            try {
                val now = Clock.System.now()
                actorDao.upsert(actors.map { it.toCacheEntity(lastSeenAt = now) })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "actor cache write-through failed (%d rows)", actors.size)
            }
        }

        private companion object {
            private const val TAG = "ActorRepo"
        }
    }

private fun ProfileView.toActorUi(): ActorUi =
    ActorUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeIf { it.isNotBlank() },
        avatarUrl = avatar?.raw,
        canMessage = canViewerMessage(associated, viewer),
    )

private fun ProfileViewBasic.toActorUi(): ActorUi =
    ActorUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeIf { it.isNotBlank() },
        avatarUrl = avatar?.raw,
        canMessage = canViewerMessage(associated, viewer),
    )
