package net.kikin.nubecita.wear.feed

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.wear.ui.PreviewWearRoundDevices
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

// Rendered without AppScaffold (its TimeText shows the wall clock) and with a
// fixed "now", so every baseline is deterministic. Images are file paths at
// runtime; previews use none, so they show the text fallbacks.

private const val NOW = 1_800_000_000_000L

private fun post(
    uri: String,
    name: String,
    text: String,
    minutesAgo: Int,
    likes: Int = 0,
    liked: Boolean = false,
    repostedBy: String? = null,
    images: Int = 0,
    video: Boolean = false,
    warning: Boolean = false,
) = WatchPostUi(
    uri = uri,
    authorName = name,
    authorHandle = "${name.lowercase().replace(' ', '.')}.example.social",
    avatarPath = null,
    text = text,
    createdAtMs = NOW - minutesAgo * 60_000L,
    replyCount = 2,
    repostCount = 1,
    likeCount = likes,
    liked = liked,
    bookmarked = false,
    repostedBy = repostedBy,
    imageCount = images,
    isVideo = video,
    hasContentWarning = warning,
    thumbnailPath = null,
)

private val POSTS =
    persistentListOf(
        post("p1", "Ana Ruiz", "Golden hour from the pier tonight. Worth the cold hands.", 6, likes = 128, liked = true, images = 1),
        post("p2", "Ben Okafor", "Shipped the release. Time for a long walk without a laptop.", 18, likes = 42, repostedBy = "cleo.example.social"),
        post("p3", "Cleo Marsh", "Careful with this one.", 41, warning = true, images = 1),
    )

private val FEEDS =
    persistentListOf(
        WatchFeedTab(key = "following", name = null, updatedAtMs = NOW - 4 * 60_000L),
        WatchFeedTab(key = "uri-photos", name = "Morning Photos", updatedAtMs = NOW - 4 * 60_000L),
    )

private val READY =
    WatchFeedState(status = WatchFeedStatus.Ready, feeds = FEEDS, selectedKey = "following", posts = POSTS)

