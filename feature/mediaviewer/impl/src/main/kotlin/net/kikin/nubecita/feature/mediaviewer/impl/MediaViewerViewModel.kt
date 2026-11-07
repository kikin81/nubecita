package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.image.ImageRetrievalException
import net.kikin.nubecita.core.image.ImageSaver
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
import net.kikin.nubecita.core.posts.PostNotFoundException
import net.kikin.nubecita.core.posts.PostProjectionException
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.imageContainer
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
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
        private val imageSaver: ImageSaver,
        private val handler: PostInteractionHandler,
        private val postInteractionsCache: PostInteractionsCache,
    ) : MviViewModel<MediaViewerState, MediaViewerEvent, MediaViewerEffect>(MediaViewerState()),
        PostInteractionHandler by handler {
        @AssistedFactory
        interface Factory {
            fun create(route: MediaViewerRoute): MediaViewerViewModel
        }

        val postUri: String get() = route.postUri
        val contextKey: String get() = route.contextKey

        init {
            handler.bind(PostSurface.MediaViewer, viewModelScope)
            // The handler owns writes; this is the read side. Without it a tap
            // on like appears to work and then reverts.
            viewModelScope.launch {
                postInteractionsCache.state.collect { snapshot ->
                    setState {
                        val current = loadStatus
                        if (current is MediaViewerLoadStatus.Loaded) {
                            copy(loadStatus = current.copy(post = current.post.withInteraction(snapshot)))
                        } else {
                            this
                        }
                    }
                }
            }
        }

        private fun PostUi.withInteraction(snapshot: Map<String, PostInteractionState>): PostUi {
            val interaction = snapshot[id]
            return if (interaction != null && !interaction.isDeleted) mergeInteractionState(interaction) else this
        }

        override fun handleEvent(event: MediaViewerEvent) {
            when (event) {
                MediaViewerEvent.Load -> load()
                MediaViewerEvent.Retry -> load()
                is MediaViewerEvent.OnPageChanged -> onPageChanged(event.index)
                MediaViewerEvent.OnTapImage -> onTapImage()
                MediaViewerEvent.OnAltBadgeClick -> setSheetOpen(true)
                MediaViewerEvent.OnAltSheetDismiss -> setSheetOpen(false)
                MediaViewerEvent.OnChromeAutoFadeTimeout -> onChromeAutoFadeTimeout()
                MediaViewerEvent.OnDismissRequest -> onDismissRequest()
                MediaViewerEvent.OnSaveClick -> onSaveClick()
                MediaViewerEvent.OnCommentsClick -> setCommentsOpen(true)
                MediaViewerEvent.OnCommentsDismiss -> setCommentsOpen(false)
                MediaViewerEvent.OnRepostClick -> updateLoaded { copy(isRepostSheetOpen = true, isChromeVisible = true) }
                MediaViewerEvent.OnRepostDismiss -> updateLoaded { copy(isRepostSheetOpen = false) }
                MediaViewerEvent.OnChromeInteraction -> updateLoaded { copy(chromeInteractions = chromeInteractions + 1) }
                is MediaViewerEvent.OnOpenInMainShell -> openInMainShell(event.route)
                MediaViewerEvent.OnAuthorClick -> onAuthorClick()
                MediaViewerEvent.OnCaptionClick -> onCaptionClick()
            }
        }

        private fun onChromeAutoFadeTimeout() {
            val status = uiState.value.loadStatus
            if (status is MediaViewerLoadStatus.Loaded && status.holdsChrome) return
            setChromeVisible(false)
        }

        /** Back and swipe-down both land here, so an open sheet closes before the viewer does. */
        private fun onDismissRequest() {
            val status = uiState.value.loadStatus
            if (status is MediaViewerLoadStatus.Loaded && status.isCommentsOpen) {
                setCommentsOpen(false)
            } else {
                sendEffect(MediaViewerEffect.Dismiss)
            }
        }

        private fun setCommentsOpen(open: Boolean) {
            updateLoaded { copy(isCommentsOpen = open, isChromeVisible = true) }
        }

        private val MediaViewerLoadStatus.Loaded.holdsChrome: Boolean
            get() = isCommentsOpen || isRepostSheetOpen

        /** Reads inside the reducer, so a concurrent merge isn't reverted by a captured copy. */
        private fun updateLoaded(transform: MediaViewerLoadStatus.Loaded.() -> MediaViewerLoadStatus.Loaded) {
            setState {
                val latest = loadStatus
                if (latest is MediaViewerLoadStatus.Loaded) copy(loadStatus = latest.transform()) else this
            }
        }

        private fun openInMainShell(route: NavKey) {
            updateLoaded { copy(isLeavingForRoute = true) }
            sendEffect(MediaViewerEffect.OpenInMainShell(route))
        }

        private fun onAuthorClick() {
            val status = uiState.value.loadStatus
            if (status !is MediaViewerLoadStatus.Loaded) return
            // By DID, as every other surface does: it still resolves for an
            // invalid handle, and it matches the key MainShell's single-top sees.
            openInMainShell(Profile(handle = status.post.author.did))
        }

        /**
         * No check for "opened from this post": the surface can't say, since a
         * quoted post's image opens from post detail too. If the post is already
         * on top, MainShell's single-top add drops the route.
         */
        private fun onCaptionClick() {
            openInMainShell(PostDetailRoute(postUri = route.postUri))
        }

        private fun onSaveClick() {
            val status = uiState.value.loadStatus
            if (status !is MediaViewerLoadStatus.Loaded) return
            // Drop the second tap rather than queueing it — the user wants one
            // copy in their gallery, not one per impatient tap.
            if (status.isSaving) return
            // The screen omits the affordance when canSave is false, so this is
            // unreachable through the UI — but reaching saveToGallery anyway
            // would surface ImageSaveUnsupportedException, which the mapping
            // below would mislabel as a storage failure.
            if (!status.canSave) return
            val image = status.images.getOrNull(status.currentIndex) ?: return

            // Same atomicity rule as the clear below: read inside the reducer.
            // `image` above is deliberately still the captured one — the user
            // meant the page that was on screen when they tapped — but writing
            // a captured Loaded back would revert a page change landing in this
            // window.
            setState {
                val latest = loadStatus
                if (latest is MediaViewerLoadStatus.Loaded) {
                    copy(loadStatus = latest.copy(isSaving = true))
                } else {
                    this
                }
            }
            viewModelScope.launch {
                val outcome =
                    imageSaver
                        .saveToGallery(url = image.fullsizeUrl)
                        .fold(
                            onSuccess = { MediaViewerSaveOutcome.Saved },
                            onFailure = { failure ->
                                if (failure is ImageRetrievalException) {
                                    MediaViewerSaveOutcome.RetrievalFailed
                                } else {
                                    MediaViewerSaveOutcome.StorageFailed
                                }
                            },
                        )
                // Read the status inside the reducer, not before it: setState
                // is a compare-and-set update, so anything captured outside can
                // be stale by the time the lambda runs — and writing a captured
                // Loaded back would silently revert a page change made during
                // the save.
                setState {
                    val settled = loadStatus
                    if (settled is MediaViewerLoadStatus.Loaded) {
                        copy(loadStatus = settled.copy(isSaving = false))
                    } else {
                        this
                    }
                }
                sendEffect(MediaViewerEffect.ShowSaveOutcome(outcome))
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
                        val embed = post.embed.imageContainer
                        if (embed == null || embed.items.isEmpty()) {
                            // The focus post has no image-container embed (Images
                            // or Gallery), directly or as the media half of a
                            // RecordWithMedia quote — or it projected to an empty
                            // list. Coerce-into-empty would throw; render the
                            // user-facing "no images" state instead.
                            setState {
                                copy(loadStatus = MediaViewerLoadStatus.Error(MediaViewerError.NoImages))
                            }
                        } else {
                            // Only when the app holds nothing for it: seed() would let a
                            // lagging appview bring back a like the reader just removed.
                            if (post.id !in postInteractionsCache.state.value) {
                                postInteractionsCache.seed(listOf(post))
                            }
                            val currentPost = post.withInteraction(postInteractionsCache.state.value)
                            setState {
                                copy(
                                    loadStatus =
                                        MediaViewerLoadStatus.Loaded(
                                            post = currentPost,
                                            images = embed.items.toImmutableList(),
                                            currentIndex = route.imageIndex.coerceIn(0, embed.items.size - 1),
                                            isChromeVisible = true,
                                            isAltSheetOpen = false,
                                            canSave = imageSaver.isSupported,
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
            if (status.isAltSheetOpen || status.holdsChrome) return
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
