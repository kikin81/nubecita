package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.data.models.DownloadedLanguage
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaListGroup
import net.kikin.nubecita.designsystem.component.NubecitaListItem
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import java.util.Locale

/**
 * Stateful Translation screen. Hoists [TranslationViewModel], surfaces its
 * save-error effect as a snackbar, and projects state through the stateless
 * [TranslationContent].
 *
 * The suppression matches [MediaAndAnimationsScreen]: what crosses into
 * `*Content` is `state` plus a bound `handleEvent` reference, not the
 * ViewModel.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun TranslationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TranslationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.translation_save_error)
    val currentSaveError by rememberUpdatedState(saveErrorMessage)

    LaunchedEffect(viewModel) {
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                TranslationEffect.ShowSaveError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(currentSaveError)
                    }
            }
        }
    }

    TranslationContent(
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
internal fun TranslationContent(
    state: TranslationState,
    onEvent: (TranslationEvent) -> Unit,
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
                title = { Text(stringResource(R.string.translation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.translation_back_content_desc),
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
                text = stringResource(R.string.translation_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )

            SectionHeader(stringResource(R.string.translation_auto_header))

            NubecitaListGroup(items = AUTO_ROW) { _, shapes ->
                NubecitaListItem(
                    shapes = shapes,
                    headlineContent = { Text(stringResource(R.string.translation_auto_label)) },
                    supportingContent = { Text(stringResource(R.string.translation_auto_supporting)) },
                    checked = state.autoTranslateEnabled,
                    onCheckedChange = { enabled -> onEvent(TranslationEvent.AutoTranslateToggled(enabled)) },
                    trailingContent = {
                        // Display-only: the row owns the toggle gesture.
                        Switch(checked = state.autoTranslateEnabled, onCheckedChange = null)
                    },
                )
            }

            SectionHeader(
                text = stringResource(R.string.translation_auto_languages_header),
                topPadding = 8.dp,
            )
            LanguageList(
                languages = state.autoTranslate,
                emptyText = stringResource(R.string.translation_auto_languages_empty),
                onRemove = { tag ->
                    onEvent(TranslationEvent.AutoTranslateLanguagesChanged(state.autoTranslate.toSet() - tag))
                },
            )

            SectionHeader(
                text = stringResource(R.string.translation_never_header),
                topPadding = 8.dp,
            )
            Text(
                text = stringResource(R.string.translation_never_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LanguageList(
                languages = state.neverTranslate,
                emptyText = stringResource(R.string.translation_never_empty),
                onRemove = { tag ->
                    onEvent(TranslationEvent.NeverTranslateLanguagesChanged(state.neverTranslate.toSet() - tag))
                },
            )

            SectionHeader(
                text = stringResource(R.string.translation_storage_header),
                topPadding = 8.dp,
            )
            StorageSection(
                downloaded = state.downloaded,
                totalBytes = state.totalBytes,
                onDelete = { tag -> onEvent(TranslationEvent.LanguageDeleted(tag)) },
            )
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = topPadding),
    )
}

@Composable
private fun LanguageList(
    languages: ImmutableList<String>,
    emptyText: String,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (languages.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 4.dp),
        )
        return
    }
    NubecitaListGroup(items = languages, modifier = modifier) { tag, shapes ->
        NubecitaListItem(
            shapes = shapes,
            headlineContent = { Text(displayName(tag)) },
            trailingContent = {
                IconButton(onClick = { onRemove(tag) }) {
                    NubecitaIcon(
                        name = NubecitaIconName.Close,
                        contentDescription = stringResource(R.string.translation_delete_content_desc, displayName(tag)),
                    )
                }
            },
        )
    }
}

@Composable
private fun StorageSection(
    downloaded: ImmutableList<DownloadedLanguage>,
    totalBytes: Long,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.translation_storage_wifi_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (downloaded.isEmpty()) {
            Text(
                text = stringResource(R.string.translation_storage_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        NubecitaListGroup(items = downloaded) { pack, shapes ->
            NubecitaListItem(
                shapes = shapes,
                headlineContent = { Text(displayName(pack.languageTag)) },
                // The real measured size, not a published figure — a pack is
                // ~45MB on device (62MB for Japanese), not the ~30MB the docs
                // suggest, because both directions of the pair are stored.
                supportingContent = { Text(Formatter.formatShortFileSize(context, pack.sizeBytes)) },
                trailingContent = {
                    IconButton(onClick = { onDelete(pack.languageTag) }) {
                        NubecitaIcon(
                            name = NubecitaIconName.Delete,
                            contentDescription =
                                stringResource(
                                    R.string.translation_delete_content_desc,
                                    displayName(pack.languageTag),
                                ),
                        )
                    }
                },
            )
        }
        Text(
            text = stringResource(R.string.translation_storage_total, Formatter.formatShortFileSize(context, totalBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A language tag as the reader's own locale names it.
 *
 * Keyed on the configuration locale so the names re-resolve when the reader
 * changes language, rather than being captured once at first composition.
 */
@Composable
private fun displayName(tag: String): String {
    val configuration = LocalConfiguration.current
    return remember(tag, configuration) {
        val locale = Locale.forLanguageTag(tag)
        locale.getDisplayLanguage(Locale.getDefault()).ifBlank { tag }
    }
}

/** Single-row groups still go through [NubecitaListGroup] so shapes match. */
private val AUTO_ROW = persistentListOf(Unit)

@Preview(name = "translation-empty", showBackground = true)
@Preview(name = "translation-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TranslationEmptyPreview() {
    NubecitaTheme {
        Surface {
            TranslationContent(state = TranslationState(), onEvent = {}, onBack = {})
        }
    }
}

@Preview(name = "translation-populated", showBackground = true)
@Preview(name = "translation-populated-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TranslationPopulatedPreview() {
    NubecitaTheme {
        Surface {
            TranslationContent(
                state =
                    TranslationState(
                        autoTranslateEnabled = true,
                        autoTranslate = persistentListOf("ja", "de"),
                        neverTranslate = persistentListOf("es"),
                        downloaded =
                            listOf(
                                // The sizes actually measured on device.
                                DownloadedLanguage("ja", 62_000_000),
                                DownloadedLanguage("de", 45_600_000),
                            ).toImmutableList(),
                    ),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
