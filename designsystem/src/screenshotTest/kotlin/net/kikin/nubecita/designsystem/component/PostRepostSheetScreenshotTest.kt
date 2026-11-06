package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for the repost sheet body. Layoutlib can't compose the
 * `ModalBottomSheet` window deterministically, so these capture
 * [PostRepostSheetContent], one for each label the first row can show.
 */
@PreviewTest
@Preview(name = "not-reposted-light", showBackground = true)
@Preview(name = "not-reposted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostRepostSheetNotRepostedScreenshot() {
    PostRepostSheetContent(isReposted = false, onAction = {})
}

@PreviewTest
@Preview(name = "reposted-light", showBackground = true)
@Preview(name = "reposted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostRepostSheetRepostedScreenshot() {
    PostRepostSheetContent(isReposted = true, onAction = {})
}
