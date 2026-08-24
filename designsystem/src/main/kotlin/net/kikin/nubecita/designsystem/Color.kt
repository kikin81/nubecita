package net.kikin.nubecita.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The brand tonal palette.
 *
 * The five ramps are generated from HCT (hue, chroma, tone) coordinates with
 * `@material/material-color-utilities`, so every stop is reproducible from a
 * recorded coordinate rather than hand-picked:
 *
 * | Ramp | Role | Hue | Chroma |
 * |---|---|---|---|
 * | Sky | primary | 255 | 72 |
 * | Lagoon | secondary | 215 | 40 |
 * | Orchid | tertiary | 318 | 45 |
 * | Neutral | surfaces | 255 | 5 |
 * | NeutralVariant | outlines | 250 | 9 |
 *
 * The values are committed as literals rather than derived at runtime: they never
 * change between launches, and generating them offline keeps the schemes plain
 * `lightColorScheme(...)` / `darkColorScheme(...)` calls with no extra dependency.
 *
 * Contract: `openspec/specs/design-system`.
 */
object NubecitaPalette {
    // Primary — HCT hue 255, chroma 72. Tone 40 is the light primary, tone 80 the dark one.
    val Sky0 = Color(0xFF000000)
    val Sky10 = Color(0xFF001C37)
    val Sky20 = Color(0xFF003259)
    val Sky30 = Color(0xFF00497F)
    val Sky40 = Color(0xFF0061A6)
    val Sky50 = Color(0xFF007ACF)
    val Sky60 = Color(0xFF0094FA)
    val Sky70 = Color(0xFF67AFFF)
    val Sky80 = Color(0xFFA0C9FF)
    val Sky90 = Color(0xFFD2E4FF)
    val Sky95 = Color(0xFFEAF1FF)
    val Sky98 = Color(0xFFF8F9FF)
    val Sky99 = Color(0xFFFDFCFF)
    val Sky100 = Color(0xFFFFFFFF)

    // Secondary — HCT hue 215, chroma 40. A cool cyan; replaced the warm Peach ramp,
    // which clashed with the blue primary and failed AA at tone 50.
    val Lagoon0 = Color(0xFF000000)
    val Lagoon10 = Color(0xFF001F25)
    val Lagoon20 = Color(0xFF00363F)
    val Lagoon30 = Color(0xFF004E5B)
    val Lagoon40 = Color(0xFF006878)
    val Lagoon50 = Color(0xFF098396)
    val Lagoon60 = Color(0xFF399DB1)
    val Lagoon70 = Color(0xFF59B8CD)
    val Lagoon80 = Color(0xFF76D4E9)
    val Lagoon90 = Color(0xFFA7EDFF)
    val Lagoon95 = Color(0xFFD6F6FF)
    val Lagoon98 = Color(0xFFF0FBFF)
    val Lagoon99 = Color(0xFFF8FDFF)
    val Lagoon100 = Color(0xFFFFFFFF)

    // Tertiary — HCT hue 318, chroma 45. Reserved for auxiliary, non-critical
    // surfaces (badges, mention chips): in dark mode it is the highest-chroma of
    // the three accent families, so it out-shouts primary if used for real UI.
    val Orchid0 = Color(0xFF000000)
    val Orchid10 = Color(0xFF310049)
    val Orchid20 = Color(0xFF481B60)
    val Orchid30 = Color(0xFF613379)
    val Orchid40 = Color(0xFF7A4B92)
    val Orchid50 = Color(0xFF9564AD)
    val Orchid60 = Color(0xFFB07DC9)
    val Orchid70 = Color(0xFFCC97E5)
    val Orchid80 = Color(0xFFE8B3FF)
    val Orchid90 = Color(0xFFF6D9FF)
    val Orchid95 = Color(0xFFFDEBFF)
    val Orchid98 = Color(0xFFFFF7FC)
    val Orchid99 = Color(0xFFFFFBFF)
    val Orchid100 = Color(0xFFFFFFFF)

