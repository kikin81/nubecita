package net.kikin.nubecita.feature.feed.impl.translation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.data.models.PostTranslationUi
import net.kikin.nubecita.data.models.PostUi
import timber.log.Timber

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

    /**
     * The language the reader is currently being offered as automatic, if any.
     *
     * Snapshot state rather than a field on `PostUi`: the offer belongs to the
     * screen, not to the post that happened to trigger it, and it outlives
     * that post scrolling away.
     */
    var autoOffer: String? by mutableStateOf(null)
        private set

    /**
     * Posts whose resolution threw.
     *
     * Not a retry list — the opposite. Without it a post that fails for a
     * persistent reason (an unreadable preference, say) is still absent from
     * [entries], so every settle would pick it up again and log another
     * failure. One entry per post per screen caps that, and the post simply
     * reads untranslated.
     */
    private val failed = mutableSetOf<String>()
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
        scope.launch {
            val source = request(post, userInitiated = true).await() ?: return@launch
            // Only after the translation actually landed. Offering to do this
            // automatically for every post in a language, before the reader
            // has seen what one translation looks like, asks them to commit
            // to something they cannot yet judge.
            if (coordinator.shouldOfferAuto(source)) autoOffer = source
        }
    }

    /** The reader accepted the automatic-translation offer. */
    fun acceptAutoOffer() {
        val source = autoOffer ?: return
        autoOffer = null
        scope.launch { guarded("accepting automatic translation for $source") { coordinator.acceptAuto(source) } }
    }

    /** The reader dismissed the offer. */
    fun declineAutoOffer() {
        val source = autoOffer ?: return
        autoOffer = null
        scope.launch { guarded("declining automatic translation for $source") { coordinator.declineAuto(source) } }
    }

    /**
     * Run a preference write without letting a failure take the screen down.
     *
     * These are DataStore writes on `scope`, a `rememberCoroutineScope` whose
     * job is a plain `Job` rather than a `SupervisorJob`. An escaping throw
     * cancels the scope and with it every later translation on the screen —
     * chip taps included — for the same reason the resolve path is guarded.
     * A failed write means the reader's choice did not stick, which is worth a
     * log; it is not worth disabling translation until they navigate away.
     */
    private suspend fun guarded(
        what: String,
        block: suspend () -> Unit,
    ) {
        runCatchingCancellable { block() }
            .onFailure { error -> Timber.w(error, "Translation preference write failed: %s", what) }
    }

    /**
     * Offer or translate every visible post, as appropriate.
     *
     * Called only when the list has settled — see [FeedTranslationEffect].
     */
    fun onSettled(posts: List<PostUi>) {
        // Only posts we have said nothing about yet.
        //
        // Re-resolving a post that already has a state is not merely wasted
        // work — it actively destroys the reader's: a post the reader
        // translated by tapping the chip is, to the automatic pass, still a
        // language that is not on the auto list, so the next settle would emit
        // `Offered` straight over the `Shown` and the translation would
        // silently revert. Found on device, where the model loaded, the
        // translation ran, and the body stayed Japanese.
        //
        // Nothing is lost by skipping: a post's declared language and the
        // reader's preferences do not change while it sits on screen, so a
        // second resolution of the same post can only reach the same answer or
        // undo a better one.
        val pending = posts.filterNot { post -> entries.containsKey(post.id) || post.id in failed }
        if (pending.isEmpty()) return

        scope.launch {
            // Each post still gets its own coroutine, so the concurrency the
            // 120hz measurement was taken against is unchanged; this one only
            // waits for them and reports the total.
            //
            // The count comes from what each request reports it did, not from
            // re-reading `entries` afterwards. Two settles can overlap — a page
            // load under a stationary reader — and the second one's duplicate
            // request returns at once through the `inFlight` guard, before the
            // first has emitted anything. Reading the map then counted that
            // post as untranslated and under-reported the batch, which for a
            // bucketed count is enough to cross a boundary. Reported this way
            // each translation is counted exactly once, by the settle that
            // actually performed it.
            val translated = pending.map { post -> request(post, userInitiated = false) }.awaitAll().filterNotNull()
            if (translated.isNotEmpty()) {
                coordinator.logBatch(translated.size, translated.first())
            }
        }
    }

    /**
     * Resolve [post], reporting the source language if this call translated it.
     *
     * Null covers every other outcome — already in flight, not offered, failed
     * — so a caller counting a batch counts only work this call actually did.
     */
    private fun request(
        post: PostUi,
        userInitiated: Boolean,
    ): Deferred<String?> =
        scope.async {
            lock.withLock {
                // Re-checked here, not just in `onSettled`'s filter. That
                // filter runs when the settle is dispatched, and two settles
                // can both be dispatched before either coroutine runs — so by
                // now another may have resolved this post and left `inFlight`
                // again. Without this the post is resolved a second time,
                // which both double-counts the batch and re-runs the work the
                // surrounding comment explains must not be repeated.
                //
                // A chip tap is exempt: the reader is asking for this post
                // specifically, and it already has an `Offered` entry.
                if (!userInitiated && entries.containsKey(post.id)) return@async null
                if (!inFlight.add(post.id)) return@async null
            }
            var translated: String? = null
            try {
                // Contained per post, not per screen. `scope` here is a
                // `rememberCoroutineScope`, whose job is a plain `Job` rather
                // than a `SupervisorJob`, so an escaping throw cancelled the
                // scope and killed every later translation on the screen —
                // chip taps included — with nothing shown to the reader.
                // Only the engine call is guarded inside `resolve`; the
                // preferences reads and the ML Kit model queries are not.
                runCatchingCancellable {
                    coordinator.resolve(post, userInitiated) { state ->
                        entries[post.id] = state
                        if (state is PostTranslationUi.Shown) translated = state.sourceLanguage
                    }
                }.onFailure { error ->
                    Timber.w(error, "Translation resolution failed for ${post.id}")
                    failed += post.id
                    coordinator.logUnexpectedFailure()
                    translated = null
                }
            } finally {
                lock.withLock { inFlight.remove(post.id) }
            }
            translated
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
