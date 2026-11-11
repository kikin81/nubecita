package net.kikin.nubecita.feature.postdetail.impl

import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem

/**
 * The thread posts behind the list items currently on screen, in order.
 *
 * By item **key**, not index: the thread's list interleaves rows that aren't
 * thread posts (outgoing replies under their parent, blocked and not-found
 * tombstones, folds), so an index into [items] would drift by every such row
 * above it and translate the wrong posts. Rows that aren't an ancestor, the
 * focus or a reply contribute nothing; they have no text to translate.
 */
internal fun visibleThreadPosts(
    visibleKeys: List<Any>,
    items: List<ThreadItem>,
): List<PostUi> {
    if (visibleKeys.isEmpty()) return emptyList()
    val byKey = items.associateBy { it.key }
    return visibleKeys.mapNotNull { key ->
        when (val item = byKey[key]) {
            is ThreadItem.Ancestor -> item.post
            is ThreadItem.Focus -> item.post
            is ThreadItem.Reply -> item.post
            else -> null
        }
    }
}
