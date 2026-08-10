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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.preferences.AutoplayPreference
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaListGroup
import net.kikin.nubecita.designsystem.component.NubecitaListItem
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Stateful Media and animations screen. Hoists [MediaAndAnimationsViewModel],
 * surfaces its save-error effect as a snackbar, and projects the state through
 * the stateless [MediaAndAnimationsContent].
 *
 * Presented full-screen on phone and inside the Settings dialog (content-swap)
 * on tablet — the route is tagged `adaptiveDialog()` in
 * `SettingsNavigationModule`.
 *
 * The suppression matches [AppearanceScreen]: what crosses into `*Content` is
 * `state` plus a bound `handleEvent` reference, not the ViewModel itself, so the
 * forwarding the check exists to prevent is not happening here.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun MediaAndAnimationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaAndAnimationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.media_save_error)
    val currentSaveError by rememberUpdatedState(saveErrorMessage)

    LaunchedEffect(viewModel) {
        // Effect scope captured so each snackbar shows in its own child job —
        // dismissing the current one to show a fresh one must not cancel the
        // collector (mirrors AppearanceScreen / FeedPreferencesScreen).
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                MediaAndAnimationsEffect.ShowSaveError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(currentSaveError)
                    }
            }
        }
    }

    MediaAndAnimationsContent(
        state = state,
        onEvent = onEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless body, extracted so previews and screenshot tests drive the layout
 * without a Hilt graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaAndAnimationsContent(
    state: MediaAndAnimationsState,
    onEvent: (MediaAndAnimationsEvent) -> Unit,
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
                title = { Text(stringResource(R.string.media_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.media_back_content_desc),
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
                text = stringResource(R.string.media_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Text(
                text = stringResource(R.string.media_autoplay_video_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Mutually exclusive, so a single-select group: `singleSelect = true`
            // adds selectableGroup(), which is what makes a screen reader announce
            // "1 of 3" instead of three unrelated radio buttons. Driven off
            // AutoplayPreference.entries, whose declaration order IS the display
            // order, so a future option appears here with no change to this file.
            NubecitaListGroup(items = AUTOPLAY_OPTIONS, singleSelect = true) { option, shapes ->
                NubecitaListItem(
                    shapes = shapes,
                    headlineContent = { Text(stringResource(option.labelRes())) },
                    supportingContent = { Text(stringResource(option.supportingRes())) },
                    selected = option == state.autoplay,
                    // Fires on EVERY tap including the selected row; the ViewModel
                    // drops the no-op write. Deliberately not guarded here.
                    onSelect = { onEvent(MediaAndAnimationsEvent.AutoplaySelected(option)) },
                    leadingContent = {
                        // Display-only: the row owns the gesture and the state.
                        RadioButton(selected = option == state.autoplay, onClick = null)
                    },
                )
            }

            Text(
                text = stringResource(R.string.media_animated_images_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            NubecitaListGroup(items = GIF_ROW) { _, shapes ->
                NubecitaListItem(
                    shapes = shapes,
                    headlineContent = { Text(stringResource(R.string.media_autoplay_gifs)) },
                    supportingContent = { Text(stringResource(R.string.media_autoplay_gifs_supporting)) },
                    checked = state.autoplayGifs,
                    onCheckedChange = { enabled ->
                        onEvent(MediaAndAnimationsEvent.AutoplayGifsToggled(enabled))
                    },
                    trailingContent = {
                        // Display-only: the row owns the toggle gesture.
                        Switch(checked = state.autoplayGifs, onCheckedChange = null)
                    },
                )
            }
        }
    }
}

private val AUTOPLAY_OPTIONS = AutoplayPreference.entries.toImmutableList()

// A one-item group so the single switch gets the same rounded-card treatment as
// the option list above; NubecitaListGroup shapes by position, so a lone item
// reads as a complete card rather than a fragment.
private val GIF_ROW = persistentListOf(Unit)

/**
 * The option's display label. `internal` because the Settings root row captions
 * itself with the active choice, and two copies of this mapping would drift.
 */
@StringRes
internal fun AutoplayPreference.labelRes(): Int =
    when (this) {
        AutoplayPreference.ALWAYS -> R.string.media_autoplay_always
        AutoplayPreference.WIFI_ONLY -> R.string.media_autoplay_wifi_only
        AutoplayPreference.NEVER -> R.string.media_autoplay_never
    }

@StringRes
private fun AutoplayPreference.supportingRes(): Int =
    when (this) {
        AutoplayPreference.ALWAYS -> R.string.media_autoplay_always_supporting
        AutoplayPreference.WIFI_ONLY -> R.string.media_autoplay_wifi_only_supporting
        AutoplayPreference.NEVER -> R.string.media_autoplay_never_supporting
    }

@Preview(name = "Media — Always")
@Composable
private fun MediaAndAnimationsAlwaysPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            MediaAndAnimationsContent(
                state = MediaAndAnimationsState(autoplay = AutoplayPreference.ALWAYS, autoplayGifs = true),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Media — Wi-Fi only, GIFs off")
@Composable
private fun MediaAndAnimationsWifiOnlyPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            MediaAndAnimationsContent(
                state = MediaAndAnimationsState(autoplay = AutoplayPreference.WIFI_ONLY, autoplayGifs = false),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Media — Never, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaAndAnimationsNeverDarkPreview() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        Surface {
            MediaAndAnimationsContent(
                state = MediaAndAnimationsState(autoplay = AutoplayPreference.NEVER, autoplayGifs = true),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
