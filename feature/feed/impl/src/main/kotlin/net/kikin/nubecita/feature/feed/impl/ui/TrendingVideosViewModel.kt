package net.kikin.nubecita.feature.feed.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.videofeed.VideoFeedSource
import net.kikin.nubecita.data.models.EmbedUi
import timber.log.Timber
import javax.inject.Inject

/** A trending-video thumbnail: its poster + its index into the vertical feed. */
data class TrendingVideoThumb(
    val index: Int,
    val posterUrl: String?,
)

/**
 * Loads the first page of trending videos for the Discover carousel. Shares
 * [VideoFeedSource] with the vertical feed, so a thumbnail's [TrendingVideoThumb.index]
 * is exactly the `VideoFeed(startIndex)` to open. Load failure → empty (the
 * carousel simply hides).
 */
@HiltViewModel
class TrendingVideosViewModel
    @Inject
    constructor(
        private val source: VideoFeedSource,
    ) : ViewModel() {
        private val _thumbs = MutableStateFlow<ImmutableList<TrendingVideoThumb>>(persistentListOf())
        val thumbs: StateFlow<ImmutableList<TrendingVideoThumb>> = _thumbs.asStateFlow()

        init {
            viewModelScope.launch {
                source
                    .loadPage(null)
                    .onSuccess { page ->
                        _thumbs.value =
                            page.items
                                .mapIndexedNotNull { index, post ->
                                    (post.embed as? EmbedUi.Video)?.let { video -> TrendingVideoThumb(index, video.posterUrl) }
                                }.toImmutableList()
                    }.onFailure { Timber.w(it, "trending videos carousel load failed") }
            }
        }
    }
