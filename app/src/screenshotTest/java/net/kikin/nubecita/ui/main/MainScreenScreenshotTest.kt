package net.kikin.nubecita.ui.main

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.theme.NubecitaTheme

@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MainScreenDefaultPreview() {
    NubecitaTheme(dynamicColor = false) {
        MainScreenContent(
            state =
                MainScreenState(
                    items = persistentListOf("Android", "Compose", "Screenshot"),
                    isLoading = false,
                ),
            onRefresh = {},
        )
    }
}
