package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.quotedRecord
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.designsystem.component.VideoPosterEmbed
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.TabLoadStatus

/**
 * Contributes the Posts or Replies tab body items to the enclosing
 * LazyColumn — an extension on [LazyListScope] (rather than a Composable
 * that opens its own LazyColumn) so the hero, sticky pill tabs, and feed
 * items share one scroll surface. Nested LazyColumns break sticky headers,
 * so this shape is the only one that satisfies the design.
 *
 * Generalized from Bead D's `profilePostsTabBody`: the [tab] parameter
 * drives the per-item LazyColumn keys (`posts-loading` vs `replies-loading`)
 * and the empty-state copy via [ProfileEmptyState]. Posts and Replies
 * share this implementation; the Media tab has its own
 * [profileMediaTabBody] because it row-packs into a 3-col grid.
 *
 * Branches on [status]:
 * - [TabLoadStatus.Idle], [TabLoadStatus.InitialLoading] → loading skeletons
 * - [TabLoadStatus.InitialError] → error state + Retry
 * - [TabLoadStatus.Loaded] with empty items → empty state
 * - [TabLoadStatus.Loaded] with items → PostCards + optional appending row
 *
 * Video embeds inside a rendered `PostCard` are routed through
 * [onVideoTap] — image-equivalent of [onImageTap] but with a single
 * URI payload because the fullscreen player handles only one video at
 * a time. Parent-video taps fire with the parent post's URI; quoted-
 * video taps fire with the quoted post's URI so the resolver fetches
 * the right `app.bsky.embed.video#view`.
 */
internal fun LazyListScope.profileFeedTabBody(
    tab: ProfileTab,
    status: TabLoadStatus,
    callbacks: PostCallbacks,
    onImageTap: (post: PostUi, imageIndex: Int) -> Unit,
    onVideoTap: (postUri: String) -> Unit,
    onRetry: () -> Unit,
    lastLikeTapPostUri: String? = null,
    lastRepostTapPostUri: String? = null,
) {
    val keyPrefix =
        when (tab) {
            ProfileTab.Posts -> "posts"
            ProfileTab.Replies -> "replies"
            // Media has its own body in [profileMediaTabBody]; defensive prefix.
            ProfileTab.Media -> "posts"
        }
    when (status) {
        TabLoadStatus.Idle,
        TabLoadStatus.InitialLoading,
        -> {
            item(key = "$keyPrefix-loading", contentType = "loading") {
                ProfileLoadingState()
            }
        }
        is TabLoadStatus.InitialError -> {
            item(key = "$keyPrefix-error", contentType = "error") {
                ProfileErrorState(error = status.error, onRetry = onRetry)
            }
        }
        is TabLoadStatus.Loaded -> {
            if (status.items.isEmpty()) {
                item(key = "$keyPrefix-empty", contentType = "empty") {
                    ProfileEmptyState(tab = tab)
                }
            } else {
                items(
                    items = status.items,
                    key = { it.postUri },
                    contentType = { item ->
                        when (item) {
                            is TabItemUi.Post -> "post"
                            is TabItemUi.MediaCell -> "media" // unreachable for Posts/Replies; defensive
                        }
                    },
                ) { item ->
                    when (item) {
                        is TabItemUi.Post -> {
                            // Hoist the video slots inside a remember keyed on
                            // the post URI + the tap lambda so the per-item
                            // closures stay stable across recompositions —
                            // same shape as FeedScreen's slot wiring.
                            val parentPostUri = item.post.id
                            val videoSlot: @Composable (EmbedUi.Video) -> Unit =
                                remember(parentPostUri, onVideoTap) {
                                    val tap = { onVideoTap(parentPostUri) }
                                    val slot: @Composable (EmbedUi.Video) -> Unit = { video ->
                                        VideoPosterEmbed(
                                            posterUrl = video.posterUrl,
                                            aspectRatio = video.aspectRatio,
                                            altText = video.altText,
                                            onTap = tap,
                                        )
                                    }
                                    slot
                                }
                            // Quoted-video slot binds identity to the quoted
                            // post's URI so the resolver fetches the right
                            // embed when the user taps a quoted video.
                            val quotedVideoUri =
                                item.post.embed.quotedRecord
                                    ?.uri
                            val quotedVideoSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? =
                                remember(quotedVideoUri, onVideoTap) {
                                    if (quotedVideoUri == null) {
                                        null
                                    } else {
                                        val tap = { onVideoTap(quotedVideoUri) }
                                        val slot: @Composable (QuotedEmbedUi.Video) -> Unit = { qVideo ->
                                            VideoPosterEmbed(
                                                posterUrl = qVideo.posterUrl,
                                                aspectRatio = qVideo.aspectRatio,
                                                altText = qVideo.altText,
                                                onTap = tap,
                                            )
                                        }
                                        slot
                                    }
                                }
                            PostCard(
                                post = item.post,
                                callbacks = callbacks,
                                onImageClick = { idx -> onImageTap(item.post, idx) },
                                videoEmbedSlot = videoSlot,
                                quotedVideoEmbedSlot = quotedVideoSlot,
                                animateLikeTap = item.post.id == lastLikeTapPostUri,
                                animateRepostTap = item.post.id == lastRepostTapPostUri,
                            )
                        }
                        is TabItemUi.MediaCell -> {
                            // Posts/Replies filter never yields a MediaCell.
                            // Branch exists for type completeness; renders nothing.
                        }
                    }
                }
                if (status.isAppending) {
                    item(key = "$keyPrefix-appending", contentType = "appending") {
                        ProfileAppendingIndicator()
                    }
                }
            }
        }
    }
}
