package net.kikin.nubecita.core.moderation

/**
 * Pure moderation decision for a piece of labeled media — `(labels × prefs) →`
 * [MediaModerationDecision]. This is the Play-critical core: it must never let
 * adult media through when the master gate is off. No I/O, no Android, no SDK
 * types — exhaustively unit-tested.
 *
 * Mirrors the AT Protocol (`@atproto/api`) resolver for the four global content
 * categories: the adult master-gate forces the three adult labels to a
 * non-overridable hide when disabled; otherwise the per-category visibility (or
 * Bluesky's default) applies; the strongest outcome across a post's labels wins;
 * the viewer's own content is never moderated.
 */
object ContentModerator {
    /**
     * @param labels the content's labels (already projected to [ModerationLabel]).
     * @param authorDid the labeled content's author DID, if known.
     * @param viewerDid the signed-in viewer's DID, if known — own content is exempt.
     */
    fun decide(
        labels: List<ModerationLabel>,
        authorDid: String?,
        viewerDid: String?,
        prefs: ModerationPrefs,
    ): MediaModerationDecision {
        // A viewer's own content is never filtered or covered.
        if (authorDid != null && authorDid == viewerDid) return MediaModerationDecision.Show

        var worst: LabelVisibility? = null
        var worstCategory: ContentLabel? = null
        var worstForced = false

        for (value in activeValues(labels)) {
            val category = ContentLabel.fromValue(value) ?: continue
            val (visibility, forced) = effectiveVisibility(category, prefs)
            if (worst == null || visibility.ordinal > worst.ordinal) {
                worst = visibility
                worstCategory = category
                worstForced = forced
            }
        }

        return when (worst) {
            null, LabelVisibility.SHOW -> MediaModerationDecision.Show
            LabelVisibility.WARN -> MediaModerationDecision.Warn(worstCategory!!)
            LabelVisibility.HIDE ->
                MediaModerationDecision.Filter(
                    category = worstCategory!!,
                    // Forced (adult gate off) → no "show anyway"; a user-chosen
                    // hide stays revealable when the post is opened directly.
                    overridable = !worstForced,
                )
        }
    }

    /**
     * Effective visibility for one category. The adult master-gate is the
     * load-bearing rule: an adult-flagged label with adult content disabled is
     * forced to a non-overridable hide regardless of the per-category setting.
     * Returns (visibility, forced).
     */
    private fun effectiveVisibility(
        category: ContentLabel,
        prefs: ModerationPrefs,
    ): Pair<LabelVisibility, Boolean> =
        if (category.isAdult && !prefs.adultContentEnabled) {
            LabelVisibility.HIDE to true
        } else {
            prefs.visibilityFor(category) to false
        }

    /**
     * The set of label values currently in effect: non-negated values minus any
     * that carry a negation (a `neg` label retracts a prior label of that value).
     * We honor labels from any source for the four known content categories
     * (self-labels and Bluesky's default labeler) — custom-labeler subscription
     * scoping is out of v1 scope.
     */
    private fun activeValues(labels: List<ModerationLabel>): Set<String> {
        val negated =
            labels
                .asSequence()
                .filter { it.isNegated }
                .map { it.value }
                .toSet()
        return labels
            .asSequence()
            .filterNot { it.isNegated }
            .map { it.value }
            .toSet() - negated
    }
}
