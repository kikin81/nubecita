package net.kikin.nubecita.feature.profile.impl.data

import net.kikin.nubecita.feature.profile.impl.TabItemUi

/**
 * Drops [TabItemUi] entries whose [TabItemUi.key] has already appeared
 * earlier in the list, keeping the first occurrence.
 *
 * The renderer's `LazyColumn` uses [TabItemUi.key] as the slot key, and
 * Compose throws `IllegalArgumentException: Key … was already used` on
 * duplicates — which crashes the profile mid-scroll if a duplicate slot
 * scrolls into view.
 *
 * Because [TabItemUi.key] folds the reposter DID into the key, an
 * author's original post and their own repost of it have *distinct* keys
 * and both survive — they render side by side, matching bsky.app. The
 * only collisions this drops are genuine duplicate entries: the same post
 * AND the same reason re-sent across a pagination cursor boundary (the
 * server returns a post at the head of a new page that we already hold at
 * the tail of the prior slice).
 *
 * Pure O(n) — one pass with a `HashSet` of seen keys.
 */
fun List<TabItemUi>.dedupeByKey(): List<TabItemUi> {
    if (size < 2) return this
    val seen = HashSet<String>(size)
    return filter { item -> seen.add(item.key) }
}
