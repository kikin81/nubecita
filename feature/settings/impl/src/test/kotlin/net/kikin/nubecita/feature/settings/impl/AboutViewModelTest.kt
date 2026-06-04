package net.kikin.nubecita.feature.settings.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.profile.ActorProfile
import net.kikin.nubecita.core.profile.ActorProfileRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class AboutViewModelTest {
    private val stavfxDid = "did:plc:q46tlww4otcbawdeynycankw"

    private fun profileFor(did: String): ActorProfile =
        ActorProfile(
            did = did,
            handle = "$did.example",
            displayName = "Name $did",
            avatarUrl = "https://cdn.example/$did.jpg",
        )

    @Test
    fun `hydrates every thanks row by fetching the profile`() =
        runTest {
            val repo = mockk<ActorProfileRepository>()
            coEvery { repo.fetchProfile(any()) } answers { Result.success(profileFor(firstArg())) }

            val vm = AboutViewModel(repo)

            vm.uiState.test {
                var state = awaitItem()
                while (state.isLoadingThanks) state = awaitItem()
                assertFalse(state.isLoadingThanks)
                assertEquals(4, state.thanks.size)
                assertTrue(state.thanks.all { it.avatarUrl != null && it.displayName != null })
            }
        }

    @Test
    fun `falls back to the curated handle when a profile fetch fails`() =
        runTest {
            val repo = mockk<ActorProfileRepository>()
            coEvery { repo.fetchProfile(any()) } answers { Result.success(profileFor(firstArg())) }
            coEvery { repo.fetchProfile(stavfxDid) } returns Result.failure(RuntimeException("boom"))

            val vm = AboutViewModel(repo)

            vm.uiState.test {
                var state = awaitItem()
                while (state.isLoadingThanks) state = awaitItem()
                val stavfxRow = state.thanks.first { it.did == stavfxDid }
                assertNull(stavfxRow.avatarUrl)
                assertEquals("stavfx.com", stavfxRow.handle)
                // Other rows still hydrate — one failure does not blank the section.
                assertTrue(state.thanks.filter { it.did != stavfxDid }.all { it.avatarUrl != null })
            }
        }

    @Test
    fun `source tap launches the github url`() =
        runTest {
            val repo = mockk<ActorProfileRepository>()
            coEvery { repo.fetchProfile(any()) } answers { Result.success(profileFor(firstArg())) }
            val vm = AboutViewModel(repo)

            vm.effects.test {
                vm.handleEvent(AboutEvent.SourceTapped)
                assertEquals(AboutEffect.LaunchUri(AboutViewModel.GITHUB_URL), awaitItem())
            }
        }

    @Test
    fun `thanks row tap navigates to that profile and licenses tap opens licenses`() =
        runTest {
            val repo = mockk<ActorProfileRepository>()
            coEvery { repo.fetchProfile(any()) } answers { Result.success(profileFor(firstArg())) }
            val vm = AboutViewModel(repo)

            vm.effects.test {
                vm.handleEvent(AboutEvent.ThanksRowTapped(stavfxDid))
                assertEquals(AboutEffect.NavigateToProfile(stavfxDid), awaitItem())

                vm.handleEvent(AboutEvent.LicensesTapped)
                assertEquals(AboutEffect.OpenLicenses, awaitItem())
            }
        }
}
