package net.kikin.nubecita.feature.settings.impl

import androidx.annotation.StringRes
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.LabelVisibility

/**
 * One content-label category row: its title/description (localized via the
 * carried `@StringRes` ids — the VM has no `Context`), the current per-category
 * [visibility], and whether its Show/Warn/Hide picker is [enabled].
 *
 * [enabled] is `false` for the three adult categories while the master gate is
 * off (their picker is greyed and the effective behavior is a forced hide);
 * non-sexual nudity is never gated, so its row stays enabled.
 */
data class CategoryRowUi(
    val label: ContentLabel,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val visibility: LabelVisibility,
    val enabled: Boolean,
)

/**
 * Flat, UI-ready state for the Content filters screen — a direct projection of
 * the cached `ModerationPrefs` (see `ContentFiltersViewModel`). [categories] is
 * fixed-order (Adult Content, Sexually Suggestive, Graphic Media, Non-sexual
 * Nudity).
 */
data class ContentFiltersState(
    val adultContentEnabled: Boolean,
    val categories: ImmutableList<CategoryRowUi>,
) : UiState

sealed interface ContentFiltersEvent : UiEvent {
    /** User flipped the "Enable adult content" master switch. */
    data class AdultContentToggled(
        val enabled: Boolean,
    ) : ContentFiltersEvent

    /** User picked a new visibility for one category. */
    data class VisibilitySelected(
        val label: ContentLabel,
        val visibility: LabelVisibility,
    ) : ContentFiltersEvent
}

sealed interface ContentFiltersEffect : UiEffect {
    /** Persisting a change to the account failed — surface a snackbar. */
    data object ShowSaveError : ContentFiltersEffect
}
