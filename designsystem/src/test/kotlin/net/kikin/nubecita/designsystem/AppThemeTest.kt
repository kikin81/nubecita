package net.kikin.nubecita.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The [AppTheme] resolution table from `openspec/changes/add-theme-selection`.
 *
 * Both halves are pure properties precisely so they can be asserted here without
 * a Compose runtime — the only Composable part is `resolvesToDark()`, which is
 * `forcedDarkTheme ?: isSystemInDarkTheme()` and carries no branch of its own.
 */
class AppThemeTest {
    @Test
    fun dynamic_usesWallpaperColor() {
        assertTrue(AppTheme.Dynamic.usesDynamicColor)
    }

    @Test
    fun lightAndDark_optOutOfWallpaperColor() {
        // Picking an explicit brightness means picking the brand palette —
        // there is no second "use wallpaper colors" axis (design D1).
        assertFalse(AppTheme.Light.usesDynamicColor)
        assertFalse(AppTheme.Dark.usesDynamicColor)
    }

    @Test
    fun dynamic_defersDarknessToTheOs() {
        assertNull(AppTheme.Dynamic.forcedDarkTheme)
    }

    @Test
    fun light_forcesLight() {
        assertEquals(false, AppTheme.Light.forcedDarkTheme)
    }

    @Test
    fun dark_forcesDark() {
        assertEquals(true, AppTheme.Dark.forcedDarkTheme)
    }

    @Test
    fun exactlyOneThemeDefersToTheOs() {
        // Guards the invariant a future Custom theme must preserve: every option
        // other than Dynamic pins its own brightness, so the picker can never
        // land in a state where two options both mean "follow the system".
        val deferring = AppTheme.entries.filter { it.forcedDarkTheme == null }
        assertEquals(listOf(AppTheme.Dynamic), deferring)
    }

    @Test
    fun theOptionListIsExactlyTheThreeSpecifiedThemes() {
        // Pins the rendering-side option set and its order. The Appearance
        // picker actually renders ThemePreference.entries — it edits storage —
        // so the value of this test is that the two enums cannot drift apart:
        // :app maps between them with exhaustive `when`s, and adding Custom to
        // one without the other would break that mapping. Failing here is the
        // prompt to extend both, plus the screen's baselines.
        assertEquals(
            listOf(AppTheme.Dynamic, AppTheme.Light, AppTheme.Dark),
            AppTheme.entries.toList(),
        )
    }
}
