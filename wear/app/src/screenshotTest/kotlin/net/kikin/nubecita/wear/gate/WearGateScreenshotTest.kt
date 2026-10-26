package net.kikin.nubecita.wear.gate

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

// Rendered without AppScaffold on purpose: its TimeText shows the wall-clock
// time, which would make every baseline non-deterministic.

@Composable
private fun Gate(status: WearGateStatus) {
    NubecitaWearTheme {
        WearGate(state = WearGateState(status = status), onEvent = {}) {}
    }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun PhoneAppMissingPreview() = Gate(WearGateStatus.PhoneAppMissing)

@PreviewTest
@WearPreviewDevices
@Composable
private fun NeedsAndroidPhonePreview() = Gate(WearGateStatus.NeedsAndroidPhone)

@PreviewTest
@WearPreviewDevices
@Composable
private fun SignedOutPreview() = Gate(WearGateStatus.SignedOut)

@PreviewTest
@WearPreviewDevices
@Composable
private fun UpdateRequiredPreview() = Gate(WearGateStatus.UpdateRequired)

// The longest gate copy plus an action button: the largest font scale must
// still scroll rather than clip.
@PreviewTest
@WearPreviewFontScales
@Composable
private fun SignedOutFontScalesPreview() = Gate(WearGateStatus.SignedOut)
