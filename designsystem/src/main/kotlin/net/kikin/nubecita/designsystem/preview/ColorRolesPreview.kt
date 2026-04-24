package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

@Composable
fun ColorSwatch(
    name: String,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Role swatch with the matching on-color overlaid — mirrors how the pair
        // actually renders in the app (onPrimary text over primary fill). The
        // earlier variant rendered onColor as text against the default surface,
        // which could be unreadable when on-colors are near-white.
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(color, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.labelLarge,
                color = onColor,
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "on$name",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ColorRoster(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        val scheme = MaterialTheme.colorScheme
        ColorSwatch("Primary", scheme.primary, scheme.onPrimary)
        ColorSwatch("PrimaryContainer", scheme.primaryContainer, scheme.onPrimaryContainer)
        ColorSwatch("Secondary", scheme.secondary, scheme.onSecondary)
        ColorSwatch("SecondaryContainer", scheme.secondaryContainer, scheme.onSecondaryContainer)
        ColorSwatch("Tertiary", scheme.tertiary, scheme.onTertiary)
        ColorSwatch("TertiaryContainer", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        ColorSwatch("Error", scheme.error, scheme.onError)
        ColorSwatch("ErrorContainer", scheme.errorContainer, scheme.onErrorContainer)
        ColorSwatch("Surface", scheme.surface, scheme.onSurface)
        ColorSwatch("SurfaceVariant", scheme.surfaceVariant, scheme.onSurfaceVariant)
        ColorSwatch("SurfaceContainerLowest", scheme.surfaceContainerLowest, scheme.onSurface)
        ColorSwatch("SurfaceContainerLow", scheme.surfaceContainerLow, scheme.onSurface)
        ColorSwatch("SurfaceContainer", scheme.surfaceContainer, scheme.onSurface)
        ColorSwatch("SurfaceContainerHigh", scheme.surfaceContainerHigh, scheme.onSurface)
        ColorSwatch("SurfaceContainerHighest", scheme.surfaceContainerHighest, scheme.onSurface)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ColorRolesPreviewLight() {
    NubecitaTheme(darkTheme = false, dynamicColor = false) {
        ColorRoster()
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun ColorRolesPreviewDark() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        ColorRoster()
    }
}

// The HC previews exercise the same brandScheme() code path that UiModeManager.contrast
// would trigger on Android 14+. NubecitaTheme itself only exposes darkTheme + dynamicColor
// externally, so for the preview we wrap ColorRoster with a direct MaterialTheme that
// plugs the HC ColorScheme in. Implementation lives in the same package.
@Preview(name = "Light HC", showBackground = true)
@Composable
private fun ColorRolesPreviewLightHighContrast() {
    HighContrastPreview(darkTheme = false) { ColorRoster() }
}

@Preview(name = "Dark HC", showBackground = true)
@Composable
private fun ColorRolesPreviewDarkHighContrast() {
    HighContrastPreview(darkTheme = true) { ColorRoster() }
}
