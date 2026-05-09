package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.feature.composer.impl.ComposerViewModel
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Stateless picker content — the `LazyColumn` of language rows + a
 * search field at the top + a Done/Cancel footer at the bottom.
 *
 * `draftSelection` is the picker's local working selection
 * (independent of `ComposerState.selectedLangs`). [onToggle] flips
 * a single tag's checkbox; [onConfirm] commits the final list back to
 * the VM via `ComposerEvent.LanguageSelectionConfirmed`; [onDismiss]
 * is fired for Cancel + scrim-tap + drag-down + back-press.
 *
 * Search filters by both BCP-47 tag (`"en"` matches English) and
 * localized display name (`"anglais"` matches English when the JVM
 * locale is French). Row order:
 *
 * 1. Currently-selected tags (alphabetical among themselves).
 * 2. The device-locale tag, if not selected.
 * 3. Everything else, alphabetical by display name in the JVM locale.
 *
 * Selection-pinning makes re-opens predictable. The cap of 3 is
 * enforced by rendering unchecked rows with `enabled = false` once
 * `draftSelection.size == ComposerViewModel.MAX_LANGS` — the M3
 * disabled-tonal idiom.
 */
@Composable
internal fun LanguagePickerContent(
    allTags: ImmutableList<String>,
    draftSelection: ImmutableList<String>,
    deviceLocaleTag: String,
    onToggle: (String) -> Unit,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The configuration's locale list is part of the cache key so the
    // sort order + filter matching refresh on a runtime locale change
    // (display names come from `Locale.getDefault()`, which tracks the
    // configuration). Without it the cache could stay stuck on the
    // pre-change ordering until another input key happens to change.
    val locales = LocalConfiguration.current.locales
    val visibleTags =
        remember(allTags, draftSelection, deviceLocaleTag, query, locales) {
            val sorted = sortPickerTags(allTags, draftSelection, deviceLocaleTag)
            if (query.isBlank()) {
                sorted
            } else {
                sorted.filter { matchesPickerQuery(tag = it, query = query) }.toImmutableList()
            }
        }
    val capReached = draftSelection.size >= ComposerViewModel.MAX_LANGS

    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    // testTag for instrumentation tests; the real
                    // a11y description comes from the localized
                    // string resource below so screen readers
                    // announce a translated label.
                    .testTag(SEARCH_FIELD_TEST_TAG),
            singleLine = true,
            label = {
                Text(text = stringResource(R.string.composer_language_search_label))
            },
            placeholder = {
                // Empty placeholder reserved for the future where the
                // search field's default state shows a tip; right now
                // the contentDescription on the field itself carries
                // the a11y narration via the label.
            },
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
        ) {
            items(items = visibleTags, key = { it }) { tag ->
                val selected = draftSelection.contains(tag)
                LanguageRow(
                    tag = tag,
                    selected = selected,
                    enabled = selected || !capReached,
                    onToggle = { onToggle(tag) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.composer_language_picker_cancel))
            }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Button(onClick = { onConfirm(draftSelection.toList()) }) {
                Text(text = stringResource(R.string.composer_language_picker_done))
            }
        }
    }
}

/**
 * Test tag for the language picker's search input. Instrumentation
 * tests find the field via this tag rather than the localized
 * contentDescription so assertions stay locale-stable.
 */
internal const val SEARCH_FIELD_TEST_TAG: String = "language_picker_search_field"

@Composable
private fun LanguageRow(
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    // The whole row carries the toggleable interaction (and the
    // accompanying Disabled semantic) — bigger tap target for the
    // user, and `enabled = false` propagates to every child node so
    // assertions like `onNodeWithText("French").assertIsNotEnabled()`
    // resolve correctly. The Checkbox switches to display-only
    // (onCheckedChange = null) so the Row is the single source of
    // toggle events.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            enabled = enabled,
        )
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(text = languageDisplayName(tag))
    }
}
