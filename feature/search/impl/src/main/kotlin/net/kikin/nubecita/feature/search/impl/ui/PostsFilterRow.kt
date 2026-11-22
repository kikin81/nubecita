package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.DateFilter
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The filter row and, under it, the estimated result count. Sits at the top of
 * the Posts tab above every body (loading, results, empty, error) so a filter
 * can always be changed or cleared.
 *
 * The count line is plain text, not a live region: it changes with every
 * refetch and announcing each change would be noise.
 */
@Composable
internal fun PostsFilterHeader(
    filters: PostSearchFilters,
    hitsTotal: Long?,
    onFiltersChange: (PostSearchFilters) -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PostsFilterRow(
            filters = filters,
            onFiltersChange = onFiltersChange,
            onOpenSheet = onOpenSheet,
        )
        shownHitsTotal(hitsTotal)?.let { PostsResultCount(hitsTotal = it) }
    }
}

/** "About 1.2K posts"; the caller omits it when the server gave no estimate. */
@Composable
internal fun PostsResultCount(
    hitsTotal: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.search_posts_hits_total, rememberCompactCount(hitsTotal)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.s4)
                .padding(bottom = MaterialTheme.spacing.s2),
    )
}

/**
 * One scrolling row of filter chips, in a fixed order that never changes:
 * sheet (`☰ n`), Sort menu, Following, Media, Video, Language, Date.
 *
 * An active chip is selected, and a chip with a value shows it in place
 * (`Language` becomes `English`) so chips never move under the finger. Chips
 * never truncate their label: long es-419 / pt-BR labels just make the row
 * scroll further.
 *
 * Touch targets: M3's `FilterChip` already enforces a 48dp minimum interactive
 * size, so the visually 32dp chips are 48dp to touch.
 */
@Composable
internal fun PostsFilterRow(
    filters: PostSearchFilters,
    onFiltersChange: (PostSearchFilters) -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayLocale = LocalLocale.current.platformLocale
    val chips = remember(filters, displayLocale) { filterChipState(filters, displayLocale) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.spacing.s4,
                    vertical = MaterialTheme.spacing.s2,
                ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
    ) {
        SheetChip(activeCount = chips.activeCount, onClick = onOpenSheet)
        SortChip(
            sort = filters.sort,
            selected = chips.sortSelected,
            onSortChange = { onFiltersChange(filters.copy(sort = it)) },
        )
        FilterChip(
            selected = chips.followingSelected,
            onClick = { onFiltersChange(filters.copy(followingOnly = !filters.followingOnly)) },
            label = { Text(stringResource(R.string.search_filter_following), maxLines = 1) },
        )
        FilterChip(
            selected = chips.mediaSelected,
            onClick = { onFiltersChange(filters.toggleMedia()) },
            label = { Text(stringResource(R.string.search_filter_media), maxLines = 1) },
        )
        FilterChip(
            selected = chips.videoSelected,
            onClick = { onFiltersChange(filters.toggleVideo()) },
            label = { Text(stringResource(R.string.search_filter_video), maxLines = 1) },
        )
        LanguageChip(
            language = filters.language,
            languageName = chips.languageName,
            onLanguageChange = { onFiltersChange(filters.copy(language = it)) },
        )
        DateChip(
            date = filters.date,
            selected = chips.dateSelected,
            onDateChange = { onFiltersChange(filters.copy(date = it)) },
        )
    }
}

@Composable
private fun SheetChip(
    activeCount: Int,
    onClick: () -> Unit,
) {
    val description =
        if (activeCount > 0) {
            pluralStringResource(R.plurals.search_filter_row_sheet_description_active, activeCount, activeCount)
        } else {
            stringResource(R.string.search_filter_row_sheet_description)
        }
    FilterChip(
        selected = activeCount > 0,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
        label = {
            // The count is part of the chip's visual label only when something is active.
            if (activeCount > 0) Text(activeCount.toString(), maxLines = 1)
        },
        leadingIcon = { NubecitaIcon(name = NubecitaIconName.Menu, contentDescription = null, opticalSize = CHIP_ICON_SIZE) },
    )
}

