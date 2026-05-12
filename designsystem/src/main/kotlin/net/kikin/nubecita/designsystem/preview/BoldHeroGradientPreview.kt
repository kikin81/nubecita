package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.extendedTypography
import net.kikin.nubecita.designsystem.hero.BoldHeroGradient

/**
 * Catalog preview for [BoldHeroGradient]. Renders the gradient at a
 * few representative avatar hues. The `banner = null` path is
 * deterministic and renders synchronously, so it's well-suited to
 * the preview pane; the banner-present path needs a live Coil
 * pipeline and is exercised on real devices (Bead D).
 */
@Composable
fun BoldHeroGradientCatalog(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gradientSwatches.forEach { (hue, label) ->
            BoldHeroGradient(
                banner = null,
                avatarHue = hue,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
            ) {
                Box(
                    modifier = Modifier.padding(24.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.extendedTypography.displayName,
                    )
                }
            }
        }
    }
}

private val gradientSwatches: List<Pair<Int, String>> =
    listOf(
        217 to "Sky (hue 217)",
        45 to "Sunrise (hue 45)",
        140 to "Mint (hue 140)",
        290 to "Plum (hue 290)",
    )

@Preview(name = "Light", showBackground = true, heightDp = 800)
@Composable
private fun BoldHeroGradientPreviewLight() {
    NubecitaTheme(darkTheme = false, dynamicColor = false) {
        Surface {
            BoldHeroGradientCatalog()
        }
    }
}

@Preview(name = "Dark", showBackground = true, heightDp = 800)
@Composable
private fun BoldHeroGradientPreviewDark() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        Surface {
            BoldHeroGradientCatalog()
        }
    }
}
