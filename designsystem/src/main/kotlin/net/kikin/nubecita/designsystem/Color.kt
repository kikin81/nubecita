package net.kikin.nubecita.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object NubecitaPalette {
    val Sky0 = Color(0xFF000D1F)
    val Sky10 = Color(0xFF001B33)
    val Sky20 = Color(0xFF003155)
    val Sky30 = Color(0xFF004880)
    val Sky40 = Color(0xFF0062B1)
    val Sky50 = Color(0xFF0A7AFF)
    val Sky60 = Color(0xFF3F92FF)
    val Sky70 = Color(0xFF6FAAFF)
    val Sky80 = Color(0xFFA6C8FF)
    val Sky90 = Color(0xFFD6E3FF)
    val Sky95 = Color(0xFFEBF1FF)
    val Sky98 = Color(0xFFF7F9FF)
    val Sky99 = Color(0xFFFDFCFF)
    val Sky100 = Color(0xFFFFFFFF)

    val Peach0 = Color(0xFF2B1100)
    val Peach10 = Color(0xFF3E1C00)
    val Peach20 = Color(0xFF5B2D00)
    val Peach30 = Color(0xFF7B4100)
    val Peach40 = Color(0xFF9C5600)
    val Peach50 = Color(0xFFC06C00)
    val Peach60 = Color(0xFFE38412)
    val Peach70 = Color(0xFFFFA04D)
    val Peach80 = Color(0xFFFFB787)
    val Peach90 = Color(0xFFFFDCC2)
    val Peach95 = Color(0xFFFFEDE0)
    val Peach98 = Color(0xFFFFF8F4)

    val Lilac30 = Color(0xFF4A3880)
    val Lilac40 = Color(0xFF6250B0)
    val Lilac50 = Color(0xFF7B6AD0)
    val Lilac70 = Color(0xFFB5A8F0)
    val Lilac90 = Color(0xFFE5DEFF)
    val Lilac95 = Color(0xFFF3EFFF)

    val Neutral0 = Color(0xFF000000)
    val Neutral10 = Color(0xFF1A1B1F)
    val Neutral20 = Color(0xFF2E2F33)
    val Neutral30 = Color(0xFF45464A)
    val Neutral40 = Color(0xFF5D5E62)
    val Neutral50 = Color(0xFF76777B)
    val Neutral60 = Color(0xFF909094)
    val Neutral70 = Color(0xFFAAAAAE)
    val Neutral80 = Color(0xFFC6C6CA)
    val Neutral90 = Color(0xFFE2E2E6)
    val Neutral95 = Color(0xFFF0F0F4)
    val Neutral98 = Color(0xFFF8F9FC)
    val Neutral99 = Color(0xFFFCFCFF)
    val Neutral100 = Color(0xFFFFFFFF)

    val NeutralVariant30 = Color(0xFF43474E)
    val NeutralVariant50 = Color(0xFF73777F)
    val NeutralVariant80 = Color(0xFFC3C7CF)
    val NeutralVariant90 = Color(0xFFDFE2EB)

    val Error40 = Color(0xFFBA1A1A)
    val Error50 = Color(0xFFDC362E)
    val Error80 = Color(0xFFFFB4AB)
    val Error90 = Color(0xFFFFDAD6)

    val Success40 = Color(0xFF006D3F)
    val Success50 = Color(0xFF1F8B5C)
    val Success80 = Color(0xFF7BD8A9)
    val Success90 = Color(0xFFB7F2D0)

    val Warning40 = Color(0xFF8A5300)
    val Warning80 = Color(0xFFFFCC80)
}

internal fun nubecitaLightColorScheme() =
    lightColorScheme(
        primary = NubecitaPalette.Sky50,
        onPrimary = NubecitaPalette.Sky100,
        primaryContainer = NubecitaPalette.Sky90,
        onPrimaryContainer = NubecitaPalette.Sky10,
        secondary = NubecitaPalette.Peach50,
        onSecondary = Color.White,
        secondaryContainer = NubecitaPalette.Peach90,
        onSecondaryContainer = NubecitaPalette.Peach10,
        tertiary = NubecitaPalette.Lilac40,
        onTertiary = Color.White,
        tertiaryContainer = NubecitaPalette.Lilac90,
        onTertiaryContainer = NubecitaPalette.Lilac30,
        error = NubecitaPalette.Error40,
        onError = Color.White,
        errorContainer = NubecitaPalette.Error90,
        onErrorContainer = Color(0xFF410002),
        background = NubecitaPalette.Sky99,
        onBackground = NubecitaPalette.Neutral10,
        surface = Color(0xFFFDFCFF),
        onSurface = NubecitaPalette.Neutral10,
        surfaceVariant = NubecitaPalette.NeutralVariant90,
        onSurfaceVariant = NubecitaPalette.NeutralVariant30,
        outline = NubecitaPalette.NeutralVariant50,
        outlineVariant = NubecitaPalette.NeutralVariant80,
        scrim = NubecitaPalette.Sky0.copy(alpha = 0.5f),
        inverseSurface = NubecitaPalette.Neutral20,
        inverseOnSurface = NubecitaPalette.Neutral95,
        inversePrimary = NubecitaPalette.Sky80,
        surfaceDim = Color(0xFFDEDCE0),
        surfaceBright = Color(0xFFFDFCFF),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7F5FA),
        surfaceContainer = Color(0xFFF1EFF4),
        surfaceContainerHigh = Color(0xFFEBE9EE),
        surfaceContainerHighest = Color(0xFFE5E3E9),
    )

