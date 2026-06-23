package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "create-light", showBackground = true)
@Preview(name = "create-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateJoinLinkContentPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            CreateJoinLinkContent(creating = false, onCreate = { _, _ -> })
        }
    }
}
