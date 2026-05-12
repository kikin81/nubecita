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

// Bundled variable .ttf so the brand's variable axes (`SOFT`, `wght`) are
// applicable via FontVariation.Settings — Google Downloadable Fonts ignore
// per-Font variation settings. The prior wiring (Google Downloadable +
// no SOFT) was tolerated as a tradeoff for APK size; the profile hero's
// `displayNameStyle` requires SOFT = 70 (see openspec/specs/design-system),
// which forces the bundle. The variable .ttf (~360 KB) ships with the
// SOFT [0..100] and wght [100..900] axes — only the entries we actually
// need are declared below.
val FrauncesFontFamily =
    FontFamily(
        Font(
            resId = R.font.fraunces,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
    )

// Display-name-only variant: same .ttf, same weight (SemiBold), but with
// `SOFT = 70` so the soft-cornered Fraunces variant renders on profile
// hero display names. Kept separate from `FrauncesFontFamily` so existing
// display/headline usages keep their current rendering (SOFT = 0 default);
// only sites that opt into `extendedTypography.displayName` get SOFT 70.
val FrauncesDisplayFontFamily =
    FontFamily(
        Font(
            resId = R.font.fraunces,
            weight = FontWeight.SemiBold,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(600),
                    FontVariation.Setting("SOFT", 70f),
                ),
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

// Bundled variable .ttf with a single `wght` axis [100..800]. Same pattern as
// Roboto Flex above: one Font entry per logical weight, each pointing at the
// same .ttf with `variationSettings` pinning the axis. Without per-weight
// entries the engine renders every weight at the file's default axis value
// (~400), collapsing any weight scale callers ask for.
val JetBrainsMonoFontFamily =
    FontFamily(
        Font(
            resId = R.font.jetbrains_mono,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            resId = R.font.jetbrains_mono,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            resId = R.font.jetbrains_mono,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
        Font(
            resId = R.font.jetbrains_mono,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )
