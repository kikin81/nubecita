package net.kikin.nubecita.feature.search.impl

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.postinteractions.ui.InteractionStrings
import net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions
import net.kikin.nubecita.data.models.FacetTarget
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.impl.ui.HashtagContent
import android.net.Uri as AndroidUri
import net.kikin.nubecita.designsystem.R as DesignSystemR

/**
 * Stateful Hashtag screen. Collects [HashtagViewModel] state, wires the shared
 * post-interaction helper (share / copy / delete / overflow snackbars), and
 * routes [HashtagEffect]s: post and profile taps push onto
 * [LocalMainShellNavState], share opens the system chooser, and append /
 * refresh failures surface as snackbars.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun HashtagScreen(
    viewModel: HashtagViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentNavState by rememberUpdatedState(navState)
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val errorNetwork = stringResource(R.string.search_snackbar_error_network)
    val errorUnknown = stringResource(R.string.search_snackbar_error_unknown)

    // Pre-resolved at composition time so locale changes participate in
    // recomposition (lint: LocalContextGetResourceValueCall). Same strings as
    // the Posts tab.
    val interactionStrings =
        InteractionStrings(
            errorNetwork = errorNetwork,
            errorUnauthenticated = stringResource(R.string.search_snackbar_error_unauthenticated),
            errorUnknown = errorUnknown,
            linkCopied = stringResource(R.string.search_snackbar_link_copied),
            clipLabel = stringResource(R.string.search_clipboard_label),
            reportComingSoon = stringResource(R.string.search_snackbar_overflow_report_coming_soon),
            muteComingSoon = stringResource(R.string.search_snackbar_overflow_mute_coming_soon),
            unmuteComingSoon = stringResource(R.string.search_snackbar_overflow_unmute_coming_soon),
            blockComingSoon = stringResource(R.string.search_snackbar_overflow_block_coming_soon),
            unblockComingSoon = stringResource(R.string.search_snackbar_overflow_unblock_coming_soon),
            muteThreadComingSoon = stringResource(R.string.search_snackbar_overflow_mute_thread_coming_soon),
            unmuteThreadComingSoon = stringResource(R.string.search_snackbar_overflow_unmute_thread_coming_soon),
            textCopied = stringResource(R.string.search_snackbar_text_copied),
            deleteTitle = stringResource(DesignSystemR.string.postcard_delete_dialog_title),
            deleteBody = stringResource(DesignSystemR.string.postcard_delete_dialog_body),
            deleteConfirm = stringResource(DesignSystemR.string.postcard_delete_dialog_confirm),
            deleteCancel = stringResource(DesignSystemR.string.postcard_delete_dialog_cancel),
            deleteSuccess = stringResource(DesignSystemR.string.postcard_delete_success),
            translationUnavailable = stringResource(DesignSystemR.string.postcard_translation_unavailable),
        )
    val interactions =
        rememberPostInteractions(
            handler = viewModel,
            snackbarHostState = snackbarHostState,
            strings = interactionStrings,
        )

    val callbacks =
        remember(viewModel, context, interactions.callbacks) {
            interactions.callbacks.copy(
                onTap = { post -> viewModel.handleEvent(HashtagEvent.PostTapped(post.id)) },
                onAuthorTap = { author -> viewModel.handleEvent(HashtagEvent.OnAuthorTapped(author.handle)) },
                onFacetTap = { target ->
                    when (target) {
                        is FacetTarget.Mention -> viewModel.handleEvent(HashtagEvent.OnAuthorTapped(target.did))
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

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // add (not replaceTop): the Hashtag screen is itself a
                // detail-pane sub-route, so a post stacks above it.
                is HashtagEffect.NavigateToPost -> currentNavState.add(PostDetailRoute(postUri = effect.uri))
                is HashtagEffect.NavigateToProfile -> currentNavState.add(Profile(handle = effect.actor))
                is HashtagEffect.Share -> {
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, effect.url)
                        }
                    context.startActivity(Intent.createChooser(send, null))
                }
                is HashtagEffect.ShowError -> {
                    val message = if (effect.error == SearchPostsError.Network) errorNetwork else errorUnknown
                    // Launched, not awaited: a visible snackbar must not hold up
                    // the next effect (e.g. a navigation) in this collector.
                    launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        }
    }

    HashtagContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        callbacks = callbacks,
        tapMarkers = interactions.tapMarkers,
        modifier = modifier,
    )
}
