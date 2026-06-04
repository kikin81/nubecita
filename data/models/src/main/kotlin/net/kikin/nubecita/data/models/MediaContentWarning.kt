package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * The four v1 content-filter categories, in UI-facing form. The render layer
 * resolves each to a display string ("Adult Content", "Graphic Media", …).
 *
 * Deliberately distinct from `:core:moderation`'s `ContentLabel` (which carries
 * the atproto wire value + `isAdult` gate): `:data:models` stays dependency-free
 * of the moderation domain, so `:core:feed-mapping` translates `ContentLabel`
 * into this enum when it bakes a decision onto a post's media.
 */
public enum class ContentWarningCategory {
    ADULT_CONTENT,
    SEXUALLY_SUGGESTIVE,
    GRAPHIC_MEDIA,
    NON_SEXUAL_NUDITY,
}

/**
 * A precomputed moderation cover for a media embed. Computed off the render
 * path (in the mapping/repository layer) so scrolling does zero moderation work
 * — `null` on a media embed's `contentWarning` means "show normally".
 *
 * [overridable] is `false` only for the forced adult-gate-off hide (no "show
 * anyway" affordance); warn covers — and any cover the user can reveal — are
 * `true`. The feed/search/profile lists drop hard-filtered posts entirely
 * (they never reach the render layer); a non-overridable cover therefore only
 * appears when the same post is opened directly in post-detail.
 */
@Immutable
public data class MediaContentWarning(
    val category: ContentWarningCategory,
    val overridable: Boolean,
)