    // Neutral — HCT hue 255, chroma 5. Tinted toward the primary hue so the
    // greys read as chosen rather than inherited.
    val Neutral0 = Color(0xFF000000)
    val Neutral1 = Color(0xFF030406)
    val Neutral3 = Color(0xFF090B0E)
    val Neutral6 = Color(0xFF111317)
    val Neutral9 = Color(0xFF171A1D)
    val Neutral10 = Color(0xFF191C1F)
    val Neutral14 = Color(0xFF222427)
    val Neutral19 = Color(0xFF2C2E32)
    val Neutral20 = Color(0xFF2E3034)
    val Neutral26 = Color(0xFF3C3E42)
    val Neutral30 = Color(0xFF45474B)
    val Neutral40 = Color(0xFF5D5E63)
    val Neutral50 = Color(0xFF75777B)
    val Neutral60 = Color(0xFF8F9095)
    val Neutral70 = Color(0xFFAAABB0)
    val Neutral80 = Color(0xFFC5C6CB)
    val Neutral87 = Color(0xFFD9DADF)
    val Neutral90 = Color(0xFFE2E2E7)
    val Neutral92 = Color(0xFFE7E8ED)
    val Neutral94 = Color(0xFFEDEDF2)
    val Neutral95 = Color(0xFFF0F0F5)
    val Neutral96 = Color(0xFFF3F3F8)
    val Neutral98 = Color(0xFFF9F9FE)
    val Neutral99 = Color(0xFFFDFCFF)
    val Neutral100 = Color(0xFFFFFFFF)

    // Neutral variant — HCT hue 250, chroma 9. Outlines and dividers.
    val NeutralVariant30 = Color(0xFF41474F)
    val NeutralVariant50 = Color(0xFF727880)
    val NeutralVariant60 = Color(0xFF8B919A)
    val NeutralVariant80 = Color(0xFFC1C7D0)
    val NeutralVariant90 = Color(0xFFDDE3EC)

    // Error family. Deliberately NOT generated from the brand hues — these are the
    // Material 3 static error colors. Error semantics must stay recognisable across
    // themes, and harmonising them toward the brand hue would weaken the signal.
    val Error10 = Color(0xFF410002)
    val Error20 = Color(0xFF690005)
    val Error30 = Color(0xFF93000A)
    val Error40 = Color(0xFFBA1A1A)
    val Error50 = Color(0xFFDC362E)
    val Error80 = Color(0xFFFFB4AB)
    val Error90 = Color(0xFFFFDAD6)

    val Success40 = Color(0xFF006D3F)
    val Success50 = Color(0xFF1F8B5C)
    val Success80 = Color(0xFF7BD8A9)
    val Success90 = Color(0xFFB7F2D0)

    val Warning40 = Color(0xFF8A5300)
    val Warning80 = Color(0xFFFFCC80)

    /**
     * The brand identity blue — the launcher icon, the system splash background,
     * the in-app splash placeholder, and the logomark's stroke accents.
     *
     * Deliberately a fixed constant and NOT a tonal-ramp stop. It held the same
     * value as [Sky50] before the palette was regenerated, but only by
     * coincidence: tone 50 of the Sky ramp is now a different blue, while the
     * identity blue must stay put so the home-screen icon, the Play listing, and
     * the splash→app handoff do not shift.
     *
     * Its resource twin is `app/src/main/res/values/colors.xml` → `brand_sky_blue`,
     * used by `windowSplashScreenBackground` and `ic_launcher_background`. Keep the
     * two in step.
     *
     * Same rationale as [VerifiedBlue]: a constant platform signal, not theme chrome.
     */
    val LauncherBlue = Color(0xFF0A7AFF)

    /**
     * Fixed verified-blue for account-verification badges. Deliberately a constant
     * platform signal — NOT tied to the theme accent and NOT light/dark-adaptive —
     * so a verified check reads consistently everywhere, like a brand mark. Bright
     * enough for adequate contrast on both surface tones.
     */
    val VerifiedBlue = Color(0xFF208BFE)
}

