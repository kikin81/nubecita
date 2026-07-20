package net.kikin.nubecita.feature.feed.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.videofeed.VideoFeedSource
import net.kikin.nubecita.data.models.EmbedUi
import timber.log.Timber
import javax.inject.Inject

/** A trending-video thumbnail: its poster + the AtUri of the post it opens. */
data class TrendingVideoThumb(
    val postUri: String,
    val posterUrl: String?,
)

/**
 * Loads the first page of trending videos for the Discover carousel.
 *
 * A thumbnail carries the post's AtUri, not its position: the carousel and the
 * vertical feed fetch this live source independently, so a position taken here
 * would denote a different post by the time the feed loads (nubecita-zdv8.13).
 * Load failure → empty (the carousel simply hides).
 *
 * Loading is an explicit [load] call (not `init`) because this ViewModel is
 * scoped to the host `FeedScreen`: it survives feed switches, so the host
 * re-triggers it on first Discover selection and on pull-to-refresh.
 */
@HiltViewModel
class TrendingVideosViewModel
    @Inject
    constructor(
        private val source: VideoFeedSource,
    ) : ViewModel() {
        private val _thumbs = MutableStateFlow<ImmutableList<TrendingVideoThumb>>(persistentListOf())
        val thumbs: StateFlow<ImmutableList<TrendingVideoThumb>> = _thumbs.asStateFlow()

        private var loadJob: Job? = null

        /** (Re)load the trending page, cancelling any in-flight load first. */
        fun load() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    source
                        .loadPage(null)
                        .onSuccess { page ->
                            _thumbs.value =
                                page.items
                                    .mapNotNull { post ->
                                        (post.embed as? EmbedUi.Video)?.let { video -> TrendingVideoThumb(post.id, video.posterUrl) }
                                    }.distinctBy { it.postUri }
                                    .toImmutableList()
                        }.onFailure { exception ->
                            Timber.w(exception, "trending videos carousel load failed")
                            _thumbs.value = persistentListOf()
                        }
                }
        }
    }
