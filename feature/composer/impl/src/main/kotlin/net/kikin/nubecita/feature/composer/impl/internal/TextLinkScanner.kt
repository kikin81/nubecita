package net.kikin.nubecita.feature.composer.impl.internal

/**
 * Shared orchestration for the composer's "detect a link in the post text and
 * attach it once" behavior, used by both quote-link and external-link detection.
 *
 * Each scanner owns a memoized set of already-attempted links: on every text
 * change it (1) prunes that set to links still present in the text — so an
 * edited/removed link can be detected again later, and the set stays bounded —
 * (2) skips when the slot is already handled, (3) runs its [detect] function
 * excluding the attempted set, and (4) memoizes the match and fires [onDetected].
 *
 * The detectors, fetches, and resulting state transitions stay per-concern; only
 * this bookkeeping is shared (the detectors return different payloads via the
 * generic [Match.value]).
 *
 * @param T the per-concern payload a detection yields (e.g. an `at://` URI for a
 *   quote, the matched URL for an external link).
 * @property detect finds the first eligible match in the text, excluding the
 *   already-attempted set; `null` when there's none.
 * @property alreadyHandled `true` when this scanner should not attach right now
 *   (e.g. the quote slot is filled, or images are present for an external card).
 *   Checked AFTER pruning so the attempted set is still maintained while handled.
 * @property onDetected invoked with the matched text and payload when a fresh link
 *   is detected. Receives [Match.matchedText] so callers that need it (e.g. to
 *   strip a quote link) don't depend on the payload carrying it.
 */
internal class TextLinkScanner<T>(
    private val detect: (text: String, exclude: Set<String>) -> Match<T>?,
    private val alreadyHandled: () -> Boolean,
    private val onDetected: (matchedText: String, value: T) -> Unit,
) {
    private val attempted = mutableSetOf<String>()

    fun scan(text: String) {
        // Forget links no longer in the text (bounded set; re-detect after edit),
        // keeping links still present remembered so a rejected one doesn't re-pop.
        attempted.retainAll { text.contains(it) }
        if (alreadyHandled()) return
        val match = detect(text, attempted) ?: return
        attempted.add(match.matchedText)
        onDetected(match.matchedText, match.value)
    }

    /**
     * Drop [matchedText] from the attempted set so it can be re-detected. Used for
     * a non-memoizing clear (e.g. an external card auto-cleared because images were
     * added should reappear once images are removed), as opposed to a manual
     * dismiss which keeps the link memoized.
     */
    fun forget(matchedText: String) {
        attempted.remove(matchedText)
    }

    /** A detected link: the exact matched text plus a per-concern payload. */
    data class Match<T>(
        val matchedText: String,
        val value: T,
    )
}
