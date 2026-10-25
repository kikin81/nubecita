package net.kikin.nubecita.wear.home

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

// Rendered without the app's AppScaffold on purpose: its TimeText shows the
// wall-clock time, which would make every baseline non-deterministic.

@PreviewTest
@WearPreviewDevices
@Composable
private fun HomeScreenDevicesPreview() {
    NubecitaWearTheme { HomeScreen() }
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun HomeScreenFontScalesPreview() {
    NubecitaWearTheme { HomeScreen() }
}
