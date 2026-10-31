package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import net.kikin.nubecita.feature.composer.impl.R

/**
 * The composer's writing surface: a borderless field set straight on the
 * screen's `surface`, with only a placeholder — no outline, no floating label.
 *
 * Driven by the ViewModel-owned [TextFieldState] (the composer's sanctioned
 * editor exception), so the IME and cursor never round-trip through the
 * reducer. Over-limit is signalled by the character counter, not the field.
 */
@Composable
internal fun ComposerTextField(
    textFieldState: TextFieldState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.bodyLarge
    // The visible "Compose post" label is gone, so give the field the same name
    // for screen readers; otherwise it reads as an unnamed edit box.
    val fieldLabel = stringResource(R.string.composer_text_field_label)
    BasicTextField(
        state = textFieldState,
        modifier = modifier.semantics { contentDescription = fieldLabel },
        enabled = enabled,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
        decorator = { innerTextField ->
            Box {
                if (textFieldState.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.composer_text_field_placeholder),
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}
