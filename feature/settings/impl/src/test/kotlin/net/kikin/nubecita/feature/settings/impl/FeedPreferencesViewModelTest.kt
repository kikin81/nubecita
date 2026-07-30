package net.kikin.nubecita.feature.settings.impl

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.feeds.FeedViewPrefs
import net.kikin.nubecita.core.feeds.ReplyVisibility
import net.kikin.nubecita.core.feeds.withReplyVisibility
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Tests for [FeedPreferencesViewModel] — the projection of the cached
 * `FeedViewPrefs` and the event → repository routing.
 */
internal class FeedPreferencesViewModelTest {
    // RegisterExtension (not ExtendWith) so `runTest(mainDispatcher.dispatcher)`
    // shares Main's scheduler — otherwise advanceUntilIdle() can't drive the
    // viewModelScope writes/collectors. Mirrors ContentFiltersViewModelTest.
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `initial state projects the cached prefs`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(FeedViewPrefs.DEFAULT.copy(hideReposts = true))

            val vm = FeedPreferencesViewModel(repo)

            assertEquals(ReplyVisibility.FOLLOWED_ONLY, vm.uiState.value.replyVisibility)
            assertTrue(vm.uiState.value.hideReposts)
        }

    @Test
    fun `a repository emission re-projects the state`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(FeedViewPrefs.DEFAULT)
            val vm = FeedPreferencesViewModel(repo)

            repo.emit(FeedViewPrefs.DEFAULT.withReplyVisibility(ReplyVisibility.NONE))
            advanceUntilIdle()

            assertEquals(ReplyVisibility.NONE, vm.uiState.value.replyVisibility)
        }

    @Test
    fun `selecting a reply visibility writes it through the repository`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(FeedViewPrefs.DEFAULT)
            val vm = FeedPreferencesViewModel(repo)

            vm.handleEvent(FeedPreferencesEvent.ReplyVisibilitySelected(ReplyVisibility.ALL))
            advanceUntilIdle()

            assertEquals(listOf(ReplyVisibility.ALL), repo.replyWrites)
        }

    @Test
    fun `toggling reposts writes it through the repository`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(FeedViewPrefs.DEFAULT)
            val vm = FeedPreferencesViewModel(repo)

            vm.handleEvent(FeedPreferencesEvent.HideRepostsToggled(true))
            advanceUntilIdle()

            assertEquals(listOf(true), repo.repostWrites)
        }

    @Test
    fun `toggling quote posts writes it through the repository`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(FeedViewPrefs.DEFAULT)
            val vm = FeedPreferencesViewModel(repo)

            vm.handleEvent(FeedPreferencesEvent.HideQuotePostsToggled(true))
            advanceUntilIdle()

            assertEquals(listOf(true), repo.quoteWrites)
        }

    @Test
    fun `a failed write surfaces the save-error effect`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(FeedViewPrefs.DEFAULT, failWrites = true)
            val vm = FeedPreferencesViewModel(repo)

            vm.effects.test {
                vm.handleEvent(FeedPreferencesEvent.HideRepostsToggled(true))
                assertEquals(FeedPreferencesEffect.ShowSaveError, awaitItem())
            }
        }

    /**
     * A failing `refresh()` on open must not surface an error or crash — the
     * cached value (defaulting to filtering ON) stands.
     */
    @Test
    fun `a failed refresh on open is silent`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(FeedViewPrefs.DEFAULT, failRefresh = true)

            val vm = FeedPreferencesViewModel(repo)

            assertEquals(ReplyVisibility.FOLLOWED_ONLY, vm.uiState.value.replyVisibility)
        }

    private class FakeRepo(
        initial: FeedViewPrefs,
        private val failWrites: Boolean = false,
        private val failRefresh: Boolean = false,
    ) : FeedViewPreferencesRepository {
        private val _prefs = MutableStateFlow(initial)
        override val prefs: StateFlow<FeedViewPrefs> = _prefs.asStateFlow()

        val replyWrites = mutableListOf<ReplyVisibility>()
        val repostWrites = mutableListOf<Boolean>()
        val quoteWrites = mutableListOf<Boolean>()

        fun emit(prefs: FeedViewPrefs) {
            _prefs.value = prefs
        }

        override suspend fun refresh() {
            if (failRefresh) error("refresh boom")
        }

        override fun resetToDefault() {
            _prefs.value = FeedViewPrefs.DEFAULT
        }

        override suspend fun setReplyVisibility(visibility: ReplyVisibility) {
            if (failWrites) error("write boom")
            replyWrites += visibility
        }

        override suspend fun setHideReposts(hide: Boolean) {
            if (failWrites) error("write boom")
            repostWrites += hide
        }

        override suspend fun setHideQuotePosts(hide: Boolean) {
            if (failWrites) error("write boom")
            quoteWrites += hide
        }
    }
}
