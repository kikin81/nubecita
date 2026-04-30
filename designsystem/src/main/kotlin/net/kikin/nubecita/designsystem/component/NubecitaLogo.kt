package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R

/**
 * Brand cloud-only mark. Square aspect (1:1).
 *
 * Backed by `nubecita_logomark.xml`, a 4-shape vector port of
 * `openspec/references/design-system/assets/logomark-mono.svg`. The vector
 * uses `#FFFFFFFF` fills throughout; [tint] is applied via
 * `ColorFilter.tint(...)` at render time, so the mark color follows whatever
 * the caller passes (default: `MaterialTheme.colorScheme.primary`, which
 * resolves to brand sky `#0A7AFF` under the static palette).
 *
 * Caller controls absolute size via [modifier] (`Modifier.size(...)` or
 * layout-driven). The intrinsic size is the vector's 72dp × 72dp.
 */
@Composable
fun NubecitaLogomark(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Image(
        painter = painterResource(R.drawable.nubecita_logomark),
        contentDescription = stringResource(R.string.logomark_content_description),
        colorFilter = ColorFilter.tint(tint),
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

@Preview(name = "Logomark · custom tint", showBackground = true)
@Composable
private fun NubecitaLogomarkCustomTintPreview() {
    NubecitaTheme(dynamicColor = false) {
        NubecitaLogomark(
            modifier = Modifier.size(96.dp),
            tint = Color(0xFF0A7AFF),
        )
    }
}
