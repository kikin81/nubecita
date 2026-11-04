package net.kikin.nubecita.core.feedcache

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.data.models.FeedItemUi

/**
 * One row of a paged feed: a rendered item, or the seam where a catch-up has
 * not yet joined up with what the reader already read.
 *
 * The paged stream carries this rather than `FeedItemUi` because a seam has to
 * reach the consuming feature to be rendered, and `FeedItemUi` cannot grow a
 * seam variant: it lives in `:data:models` and `:feature:search:impl`
 * exhausts it too, so a new variant would force `when` changes across modules
 * that have no notion of a seam (D23). Widening here instead keeps that type
 * untouched and the blast radius inside the cache and its one paging consumer
 * — the widget and the watch read `head`, not this.
 *
 * The alternative, inferring the seam from a gap between positions, is
 * explicitly ruled out: a prepend reserves a window and leaves its unused end
 * empty, so gaps are normal and arithmetic cannot tell one from a seam.
 */
@Immutable
sealed interface CachedFeedRow {
    /**
     * The `LazyColumn` key for this row.
     *
     * An item's key is its own content-derived one; a seam's is derived from
     * its position, which is unique within the partition by construction (it
     * is the primary key) and stable for as long as the seam exists.
     */
    val key: String

    @Immutable
    data class Item(
        val item: FeedItemUi,
    ) : CachedFeedRow {
        override val key: String get() = item.key
    }

    /**
     * The boundary between entries a catch-up brought in and the content below
     * it that the reader has already seen.
     *
     * [exhausted] is the difference between "there is more to fetch in this
     * range" and "this is as far as the catch-up is allowed to go" — the first
     * fills on scroll, the second offers a jump to the newest instead (D5).
     * It is persisted, so an interrupted fill still shows the right affordance
     * after process death.
     */
    @Immutable
    data class Seam(
        val position: Int,
        val exhausted: Boolean,
    ) : CachedFeedRow {
        override val key: String get() = "$SEAM_KEY_PREFIX$position"
    }

    companion object {
        /** Namespaced like the grouped item keys, and for the same reason. */
        const val SEAM_KEY_PREFIX: String = "seam:"
    }
}
