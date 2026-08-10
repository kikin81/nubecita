package net.kikin.nubecita.feature.settings.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.preferences.AutoplayPreference
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
internal class MediaAndAnimationsViewModelTest {
    @Test
    fun `state reflects both stored preferences`() =
        runTest {
            val vm =
                viewModelWith(
                    autoplay = MutableStateFlow(AutoplayPreference.WIFI_ONLY),
                    gifs = MutableStateFlow(false),
                )
            runCurrent()

            assertEquals(AutoplayPreference.WIFI_ONLY, vm.uiState.value.autoplay)
            assertFalse(vm.uiState.value.autoplayGifs)
        }

    @Test
    fun `selecting an autoplay option writes it through`() =
        runTest {
            val repository = repositoryFor(MutableStateFlow(AutoplayPreference.ALWAYS), MutableStateFlow(true))
            val vm = MediaAndAnimationsViewModel(repository)
            runCurrent()

            vm.handleEvent(MediaAndAnimationsEvent.AutoplaySelected(AutoplayPreference.NEVER))
            runCurrent()

            coVerify(exactly = 1) { repository.setAutoplayPreference(AutoplayPreference.NEVER) }
        }

    @Test
    fun `toggling GIFs writes it through`() =
        runTest {
            val repository = repositoryFor(MutableStateFlow(AutoplayPreference.ALWAYS), MutableStateFlow(true))
            val vm = MediaAndAnimationsViewModel(repository)
            runCurrent()

            vm.handleEvent(MediaAndAnimationsEvent.AutoplayGifsToggled(false))
            runCurrent()

            coVerify(exactly = 1) { repository.setAutoplayGifs(false) }
        }

    @Test
    fun `state follows a repository re-emission rather than the tap`() =
        runTest {
            // Both controls are projections of storage, not local optimistic
            // state, so what the screen shows and what playback honours cannot
            // disagree — including when another surface changes the value.
            val autoplay = MutableStateFlow(AutoplayPreference.ALWAYS)
            val gifs = MutableStateFlow(true)
            val vm = viewModelWith(autoplay, gifs)
            runCurrent()

            autoplay.value = AutoplayPreference.WIFI_ONLY
            gifs.value = false
            runCurrent()

            assertEquals(AutoplayPreference.WIFI_ONLY, vm.uiState.value.autoplay)
            assertFalse(vm.uiState.value.autoplayGifs)
        }

    @Test
    fun `re-tapping the selected autoplay option performs no write`() =
        runTest {
            // NubecitaListItem's onSelect fires on every tap including the
            // already-selected row; the guard lives in the VM so it survives a
            // swap of the control.
            val repository = repositoryFor(MutableStateFlow(AutoplayPreference.NEVER), MutableStateFlow(true))
            val vm = MediaAndAnimationsViewModel(repository)
            runCurrent()

            vm.handleEvent(MediaAndAnimationsEvent.AutoplaySelected(AutoplayPreference.NEVER))
            runCurrent()

            coVerify(exactly = 0) { repository.setAutoplayPreference(any()) }
            assertEquals(AutoplayPreference.NEVER, vm.uiState.value.autoplay)
        }

    @Test
    fun `re-emitting the current GIF value performs no write`() =
        runTest {
            val repository = repositoryFor(MutableStateFlow(AutoplayPreference.ALWAYS), MutableStateFlow(true))
            val vm = MediaAndAnimationsViewModel(repository)
            runCurrent()

            vm.handleEvent(MediaAndAnimationsEvent.AutoplayGifsToggled(true))
            runCurrent()

            coVerify(exactly = 0) { repository.setAutoplayGifs(any()) }
            assertTrue(vm.uiState.value.autoplayGifs)
        }

    @Test
    fun `a failed autoplay write surfaces a save error`() =
        runTest {
            val repository = repositoryFor(MutableStateFlow(AutoplayPreference.ALWAYS), MutableStateFlow(true))
            coEvery { repository.setAutoplayPreference(any()) } throws IllegalStateException("disk full")
            val vm = MediaAndAnimationsViewModel(repository)
            runCurrent()

            vm.effects.test {
                vm.handleEvent(MediaAndAnimationsEvent.AutoplaySelected(AutoplayPreference.NEVER))
                runCurrent()
                assertEquals(MediaAndAnimationsEffect.ShowSaveError, awaitItem())
            }
        }

    @Test
    fun `a failed GIF write surfaces a save error`() =
        runTest {
            val repository = repositoryFor(MutableStateFlow(AutoplayPreference.ALWAYS), MutableStateFlow(true))
            coEvery { repository.setAutoplayGifs(any()) } throws IllegalStateException("disk full")
            val vm = MediaAndAnimationsViewModel(repository)
            runCurrent()

            vm.effects.test {
                vm.handleEvent(MediaAndAnimationsEvent.AutoplayGifsToggled(false))
                runCurrent()
                assertEquals(MediaAndAnimationsEffect.ShowSaveError, awaitItem())
            }
        }

    private fun repositoryFor(
        autoplay: MutableStateFlow<AutoplayPreference>,
        gifs: MutableStateFlow<Boolean>,
    ): UserPreferencesRepository =
        mockk<UserPreferencesRepository>(relaxed = true).also {
            every { it.autoplayPreference } returns autoplay
            every { it.autoplayGifs } returns gifs
        }

    private fun viewModelWith(
        autoplay: MutableStateFlow<AutoplayPreference>,
        gifs: MutableStateFlow<Boolean>,
    ): MediaAndAnimationsViewModel = MediaAndAnimationsViewModel(repositoryFor(autoplay, gifs))
}
