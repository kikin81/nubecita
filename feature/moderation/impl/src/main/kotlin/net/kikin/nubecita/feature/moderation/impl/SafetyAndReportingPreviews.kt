package net.kikin.nubecita.feature.moderation.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme

@Preview(name = "Safety and Reporting — Light", showBackground = true)
@Preview(name = "Safety and Reporting — Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SafetyAndReportingPreview() {
    NubecitaTheme {
        SafetyAndReportingContent(
            onBack = {},
            onReportChildSafetyClick = {},
            onReportAccountClick = {},
            onNavigateToBlockedAccounts = {},
            onOpenUrl = {},
        )
    }
}

@Preview(name = "Report Account Dialog — Light", showBackground = true)
@Preview(name = "Report Account Dialog — Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportAccountEntryDialogPreview() {
    NubecitaTheme {
        ReportAccountEntryDialog(
            isChildSafety = false,
            onDismiss = {},
            onConfirm = {},
            initialInput = "user.bsky.social",
            initialError = false,
        )
    }
}

@Preview(name = "Report Account Dialog Error — Light", showBackground = true)
@Preview(name = "Report Account Dialog Error — Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportAccountEntryDialogErrorPreview() {
    NubecitaTheme {
        ReportAccountEntryDialog(
            isChildSafety = false,
            onDismiss = {},
            onConfirm = {},
            initialInput = "invalid",
            initialError = true,
        )
    }
}
