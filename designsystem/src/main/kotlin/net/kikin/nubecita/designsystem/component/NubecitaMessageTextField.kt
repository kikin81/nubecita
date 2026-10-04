package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Material 3 Expressive pill-shaped text field for chat and message input.
 *
 * Uses Compose Foundation's modern [TextFieldState] and a pill shape
 * ([RoundedCornerShape] with 24.dp corners) that looks smooth on a single line
 * and preserves rounded corners as text wraps up to [lineLimits].
 */
@Composable
fun NubecitaMessageTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    shape: Shape = RoundedCornerShape(24.dp),
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
    keyboardOptions: KeyboardOptions =
        KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Send,
        ),
    onKeyboardAction: KeyboardActionHandler? = null,
    colors: TextFieldColors =
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
) {
    OutlinedTextField(
        state = state,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        readOnly = readOnly,
        shape = shape,
        lineLimits = lineLimits,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        colors = colors,
    )
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun NubecitaMessageTextFieldEmptyPreview() {
    NubecitaTheme {
        NubecitaMessageTextField(
            state = TextFieldState(),
            placeholder = { Text("Message") },
        )
    }
}

@Preview(name = "With Text", showBackground = true)
@Composable
private fun NubecitaMessageTextFieldWithTextPreview() {
    NubecitaTheme {
        NubecitaMessageTextField(
            state = TextFieldState("Hello from Nubecita!"),
            placeholder = { Text("Message") },
        )
    }
}
