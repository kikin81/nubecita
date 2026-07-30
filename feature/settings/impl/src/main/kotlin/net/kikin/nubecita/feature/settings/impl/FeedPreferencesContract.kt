package net.kikin.nubecita.feature.settings.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.feeds.ReplyVisibility

/**
 * Flat, UI-ready state for the Feed preferences screen — a direct projection of
 * the cached `FeedViewPrefs` (see `FeedPreferencesViewModel`).
 *
 * [replyVisibility] is a single sum type rather than the two booleans the wire
 * format carries. `hideReplies` and `hideRepliesByUnfollowed` are mutually
 * exclusive in behaviour, so a pair of flat flags could express a combination
 * with no meaning; the enum makes that unrepresentable, per CLAUDE.md's rule for
 * mutually-exclusive modes. [hideReposts] and [hideQuotePosts] are genuinely
 * independent and stay flat booleans.
 */
data class FeedPreferencesState(
    val replyVisibility: ReplyVisibility,
    val hideReposts: Boolean,
    val hideQuotePosts: Boolean,
) : UiState

sealed interface FeedPreferencesEvent : UiEvent {
    /** User picked a reply-visibility segment. */
    data class ReplyVisibilitySelected(
        val visibility: ReplyVisibility,
    ) : FeedPreferencesEvent

    /** User flipped the "Hide reposts" switch. */
    data class HideRepostsToggled(
        val hide: Boolean,
    ) : FeedPreferencesEvent

    /** User flipped the "Hide quote posts" switch. */
    data class HideQuotePostsToggled(
        val hide: Boolean,
    ) : FeedPreferencesEvent
}

sealed interface FeedPreferencesEffect : UiEffect {
    /** Persisting a change to the account failed — surface a snackbar. */
    data object ShowSaveError : FeedPreferencesEffect
}
