package net.kikin.nubecita.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun NubecitaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> nubecitaDarkColorScheme()
            else -> nubecitaLightColorScheme()
        }

    val semanticColors =
        if (darkTheme) {
            NubecitaSemanticColors(
                success = NubecitaPalette.Success80,
                onSuccess = NubecitaPalette.Neutral10,
                successContainer = NubecitaPalette.Success40,
                onSuccessContainer = NubecitaPalette.Success90,
                warning = NubecitaPalette.Warning80,
                onWarning = NubecitaPalette.Neutral10,
            )
        } else {
            NubecitaSemanticColors(
                success = NubecitaPalette.Success40,
                onSuccess = NubecitaPalette.Neutral100,
                successContainer = NubecitaPalette.Success90,
                onSuccessContainer = NubecitaPalette.Success40,
                warning = NubecitaPalette.Warning40,
                onWarning = NubecitaPalette.Neutral100,
            )
        }

    val nubecitaTokens =
        NubecitaTokens(
            semanticColors = semanticColors,
        )

    CompositionLocalProvider(LocalNubecitaTokens provides nubecitaTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = nubecitaTypography,
            shapes = nubecitaShapes,
            content = content,
        )
    }
}
