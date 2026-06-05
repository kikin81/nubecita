package net.kikin.nubecita.feature.settings.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.cancellation.CancellationException

class ContentFiltersViewModelTest {
    // RegisterExtension (not ExtendWith) so `runTest(mainDispatcher.dispatcher)`
    // shares Main's scheduler — otherwise advanceUntilIdle() can't drive the
    // viewModelScope writes/collectors.
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val adultOn = ModerationPrefs.DEFAULT.copy(adultContentEnabled = true)

    private fun fakeRepo(
        initial: ModerationPrefs = ModerationPrefs.DEFAULT,
    ): Pair<ModerationPreferencesRepository, MutableStateFlow<ModerationPrefs>> {
        val flow = MutableStateFlow(initial)
        val repo = mockk<ModerationPreferencesRepository>(relaxed = true)
        every { repo.prefs } returns flow
        coEvery { repo.refresh() } returns Unit
        return repo to flow
    }

    private fun ContentFiltersState.row(label: ContentLabel): CategoryRowUi = categories.first { it.label == label }

    @Test
    fun `adult-off projects defaults with adult pickers disabled and nudity enabled`() =
        runTest(mainDispatcher.dispatcher) {
            val (repo, _) = fakeRepo()
            val vm = ContentFiltersViewModel(repo)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.adultContentEnabled)
            assertEquals(
                listOf(ContentLabel.PORN, ContentLabel.SEXUAL, ContentLabel.GRAPHIC_MEDIA, ContentLabel.NUDITY),
                state.categories.map { it.label },
            )
            assertFalse(state.row(ContentLabel.PORN).enabled)
            assertEquals(LabelVisibility.HIDE, state.row(ContentLabel.PORN).visibility)
            assertFalse(state.row(ContentLabel.SEXUAL).enabled)
            assertEquals(LabelVisibility.WARN, state.row(ContentLabel.SEXUAL).visibility)
            assertFalse(state.row(ContentLabel.GRAPHIC_MEDIA).enabled)
            // Non-sexual nudity is never gated.
            assertTrue(state.row(ContentLabel.NUDITY).enabled)
            assertEquals(LabelVisibility.SHOW, state.row(ContentLabel.NUDITY).visibility)
        }

    @Test
    fun `adult-on enables every category picker`() =
        runTest(mainDispatcher.dispatcher) {
            val (repo, _) = fakeRepo(adultOn)
            val vm = ContentFiltersViewModel(repo)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.adultContentEnabled)
            assertTrue(
                vm.uiState.value.categories
                    .all { it.enabled },
            )
        }

    @Test
    fun `a new repository emission re-projects the state`() =
        runTest(mainDispatcher.dispatcher) {
            val (repo, flow) = fakeRepo()
            val vm = ContentFiltersViewModel(repo)

            vm.uiState.test {
                assertFalse(awaitItem().adultContentEnabled)
                flow.value = adultOn
                val updated = awaitItem()
                assertTrue(updated.adultContentEnabled)
                assertTrue(updated.categories.all { it.enabled })
            }
        }

    @Test
    fun `toggling the master gate writes through to the repository`() =
        runTest(mainDispatcher.dispatcher) {
            val (repo, _) = fakeRepo()
            val vm = ContentFiltersViewModel(repo)

            vm.handleEvent(ContentFiltersEvent.AdultContentToggled(enabled = true))
            advanceUntilIdle()

            coVerify { repo.setAdultContentEnabled(true) }
        }

    @Test
    fun `selecting a visibility writes through to the repository`() =
        runTest(mainDispatcher.dispatcher) {
            val (repo, _) = fakeRepo(adultOn)
            val vm = ContentFiltersViewModel(repo)

            vm.handleEvent(ContentFiltersEvent.VisibilitySelected(ContentLabel.PORN, LabelVisibility.WARN))
            advanceUntilIdle()

            coVerify { repo.setVisibility(ContentLabel.PORN, LabelVisibility.WARN) }
        }

    @Test
    fun `rapid same-field toggles cancel the in-flight write (per-field single-flight)`() =
        runTest(mainDispatcher.dispatcher) {
            val (repo, _) = fakeRepo(adultOn)
            val firstGate = CompletableDeferred<Unit>()
            var firstCancelled = false
            coEvery { repo.setVisibility(ContentLabel.PORN, LabelVisibility.HIDE) } coAnswers {
                try {
                    firstGate.await()
                } catch (e: CancellationException) {
                    firstCancelled = true
                    throw e
                }
            }
            coEvery { repo.setVisibility(ContentLabel.PORN, LabelVisibility.SHOW) } returns Unit
            val vm = ContentFiltersViewModel(repo)

            vm.handleEvent(ContentFiltersEvent.VisibilitySelected(ContentLabel.PORN, LabelVisibility.HIDE))
            advanceUntilIdle() // let the HIDE write start and park on firstGate
            vm.handleEvent(ContentFiltersEvent.VisibilitySelected(ContentLabel.PORN, LabelVisibility.SHOW))
            advanceUntilIdle()

            assertTrue(firstCancelled, "a newer toggle for the SAME field must cancel the in-flight write")
        }

    @Test
    fun `a toggle on a different field does not cancel an in-flight write`() =
        runTest(mainDispatcher.dispatcher) {
            val (repo, _) = fakeRepo(adultOn)
            val pornGate = CompletableDeferred<Unit>()
            var pornFinished = false
            coEvery { repo.setVisibility(ContentLabel.PORN, LabelVisibility.HIDE) } coAnswers {
                pornGate.await()
                pornFinished = true
            }
            val vm = ContentFiltersViewModel(repo)

            vm.handleEvent(ContentFiltersEvent.VisibilitySelected(ContentLabel.PORN, LabelVisibility.HIDE))
            // A DIFFERENT field's toggle must not touch the porn write.
            vm.handleEvent(ContentFiltersEvent.AdultContentToggled(enabled = false))
            advanceUntilIdle()
            pornGate.complete(Unit)
            advanceUntilIdle()

            assertTrue(pornFinished, "a different field's toggle must not cancel the porn write")
        }

    @Test
    fun `a failed write surfaces a save-error effect`() =
        runTest(mainDispatcher.dispatcher) {
            val (repo, _) = fakeRepo(adultOn)
            coEvery { repo.setVisibility(any(), any()) } throws RuntimeException("network down")
            val vm = ContentFiltersViewModel(repo)

            vm.effects.test {
                vm.handleEvent(ContentFiltersEvent.VisibilitySelected(ContentLabel.PORN, LabelVisibility.HIDE))
                assertEquals(ContentFiltersEffect.ShowSaveError, awaitItem())
            }
        }
}
