@file:SuppressLint("RestrictedApi") // Glance Wear preview params are LIBRARY_GROUP-restricted in the alpha.

package net.kikin.nubecita.wear.widget

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.glance.wear.core.ContainerInfo
import androidx.glance.wear.core.WearWidgetParams
import androidx.glance.wear.core.WidgetInstanceId
import androidx.glance.wear.tooling.preview.RectangularLargeWidgetPreviewParams
import androidx.glance.wear.tooling.preview.RoundLargeWidgetPreviewParams
import androidx.glance.wear.tooling.preview.SquircleLargeWidgetPreviewParams
import androidx.glance.wear.tooling.preview.WearWidgetPreview
import com.android.tools.screenshot.PreviewTest

// The launcher in its two forms: the large widget card (rectangular = the
// picker image, plus the round and squircle masks) and the tile form Pixel Watch
// and Wear OS 4-6 show. Tile params are what a Wear OS 7 emulator's host passed:
// a 165.6x114dp slot inside the host-drawn tile, 8dp padding, 26dp corners.
// Fixed counts keep every baseline stable.

private const val LARGE_DEVICE = "spec:width=1000dp,height=1000dp,dpi=320"
private const val TILE_DEVICE = "spec:width=200dp,height=140dp,dpi=320"

private val TileParams =
    WearWidgetParams(WidgetInstanceId("preview", 0), ContainerInfo.CONTAINER_TYPE_TILE_COMPAT, 165.6f, 114.2f, 8f, 8f, 26f)

@PreviewTest
@Preview(name = "Large, rectangular (picker)", device = LARGE_DEVICE)
@Composable
private fun LauncherLargeRectangularPreview(
    @PreviewParameter(RectangularLargeWidgetPreviewParams::class) params: WearWidgetParams,
) {
    WearWidgetPreview(LauncherWidget { 3 }, params)
}

@PreviewTest
@Preview(name = "Large, round", device = LARGE_DEVICE)
@Composable
private fun LauncherLargeRoundPreview(
    @PreviewParameter(RoundLargeWidgetPreviewParams::class) params: WearWidgetParams,
) {
    WearWidgetPreview(LauncherWidget { 3 }, params)
}

@PreviewTest
@Preview(name = "Large, squircle", device = LARGE_DEVICE)
@Composable
private fun LauncherLargeSquirclePreview(
    @PreviewParameter(SquircleLargeWidgetPreviewParams::class) params: WearWidgetParams,
) {
    WearWidgetPreview(LauncherWidget { 3 }, params)
}

@PreviewTest
@Preview(name = "Tile, 3 unread", device = TILE_DEVICE)
@Composable
private fun LauncherTileUnreadPreview() {
    WearWidgetPreview(LauncherWidget { 3 }, TileParams)
}

@PreviewTest
@Preview(name = "Tile, none unread", device = TILE_DEVICE)
@Composable
private fun LauncherTileNoneUnreadPreview() {
    WearWidgetPreview(LauncherWidget { 0 }, TileParams)
}

@PreviewTest
@Preview(name = "Tile, never synced", device = TILE_DEVICE)
@Composable
private fun LauncherTileNoDataPreview() {
    WearWidgetPreview(LauncherWidget { null }, TileParams)
}

@PreviewTest
@Preview(name = "Tile, 99+ at largest font", device = TILE_DEVICE, fontScale = 1.24f)
@Composable
private fun LauncherTileManyLargestFontPreview() {
    WearWidgetPreview(LauncherWidget { 120 }, TileParams)
}
