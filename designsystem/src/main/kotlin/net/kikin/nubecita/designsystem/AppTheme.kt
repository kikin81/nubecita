package net.kikin.nubecita.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * The theme the app renders in — the **rendering** identity that the
 * composition root passes to [NubecitaTheme].
 *
 * Its storage counterpart is `net.kikin.nubecita.core.preferences.ThemePreference`
 * (constants `DYNAMIC` / `LIGHT` / `DARK`), which `:app` maps to this. The two
 * are separate types on purpose: this module must not depend on
 * `:core:preferences`, which is flavored (`production` / `bench`) and would
 * ripple flavors through every UI module's variant matrix.
 *
 * The options form a single mutually-exclusive axis — there is no separate
 * "use wallpaper colors" switch, because a boolean could not express a third
 * color source once custom themes land. A future `Custom` entry appends here,
 * and the `when` in [forcedDarkTheme] will fail to compile until it is handled.
 */
enum class AppTheme {
    /** Material You wallpaper-derived color, light/dark following the OS. */
    Dynamic,

    /** The Nubecita brand palette, light, regardless of the OS setting. */
    Light,

    /** The Nubecita brand palette, dark, regardless of the OS setting. */
    Dark,
}

/**
 * Whether this theme sources its color scheme from the wallpaper (Material You).
 *
 * Only [AppTheme.Dynamic] does; choosing an explicit brightness means choosing
 * the brand palette. On Android 11 and earlier the dynamic color API is
 * unavailable and [NubecitaTheme] falls back to the brand palette regardless.
 *
 * Spelled as an exhaustive `when` rather than `this == Dynamic` so that adding a
 * future `Custom` theme is a compile error here — under equality it would
 * silently default to "not dynamic", which may well be wrong for a custom
 * palette that wants to blend with the wallpaper.
 */
val AppTheme.usesDynamicColor: Boolean
    get() =
        when (this) {
            AppTheme.Dynamic -> true
            AppTheme.Light -> false
            AppTheme.Dark -> false
        }

/**
 * The darkness this theme pins, or `null` when it defers to the OS setting.
 *
 * Kept as a pure property (rather than folding it into the Composable below) so
 * the whole resolution table is assertable without a Compose runtime — see
 * `AppThemeTest`.
 */
val AppTheme.forcedDarkTheme: Boolean?
    get() =
        when (this) {
            AppTheme.Dynamic -> null
            AppTheme.Light -> false
            AppTheme.Dark -> true
        }

/**
 * Whether this theme resolves to dark *right now*, reading the OS setting when
 * the theme defers to it.
 *
 * The composition root needs this beyond picking a color scheme: system bar
 * icon polarity must follow the resolved theme rather than the OS, or an app
 * themed [AppTheme.Dark] on an OS in light mode draws dark status-bar icons
 * over a dark surface and they vanish.
 */
@Composable
fun AppTheme.resolvesToDark(): Boolean = forcedDarkTheme ?: isSystemInDarkTheme()
