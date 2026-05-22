package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R

/**
 * Brand cloud-only mark. Square aspect (1:1).
 *
 * Backed by [LogoImageVector], a Compose `ImageVector` port of the brand
 * logomark. The vector carries its own colors, so callers do not pass a tint.
 *
 * Caller controls absolute size via [modifier] (`Modifier.size(...)` or
 * layout-driven). The intrinsic size is 72dp × 72dp.
 */
@Composable
fun NubecitaLogomark(modifier: Modifier = Modifier) {
    Image(
        imageVector = LogoImageVector,
        contentDescription = stringResource(R.string.logomark_content_description),
        modifier = modifier,
    )
}

@Preview(name = "Logomark", showBackground = true)
@Composable
private fun NubecitaLogomarkPreview() {
    NubecitaTheme(dynamicColor = false) {
        NubecitaLogomark(modifier = Modifier.size(96.dp))
    }
}

@Preview(name = "Logomark · dark", showBackground = true)
@Composable
private fun NubecitaLogomarkDarkPreview() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        NubecitaLogomark(modifier = Modifier.size(96.dp))
    }
}
