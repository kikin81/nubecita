package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ImageUi

/**
 * One frame's worth of UI state for the fullscreen image viewer.
 *
 * `loadStatus` is a sealed sum (`Loading` / `Loaded` / `Error`) per the
 * "mutually-exclusive view modes" carve-out in CLAUDE.md — these phases
 * cannot coexist, and the per-`Loaded` fields (`currentIndex`,
 * `isChromeVisible`, `isAltSheetOpen`) only make sense once images are
 * resolved.
 */
@Immutable
internal data class MediaViewerState(
    val loadStatus: MediaViewerLoadStatus = MediaViewerLoadStatus.Loading,
) : UiState

internal sealed interface MediaViewerLoadStatus {
    /** Initial post fetch in flight. */
    @Immutable
    data object Loading : MediaViewerLoadStatus

    /**
     * Post resolved with a non-empty image set. All viewer-active state
     * lives in this variant.
     *
     * - [currentIndex] tracks the active page (driven by
     *   `HorizontalPager.currentPage` via [MediaViewerEvent.OnPageChanged]).
     * - [isChromeVisible] toggles via [MediaViewerEvent.OnTapImage] and
     *   the auto-fade timer; resets to `true` on every page change.
     * - [isAltSheetOpen] is the alt-text bottom-sheet visibility flag,
     *   gated by the current image's alt text being non-null.
     */
    @Immutable
    data class Loaded(
        val images: ImmutableList<ImageUi>,
        val currentIndex: Int,
        val isChromeVisible: Boolean,
        val isAltSheetOpen: Boolean,
    ) : MediaViewerLoadStatus

    /**
     * Sticky failure. The screen renders an error layout with a retry
     * button — dismiss is still available via close button or back press.
     */
    @Immutable
    data class Error(
        val error: MediaViewerError,
    ) : MediaViewerLoadStatus
}

/**
 * UI-resolvable error categories. Mirrors `:feature:postdetail:impl`'s
 * `PostDetailError` shape — the screen maps each variant to a
 * `stringResource` so the VM stays Android-resource-free.
 */
internal sealed interface MediaViewerError {
    /** Underlying network or transport failure. */
    @Immutable
    data object Network : MediaViewerError

    /** No authenticated session. */
    @Immutable
    data object Unauthenticated : MediaViewerError

    /** Post is gone or returned an empty `posts` list from `getPosts`. */
    @Immutable
    data object NotFound : MediaViewerError

    /**
     * Post resolved but its embed is not `EmbedUi.Images` (defensive
     * — viewer was opened on a video / external / record post via some
     * out-of-band path). Renders "This post has no images".
     */
    @Immutable
    data object NoImages : MediaViewerError

    /** Anything else (5xx, decode failure, unexpected throwable). */
    @Immutable
    data class Unknown(
        val cause: String?,
    ) : MediaViewerError
}

internal sealed interface MediaViewerEvent : UiEvent {
    /** First page-fetch attempt. Idempotent — repeated `Load` is a no-op. */
    data object Load : MediaViewerEvent

    /** Re-run the fetch after a [MediaViewerLoadStatus.Error]. */
    data object Retry : MediaViewerEvent

    /** Pager moved to a new page; updates state.currentIndex and resets chrome timer. */
    data class OnPageChanged(
        val index: Int,
    ) : MediaViewerEvent

    /** Single-tap on the image; toggles chrome visibility. */
    data object OnTapImage : MediaViewerEvent

    /** Tap on the `ALT` badge; opens the alt-text bottom sheet. */
    data object OnAltBadgeClick : MediaViewerEvent

    /** Bottom-sheet dismiss (tap-outside / scrim / back press while sheet is open). */
    data object OnAltSheetDismiss : MediaViewerEvent

    /** Auto-fade timer fired; chrome should hide. No-op if already hidden. */
    data object OnChromeAutoFadeTimeout : MediaViewerEvent

    /** Close button, swipe-down past threshold, or back press; dispatches [MediaViewerEffect.Dismiss]. */
    data object OnDismissRequest : MediaViewerEvent
}

internal sealed interface MediaViewerEffect : UiEffect {
    /** Pop the viewer; the screen's collector calls `LocalMainShellNavState.current.removeLast()`. */
    @Immutable
    data object Dismiss : MediaViewerEffect

    /** Non-sticky error (snackbar). Sticky errors flow through state. */
    @Immutable
    data class ShowError(
        val error: MediaViewerError,
    ) : MediaViewerEffect
}
