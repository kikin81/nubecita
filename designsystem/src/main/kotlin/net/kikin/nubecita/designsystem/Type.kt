package net.kikin.nubecita.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val nubecitaTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.02).em,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = (-0.015).em,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.01).em,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.01).em,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.005).em,
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
                letterSpacing = 0.01.em,
            ),
        titleSmall =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.01.em,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                // ratio 1.29 — feed-density tight (matches Bluesky's reference
                // ~1.25-1.30). M3 default 1.5 / our prior 1.53 was long-form-
                // reading air; in a scrollable feed where post bodies are
                // typically 1-3 lines, that vertical air made the feed look
                // less dense than peer clients. 22sp keeps comfortable read
                // for multi-line posts while halving the cross-card cost.
                lineHeight = 22.sp,
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
                letterSpacing = 0.01.em,
            ),
        labelMedium =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.04.em,
            ),
        labelSmall =
            TextStyle(
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.04.em,
            ),
    )

data class NubecitaExtendedTypography(
    val mono: TextStyle =
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 14.sp,
        ),
    // Profile hero display name. Uses Fraunces's `SOFT = 70` variant
    // (see Fonts.kt#FrauncesDisplayFontFamily) per the openspec design-
    // system requirement. Size is sub-`displayLarge` (57sp) because the
    // hero is composed against an avatar + handle + bio at a phone
    // scale; the wireframes show the name occupying ~1.5× the bio
    // height. Letter-spacing matches the prompt's −0.02 em.
    val displayName: TextStyle =
        TextStyle(
            fontFamily = FrauncesDisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.02).em,
        ),
    // Profile handle (`@alice.bsky.social`). Monospace by design intent.
    // 13sp is intentionally one step below `mono` (14sp) so the handle
    // reads as secondary text against the display-name above it.
    val handle: TextStyle =
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
)
