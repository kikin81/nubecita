package net.kikin.nubecita.wear.feed

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.wearcontract.WearFeedKey
import net.kikin.nubecita.wear.data.WatchFeedRepository
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class WatchFeedViewModelTest {
    private val did = "did:plc:alice"
    private val cats = "uri-cats"
    private val repo = FakeRepository()

    private fun vm() = WatchFeedViewModel(repo)

    @Test
    fun without_an_account_it_is_loading() =
        runTest {
            val vm = vm()
            advanceUntilIdle()

            assertEquals(WatchFeedStatus.Loading, vm.uiState.value.status)
        }

    @Test
    fun signed_in_with_nothing_synced_is_no_feeds() =
        runTest {
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()

            assertEquals(WatchFeedStatus.NoFeeds, vm.uiState.value.status)
        }

    @Test
    fun following_comes_first_and_is_selected_by_default() =
        runTest {
            repo.feeds.value = listOf(feed(cats, "Cats"), feed(WearFeedKey.FOLLOWING, null))
            repo.posts[WearFeedKey.FOLLOWING] = listOf(post("f1", liked = true))
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(WatchFeedStatus.Ready, state.status)
            assertEquals(listOf(WearFeedKey.FOLLOWING, cats), state.feeds.map { it.key })
            assertEquals(WearFeedKey.FOLLOWING, state.selectedKey)
            assertTrue(state.posts.single().liked)
        }

    @Test
    fun selecting_a_feed_shows_its_posts() =
        runTest {
            repo.feeds.value = listOf(feed(WearFeedKey.FOLLOWING, null), feed(cats, "Cats"))
            repo.posts[cats] = listOf(post("c1"))
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()

            vm.handleEvent(WatchFeedEvent.FeedSelected(cats))
            advanceUntilIdle()

            assertEquals(cats, vm.uiState.value.selectedKey)
            assertEquals(
                listOf("c1"),
                vm.uiState.value.posts
                    .map { it.uri },
            )
        }

    @Test
    fun a_selected_feed_that_disappears_falls_back_to_the_first() =
        runTest {
            repo.feeds.value = listOf(feed(WearFeedKey.FOLLOWING, null), feed(cats, "Cats"))
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            vm.handleEvent(WatchFeedEvent.FeedSelected(cats))
            advanceUntilIdle()

            repo.feeds.value = listOf(feed(WearFeedKey.FOLLOWING, null))
            advanceUntilIdle()

            assertEquals(WearFeedKey.FOLLOWING, vm.uiState.value.selectedKey)
        }

    @Test
    fun a_post_without_a_like_uri_is_not_liked() =
        runTest {
            repo.feeds.value = listOf(feed(WearFeedKey.FOLLOWING, null))
            repo.posts[WearFeedKey.FOLLOWING] = listOf(post("f1", liked = false))
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()

            assertFalse(
                vm.uiState.value.posts
                    .single()
                    .liked,
            )
        }

    private fun feed(
        key: String,
        name: String?,
    ) = WatchFeedEntity(did, key, name, publishedAtMs = 100)

    private fun post(
        uri: String,
        liked: Boolean = false,
    ) = WatchPostEntity(
        accountDid = did,
        feedKey = "",
        position = 0,
        uri = uri,
        cid = "c",
        authorDid = "did:plc:x",
        authorHandle = "x.bsky.social",
        authorDisplayName = null,
        authorAvatarPath = null,
        text = "t",
        createdAtMs = 0,
        replyCount = 0,
        repostCount = 0,
        likeCount = 1,
        repostedBy = null,
        likeUri = if (liked) "at://like" else null,
        bookmarked = false,
        imageCount = 0,
        isVideo = false,
        hasContentWarning = false,
        thumbnailPath = null,
    )

    @Test
    fun switching_accounts_never_queries_the_new_account_with_the_old_feed() =
        runTest {
            repo.feeds.value = listOf(feed(WearFeedKey.FOLLOWING, null), feed(cats, "Cats"))
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            vm.handleEvent(WatchFeedEvent.FeedSelected(cats))
            advanceUntilIdle()

            vm.handleEvent(WatchFeedEvent.AccountChanged("did:plc:bob"))
            advanceUntilIdle()

            assertFalse(repo.postQueries.contains("did:plc:bob" to cats)) { "${repo.postQueries}" }
            assertEquals(WearFeedKey.FOLLOWING, vm.uiState.value.selectedKey)
        }

    private class FakeRepository : WatchFeedRepository {
        val feeds = MutableStateFlow(emptyList<WatchFeedEntity>())
        val posts = mutableMapOf<String, List<WatchPostEntity>>()
        val postQueries = mutableListOf<Pair<String, String>>()

        override fun feeds(accountDid: String): Flow<List<WatchFeedEntity>> = feeds

        override fun posts(
            accountDid: String,
            feedKey: String,
        ): Flow<List<WatchPostEntity>> {
            postQueries += accountDid to feedKey
            return feeds.map { posts[feedKey].orEmpty() }
        }
    }
}
