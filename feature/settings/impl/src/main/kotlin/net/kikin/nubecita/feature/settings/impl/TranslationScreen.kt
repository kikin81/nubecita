package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.data.models.DownloadedLanguage
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaLanguagePicker
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
    // Which picker is open, if any. Screen-local rather than `UiState`: an
    // open sheet is a Compose runtime concern the same way scroll position is,
    // and routing it through the reducer would make a stored preference and a
    // transient sheet share a lifetime they do not share.
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

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
                chooseLabel = stringResource(R.string.translation_choose_auto_languages),
                onRemove = { tag ->
                    onEvent(TranslationEvent.AutoTranslateLanguagesChanged(state.autoTranslate.toSet() - tag))
                },
                onAdd = { picker = PickerTarget.Auto },
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
                chooseLabel = stringResource(R.string.translation_choose_never_languages),
                onRemove = { tag ->
                    onEvent(TranslationEvent.NeverTranslateLanguagesChanged(state.neverTranslate.toSet() - tag))
                },
                onAdd = { picker = PickerTarget.Never },
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

        picker?.let { target ->
            val chosen =
                when (target) {
                    PickerTarget.Auto -> state.autoTranslate
                    PickerTarget.Never -> state.neverTranslate
                }
            LanguageSelectionDialog(
                available = state.availableLanguages,
                initialSelection = chosen,
                title =
                    when (target) {
                        PickerTarget.Auto -> stringResource(R.string.translation_choose_auto_languages)
                        PickerTarget.Never -> stringResource(R.string.translation_choose_never_languages)
                    },
                // The confirmed set replaces the list outright. A language in
                // the *other* list is still offered, because moving it is a
                // deliberate act and the ViewModel already drops it from the
                // one it left — the two lists are contradictory by definition.
                onConfirm = { tags ->
                    when (target) {
                        PickerTarget.Auto ->
                            onEvent(TranslationEvent.AutoTranslateLanguagesChanged(tags.toSet()))
                        PickerTarget.Never ->
                            onEvent(TranslationEvent.NeverTranslateLanguagesChanged(tags.toSet()))
                    }
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }
    }
}

/** Which of the two language lists an open picker is adding to. */
private enum class PickerTarget {
    Auto,
    Never,
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

/**
 * Hosts the shared [NubecitaLanguagePicker] for one of the two language lists.
 *
 * A Dialog rather than a ModalBottomSheet: a sheet renders in its own window
 * that does not resize for the IME, so the keyboard covered the very results
 * the picker's search field filters (seen on a Fold 8). A dialog window
 * resizes. It also avoids a sheet's drag-to-dismiss fighting the list's
 * scroll, which is why EmojiPickerDialog made the same call.
 *
 * The draft is seeded with what the list already holds, so the picker opens
 * showing the current selection pinned at the top and Done commits the whole
 * set — the same editing model the composer uses, rather than an add-only
 * flow that would leave removal to a separate control.
 */
@Composable
private fun LanguageSelectionDialog(
    available: ImmutableList<String>,
    initialSelection: ImmutableList<String>,
    title: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Held as the ImmutableList the picker wants, not a List converted on each
    // pass: the picker recomposes on every keystroke in its search field, and
    // converting there allocated a fresh persistent list per keystroke to say
    // the same thing. Toggling is the only time the selection actually
    // changes, so that is the only time it needs rebuilding.
    var draft by remember(initialSelection) { mutableStateOf(initialSelection) }
    val deviceLocaleTag = LocalLocale.current.platformLocale.toLanguageTag()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                NubecitaLanguagePicker(
                    allTags = available,
                    draftSelection = draft,
                    deviceLocaleTag = deviceLocaleTag,
                    onToggle = { tag ->
                        draft = (if (tag in draft) draft - tag else draft + tag).toImmutableList()
                    },
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                    // No cap here: a reader may translate as many languages as
                    // they read. The composer's limit is a post-record
                    // constraint, not a picker one.
                )
            }
        }
    }
}

@Composable
private fun LanguageList(
    languages: ImmutableList<String>,
    emptyText: String,
    chooseLabel: String,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (languages.isEmpty()) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            AddLanguageRow(label = chooseLabel, onClick = onAdd)
        }
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageRows(languages = displayOrdered(languages), onRemove = onRemove)
        AddLanguageRow(label = chooseLabel, onClick = onAdd)
    }
}

/**
 * [languages] ordered the way the reader reads them.
 *
 * The ViewModel sorts by bare subtag, which is stable and fine for state but
 * meaningless on screen: `af, ar, bn, de, sq` renders as Afrikaans, Arabic,
 * Bangla, German, **Albanian** — alphabetical in a spelling the reader never
 * sees. Ordering belongs here, where the locale that names them is available.
 */
@Composable
private fun displayOrdered(languages: ImmutableList<String>): ImmutableList<String> {
    val locale = LocalLocale.current.platformLocale
    return remember(languages, locale) {
        languages.sortedBy { tag -> displayNameIn(tag, locale).lowercase(locale) }.toImmutableList()
    }
}

/** Non-composable twin of [displayName], for sorting off the composition. */
private fun displayNameIn(
    tag: String,
    locale: Locale,
): String = Locale.forLanguageTag(tag).getDisplayLanguage(locale).ifBlank { tag }

/** The add affordance, in its own single-row group so the shapes match. */
@Composable
private fun AddLanguageRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NubecitaListGroup(items = ADD_ROW, modifier = modifier) { _, shapes ->
        NubecitaListItem(
            shapes = shapes,
            headlineContent = { Text(label) },
            onClick = onClick,
            leadingContent = {
                NubecitaIcon(name = NubecitaIconName.Add, contentDescription = null)
            },
        )
    }
}

@Composable
private fun LanguageRows(
    languages: ImmutableList<String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
    // Resolved against the Compose-propagated locale, not Locale.getDefault().
    // Those differ whenever the reader has set a per-app language, which is
    // exactly the reader most likely to be looking at this screen — the names
    // would come back in the system language rather than the app's.
    val locale = LocalLocale.current.platformLocale
    return remember(tag, locale) {
        Locale.forLanguageTag(tag).getDisplayLanguage(locale).ifBlank { tag }
    }
}

/** Single-row groups still go through [NubecitaListGroup] so shapes match. */
private val AUTO_ROW = persistentListOf(Unit)

private val ADD_ROW = persistentListOf(Unit)

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
