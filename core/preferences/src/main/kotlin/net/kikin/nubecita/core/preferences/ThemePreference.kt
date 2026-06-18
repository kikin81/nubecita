package net.kikin.nubecita.core.preferences

/**
 * The user's app-theme choice. Persisted by [UserPreferencesRepository].
 *
 * NOTE (nubecita-y5h5): the app does not yet expose a theme picker — `NubecitaTheme`
 * currently follows the OS via `isSystemInDarkTheme()`, so this preference reads
 * [SYSTEM] for everyone until a picker writes it. The storage + flow exist now so
 * the `theme_preference` analytics user property has a single source of truth that
 * becomes accurate the moment a picker (and a `NubecitaTheme` read of this value)
 * lands. Kept in `:core:preferences` (the storage owner) rather than depending on
 * `:core:analytics`; the coordinator maps this to the analytics wire enum.
 */
enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM,
}
