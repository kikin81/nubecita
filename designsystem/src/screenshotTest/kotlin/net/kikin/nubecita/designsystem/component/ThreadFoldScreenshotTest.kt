package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [ThreadFold] in light + dark themes, with
 * and without a `count`. Four baselines total.
 *
 * The connector line that runs through the fold is part of the fold's
 * own draw — covering it across both themes verifies the
 * outlineVariant color resolves correctly per theme.
 */

private const val WIDTH_DP: Int = 360

@Composable
private fun FoldPreviewSurface(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        content()
    }
}

@PreviewTest
@Preview(name = "no-count-light", widthDp = WIDTH_DP)
@Composable
private fun ThreadFoldNoCountLight() {
    NubecitaTheme(dynamicColor = false) {
        FoldPreviewSurface {
            ThreadFold(count = 0)
        }
    }
}

@PreviewTest
@Preview(name = "no-count-dark", widthDp = WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadFoldNoCountDark() {
    NubecitaTheme(dynamicColor = false) {
        FoldPreviewSurface {
            ThreadFold(count = 0)
        }
    }
}

@PreviewTest
@Preview(name = "with-count-light", widthDp = WIDTH_DP)
@Composable
private fun ThreadFoldWithCountLight() {
    NubecitaTheme(dynamicColor = false) {
        FoldPreviewSurface {
            ThreadFold(count = 5)
        }
    }
}

@PreviewTest
@Preview(name = "with-count-dark", widthDp = WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadFoldWithCountDark() {
    NubecitaTheme(dynamicColor = false) {
        FoldPreviewSurface {
            ThreadFold(count = 5)
        }
    }
}
