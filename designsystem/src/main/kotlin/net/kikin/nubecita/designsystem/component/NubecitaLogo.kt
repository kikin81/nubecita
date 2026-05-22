package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaPalette
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R

/**
 * Brand cloud-only mark. Square aspect (1:1).
 *
 * Backed by [LogoImageVector], a Compose `ImageVector` port of the brand
 * logomark — white fill with sky-blue strokes. Use the default multi-color
 * rendering on surfaces with a contrasting (typically darker or branded)
 * background. For low-contrast surfaces like the near-white `Sky99` theme
 * background, pass a [tint] (e.g. `NubecitaPalette.Sky50`) to collapse the
 * mark to a single legible color via `ColorFilter.tint(...)`.
 *
 * Caller controls absolute size via [modifier] (`Modifier.size(...)` or
 * layout-driven). The intrinsic size is 72dp × 72dp.
 */
@Composable
fun NubecitaLogomark(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Image(
        imageVector = LogoImageVector,
        contentDescription = stringResource(R.string.logomark_content_description),
        colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null,
        modifier = modifier,
    )
}

@Preview(name = "Logomark", showBackground = true)
@Composable
private fun NubecitaLogomarkPreview() {
    NubecitaTheme(dynamicColor = false) {
        NubecitaLogomark(modifier = Modifier.size(96.dp).background(NubecitaPalette.Sky50))
    }
}

@Preview(name = "Logomark · dark", showBackground = true)
@Composable
private fun NubecitaLogomarkDarkPreview() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        NubecitaLogomark(modifier = Modifier.size(96.dp).background(NubecitaPalette.Sky50))
    }
}

@Preview(name = "Logomark · tinted on light surface", showBackground = true)
@Composable
private fun NubecitaLogomarkTintedPreview() {
    NubecitaTheme(dynamicColor = false) {
        NubecitaLogomark(
            modifier = Modifier.size(96.dp).background(NubecitaPalette.Sky99),
            tint = NubecitaPalette.Sky50,
        )
    }
}
