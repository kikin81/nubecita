// ============================================================
// Nubecita — M3 Typography
// Fraunces (display) + Roboto Flex (body) + JetBrains Mono.
// Add the three font families to res/font/ first — see README.
// ============================================================
package app.nubecita.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Replace these stubs with real Font(R.font.fraunces_*) entries.
val Fraunces    = FontFamily.Default      // TODO swap for Font(R.font.fraunces_variable)
val RobotoFlex  = FontFamily.Default      // TODO swap for Font(R.font.roboto_flex_variable)
val JetBrains   = FontFamily.Monospace    // TODO swap for Font(R.font.jetbrains_mono)

val NubecitaTypography = Typography(
    // ── DISPLAY (Fraunces, expressive) ──
    displayLarge = TextStyle(
        fontFamily = Fraunces, fontWeight = FontWeight.SemiBold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.02).em,
    ),
    displayMedium = TextStyle(
        fontFamily = Fraunces, fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.015).em,
    ),
    displaySmall = TextStyle(
        fontFamily = Fraunces, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em,
    ),

    // ── HEADLINE (large = Fraunces, medium/small = Roboto Flex bold) ──
    headlineLarge = TextStyle(
        fontFamily = Fraunces, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = RobotoFlex, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.005).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = RobotoFlex, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 32.sp,
    ),

    // ── TITLE ──
    titleLarge  = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.01.em),
    titleSmall  = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),

    // ── BODY (note bodyLarge bumped to 17sp for feed readability) ──
    bodyLarge  = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall  = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),

    // ── LABEL ──
    labelLarge  = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.em),
    labelSmall  = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.04.em),
)