/**
 * The M3 `surface*` tokens in these color schemes are assigned to depth
 * roles — pick the role first, then the token follows from the table.
 * Full contract: `docs/design-system/surface-roles.md`.
 *
 * Role → token at a glance:
 *
 * - **Screen canvas** → `surface` (Scaffold/modal root)
 * - **Item card** → `surfaceContainer` (post card, settings section, convo row)
 * - **Recessed inset** → `surfaceContainerLow` (anything nested inside an item card)
 * - **Raised affordance** → `surfaceContainerHigh` (message bubble, day chip)
 * - **Strong fill** → `surfaceContainerHighest` (thumbnails, shimmer, placeholders)
 *
 * `surfaceDim`, `surfaceBright`, and `surfaceContainerLowest` are reserved
 * and should not be used outside design-system internals — code review
 * enforces (per the nubecita-zw4k decision; a custom lint rule was
 * considered and deferred indefinitely).
 *
 * `background` is set equal to `surface` and treated as a synonym; new code
 * should reference `colorScheme.surface` rather than `colorScheme.background`
 * outside design-system internals. Code review enforces this convention.
 *
 * **Accent adjacency.** Two accent affordances rendered next to each other MUST
 * take their fills from different tiers — exactly one filled role (`primary` /
 * `secondary` / `tertiary`) and exactly one container role. M3 puts all three
 * families on the same tonal stop per tier, so any same-tier pairing differs only
 * in hue and measures ~1:1.
 */
internal fun nubecitaLightColorScheme() =
    lightColorScheme(
        primary = NubecitaPalette.Sky40,
        onPrimary = NubecitaPalette.Sky100,
        primaryContainer = NubecitaPalette.Sky90,
        onPrimaryContainer = NubecitaPalette.Sky10,
        secondary = NubecitaPalette.Lagoon40,
        onSecondary = NubecitaPalette.Lagoon100,
        secondaryContainer = NubecitaPalette.Lagoon90,
        onSecondaryContainer = NubecitaPalette.Lagoon10,
        tertiary = NubecitaPalette.Orchid40,
        onTertiary = NubecitaPalette.Orchid100,
        tertiaryContainer = NubecitaPalette.Orchid90,
        onTertiaryContainer = NubecitaPalette.Orchid10,
        error = NubecitaPalette.Error40,
        onError = NubecitaPalette.Neutral100,
        errorContainer = NubecitaPalette.Error90,
        onErrorContainer = NubecitaPalette.Error10,
        background = NubecitaPalette.Neutral99,
        onBackground = NubecitaPalette.Neutral10,
        surface = NubecitaPalette.Neutral99,
        onSurface = NubecitaPalette.Neutral10,
        surfaceVariant = NubecitaPalette.NeutralVariant90,
        onSurfaceVariant = NubecitaPalette.NeutralVariant30,
        outline = NubecitaPalette.NeutralVariant50,
        outlineVariant = NubecitaPalette.NeutralVariant80,
        scrim = NubecitaPalette.Neutral0.copy(alpha = 0.5f),
        inverseSurface = NubecitaPalette.Neutral20,
        inverseOnSurface = NubecitaPalette.Neutral95,
        inversePrimary = NubecitaPalette.Sky80,
        surfaceDim = NubecitaPalette.Neutral87,
        surfaceBright = NubecitaPalette.Neutral99,
        surfaceContainerLowest = NubecitaPalette.Neutral100,
        surfaceContainerLow = NubecitaPalette.Neutral96,
        surfaceContainer = NubecitaPalette.Neutral94,
        surfaceContainerHigh = NubecitaPalette.Neutral92,
        surfaceContainerHighest = NubecitaPalette.Neutral90,
        // Fixed accent roles hold the same value in light and dark. Assigned
        // explicitly because lightColorScheme()/darkColorScheme() default them to
        // ColorLightTokens/ColorDarkTokens — the stock Material baseline palette —
        // which would leave a Material default reachable through colorScheme.
        primaryFixed = NubecitaPalette.Sky90,
        primaryFixedDim = NubecitaPalette.Sky80,
        onPrimaryFixed = NubecitaPalette.Sky10,
        onPrimaryFixedVariant = NubecitaPalette.Sky30,
        secondaryFixed = NubecitaPalette.Lagoon90,
        secondaryFixedDim = NubecitaPalette.Lagoon80,
        onSecondaryFixed = NubecitaPalette.Lagoon10,
        onSecondaryFixedVariant = NubecitaPalette.Lagoon30,
        tertiaryFixed = NubecitaPalette.Orchid90,
        tertiaryFixedDim = NubecitaPalette.Orchid80,
        onTertiaryFixed = NubecitaPalette.Orchid10,
        onTertiaryFixedVariant = NubecitaPalette.Orchid30,
    )

