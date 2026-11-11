package net.kikin.nubecita.core.posttranslation.ui

import androidx.compose.foundation.lazy.LazyListState
import net.kikin.nubecita.data.models.PostUi

/**
 * The posts behind this list's visible items, for a list whose post items
 * are keyed by the post's uri (`items(posts, key = { it.id })`).
 *
 * The usual `visiblePosts` argument to [rememberPostTranslations]. By key,
 * not index, so headers, sort rows, loading rows and anything else in the
 * list that isn't a post neither shift the mapping nor get translated. A list
 * keyed some other way (post detail prefixes its keys by role) maps its own.
 */
fun LazyListState.visiblePostsById(posts: List<PostUi>): List<PostUi> = visiblePostsById(layoutInfo.visibleItemsInfo.map { it.key }, posts)

/** [LazyListState.visiblePostsById] over plain keys, so it can be tested without a list. */
internal fun visiblePostsById(
    visibleKeys: List<Any>,
    posts: List<PostUi>,
): List<PostUi> {
    if (visibleKeys.isEmpty() || posts.isEmpty()) return emptyList()
    val byId = posts.associateBy { it.id }
    return visibleKeys.mapNotNull { key -> byId[key] }
}
