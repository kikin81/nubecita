package net.kikin.nubecita.ui.main

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.theme.NubecitaTheme

@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MainScreenDefaultPreview() {
    NubecitaTheme(dynamicColor = false) {
        MainScreen(data = listOf("Android", "Compose", "Screenshot"))
    }
}
