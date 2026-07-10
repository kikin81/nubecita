package net.kikin.nubecita.feature.search.impl

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.postinteractions.ui.InteractionStrings
import net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions
import net.kikin.nubecita.data.models.FacetTarget
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.ui.PostsTabContent
import android.net.Uri as AndroidUri

/**
 * Stateful entry for the Posts tab. Hoists [SearchPostsViewModel],
 * wires the parent's debounced query via [LaunchedEffect], and routes
 * [SearchPostsEffect] to [LocalMainShellNavState] —
 * mirroring `:feature:chats:impl/ChatScreen`'s effect-collection
 * pattern.
 *
 * Post-interaction effects (share sheet, clipboard, snackbars for errors /
 * coming-soon / link-copied, composer / report / block navigation) are
 * handled by the shared [rememberPostInteractions] helper which observes
 * [SearchPostsViewModel.interactionEffects] directly. The snackbars land in
 * [snackbarHostState], which callers pass in from the parent
 * [SearchScreenContent] so a single [SnackbarHost] services the whole
 * search screen.
 *
 * Two effects propagate via callback up to the (future) vrba.8
 * search-results screen:
 *  - [onClearQuery]: parent SearchViewModel owns the canonical
 *    TextFieldState and is the only thing that can reset it.
 *  - [onShowAppendError]: append-time failures surface as snackbars
 *    in the host's SnackbarHostState, which lives at the search-
 *    screen level (not inside the per-tab content).
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun SearchPostsScreen(
    currentQuery: String,
    fromRecent: Boolean,
    onClearQuery: () -> Unit,
    onShowAppendError: (SearchPostsError) -> Unit,
    modifier: Modifier = Modifier,
    initialSort: SearchPostsSort = SearchPostsSort.TOP,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: SearchPostsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentNavState by rememberUpdatedState(navState)
    val currentOnClearQuery by rememberUpdatedState(onClearQuery)
    val currentOnShowAppendError by rememberUpdatedState(onShowAppendError)

    // Pre-resolve InteractionStrings at composition time so locale / dark-mode
    // changes participate in recomposition (lint: LocalContextGetResourceValueCall).
    val interactionStrings =
        InteractionStrings(
            errorNetwork = stringResource(R.string.search_snackbar_error_network),
            errorUnauthenticated = stringResource(R.string.search_snackbar_error_unauthenticated),
            errorUnknown = stringResource(R.string.search_snackbar_error_unknown),
            linkCopied = stringResource(R.string.search_snackbar_link_copied),
            clipLabel = stringResource(R.string.search_clipboard_label),
            reportComingSoon = stringResource(R.string.search_snackbar_overflow_report_coming_soon),
            muteComingSoon = stringResource(R.string.search_snackbar_overflow_mute_coming_soon),
            unmuteComingSoon = stringResource(R.string.search_snackbar_overflow_unmute_coming_soon),
            blockComingSoon = stringResource(R.string.search_snackbar_overflow_block_coming_soon),
            unblockComingSoon = stringResource(R.string.search_snackbar_overflow_unblock_coming_soon),
            muteThreadComingSoon =
                stringResource(R.string.search_snackbar_overflow_mute_thread_coming_soon),
            unmuteThreadComingSoon =
                stringResource(R.string.search_snackbar_overflow_unmute_thread_coming_soon),
            copyTextComingSoon =
                stringResource(R.string.search_snackbar_overflow_copy_text_coming_soon),
        )

    // Wire the shared interaction helper — it collects viewModel.interactionEffects
    // directly (share sheet, clipboard, error/coming-soon snackbars, composer /
    // report / block navigation). Search has NO haptics layer, so onInteractionError
    // is left at the default no-op.
    val interactions =
        rememberPostInteractions(
            handler = viewModel,
            snackbarHostState = snackbarHostState,
            strings = interactionStrings,
        )

    val context = LocalContext.current

    // Merge the shared callbacks with search-local overrides:
    //  - onTap  → PostTapped MVI event
    //  - onAuthorTap → OnAuthorTapped MVI event (→ NavigateToProfile effect)
    //  - onFacetTap → mention: same author-nav event (DID routes via Profile);
    //    link: in-app browser (Custom Tab).
    // Do NOT re-wrap onLike / onRepost — search has no haptics and the
    // shared handler slots are correct as-is.
    val callbacks =
        remember(viewModel, context, interactions.callbacks) {
            interactions.callbacks.copy(
                onTap = { post -> viewModel.handleEvent(SearchPostsEvent.PostTapped(post.id)) },
                onAuthorTap = { author ->
                    viewModel.handleEvent(SearchPostsEvent.OnAuthorTapped(author.handle))
                },
                onFacetTap = { target ->
                    when (target) {
                        // OnAuthorTapped carries the actor id for Profile(handle=…),
                        // which resolves a handle OR a DID — here it's a mention's DID.
                        is FacetTarget.Mention ->
                            viewModel.handleEvent(SearchPostsEvent.OnAuthorTapped(target.did))
                        is FacetTarget.Link ->
                            try {
                                CustomTabsIntent
                                    .Builder()
                                    .setShowTitle(true)
                                    .build()
                                    .launchUrl(context, AndroidUri.parse(target.uri))
                            } catch (_: ActivityNotFoundException) {
                                // No browser available — silent no-op.
                            }
                    }
                },
            )
        }

    // Push the latest debounced query down to the VM. The VM
    // dedupes via StateFlow operator fusion on the FetchKey.
    // Key on fromRecent too: the same query can be re-submitted with a
    // different origin (e.g. typed Search after a recent-chip tap), and the
    // VM must see the new fromRecent so search_perform isn't logged stale.
    LaunchedEffect(currentQuery, fromRecent) {
        viewModel.setQuery(currentQuery, fromRecent = fromRecent)
    }

    // initialSort is fired once on screen entry — the user's
    // subsequent SortClicked events drive sort changes from inside the
    // VM, so we don't re-LaunchedEffect on this past first composition.
    LaunchedEffect(Unit) {
        if (initialSort != SearchPostsSort.TOP) {
            viewModel.handleEvent(SearchPostsEvent.SortClicked(initialSort))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchPostsEffect.NavigateToPost ->
                    // replaceTop (not add): on Medium/Expanded the tapped post
                    // swaps the detail pane and system back returns to the
                    // results list; on Compact it degrades to a normal push.
                    currentNavState.replaceTop(PostDetailRoute(postUri = effect.uri))
                is SearchPostsEffect.ShowAppendError ->
                    currentOnShowAppendError(effect.error)
                SearchPostsEffect.NavigateToClearQuery ->
                    currentOnClearQuery()
                is SearchPostsEffect.NavigateToProfile ->
                    currentNavState.add(Profile(handle = effect.handle))
            }
        }
    }

    PostsTabContent(
        state = state,
        callbacks = callbacks,
        tapMarkers = interactions.tapMarkers,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
