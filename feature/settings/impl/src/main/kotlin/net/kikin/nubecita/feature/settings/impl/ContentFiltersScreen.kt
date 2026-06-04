package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
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
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Stateful Content filters screen (`nubecita-twmt.2`). Hoists
 * [ContentFiltersViewModel], surfaces its save-error effect as a snackbar, and
 * projects [ContentFiltersState] through the stateless [ContentFiltersContent].
 *
 * Presented full-screen on phone and inside the Settings dialog (content-swap)
 * on tablet — the route is tagged `adaptiveDialog()` in `SettingsNavigationModule`.
 * `onNavigateTo` is unused today (no sub-routes) but kept for parity with the
 * other Settings sub-screens.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding", "UnusedParameter")
@Composable
internal fun ContentFiltersScreen(
    onBack: () -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContentFiltersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.content_filters_save_error)
    val currentSaveError by rememberUpdatedState(saveErrorMessage)

    LaunchedEffect(viewModel) {
        // Capture the effect scope so each snackbar shows in its own child job:
        // dismissing the current snackbar to show a fresh one must not cancel
        // the collector itself (mirrors SettingsScreen's effect collector).
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                ContentFiltersEffect.ShowSaveError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(currentSaveError)
                    }
            }
        }
    }

    ContentFiltersContent(
        state = state,
        onEvent = onEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless Content filters body. Extracted so preview / screenshot-test
 * composables can drive the layout without a Hilt graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentFiltersContent(
    state: ContentFiltersState,
    onEvent: (ContentFiltersEvent) -> Unit,
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
                title = { Text(stringResource(R.string.content_filters_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.content_filters_back_content_desc),
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
                text = stringResource(R.string.content_filters_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            AdultContentToggleRow(
                enabled = state.adultContentEnabled,
                onToggle = { onEvent(ContentFiltersEvent.AdultContentToggled(it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            state.categories.forEach { row ->
                CategoryBlock(
                    row = row,
                    onSelect = { onEvent(ContentFiltersEvent.VisibilitySelected(row.label, it)) },
                )
            }
        }
    }
}

@Composable
private fun AdultContentToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.content_filters_enable_adult),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.content_filters_enable_adult_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun CategoryBlock(
    row: CategoryRowUi,
    onSelect: (LabelVisibility) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(row.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(row.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabelVisibilityGroup(
            selected = row.visibility,
            enabled = row.enabled,
            onSelect = onSelect,
        )
    }
}

/**
 * Single-select Show/Warn/Hide picker, built on M3 Expressive's [ButtonGroup]
 * with `toggleableItem` children (mirrors `ProfilePillTabs`). Tapping the
 * already-selected segment is a no-op; the whole group is disabled (greyed)
 * when [enabled] is false (an adult category with the master gate off).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LabelVisibilityGroup(
    selected: LabelVisibility,
    enabled: Boolean,
    onSelect: (LabelVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve labels in the composable body — ButtonGroup's content is a
    // non-composable builder scope (like LazyColumn's), so `stringResource`
    // can't be called inside the `toggleableItem` registrations.
    val options = VISIBILITY_ORDER.map { it to stringResource(it.labelRes()) }
    ButtonGroup(
        overflowIndicator = {},
        expandedRatio = 0.025f,
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEach { (visibility, label) ->
            toggleableItem(
                checked = visibility == selected,
                label = label,
                onCheckedChange = { newChecked -> if (newChecked) onSelect(visibility) },
                enabled = enabled,
                weight = 1f,
            )
        }
    }
}

private val VISIBILITY_ORDER = listOf(LabelVisibility.SHOW, LabelVisibility.WARN, LabelVisibility.HIDE)

@androidx.annotation.StringRes
private fun LabelVisibility.labelRes(): Int =
    when (this) {
        LabelVisibility.SHOW -> R.string.content_filters_show
        LabelVisibility.WARN -> R.string.content_filters_warn
        LabelVisibility.HIDE -> R.string.content_filters_hide
    }

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

internal fun contentFiltersPreviewState(adultEnabled: Boolean): ContentFiltersState = ModerationPrefs.DEFAULT.copy(adultContentEnabled = adultEnabled).toContentFiltersState()

@Preview(name = "Content filters — adult off — light", showBackground = true, heightDp = 820)
@Preview(
    name = "Content filters — adult off — dark",
    showBackground = true,
    heightDp = 820,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ContentFiltersPreview() {
    NubecitaTheme {
        Surface {
            ContentFiltersContent(
                state = contentFiltersPreviewState(adultEnabled = false),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
