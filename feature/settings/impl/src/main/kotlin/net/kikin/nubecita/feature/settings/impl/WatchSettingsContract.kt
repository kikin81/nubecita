package net.kikin.nubecita.feature.settings.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.preferences.WatchFeedsPreference
import net.kikin.nubecita.data.models.FeedKind

/** One pinned feed the user can sync to the watch. */
internal data class WatchFeedOption(
    val uri: String,
    val kind: FeedKind,
    val displayName: String,
    val avatarUrl: String?,
)

/**
 * Flat state for Settings → Watch.
 *
 * [selectedUris] holds only feeds that are still in [feeds]: a stored URI for a
 * feed the user has since unpinned is dropped from the projection, so it can't
 * silently use one of the three slots.
 */
internal data class WatchSettingsState(
    val feeds: ImmutableList<WatchFeedOption> = persistentListOf(),
    val selectedUris: ImmutableSet<String> = persistentSetOf(WatchFeedsPreference.FOLLOWING_FEED_URI),
) : UiState {
    /** Room for another feed. Unchecked rows are disabled once this is false. */
    val canSelectMore: Boolean get() = selectedUris.size < WatchFeedsPreference.MAX_FEEDS

    /** Whether [uri]'s checkbox can change: never empty the watch, never pass the cap. */
    fun isToggleable(uri: String): Boolean = if (uri in selectedUris) selectedUris.size > 1 else canSelectMore
}

internal sealed interface WatchSettingsEvent : UiEvent {
    data class FeedToggled(
        val uri: String,
        val selected: Boolean,
    ) : WatchSettingsEvent
}

internal sealed interface WatchSettingsEffect : UiEffect {
    /** A preference write failed; the screen surfaces it as a snackbar. */
    data object ShowSaveError : WatchSettingsEffect
}
