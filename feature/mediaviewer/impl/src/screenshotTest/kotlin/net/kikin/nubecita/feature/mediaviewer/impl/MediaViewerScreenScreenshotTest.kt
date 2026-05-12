package net.kikin.nubecita.feature.mediaviewer.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for `MediaViewerScreen`'s view-mode matrix:
 * `Loading`, three `Error` variants, `Loaded(single)`, `Loaded(multi)`
 * with chrome and the `ALT` badge, and `Loaded` with the alt-text bottom
 * sheet open.
 *
 * Driven through [MediaViewerScreenContent] directly with fixture state
 * inputs so the captures are deterministic across machines: no Hilt
 * graph, no `PostRepository` call, no live network. The pager's
 * `ZoomableAsyncImage` cells render Coil's preview placeholder (preview
 * tooling doesn't hit the network) so the baselines verify chrome
 * geometry + black-canvas hierarchy + sheet positioning, not image
 * content.
 */

@PreviewTest
@Preview(name = "loading-light", showBackground = true)
@Preview(name = "loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaViewerLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        MediaViewerScreenContent(
            state = MediaViewerState(loadStatus = MediaViewerLoadStatus.Loading),
            onRetry = {},
            onDismissRequest = {},
            onPageChange = {},
            onTapImage = {},
            onAltBadgeClick = {},
            onAltSheetDismiss = {},
            onChromeAutoFadeTimeout = {},
        )
    }
}

@PreviewTest
@Preview(name = "error-network-light", showBackground = true)
@Preview(name = "error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaViewerErrorNetworkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        MediaViewerScreenContent(
            state = MediaViewerState(loadStatus = MediaViewerLoadStatus.Error(MediaViewerError.Network)),
            onRetry = {},
            onDismissRequest = {},
            onPageChange = {},
            onTapImage = {},
            onAltBadgeClick = {},
            onAltSheetDismiss = {},
            onChromeAutoFadeTimeout = {},
        )
    }
}

@PreviewTest
@Preview(name = "error-not-found-light", showBackground = true)
@Preview(name = "error-not-found-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaViewerErrorNotFoundScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        MediaViewerScreenContent(
            state = MediaViewerState(loadStatus = MediaViewerLoadStatus.Error(MediaViewerError.NotFound)),
            onRetry = {},
            onDismissRequest = {},
            onPageChange = {},
            onTapImage = {},
            onAltBadgeClick = {},
            onAltSheetDismiss = {},
            onChromeAutoFadeTimeout = {},
        )
    }
}

@PreviewTest
@Preview(name = "error-no-images-light", showBackground = true)
@Preview(name = "error-no-images-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaViewerErrorNoImagesScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        MediaViewerScreenContent(
            state = MediaViewerState(loadStatus = MediaViewerLoadStatus.Error(MediaViewerError.NoImages)),
            onRetry = {},
            onDismissRequest = {},
            onPageChange = {},
            onTapImage = {},
            onAltBadgeClick = {},
            onAltSheetDismiss = {},
            onChromeAutoFadeTimeout = {},
        )
    }
}

@PreviewTest
@Preview(name = "loaded-single-light", showBackground = true)
@Preview(name = "loaded-single-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaViewerLoadedSingleScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        MediaViewerScreenContent(
            state =
                MediaViewerState(
                    loadStatus =
                        MediaViewerLoadStatus.Loaded(
                            images = persistentListOf(previewImage(0, altText = "the cat sat on the mat")),
                            currentIndex = 0,
                            isChromeVisible = true,
                            isAltSheetOpen = false,
                        ),
                ),
            onRetry = {},
            onDismissRequest = {},
            onPageChange = {},
            onTapImage = {},
            onAltBadgeClick = {},
            onAltSheetDismiss = {},
            onChromeAutoFadeTimeout = {},
        )
    }
}

@PreviewTest
@Preview(name = "loaded-multi-light", showBackground = true)
@Preview(name = "loaded-multi-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaViewerLoadedMultiScreenshot() {
    // Three images with chrome visible: page indicator should read "1 / 3"
    // and the ALT badge should render (current image has alt text).
    NubecitaTheme(dynamicColor = false) {
        MediaViewerScreenContent(
            state =
                MediaViewerState(
                    loadStatus =
                        MediaViewerLoadStatus.Loaded(
                            images =
                                persistentListOf(
                                    previewImage(0, altText = "first image alt text"),
                                    previewImage(1, altText = null),
                                    previewImage(2, altText = "third"),
                                ),
                            currentIndex = 0,
                            isChromeVisible = true,
                            isAltSheetOpen = false,
                        ),
                ),
            onRetry = {},
            onDismissRequest = {},
            onPageChange = {},
            onTapImage = {},
            onAltBadgeClick = {},
            onAltSheetDismiss = {},
            onChromeAutoFadeTimeout = {},
        )
    }
}

@PreviewTest
@Preview(name = "loaded-multi-no-alt-light", showBackground = true)
@Preview(name = "loaded-multi-no-alt-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaViewerLoadedMultiNoAltScreenshot() {
    // Verifies the ALT badge does NOT render when the current image has
    // no alt text (the centered page indicator should stay perfectly
    // centered with the close button on the left).
    NubecitaTheme(dynamicColor = false) {
        MediaViewerScreenContent(
            state =
                MediaViewerState(
                    loadStatus =
                        MediaViewerLoadStatus.Loaded(
                            images =
                                persistentListOf(
                                    previewImage(0, altText = null),
                                    previewImage(1, altText = null),
                                ),
                            currentIndex = 0,
                            isChromeVisible = true,
                            isAltSheetOpen = false,
                        ),
                ),
            onRetry = {},
            onDismissRequest = {},
            onPageChange = {},
            onTapImage = {},
            onAltBadgeClick = {},
            onAltSheetDismiss = {},
            onChromeAutoFadeTimeout = {},
        )
    }
}

private fun previewImage(
    index: Int,
    altText: String?,
): ImageUi =
    ImageUi(
        fullsizeUrl = "https://example.com/placeholder/$index.jpg",
        thumbUrl = "https://example.com/placeholder/$index.jpg",
        altText = altText,
        aspectRatio = 1.5f,
    )
