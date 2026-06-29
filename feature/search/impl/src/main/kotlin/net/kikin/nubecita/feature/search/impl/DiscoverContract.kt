package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.feature.search.impl.data.FeedPreviewPostUi
import net.kikin.nubecita.feature.search.impl.data.SuggestedAccountUi
import net.kikin.nubecita.feature.search.impl.data.SuggestedFeedUi

/**
 * A suggested feed entry augmented with its lazy-loaded preview strip and
 * a live [isPinned] flag seeded from [net.kikin.nubecita.core.feeds.PinnedFeedsRepository]
 * and toggled optimistically by [DiscoverEvent.OnPinTapped].
 *
 * The base [feed] holds the API projection ([SuggestedFeedUi]), which carries
 * its own [SuggestedFeedUi.isPinned] field. [DiscoverViewModel] overlays that
 * field from [net.kikin.nubecita.core.feeds.PinnedFeedsRepository.observePinnedFeeds]
 * (URI match) immediately after the feeds section loads and again whenever the
 * pinned-feeds Flow emits a new value.
 *
 * [preview] is null until [DiscoverEvent.OnFeedCardVisible] triggers the first
 * fetch; [previewStatus] tracks the lifecycle of that fetch.
 */
@Immutable
internal data class DiscoverFeedUi(
    val feed: SuggestedFeedUi,
    val preview: ImmutableList<FeedPreviewPostUi>? = null,
    val previewStatus: FeedPreviewStatus = FeedPreviewStatus.Idle,
)

/**
 * Load lifecycle for the preview strip inside a single [DiscoverFeedUi].
 *
 * Transitions: Idle → Loading → Loaded | Error.
 * Once in [Loaded] or [Error], a subsequent [DiscoverEvent.OnFeedCardVisible]
 * for the same URI is a no-op — the fetch is cached for the lifetime of
 * the [DiscoverViewModel].
 */
@Immutable
internal sealed interface FeedPreviewStatus {
    /** Preview has not been requested yet. */
    data object Idle : FeedPreviewStatus

    /** Preview fetch is in flight. */
    data object Loading : FeedPreviewStatus

    /** Preview loaded successfully; [DiscoverFeedUi.preview] carries the data. */
    data object Loaded : FeedPreviewStatus

    /** Preview fetch failed; [DiscoverFeedUi.preview] remains null. */
    data object Error : FeedPreviewStatus
}

/**
 * Mutually-exclusive load lifecycle shared by the accounts and feeds sections
 * in [DiscoverState].
 *
 * - [Loaded]: section is shown with its list.
 * - [Empty] / [Error]: section is hidden; a subsequent [DiscoverEvent.OnAppear]
 *   triggers an automatic retry.
 */
@Immutable
internal sealed interface DiscoverSectionStatus {
    /** Initial state — never loaded. */
    data object Idle : DiscoverSectionStatus

    /** Fetch in flight. A concurrent [DiscoverEvent.OnAppear] is a no-op. */
    data object Loading : DiscoverSectionStatus

    /** Populated. The UI renders the section list. */
    data object Loaded : DiscoverSectionStatus

    /**
     * Fetched but returned zero items. The UI hides the section.
     * A subsequent [DiscoverEvent.OnAppear] retries the fetch.
     */
    data object Empty : DiscoverSectionStatus

    /**
     * Fetch failed. The UI hides the section.
     * A subsequent [DiscoverEvent.OnAppear] retries the fetch.
     */
    data object Error : DiscoverSectionStatus
}

/** Returns true when a (re-)fetch should be initiated by [DiscoverEvent.OnAppear]. */
internal fun DiscoverSectionStatus.needsLoad(): Boolean = this !is DiscoverSectionStatus.Loaded && this !is DiscoverSectionStatus.Loading

/**
 * MVI state for the Discover tab.
 *
 * The two sections ([accounts] / [feeds]) load and fail independently so a
 * feeds error does not mask the accounts section and vice-versa.
 *
 * [accounts] is the suggested-people list after session-local dismissals are
 * applied — dismissed DIDs are removed immediately and the exclusion is kept
 * in [DiscoverViewModel] for the lifetime of the ViewModel. [accountsStatus]
 * is the section load lifecycle.
 *
 * [feeds] is the list of [DiscoverFeedUi] entries, each with its own lazy
 * preview state. [feedsStatus] is the section load lifecycle.
 */
