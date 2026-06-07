package net.kikin.nubecita.designsystem.component

import androidx.compose.runtime.Stable
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedPostUi

/**
 * Interaction callbacks consumed by [PostCard].
 *
 * Single-data-class callback bag (vs flat lambda parameters on the
 * composable signature) keeps PostCard's parameter list short and lets
 * preview / test call sites construct `PostCallbacks()` with no arguments.
 * Each callback defaults to a no-op so previews stay one-line.
 *
 * Hosts construct ONE `remember`-d instance per screen and pass it to
 * every PostCard in the list, per Compose's stability rules.
 *
 * `@Stable`: data classes with function-typed fields are otherwise
 * marked unstable by the Compose compiler (it can't prove lambda
 * equality), which would defeat skip optimizations on every PostCard
 * inside a `LazyColumn`. Promised contract: `equals` reflects observable
 * behavior — call sites that hold a `PostCallbacks` across recompositions
 * MUST keep the same instance (use `remember { PostCallbacks(...) }`).
 */
@Stable
data class PostCallbacks(
    val onTap: (PostUi) -> Unit = {},
    val onAuthorTap: (AuthorUi) -> Unit = {},
    val onLike: (PostUi) -> Unit = {},
    val onRepost: (PostUi) -> Unit = {},
    /**
     * "Quote post" was selected from the repost cell's menu. `null` (the default)
     * means the host has NOT wired quote-compose — the repost cell then keeps its
     * plain tap-to-toggle behavior and shows no menu (same suppression pattern as
     * [onShareLongPress] / [onQuotedPostTap] / [onOverflowAction], so previews and
     * non-feed call sites don't advertise a no-op action). When wired AND the post
     * permits quoting (`viewer.canViewerQuote`), a single tap on the repost cell
     * opens a Repost/Quote menu and a long-press performs the repost toggle.
     */
    val onQuote: ((PostUi) -> Unit)? = null,
    val onReply: (PostUi) -> Unit = {},
    val onShare: (PostUi) -> Unit = {},
    /**
     * Long-press on the share button. Hosts wire this to a "copy
     * permalink" path (Threads-style). `null` (the default) disables
     * the long-press gesture entirely so PostStat doesn't advertise a
     * long-press action that no-ops — important for previews and
     * non-feed PostCard call sites that pass `PostCallbacks.None`,
     * where TalkBack would otherwise announce a long-press action that
     * does nothing.
     */
    val onShareLongPress: ((PostUi) -> Unit)? = null,
    val onExternalEmbedTap: (uri: String) -> Unit = {},
    /**
     * Tap on the inner quoted-post region of a record / record-with-media
     * embed. `null` (the default) means the host has NOT wired tap-to-open
     * for the quoted card — PostCard.EmbedSlot omits the inner clickable
     * entirely so the gesture falls through to the outer parent `onTap`
     * instead of being consumed by a no-op. Hosts that want the inner
     * tap (Feed, Profile, PostDetail) supply a real lambda.
     */
    val onQuotedPostTap: ((QuotedPostUi) -> Unit)? = null,
    /**
     * One of the overflow-menu entries (Report / Mute / Unmute /
     * Block / Unblock / MuteThread / UnmuteThread / CopyPostText) was
     * selected on [post]'s overflow menu. `null` (the default) suppresses
     * the overflow icon entirely — PostCard does NOT render the 5th
     * action-row affordance when this is unwired. Same suppression
     * pattern as [onShareLongPress] and [onQuotedPostTap] — gives
     * previews and non-feed call sites (e.g. `PostCallbacks.None`) a
     * clean "no extras" rendering without TalkBack advertising a
     * no-op affordance.
     *
     * Hosts that wire this (Feed / Profile / PostDetail / Search)
     * route the action through an MVI event whose effect surfaces a
     * coming-soon snackbar in oftc.2; oftc.3 / .4 / .5 swap each
     * variant's snackbar for the matching RPC call.
     */
    val onOverflowAction: ((post: PostUi, action: PostOverflowAction) -> Unit)? = null,
) {
    public companion object {
        /**
         * Stable no-op singleton. Use as the default value where a PostCard
         * call site is preview / placeholder / non-interactive — re-using
         * this instance is the difference between PostCard skipping
         * recomposition and rebuilding on every parent change.
         *
         * `data class` defaults like `PostCallbacks()` create a new instance
         * (with new lambda identities) per call, so `default == default` is
         * `false`. The singleton fixes that — `None == None` is true, and
         * Compose's stability inference can skip the row.
         */
        public val None: PostCallbacks = PostCallbacks()
    }
}
