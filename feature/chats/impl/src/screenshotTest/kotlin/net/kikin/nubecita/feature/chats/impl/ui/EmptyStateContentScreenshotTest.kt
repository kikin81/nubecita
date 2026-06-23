package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

@PreviewTest
@Preview(name = "empty-state-light", showBackground = true, heightDp = 300)
@Preview(name = "empty-state-dark", showBackground = true, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyStateContentDefault() {
    NubecitaTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) {
            EmptyStateContent(icon = NubecitaIconName.PersonAdd, message = "No pending requests")
        }
    }
}
