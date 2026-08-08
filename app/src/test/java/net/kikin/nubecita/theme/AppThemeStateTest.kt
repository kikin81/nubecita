package net.kikin.nubecita.theme

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.preferences.ThemePreference
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.designsystem.AppTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppThemeStateTest {
    @Test
    fun `stays null until the stored preference resolves`() =
        runTest(UnconfinedTestDispatcher()) {
            // Modelled with a replay-less SharedFlow because that is what DataStore
            // looks like at cold start: subscribed, but no value yet. The null latch
            // is the whole point — MainActivity holds the splash while it is null,
            // which is what stops a Dark-theme user seeing a light first frame. A
            // non-null seed would defeat that by claiming a theme before the store
            // answered.
            val preference = MutableSharedFlow<ThemePreference>()
            val state = appThemeStateWith(preference)
            runCurrent()

            assertNull(state.appTheme.value)

            preference.emit(ThemePreference.DARK)
            runCurrent()

            assertEquals(AppTheme.Dark, state.appTheme.value)
        }

    @Test
    fun `maps every ThemePreference to its AppTheme counterpart`() =
        runTest(UnconfinedTestDispatcher()) {
            val expected =
                mapOf(
                    ThemePreference.DYNAMIC to AppTheme.Dynamic,
                    ThemePreference.LIGHT to AppTheme.Light,
                    ThemePreference.DARK to AppTheme.Dark,
                )
            // Keyed off entries so a future CUSTOM preference fails here too, not
            // only at the mapping's compile-time `when`.
            assertEquals(ThemePreference.entries.toSet(), expected.keys)

            expected.forEach { (preference, appTheme) ->
                val state = appThemeStateWith(MutableStateFlow(preference))
                runCurrent()
                assertEquals(appTheme, state.appTheme.value, "wrong AppTheme for $preference")
            }
        }

    @Test
    fun `keeps observing so every later theme change still lands`() =
        runTest(UnconfinedTestDispatcher()) {
            // Guards against a "read once and stop" refactor: the picker changing
            // the theme mid-session must keep reaching the composition root, not
            // just the first time.
            val preference = MutableStateFlow(ThemePreference.DYNAMIC)
            val state = appThemeStateWith(preference)
            runCurrent()
            assertEquals(AppTheme.Dynamic, state.appTheme.value)

            preference.value = ThemePreference.LIGHT
            runCurrent()
            assertEquals(AppTheme.Light, state.appTheme.value)

            preference.value = ThemePreference.DARK
            runCurrent()
            assertEquals(AppTheme.Dark, state.appTheme.value)
        }

    private fun TestScope.appThemeStateWith(preference: Flow<ThemePreference>): AppThemeState {
        val prefs = mockk<UserPreferencesRepository>(relaxed = true)
        every { prefs.themePreference } returns preference
        return AppThemeState(userPreferencesRepository = prefs, scope = backgroundScope)
    }
}
