package net.kikin.nubecita.feature.search.impl.testing

import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.search.impl.data.SearchActorsPage
import net.kikin.nubecita.feature.search.impl.data.SearchActorsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Instrumentation-test fake for [SearchActorsRepository]. Hilt-injected via
 * [TestSearchActorsRepositoryModule]'s
 * `@TestInstallIn(replaces = [SearchActorsRepositoryModule::class])`.
 *
 * Returns a fixed two-actor page for any non-blank query. Mirrors the
 * synchronous shape of [FakeSearchPostsRepository] — the `vrba.9`
 * tap-through test only needs a rendered row to drive the
 * `NavigateToProfile` effect.
 */
@Singleton
internal class FakeSearchActorsRepository
    @Inject
    constructor() : SearchActorsRepository {
        override suspend fun searchActors(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchActorsPage> =
            Result.success(
                SearchActorsPage(
                    items = DEFAULT_HITS.toImmutableList(),
                    nextCursor = null,
                ),
            )

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
