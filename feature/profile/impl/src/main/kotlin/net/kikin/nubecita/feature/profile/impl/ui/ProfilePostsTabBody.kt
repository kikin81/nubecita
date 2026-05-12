package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.TabLoadStatus

/**
 * Contributes the Posts-tab body items to the enclosing LazyColumn —
 * an extension on [LazyListScope] (rather than a Composable that
 * opens its own LazyColumn) so the hero, sticky pill tabs, and
 * posts share one scroll surface. Nested LazyColumns break sticky
 * headers, so this shape is the only one that satisfies the design.
 *
 * Branches on [status]:
 * - [TabLoadStatus.Idle], [TabLoadStatus.InitialLoading] → loading skeletons
 * - [TabLoadStatus.InitialError] → error state + Retry
 * - [TabLoadStatus.Loaded] with empty items → empty state
 * - [TabLoadStatus.Loaded] with items → PostCards + optional appending row
 */
internal fun LazyListScope.profilePostsTabBody(
    status: TabLoadStatus,
    callbacks: PostCallbacks,
    onRetry: () -> Unit,
) {
    when (status) {
        TabLoadStatus.Idle,
        TabLoadStatus.InitialLoading,
        -> {
            item(key = "posts-loading", contentType = "loading") {
                ProfileLoadingState()
            }
        }
        is TabLoadStatus.InitialError -> {
            item(key = "posts-error", contentType = "error") {
                ProfileErrorState(error = status.error, onRetry = onRetry)
            }
        }
        is TabLoadStatus.Loaded -> {
            if (status.items.isEmpty()) {
                item(key = "posts-empty", contentType = "empty") {
                    ProfileEmptyState(tab = ProfileTab.Posts)
                }
            } else {
                items(
                    items = status.items,
                    key = { it.postUri },
                    contentType = { item ->
                        when (item) {
                            is TabItemUi.Post -> "post"
                            is TabItemUi.MediaCell -> "media" // unreachable for Posts tab; defensive
                        }
                    },
                ) { item ->
                    when (item) {
                        is TabItemUi.Post -> PostCard(post = item.post, callbacks = callbacks)
                        is TabItemUi.MediaCell -> {
                            // Posts filter (posts_no_replies) never yields a MediaCell.
                            // Branch exists for type completeness; renders nothing.
                        }
                    }
                }
                if (status.isAppending) {
                    item(key = "posts-appending", contentType = "appending") {
                        ProfileAppendingIndicator()
                    }
                }
            }
        }
    }
}
