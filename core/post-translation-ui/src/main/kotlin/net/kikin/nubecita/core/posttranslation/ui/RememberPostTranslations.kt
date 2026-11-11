package net.kikin.nubecita.core.posttranslation.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import net.kikin.nubecita.core.posttranslation.PostTranslations
import net.kikin.nubecita.data.models.PostUi

/**
 * Translation for one list of posts, dispatched only when the list settles.
 *
 * **This is the 120hz guarantee.** Work starts only when `isScrollInProgress`
 * goes false, for the visible posts only; nothing is translated during a
 * fling. Measured on a 120Hz device: 2.01% janky frames with translation off
 * against 1.77% and 1.65% with it on, p50 5ms throughout, with cold model
 * downloads running concurrently. Moving the dispatch out of this gate
 * invalidates that measurement.
 *
 * [visiblePosts] is read at settle time, not captured: the posts that matter
 * are the ones on screen when the scroll stops. Map
 * `listState.layoutInfo.visibleItemsInfo` to posts by item key wherever the
 * list holds rows that aren't posts.
 *
 * Returns null when [translations] is null, which is what previews and
 * screenshot fixtures pass to render with no translation.
 */
@Composable
fun rememberPostTranslations(
    listState: LazyListState,
    visiblePosts: () -> List<PostUi>,
    translations: PostTranslations?,
): PostTranslationsHandle? {
    if (translations == null) return null
    val scope = rememberCoroutineScope()
    val handle = remember(translations, scope) { StorePostTranslationsHandle(translations, scope) }
    // One fresh try per visit for posts that failed, which is what rebuilding
    // a screen-scoped map on each visit used to give for free.
    LaunchedEffect(translations) { translations.onSurfaceEntered() }
    // rememberUpdatedState, not the captured lambda: the effect below is keyed
    // on the list and survives recomposition, while the lambda's captures
    // (loaded items, leading rows) change on every page load.
    val currentVisiblePosts by rememberUpdatedState(visiblePosts)
    LaunchedEffect(translations, listState) {
        settles(
            snapshotFlow {
                val visible = listState.layoutInfo.visibleItemsInfo
                SettleSignal(
                    scrolling = listState.isScrollInProgress,
                    firstIndex = visible.firstOrNull()?.index ?: -1,
                    count = visible.size,
                    generation = translations.generation,
                )
            },
        ).collect { translations.onSettled(currentVisiblePosts()) }
    }
    return handle
}

/**
 * The moments a list has settled over something to translate.
 *
 * "Settled AND something is laid out", not just "not scrolling": the first
 * `isScrollInProgress` is already false and arrives before the list has
 * measured, so a filter on scrolling alone fires once against nothing and,
 * for a reader who never scrolls, never again. The visible window is in the
 * signal so a refresh or page load under a stationary reader re-settles, and
 * the store's generation is in it so a preference change does too.
 */
internal fun settles(signals: Flow<SettleSignal>): Flow<SettleSignal> =
    signals
        .distinctUntilChanged()
        .filter { signal -> !signal.scrolling && signal.count > 0 }

/**
 * What "the list is settled" means, as one comparable value.
 *
 * One data class rather than separate flows so the fields move together:
 * they describe one moment, and reacting to them separately would dispatch
 * mid-layout.
 */
internal data class SettleSignal(
    val scrolling: Boolean,
    val firstIndex: Int,
    val count: Int,
    val generation: Int,
)
