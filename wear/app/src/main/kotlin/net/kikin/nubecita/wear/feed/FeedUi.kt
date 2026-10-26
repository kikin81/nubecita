package net.kikin.nubecita.wear.feed

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import net.kikin.nubecita.wear.R
import java.io.File

/** The current time, ticking once a minute (enough for "5 min ago"). */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(MINUTE_MS)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** "5 min. ago", localized by the platform. */
fun relativeTime(
    timeMs: Long,
    nowMs: Long,
): String =
    DateUtils
        .getRelativeTimeSpanString(timeMs, maxOf(timeMs, nowMs), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        .toString()

/** A feed's display name: Following is localized, custom feeds and lists use their own name. */
@Composable
fun WatchFeedTab.label(): String = name ?: stringResource(R.string.wear_feed_following)

/** Author avatar, or nothing when the phone sent none (the name carries the row). */
@Composable
fun AuthorAvatar(
    path: String?,
    modifier: Modifier = Modifier,
) {
    if (path == null) return
    AsyncImage(
        model = File(path),
        contentDescription = null, // decorative: the author's name is next to it
        modifier = modifier.size(24.dp).clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}

/** Author line: avatar, name and time. */
@Composable
fun AuthorRow(
    post: WatchPostUi,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AuthorAvatar(post.avatarPath)
        Column(modifier = Modifier.weight(1f)) {
            Text(post.authorName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                relativeTime(post.createdAtMs, nowMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The post's media: a thumbnail, or a line saying what's there and why it isn't shown. */
@Composable
fun PostMedia(
    post: WatchPostUi,
    imageHeight: Int,
    modifier: Modifier = Modifier,
) {
    when {
        post.thumbnailPath != null ->
            AsyncImage(
                model = File(post.thumbnailPath),
                contentDescription = stringResource(if (post.isVideo) R.string.wear_post_video else R.string.wear_post_image),
                modifier = modifier.fillMaxWidth().height(imageHeight.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        post.hasContentWarning -> MediaLabel(stringResource(R.string.wear_post_sensitive_media), modifier)
        post.isVideo -> MediaLabel(stringResource(R.string.wear_post_video), modifier)
        post.imageCount > 0 -> MediaLabel(pluralStringResource(R.plurals.wear_post_images, post.imageCount, post.imageCount), modifier)
    }
}

@Composable
private fun MediaLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Reply / repost / like counts, read aloud as one phrase. */
@Composable
fun PostCounts(
    post: WatchPostUi,
    modifier: Modifier = Modifier,
) {
    val description =
        listOf(
            pluralStringResource(R.plurals.wear_post_replies, post.replyCount, post.replyCount),
            pluralStringResource(R.plurals.wear_post_reposts, post.repostCount, post.repostCount),
            pluralStringResource(R.plurals.wear_post_likes, post.likeCount, post.likeCount),
        ).joinToString(", ")
    Text(
        text = "↩ ${post.replyCount}   ↻ ${post.repostCount}   ${if (post.liked) "♥" else "♡"} ${post.likeCount}",
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        style = MaterialTheme.typography.labelSmall,
        color = if (post.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val MINUTE_MS = 60_000L

/**
 * The `bsky.app` link for a post's AT-URI (`at://<did>/app.bsky.feed.post/<rkey>`),
 * which Nubecita on the phone opens through its App Links. Null for anything
 * that isn't a post URI.
 */
fun bskyPostUrl(postUri: String): String? {
    val parts = postUri.removePrefix("at://").split('/')
    if (!postUri.startsWith("at://") || parts.size != 3 || parts[1] != "app.bsky.feed.post") return null
    val (did, _, rkey) = parts
    return if (did.isEmpty() || rkey.isEmpty()) null else "https://bsky.app/profile/$did/post/$rkey"
}