/**
 * The dark scheme places `surface` at HCT tone 3 — below Material 3's canonical
 * tone 6 — for OLED power draw, with the container steps widened (1 / 3 / 6 / 9 /
 * 14 / 19 / 26) so each depth tier stays separable near black.
 *
 * This is a deliberate departure, NOT a spec-compliance fix: the palette this
 * replaced already sat at tone 5.9, i.e. it was already canonical. Do not
 * "correct" it back to tone 6.
 */
internal fun nubecitaDarkColorScheme() =
    darkColorScheme(
        primary = NubecitaPalette.Sky80,
        onPrimary = NubecitaPalette.Sky20,
        primaryContainer = NubecitaPalette.Sky30,
        onPrimaryContainer = NubecitaPalette.Sky90,
        secondary = NubecitaPalette.Lagoon80,
        onSecondary = NubecitaPalette.Lagoon20,
        secondaryContainer = NubecitaPalette.Lagoon30,
        onSecondaryContainer = NubecitaPalette.Lagoon90,
        tertiary = NubecitaPalette.Orchid80,
        onTertiary = NubecitaPalette.Orchid20,
        tertiaryContainer = NubecitaPalette.Orchid30,
        onTertiaryContainer = NubecitaPalette.Orchid90,
        error = NubecitaPalette.Error80,
        onError = NubecitaPalette.Error20,
        errorContainer = NubecitaPalette.Error30,
        onErrorContainer = NubecitaPalette.Error90,
        background = NubecitaPalette.Neutral3,
        onBackground = NubecitaPalette.Neutral90,
        surface = NubecitaPalette.Neutral3,
        onSurface = NubecitaPalette.Neutral90,
        surfaceVariant = NubecitaPalette.NeutralVariant30,
        onSurfaceVariant = NubecitaPalette.NeutralVariant80,
        outline = NubecitaPalette.NeutralVariant60,
        outlineVariant = NubecitaPalette.NeutralVariant30,
        scrim = NubecitaPalette.Neutral0.copy(alpha = 0.6f),
        inverseSurface = NubecitaPalette.Neutral90,
        inverseOnSurface = NubecitaPalette.Neutral20,
        inversePrimary = NubecitaPalette.Sky40,
        surfaceDim = NubecitaPalette.Neutral3,
        surfaceBright = NubecitaPalette.Neutral26,
        surfaceContainerLowest = NubecitaPalette.Neutral1,
        surfaceContainerLow = NubecitaPalette.Neutral6,
        surfaceContainer = NubecitaPalette.Neutral9,
        surfaceContainerHigh = NubecitaPalette.Neutral14,
        surfaceContainerHighest = NubecitaPalette.Neutral19,
        // Same values as the light scheme — fixed roles do not flip with theme.
        primaryFixed = NubecitaPalette.Sky90,
        primaryFixedDim = NubecitaPalette.Sky80,
        onPrimaryFixed = NubecitaPalette.Sky10,
        onPrimaryFixedVariant = NubecitaPalette.Sky30,
        secondaryFixed = NubecitaPalette.Lagoon90,
        secondaryFixedDim = NubecitaPalette.Lagoon80,
        onSecondaryFixed = NubecitaPalette.Lagoon10,
        onSecondaryFixedVariant = NubecitaPalette.Lagoon30,
        tertiaryFixed = NubecitaPalette.Orchid90,
        tertiaryFixedDim = NubecitaPalette.Orchid80,
        onTertiaryFixed = NubecitaPalette.Orchid10,
        onTertiaryFixedVariant = NubecitaPalette.Orchid30,
    )

