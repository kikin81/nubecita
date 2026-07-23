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
                    // Portrait clip that LIES about its ratio: the asset is 720x1280 but it
                    // declares 16:9, simulating a record whose optional aspectRatio is absent
                    // (the mapper fabricates 16:9). The surface must follow the decoded size,
                    // not the declared 16:9, or the portrait clip stretches (nubecita-mfac).
                    videoPost(4, "No aspect ratio in the record", "ivy.jpg", "Lyra", declaredAspectRatio = 16f / 9f),
                    // Portrait 9:16 clip, so a landscape -> portrait swipe is one step away.
                    // Exercises the aspect-ratio transition (nubecita aspect-lag bug).
                    videoPost(4, "Vertical vista", "ivy.jpg", "Ivy"),
                    videoPost(2, "Sunset over the bay", "ivy.jpg", "Ivy"),
                    videoPost(3, "Studio session take 4", "jess.jpg", "Jess"),
                    videoPost(1, "Rewatching the good part", "hugo.jpg", "Hugo"),
                    videoPost(2, "One more loop", "ivy.jpg", "Ivy"),
                )

            /**
             * Real geometry of the bundled clips, so the fixture does not lie to the
             * layer under test. `aspectRatio` sizes the poster before the video
             * decodes (design D4), so a wrong value makes every page's poster snap
             * when the real size arrives — the exact jump D4 exists to prevent.
             *
             * clip-1 1280x720, clip-2 1694x720, clip-3 1728x720 (landscape); clip-4
             * 720x1280 (portrait 9:16). Durations 15/14/15/12s.
             */
            private fun aspectFor(clip: Int): Float =
                when (clip) {
                    1 -> 1280f / 720f
                    2 -> 1694f / 720f
                    4 -> 720f / 1280f // portrait 9:16
                    else -> 1728f / 720f
                }

            private fun durationFor(clip: Int): Int =
                when (clip) {
                    2 -> 14
                    4 -> 12
                    else -> 15
                }

            private fun videoPost(
                clip: Int,
                text: String,
                avatar: String,
                name: String,
                declaredAspectRatio: Float = aspectFor(clip),
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
                            aspectRatio = declaredAspectRatio,
                            durationSeconds = durationFor(clip),
                            altText = null,
                        ),
                    stats = PostStatsUi(),
                    viewer = ViewerStateUi(),
                    repostedBy = null,
                )
        }
    }
