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
    val onQuotedPostTap: (QuotedPostUi) -> Unit = {},
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
