package net.kikin.nubecita.core.preferences

/**
 * The user's app-theme choice, as persisted by [UserPreferencesRepository].
 *
 * This is the **storage** identity. Its rendering counterpart is
 * `net.kikin.nubecita.designsystem.AppTheme` (constants `Dynamic` / `Light` /
 * `Dark`), which the composition root maps this to. The two are deliberately
 * separate types: `:designsystem` must not depend on this module, which is
 * flavored (`production` / `bench`) and would ripple flavors through every UI
 * module's variant matrix.
 *
 * The three options are mutually exclusive — there is no separate "use
 * wallpaper colors" axis:
 *
 * - [DYNAMIC] — Material You wallpaper-derived color, light/dark following the
 *   OS. The default, and the app's behavior before a picker existed.
 * - [LIGHT] / [DARK] — the Nubecita brand palette locked to that brightness,
 *   regardless of the OS setting.
 *
 * Persisted by [Enum.name]. An absent or unrecognized stored value maps to
 * [DYNAMIC], which is what lets a build read a value written by a newer one
 * (a future custom theme) without crashing — see
 * `DefaultUserPreferencesRepository.themePreference`. A future `CUSTOM` option
 * appends to this enum rather than adding a second axis; keep the fallback
 * intact so older builds degrade instead of throwing.
 *
 * `:core:analytics` declares its own same-named enum for GA4 wire values;
 * `ProAnalyticsCoordinator` maps between them and [DYNAMIC] deliberately keeps
 * the pre-existing `"system"` wire value so historical `theme_preference`
 * reporting stays continuous.
 */
enum class ThemePreference {
    DYNAMIC,
    LIGHT,
    DARK,
}
