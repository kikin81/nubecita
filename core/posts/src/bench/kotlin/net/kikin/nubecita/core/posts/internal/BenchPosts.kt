package net.kikin.nubecita.core.posts.internal

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import kotlin.time.Instant

/*
 * Deterministic, network-free post fixtures shared by the bench-flavor fakes
 * (BenchFakePostRepository single-post read, BenchFakePostThreadRepository
 * thread read). Asset-backed (file:///android_asset/..., asset:///...) so they
 * render fully offline under the `bench` flavor.
 *
 * The fixtures' AT-URIs are DID-based on purpose: the appview's
 * app.bsky.feed.getPosts only resolves DID-based at-uris, and the bench
 * single-post fake mirrors that (see isDidAtUri) so a deep-link's handle-based
 * URI faithfully reproduces the "Post not found" media-viewer path offline.
 */

/**
 * Whether [this] is a DID-based AT-URI (`at://did:.../...`). Mirrors the
 * appview's `getPosts` constraint: handle-based at-uris (the deep-link form
 * `at://<handle>/...`) are not resolved and surface as NotFound.
 */
internal fun String.isDidAtUri(): Boolean = startsWith("at://did:")

private fun galleryImage(
    name: String,
    alt: String,
    aspect: Float,
): ImageUi =
    ImageUi(
        fullsizeUrl = "file:///android_asset/img/posts/$name",
        thumbUrl = "file:///android_asset/img/posts/$name",
        altText = alt,
        aspectRatio = aspect,
    )

private fun benchTextPost(
    rkey: String,
    didSuffix: String,
    handle: String,
    displayName: String,
    avatar: String,
    createdAt: String,
    body: String,
    stats: PostStatsUi,
): PostUi =
    PostUi(
        id = "at://did:plc:$didSuffix/app.bsky.feed.post/$rkey",
        cid = "bafyreibench${didSuffix}00000000000000000000000000000000000000",
        author =
            AuthorUi(
                did = "did:plc:$didSuffix",
                handle = handle,
                displayName = displayName,
                avatarUrl = "file:///android_asset/img/avatars/$avatar",
            ),
        createdAt = Instant.parse(createdAt),
        text = body,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = stats,
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

/**
 * The asset-backed video post that unblocks the fullscreen player under bench
 * (resolved via `getPost` when the bench journey taps the feed's video post).
 */
internal val benchVideoPost: PostUi =
    PostUi(
        id = "at://did:plc:benchivy/app.bsky.feed.post/canyon",
        cid = "bafyreiivyf00000000000000000000000000000000000000000001",
        author =
            AuthorUi(
                did = "did:plc:benchivy",
                handle = "ivy.fpv",
                displayName = "Ivy Park",
                avatarUrl = "file:///android_asset/img/avatars/hugo.jpg",
            ),
        createdAt = Instant.parse("2026-06-01T12:00:00Z"),
        text =
            "test flight over the canyon yesterday. half a battery on hard cuts " +
                "between rim, slot, and the wash.",
        facets = persistentListOf(),
        embed =
            EmbedUi.Video(
                posterUrl = "file:///android_asset/img/posts/video-poster-3.jpg",
                playlistUrl = "asset:///video/clip-3.mp4",
                aspectRatio = 1.7777778f,
                durationSeconds = 15,
                altText = "Fast cuts between FPV drone shots over canyon terrain.",
            ),
        stats = PostStatsUi(replyCount = 9, repostCount = 2, likeCount = 41),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

/**
 * The multi-image **gallery** post used as the focus of the bench post-detail
 * thread. Tapping any image opens the media viewer, which re-reads the post via
 * `getPost(focus.id)` — DID-based, so it resolves (the deep-link fix). Backed by
 * four committed `img/posts` assets.
 */
internal val benchGalleryPost: PostUi =
    PostUi(
        // rkey is a syntactically valid 13-char TID ([2-7a-z]{13}) so the
        // post deep-link matcher (isValidRkey) accepts a deep link into this
        // bench post — `https://bsky.app/profile/jess.trails/post/ridgewalk2357`.
        id = "at://did:plc:benchjess/app.bsky.feed.post/ridgewalk2357",
        cid = "bafyreijess00000000000000000000000000000000000000000001",
        author =
            AuthorUi(
                did = "did:plc:benchjess",
                handle = "jess.trails",
                displayName = "Jessica Elena",
                avatarUrl = "file:///android_asset/img/avatars/elena.jpg",
            ),
        createdAt = Instant.parse("2026-06-02T09:30:00Z"),
        text =
            "three days on the ridge — first light on the crux pitch, the long walk in, " +
                "and the topo that got us there.",
        facets = persistentListOf(),
        embed =
            EmbedUi.Images(
                items =
                    persistentListOf(
                        galleryImage("ridge-overlook.jpg", "Sunrise over the ridge crest.", 1.5f),
                        galleryImage("ridge-trail.jpg", "The trail switchbacking up to the crux.", 0.75f),
                        galleryImage("ridge-flower.jpg", "Alpine flowers at the saddle.", 1.0f),
                        galleryImage("topo-map.jpg", "Topographic map with the route traced in red.", 1.3333334f),
                    ),
            ),
        stats = PostStatsUi(replyCount = 2, repostCount = 12, likeCount = 88),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

/**
 * Two text replies beneath [benchGalleryPost] so the bench post-detail renders a
 * full thread (focus + replies) — a richer tablet list-detail pane than the
 * empty "select post" placeholder.
 */
internal val benchGalleryReplies: List<PostUi> =
    listOf(
        benchTextPost(
            rkey = "reply1",
            didSuffix = "benchgabe",
            handle = "gabe.climbs",
            displayName = "Gabe",
            avatar = "gabe.jpg",
            createdAt = "2026-06-02T10:05:00Z",
            body = "that second frame is unreal. what time did you start the approach?",
            stats = PostStatsUi(replyCount = 0, repostCount = 0, likeCount = 6),
        ),
        benchTextPost(
            rkey = "reply2",
            didSuffix = "benchcarmen",
            handle = "carmen.alpine",
            displayName = "Carmen",
            avatar = "carmen.jpg",
            createdAt = "2026-06-02T10:40:00Z",
            body = "saving the topo. been eyeing this line all season.",
            stats = PostStatsUi(replyCount = 0, repostCount = 1, likeCount = 14),
        ),
    )
