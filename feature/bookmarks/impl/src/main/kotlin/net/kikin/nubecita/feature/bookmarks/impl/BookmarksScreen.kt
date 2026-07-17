package net.kikin.nubecita.feature.bookmarks.impl

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.postinteractions.ui.InteractionStrings
import net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions
import net.kikin.nubecita.data.models.FacetTarget
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.bookmarks.impl.ui.BookmarksContent
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import android.net.Uri as AndroidUri

/**
 * Full-screen Bookmarks route. Owns its own Scaffold + TopAppBar (back
 * arrow + title) because it is a `@MainShell` sub-route, not a tab —
 * mirrors `:feature:settings:impl/SettingsScreen`'s chrome. The post
 * list body + pagination live in [BookmarksContent].
 *
 * Post-interaction effects (share sheet, clipboard, error / coming-soon
 * snackbars, composer / report / block navigation) are handled by the
 * shared [rememberPostInteractions] helper, which observes
 * [BookmarksViewModel.interactionEffects] directly. The VM's own effect
 * channel is reserved for screen-specific navigation (post detail,
 * author profile) + the append-error snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun BookmarksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentNavState by rememberUpdatedState(navState)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Pre-resolve InteractionStrings at composition time so locale / dark-mode
    // changes participate in recomposition (lint: LocalContextGetResourceValueCall).
    val interactionStrings =
        InteractionStrings(
            errorNetwork = stringResource(R.string.bookmarks_snackbar_error_network),
            errorUnauthenticated = stringResource(R.string.bookmarks_snackbar_error_unauthenticated),
            errorUnknown = stringResource(R.string.bookmarks_snackbar_error_unknown),
            linkCopied = stringResource(R.string.bookmarks_snackbar_link_copied),
            clipLabel = stringResource(R.string.bookmarks_clipboard_label),
            reportComingSoon = stringResource(R.string.bookmarks_snackbar_overflow_report_coming_soon),
            muteComingSoon = stringResource(R.string.bookmarks_snackbar_overflow_mute_coming_soon),
            unmuteComingSoon = stringResource(R.string.bookmarks_snackbar_overflow_unmute_coming_soon),
            blockComingSoon = stringResource(R.string.bookmarks_snackbar_overflow_block_coming_soon),
            unblockComingSoon = stringResource(R.string.bookmarks_snackbar_overflow_unblock_coming_soon),
            muteThreadComingSoon =
                stringResource(R.string.bookmarks_snackbar_overflow_mute_thread_coming_soon),
            unmuteThreadComingSoon =
                stringResource(R.string.bookmarks_snackbar_overflow_unmute_thread_coming_soon),
            textCopied =
                stringResource(R.string.bookmarks_snackbar_text_copied),
        )

    // Wire the shared interaction helper — it collects viewModel.interactionEffects
    // directly (share sheet, clipboard, error/coming-soon snackbars, composer /
    // report / block navigation). No haptics layer, so onInteractionError is left
    // at the default no-op.
    val interactions =
        rememberPostInteractions(
            handler = viewModel,
            snackbarHostState = snackbarHostState,
            strings = interactionStrings,
        )

    // Merge the shared callbacks with bookmarks-local overrides:
    //  - onTap → PostTapped MVI event
    //  - onAuthorTap → OnAuthorTapped MVI event (→ NavigateToProfile effect)
    //  - onFacetTap → mention: same author-nav event (DID routes via Profile);
    //    link: in-app browser (Custom Tab).
    val callbacks =
        remember(viewModel, context, interactions.callbacks) {
            interactions.callbacks.copy(
                onTap = { post -> viewModel.handleEvent(BookmarksEvent.PostTapped(post.id)) },
                onAuthorTap = { author ->
                    viewModel.handleEvent(BookmarksEvent.OnAuthorTapped(author.handle))
                },
                onFacetTap = { target ->
                    when (target) {
                        is FacetTarget.Mention ->
                            viewModel.handleEvent(BookmarksEvent.OnAuthorTapped(target.did))
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

    val appendErrorNetwork = stringResource(R.string.bookmarks_snackbar_error_network)
    val appendErrorUnknown = stringResource(R.string.bookmarks_snackbar_error_unknown)

    LaunchedEffect(Unit) {
        // Capture the collector's scope so each snackbar runs in its own child
        // job: awaiting showSnackbar() inline would block subsequent effects
        // (e.g. a navigation) until the snackbar dismisses, and a dismiss
        // interrupting the suspended show would cancel the whole collector.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is BookmarksEffect.NavigateToPost ->
                    currentNavState.add(PostDetailRoute(postUri = effect.uri))
                is BookmarksEffect.NavigateToProfile ->
                    currentNavState.add(Profile(handle = effect.handle))
                is BookmarksEffect.ShowAppendError -> {
                    val message =
                        when (effect.error) {
                            BookmarksError.Network -> appendErrorNetwork
                            is BookmarksError.Unknown -> appendErrorUnknown
                        }
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.bookmarks_back_content_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BookmarksContent(
            state = state,
            callbacks = callbacks,
            tapMarkers = interactions.tapMarkers,
            onEvent = viewModel::handleEvent,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