internal fun nubecitaDarkColorScheme() =
    darkColorScheme(
        primary = NubecitaPalette.Sky80,
        onPrimary = NubecitaPalette.Sky20,
        primaryContainer = NubecitaPalette.Sky30,
        onPrimaryContainer = NubecitaPalette.Sky90,
        secondary = NubecitaPalette.Peach80,
        onSecondary = NubecitaPalette.Peach20,
        secondaryContainer = NubecitaPalette.Peach30,
        onSecondaryContainer = NubecitaPalette.Peach90,
        tertiary = NubecitaPalette.Lilac70,
        onTertiary = NubecitaPalette.Lilac30,
        tertiaryContainer = NubecitaPalette.Lilac30,
        onTertiaryContainer = NubecitaPalette.Lilac90,
        error = NubecitaPalette.Error80,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = NubecitaPalette.Error90,
        background = Color(0xFF111318),
        onBackground = Color(0xFFE3E2E6),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE3E2E6),
        surfaceVariant = NubecitaPalette.NeutralVariant30,
        onSurfaceVariant = NubecitaPalette.NeutralVariant80,
        outline = NubecitaPalette.NeutralVariant50,
        outlineVariant = NubecitaPalette.NeutralVariant30,
        scrim = Color.Black.copy(alpha = 0.6f),
        inverseSurface = NubecitaPalette.Neutral90,
        inverseOnSurface = NubecitaPalette.Neutral10,
        inversePrimary = NubecitaPalette.Sky40,
        surfaceDim = Color(0xFF111318),
        surfaceBright = Color(0xFF37393E),
        surfaceContainerLowest = Color(0xFF0C0E13),
        surfaceContainerLow = Color(0xFF191C20),
        surfaceContainer = Color(0xFF1D2024),
        surfaceContainerHigh = Color(0xFF272A2F),
        surfaceContainerHighest = Color(0xFF32353A),
    )

// Contrast variants derive mechanically from the brand tonal palette. Light
// variants push primary/secondary/tertiary to lower (darker) tones; dark variants
// push them to higher (lighter) tones. Every color MUST come from NubecitaPalette
// — no ad-hoc hex literals — per the design-system spec.

internal fun nubecitaLightMediumContrastColorScheme() =
    nubecitaLightColorScheme().copy(
        primary = NubecitaPalette.Sky30,
        onPrimary = NubecitaPalette.Sky100,
        primaryContainer = NubecitaPalette.Sky40,
        onPrimaryContainer = NubecitaPalette.Sky100,
        secondary = NubecitaPalette.Peach30,
        onSecondary = NubecitaPalette.Peach98,
        secondaryContainer = NubecitaPalette.Peach40,
        onSecondaryContainer = NubecitaPalette.Peach98,
        tertiary = NubecitaPalette.Lilac30,
        onTertiary = NubecitaPalette.Lilac95,
        tertiaryContainer = NubecitaPalette.Lilac40,
        onTertiaryContainer = NubecitaPalette.Lilac95,
        outline = NubecitaPalette.NeutralVariant30,
    )

internal fun nubecitaLightHighContrastColorScheme() =
    nubecitaLightColorScheme().copy(
        primary = NubecitaPalette.Sky20,
        onPrimary = NubecitaPalette.Sky100,
        primaryContainer = NubecitaPalette.Sky30,
        onPrimaryContainer = NubecitaPalette.Sky100,
        secondary = NubecitaPalette.Peach20,
        onSecondary = NubecitaPalette.Peach98,
        secondaryContainer = NubecitaPalette.Peach30,
        onSecondaryContainer = NubecitaPalette.Peach98,
        tertiary = NubecitaPalette.Lilac30, // palette has no Lilac20; lowest available
        onTertiary = NubecitaPalette.Lilac95,
        tertiaryContainer = NubecitaPalette.Lilac40,
        onTertiaryContainer = NubecitaPalette.Lilac95,
        outline = NubecitaPalette.Neutral20,
    )

internal fun nubecitaDarkMediumContrastColorScheme() =
    nubecitaDarkColorScheme().copy(
        primary = NubecitaPalette.Sky90,
        onPrimary = NubecitaPalette.Sky10,
        primaryContainer = NubecitaPalette.Sky40,
        onPrimaryContainer = NubecitaPalette.Sky99,
        secondary = NubecitaPalette.Peach90,
        onSecondary = NubecitaPalette.Peach10,
        secondaryContainer = NubecitaPalette.Peach40,
        onSecondaryContainer = NubecitaPalette.Peach98,
        tertiary = NubecitaPalette.Lilac90,
        onTertiary = NubecitaPalette.Lilac30, // palette has no Lilac10
        tertiaryContainer = NubecitaPalette.Lilac40,
        onTertiaryContainer = NubecitaPalette.Lilac95,
        outline = NubecitaPalette.NeutralVariant80,
    )

internal fun nubecitaDarkHighContrastColorScheme() =
    nubecitaDarkColorScheme().copy(
        primary = NubecitaPalette.Sky95,
        onPrimary = NubecitaPalette.Sky10,
        primaryContainer = NubecitaPalette.Sky80,
        onPrimaryContainer = NubecitaPalette.Sky10,
        secondary = NubecitaPalette.Peach95,
        onSecondary = NubecitaPalette.Peach10,
        secondaryContainer = NubecitaPalette.Peach80,
        onSecondaryContainer = NubecitaPalette.Peach10,
        tertiary = NubecitaPalette.Lilac95,
        onTertiary = NubecitaPalette.Lilac30,
        tertiaryContainer = NubecitaPalette.Lilac70,
        onTertiaryContainer = NubecitaPalette.Lilac30,
        outline = NubecitaPalette.NeutralVariant90,
    )
