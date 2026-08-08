package net.kikin.nubecita.feature.settings.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.preferences.ThemePreference
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
internal class AppearanceViewModelTest {
    @Test
    fun `state reflects the stored preference`() =
        runTest {
            val vm = viewModelWith(MutableStateFlow(ThemePreference.DARK))
            runCurrent()

            assertEquals(ThemePreference.DARK, vm.uiState.value.selected)
        }

    @Test
    fun `selecting a theme writes it through`() =
        runTest {
            val prefs = MutableStateFlow(ThemePreference.DYNAMIC)
            val repository = repositoryFor(prefs)
            val vm = AppearanceViewModel(repository)
            runCurrent()

            vm.handleEvent(AppearanceEvent.ThemeSelected(ThemePreference.LIGHT))
            runCurrent()

            coVerify(exactly = 1) { repository.setThemePreference(ThemePreference.LIGHT) }
        }

    @Test
    fun `state follows a repository re-emission rather than the tap`() =
        runTest {
            // The rendered selection is a projection of storage, not local
            // optimistic state, so the list and the app can never disagree.
            val prefs = MutableStateFlow(ThemePreference.DYNAMIC)
            val vm = viewModelWith(prefs)
            runCurrent()

            prefs.value = ThemePreference.DARK
            runCurrent()

            assertEquals(ThemePreference.DARK, vm.uiState.value.selected)
        }

    @Test
    fun `re-tapping the selected theme performs no write`() =
        runTest {
            // NubecitaListItem's onSelect fires on every tap, including the
            // already-selected row, and its KDoc puts the guard here rather than
            // in the UI so it survives a swap of the control.
            val prefs = MutableStateFlow(ThemePreference.DARK)
            val repository = repositoryFor(prefs)
            val vm = AppearanceViewModel(repository)
            runCurrent()

            vm.handleEvent(AppearanceEvent.ThemeSelected(ThemePreference.DARK))
            runCurrent()

            coVerify(exactly = 0) { repository.setThemePreference(any()) }
            assertEquals(ThemePreference.DARK, vm.uiState.value.selected)
        }

    @Test
    fun `a failed write surfaces a save error`() =
        runTest {
            val prefs = MutableStateFlow(ThemePreference.DYNAMIC)
            val repository = repositoryFor(prefs)
            coEvery { repository.setThemePreference(any()) } throws IllegalStateException("disk full")
            val vm = AppearanceViewModel(repository)
            runCurrent()

            vm.effects.test {
                vm.handleEvent(AppearanceEvent.ThemeSelected(ThemePreference.DARK))
                runCurrent()
                assertEquals(AppearanceEffect.ShowSaveError, awaitItem())
            }
        }

    private fun repositoryFor(prefs: MutableStateFlow<ThemePreference>): UserPreferencesRepository =
        mockk<UserPreferencesRepository>(relaxed = true).also {
            io.mockk.every { it.themePreference } returns prefs
        }

    private fun viewModelWith(prefs: MutableStateFlow<ThemePreference>): AppearanceViewModel = AppearanceViewModel(repositoryFor(prefs))
}
