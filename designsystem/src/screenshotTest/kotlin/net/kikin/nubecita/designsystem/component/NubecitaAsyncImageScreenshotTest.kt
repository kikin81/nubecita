package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baseline for [NubecitaAsyncImage] with no model — exercises
 * the placeholder painter (which is what users see whenever an image
 * fails to load or is mid-flight).
 */

@PreviewTest
@Preview(name = "placeholder-light", showBackground = true)
@Preview(name = "placeholder-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaAsyncImagePlaceholderScreenshot() {
    NubecitaAsyncImage(
        model = null,
        contentDescription = null,
        modifier = Modifier.size(96.dp),
    )
}
