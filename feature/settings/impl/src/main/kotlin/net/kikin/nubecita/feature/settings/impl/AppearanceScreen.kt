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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.preferences.ThemePreference
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaListGroup
import net.kikin.nubecita.designsystem.component.NubecitaListItem
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Stateful Appearance screen (`nubecita-wqb8.4`). Hoists [AppearanceViewModel],
 * surfaces its save-error effect as a snackbar, and projects
 * [AppearanceState] through the stateless [AppearanceContent].
 *
 * Presented full-screen on phone and inside the Settings dialog (content-swap)
 * on tablet — the route is tagged `adaptiveDialog()` in
 * `SettingsNavigationModule`. `onNavigateTo` is unused today (no sub-routes) but
 * kept for parity with the other Settings sub-screens.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding", "UnusedParameter")
@Composable
internal fun AppearanceScreen(
    onBack: () -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.appearance_save_error)
    val currentSaveError by rememberUpdatedState(saveErrorMessage)

    LaunchedEffect(viewModel) {
        // Capture the effect scope so each snackbar shows in its own child job:
        // dismissing the current snackbar to show a fresh one must not cancel
        // the collector itself (mirrors FeedPreferencesScreen).
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                AppearanceEffect.ShowSaveError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(currentSaveError)
                    }
            }
        }
    }

    AppearanceContent(
        state = state,
        onEvent = onEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless Appearance body. Extracted so preview / screenshot-test composables
 * can drive the layout without a Hilt graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceContent(
    state: AppearanceState,
    onEvent: (AppearanceEvent) -> Unit,
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
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.appearance_back_content_desc),
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
                text = stringResource(R.string.appearance_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            // One mutually-exclusive choice, so a single-select group:
            // `singleSelect = true` adds selectableGroup(), which is what makes a
            // screen reader announce "1 of 3" rather than three unrelated radio
            // buttons. Driven off ThemePreference.entries so a future CUSTOM
            // option appears here with no change to this screen — the enum's
            // declaration order IS the required display order.
            NubecitaListGroup(items = THEME_OPTIONS, singleSelect = true) { theme, shapes ->
                NubecitaListItem(
                    shapes = shapes,
                    headlineContent = { Text(stringResource(theme.labelRes())) },
                    supportingContent =
                        theme.supportingRes()?.let { supporting ->
                            { Text(stringResource(supporting)) }
                        },
                    selected = theme == state.selected,
                    // Fires on EVERY tap, including the already-selected row. The
                    // ViewModel drops the no-op write; deliberately not guarded
                    // here, because a UI-side guard is what vanished when a
                    // comparable control last changed (nubecita-239m).
                    onSelect = { onEvent(AppearanceEvent.ThemeSelected(theme)) },
                    leadingContent = {
                        // Display-only: the row owns the gesture and the state.
                        RadioButton(selected = theme == state.selected, onClick = null)
                    },
                )
            }
        }
    }
}

private val THEME_OPTIONS = ThemePreference.entries.toImmutableList()

@StringRes
private fun ThemePreference.labelRes(): Int =
    when (this) {
        ThemePreference.DYNAMIC -> R.string.appearance_theme_dynamic
        ThemePreference.LIGHT -> R.string.appearance_theme_light
        ThemePreference.DARK -> R.string.appearance_theme_dark
    }

/**
 * Only `Dynamic` carries supporting text — it is the one option whose behaviour
 * isn't obvious from its label, since it means both "follow the system" and
 * "take colours from the wallpaper". Explaining `Light` and `Dark` would be
 * noise.
 */
@StringRes
private fun ThemePreference.supportingRes(): Int? =
    when (this) {
        ThemePreference.DYNAMIC -> R.string.appearance_theme_dynamic_supporting
        ThemePreference.LIGHT, ThemePreference.DARK -> null
    }

@Preview(name = "Appearance — Dynamic")
@Composable
private fun AppearanceContentDynamicPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            AppearanceContent(
                state = AppearanceState(selected = ThemePreference.DYNAMIC),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Appearance — Light")
@Composable
private fun AppearanceContentLightPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            AppearanceContent(
                state = AppearanceState(selected = ThemePreference.LIGHT),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Appearance — Dark selected", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppearanceContentDarkPreview() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        Surface {
            AppearanceContent(
                state = AppearanceState(selected = ThemePreference.DARK),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
