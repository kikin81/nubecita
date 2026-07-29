package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.embed.RecordView
import io.github.kikin81.atproto.app.bsky.embed.RecordViewRecord
import io.github.kikin81.atproto.app.bsky.embed.RecordWithMediaView
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ReasonRepost
import io.github.kikin81.atproto.app.bsky.feed.ReplyRef
import net.kikin.nubecita.core.feeds.FeedViewPrefs

/**
 * Whether this feed entry survives the viewer's [FeedViewPrefs] on a
 * follow-scoped feed (the Following timeline and List feeds).
 *
 * **Not applied to feed generators** (Discover / custom feeds). A generator
 * already curates its own output, so filtering replies out of it would remove
 * most of what the algorithm deliberately selected. This mirrors the official
 * client, which applies these tuners to `following` and `list...` descriptors
 * only, never to `feedgen...`.
 *
 * Reposts are exempt from reply filtering (but still subject to
 * [FeedViewPrefs.hideReposts]) — a repost is an explicit endorsement by
 * someone the viewer follows, so the reply-context rules don't apply.
 */
internal fun FeedViewPost.shouldDisplayInFollowingFeed(
    prefs: FeedViewPrefs,
    viewerDid: String?,
): Boolean {
    val isRepost = reason is ReasonRepost
    if (prefs.hideReposts && isRepost) return false
    if (prefs.hideQuotePosts && post.isQuotePost()) return false

    val replyRef = reply ?: return true
    if (isRepost) return true
    if (prefs.hideReplies) return false
    if (!prefs.hideRepliesByUnfollowed) return true

    return shouldDisplayReplyInFollowing(replyRef, post.author, viewerDid)
}

/**
 * Port of `shouldDisplayReplyInFollowing` from the official client
 * (bluesky-social/social-app `src/lib/api/feed-manip.ts`).
 *
 * 1. The reply's own author must be the viewer or someone they follow.
 * 2. A pure self-thread (no distinct parent / grandparent / root author) always
 *    shows.
 * 3. Otherwise at least one distinct ancestor author must also be the viewer or
 *    someone they follow.
 *
 * Step 3 is the part the lexicon prose omits, and the reason a naive "hide
 * replies whose author I don't follow" implementation does NOT fix the reported
 * bug: the offending posts are authored by accounts the viewer follows, replying
 * to strangers. See `nubecita-1fmx`.
 *
 * `parent` / `root` that are `NotFoundPost`, `BlockedPost` or an open-union
 * `Unknown` carry no usable [ProfileViewBasic] and are treated as absent —
 * matching the official client, whose author lookup yields `undefined` for them.
 */
private fun shouldDisplayReplyInFollowing(
    replyRef: ReplyRef,
    author: ProfileViewBasic,
    viewerDid: String?,
): Boolean {
    if (!author.isSelfOrFollowing(viewerDid)) return false

    val ancestors =
        listOfNotNull(
            (replyRef.parent as? PostView)?.author,
            replyRef.grandparentAuthor,
            (replyRef.root as? PostView)?.author,
        ).filter { it.did.raw != author.did.raw }

    // Self-thread: nothing above the reply belongs to anyone else.
    if (ancestors.isEmpty()) return true

    return ancestors.any { it.isSelfOrFollowing(viewerDid) }
}

/**
 * Whether this post quotes another POST.
 *
 * `app.bsky.embed.record#view` is also how embedded feed generators, lists,
 * starter packs and labelers travel, so the embed type alone is not enough —
 * only an inner [RecordViewRecord] is a genuine quote. Treating a shared feed
 * generator as a quote post would hide it under "hide quote posts", which is
 * not what the preference means.
 */
private fun PostView.isQuotePost(): Boolean {
    val record =
        when (val embedded = embed) {
            is RecordView -> embedded.record
            is RecordWithMediaView -> embedded.record.record
            else -> null
        }
    return record is RecordViewRecord
}

/**
 * `viewer.following` is a follow-record URI when the viewer follows this actor
 * and `null` otherwise — the appview hydrates it on every [ProfileViewBasic] in
 * the feed payload, so this check needs no extra fetch and no local follow-graph
 * cache.
 */
private fun ProfileViewBasic.isSelfOrFollowing(viewerDid: String?): Boolean = did.raw == viewerDid || viewer?.following != null
