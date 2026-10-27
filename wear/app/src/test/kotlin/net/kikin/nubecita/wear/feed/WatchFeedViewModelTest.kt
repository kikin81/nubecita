package net.kikin.nubecita.wear.feed

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearFeedKey
import net.kikin.nubecita.core.wearcontract.WearPostAction
import net.kikin.nubecita.wear.data.PhoneActionSupport
import net.kikin.nubecita.wear.data.PhoneAppStatus
import net.kikin.nubecita.wear.data.PhoneConnection
import net.kikin.nubecita.wear.data.WatchActionRepository
import net.kikin.nubecita.wear.data.WatchFeedRepository
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchPendingActionEntity
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
    private val actions = FakeActions()
    private val support = FakeSupport()
    private val phone = FakePhone()

    private fun vm() = WatchFeedViewModel(repo, actions, support, phone)

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
        reposted: Boolean = false,
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
        repostUri = if (reposted) "at://repost" else null,
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

    @Test
    fun a_pending_like_shows_at_once_with_the_count_up() =
        runTest {
            repo.feeds.value = listOf(feed(WearFeedKey.FOLLOWING, null))
            repo.posts[WearFeedKey.FOLLOWING] = listOf(post("f1"))
            actions.pending.value = listOf(WatchPendingActionEntity("a1", did, "f1", "like", createdAtMs = 1))
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()

            val p =
                vm.uiState.value.posts
                    .single()
            assertTrue(p.liked)
            assertTrue(p.likePending)
            assertEquals(2, p.likeCount)
        }

    @Test
    fun toggles_submit_the_matching_action_for_the_signed_in_account() =
        runTest {
            repo.feeds.value = listOf(feed(WearFeedKey.FOLLOWING, null))
            repo.posts[WearFeedKey.FOLLOWING] = listOf(post("f1", liked = true, reposted = true), post("f2"))
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()

            vm.handleEvent(WatchFeedEvent.ToggleLike("f1"))
            vm.handleEvent(WatchFeedEvent.ToggleRepost("f1"))
            vm.handleEvent(WatchFeedEvent.ToggleLike("f2"))
            vm.handleEvent(WatchFeedEvent.ToggleRepost("f2"))
            vm.handleEvent(WatchFeedEvent.ToggleBookmark("f2"))
            advanceUntilIdle()

            assertEquals(
                listOf(
                    did to WearAction.Unlike("f1", "at://like"),
                    did to WearAction.Unrepost("f1", "at://repost"),
                    did to WearAction.Like("f2", "c"),
                    did to WearAction.Repost("f2", "c"),
                    did to WearAction.Bookmark("f2", "c"),
                ),
                actions.submitted,
            )
        }

    @Test
    fun a_toggle_with_an_unanswered_action_ignores_taps() =
        runTest {
            repo.feeds.value = listOf(feed(WearFeedKey.FOLLOWING, null))
            repo.posts[WearFeedKey.FOLLOWING] = listOf(post("f1"))
            actions.pending.value = listOf(WatchPendingActionEntity("a1", did, "f1", "like", createdAtMs = 1))
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()

            vm.handleEvent(WatchFeedEvent.ToggleLike("f1"))
            advanceUntilIdle()

            assertTrue(actions.submitted.isEmpty())
        }

    @Test
    fun actions_are_offered_only_when_the_phone_supports_them() =
        runTest {
            repo.feeds.value = listOf(feed(WearFeedKey.FOLLOWING, null))
            val vm = vm()
            vm.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.actionsAvailable)

            support.available = false
            val older = vm()
            older.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()
            assertFalse(older.uiState.value.actionsAvailable)

            support.fail = true
            val unknown = vm()
            unknown.handleEvent(WatchFeedEvent.AccountChanged(did))
            advanceUntilIdle()
            assertFalse(unknown.uiState.value.actionsAvailable)
        }

    private class FakeActions : WatchActionRepository {
        val pending = MutableStateFlow(emptyList<WatchPendingActionEntity>())
        val submitted = mutableListOf<Pair<String, WearPostAction>>()

        override fun pending(accountDid: String): Flow<List<WatchPendingActionEntity>> = pending

        override suspend fun submit(
            accountDid: String,
            action: WearPostAction,
        ) {
            submitted += accountDid to action
        }
    }

    @Test
    fun open_on_phone_opens_the_posts_bsky_link() =
        runTest {
            val vm = vm()

            vm.handleEvent(WatchFeedEvent.OpenOnPhone("at://did:plc:bob/app.bsky.feed.post/3k"))
            vm.handleEvent(WatchFeedEvent.OpenOnPhone("not a post"))

            assertEquals(listOf("https://bsky.app/profile/did:plc:bob/post/3k"), phone.opened)
        }

    private class FakePhone : PhoneConnection {
        val opened = mutableListOf<String>()

        override suspend fun phoneAppStatus() = PhoneAppStatus.Installed

        override suspend fun requestSync() = Unit

        override fun openNubecitaOnPhone() = Unit

        override fun openUrlOnPhone(url: String) {
            opened += url
        }
    }

    private class FakeSupport : PhoneActionSupport {
        var available = true
        var fail = false

        override suspend fun available(): Boolean {
            if (fail) error("Play services unavailable")
            return available
        }

        override suspend fun dmAvailable() = available
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
