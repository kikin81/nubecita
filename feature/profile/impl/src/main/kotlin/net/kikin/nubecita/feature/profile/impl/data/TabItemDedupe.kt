package net.kikin.nubecita.feature.profile.impl.data

import net.kikin.nubecita.feature.profile.impl.TabItemUi

/**
 * Drops [TabItemUi] entries whose [TabItemUi.postUri] has already appeared
 * earlier in the list, keeping the first occurrence.
 *
 * The renderer's `LazyColumn` uses [TabItemUi.postUri] as the slot key,
 * and Compose throws `IllegalArgumentException: Key … was already used`
 * on duplicates — which crashes the profile mid-scroll if a duplicate
 * slot scrolls into view.
 *
 * Scenarios that surface duplicates:
 * - A user reposts the same post twice (rare, but valid in the wire).
 * - Pagination cursor-resync overlap: the server returns a post at the
 *   head of a new page that we already have at the tail of the existing
 *   slice.
 */
fun List<TabItemUi>.dedupeByPostUri(): List<TabItemUi> {
    if (size < 2) return this
    val seen = HashSet<String>(size)
    return filter { item -> seen.add(item.postUri) }
}
