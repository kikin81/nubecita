package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.search.impl.R

/**
 * Search bar: a single-line `OutlinedTextField(state = textFieldState, ...)`
 * with a leading search icon, a trailing clear-X (rendered only when the
 * field is non-blank), and IME action `Search` wired to [onSubmit].
 *
 * The composable is stateless beyond the externally-owned [textFieldState];
 * [isQueryBlank] is read from `SearchScreenViewState` so the trailing icon
 * can hide without sampling Snapshot state from inside the row. Icons use
 * the design-system [NubecitaIcon] (Material Symbols Rounded variable
 * font) per project convention — see
 * `docs/superpowers/specs/2026-05-10-material-symbols-rounded-migration-design.md`.
 */
@Composable
internal fun SearchInputRow(
    textFieldState: TextFieldState,
    isQueryBlank: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = textFieldState,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_input_hint)) },
        leadingIcon = {
            NubecitaIcon(
                name = NubecitaIconName.Search,
                contentDescription = stringResource(R.string.search_input_leading_icon_content_desc),
            )
        },
        trailingIcon = {
            if (!isQueryBlank) {
                IconButton(onClick = { textFieldState.clearText() }) {
                    NubecitaIcon(
                        name = NubecitaIconName.Close,
                        contentDescription = stringResource(R.string.search_input_clear_content_desc),
                    )
                }
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = KeyboardActionHandler { onSubmit() },
    )
}
