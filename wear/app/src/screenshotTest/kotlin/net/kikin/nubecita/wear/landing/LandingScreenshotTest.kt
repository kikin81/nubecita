package net.kikin.nubecita.wear.landing

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.wear.ui.PreviewWearRoundDevices
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

// Rendered without AppScaffold (its TimeText shows the wall clock), so every
// baseline is deterministic.

@PreviewTest
@WearPreviewDevices
@Composable
private fun LandingPreview() {
    NubecitaWearTheme { LandingScreen(onOpenFeed = {}, onOpenMessages = {}, onNewPost = {}) }
}

@PreviewTest
@PreviewWearRoundDevices
@Composable
private fun LandingUnreadPreview() {
    // Messages shows the unread count under its icon.
    NubecitaWearTheme { LandingScreen(onOpenFeed = {}, unreadMessages = 3, onOpenMessages = {}, onNewPost = {}) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun LandingNotReadyPreview() {
    // DMs not synced yet and posting unavailable: those two stay in place, disabled.
    NubecitaWearTheme { LandingScreen(onOpenFeed = {}) }
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun LandingFontScalesPreview() {
    NubecitaWearTheme { LandingScreen(onOpenFeed = {}, unreadMessages = 12, onOpenMessages = {}, onNewPost = {}) }
}
