package net.kikin.nubecita.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val nubecitaTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.02).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = (-0.015).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.01).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.01).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.005).sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.01.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.01.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                lineHeight = 26.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.01.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.04.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.04.sp,
            ),
    )

data class NubecitaExtendedTypography(
    val mono: TextStyle =
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 14.sp,
        ),
)
