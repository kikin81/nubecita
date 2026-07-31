package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
 * The reply choice: a single-select radio list over the three mutually
 * exclusive options.
 *
 * Deliberately NOT a connected button group (which is what
 * `ContentFiltersScreen`'s `LabelVisibilityGroup` uses). A button group gives
 * every segment equal width, so the longest label decides the layout: "People
 * you follow" wrapped to two lines while "All" and "None" stayed on one,
 * leaving the group ragged and the middle segment visually dominant.
 * Shortening the English label would not fix it — pt-BR is "Pessoas que você
 * segue", longer still. A button group is for short, comparable labels; a
 * radio list has a full row per option and is indifferent to label length.
 */
@Composable
private fun RepliesBlock(
    selected: ReplyVisibility,
    onSelect: (ReplyVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
        }
        ReplyVisibilityGroup(selected = selected, onSelect = onSelect)
    }
}

@Composable
private fun ReplyVisibilityGroup(
    selected: ReplyVisibility,
    onSelect: (ReplyVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The enum -> @StringRes pairing is static, so hoist it out of recomposition;
    // stringResource must stay in composition because it reads configuration.
    val options = remember { REPLY_VISIBILITY_ORDER.map { it to it.labelRes() } }
    Column(
        // selectableGroup() makes TalkBack announce these as one radio group
        // ("1 of 3") rather than three unrelated radio buttons.
        modifier = modifier.fillMaxWidth().selectableGroup(),
    ) {
        options.forEach { (visibility, labelRes) ->
            ReplyVisibilityRow(
                label = stringResource(labelRes),
                selected = visibility == selected,
                onSelect = { onSelect(visibility) },
            )
        }
    }
}

@Composable
private fun ReplyVisibilityRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // selectable BEFORE padding so the whole padded row is the touch
                // target, and so TalkBack merges the label and selected state into
                // one announcement — same ownership rule as SwitchRow below.
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onSelect,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Gesture + semantics live on the Row; a handler here would duplicate both.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
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
                // toggleable BEFORE padding so the whole padded row is the touch
                // target, and so TalkBack merges the title, supporting text and
                // switch state into one announcement. Without it the Switch has
                // no label at all and reads as a bare "switch, off".
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
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
        // Gesture + semantics live on the Row; a handler here would duplicate both.
        Switch(checked = checked, onCheckedChange = null)
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
