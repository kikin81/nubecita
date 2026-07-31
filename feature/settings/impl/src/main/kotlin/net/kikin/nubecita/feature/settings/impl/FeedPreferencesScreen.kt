package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.feeds.FeedViewPrefs
import net.kikin.nubecita.core.feeds.ReplyVisibility
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaListGroup
import net.kikin.nubecita.designsystem.component.NubecitaListItem
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.feed_preferences_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            // Caption + explanation are rendered here rather than via the group's
            // `label` slot: the group draws its caption immediately above the rows,
            // which would strand the explanation BELOW the options where it reads
            // as though it belonged to the next group. Reading order is heading,
            // then why it matters, then the choice.
            Text(
                text = stringResource(R.string.feed_preferences_replies),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.feed_preferences_replies_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            // The three reply options are one mutually-exclusive choice, so they
            // form a single-select group: `singleSelect = true` adds
            // selectableGroup(), which is what makes a screen reader announce
            // "1 of 3" rather than three unrelated radio buttons.
            NubecitaListGroup(items = REPLY_VISIBILITY_ORDER, singleSelect = true) { visibility, shapes ->
                NubecitaListItem(
                    shapes = shapes,
                    headlineContent = { Text(stringResource(visibility.labelRes())) },
                    selected = visibility == state.replyVisibility,
                    // Fires on EVERY tap, including the already-selected row. The
                    // ViewModel drops the no-op write; deliberately not guarded
                    // here, because a UI-side guard is what vanished when this
                    // control last changed (nubecita-239m).
                    onSelect = { onEvent(FeedPreferencesEvent.ReplyVisibilitySelected(visibility)) },
                    leadingContent = {
                        // Display-only: the row owns the gesture and the state.
                        RadioButton(selected = visibility == state.replyVisibility, onClick = null)
                    },
                )
            }

            // Reposts and quote posts are independent toggles, so they are a
            // second group rather than more rows in the first — the split is
            // what carries "these are separate decisions".
            // Uncaptioned: inventing a section name would mean a new user-facing
            // string in three locales for a styling change, and mixed
            // captioned/uncaptioned groups is what SettingsContent already does.
            NubecitaListGroup(items = FILTER_TOGGLES) { toggle, shapes ->
                val checked = if (toggle == FilterToggle.Reposts) state.hideReposts else state.hideQuotePosts
                NubecitaListItem(
                    shapes = shapes,
                    headlineContent = { Text(stringResource(toggle.titleRes)) },
                    supportingContent = { Text(stringResource(toggle.supportingRes)) },
                    checked = checked,
                    onCheckedChange = { hide ->
                        onEvent(
                            when (toggle) {
                                FilterToggle.Reposts -> FeedPreferencesEvent.HideRepostsToggled(hide)
                                FilterToggle.QuotePosts -> FeedPreferencesEvent.HideQuotePostsToggled(hide)
                            },
                        )
                    },
                    trailingContent = {
                        // Display-only: the row owns the toggle, so there is one
                        // interactive node rather than two.
                        Switch(checked = checked, onCheckedChange = null)
                    },
                )
            }
        }
    }
}

/** Least-filtered to most-filtered, so the group reads as an intensity scale. */
private val REPLY_VISIBILITY_ORDER =
    persistentListOf(ReplyVisibility.ALL, ReplyVisibility.FOLLOWED_ONLY, ReplyVisibility.NONE)

/** The two independent hide-toggles, as one group. */
private enum class FilterToggle(
    @param:StringRes val titleRes: Int,
    @param:StringRes val supportingRes: Int,
) {
    Reposts(R.string.feed_preferences_hide_reposts, R.string.feed_preferences_hide_reposts_supporting),
    QuotePosts(R.string.feed_preferences_hide_quotes, R.string.feed_preferences_hide_quotes_supporting),
}

private val FILTER_TOGGLES = persistentListOf(FilterToggle.Reposts, FilterToggle.QuotePosts)

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
