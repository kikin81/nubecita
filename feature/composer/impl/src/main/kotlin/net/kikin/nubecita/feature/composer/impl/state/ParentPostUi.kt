package net.kikin.nubecita.feature.composer.impl.state

import io.github.kikin81.atproto.com.atproto.repo.StrongRef

/**
 * Resolved parent post for reply-mode rendering + reply-ref
 * construction. Carried inside [ParentLoadStatus.Loaded] once the
 * `app.bsky.feed.getPostThread` fetch completes.
 *
 * The two `StrongRef`s are required to construct the eventual
 * `Post.reply` field at submit time:
 * - [parentRef] = the post being replied to (always populated to the
 *   target's own URI + CID).
 * - [rootRef] = the originating post of the thread. When the target
 *   is itself a reply, inherits from `target.record.reply.root`.
 *   When the target IS the thread root, equals [parentRef].
 *
 * Display fields ([authorHandle], [authorDisplayName], [text]) are
 * here only so the composer's parent-post card (rendered in wtq.6's
 * UI work) has what it needs. The skeleton VM in wtq.3 only stores
 * them; the screen Composable is what consumes them.
 */
data class ParentPostUi(
    val parentRef: StrongRef,
    val rootRef: StrongRef,
    val authorHandle: String,
    val authorDisplayName: String?,
    val text: String,
    /** Author avatar URL for the context card, or `null` when the author has none. */
    val avatarUrl: String? = null,
    /**
     * Thumbnail URL of the parent's first image embed, or `null` when it has no
     * image media. Rendered as a small trailing thumbnail on the context card.
     */
    val thumbnailUrl: String? = null,
    /**
     * Whether the viewer is allowed to reply to this parent — the inverse of the
     * post viewer's `replyDisabled` (the appview's server-computed threadgate
     * result). Defaults to `true` (fail open). The composer checks this at launch
     * and short-circuits with a snackbar if the gate changed after the user
     * tapped reply (the PostCard CTA is the primary gate; this is defensive).
     */
    val canViewerReply: Boolean = true,
)
