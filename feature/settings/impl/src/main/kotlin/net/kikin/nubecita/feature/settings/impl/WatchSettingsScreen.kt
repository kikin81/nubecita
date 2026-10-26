package net.kikin.nubecita.feature.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.preferences.WatchFeedsPreference
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.designsystem.component.NubecitaListGroup
import net.kikin.nubecita.designsystem.component.NubecitaListItem
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Stateful Settings → Watch screen: hoists [WatchSettingsViewModel], surfaces
 * its save-error effect as a snackbar, and projects the state through the
 * stateless [WatchSettingsContent].
 *
 * The suppression matches [MediaAndAnimationsScreen]: only `state` and a bound
 * `handleEvent` reference cross into `*Content`, not the ViewModel itself.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun WatchSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.watch_save_error)
    val currentSaveError by rememberUpdatedState(saveErrorMessage)

    LaunchedEffect(viewModel) {
        // Each snackbar in its own child job, so replacing one doesn't cancel
        // the collector (mirrors MediaAndAnimationsScreen).
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                WatchSettingsEffect.ShowSaveError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(currentSaveError)
                    }
            }
        }
    }

    WatchSettingsContent(
        state = state,
        onEvent = onEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Stateless body, so previews and screenshot tests need no Hilt graph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchSettingsContent(
    state: WatchSettingsState,
    onEvent: (WatchSettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watch_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.watch_back_content_desc),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.watch_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )

            Text(
                text = stringResource(R.string.watch_feeds_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.watch_feeds_limit,
                        WatchFeedsPreference.MAX_FEEDS,
                        WatchFeedsPreference.MAX_FEEDS,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NubecitaListGroup(items = state.feeds) { feed, shapes ->
                val checked = feed.uri in state.selectedUris
                val toggleable = state.isToggleable(feed.uri)
                NubecitaListItem(
                    shapes = shapes,
                    // A row that can't change (the cap is reached, or it's the
                    // last checked feed) drops its toggle gesture and says so.
                    modifier = if (toggleable) Modifier else Modifier.semantics { disabled() },
                    headlineContent = { Text(feed.label()) },
                    checked = checked,
                    onCheckedChange =
                        if (toggleable) {
                            { selected -> onEvent(WatchSettingsEvent.FeedToggled(feed.uri, selected)) }
                        } else {
                            null
                        },
                    trailingContent = {
                        // Display-only: the row owns the toggle gesture.
                        Checkbox(checked = checked, onCheckedChange = null, enabled = toggleable)
                    },
                )
            }
        }
    }
}

@Composable
private fun WatchFeedOption.label(): String = if (kind == FeedKind.Following) stringResource(R.string.watch_feed_following) else displayName
