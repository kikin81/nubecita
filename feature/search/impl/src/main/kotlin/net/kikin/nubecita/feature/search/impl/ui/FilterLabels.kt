package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.feature.search.impl.data.DateFilter
import net.kikin.nubecita.feature.search.impl.data.MediaFilter
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import java.util.Locale

/**
 * What each chip in the filter row shows, derived from [PostSearchFilters].
 * Pure, so the rules (which chip is selected, what value it shows in place)
 * are unit-tested without a Compose runtime. The row maps these to strings
 * and styling.
 *
 * [Video][videoSelected] on implies [Media][mediaSelected] on, because both
 * drive the one `media` field and video-only results are media results.
 */
@Immutable
internal data class FilterChipState(
    /** Number shown on the sheet control; [PostSearchFilters.activeCount]. */
    val activeCount: Int,
    val sortSelected: Boolean,
    val followingSelected: Boolean,
    val mediaSelected: Boolean,
    val videoSelected: Boolean,
    /** The language's display name in the display locale, or null when any. */
    val languageName: String?,
    val date: DateFilter,
) {
    val languageSelected: Boolean get() = languageName != null
    val dateSelected: Boolean get() = date != DateFilter.AllTime
}

internal fun filterChipState(
    filters: PostSearchFilters,
    displayLocale: Locale,
): FilterChipState =
    FilterChipState(
        activeCount = filters.activeCount,
        sortSelected = filters.sort != SearchPostsSort.TOP,
        followingSelected = filters.followingOnly,
        mediaSelected = filters.media != MediaFilter.ANY,
        videoSelected = filters.media == MediaFilter.VIDEO_ONLY,
        languageName = filters.language?.let { languageDisplayName(it, displayLocale) },
        date = filters.date,
    )

/** The Media chip: on means [MediaFilter.HAS_MEDIA]; off from any media state returns to any. */
internal fun PostSearchFilters.toggleMedia(): PostSearchFilters = copy(media = if (media == MediaFilter.ANY) MediaFilter.HAS_MEDIA else MediaFilter.ANY)

/** The Video chip: on means [MediaFilter.VIDEO_ONLY]; off returns to any. */
internal fun PostSearchFilters.toggleVideo(): PostSearchFilters = copy(media = if (media == MediaFilter.VIDEO_ONLY) MediaFilter.ANY else MediaFilter.VIDEO_ONLY)

/** A language the user can pick: BCP-47 [code] and its [label] in the display locale. */
@Immutable
internal data class LanguageChoice(
    val code: String,
    val label: String,
)

/**
 * The languages offered besides "Any": one entry per language code among the
 * device's locales (`en-US` and `en-GB` are both `en`), in device order, plus
 * [selected] when it is not already there so the current choice is always
 * visible. No network and no preference involved.
 */
internal fun languageChoices(
    deviceLocales: List<Locale>,
    selected: String?,
    displayLocale: Locale,
): ImmutableList<LanguageChoice> {
    val codes = LinkedHashSet<String>()
    deviceLocales.forEach { locale -> locale.language.takeIf { it.isNotBlank() }?.let(codes::add) }
    selected?.takeIf { it.isNotBlank() }?.let(codes::add)
    return codes.map { LanguageChoice(code = it, label = languageDisplayName(it, displayLocale)) }.toImmutableList()
}

private fun languageDisplayName(
    code: String,
    displayLocale: Locale,
): String {
    val name = Locale.forLanguageTag(code).getDisplayLanguage(displayLocale)
    return name.ifBlank { code }.replaceFirstChar { it.titlecase(displayLocale) }
}

/**
 * The count worth showing: positive only. The server's figure is an estimate
 * and zero would read "about 0 posts", so null and zero both mean "show nothing".
 */
internal fun shownHitsTotal(hitsTotal: Long?): Long? = hitsTotal?.takeIf { it > 0 }
