package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.feeds.FeedViewPrefs
import net.kikin.nubecita.core.feeds.ReplyVisibility
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Stateful Feed preferences screen (`nubecita-1fmx.2`). Hoists
 * [FeedPreferencesViewModel], surfaces its save-error effect as a snackbar, and
 * projects [FeedPreferencesState] through the stateless [FeedPreferencesContent].
 *
 * Presented full-screen on phone and inside the Settings dialog (content-swap)
 * on tablet — the route is tagged `adaptiveDialog()` in
 * `SettingsNavigationModule`. `onNavigateTo` is unused today (no sub-routes) but
 * kept for parity with the other Settings sub-screens.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding", "UnusedParameter")
@Composable
internal fun FeedPreferencesScreen(
    onBack: () -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedPreferencesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.feed_preferences_save_error)
    val currentSaveError by rememberUpdatedState(saveErrorMessage)

    LaunchedEffect(viewModel) {
        // Capture the effect scope so each snackbar shows in its own child job:
        // dismissing the current snackbar to show a fresh one must not cancel
        // the collector itself (mirrors ContentFiltersScreen).
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                FeedPreferencesEffect.ShowSaveError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(currentSaveError)
                    }
            }
        }
    }

    FeedPreferencesContent(
        state = state,
        onEvent = onEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless Feed preferences body. Extracted so preview / screenshot-test
 * composables can drive the layout without a Hilt graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedPreferencesContent(
    state: FeedPreferencesState,
    onEvent: (FeedPreferencesEvent) -> Unit,
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
                title = { Text(stringResource(R.string.feed_preferences_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.feed_preferences_back_content_desc),
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
                    .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.feed_preferences_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            RepliesBlock(
                selected = state.replyVisibility,
                onSelect = { onEvent(FeedPreferencesEvent.ReplyVisibilitySelected(it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SwitchRow(
                title = stringResource(R.string.feed_preferences_hide_reposts),
                supporting = stringResource(R.string.feed_preferences_hide_reposts_supporting),
                checked = state.hideReposts,
                onCheckedChange = { onEvent(FeedPreferencesEvent.HideRepostsToggled(it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SwitchRow(
                title = stringResource(R.string.feed_preferences_hide_quotes),
                supporting = stringResource(R.string.feed_preferences_hide_quotes_supporting),
                checked = state.hideQuotePosts,
                onCheckedChange = { onEvent(FeedPreferencesEvent.HideQuotePostsToggled(it)) },
            )
        }
    }
}

/**
 * The reply choice: one connected button group over the three mutually
 * exclusive options, mirroring `ContentFiltersScreen`'s `LabelVisibilityGroup`.
 */
@Composable
private fun RepliesBlock(
    selected: ReplyVisibility,
    onSelect: (ReplyVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.feed_preferences_replies),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.feed_preferences_replies_supporting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReplyVisibilityGroup(selected = selected, onSelect = onSelect)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReplyVisibilityGroup(
    selected: ReplyVisibility,
    onSelect: (ReplyVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = REPLY_VISIBILITY_ORDER.map { it to stringResource(it.labelRes()) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (visibility, label) ->
            ToggleButton(
                checked = visibility == selected,
                // Single-select: tapping the active segment fires
                // onCheckedChange(false), which we ignore (no "none selected").
                onCheckedChange = { newChecked -> if (newChecked) onSelect(visibility) },
                modifier = Modifier.weight(1f),
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Least-filtered to most-filtered, so the group reads as an intensity scale. */
private val REPLY_VISIBILITY_ORDER =
    listOf(ReplyVisibility.ALL, ReplyVisibility.FOLLOWED_ONLY, ReplyVisibility.NONE)

@androidx.annotation.StringRes
private fun ReplyVisibility.labelRes(): Int =
    when (this) {
        ReplyVisibility.ALL -> R.string.feed_preferences_replies_all
        ReplyVisibility.FOLLOWED_ONLY -> R.string.feed_preferences_replies_followed
        ReplyVisibility.NONE -> R.string.feed_preferences_replies_none
    }

internal fun feedPreferencesPreviewState(prefs: FeedViewPrefs = FeedViewPrefs.DEFAULT): FeedPreferencesState = prefs.toFeedPreferencesState()

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedPreferencesPreview() {
    NubecitaTheme {
        Surface {
            FeedPreferencesContent(
                state = feedPreferencesPreviewState(),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
