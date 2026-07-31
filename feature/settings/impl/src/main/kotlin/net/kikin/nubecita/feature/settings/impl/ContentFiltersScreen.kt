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
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaListGroup
import net.kikin.nubecita.designsystem.component.NubecitaListItem
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
 *
 * The master "Enable adult content" switch renders through the design system's
 * M3 Expressive grouped list ([NubecitaListGroup] + [NubecitaListItem]) — the
 * same components the Settings home uses — instead of a hand-rolled `Row` split
 * off by a `HorizontalDivider`.
 *
 * The screen holds two kinds of control, so it is grouped by meaning rather
 * than emitted as one flat run:
 *
 *  1. **The master gate** — a single-row group. It is an account-level on/off
 *     switch, the same shape as every other Settings toggle, so it gets the
 *     segmented list treatment and its own visual block.
 *  2. **The per-category pickers** — one [CategoryBlock] each. These are
 *     three-way Show/Warn/Hide choices, not list rows, so they stay
 *     [LabelVisibilityGroup] connected button groups (short, comparable,
 *     equal-width labels are exactly what a button group is for) with their
 *     title and description above, mirroring `FeedPreferencesScreen`'s
 *     `RepliesBlock`.
 *
 * No new section captions were invented: the pre-existing per-category titles
 * already label their own picker, and a caption on the one-row gate group would
 * need a brand-new string (plus es-419 / pt-BR) for what is a styling change.
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.content_filters_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // One-row group: the account-level adult-content gate. The group's
            // single item IS the checked value, so the row slot receives it
            // directly and no separate row model is needed.
            NubecitaListGroup(items = persistentListOf(state.adultContentEnabled)) { checked, shapes ->
                AdultGateRowContent(
                    checked = checked,
                    shapes = shapes,
                    onToggle = { onEvent(ContentFiltersEvent.AdultContentToggled(it)) },
                )
            }

            state.categories.forEach { row ->
                CategoryBlock(
                    row = row,
                    onSelect = { onEvent(ContentFiltersEvent.VisibilitySelected(row.label, it)) },
                )
            }
        }
    }
}

/**
 * The "Enable adult content" master switch as an M3 Expressive segmented row.
 *
 * The row itself owns the toggle (via [NubecitaListItem]'s `onCheckedChange`
 * mode, which applies `Modifier.toggleable(role = Role.Switch)`), so a screen
 * reader announces the label together with the on/off state. The trailing
 * [Switch] is display-only (`onCheckedChange = null`) — one interactive node,
 * not two. The pre-migration row had the gesture on the bare `Switch` with no
 * label attached to it at all, so this also fixes a "switch, off" announcement.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AdultGateRowContent(
    checked: Boolean,
    shapes: ListItemShapes,
    onToggle: (Boolean) -> Unit,
) {
    NubecitaListItem(
        shapes = shapes,
        headlineContent = {
            Text(
                text = stringResource(R.string.content_filters_enable_adult),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        checked = checked,
        onCheckedChange = onToggle,
        supportingContent = {
            Text(
                text = stringResource(R.string.content_filters_enable_adult_supporting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

/**
 * One content-label category: its title and description in a nested 4dp Column
 * ABOVE the picker, then the Show/Warn/Hide [LabelVisibilityGroup].
 *
 * The text is deliberately NOT the group's caption slot and not a list row: the
 * description explains the choice that follows it, and a caption drawn
 * immediately above the segments would strand it where it reads as belonging to
 * the next block. Same layout as `FeedPreferencesScreen`'s `RepliesBlock`.
 */
@Composable
private fun CategoryBlock(
    row: CategoryRowUi,
    onSelect: (LabelVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(row.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(row.descriptionRes),
                // bodyMedium, matching the gate row above: the segmented row takes
                // M3's default supporting-text size, and leaving these at bodySmall
                // made one description on the screen visibly larger than the rest.
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LabelVisibilityGroup(
            selected = row.visibility,
            enabled = row.enabled,
            onSelect = onSelect,
        )
    }
}

/**
 * Single-select Show/Warn/Hide picker, built as an M3 Expressive **connected
 * button group**: a row of [ToggleButton]s whose leading / middle / trailing
 * segments take asymmetric connected shapes ([ButtonGroupDefaults]) so they read
 * as one joined control. Tapping the already-selected segment is a no-op; every
 * segment is disabled (greyed) when [enabled] is false (an adult category with
 * the master gate off).
 *
 * Deliberately kept a button group through the `NubecitaListGroup` migration.
 * `FeedPreferencesScreen`'s reply picker became a radio list because "People you
 * follow" wrapped to two lines against "All" / "None" (and pt-BR is longer
 * still), leaving the equal-width segments ragged. Show / Warn / Hide are short
 * and comparable in all three locales, which is precisely what a connected
 * button group is for — and its per-segment `enabled` greying is the affordance
 * that communicates the master gate.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LabelVisibilityGroup(
    selected: LabelVisibility,
    enabled: Boolean,
    onSelect: (LabelVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = VISIBILITY_ORDER.map { it to stringResource(it.labelRes()) }
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
                enabled = enabled,
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
