package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
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
 */
internal fun LazyListScope.profileFeedTabBody(
    tab: ProfileTab,
    status: TabLoadStatus,
    callbacks: PostCallbacks,
    onImageTap: (post: PostUi, imageIndex: Int) -> Unit,
    onRetry: () -> Unit,
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
                        is TabItemUi.Post ->
                            PostCard(
                                post = item.post,
                                callbacks = callbacks,
                                onImageClick = { idx -> onImageTap(item.post, idx) },
                            )
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
