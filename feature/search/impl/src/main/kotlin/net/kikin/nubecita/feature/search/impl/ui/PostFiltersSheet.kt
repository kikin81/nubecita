package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.DateFilter
import net.kikin.nubecita.feature.search.impl.data.MediaFilter
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.ReplyFilter
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The full filter sheet. Every choice applies live: it calls [onFiltersChange]
 * straight away and the Posts tab refetches behind the sheet through the shared
 * first-page cache, so the count in the button is the count of what is being
 * shown. There is no draft to commit; the button at the bottom only dismisses.
 * [onReset] restores every filter to its default except the sort, which is not
 * a filter the user can clear.
 *
 * The body lives in [PostFiltersSheetContent] so it can be screenshot-tested
 * without a dialog window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostFiltersSheet(
    filters: PostSearchFilters,
    hitsTotal: Long?,
    onFiltersChange: (PostSearchFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fullScreenRangePicker: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val languages = rememberLanguageChoices(selected = filters.language)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        PostFiltersSheetContent(
            filters = filters,
            hitsTotal = hitsTotal,
            languages = languages,
            onFiltersChange = onFiltersChange,
            onReset = onReset,
            fullScreenRangePicker = fullScreenRangePicker,
            onDone = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            },
        )
    }
}

@Composable
internal fun PostFiltersSheetContent(
    filters: PostSearchFilters,
    hitsTotal: Long?,
    languages: ImmutableList<LanguageChoice>,
    onFiltersChange: (PostSearchFilters) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    fullScreenRangePicker: Boolean = false,
) {
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    Column(
        // Scrollable so every section stays reachable with enlarged text or a short window.
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.s4)
                .padding(bottom = MaterialTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.search_filter_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            TextButton(onClick = onReset, enabled = filters.activeCount > 0) {
                Text(stringResource(R.string.search_filter_sheet_reset))
            }
        }

        FilterSection(title = stringResource(R.string.search_posts_sort_label)) {
            SearchPostsSort.entries.forEach { option ->
                ChoiceChip(
                    label = sortLabel(option),
                    selected = filters.sort == option,
                    onClick = { onFiltersChange(filters.copy(sort = option)) },
                )
            }
        }
        FilterSection(title = stringResource(R.string.search_filter_section_from)) {
            ChoiceChip(
                label = stringResource(R.string.search_filter_from_anyone),
                selected = !filters.followingOnly,
                onClick = { onFiltersChange(filters.copy(followingOnly = false)) },
            )
            ChoiceChip(
                label = stringResource(R.string.search_filter_from_following),
                selected = filters.followingOnly,
                onClick = { onFiltersChange(filters.copy(followingOnly = true)) },
            )
        }
        FilterSection(title = stringResource(R.string.search_filter_media)) {
            MediaFilter.entries.forEach { option ->
                ChoiceChip(
                    label =
                        stringResource(
                            when (option) {
                                MediaFilter.ANY -> R.string.search_filter_any
                                MediaFilter.HAS_MEDIA -> R.string.search_filter_media_has
                                MediaFilter.VIDEO_ONLY -> R.string.search_filter_media_video_only
                            },
                        ),
                    selected = filters.media == option,
                    onClick = { onFiltersChange(filters.copy(media = option)) },
                )
            }
        }
        FilterSection(title = stringResource(R.string.search_filter_section_replies)) {
            ReplyFilter.entries.forEach { option ->
                ChoiceChip(
                    label =
                        stringResource(
                            when (option) {
                                ReplyFilter.INCLUDE -> R.string.search_filter_replies_include
                                ReplyFilter.HIDE -> R.string.search_filter_replies_hide
                                ReplyFilter.ONLY -> R.string.search_filter_replies_only
                            },
                        ),
                    selected = filters.replies == option,
                    onClick = { onFiltersChange(filters.copy(replies = option)) },
                )
            }
        }
        FilterSection(title = stringResource(R.string.search_filter_language)) {
            ChoiceChip(
                label = stringResource(R.string.search_filter_any),
                selected = filters.language == null,
                onClick = { onFiltersChange(filters.copy(language = null)) },
            )
            languages.forEach { choice ->
                ChoiceChip(
                    label = choice.label,
                    selected = bareLanguageCode(filters.language) == choice.code,
                    onClick = { onFiltersChange(filters.copy(language = choice.code)) },
                )
            }
        }
        FilterSection(title = stringResource(R.string.search_filter_date)) {
            DATE_PRESETS.forEach { preset ->
                ChoiceChip(
                    label = dateLabel(preset),
                    selected = filters.date == preset,
                    onClick = { onFiltersChange(filters.copy(date = preset)) },
                )
            }
            val custom = filters.date as? DateFilter.Custom
            ChoiceChip(
                label = if (custom != null) dateLabel(custom) else stringResource(R.string.search_filter_date_custom),
                selected = custom != null,
                onClick = { showRangePicker = true },
            )
        }

        val shown = shownHitsTotal(hitsTotal)
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (shown != null) {
                    stringResource(R.string.search_filter_sheet_show_count, rememberCompactCount(shown))
                } else {
                    stringResource(R.string.search_filter_sheet_show_results)
                },
            )
        }
    }
    if (showRangePicker) {
        CustomRangeDialog(
            initial = filters.date as? DateFilter.Custom,
            onConfirm = {
                showRangePicker = false
                onFiltersChange(filters.copy(date = it))
            },
            onDismiss = { showRangePicker = false },
            fullScreen = fullScreenRangePicker,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s1)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        // FlowRow, not a button group: long es-419 / pt-BR labels wrap to a second
        // line instead of breaking the layout.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
        ) {
            content()
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon =
            if (selected) {
                { NubecitaIcon(name = NubecitaIconName.Check, contentDescription = null, opticalSize = 18.dp) }
            } else {
                null
            },
    )
}

/**
 * The M3 date-range picker over the sheet or the row. On compact windows
 * ([fullScreen]) it is a full-screen dialog, as Material specifies for phones;
 * on wider windows it stays in a standard dialog. Confirm is enabled once both
 * ends are chosen. The picker works in UTC-midnight milliseconds;
 * [DateFilter.Custom] holds plain dates, so they convert here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomRangeDialog(
    initial: DateFilter.Custom?,
    onConfirm: (DateFilter.Custom) -> Unit,
    onDismiss: () -> Unit,
    fullScreen: Boolean = false,
) {
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = initial?.from?.toUtcMillis(),
            initialSelectedEndDateMillis = initial?.to?.toUtcMillis(),
        )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    val confirm = {
        if (start != null && end != null) {
            onConfirm(DateFilter.Custom(from = start.toUtcDate(), to = end.toUtcDate()))
        }
    }
    if (fullScreen) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                RangePickerFullScreenContent(
                    state = state,
                    onClose = onDismiss,
                    onConfirm = confirm,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        }
    } else {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(enabled = start != null && end != null, onClick = confirm) {
                    Text(stringResource(R.string.search_filter_range_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.search_filter_range_cancel)) }
            },
        ) {
            DateRangePicker(state = state)
        }
    }
}

/**
 * The body of the full-screen range picker: a close button and a confirm button
 * (enabled once both dates are chosen) over the picker. Stateless apart from the
 * picker [state] it is handed, so it can be previewed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RangePickerFullScreenContent(
    state: DateRangePickerState,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose) {
                NubecitaIcon(
                    name = NubecitaIconName.Close,
                    contentDescription = stringResource(R.string.search_filter_range_cancel),
                )
            }
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = onConfirm,
            ) {
                Text(stringResource(R.string.search_filter_range_apply))
            }
        }
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate =
    Instant
        .ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
