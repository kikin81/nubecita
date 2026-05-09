package net.kikin.nubecita.feature.composer.impl.internal

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * Pure ordering rule for [LanguagePickerContent]'s `LazyColumn`.
 * Exposed `internal` so the JVM unit test can pin the contract
 * without booting the Compose runtime.
 *
 * Order:
 *
 * 1. Currently-selected tags (alphabetical among themselves by
 *    localized display name).
 * 2. The device-locale tag (its bare language portion), if not
 *    selected.
 * 3. Everything else, alphabetical by localized display name in the
 *    JVM default locale.
 *
 * Selection-pinning is what makes re-opens predictable: tags the
 * user just confirmed stay at the top of the next open's list.
 */
internal fun sortPickerTags(
    allTags: ImmutableList<String>,
    draftSelection: ImmutableList<String>,
    deviceLocaleTag: String,
): ImmutableList<String> {
    val deviceLang = Locale.forLanguageTag(deviceLocaleTag).language
    val selectedSet = draftSelection.toSet()
    val sorted = allTags.sortedBy { languageDisplayName(it) }
    val (selected, rest) = sorted.partition { it in selectedSet }
    val (deviceFirst, others) = rest.partition { it == deviceLang }
    return (selected + deviceFirst + others).toImmutableList()
}

/**
 * Pure search-match predicate for [LanguagePickerContent]. A [tag]
 * matches a non-blank [query] when either:
 *
 * - The bare BCP-47 [tag] (lowercased) contains the [query]
 *   (lowercased) — so `"en"` matches `"en"`, `"en-US"` etc.
 * - The localized display name of the [tag] (lowercased) contains
 *   the [query] (lowercased) — so on a French JVM `"anglais"`
 *   matches English.
 *
 * A blank or empty [query] matches every tag — the caller is expected
 * to short-circuit and return the unfiltered list, but defending the
 * predicate against blank input keeps it composable in folds.
 */
internal fun matchesPickerQuery(
    tag: String,
    query: String,
): Boolean {
    val normalized = query.trim().lowercase(Locale.getDefault())
    if (normalized.isEmpty()) return true
    return tag.lowercase(Locale.getDefault()).contains(normalized) ||
        languageDisplayName(tag).lowercase(Locale.getDefault()).contains(normalized)
}
