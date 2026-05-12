package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ProfileTab

@PreviewTest
@Preview(name = "tab-placeholder-replies-light", showBackground = true)
@Preview(name = "tab-placeholder-replies-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTabPlaceholderRepliesScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTabPlaceholder(tab = ProfileTab.Replies)
    }
}

@PreviewTest
@Preview(name = "tab-placeholder-media-light", showBackground = true)
@Preview(name = "tab-placeholder-media-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTabPlaceholderMediaScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTabPlaceholder(tab = ProfileTab.Media)
    }
}
