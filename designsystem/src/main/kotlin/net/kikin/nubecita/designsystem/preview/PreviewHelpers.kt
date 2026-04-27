package net.kikin.nubecita.designsystem.preview

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import net.kikin.nubecita.designsystem.LocalNubecitaTokens
import net.kikin.nubecita.designsystem.NubecitaMotion
import net.kikin.nubecita.designsystem.NubecitaMotionScheme
import net.kikin.nubecita.designsystem.NubecitaPalette
import net.kikin.nubecita.designsystem.NubecitaSemanticColors
import net.kikin.nubecita.designsystem.NubecitaTokens
import net.kikin.nubecita.designsystem.nubecitaDarkHighContrastColorScheme
import net.kikin.nubecita.designsystem.nubecitaLightHighContrastColorScheme
import net.kikin.nubecita.designsystem.nubecitaShapes
import net.kikin.nubecita.designsystem.nubecitaTypography

// Preview-only wrapper that plugs a high-contrast ColorScheme into a fully-wired
// MaterialExpressiveTheme + NubecitaTokens bundle. NubecitaTheme picks contrast from
// UiModeManager.contrast at runtime; previews need a way to force HC without
// spoofing the system service.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HighContrastPreview(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme =
        if (darkTheme) nubecitaDarkHighContrastColorScheme() else nubecitaLightHighContrastColorScheme()
    val tokens =
        NubecitaTokens(
            semanticColors = previewSemanticColors(darkTheme),
            motion = NubecitaMotion.Standard,
        )
    CompositionLocalProvider(LocalNubecitaTokens provides tokens) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = nubecitaTypography,
            shapes = nubecitaShapes,
            motionScheme = NubecitaMotionScheme(isReduced = false),
            content = content,
        )
    }
}

private fun previewSemanticColors(darkTheme: Boolean): NubecitaSemanticColors =
    if (darkTheme) {
        NubecitaSemanticColors(
            success = NubecitaPalette.Success80,
            onSuccess = NubecitaPalette.Neutral10,
            successContainer = NubecitaPalette.Success40,
            onSuccessContainer = NubecitaPalette.Success90,
            warning = NubecitaPalette.Warning80,
            onWarning = NubecitaPalette.Neutral10,
            videoOverlayScrim = NubecitaPalette.Neutral0.copy(alpha = 0.8f),
            videoOverlayScrimSubtle = NubecitaPalette.Neutral0.copy(alpha = 0.4f),
            onVideoOverlay = NubecitaPalette.Neutral100,
        )
    } else {
        NubecitaSemanticColors(
            success = NubecitaPalette.Success40,
            onSuccess = NubecitaPalette.Neutral100,
            successContainer = NubecitaPalette.Success90,
            onSuccessContainer = NubecitaPalette.Success40,
            warning = NubecitaPalette.Warning40,
            onWarning = NubecitaPalette.Neutral100,
            videoOverlayScrim = NubecitaPalette.Neutral0.copy(alpha = 0.8f),
            videoOverlayScrimSubtle = NubecitaPalette.Neutral0.copy(alpha = 0.4f),
            onVideoOverlay = NubecitaPalette.Neutral100,
        )
    }