// Contrast variants derive mechanically from the brand tonal palette. Light
// variants push primary/secondary/tertiary to lower (darker) tones; dark variants
// push them to higher (lighter) tones. Every color MUST come from NubecitaPalette
// — no ad-hoc hex literals — per the design-system spec.

internal fun nubecitaLightMediumContrastColorScheme() =
    nubecitaLightColorScheme().copy(
        primary = NubecitaPalette.Sky30,
        onPrimary = NubecitaPalette.Sky100,
        primaryContainer = NubecitaPalette.Sky40,
        onPrimaryContainer = NubecitaPalette.Sky100,
        secondary = NubecitaPalette.Lagoon30,
        onSecondary = NubecitaPalette.Lagoon98,
        secondaryContainer = NubecitaPalette.Lagoon40,
        onSecondaryContainer = NubecitaPalette.Lagoon98,
        tertiary = NubecitaPalette.Orchid30,
        onTertiary = NubecitaPalette.Orchid95,
        tertiaryContainer = NubecitaPalette.Orchid40,
        onTertiaryContainer = NubecitaPalette.Orchid95,
        outline = NubecitaPalette.NeutralVariant30,
    )

internal fun nubecitaLightHighContrastColorScheme() =
    nubecitaLightColorScheme().copy(
        primary = NubecitaPalette.Sky20,
        onPrimary = NubecitaPalette.Sky100,
        primaryContainer = NubecitaPalette.Sky30,
        onPrimaryContainer = NubecitaPalette.Sky100,
        secondary = NubecitaPalette.Lagoon20,
        onSecondary = NubecitaPalette.Lagoon98,
        secondaryContainer = NubecitaPalette.Lagoon30,
        onSecondaryContainer = NubecitaPalette.Lagoon98,
        tertiary = NubecitaPalette.Orchid20,
        onTertiary = NubecitaPalette.Orchid95,
        tertiaryContainer = NubecitaPalette.Orchid30,
        onTertiaryContainer = NubecitaPalette.Orchid95,
        outline = NubecitaPalette.Neutral20,
    )

internal fun nubecitaDarkMediumContrastColorScheme() =
    nubecitaDarkColorScheme().copy(
        primary = NubecitaPalette.Sky90,
        onPrimary = NubecitaPalette.Sky10,
        primaryContainer = NubecitaPalette.Sky40,
        onPrimaryContainer = NubecitaPalette.Sky99,
        secondary = NubecitaPalette.Lagoon90,
        onSecondary = NubecitaPalette.Lagoon10,
        secondaryContainer = NubecitaPalette.Lagoon40,
        onSecondaryContainer = NubecitaPalette.Lagoon98,
        tertiary = NubecitaPalette.Orchid90,
        onTertiary = NubecitaPalette.Orchid10,
        tertiaryContainer = NubecitaPalette.Orchid40,
        onTertiaryContainer = NubecitaPalette.Orchid95,
        outline = NubecitaPalette.NeutralVariant80,
    )

internal fun nubecitaDarkHighContrastColorScheme() =
    nubecitaDarkColorScheme().copy(
        primary = NubecitaPalette.Sky95,
        onPrimary = NubecitaPalette.Sky10,
        primaryContainer = NubecitaPalette.Sky80,
        onPrimaryContainer = NubecitaPalette.Sky10,
        secondary = NubecitaPalette.Lagoon95,
        onSecondary = NubecitaPalette.Lagoon10,
        secondaryContainer = NubecitaPalette.Lagoon80,
        onSecondaryContainer = NubecitaPalette.Lagoon10,
        tertiary = NubecitaPalette.Orchid95,
        onTertiary = NubecitaPalette.Orchid10,
        tertiaryContainer = NubecitaPalette.Orchid70,
        onTertiaryContainer = NubecitaPalette.Orchid10,
        outline = NubecitaPalette.NeutralVariant90,
    )
