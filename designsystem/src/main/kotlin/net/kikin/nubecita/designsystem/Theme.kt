package net.kikin.nubecita.designsystem

import android.animation.ValueAnimator
import android.app.UiModeManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NubecitaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val contrastLevel = currentContrastLevel(context)
    val reduceMotion by reduceMotionState(context)

    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicScheme(context, darkTheme)
            else -> brandScheme(darkTheme, contrastLevel)
        }

    val motionScheme =
        remember(reduceMotion) {
            NubecitaMotionScheme(isReduced = reduceMotion)
        }

    val tokens =
        NubecitaTokens(
            semanticColors = nubecitaSemanticColors(darkTheme),
            motion = if (reduceMotion) NubecitaMotion.Reduced else NubecitaMotion.Standard,
        )

    CompositionLocalProvider(LocalNubecitaTokens provides tokens) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = nubecitaTypography,
            shapes = nubecitaShapes,
            motionScheme = motionScheme,
            content = content,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicScheme(
    context: Context,
    darkTheme: Boolean,
): ColorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

private fun brandScheme(
    darkTheme: Boolean,
    contrastLevel: ContrastLevel,
): ColorScheme =
    when {
        darkTheme && contrastLevel == ContrastLevel.High -> nubecitaDarkHighContrastColorScheme()
        darkTheme && contrastLevel == ContrastLevel.Medium -> nubecitaDarkMediumContrastColorScheme()
        darkTheme -> nubecitaDarkColorScheme()
        contrastLevel == ContrastLevel.High -> nubecitaLightHighContrastColorScheme()
        contrastLevel == ContrastLevel.Medium -> nubecitaLightMediumContrastColorScheme()
        else -> nubecitaLightColorScheme()
    }

private fun nubecitaSemanticColors(darkTheme: Boolean): NubecitaSemanticColors =
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

private enum class ContrastLevel { Standard, Medium, High }

@Composable
private fun currentContrastLevel(context: Context): ContrastLevel {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return ContrastLevel.Standard
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return ContrastLevel.Standard
    return contrastLevelFromValue(uiModeManager.contrast)
}

private fun contrastLevelFromValue(value: Float): ContrastLevel =
    when {
        value >= 0.66f -> ContrastLevel.High
        value >= 0.33f -> ContrastLevel.Medium
        else -> ContrastLevel.Standard
    }

@Composable
private fun reduceMotionState(context: Context): State<Boolean> =
    produceState(initialValue = isReduceMotionEnabled(context), context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // ValueAnimator.areAnimatorsEnabled requires API 26; on 24/25 stay on Standard motion.
            value = false
            return@produceState
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val handler = Handler(Looper.getMainLooper())
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    value = isReduceMotionEnabled(context)
                }
            }
        context.contentResolver.registerContentObserver(uri, false, observer)
        awaitDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

private fun isReduceMotionEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return !ValueAnimator.areAnimatorsEnabled()
}
