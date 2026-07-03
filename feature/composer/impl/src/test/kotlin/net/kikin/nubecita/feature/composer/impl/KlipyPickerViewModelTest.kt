package net.kikin.nubecita.feature.composer.impl

import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.klipy.KlipyReportReason
import net.kikin.nubecita.core.klipy.KlipyRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.KlipyMediaPage
import net.kikin.nubecita.data.models.KlipyMediaType
import net.kikin.nubecita.data.models.KlipyMediaUiFixtures
import net.kikin.nubecita.feature.composer.impl.state.KlipyPickerEffect
import net.kikin.nubecita.feature.composer.impl.state.KlipyPickerEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class KlipyPickerViewModelTest {
    // UnconfinedTestDispatcher as Main so the VM's init (categories load) runs
    // eagerly and is observable without advanceUntilIdle ceremony — mirrors
    // ComposerViewModelTest.
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension(UnconfinedTestDispatcher())

    private val repository = mockk<KlipyRepository>(relaxUnitFun = true)
    private val media = KlipyMediaUiFixtures.media(slug = "cat")

    @BeforeEach
    fun setUp() {
        coEvery { repository.categories(any()) } returns Result.success(persistentListOf("Trending", "Love"))
        coEvery { repository.search(any(), any(), any()) } returns
            Result.success(KlipyMediaPage(persistentListOf(media), hasNext = false))
        coEvery { repository.recents(any(), any()) } returns
            Result.success(KlipyMediaPage(persistentListOf(), hasNext = false))
        coEvery { repository.report(any(), any(), any()) } returns Result.success(Unit)
    }

    @Test
    fun `init loads categories and selects Trending`() =
        runTest {
            val vm = KlipyPickerViewModel(repository)

            val state = vm.uiState.value
            assertEquals(persistentListOf("Trending", "Love"), state.categories)
            assertEquals("Trending", state.selectedCategory)
        }

    @Test
    fun `query change updates state and clears the selected category`() =
        runTest {
            val vm = KlipyPickerViewModel(repository)

            vm.handleEvent(KlipyPickerEvent.QueryChanged("dogs"))

            assertEquals("dogs", vm.uiState.value.query)
            assertNull(vm.uiState.value.selectedCategory)
        }

    @Test
    fun `tab selection switches tab and reloads that tab's categories`() =
        runTest {
            val vm = KlipyPickerViewModel(repository)

            vm.handleEvent(KlipyPickerEvent.TabSelected(KlipyMediaType.STICKER))

            assertEquals(KlipyMediaType.STICKER, vm.uiState.value.tab)
            coVerify { repository.categories(KlipyMediaType.STICKER) }
        }

    @Test
    fun `previewing an item fires a view and shows the preview`() =
        runTest {
            val vm = KlipyPickerViewModel(repository)

            vm.handleEvent(KlipyPickerEvent.ItemPreviewed(media))

            verify { repository.trackView(KlipyMediaType.GIF, "cat") }
            assertEquals(media, vm.uiState.value.preview)
        }

    @Test
    fun `selecting an item fires a share and emits MediaSelected`() =
        runTest {
            val vm = KlipyPickerViewModel(repository)

            vm.effects.test {
                vm.handleEvent(KlipyPickerEvent.ItemSelected(media))
                assertEquals(KlipyPickerEffect.MediaSelected(media), awaitItem())
            }
            verify { repository.trackShare(KlipyMediaType.GIF, "cat") }
        }

    @Test
    fun `reporting an item submits and emits a completed effect`() =
        runTest {
            val vm = KlipyPickerViewModel(repository)

            vm.effects.test {
                vm.handleEvent(KlipyPickerEvent.ItemReported(media, KlipyReportReason.SPAM))
                assertEquals(KlipyPickerEffect.ReportCompleted(succeeded = true), awaitItem())
            }
            coVerify { repository.report(KlipyMediaType.GIF, "cat", KlipyReportReason.SPAM) }
            assertNull(vm.uiState.value.preview)
        }

    @Test
    fun `media flow loads and maps the initial trending page`() =
        runTest {
            val vm = KlipyPickerViewModel(repository)

            val snapshot = vm.media.asSnapshot()

            assertTrue(snapshot.any { it.slug == "cat" })
        }
}
