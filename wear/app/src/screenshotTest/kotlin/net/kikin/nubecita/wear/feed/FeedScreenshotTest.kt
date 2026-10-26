package net.kikin.nubecita.wear.feed

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
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
