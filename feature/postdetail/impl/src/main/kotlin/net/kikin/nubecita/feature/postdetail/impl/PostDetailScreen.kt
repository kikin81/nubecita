package net.kikin.nubecita.feature.postdetail.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.feature.postdetail.impl.data.ThreadItem

/**
 * Hilt-aware post-detail screen.
 *
 * Owns the screen's lifecycle wiring: state collection, the single
 * `effects` collector that surfaces snackbars + nav callbacks, the
 * `remember`-d `PostCallbacks` that dispatch to the VM, and the
 * one-shot `LaunchedEffect(Unit)` that fires `PostDetailEvent.Load` on
 * first composition. Delegates the actual rendering to
 * [PostDetailScreenContent] which previews and tests can call directly
 * with fixture inputs (no ViewModel, no Hilt graph).
 *
 * # m28.5.1 visual scope
 *
 * Plain `LazyColumn` rendering each [ThreadItem] as the existing
 * `:designsystem` PostCard. Standard M3 `TopAppBar` with back arrow.
 * No expressive container hierarchy, no carousel, no floating composer
 * — those land in m28.5.2. Reviewers should be able to tell at a glance
 * "this PR isn't trying to look pretty yet."
 */
@Composable
internal fun PostDetailScreen(
    viewModel: PostDetailViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToAuthor: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val callbacks =
        remember(viewModel) {
            // m28.5.1 wires only the navigation callbacks. Like / repost /
            // share / reply land with the visual treatment in m28.5.2; the
            // `PostCallbacks.None`-shaped defaults make those gestures
            // no-op silently here without TalkBack announcing them (see
            // PostCallbacks KDoc on `onShareLongPress = null`).
            PostCallbacks(
                onTap = { viewModel.handleEvent(PostDetailEvent.OnPostTapped(it.id)) },
                onAuthorTap = { viewModel.handleEvent(PostDetailEvent.OnAuthorTapped(it.did)) },
            )
        }

    val onRetry = remember(viewModel) { { viewModel.handleEvent(PostDetailEvent.Retry) } }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToAuthor by rememberUpdatedState(onNavigateToAuthor)

    // Pre-resolve snackbar copy at composition time so locale changes
    // participate in recomposition (lint: LocalContextGetResourceValueCall).
    val networkErrorMessage = stringResource(R.string.postdetail_snackbar_error_network)
    val unauthErrorMessage = stringResource(R.string.postdetail_snackbar_error_unauthenticated)
    val notFoundErrorMessage = stringResource(R.string.postdetail_snackbar_error_notfound)
    val unknownErrorMessage = stringResource(R.string.postdetail_snackbar_error_unknown)

    LaunchedEffect(Unit) { viewModel.handleEvent(PostDetailEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PostDetailEffect.ShowError -> {
                    val message =
                        when (effect.error) {
                            PostDetailError.Network -> networkErrorMessage
                            PostDetailError.Unauthenticated -> unauthErrorMessage
                            PostDetailError.NotFound -> notFoundErrorMessage
                            is PostDetailError.Unknown -> unknownErrorMessage
                        }
                    // Replace, don't stack — successive errors during a flapping
                    // network spell would otherwise queue snackbars indefinitely.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
                is PostDetailEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is PostDetailEffect.NavigateToAuthor -> currentOnNavigateToAuthor(effect.authorDid)
            }
        }
    }

    PostDetailScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        callbacks = callbacks,
        onBack = currentOnBack,
        onRetry = onRetry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostDetailScreenContent(
    state: PostDetailState,
    snackbarHostState: SnackbarHostState,
    callbacks: PostCallbacks,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.postdetail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.postdetail_back_content_description),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val status = state.loadStatus) {
            PostDetailLoadStatus.InitialLoading ->
                LoadingState(contentPadding = padding)
            is PostDetailLoadStatus.InitialError ->
                ErrorState(
                    error = status.error,
                    onRetry = onRetry,
                    contentPadding = padding,
                )
            PostDetailLoadStatus.Idle,
            PostDetailLoadStatus.Refreshing,
            ->
                LoadedThread(
                    items = state.items,
                    callbacks = callbacks,
                    contentPadding = padding,
                )
        }
    }
}

@Composable
private fun LoadedThread(
    items: ImmutableList<ThreadItem>,
    callbacks: PostCallbacks,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(items = items, key = { it.key }) { item ->
            when (item) {
                is ThreadItem.Ancestor -> PostCard(post = item.post, callbacks = callbacks)
                is ThreadItem.Focus -> PostCard(post = item.post, callbacks = callbacks)
                is ThreadItem.Reply -> PostCard(post = item.post, callbacks = callbacks)
                is ThreadItem.Blocked ->
                    InlineUnavailableRow(
                        label = stringResource(R.string.postdetail_inline_blocked),
                    )
                is ThreadItem.NotFound ->
                    InlineUnavailableRow(
                        label = stringResource(R.string.postdetail_inline_notfound),
                    )
                is ThreadItem.Fold -> {
                    // m28.5.1 mapper does not emit Fold; leaving the case
                    // explicit here so the exhaustive-when stays compile-
                    // checked. m28.5.2's visual treatment will render a
                    // "View more" affordance here.
                }
            }
        }
    }
}

@Composable
private fun InlineUnavailableRow(label: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingState(contentPadding: PaddingValues) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    error: PostDetailError,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
) {
    val titleRes =
        when (error) {
            PostDetailError.Network -> R.string.postdetail_error_network_title
            PostDetailError.Unauthenticated -> R.string.postdetail_error_unauthenticated_title
            PostDetailError.NotFound -> R.string.postdetail_error_notfound_title
            is PostDetailError.Unknown -> R.string.postdetail_error_unknown_title
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            // NotFound is terminal — retry can't recover (the post is
            // gone). Suppress the action button for that variant; show
            // it for every recoverable error.
            if (error !is PostDetailError.NotFound) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.postdetail_error_action))
                }
            }
        }
    }
}
