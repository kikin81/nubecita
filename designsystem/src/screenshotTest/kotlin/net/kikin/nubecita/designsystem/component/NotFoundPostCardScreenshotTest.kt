package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaScreenPreviewTheme

/**
 * Screenshot baselines for [NotFoundPostCard]. Single visual state — no
 * trailing affordance — captured in light + dark to lock the
 * `surfaceContainer` / `onSurfaceVariant` pairing.
 */

@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotFoundPostCardScreenshot() {
    NubecitaScreenPreviewTheme {
        NotFoundPostCard()
    }
}
