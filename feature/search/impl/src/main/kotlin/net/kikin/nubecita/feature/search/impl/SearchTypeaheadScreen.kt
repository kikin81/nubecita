package net.kikin.nubecita.feature.search.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.impl.ui.ActorRow
import net.kikin.nubecita.feature.search.impl.ui.SearchForCtaButton
import net.kikin.nubecita.feature.search.impl.ui.TypeaheadSectionHeader

/**
 * Stateful Search typeahead screen (vrba.10). Hoists
 * [SearchTypeaheadViewModel], wires the parent's debounced query via
 * [LaunchedEffect], and routes [SearchTypeaheadEffect.NavigateToProfile]
 * to [LocalMainShellNavState] — mirroring the [SearchActorsScreen]
 * pattern so tap-through nav stays uniform across the search surface.
 *
 * The "Search for {q}" CTA's commit path goes through [onCommitQuery]
 * (a callback up to the parent [SearchScreen], not a VM effect), the
 * same way [SearchActorsScreen]'s [onClearQuery] reaches the parent's
 * canonical `TextFieldState`. Routing commit through the typeahead VM
 * would couple this screen to a piece of state it doesn't own.
 */
@Composable
internal fun SearchTypeaheadScreen(
    currentQuery: String,
    onCommitQuery: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchTypeaheadViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    // Bound method reference is allocated fresh each call; without this
    // remember, SearchTypeaheadContent sees a "new" onEvent every parent
    // recomposition and can't skip. Mirrors the SearchScreen pattern at
    // `SearchScreen.kt`: `val onEvent = remember(viewModel) { viewModel::handleEvent }`.
    val onEvent = remember(viewModel) { viewModel::handleEvent }

    LaunchedEffect(currentQuery) {
        viewModel.setQuery(currentQuery)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchTypeaheadEffect.NavigateToProfile ->
                    navState.add(Profile(handle = effect.handle))
            }
        }
    }

    SearchTypeaheadContent(
        query = currentQuery,
        status = state.status,
        onCommitQuery = onCommitQuery,
        onEvent = onEvent,
        modifier = modifier,
    )
}

/**
 * Stateless body for the typeahead screen. The CTA renders
 * unconditionally; the section below it is driven by [status].
 *
 * Layout uses a single [LazyColumn] (CTA + sections share the same
 * scroll surface) so a tall device with several suggestion rows stays
 * scrollable end-to-end. Pagination is N/A — `searchActorsTypeahead`
 * returns at most 8 actors per call.
 */
@Composable
internal fun SearchTypeaheadContent(
    query: String,
    status: SearchTypeaheadStatus,
    onCommitQuery: () -> Unit,
    onEvent: (SearchTypeaheadEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "cta") {
            SearchForCtaButton(query = query, onClick = onCommitQuery)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        when (status) {
            SearchTypeaheadStatus.Idle -> Unit
            is SearchTypeaheadStatus.Loading ->
                item(key = "loading") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            is SearchTypeaheadStatus.NoResults -> Unit
            is SearchTypeaheadStatus.Suggestions -> {
                item(key = "topmatch-header") {
                    TypeaheadSectionHeader(
                        label = stringResource(R.string.search_typeahead_top_match_label),
                    )
                }
                item(key = "topmatch") {
                    ActorRow(
                        actor = status.topMatch,
                        query = status.query,
                        onClick = { onEvent(SearchTypeaheadEvent.ActorTapped(status.topMatch.handle)) },
                    )
                }
                if (status.people.isNotEmpty()) {
                    item(key = "people-header") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TypeaheadSectionHeader(
                            label = stringResource(R.string.search_typeahead_people_label),
                        )
                    }
                    items(items = status.people, key = { it.did }) { actor ->
                        ActorRow(
                            actor = actor,
                            query = status.query,
                            onClick = { onEvent(SearchTypeaheadEvent.ActorTapped(actor.handle)) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private fun previewActor(
    did: String,
    handle: String,
    displayName: String?,
): ActorUi =
    ActorUi(
        did = did,
        handle = handle,
        displayName = displayName,
        avatarUrl = null,
    )

@Preview(name = "Typeahead — Idle", showBackground = true)
@Composable
private fun SearchTypeaheadIdlePreview() {
    NubecitaTheme {
        Surface {
            SearchTypeaheadContent(
                query = "alice",
                status = SearchTypeaheadStatus.Idle,
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Typeahead — Loading", showBackground = true)
@Composable
private fun SearchTypeaheadLoadingPreview() {
    NubecitaTheme {
        Surface {
            SearchTypeaheadContent(
                query = "alice",
                status = SearchTypeaheadStatus.Loading("alice"),
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Typeahead — Suggestions, top match only", showBackground = true)
@Composable
private fun SearchTypeaheadSuggestionsTopMatchOnlyPreview() {
    NubecitaTheme {
        Surface {
            SearchTypeaheadContent(
                query = "alice",
                status =
                    SearchTypeaheadStatus.Suggestions(
                        query = "alice",
                        topMatch = previewActor("did:plc:alice", "alice.bsky.social", "Alice Chen"),
                        people = persistentListOf(),
                    ),
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Typeahead — Suggestions, top + people", showBackground = true)
@Composable
private fun SearchTypeaheadSuggestionsTopAndPeoplePreview() {
    NubecitaTheme {
        Surface {
            SearchTypeaheadContent(
                query = "al",
                status =
                    SearchTypeaheadStatus.Suggestions(
                        query = "al",
                        topMatch = previewActor("did:plc:alice", "alice.bsky.social", "Alice Chen"),
                        people =
                            persistentListOf(
                                previewActor("did:plc:alex", "alex.bsky.social", "Alex Park"),
                                previewActor("did:plc:albert", "albert.bsky.social", null),
                                previewActor("did:plc:alma", "alma.bsky.social", "Alma Rivera"),
                            ),
                    ),
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Typeahead — NoResults", showBackground = true)
@Composable
private fun SearchTypeaheadNoResultsPreview() {
    NubecitaTheme {
        Surface {
            SearchTypeaheadContent(
                query = "zxyqq",
                status = SearchTypeaheadStatus.NoResults("zxyqq"),
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}

@Preview(
    name = "Typeahead — Suggestions, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchTypeaheadSuggestionsDarkPreview() {
    NubecitaTheme {
        Surface {
            SearchTypeaheadContent(
                query = "al",
                status =
                    SearchTypeaheadStatus.Suggestions(
                        query = "al",
                        topMatch = previewActor("did:plc:alice", "alice.bsky.social", "Alice Chen"),
                        people =
                            persistentListOf(
                                previewActor("did:plc:alex", "alex.bsky.social", "Alex Park"),
                            ),
                    ),
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}
