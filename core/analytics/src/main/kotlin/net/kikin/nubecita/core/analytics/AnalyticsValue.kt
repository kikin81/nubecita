package net.kikin.nubecita.core.analytics

/**
 * Neutral, provider-agnostic wire value for an analytics parameter.
 *
 * Every [AnalyticsEvent] param is one of these four primitives, derived from a
 * typed enum / boolean / bucketed count — never a free-form, identifying string
 * built from user content. Each provider implementation translates this neutral
 * form into its own SDK shape (Firebase → `Bundle`; a future PostHog → its map)
 * so wire naming lives exactly once in the model and no provider re-derives it.
 */
sealed interface AnalyticsValue {
    @JvmInline
    value class Str(
        val value: String,
    ) : AnalyticsValue

    @JvmInline
    value class LongVal(
        val value: Long,
    ) : AnalyticsValue

    @JvmInline
    value class DoubleVal(
        val value: Double,
    ) : AnalyticsValue

    @JvmInline
    value class BoolVal(
        val value: Boolean,
    ) : AnalyticsValue
}
