package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Carries the gallery page the user landed on in the full-screen media viewer
 * back to the post card that opened it, so the card's carousel is already
 * scrolled to that image when the viewer pops.
 *
 * Holds **at most one** pending hand-off. Only one viewer is open at a time and
 * it pops back to exactly one card, so a map keyed by post URI would grow for
 * the lifetime of the process while never holding more than one entry anybody
 * reads. A single slot is also trivially bounded, which matters because this
 * state is saved into the activity `Bundle` across configuration change.
 *
 * The hand-off is consume-once: [consumeActiveIndex] clears the slot so an
 * index applied on return cannot be re-applied later and clobber a swipe the
 * user has since made on the card.
 */
class GalleryStateSync internal constructor(
    pending: PendingSync?,
) {
    constructor() : this(pending = null)

    internal var pending: PendingSync? by mutableStateOf(pending)
        private set

    /** Records [index] as the page the viewer is showing for [postUri]. */
    fun setActiveIndex(
        postUri: String,
        index: Int,
    ) {
        if (postUri.isNotBlank()) {
            pending = PendingSync(postUri = postUri, index = index)
        }
    }

    /**
     * Reads the pending index for [postUri] without consuming it. Safe to call
     * during composition.
     */
    fun peekActiveIndex(postUri: String): Int? = pending?.takeIf { it.postUri == postUri }?.index

    /**
     * Returns and clears the pending index for [postUri].
     *
     * This WRITES state, so it must only be called from an effect — never from
     * a composable body, where a write to state the same composable reads is
     * the documented path to a recomposition loop.
     */
    fun consumeActiveIndex(postUri: String): Int? = peekActiveIndex(postUri)?.also { pending = null }

    /** Drops any pending hand-off. */
    fun clear() {
        pending = null
    }

    internal data class PendingSync(
        val postUri: String,
        val index: Int,
    )
}

/**
 * Persists the single pending hand-off as a `[postUri, index]` list so the
 * carousel still lands on the right image when the viewer is open across a
 * configuration change or process death.
 */
val GalleryStateSyncSaver: Saver<GalleryStateSync, List<Any>> =
    Saver(
        save = { sync -> sync.pending?.let { listOf(it.postUri, it.index) } ?: emptyList() },
        restore = { saved ->
            val postUri = saved.getOrNull(0) as? String
            val index = saved.getOrNull(1) as? Int
            GalleryStateSync(
                pending =
                    if (postUri != null && index != null) {
                        GalleryStateSync.PendingSync(postUri = postUri, index = index)
                    } else {
                        null
                    },
            )
        },
    )

@Composable
fun rememberGalleryStateSync(): GalleryStateSync = rememberSaveable(saver = GalleryStateSyncSaver) { GalleryStateSync() }

@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalGalleryStateSync: ProvidableCompositionLocal<GalleryStateSync> =
    compositionLocalOf { GalleryStateSync() }
