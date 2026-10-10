package net.kikin.nubecita.feature.moderation.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

@PreviewTest
@Preview(name = "safety-and-reporting-light", showBackground = true)
@Preview(name = "safety-and-reporting-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SafetyAndReportingScreenshot() {
    NubecitaCanvasPreviewTheme {
        SafetyAndReportingContent(
            onBack = {},
            onReportChildSafetyClick = {},
            onReportAccountClick = {},
            onNavigateToBlockedAccounts = {},
            onOpenUrl = {},
        )
    }
}

@PreviewTest
@Preview(name = "report-account-dialog-light", showBackground = true)
@Preview(name = "report-account-dialog-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportAccountEntryDialogScreenshot() {
    NubecitaCanvasPreviewTheme {
        ReportAccountEntryDialog(
            isChildSafety = false,
            onDismiss = {},
            onConfirm = {},
            initialInput = "user.bsky.social",
            initialError = false,
        )
    }
}

@PreviewTest
@Preview(name = "report-account-dialog-error-light", showBackground = true)
@Preview(name = "report-account-dialog-error-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportAccountEntryDialogErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        ReportAccountEntryDialog(
            isChildSafety = false,
            onDismiss = {},
            onConfirm = {},
            initialInput = "invalid",
            initialError = true,
        )
    }
}
