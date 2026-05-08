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
import net.kikin.nubecita.core.posts.PostNotFoundException
import net.kikin.nubecita.core.posts.PostProjectionException
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
 * The VM does NOT inject any nav state holder (per the `CLAUDE.md` rule
 * that ViewModels never reach into Compose `CompositionLocal`s). All
 * dismiss paths — close button, swipe-down, back press — converge on
 * [MediaViewerEffect.Dismiss], which the screen's `LaunchedEffect`
 * collector translates to `LocalAppNavigator.current.goBack()` on the
 * outer `Navigator` (the viewer is hosted on the outer `NavDisplay`
 * to escape `MainShell`'s chrome — see `MediaViewerNavigationModule`).
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
            // Idempotent. Drop when:
            //  - a fetch is already in flight (prevents duplicate network
            //    calls + setState races when LaunchedEffect(Unit) refires
            //    on recomposition or the user double-taps Retry); OR
            //  - the surface is already Loaded (no reason to refetch a
            //    working surface).
            // Mirrors the in-flight guard pattern in PostDetail / Feed VMs.
            if (hasActiveFetch) return
            if (uiState.value.loadStatus is MediaViewerLoadStatus.Loaded) return
            setState { copy(loadStatus = MediaViewerLoadStatus.Loading) }
            hasActiveFetch = true
            viewModelScope.launch {
                postRepository
                    .getPost(route.postUri)
                    .onSuccess { post ->
                        val embed = post.embed
                        if (embed !is EmbedUi.Images || embed.items.isEmpty()) {
                            // Either the focus post has no image embed (defensive
                            // — viewer was opened on a non-image post via some
                            // out-of-band path) or the embed projected to an
                            // empty list. Coerce-into-empty would throw; render
                            // the user-facing "no images" state instead.
                            setState {
                                copy(loadStatus = MediaViewerLoadStatus.Error(MediaViewerError.NoImages))
                            }
                        } else {
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
                        }
                    }.onFailure { throwable ->
                        setState {
                            copy(loadStatus = MediaViewerLoadStatus.Error(throwable.toMediaViewerError()))
                        }
                    }
                hasActiveFetch = false
            }
        }

        /** Tracks an in-flight `getPost(...)` so concurrent `Load` / `Retry` events drop. */
        private var hasActiveFetch: Boolean = false

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
                // :core:posts public sentinels — type-safe `is` checks survive
                // R8 minification (the previous stringly-typed simpleName
                // matching would silently misclassify on release builds).
                is PostNotFoundException, is PostProjectionException -> MediaViewerError.NotFound
                else -> MediaViewerError.Unknown(cause = message)
            }

        private companion object {
            const val HTTP_NOT_FOUND = 404
        }
    }
