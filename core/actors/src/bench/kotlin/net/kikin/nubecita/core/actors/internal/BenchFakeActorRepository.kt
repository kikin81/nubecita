package net.kikin.nubecita.core.actors.internal

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.ActorSearchPage
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.VerifiedBadge
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [ActorRepository] returning a fixed, deterministic set of
 * people with no network — so the search typeahead dropdown and the People
 * results tab have data on the bench build. Used to reproduce the
 * typeahead person-tap navigation bug (nubecita-m4jc) on-device.
 *
 * [searchTypeahead] and [searchActors] return the fixed list filtered by a
 * loose case-insensitive substring match on handle / display name (falling
 * back to the full list so any query surfaces people). The cache methods
 * resolve against the same list.
 */
@Singleton
internal class BenchFakeActorRepository
    @Inject
    constructor() : ActorRepository {
        override suspend fun searchTypeahead(
            query: String,
            limit: Int,
        ): Result<List<ActorUi>> = Result.success(match(query).take(limit))

        override suspend fun searchActors(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<ActorSearchPage> =
            Result.success(
                ActorSearchPage(
                    items = match(query).take(limit).toImmutableList(),
                    nextCursor = null,
                ),
            )

        override fun getActor(did: String): Flow<ActorUi?> = flowOf(PEOPLE.firstOrNull { it.did == did })

        override fun recentActors(
            selfDid: String?,
            limit: Int,
        ): Flow<List<ActorUi>> = flowOf(PEOPLE.filter { it.did != selfDid }.take(limit))

        private fun match(query: String): List<ActorUi> {
            // Locale.ROOT keeps matching deterministic and locale-independent
            // (default-locale lowercase mis-folds e.g. Turkish I/i).
            val q = query.trim().lowercase(Locale.ROOT)
            if (q.isEmpty()) return PEOPLE
            val hits =
                PEOPLE.filter {
                    it.handle.lowercase(Locale.ROOT).contains(q) ||
                        (it.displayName?.lowercase(Locale.ROOT)?.contains(q) == true)
                }
            return hits.ifEmpty { PEOPLE }
        }

        private companion object {
            val PEOPLE =
                persistentListOf(
                    ActorUi(
                        did = "did:plc:alice",
                        handle = "alice.bsky.social",
                        displayName = "Alice Chen",
                        avatarUrl = null,
                        verifiedBadge = VerifiedBadge.Verified,
                    ),
                    ActorUi(
                        did = "did:plc:bob",
                        handle = "bob.dev",
                        displayName = "Bob Iglesias",
                        avatarUrl = null,
                        verifiedBadge = VerifiedBadge.TrustedVerifier,
                    ),
                    ActorUi(did = "did:plc:carmen", handle = "carmen.design", displayName = "Carmen Ortiz", avatarUrl = null),
                    ActorUi(did = "did:plc:dev", handle = "shipit.bsky.social", displayName = "Indie Dev", avatarUrl = null),
                    ActorUi(did = "did:plc:designer", handle = "studio.bsky.social", displayName = "The Studio", avatarUrl = null),
                )
        }
    }
