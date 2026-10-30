package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [NubecitaTextField]: the empty field (label + placeholder +
 * supporting text), a filled field, and the error state.
 */

@PreviewTest
@Preview(name = "empty-light", showBackground = true)
@Preview(name = "empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaTextFieldEmptyScreenshot() {
    NubecitaTextField(
        state = TextFieldState(),
        label = "Members",
        placeholder = "Search by name or handle",
        supportingText = { Text("You can add people who follow you") },
    )
}

@PreviewTest
@Preview(name = "filled-light", showBackground = true)
@Preview(name = "filled-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaTextFieldFilledScreenshot() {
    NubecitaTextField(
        state = TextFieldState("Book club"),
        label = "Group name",
        supportingText = { Text("Up to 64 characters") },
    )
}

@PreviewTest
@Preview(name = "error-light", showBackground = true)
@Preview(name = "error-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaTextFieldErrorScreenshot() {
    NubecitaTextField(
        state = TextFieldState("A group name that keeps going and going well past the limit"),
        label = "Group name",
        isError = true,
        supportingText = { Text("70 / 64") },
    )
}
