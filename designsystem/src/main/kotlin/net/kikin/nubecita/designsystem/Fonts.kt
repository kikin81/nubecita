@file:OptIn(ExperimentalTextApi::class)

package net.kikin.nubecita.designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

private val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

// Google Downloadable Fonts don't accept FontVariation.Settings, so the brand's
// Fraunces `SOFT 50` axis customization isn't applied at runtime — the font
// renders at its default axis values. To apply SOFT 50, bundle the variable .ttf
// in res/font/ and swap to Font(resId = ..., variationSettings = ...) — variable
// axes work on minSdk 26 (our current floor). Visual difference at brand display
// sizes is modest; we accept it to keep Fraunces off the APK (~500KB).
val FrauncesFontFamily =
    FontFamily(
        Font(
            googleFont = GoogleFont("Fraunces"),
            fontProvider = provider,
            weight = FontWeight.SemiBold,
        ),
    )

val MaterialSymbolsRoundedFontFamily =
    FontFamily(
        Font(
            googleFont = GoogleFont("Material Symbols Rounded"),
            fontProvider = provider,
        ),
    )

// Bundled variable .ttf so body text renders instantly with no FOUT on cold start.
// Roboto Flex is a variable font with a `wght` axis. Without one Font entry per
// requested FontWeight + matching FontVariation.weight() axis position, the
// engine renders every weight at the file's default axis value (~700), which
// collapses Type.kt's scale into a uniformly-bold mush. Per-weight Font entries
// reference the same .ttf — only variationSettings change.
val RobotoFlexFontFamily =
    FontFamily(
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )

val JetBrainsMonoFontFamily =
    FontFamily(
        Font(resId = R.font.jetbrains_mono),
    )
