package net.kikin.nubecita.feature.settings.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.feeds.FeedViewPrefs
import net.kikin.nubecita.core.feeds.replyVisibility
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Backs the Feed preferences screen. Mirrors [ContentFiltersViewModel]: the UI is
 * a pure projection of [FeedViewPreferencesRepository.prefs], and writes flow
 * straight back through the repository, which updates its cache optimistically
 * so the screen reflects a change without a refetch. A failed write rolls the
 * cache back and surfaces [FeedPreferencesEffect.ShowSaveError].
 *
 * `refresh()` runs once on open; a failure is a silent no-op, leaving the cached
 * value — which defaults to filtering ON, the same direction the Following feed
 * already applies. The VM never touches navigation state.
 */
@HiltViewModel
internal class FeedPreferencesViewModel
    @Inject
    constructor(
        private val repository: FeedViewPreferencesRepository,
    ) : MviViewModel<FeedPreferencesState, FeedPreferencesEvent, FeedPreferencesEffect>(
            repository.prefs.value.toFeedPreferencesState(),
        ) {
        init {
            repository.prefs
                .onEach { prefs -> setState { prefs.toFeedPreferencesState() } }
                .launchIn(viewModelScope)
            viewModelScope.launch {
                // Pull the latest; a failure leaves the cached/default value.
                // Let cancellation propagate — don't swallow it.
                try {
                    repository.refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Silent no-op — the cached/default prefs remain.
                }
            }
        }

        // Per-field in-flight write, keyed so a newer change to the SAME field
        // supersedes its predecessor while OTHER fields write independently.
        private val writeJobs = mutableMapOf<String, Job>()

        override fun handleEvent(event: FeedPreferencesEvent) {
            when (event) {
                is FeedPreferencesEvent.ReplyVisibilitySelected ->
                    persist(REPLIES_KEY) { repository.setReplyVisibility(event.visibility) }
                is FeedPreferencesEvent.HideRepostsToggled ->
                    persist(REPOSTS_KEY) { repository.setHideReposts(event.hide) }
                is FeedPreferencesEvent.HideQuotePostsToggled ->
                    persist(QUOTES_KEY) { repository.setHideQuotePosts(event.hide) }
            }
        }

        /**
         * Per-field single-flight write. A newer change to [key] cancels the
         * in-flight write for that field, so rapid flips collapse to one network
         * round-trip instead of queuing N; different fields write concurrently.
         * The optimistic cache already updated, so cancelling mid-write hits the
         * repository's `CancellationException` path (no revert — the newer
         * optimistic value stands) and the next same-field write reconciles the
         * server. Only a real failure surfaces the snackbar.
         */
        private fun persist(
            key: String,
            write: suspend () -> Unit,
        ) {
            writeJobs[key]?.cancel()
            writeJobs[key] =
                viewModelScope.launch {
                    try {
                        write()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        sendEffect(FeedPreferencesEffect.ShowSaveError)
                    }
                }
        }

        private companion object {
            const val REPLIES_KEY = "replies"
            const val REPOSTS_KEY = "reposts"
            const val QUOTES_KEY = "quotes"
        }
    }

/**
 * Projects the resolved [FeedViewPrefs] into [FeedPreferencesState], collapsing
 * the two reply booleans into one [net.kikin.nubecita.core.feeds.ReplyVisibility].
 * Pure — unit-tested via the VM.
 */
internal fun FeedViewPrefs.toFeedPreferencesState(): FeedPreferencesState =
    FeedPreferencesState(
        replyVisibility = replyVisibility,
        hideReposts = hideReposts,
        hideQuotePosts = hideQuotePosts,
    )
