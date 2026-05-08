package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import java.io.IOException

/**
 * Presenter for the fullscreen image viewer.
 *
 * Uses Hilt's assisted-injection bridge so the [MediaViewerRoute]
 * (carrying `postUri` + `imageIndex`) flows from the entry-provider
 * call site into the VM constructor — same pattern as `PostDetailViewModel`.
 *
 * The VM does NOT own the pager state. `HorizontalPager`'s
 * `rememberPagerState` lives in the screen composable; the active page
 * flows back into state via [MediaViewerEvent.OnPageChanged] for chrome
 * rendering (page indicator, ALT badge visibility).
 *
 * The VM does NOT inject `LocalMainShellNavState` (per the
 * `CLAUDE.md` rule that ViewModels never reach into Compose
 * `CompositionLocal`s). All dismiss paths — close button, swipe-down,
 * back press — converge on [MediaViewerEffect.Dismiss], which the
 * screen's `LaunchedEffect` collector translates to
 * `LocalMainShellNavState.current.removeLast()`.
 */
@HiltViewModel(assistedFactory = MediaViewerViewModel.Factory::class)
internal class MediaViewerViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: MediaViewerRoute,
        private val postRepository: PostRepository,
    ) : MviViewModel<MediaViewerState, MediaViewerEvent, MediaViewerEffect>(MediaViewerState()) {
        @AssistedFactory
        interface Factory {
            fun create(route: MediaViewerRoute): MediaViewerViewModel
        }

        init {
            handleEvent(MediaViewerEvent.Load)
        }

        override fun handleEvent(event: MediaViewerEvent) {
            when (event) {
                MediaViewerEvent.Load -> load()
                MediaViewerEvent.Retry -> load()
                is MediaViewerEvent.OnPageChanged -> onPageChanged(event.index)
                MediaViewerEvent.OnTapImage -> onTapImage()
                MediaViewerEvent.OnAltBadgeClick -> setSheetOpen(true)
                MediaViewerEvent.OnAltSheetDismiss -> setSheetOpen(false)
                MediaViewerEvent.OnChromeAutoFadeTimeout -> setChromeVisible(false)
                MediaViewerEvent.OnDismissRequest -> sendEffect(MediaViewerEffect.Dismiss)
            }
        }

        private fun load() {
            // Idempotent: if already loading, drop the event. From Loaded,
            // also drop (no reason to re-fetch a working surface).
            val status = uiState.value.loadStatus
            if (status is MediaViewerLoadStatus.Loading || status is MediaViewerLoadStatus.Loaded) return
            setState { copy(loadStatus = MediaViewerLoadStatus.Loading) }
            viewModelScope.launch {
                postRepository
                    .getPost(route.postUri)
                    .onSuccess { post ->
                        when (val embed = post.embed) {
                            is EmbedUi.Images ->
                                setState {
                                    copy(
                                        loadStatus =
                                            MediaViewerLoadStatus.Loaded(
                                                images = embed.items.toImmutableList(),
                                                currentIndex = route.imageIndex.coerceIn(0, embed.items.size - 1),
                                                isChromeVisible = true,
                                                isAltSheetOpen = false,
                                            ),
                                    )
                                }
                            else ->
                                // Post was opened on something that isn't an image
                                // embed — defensive (the contract is that only
                                // image-bearing focus posts route here, but a future
                                // deep-link landing could bring us here on, e.g.,
                                // a video post).
                                setState {
                                    copy(loadStatus = MediaViewerLoadStatus.Error(MediaViewerError.NoImages))
                                }
                        }
                    }.onFailure { throwable ->
                        setState {
                            copy(loadStatus = MediaViewerLoadStatus.Error(throwable.toMediaViewerError()))
                        }
                    }
            }
        }

        private fun onPageChanged(index: Int) {
            val status = uiState.value.loadStatus
            if (status !is MediaViewerLoadStatus.Loaded) return
            if (status.currentIndex == index) return
            // Page change re-shows chrome and resets the auto-fade timer
            // (the timer is driven by the screen's LaunchedEffect; we just
            // flip the flag back to true so it restarts).
            setState {
                copy(
                    loadStatus =
                        status.copy(
                            currentIndex = index,
                            isChromeVisible = true,
                            isAltSheetOpen = false,
                        ),
                )
            }
        }

        private fun onTapImage() {
            val status = uiState.value.loadStatus
            if (status !is MediaViewerLoadStatus.Loaded) return
            // Tapping while the alt sheet is open is a no-op — the sheet's
            // scrim already absorbed the tap before it reached the image.
            if (status.isAltSheetOpen) return
            setState { copy(loadStatus = status.copy(isChromeVisible = !status.isChromeVisible)) }
        }

        private fun setSheetOpen(open: Boolean) {
            val status = uiState.value.loadStatus
            if (status !is MediaViewerLoadStatus.Loaded) return
            // Opening the sheet implicitly shows chrome (the sheet sits
            // above chrome anyway, but flipping the flag keeps state
            // self-consistent if the user dismisses the sheet).
            setState {
                copy(loadStatus = status.copy(isAltSheetOpen = open, isChromeVisible = if (open) true else status.isChromeVisible))
            }
        }

        private fun setChromeVisible(visible: Boolean) {
            val status = uiState.value.loadStatus
            if (status !is MediaViewerLoadStatus.Loaded) return
            if (status.isChromeVisible == visible) return
            setState { copy(loadStatus = status.copy(isChromeVisible = visible)) }
        }

        private fun Throwable.toMediaViewerError(): MediaViewerError =
            when (this) {
                is NoSessionException -> MediaViewerError.Unauthenticated
                is IOException -> MediaViewerError.Network
                is XrpcError -> {
                    if (errorName.equals("NotFound", ignoreCase = true) || status == HTTP_NOT_FOUND) {
                        MediaViewerError.NotFound
                    } else {
                        MediaViewerError.Unknown(cause = errorName)
                    }
                }
                // PostNotFoundException + PostProjectionException are :core:posts
                // sentinels; they collapse to NotFound at the user surface.
                else -> {
                    val name = this::class.simpleName.orEmpty()
                    if (name == "PostNotFoundException" || name == "PostProjectionException") {
                        MediaViewerError.NotFound
                    } else {
                        MediaViewerError.Unknown(cause = message)
                    }
                }
            }

        private companion object {
            const val HTTP_NOT_FOUND = 404
        }
    }
