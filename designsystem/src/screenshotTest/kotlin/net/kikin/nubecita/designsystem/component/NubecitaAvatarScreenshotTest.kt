package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baseline for [NubecitaAvatar]'s placeholder render. Real
 * Coil-fetched avatars don't exercise here (preview tooling doesn't hit
 * the network); this verifies the placeholder painter, circular clip,
 * and theme-color reactivity.
 */

@PreviewTest
@Preview(name = "placeholder-light", showBackground = true)
@Preview(name = "placeholder-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaAvatarPlaceholderScreenshot() {
    NubecitaAvatar(model = null, contentDescription = null)
}
