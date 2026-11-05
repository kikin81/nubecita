package net.kikin.nubecita.feature.feed.impl.translation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kikin.nubecita.data.models.PostTranslationUi
import net.kikin.nubecita.data.models.PostUi

/**
 * Per-post translation state for one feed, held at screen scope.
 *
 * **Not on `PostUi`.** A post's declared language is intrinsic to the post;
 * whether this surface has translated it is not. Keeping it here is what lets
 * every other `PostCard` host render unchanged.
 *
 * Screen-scoped rather than per-item, so a reader's "show original" choice
 * survives the post scrolling out of view and back. It is deliberately *not*
 * persisted across process death: reverting is cheap, and the translation
 * itself is cached.
 */
@Stable
class FeedTranslationState(
    private val coordinator: FeedTranslationCoordinator,
    private val scope: CoroutineScope,
) {
    private val entries = mutableStateMapOf<String, PostTranslationUi>()
    private val showOriginal = mutableStateMapOf<String, Boolean>()
    private val inFlight = mutableSetOf<String>()
    private val lock = Mutex()

    /**
     * What the card for [post] should say, or null for "say nothing".
     *
     * Reads a snapshot-backed map, so a translation landing recomposes only
     * the card it belongs to.
     */
    fun forPost(post: PostUi): PostTranslationUi? {
        val entry = entries[post.id] ?: return null
        return if (showOriginal[post.id] == true && entry is PostTranslationUi.Shown) {
            PostTranslationUi.OriginalShown(entry.sourceLanguage)
        } else {
            entry
        }
    }

    /** Toggle between the translation and the original for [postUri]. */
    fun toggleOriginal(postUri: String) {
        showOriginal[postUri] = showOriginal[postUri] != true
    }

    /** The reader tapped the chip on [post]. */
    fun translate(post: PostUi) {
        showOriginal[post.id] = false
        request(post, userInitiated = true)
    }

    /**
     * Offer or translate every visible post, as appropriate.
     *
     * Called only when the list has settled — see [FeedTranslationEffect].
     */
    fun onSettled(posts: List<PostUi>) {
        posts.forEach { post ->
            // Only posts we have said nothing about yet.
            //
            // Re-resolving a post that already has a state is not merely
            // wasted work — it actively destroys the reader's: a post the
            // reader translated by tapping the chip is, to the automatic pass,
            // still a language that is not on the auto list, so the next
            // settle would emit `Offered` straight over the `Shown` and the
            // translation would silently revert. Found on device, where the
            // model loaded, the translation ran, and the body stayed Japanese.
            //
            // Nothing is lost by skipping: a post's declared language and the
            // reader's preferences do not change while it sits on screen, so
            // a second resolution of the same post can only reach the same
            // answer or undo a better one.
            if (entries.containsKey(post.id)) return@forEach
            request(post, userInitiated = false)
        }
    }

    private fun request(
        post: PostUi,
        userInitiated: Boolean,
    ) {
        scope.launch {
            lock.withLock {
                if (!inFlight.add(post.id)) return@launch
            }
            try {
                coordinator.resolve(post, userInitiated) { state -> entries[post.id] = state }
            } finally {
                lock.withLock { inFlight.remove(post.id) }
            }
        }
    }
}

/**
 * Wires [FeedTranslationState] to the list's settle events.
 *
 * **This is the 120hz guarantee.** Work is dispatched only when
 * `isScrollInProgress` goes false, for visible items only. Nothing is
 * translated during a fling — which is what makes the feature free: measured
 * on a 120Hz device, 2.01% janky frames with translation off versus 1.77% and
 * 1.65% with it on, p50 5ms throughout, with cold model downloads running
 * concurrently. Moving the dispatch out of this gate invalidates that
 * measurement.
 */
@Composable
internal fun rememberFeedTranslations(
    listState: LazyListState,
    visiblePosts: () -> List<PostUi>,
    coordinator: FeedTranslationCoordinator?,
    scope: CoroutineScope,
): FeedTranslationState? {
    if (coordinator == null) return null
    val state = remember(coordinator, scope) { FeedTranslationState(coordinator, scope) }
    // rememberUpdatedState, not the captured lambda: the effect is keyed on the
    // list state and survives recomposition, so a directly-captured
    // `visiblePosts` would keep reading the snapshot and leading-item count
    // from whichever composition created it. Those change on every page load.
    val currentVisiblePosts by rememberUpdatedState(visiblePosts)
    LaunchedEffect(state, listState) {
        // The signal is "settled AND something is laid out", not just
        // "not scrolling". The first emission of `isScrollInProgress` is
        // already false — it arrives before the list has measured, when
        // `visibleItemsInfo` is still empty — so a filter on scrolling alone
        // fires once against nothing and, for a reader who never scrolls,
        // never fires again. Found on device: the feed sat there with no
        // translate affordance on a post that plainly declared Japanese.
        //
        // Including the visible window in the key also covers the cases where
        // the content changes under a stationary reader: a refresh, a page
        // load, a feed switch.
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            SettleSignal(
                scrolling = listState.isScrollInProgress,
                firstIndex = visible.firstOrNull()?.index ?: -1,
                count = visible.size,
            )
        }.distinctUntilChanged()
            .filter { signal -> !signal.scrolling && signal.count > 0 }
            .collect { state.onSettled(currentVisiblePosts()) }
    }
    return state
}

/**
 * What "the list is settled" means, as one comparable value.
 *
 * A data class rather than three `snapshotFlow`s so the three move together:
 * they describe one moment, and reacting to them separately would dispatch
 * mid-layout.
 */
private data class SettleSignal(
    val scrolling: Boolean,
    val firstIndex: Int,
    val count: Int,
)
