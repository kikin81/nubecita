package net.kikin.nubecita.core.moderation

/**
 * The four global content-label categories nubecita honors in v1, mapped to
 * their AT Protocol label values. [isAdult] marks the three categories gated by
 * the "Enable adult content" master switch (non-sexual nudity is NOT gated).
 */
enum class ContentLabel(
    val value: String,
    val isAdult: Boolean,
) {
    PORN("porn", isAdult = true),
    SEXUAL("sexual", isAdult = true),
    GRAPHIC_MEDIA("graphic-media", isAdult = true),
    NUDITY("nudity", isAdult = false),
    ;

    companion object {
        fun fromValue(value: String): ContentLabel? = entries.firstOrNull { it.value == value }
    }
}

/**
 * A label's effective visibility for the viewer. The AT Protocol wire values
 * `ignore` and `show` both map to [SHOW]. Ordinal order is significance order
 * (SHOW < WARN < HIDE) so "strongest wins" is a simple `ordinal` comparison.
 */
enum class LabelVisibility {
    SHOW,
    WARN,
    HIDE,
    ;

    /** Persisted form for `contentLabelPref.visibility`. */
    val wireValue: String
        get() =
            when (this) {
                SHOW -> "show"
                WARN -> "warn"
                HIDE -> "hide"
            }

    companion object {
        fun fromWire(value: String): LabelVisibility? =
            when (value) {
                "ignore", "show" -> SHOW
                "warn" -> WARN
                "hide" -> HIDE
                else -> null
            }
    }
}

/**
 * A slim, SDK-free projection of one `com.atproto.label.defs#label` — just what
 * the moderation decision needs. Keeps [ContentModerator] purely testable; the
 * feed-mapping layer (twmt.3) converts `PostView.labels` into these.
 */
data class ModerationLabel(
    val value: String,
    /** DID of the label's source — equals the author's DID for a self-label. */
    val src: String,
    /** A negation retracts a previously-applied label of the same value. */
    val isNegated: Boolean = false,
)

/**
 * The viewer's resolved content-filter preferences. [adultContentEnabled] is the
 * master gate (default off). [visibilities] holds the per-category choice;
 * missing entries fall back to [DEFAULT].
 */
data class ModerationPrefs(
    val adultContentEnabled: Boolean,
    val visibilities: Map<ContentLabel, LabelVisibility>,
) {
    fun visibilityFor(label: ContentLabel): LabelVisibility = visibilities[label] ?: DEFAULT.visibilities.getValue(label)

    companion object {
        /** Bluesky's shipped defaults: adult off; porn=hide, sexual/graphic=warn, nudity=show. */
        val DEFAULT =
            ModerationPrefs(
                adultContentEnabled = false,
                visibilities =
                    mapOf(
                        ContentLabel.PORN to LabelVisibility.HIDE,
                        ContentLabel.SEXUAL to LabelVisibility.WARN,
                        ContentLabel.GRAPHIC_MEDIA to LabelVisibility.WARN,
                        ContentLabel.NUDITY to LabelVisibility.SHOW,
                    ),
            )
    }
}

/**
 * The per-media moderation outcome the render layer applies.
 *
 * - [Show]: render normally.
 * - [Warn]: cover the media with an overridable "show anyway".
 * - [Filter]: drop the post from feed/search/profile lists; when the post is
 *   opened directly (post-detail) cover the media instead, revealable only if
 *   [overridable] (false when forced by the adult gate being off).
 */
sealed interface MediaModerationDecision {
    data object Show : MediaModerationDecision

    data class Warn(
        val category: ContentLabel,
    ) : MediaModerationDecision

    data class Filter(
        val category: ContentLabel,
        val overridable: Boolean,
    ) : MediaModerationDecision
}
