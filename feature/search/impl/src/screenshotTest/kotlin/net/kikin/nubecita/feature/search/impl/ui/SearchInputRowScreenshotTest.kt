package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "search-input-blank-light", showBackground = true)
@Preview(name = "search-input-blank-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchInputRowBlankScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchInputRow(
                textFieldState = rememberTextFieldState(),
                isQueryBlank = true,
                onSubmit = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "search-input-typed-light", showBackground = true)
@Preview(name = "search-input-typed-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchInputRowTypedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchInputRow(
                textFieldState = rememberTextFieldState(initialText = "kotlin"),
                isQueryBlank = false,
                onSubmit = {},
            )
        }
    }
}
