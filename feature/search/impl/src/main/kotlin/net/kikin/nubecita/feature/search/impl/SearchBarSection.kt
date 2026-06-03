package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * The text the field reverts to when the search bar collapses without a
 * submit (the back affordance): the last submitted query when the body is
 * showing [SearchPhase.Results], otherwise blank. Keeps the collapsed pill
 * text consistent with the body underneath it.
 */
internal fun revertTargetFor(phase: SearchPhase): String = (phase as? SearchPhase.Results)?.query ?: ""

/**
 * The Material 3 Expressive search bar for the Search tab: a collapsed
 * full-corner [SearchBar] pill that expands into an
 * [ExpandedFullScreenSearchBar] typing overlay. A single shared
 * [SearchBarDefaults.InputField] backs both so the component animates the
 * collapse/expand transition.
 *
 * The [searchBarState] is owned by the screen Composable (not the
 * ViewModel) — it is animation/runtime state, like scroll position. The
 * overlay content is driven by the *live* query text ([currentQuery]):
 * blank → the recent-search list, non-blank → [SearchTypeaheadScreen]. The
 * body beneath the collapsed pill is driven by `SearchPhase` in the caller.
 *
 * Collapse semantics:
 *  - `onSearch` (IME action / "Search for {q}" CTA) collapses the bar after
 *    [onSubmit] persists the query.
 *  - The leading back affordance (shown only while expanded) collapses the
 *    bar and reverts the field to [revertTarget] (the last submitted query,
 *    or blank), so the pill text and the body stay consistent.
 *
 * PR2 (`nubecita-h5zd.2`) swaps [ExpandedFullScreenSearchBar] for a
 * pane-scoped variant (`ExpandedDockedSearchBar` /
 * `ExpandedFullScreenContainedSearchBar`) on Medium/Expanded widths so the
 * detail pane stays visible; single-pane (PR1) always uses full-screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchBarSection(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    isQueryBlank: Boolean,
    currentQuery: String,
    recentSearches: ImmutableList<String>,
    revertTarget: String,
    onSubmit: () -> Unit,
    onChipTap: (String) -> Unit,
    onChipRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val isExpanded = searchBarState.targetValue == SearchBarValue.Expanded

    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = {
                    onSubmit()
                    scope.launch { searchBarState.animateToCollapsed() }
                },
                placeholder = { Text(stringResource(R.string.search_input_hint)) },
                leadingIcon = {
                    if (isExpanded) {
                        IconButton(
                            onClick = {
                                textFieldState.setTextAndPlaceCursorAtEnd(revertTarget)
                                scope.launch { searchBarState.animateToCollapsed() }
                            },
                        ) {
                            NubecitaIcon(
                                name = NubecitaIconName.ArrowBack,
                                contentDescription = stringResource(R.string.search_input_back_content_desc),
                            )
                        }
                    } else {
                        NubecitaIcon(
                            name = NubecitaIconName.Search,
                            contentDescription = stringResource(R.string.search_input_leading_icon_content_desc),
                        )
                    }
                },
                trailingIcon = {
                    if (!isQueryBlank) {
                        IconButton(onClick = { textFieldState.clearText() }) {
                            NubecitaIcon(
                                name = NubecitaIconName.Close,
                                contentDescription = stringResource(R.string.search_input_clear_content_desc),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }

    SearchBar(
        state = searchBarState,
        inputField = inputField,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField,
    ) {
        SearchOverlayContent(
            isQueryBlank = isQueryBlank,
            currentQuery = currentQuery,
            recentSearches = recentSearches,
            onChipTap = onChipTap,
            onChipRemove = onChipRemove,
            onClearAll = onClearAll,
            onCommitQuery = onSubmit,
        )
    }
}

/**
 * Content of the expanded search overlay. Driven by the live query text:
 * blank shows the recent-search list, non-blank shows the existing
 * [SearchTypeaheadScreen] (top-match + people suggestions + the
 * "Search for {q}" CTA).
 */
@Composable
private fun ColumnScope.SearchOverlayContent(
    isQueryBlank: Boolean,
    currentQuery: String,
    recentSearches: ImmutableList<String>,
    onChipTap: (String) -> Unit,
    onChipRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onCommitQuery: () -> Unit,
) {
    if (isQueryBlank) {
        RecentSearchOverlayList(
            items = recentSearches,
            onItemTap = onChipTap,
            onItemRemove = onChipRemove,
            onClearAll = onClearAll,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    } else {
        SearchTypeaheadScreen(
            currentQuery = currentQuery,
            onCommitQuery = onCommitQuery,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/**
 * Vertical list of recent searches shown in the expanded overlay's blank
 * state. Each row taps through via [onItemTap]; the trailing X fires
 * [onItemRemove]. A "Clear all" text action in the header fires
 * [onClearAll]. Renders nothing when [items] is empty.
 *
 * Uses the [NubecitaIconName.Search] glyph as the leading marker rather
 * than a history/clock glyph — the design-system font is a vendored subset
 * and adding a new glyph requires regenerating the whole font (avoided).
 */
@Composable
private fun RecentSearchOverlayList(
    items: ImmutableList<String>,
    onItemTap: (String) -> Unit,
    onItemRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    LazyColumn(modifier = modifier) {
        item(key = "recent-header") {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_recent_section_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.search_recent_clear_all))
                }
            }
        }
        items(items = items, key = { it }) { query ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onItemTap(query) }
                        .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.Search,
                    contentDescription = null,
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                )
                Box(
                    modifier =
                        Modifier
                            .clickable { onItemRemove(query) }
                            .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NubecitaIcon(
                        name = NubecitaIconName.Close,
                        contentDescription = stringResource(R.string.search_recent_remove_content_desc),
                    )
                }
            }
        }
    }
}
