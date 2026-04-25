package net.kikin.nubecita.designsystem.component

import androidx.compose.runtime.Stable
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.PostUi

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
)
