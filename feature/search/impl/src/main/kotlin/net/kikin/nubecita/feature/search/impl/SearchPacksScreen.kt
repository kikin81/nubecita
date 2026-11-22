package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.search.impl.ui.PacksTabContent
import net.kikin.nubecita.feature.starterpack.api.StarterPack

/**
 * Stateful entry for the Packs tab. Hoists [SearchPacksViewModel], feeds it the submitted search,
 * and routes [SearchPacksEffect]: append errors and the empty state's clear-query up to the host
 * screen, and a pack tap onto [LocalMainShellNavState] as a [StarterPack]. Mirrors
 * [SearchFeedsScreen].
 */
@Composable
internal fun SearchPacksScreen(
    currentQuery: String,
    fromRecent: Boolean,
    submission: Int,
    onClearQuery: () -> Unit,
    onShowAppendError: (SearchPacksError) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchPacksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentOnClearQuery by rememberUpdatedState(onClearQuery)
    val currentOnShowAppendError by rememberUpdatedState(onShowAppendError)

    // Keyed on every part of the submitted search: a same-text re-submit has a new `submission`
    // and must fetch fresh; `fromRecent` keeps search_perform from logging a stale origin.
    LaunchedEffect(currentQuery, fromRecent, submission) {
        viewModel.setQuery(currentQuery, fromRecent = fromRecent, submission = submission)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchPacksEffect.ShowAppendError -> currentOnShowAppendError(effect.error)
                SearchPacksEffect.NavigateToClearQuery -> currentOnClearQuery()
                is SearchPacksEffect.NavigateToPack -> navState.add(StarterPack(uri = effect.uri))
            }
        }
    }

    PacksTabContent(
        state = state,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
