package net.kikin.nubecita.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

private val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

val FrauncesFontFamily =
    FontFamily(
        Font(
            googleFont = GoogleFont("Fraunces"),
            fontProvider = provider,
            weight = FontWeight.SemiBold,
        ),
        Font(resId = R.font.roboto_flex, weight = FontWeight.SemiBold), // Fallback to Roboto Flex if Fraunces fails
    )

val MaterialSymbolsRoundedFontFamily =
    FontFamily(
        Font(
            googleFont = GoogleFont("Material Symbols Rounded"),
            fontProvider = provider,
        ),
    )

val RobotoFlexFontFamily =
    FontFamily(
        Font(resId = R.font.roboto_flex),
    )

val JetBrainsMonoFontFamily =
    FontFamily(
        Font(resId = R.font.jetbrains_mono),
    )
