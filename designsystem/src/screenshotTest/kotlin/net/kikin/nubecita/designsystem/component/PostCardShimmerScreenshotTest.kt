package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostCardShimmer]. The shimmer animation is
 * captured at its initial frame; baselines may need regenerating after
 * Compose UI version bumps that change `rememberInfiniteTransition`'s
 * initial-time semantics.
 */

@PreviewTest
@Preview(name = "no-image-light", showBackground = true)
@Preview(name = "no-image-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardShimmerNoImageScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardShimmer()
    }
}

@PreviewTest
@Preview(name = "with-image-light", showBackground = true)
@Preview(name = "with-image-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardShimmerWithImageScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardShimmer(showImagePlaceholder = true)
    }
}
