package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Tap target for the per-post language picker. Shows the localized
 * display name of either the user's explicit selection
 * ([selectedLangs] non-null) or the device-locale fallback
 * ([deviceLocaleTag], used when [selectedLangs] is null).
 *
 * Multi-selection (>= 2 tags) shows the first selected tag's display
 * name plus a `+N` overflow ("English +1", "English +2") — predictable
 * chip width regardless of how many tags are selected. The full list
 * is revealed when the user re-opens the picker.
 *
 * The 200dp width cap defends against pathological display names
 * (`"Norwegian Bokmål"` and similar). Beyond the cap the label
 * truncates with `…`; the user's full selection is still in
 * [selectedLangs] and visible the next time the picker opens.
 */
@Composable
internal fun ComposerLanguageChip(
    selectedLangs: List<String>?,
    deviceLocaleTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read the empty-state label here (in a Composable scope) and pass
    // the resolved string into the pure helper, instead of resolving
    // resources from inside `chipLabelFor`. Keeps the helper unit-
    // testable and gives the chip-label expression its correct
    // recomposition keys.
    val emptyLabel = stringResource(R.string.composer_language_chip_none)
    val label =
        remember(selectedLangs, deviceLocaleTag, emptyLabel) {
            chipLabelFor(selectedLangs, deviceLocaleTag, emptyLabel)
        }
    AssistChip(
        onClick = onClick,
        modifier = modifier.widthIn(max = 200.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        label = {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}

/**
 * Pure label-resolution rule for [ComposerLanguageChip]. Exposed
 * `internal` so the JVM unit test can pin the contract without booting
 * the Compose runtime; the composable is a 4-line wrapper that calls
 * this function inside a `remember(...)`.
 *
 * `null` and `emptyList()` are deliberately distinct — `null` means
 * "user hasn't touched the picker; createPost will derive the device-
 * locale default per wtq.12", so the chip mirrors that device-locale
 * display name. `emptyList()` means "user explicitly chose no
 * language; createPost will omit the langs field entirely", so the
 * chip surfaces a localized "No language" string supplied by the
 * caller via [emptyLabel]. Collapsing both to the device-locale
 * label would make the chip lie about what's about to be sent.
 *
 * The multi-tag overflow form is `"<first> +N"` where N is
 * `selectedLangs.size - 1`. The 200dp width cap on the composable
 * truncates the resolved label with `…` if it overflows.
 */
internal fun chipLabelFor(
    selectedLangs: List<String>?,
    deviceLocaleTag: String,
    emptyLabel: String,
): String =
    when {
        selectedLangs == null -> languageDisplayName(deviceLocaleTag)
        selectedLangs.isEmpty() -> emptyLabel
        else -> {
            val first = languageDisplayName(selectedLangs.first())
            when (val extras = selectedLangs.size - 1) {
                0 -> first
                else -> "$first +$extras"
            }
        }
    }
