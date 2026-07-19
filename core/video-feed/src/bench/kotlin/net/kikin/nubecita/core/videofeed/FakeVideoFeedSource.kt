package net.kikin.nubecita.core.videofeed

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Bench-flavor [VideoFeedSource]. Returns a fixed set of video posts backed by
 * the bundled `asset:///video/clip-*.mp4` clips (and their `video-poster-*.jpg`
 * posters), so the Trending Videos carousel + the vertical video feed play
 * fully offline on the bench build. Single page (no cursor).
 */
public class FakeVideoFeedSource
    @Inject
    constructor() : VideoFeedSource {
        override suspend fun loadPage(cursor: String?): Result<VideoFeedPage> = Result.success(VideoFeedPage(items = ITEMS, cursor = null))

        private companion object {
            val ITEMS: List<PostUi> =
                listOf(
                    videoPost(1, "Trail run this morning", "hugo.jpg", "Hugo"),
                    videoPost(2, "Sunset over the bay", "ivy.jpg", "Ivy"),
                    videoPost(3, "Studio session take 4", "jess.jpg", "Jess"),
                    videoPost(1, "Rewatching the good part", "hugo.jpg", "Hugo"),
                    videoPost(2, "One more loop", "ivy.jpg", "Ivy"),
                )

            private fun videoPost(
                clip: Int,
                text: String,
                avatar: String,
                name: String,
            ): PostUi =
                PostUi(
                    id = "at://did:plc:benchvideo/app.bsky.feed.post/vid-$clip-${name.lowercase()}",
                    cid = "bafyreifakevideobenchbenchbenchbenchbenchbenc$clip",
                    author =
                        AuthorUi(
                            did = "did:plc:benchvideo$clip",
                            handle = "${name.lowercase()}.bsky.social",
                            displayName = name,
                            avatarUrl = "file:///android_asset/img/avatars/$avatar",
                        ),
                    createdAt = Instant.parse("2026-07-18T12:00:00Z"),
                    text = text,
                    facets = persistentListOf(),
                    embed =
                        EmbedUi.Video(
                            posterUrl = "file:///android_asset/img/posts/video-poster-$clip.jpg",
                            playlistUrl = "asset:///video/clip-$clip.mp4",
                            aspectRatio = 0.5625f,
                            durationSeconds = 8,
                            altText = null,
                        ),
                    stats = PostStatsUi(),
                    viewer = ViewerStateUi(),
                    repostedBy = null,
                )
        }
    }