@Immutable
internal data class DiscoverState(
    val accounts: ImmutableList<SuggestedAccountUi> = persistentListOf(),
    val accountsStatus: DiscoverSectionStatus = DiscoverSectionStatus.Idle,
    val feeds: ImmutableList<DiscoverFeedUi> = persistentListOf(),
    val feedsStatus: DiscoverSectionStatus = DiscoverSectionStatus.Idle,
) : UiState

internal sealed interface DiscoverEvent : UiEvent {
    /**
     * The Discover tab (or its host screen) became visible.
     *
     * On first appear both sections are loaded concurrently. On subsequent
     * appearances:
     * - [DiscoverSectionStatus.Loaded] sections are skipped (load-once).
     * - [DiscoverSectionStatus.Error] and [DiscoverSectionStatus.Empty] sections
     *   are retried automatically.
     * - [DiscoverSectionStatus.Loading] sections are skipped (in-flight guard).
     */
    data object OnAppear : DiscoverEvent

    /** Pull-to-refresh — unconditionally reloads both sections. */
    data object OnRefresh : DiscoverEvent

    /**
     * The Follow / Following button was tapped for the account with [did].
     *
     * Optimistically toggles [SuggestedAccountUi.isFollowing] in state, then
     * calls the appropriate [net.kikin.nubecita.core.postinteractions.FollowRepository]
     * method. On success fires analytics. On failure reverts the flag and emits
     * [DiscoverEffect.ShowError]. Concurrent taps on the same [did] are dropped.
     */
    data class OnFollowTapped(
        val did: String,
    ) : DiscoverEvent

    /**
     * The Pin / Unpin button was tapped for the feed at [uri].
     *
     * Optimistically flips [SuggestedFeedUi.isPinned] in state, then calls
     * [net.kikin.nubecita.core.feeds.PinnedFeedsRepository.pinFeed] or
     * [net.kikin.nubecita.core.feeds.PinnedFeedsRepository.unpinFeed].
     * On success fires analytics. On failure reverts the flag on the CURRENT
     * state (targeted flip, not snapshot clobber) and emits [DiscoverEffect.ShowError].
     * Concurrent taps on the same [uri] are dropped.
     */
    data class OnPinTapped(
        val uri: String,
    ) : DiscoverEvent

    /**
     * A feed card scrolled into view. Triggers a one-shot lazy preview fetch
     * for [uri] via [net.kikin.nubecita.feature.search.impl.data.SuggestionsRepository.getFeedPreview].
     * Subsequent calls for the same [uri] are no-ops once the fetch has started
     * or completed (successful or failed).
     */
    data class OnFeedCardVisible(
        val uri: String,
    ) : DiscoverEvent

    /** Tap on an account row. Emits [DiscoverEffect.NavigateToProfile]. */
    data class OnAccountTapped(
        val did: String,
        val handle: String,
    ) : DiscoverEvent

    /** Tap on a feed card (outside the Pin button). Emits [DiscoverEffect.NavigateToFeed]. */
    data class OnFeedTapped(
        val uri: String,
        val displayName: String,
    ) : DiscoverEvent

    /**
     * The user dismissed the account card for [did]. Removes the account from
     * [DiscoverState.accounts] for the lifetime of this ViewModel session.
     * The dismissal is NOT persisted — restarting the app restores the account
     * on the next suggestions fetch.
     */
    data class OnAccountDismissed(
        val did: String,
    ) : DiscoverEvent
}

internal sealed interface DiscoverEffect : UiEffect {
    /** Navigate to the Profile screen for the account identified by [handle]. */
    data class NavigateToProfile(
        val handle: String,
    ) : DiscoverEffect

    /** Navigate to the FeedView screen for the given feed. */
    data class NavigateToFeed(
        val uri: String,
        val displayName: String,
    ) : DiscoverEffect

    /** Non-sticky error — the screen surfaces this as a transient snackbar. */
    data class ShowError(
        val message: String,
    ) : DiscoverEffect
}
