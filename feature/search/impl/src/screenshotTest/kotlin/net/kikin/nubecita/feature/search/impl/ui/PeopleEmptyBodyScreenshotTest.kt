package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "people-empty-light", showBackground = true)
@Preview(name = "people-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleEmptyBodyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PeopleEmptyBody(currentQuery = "alice", onClearQuery = {})
        }
    }
}
