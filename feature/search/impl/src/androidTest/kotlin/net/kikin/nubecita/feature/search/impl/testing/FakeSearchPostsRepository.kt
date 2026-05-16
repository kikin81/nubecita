package net.kikin.nubecita.feature.search.impl.testing

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.search.impl.data.SearchPostsPage
import net.kikin.nubecita.feature.search.impl.data.SearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Instrumentation-test fake for [SearchPostsRepository]. Hilt-injected via
 * [TestSearchPostsRepositoryModule]'s
 * `@TestInstallIn(replaces = [SearchPostsRepositoryModule::class])`.
 *
 * Returns a fixed two-post page for any non-blank query. The
 * `vrba.9` tap-through tests only need a single rendered hit to
 * verify the post-tap effect routes through to `LocalMainShellNavState`,
 * so we don't model the gated `CompletableDeferred` shape used by the
 * unit-test fake — synchronous success is sufficient.
 */
@Singleton
internal class FakeSearchPostsRepository
    @Inject
    constructor() : SearchPostsRepository {
        override suspend fun searchPosts(
            query: String,
            cursor: String?,
            limit: Int,
            sort: SearchPostsSort,
        ): Result<SearchPostsPage> =
            Result.success(
                SearchPostsPage(
                    items = DEFAULT_HITS.toImmutableList(),
                    nextCursor = null,
                ),
            )

        companion object {
            const val POST_ALICE_URI: String = "at://did:plc:alice/app.bsky.feed.post/post1"
            const val POST_BOB_URI: String = "at://did:plc:bob/app.bsky.feed.post/post2"
            const val POST_ALICE_TEXT: String = "Hello from alice in search"
            const val POST_BOB_TEXT: String = "Bob's hit in the search results"

            private val DEFAULT_HITS: List<FeedItemUi.Single> =
                listOf(
                    singlePost(
                        id = POST_ALICE_URI,
                        cid = "bafyreitest1",
                        authorHandle = "alice.bsky.social",
                        authorDisplayName = "Alice",
                        text = POST_ALICE_TEXT,
                    ),
                    singlePost(
                        id = POST_BOB_URI,
                        cid = "bafyreitest2",
                        authorHandle = "bob.bsky.social",
                        authorDisplayName = "Bob",
                        text = POST_BOB_TEXT,
                    ),
                )

            private fun singlePost(
                id: String,
                cid: String,
                authorHandle: String,
                authorDisplayName: String,
                text: String,
            ): FeedItemUi.Single =
                FeedItemUi.Single(
                    post =
                        PostUi(
                            id = id,
                            cid = cid,
                            author =
                                AuthorUi(
                                    did = id.substringAfter("at://").substringBefore("/"),
                                    handle = authorHandle,
                                    displayName = authorDisplayName,
                                    avatarUrl = null,
                                ),
                            createdAt = Instant.fromEpochSeconds(1_700_000_000),
                            text = text,
                            facets = persistentListOf(),
                            embed = EmbedUi.Empty,
                            stats = PostStatsUi(),
                            viewer = ViewerStateUi(),
                            repostedBy = null,
                        ),
                )
        }
    }
