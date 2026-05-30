package net.kikin.nubecita.feature.search.impl.testing

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.ActorSearchPage
import net.kikin.nubecita.data.models.ActorUi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Instrumentation-test fake for [ActorRepository]. Hilt-injected via
 * [TestSearchActorsRepositoryModule]'s
 * `@TestInstallIn(replaces = [ActorsModule::class])`.
 *
 * Returns a fixed two-actor page for any non-blank query. Mirrors the
 * synchronous shape of [FakeSearchPostsRepository] — the `vrba.9`
 * tap-through test only needs a rendered row to drive the
 * `NavigateToProfile` effect.
 */
@Singleton
internal class FakeSearchActorsRepository
    @Inject
    constructor() : ActorRepository {
        override suspend fun searchTypeahead(
            query: String,
            limit: Int,
        ): Result<List<ActorUi>> = error("searchTypeahead is not used in search tests")

        override suspend fun searchActors(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<ActorSearchPage> =
            Result.success(
                ActorSearchPage(
                    items = DEFAULT_HITS.toImmutableList(),
                    nextCursor = null,
                ),
            )

        override fun getActor(did: String): Flow<ActorUi?> = flowOf(null)

        companion object {
            const val ACTOR_ALICE_HANDLE: String = "alice.bsky.social"
            const val ACTOR_BOB_HANDLE: String = "bob.bsky.social"
            const val ACTOR_ALICE_NAME: String = "Alice Chen"
            const val ACTOR_BOB_NAME: String = "Bob Park"

            private val DEFAULT_HITS: List<ActorUi> =
                listOf(
                    ActorUi(
                        did = "did:plc:alice",
                        handle = ACTOR_ALICE_HANDLE,
                        displayName = ACTOR_ALICE_NAME,
                        avatarUrl = null,
                    ),
                    ActorUi(
                        did = "did:plc:bob",
                        handle = ACTOR_BOB_HANDLE,
                        displayName = ACTOR_BOB_NAME,
                        avatarUrl = null,
                    ),
                )
        }
    }
