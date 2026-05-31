package net.kikin.nubecita.feature.profile.impl

import android.net.Uri
import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.impl.data.ImageChange
import net.kikin.nubecita.feature.profile.impl.data.ProfileHeaderWithViewer
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileTabPage
import net.kikin.nubecita.feature.profile.impl.data.ProfileUpdateError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class EditProfileViewModelTest {
    @Test
    fun init_prefillsFromRoute_andIsNotDirty() {
        val vm = viewModel(EditProfile(displayName = "Alice", description = "hi there"))
        val state = vm.uiState.value
        assertEquals("Alice", state.displayName)
        assertEquals("hi there", state.description)
        assertEquals(5, state.displayNameGraphemes)
        assertEquals(8, state.descriptionGraphemes)
        assertFalse(state.isDirty)
        assertFalse(state.canSave)
    }

    @Test
    fun displayNameChanged_updatesGraphemesAndDirty() {
        val vm = viewModel(EditProfile(displayName = "Alice"))
        vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))
        val state = vm.uiState.value
        assertEquals("Alicia", state.displayName)
        assertEquals(6, state.displayNameGraphemes)
        assertTrue(state.isDirty)
        assertTrue(state.canSave)
    }

    @Test
    fun editingBackToOriginal_clearsDirty() {
        val vm = viewModel(EditProfile(displayName = "Alice"))
        vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))
        vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alice"))
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test
    fun displayNameOverLimit_blocksSave() {
        val vm = viewModel(EditProfile(displayName = "Alice"))
        vm.handleEvent(EditProfileEvent.DisplayNameChanged("a".repeat(65)))
        val state = vm.uiState.value
        assertTrue(state.isDisplayNameOverLimit)
        assertFalse(state.canSave)
    }

    @Test
    fun descriptionOverLimit_blocksSave() {
        val vm = viewModel(EditProfile(displayName = "Alice"))
        vm.handleEvent(EditProfileEvent.DescriptionChanged("a".repeat(257)))
        val state = vm.uiState.value
        assertTrue(state.isDescriptionOverLimit)
        assertFalse(state.canSave)
    }

    @Test
    fun imagePicked_opensCropForThatSlot() {
        val uri = mockk<Uri>()
        val vm = viewModel(EditProfile(displayName = "Alice"))
        vm.handleEvent(EditProfileEvent.ImagePicked(ImageSlotKind.Avatar, uri))
        assertEquals(CropRequest(uri = uri, slot = ImageSlotKind.Avatar), vm.uiState.value.croppingTarget)
    }

    @Test
    fun cropCancelled_clearsCroppingTarget() {
        val vm = viewModel(EditProfile(displayName = "Alice"))
        vm.handleEvent(EditProfileEvent.ImagePicked(ImageSlotKind.Banner, mockk()))
        vm.handleEvent(EditProfileEvent.CropCancelled)
        assertNull(vm.uiState.value.croppingTarget)
    }

    @Test
    fun imageCropped_setsCroppedSlot_clearsCrop_andMarksDirty() {
        val bytes = byteArrayOf(1, 2, 3)
        val vm = viewModel(EditProfile(displayName = "Alice"))
        vm.handleEvent(EditProfileEvent.ImagePicked(ImageSlotKind.Avatar, mockk()))
        vm.handleEvent(EditProfileEvent.ImageCropped(ImageSlotKind.Avatar, bytes, "image/webp"))
        val state = vm.uiState.value
        assertEquals(ImageSlot.Cropped(bytes, "image/webp"), state.avatar)
        assertNull(state.croppingTarget)
        assertTrue(state.isDirty)
        assertTrue(state.canSave)
    }

    @Test
    fun removeImage_clearsSlot_andMarksDirty() {
        val vm = viewModel(EditProfile(displayName = "Alice", avatarUrl = "https://a.test/x.jpg"))
        vm.handleEvent(EditProfileEvent.RemoveImage(ImageSlotKind.Avatar))
        assertEquals(ImageSlot.Removed, vm.uiState.value.avatar)
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun save_mapsImageSlotsToImageChanges() =
        runTest {
            val repo = FakeProfileRepository()
            val vm =
                viewModel(
                    EditProfile(displayName = "Alice", avatarUrl = "https://a.test/x.jpg", bannerUrl = "https://b.test/y.jpg"),
                    repo,
                )
            vm.handleEvent(EditProfileEvent.ImageCropped(ImageSlotKind.Avatar, byteArrayOf(9), "image/webp"))
            vm.handleEvent(EditProfileEvent.RemoveImage(ImageSlotKind.Banner))

            vm.effects.test {
                vm.handleEvent(EditProfileEvent.SaveTapped)
                assertEquals(EditProfileEffect.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(ImageChange.Replaced(byteArrayOf(9), "image/webp"), repo.lastAvatar)
            assertEquals(ImageChange.Removed, repo.lastBanner)
        }

    @Test
    fun save_success_callsUpdateProfile_andEmitsNavigateBack() =
        runTest {
            val repo = FakeProfileRepository()
            val vm = viewModel(EditProfile(displayName = "Alice", description = "old"), repo)
            vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))
            vm.handleEvent(EditProfileEvent.DescriptionChanged("new bio"))

            vm.effects.test {
                vm.handleEvent(EditProfileEvent.SaveTapped)
                assertEquals(EditProfileEffect.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, repo.updateCalls)
            assertEquals("Alicia", repo.lastDisplayName)
            assertEquals("new bio", repo.lastDescription)
            assertEquals(ImageChange.Unchanged, repo.lastAvatar)
            assertEquals(ImageChange.Unchanged, repo.lastBanner)
        }

    @Test
    fun save_swapConflict_emitsShowError_andResetsSaving() =
        runTest {
            val repo = FakeProfileRepository(Result.failure(ProfileUpdateError.SwapConflict))
            val vm = viewModel(EditProfile(displayName = "Alice"), repo)
            vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))

            vm.effects.test {
                vm.handleEvent(EditProfileEvent.SaveTapped)
                assertEquals(EditProfileEffect.ShowError(SaveError.SwapConflict), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.isSaving)
        }

    @Test
    fun saveTapped_whenNotDirty_doesNothing() =
        runTest {
            val repo = FakeProfileRepository()
            val vm = viewModel(EditProfile(displayName = "Alice"), repo)
            vm.handleEvent(EditProfileEvent.SaveTapped)
            assertEquals(0, repo.updateCalls)
        }

    @Test
    fun backPressed_whileSaving_isNoOp() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repo = FakeProfileRepository(gate = gate)
            val vm = viewModel(EditProfile(displayName = "Alice"), repo)
            vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))
            vm.handleEvent(EditProfileEvent.SaveTapped) // suspends in updateProfile → isSaving stays true
            assertTrue(vm.uiState.value.isSaving)

            vm.effects.test {
                vm.handleEvent(EditProfileEvent.BackPressed)
                expectNoEvents() // no NavigateBack while the save is in flight
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.showDiscardDialog) // and no discard dialog
            gate.complete(Unit) // let the save finish so the test scope can settle
        }

    @Test
    fun backPressed_whenDirty_showsDiscardDialog() {
        val vm = viewModel(EditProfile(displayName = "Alice"))
        vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))
        vm.handleEvent(EditProfileEvent.BackPressed)
        assertTrue(vm.uiState.value.showDiscardDialog)
    }

    @Test
    fun backPressed_whenClean_navigatesBack() =
        runTest {
            val vm = viewModel(EditProfile(displayName = "Alice"))
            vm.effects.test {
                vm.handleEvent(EditProfileEvent.BackPressed)
                assertEquals(EditProfileEffect.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.showDiscardDialog)
        }

    @Test
    fun discardConfirmed_navigatesBack() =
        runTest {
            val vm = viewModel(EditProfile(displayName = "Alice"))
            vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))
            vm.handleEvent(EditProfileEvent.BackPressed)
            vm.effects.test {
                vm.handleEvent(EditProfileEvent.DiscardConfirmed)
                assertEquals(EditProfileEffect.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.showDiscardDialog)
        }

    @Test
    fun discardDismissed_closesDialog_andStaysOnScreen() {
        val vm = viewModel(EditProfile(displayName = "Alice"))
        vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))
        vm.handleEvent(EditProfileEvent.BackPressed)
        vm.handleEvent(EditProfileEvent.DiscardDismissed)
        assertFalse(vm.uiState.value.showDiscardDialog)
    }

    private fun viewModel(
        route: EditProfile,
        repository: ProfileRepository = FakeProfileRepository(),
    ) = EditProfileViewModel(route = route, repository = repository)

    private class FakeProfileRepository(
        private val updateResult: Result<Unit> = Result.success(Unit),
        private val gate: CompletableDeferred<Unit>? = null,
    ) : ProfileRepository {
        override val ownProfileUpdates = MutableSharedFlow<Unit>()

        var updateCalls = 0
        var lastDisplayName: String? = null
        var lastDescription: String? = null
        var lastAvatar: ImageChange? = null
        var lastBanner: ImageChange? = null

        override suspend fun updateProfile(
            displayName: String?,
            description: String?,
            avatar: ImageChange,
            banner: ImageChange,
        ): Result<Unit> {
            updateCalls++
            lastDisplayName = displayName
            lastDescription = description
            lastAvatar = avatar
            lastBanner = banner
            // When a gate is supplied, suspend here so the test can observe the
            // in-flight (isSaving = true) state before the write resolves.
            gate?.await()
            return updateResult
        }

        override suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer> = error("unused")

        override suspend fun fetchTab(
            actor: String,
            tab: ProfileTab,
            cursor: String?,
            limit: Int,
        ): Result<ProfileTabPage> = error("unused")

        override suspend fun follow(subjectDid: String): Result<String> = error("unused")

        override suspend fun unfollow(followUri: String): Result<Unit> = error("unused")
    }
}