@PreviewTest
@WearPreviewDevices
@Composable
private fun FeedReadyPreview() {
    NubecitaWearTheme { FeedScreen(state = READY, nowMs = NOW, onPostClick = {}, onSwitchFeed = {}) }
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun FeedReadyFontScalesPreview() {
    NubecitaWearTheme { FeedScreen(state = READY, nowMs = NOW, onPostClick = {}, onSwitchFeed = {}) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun FeedNoFeedsPreview() {
    NubecitaWearTheme { FeedScreen(state = WatchFeedState(status = WatchFeedStatus.NoFeeds), nowMs = NOW, onPostClick = {}, onSwitchFeed = {}) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun FeedPickerPreview() {
    NubecitaWearTheme { FeedPickerScreen(feeds = FEEDS, selectedKey = "following", onSelect = {}) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun PostPreview() {
    NubecitaWearTheme { PostScreen(post = POSTS.first(), nowMs = NOW, onOpenOnPhone = {}) }
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun PostFontScalesPreview() {
    NubecitaWearTheme { PostScreen(post = POSTS[1], nowMs = NOW, onOpenOnPhone = {}) }
}

private val LONG_POST =
    post(
        "long",
        "HEY, SLOW DOWN! 20 IS PLENTY ON OUR NEIGHBORHOOD STREETS",
        "A car went past the school at roughly 31 mph, eleven over the limit, with nobody slowing for the crossing.\n\n" +
            "At that speed about one in three people hit on foot are seriously injured, and more than that at higher speeds. " +
            "Twenty is plenty on streets where people live, walk and cross with their kids every single day.",
        240,
        repostedBy = "a.very.long.handle.for.a.reposter.example.social",
    )

// The author is item 0 and the repost line 1, so this opens mid-post, where
// lines run the full height of the circle.
private const val LONG_POST_BODY_ITEM = 2

@PreviewTest
@WearPreviewDevices
@Composable
private fun PostLongTopPreview() {
    NubecitaWearTheme { PostScreen(post = LONG_POST, nowMs = NOW) }
}

@PreviewTest
@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PostLongBodyPreview() {
    NubecitaWearTheme {
        PostScreen(post = LONG_POST, nowMs = NOW, listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = LONG_POST_BODY_ITEM))
    }
}

@PreviewTest
@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun FeedLongPostPreview() {
    NubecitaWearTheme {
        FeedScreen(state = READY.copy(posts = persistentListOf(LONG_POST)), nowMs = NOW, onPostClick = {}, onSwitchFeed = {})
    }
}

// A circle on a flat fill: a stretched image shows up as an oval, which a
// plain colour block would hide.
private fun previewBitmap(): Bitmap =
    Bitmap.createBitmap(PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).run {
            drawColor(android.graphics.Color.rgb(250, 170, 120))
            drawCircle(PREVIEW_IMAGE_WIDTH / 2f, PREVIEW_IMAGE_HEIGHT / 2f, PREVIEW_IMAGE_HEIGHT / 4f, Paint().apply { color = android.graphics.Color.WHITE })
        }
    }

private const val PREVIEW_IMAGE_WIDTH = 640
private const val PREVIEW_IMAGE_HEIGHT = 360

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun WithPreviewImage(content: @Composable () -> Unit) {
    val image = remember { previewBitmap().asImage() }
    val handler = remember(image) { AsyncImagePreviewHandler { image } }
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides handler, content = content)
}

private val IMAGE_POST = POSTS.first().copy(thumbnailPath = "/preview/image.webp")

// Author, text, image, counts, actions: anchoring on the actions puts the image
// near the top of the screen, where it has to narrow.
private const val IMAGE_POST_ACTIONS_ITEM = 4

@PreviewTest
@PreviewWearRoundDevices
@Composable
private fun PostImageAtBottomPreview() {
    WithPreviewImage { NubecitaWearTheme { PostScreen(post = IMAGE_POST, nowMs = NOW) } }
}

@PreviewTest
@PreviewWearRoundDevices
@Composable
private fun PostImageAtTopPreview() {
    WithPreviewImage {
        NubecitaWearTheme {
            PostScreen(
                post = IMAGE_POST,
                nowMs = NOW,
                actionsAvailable = true,
                listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = IMAGE_POST_ACTIONS_ITEM),
            )
        }
    }
}

@PreviewTest
@PreviewWearRoundDevices
@Composable
private fun FeedImageCardPreview() {
    WithPreviewImage {
        NubecitaWearTheme {
            FeedScreen(state = READY.copy(posts = persistentListOf(IMAGE_POST)), nowMs = NOW, onPostClick = {}, onSwitchFeed = {})
        }
    }
}

// A card taller than the screen: its repost line and counts sit in the narrow
// top and bottom of a round watch. Anchored on the card, where the feed's snap
// stops it.
private val TALL_IMAGE_POST =
    post(
        "tall",
        "depths of wikipedia with a long display name",
        "i can't believe we just let this guy decide what we can end sentences with",
        120,
        likes = 67,
        repostedBy = "jay.edidin.example.social",
        images = 1,
    ).copy(thumbnailPath = "/preview/tall.webp")

private const val FEED_FIRST_POST_ITEM = 1

@PreviewTest
@PreviewWearRoundDevices
@Composable
private fun FeedTallImageCardPreview() {
    WithPreviewImage {
        NubecitaWearTheme {
            FeedScreen(
                state = READY.copy(posts = persistentListOf(TALL_IMAGE_POST)),
                nowMs = NOW,
                onPostClick = {},
                onSwitchFeed = {},
                listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = FEED_FIRST_POST_ITEM),
            )
        }
    }
}

// The action row is the last item (author, repost line, text, media, counts,
// actions → index 5). Anchoring on it centres the row, where a user scrolls it
// to; near the bottom edge the round screen is too narrow for three buttons.
private const val ACTIONS_ITEM = 5

@Composable
private fun PostWithActions(post: WatchPostUi) {
    NubecitaWearTheme {
        PostScreen(
            post = post,
            nowMs = NOW,
            actionsAvailable = true,
            onOpenOnPhone = {},
            listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = ACTIONS_ITEM),
        )
    }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun PostActionsOffPreview() {
    PostWithActions(POSTS[1])
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun PostActionsOnPreview() {
    PostWithActions(POSTS[1].copy(liked = true, reposted = true, bookmarked = true, likeCount = 43, repostCount = 2))
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun PostActionPendingPreview() {
    // Liked a moment ago, no answer from the phone yet: the like toggle is disabled.
    PostWithActions(POSTS[1].copy(liked = true, likePending = true, likeCount = 43))
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun PostActionsFontScalesPreview() {
    PostWithActions(POSTS[1].copy(bookmarked = true))
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun RepostConfirmPreview() {
    NubecitaWearTheme { RepostConfirmContent() }
}

private val POSTING =
    READY.copy(
        postingAvailable = true,
        pendingPosts =
            persistentListOf(
                WatchPendingPostUi("action-1", "Finished my first 10k this morning!", failed = false),
                WatchPendingPostUi("action-2", "Golden hour at the pier again", failed = true),
            ),
    )

@PreviewTest
@WearPreviewDevices
@Composable
private fun FeedPostingPreview() {
    NubecitaWearTheme { FeedScreen(state = POSTING, nowMs = NOW, onPostClick = {}, onSwitchFeed = {}) }
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun FeedPostingFontScalesPreview() {
    NubecitaWearTheme { FeedScreen(state = POSTING, nowMs = NOW, onPostClick = {}, onSwitchFeed = {}) }
}
