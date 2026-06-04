package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.com.atproto.label.Label
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.ContentModerator
import net.kikin.nubecita.core.moderation.MediaModerationDecision
import net.kikin.nubecita.core.moderation.ModerationLabel
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.ContentWarningCategory
import net.kikin.nubecita.data.models.MediaContentWarning
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.withMediaContentWarning

/*
 * The moderation bridge between the atproto wire layer, the pure
 * `:core:moderation` resolver, and the UI PostUi model. Repositories call
 * applyModeration over a mapped page using the cached ModerationPrefs; the
 * decision is precomputed here (off the render path) and baked onto the post's
 * media, so scrolling does zero moderation work — the 120 Hz contract.
 */

/** Projects one atproto [Label] into the moderation domain's [ModerationLabel]. */
public fun Label.toModerationLabel(): ModerationLabel = ModerationLabel(value = `val`, src = src.raw, isNegated = neg == true)

/**
 * Projects a post's wire labels into moderation labels. `PostView.labels` is
 * nullable per the lexicon (no labels → empty); the appview merges author
 * self-labels into it, so this is the complete set the resolver needs.
 */
public fun List<Label>?.toModerationLabels(): List<ModerationLabel> = this?.map(Label::toModerationLabel) ?: emptyList()

/** Maps a moderation [ContentLabel] to its UI-facing [ContentWarningCategory]. */
public fun ContentLabel.toContentWarningCategory(): ContentWarningCategory =
    when (this) {
        ContentLabel.PORN -> ContentWarningCategory.ADULT_CONTENT
        ContentLabel.SEXUAL -> ContentWarningCategory.SEXUALLY_SUGGESTIVE
        ContentLabel.GRAPHIC_MEDIA -> ContentWarningCategory.GRAPHIC_MEDIA
        ContentLabel.NUDITY -> ContentWarningCategory.NON_SEXUAL_NUDITY
    }

/**
 * Projects a [MediaModerationDecision] into the UI cover for a media embed:
 * `Show` → `null` (render normally); `Warn` → an always-overridable cover;
 * `Filter` → a cover carrying the decision's `overridable` flag (`false` for
 * the forced adult-gate-off hide).
 */
public fun MediaModerationDecision.toMediaContentWarning(): MediaContentWarning? =
    when (this) {
        MediaModerationDecision.Show -> null
        is MediaModerationDecision.Warn -> MediaContentWarning(category.toContentWarningCategory(), overridable = true)
        is MediaModerationDecision.Filter ->
            MediaContentWarning(category.toContentWarningCategory(), overridable = overridable)
    }

/**
 * Applies the precomputed moderation decision to a mapped [PostUi].
 *
 * @param labels the post's wire labels (`PostView.labels`); the appview already
 *   merges author self-labels in, so this is the complete set.
 * @param viewerDid the signed-in viewer's DID — the viewer's own content is
 *   never filtered or covered (enforced inside [ContentModerator]).
 * @param prefs the resolved content-filter preferences (from the cached
 *   `ModerationPreferencesRepository` stream).
 * @param dropFiltered `true` for feed / search / profile lists (a hard
 *   `Filter` removes the post by returning `null`); `false` for post-detail,
 *   where the same post stays and its media is covered instead.
 *
 * @return `null` when the post is filtered out of a list; otherwise the post
 *   with its media embed covered per the decision (`Show` returns it unchanged).
 */
public fun PostUi.applyModeration(
    labels: List<Label>?,
    viewerDid: String?,
    prefs: ModerationPrefs,
    dropFiltered: Boolean,
): PostUi? {
    val decision = ContentModerator.decide(labels.toModerationLabels(), author.did, viewerDid, prefs)
    if (dropFiltered && decision is MediaModerationDecision.Filter) return null
    val warning = decision.toMediaContentWarning() ?: return this // Show — no cover, no copy
    return copy(embed = embed.withMediaContentWarning(warning))
}
