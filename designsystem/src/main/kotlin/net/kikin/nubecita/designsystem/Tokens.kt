@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package net.kikin.nubecita.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

data class NubecitaTokens(
    val spacing: NubecitaSpacing = NubecitaSpacing(),
    val elevation: NubecitaElevation = NubecitaElevation(),
    val semanticColors: NubecitaSemanticColors,
    val motionDurations: NubecitaDurations = NubecitaDurations(),
    val extendedShape: NubecitaExtendedShape = NubecitaExtendedShape(),
    val extendedTypography: NubecitaExtendedTypography = NubecitaExtendedTypography(),
)

data class NubecitaSemanticColors(
    val success: androidx.compose.ui.graphics.Color,
    val onSuccess: androidx.compose.ui.graphics.Color,
    val successContainer: androidx.compose.ui.graphics.Color,
    val onSuccessContainer: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val onWarning: androidx.compose.ui.graphics.Color,
)

data class NubecitaDurations(
    val short1: Int = 50,
    val short2: Int = 100,
    val short3: Int = 150,
    val short4: Int = 200,
    val medium1: Int = 250,
    val medium2: Int = 300,
    val medium3: Int = 350,
    val medium4: Int = 400,
    val long1: Int = 450,
    val long2: Int = 500,
    val long3: Int = 550,
    val long4: Int = 600,
    val xLong1: Int = 700,
)

val LocalNubecitaTokens =
    staticCompositionLocalOf<NubecitaTokens> {
        error("NubecitaTokens not provided — wrap your UI with NubecitaTheme { ... }")
    }

val MaterialTheme.spacing: NubecitaSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalNubecitaTokens.current.spacing

val MaterialTheme.elevation: NubecitaElevation
    @Composable
    @ReadOnlyComposable
    get() = LocalNubecitaTokens.current.elevation

val MaterialTheme.semanticColors: NubecitaSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalNubecitaTokens.current.semanticColors

val MaterialTheme.motionDurations: NubecitaDurations
    @Composable
    @ReadOnlyComposable
    get() = LocalNubecitaTokens.current.motionDurations

val MaterialTheme.extendedShape: NubecitaExtendedShape
    @Composable
    @ReadOnlyComposable
    get() = LocalNubecitaTokens.current.extendedShape

val MaterialTheme.extendedTypography: NubecitaExtendedTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalNubecitaTokens.current.extendedTypography
