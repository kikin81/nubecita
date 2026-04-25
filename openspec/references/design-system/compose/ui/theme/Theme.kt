// ============================================================
// Nubecita — M3 ColorScheme (light + dark)
// Ports the semantic roles in colors_and_type.css to Material 3.
// Use NubecitaTheme { ... } in your Activity.
// ============================================================
package app.nubecita.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val NubecitaLight = lightColorScheme(
    primary              = NubecitaTones.Sky50,
    onPrimary            = NubecitaTones.Sky100,
    primaryContainer     = NubecitaTones.Sky90,
    onPrimaryContainer   = NubecitaTones.Sky10,

    secondary            = NubecitaTones.Peach50,
    onSecondary          = Color.White,
    secondaryContainer   = NubecitaTones.Peach90,
    onSecondaryContainer = NubecitaTones.Peach10,

    tertiary             = NubecitaTones.Lilac40,
    onTertiary           = Color.White,
    tertiaryContainer    = NubecitaTones.Lilac90,
    onTertiaryContainer  = NubecitaTones.Lilac30,

    error                = NubecitaTones.Error40,
    onError              = Color.White,
    errorContainer       = NubecitaTones.Error90,
    onErrorContainer     = Color(0xFF410002),

    background           = Color(0xFFFDFCFF),
    onBackground         = NubecitaTones.Neutral10,

    surface              = Color(0xFFFDFCFF),
    onSurface            = NubecitaTones.Neutral10,
    surfaceVariant       = NubecitaTones.NV90,
    onSurfaceVariant     = NubecitaTones.NV30,

    surfaceContainerLowest  = Color(0xFFFFFFFF),
    surfaceContainerLow     = Color(0xFFF7F5FA),
    surfaceContainer        = Color(0xFFF1EFF4),
    surfaceContainerHigh    = Color(0xFFEBE9EE),
    surfaceContainerHighest = Color(0xFFE5E3E9),

    outline              = NubecitaTones.NV50,
    outlineVariant       = NubecitaTones.NV80,
    scrim                = Color.Black,
    inverseSurface       = NubecitaTones.Neutral10,
    inverseOnSurface     = Color(0xFFE3E2E6),
    inversePrimary       = NubecitaTones.Sky80,
)

private val NubecitaDark = darkColorScheme(
    primary              = NubecitaTones.Sky80,
    onPrimary            = NubecitaTones.Sky20,
    primaryContainer     = NubecitaTones.Sky30,
    onPrimaryContainer   = NubecitaTones.Sky90,

    secondary            = NubecitaTones.Peach80,
    onSecondary          = NubecitaTones.Peach20,
    secondaryContainer   = NubecitaTones.Peach30,
    onSecondaryContainer = NubecitaTones.Peach90,

    tertiary             = NubecitaTones.Lilac70,
    onTertiary           = NubecitaTones.Lilac30,
    tertiaryContainer    = NubecitaTones.Lilac30,
    onTertiaryContainer  = NubecitaTones.Lilac90,

    error                = NubecitaTones.Error80,
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = NubecitaTones.Error90,

    background           = Color(0xFF111318),
    onBackground         = Color(0xFFE3E2E6),

    surface              = Color(0xFF111318),
    onSurface            = Color(0xFFE3E2E6),
    surfaceVariant       = NubecitaTones.NV30,
    onSurfaceVariant     = NubecitaTones.NV80,

    surfaceContainerLowest  = Color(0xFF0C0E13),
    surfaceContainerLow     = Color(0xFF191C20),
    surfaceContainer        = Color(0xFF1D2024),
    surfaceContainerHigh    = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),

    outline              = NubecitaTones.NV50,
    outlineVariant       = NubecitaTones.NV30,
    scrim                = Color.Black,
    inverseSurface       = Color(0xFFE3E2E6),
    inverseOnSurface     = NubecitaTones.Neutral10,
    inversePrimary       = NubecitaTones.Sky50,
)

@Composable
fun NubecitaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Set false to force the brand palette over Android 12+ wallpaper colors. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> NubecitaDark
        else      -> NubecitaLight
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = NubecitaTypography,
        shapes      = NubecitaShapes,
        content     = content,
    )
}
