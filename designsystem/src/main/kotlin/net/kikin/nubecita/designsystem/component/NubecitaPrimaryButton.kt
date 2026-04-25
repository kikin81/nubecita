package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Brand primary button with built-in loading state.
 *
 * Wraps Material 3's filled [Button] and centralizes the
 * [CircularWavyProgressIndicator] (M3 Expressive) loading pattern so every
 * screen that triggers an async action gets the same wavy spinner without
 * each call site re-importing the experimental API and re-sizing the
 * indicator. When [isLoading] is true the button is non-interactive, the
 * label is hidden, and the wavy indicator takes its place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NubecitaPrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        // Preserve the button's accessible label across both visual states. When isLoading
        // is true the Text is replaced with a CircularWavyProgressIndicator, which has no
        // intrinsic label; without this semantics override TalkBack would announce only
        // "loading" / "progress indicator" with no hint about which action is loading.
        modifier =
            modifier.semantics {
                contentDescription = text
            },
    ) {
        if (isLoading) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(LOADING_INDICATOR_SIZE),
            )
        } else {
            Text(text)
        }
    }
}

private val LOADING_INDICATOR_SIZE = 20.dp

@Preview(name = "Idle", showBackground = true)
@Composable
private fun NubecitaPrimaryButtonIdlePreview() {
    NubecitaTheme {
        NubecitaPrimaryButton(onClick = {}, text = "Continue")
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun NubecitaPrimaryButtonLoadingPreview() {
    NubecitaTheme {
        NubecitaPrimaryButton(onClick = {}, text = "Continue", isLoading = true)
    }
}

@Preview(name = "Disabled", showBackground = true)
@Composable
private fun NubecitaPrimaryButtonDisabledPreview() {
    NubecitaTheme {
        NubecitaPrimaryButton(onClick = {}, text = "Continue", enabled = false)
    }
}