@Composable
private fun SortChip(
    sort: SearchPostsSort,
    selected: Boolean,
    onSortChange: (SearchPostsSort) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = sortLabel(sort)
    val description = stringResource(R.string.search_filter_sort_description, label)
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { DropdownArrow() },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SearchPostsSort.entries.forEach { option ->
                ChoiceMenuItem(
                    label = sortLabel(option),
                    selected = option == sort,
                    onClick = {
                        expanded = false
                        onSortChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(
    language: String?,
    languageName: String?,
    onLanguageChange: (String?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val choices = rememberLanguageChoices(selected = language)
    val label = languageName ?: stringResource(R.string.search_filter_language)
    val description =
        languageName?.let { stringResource(R.string.search_filter_language_description, it) } ?: label
    Box {
        FilterChip(
            selected = languageName != null,
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { DropdownArrow() },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChoiceMenuItem(
                label = stringResource(R.string.search_filter_any),
                selected = language == null,
                onClick = {
                    expanded = false
                    onLanguageChange(null)
                },
            )
            choices.forEach { choice ->
                ChoiceMenuItem(
                    label = choice.label,
                    selected = choice.code == language,
                    onClick = {
                        expanded = false
                        onLanguageChange(choice.code)
                    },
                )
            }
        }
    }
}

@Composable
private fun DateChip(
    date: DateFilter,
    selected: Boolean,
    onDateChange: (DateFilter) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    val label = if (selected) dateLabel(date) else stringResource(R.string.search_filter_date)
    val description = if (selected) stringResource(R.string.search_filter_date_description, label) else label
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { DropdownArrow() },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DATE_PRESETS.forEach { preset ->
                ChoiceMenuItem(
                    label = dateLabel(preset),
                    selected = date == preset,
                    onClick = {
                        expanded = false
                        onDateChange(preset)
                    },
                )
            }
            ChoiceMenuItem(
                label = stringResource(R.string.search_filter_date_custom),
                selected = date is DateFilter.Custom,
                onClick = {
                    expanded = false
                    showRangePicker = true
                },
            )
        }
    }
    if (showRangePicker) {
        CustomRangeDialog(
            initial = date as? DateFilter.Custom,
            onConfirm = {
                showRangePicker = false
                onDateChange(it)
            },
            onDismiss = { showRangePicker = false },
        )
    }
}

@Composable
private fun ChoiceMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon =
            if (selected) {
                { NubecitaIcon(name = NubecitaIconName.Check, contentDescription = null, opticalSize = CHIP_ICON_SIZE) }
            } else {
                null
            },
    )
}

@Composable
private fun DropdownArrow() {
    NubecitaIcon(name = NubecitaIconName.ExpandMore, contentDescription = null, opticalSize = CHIP_ICON_SIZE)
}

@Composable
internal fun sortLabel(sort: SearchPostsSort): String =
    stringResource(
        when (sort) {
            SearchPostsSort.TOP -> R.string.search_posts_sort_top
            SearchPostsSort.LATEST -> R.string.search_posts_sort_latest
        },
    )

/** A date filter's label: a preset's name, or `start – end` for a custom range. */
@Composable
internal fun dateLabel(date: DateFilter): String =
    when (date) {
        DateFilter.AllTime -> stringResource(R.string.search_filter_date_all_time)
        DateFilter.PastDay -> stringResource(R.string.search_filter_date_past_day)
        DateFilter.PastWeek -> stringResource(R.string.search_filter_date_past_week)
        DateFilter.PastMonth -> stringResource(R.string.search_filter_date_past_month)
        is DateFilter.Custom -> {
            val locale = LocalLocale.current.platformLocale
            val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
            val first = minOf(date.from, date.to)
            val last = maxOf(date.from, date.to)
            stringResource(R.string.search_filter_date_range, formatter.format(first), formatter.format(last))
        }
    }

/** The device's languages, as picker choices; recomputed when the locale list or selection changes. */
@Composable
internal fun rememberLanguageChoices(selected: String?): ImmutableList<LanguageChoice> {
    val locales = LocalConfiguration.current.locales
    val displayLocale = LocalLocale.current.platformLocale
    return remember(locales, selected, displayLocale) {
        languageChoices(
            deviceLocales = List(locales.size()) { locales[it] },
            selected = selected,
            displayLocale = displayLocale,
        )
    }
}

/** The preset windows, in menu order; [DateFilter.AllTime] is the default and first. */
internal val DATE_PRESETS: List<DateFilter> =
    listOf(DateFilter.AllTime, DateFilter.PastDay, DateFilter.PastWeek, DateFilter.PastMonth)

private val CHIP_ICON_SIZE = 18.dp
