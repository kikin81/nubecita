package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

@PreviewTest
@Preview(name = "fallback-light", showBackground = true)
@Preview(name = "fallback-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaAvatarFallbackScreenshot() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NubecitaAvatar(model = null, contentDescription = null, fallback = AvatarFallback(hue = 47, initial = 'A'))
        NubecitaAvatar(model = null, contentDescription = null, fallback = AvatarFallback(hue = 307, initial = 'B'))
        NubecitaAvatar(model = null, contentDescription = null, fallback = AvatarFallback(hue = 279, initial = null))
        NubecitaAvatar(model = null, contentDescription = null)
    }
}
