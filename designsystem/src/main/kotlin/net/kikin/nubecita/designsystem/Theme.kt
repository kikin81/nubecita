package net.kikin.nubecita.designsystem

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import net.kikin.nubecita.designsystem.hero.DefaultGradientCache
import net.kikin.nubecita.designsystem.hero.LocalBoldHeroGradientCache

// SuppressLint: the Build.VERSION.SDK_INT >= S guard directly wraps the dynamic*
// calls, but AGP 9.2.0 lint's NewApi detector doesn't trace the guard through the
// nested-if structure. The runtime check is provably correct; the baseline
// fallback (brandScheme) renders on pre-12 and when dynamicColor is off.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("NewApi")
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
        if (dynamicColor) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                brandScheme(darkTheme, contrastLevel)
            }
        } else {
            brandScheme(darkTheme, contrastLevel)
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

    CompositionLocalProvider(
        LocalNubecitaTokens provides tokens,
        LocalBoldHeroGradientCache provides DefaultGradientCache,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = nubecitaTypography,
            shapes = nubecitaShapes,
            motionScheme = motionScheme,
            content = content,
        )
    }
}

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
            videoOverlayScrim = VideoOverlayScrim,
            videoOverlayScrimSubtle = VideoOverlayScrimSubtle,
            onVideoOverlay = OnVideoOverlay,
        )
    } else {
        NubecitaSemanticColors(
            success = NubecitaPalette.Success40,
            onSuccess = NubecitaPalette.Neutral100,
            successContainer = NubecitaPalette.Success90,
            onSuccessContainer = NubecitaPalette.Success40,
            warning = NubecitaPalette.Warning40,
            onWarning = NubecitaPalette.Neutral100,
            videoOverlayScrim = VideoOverlayScrim,
            videoOverlayScrimSubtle = VideoOverlayScrimSubtle,
            onVideoOverlay = OnVideoOverlay,
        )
    }

// Video overlay tokens are theme-invariant — they sit on top of
// arbitrary user-supplied video content, so the contrast target is
// the video pixels rather than the page background. Held as named
// constants here (vs inlining `Color(0xCC...).copy(...)` at every
// call site) so the values stay consistent and the intent is named.
private val VideoOverlayScrim = NubecitaPalette.Neutral0.copy(alpha = 0.8f)
private val VideoOverlayScrimSubtle = NubecitaPalette.Neutral0.copy(alpha = 0.4f)
private val OnVideoOverlay = NubecitaPalette.Neutral100

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
    produceState(initialValue = isReduceMotionEnabled(), context) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val handler = Handler(Looper.getMainLooper())
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    value = isReduceMotionEnabled()
                }
            }
        context.contentResolver.registerContentObserver(uri, false, observer)
        awaitDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

private fun isReduceMotionEnabled(): Boolean = !ValueAnimator.areAnimatorsEnabled()
