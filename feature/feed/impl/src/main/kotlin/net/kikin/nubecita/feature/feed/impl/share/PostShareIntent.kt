package net.kikin.nubecita.feature.feed.impl.share

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.data.models.PostUi

/**
 * Pre-computed share payload for a [PostUi].
 *
 * Built in the VM layer so the UI side stays free of AT-URI parsing and
 * the test surface (`PostUi → PostShareIntent`) is a pure-Kotlin
 * function. The screen consumes this via [FeedEffect.SharePost]
 * (single tap → system share sheet) or reads [permalink] only via
 * [FeedEffect.CopyPermalink] (long press → clipboard).
 *
 * `text` is what goes into `Intent.EXTRA_TEXT` for the system share
 * sheet. We keep it as **just the permalink** because bsky.app links
 * carry rich OG previews on every modern target (Slack, iMessage,
 * Discord, Twitter, etc.) — packing the post body alongside the URL
 * would just duplicate what the preview already shows.
 */
@Immutable
data class PostShareIntent(
    val permalink: String,
    val text: String,
)

/**
 * Build a [PostShareIntent] from a feed [PostUi].
 *
 * Permalink shape: `https://bsky.app/profile/<handle>/post/<rkey>`
 * matching what bsky.app uses publicly. Handle is preferred over DID
 * for human-readability — it can break if the user changes handles, but
 * that's rare and the published web client does the same.
 *
 * `rkey` is extracted from [PostUi.id], which the feed mapper sets to
 * the post's AT-URI raw form
 * (`at://did:plc:.../app.bsky.feed.post/<rkey>`). `substringAfterLast`
 * tolerates the (unexpected) case where the id isn't a slash-bearing
 * URI by returning the whole string — better to ship a slightly-
 * malformed permalink than to crash on share.
 */
fun PostUi.toShareIntent(): PostShareIntent {
    val rkey = id.substringAfterLast('/')
    val permalink = "https://bsky.app/profile/${author.handle}/post/$rkey"
    return PostShareIntent(permalink = permalink, text = permalink)
}
