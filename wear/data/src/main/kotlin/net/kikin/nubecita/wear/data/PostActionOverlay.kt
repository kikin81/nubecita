package net.kikin.nubecita.wear.data

import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearPostAction
import net.kikin.nubecita.wear.data.db.WatchPendingActionEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity

/** The three things the watch can toggle on a post. */
enum class PostToggle { LIKE, REPOST, BOOKMARK }

/** A post action's kind, stored by its [wire] name; [on] is false for an undo. */
enum class PostActionKind(
    val wire: String,
    val toggle: PostToggle,
    val on: Boolean,
) {
    LIKE("like", PostToggle.LIKE, true),
    UNLIKE("unlike", PostToggle.LIKE, false),
    REPOST("repost", PostToggle.REPOST, true),
    UNREPOST("unrepost", PostToggle.REPOST, false),
    BOOKMARK("bookmark", PostToggle.BOOKMARK, true),
    UNBOOKMARK("unbookmark", PostToggle.BOOKMARK, false),
    ;

    companion object {
        fun fromWire(wire: String): PostActionKind? = entries.firstOrNull { it.wire == wire }

        fun of(action: WearPostAction): PostActionKind =
            when (action) {
                is WearAction.Like -> LIKE
                is WearAction.Unlike -> UNLIKE
                is WearAction.Repost -> REPOST
                is WearAction.Unrepost -> UNREPOST
                is WearAction.Bookmark -> BOOKMARK
                is WearAction.Unbookmark -> UNBOOKMARK
            }
    }
}

/**
 * The viewer's state on a post as the watch shows it: the synced state with
 * the user's own pending and confirmed actions on top.
 *
 * `*Pending` is true while an action on that toggle has no answer yet; the UI
 * ignores further taps on it until then, so an undo always has the record it
 * needs. The counts move by one when the overlay disagrees with the snapshot.
 */
data class EffectiveViewer(
    val liked: Boolean,
    val likeUri: String?,
    val likePending: Boolean,
    val reposted: Boolean,
    val repostUri: String?,
    val repostPending: Boolean,
    val bookmarked: Boolean,
    val bookmarkPending: Boolean,
    val likeCount: Int,
    val repostCount: Int,
)

/**
 * Applies [actions] (this post's, in any order) to [post]. Per toggle, the
 * newest action decides the state; a like or repost's record comes from its
 * ok result, falling back to the synced one.
 */
fun effectiveViewer(
    post: WatchPostEntity,
    actions: List<WatchPendingActionEntity>,
): EffectiveViewer {
    val byToggle =
        actions
            .mapNotNull { a -> PostActionKind.fromWire(a.kind)?.let { it to a } }
            .sortedBy { (_, a) -> a.createdAtMs }
            .groupBy { (kind, _) -> kind.toggle }

    fun latest(toggle: PostToggle) = byToggle[toggle]?.lastOrNull()

    fun pending(toggle: PostToggle) = byToggle[toggle].orEmpty().any { (_, a) -> !a.confirmed }

    val like = latest(PostToggle.LIKE)
    val liked = like?.first?.on ?: (post.likeUri != null)
    val likeUri = if (liked) like?.second?.recordUri ?: post.likeUri else null

    val repost = latest(PostToggle.REPOST)
    val reposted = repost?.first?.on ?: (post.repostUri != null)
    val repostUri = if (reposted) repost?.second?.recordUri ?: post.repostUri else null

    val bookmarked = latest(PostToggle.BOOKMARK)?.first?.on ?: post.bookmarked

    fun Boolean.toInt() = if (this) 1 else 0

    return EffectiveViewer(
        liked = liked,
        likeUri = likeUri,
        likePending = pending(PostToggle.LIKE),
        reposted = reposted,
        repostUri = repostUri,
        repostPending = pending(PostToggle.REPOST),
        bookmarked = bookmarked,
        bookmarkPending = pending(PostToggle.BOOKMARK),
        likeCount = (post.likeCount + liked.toInt() - (post.likeUri != null).toInt()).coerceAtLeast(0),
        repostCount = (post.repostCount + reposted.toInt() - (post.repostUri != null).toInt()).coerceAtLeast(0),
    )
}

/**
 * The confirmed actions that can be dropped after [posts] were stored: those
 * the snapshot already shows, and any older than [maxAgeMs] (a post that left
 * every synced feed never confirms them). Unanswered actions are never
 * dropped: they're waiting for the phone.
 */
fun settledActions(
    posts: List<WatchPostEntity>,
    actions: List<WatchPendingActionEntity>,
    nowMs: Long,
    maxAgeMs: Long = SETTLE_MAX_AGE_MS,
): List<String> {
    val byUri = posts.associateBy { it.uri }
    return actions
        .filter { it.confirmed }
        .filter { action ->
            val stale = nowMs - (action.completedAtMs ?: action.createdAtMs) > maxAgeMs
            val kind = PostActionKind.fromWire(action.kind)
            val post = byUri[action.postUri]
            val shown =
                kind != null &&
                    post != null &&
                    when (kind.toggle) {
                        PostToggle.LIKE -> (post.likeUri != null) == kind.on
                        PostToggle.REPOST -> (post.repostUri != null) == kind.on
                        PostToggle.BOOKMARK -> post.bookmarked == kind.on
                    }
            stale || kind == null || shown
        }.map { it.actionId }
}

/** A confirmed action whose post never shows up again is dropped after a day. */
const val SETTLE_MAX_AGE_MS = 24 * 60 * 60_000L
