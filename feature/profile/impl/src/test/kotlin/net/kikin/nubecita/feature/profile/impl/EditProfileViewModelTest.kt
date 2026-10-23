package net.kikin.nubecita.feature.profile.impl

import android.net.Uri
import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.VerifierUi
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.impl.data.EditableProfileLink
import net.kikin.nubecita.feature.profile.impl.data.ImageChange
import net.kikin.nubecita.feature.profile.impl.data.LinkFaviconFetcher
import net.kikin.nubecita.feature.profile.impl.data.LinksChange
import net.kikin.nubecita.feature.profile.impl.data.PendingIcon
import net.kikin.nubecita.feature.profile.impl.data.ProfileHeaderWithViewer
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileTabPage
import net.kikin.nubecita.feature.profile.impl.data.ProfileUpdateError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {
    // Unconfined so the VM's init-time links load (and other viewModelScope
    // launches) run eagerly — the default StandardTestDispatcher would leave
    // them queued with no scheduler driving them in non-runTest tests.
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension(UnconfinedTestDispatcher())

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

    // --- links ---

    private val savedA =
        EditableProfileLink(localId = "at://me/app.bsky.actor.link/a", url = "https://a.example", title = "A", uri = "at://me/app.bsky.actor.link/a", cid = "cidA")
    private val savedB =
        EditableProfileLink(localId = "at://me/app.bsky.actor.link/b", url = "https://b.example", title = null, uri = "at://me/app.bsky.actor.link/b", cid = "cidB")

    @Test
    fun links_load_on_init_and_are_not_dirty() {
        val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA, savedB)))
        val vm = viewModel(EditProfile(displayName = "Alice"), repository = repo)
        val state = vm.uiState.value
        assertEquals(LinksLoadStatus.Loaded, state.linksStatus)
        assertEquals(listOf(savedA, savedB), state.links)
        assertFalse(state.isDirty)
    }

    @Test
    fun failed_load_then_name_edit_saves_links_as_unchanged() =
        runTest {
            val repo = FakeProfileRepository(linksResult = Result.failure(RuntimeException("offline")))
            val vm = viewModel(EditProfile(displayName = "Alice"), repository = repo)
            assertEquals(LinksLoadStatus.Failed, vm.uiState.value.linksStatus)
            assertFalse(vm.uiState.value.canAddLink)

            vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))
            vm.handleEvent(EditProfileEvent.SaveTapped)

            assertEquals(1, repo.updateCalls)
            assertEquals(LinksChange.Unchanged, repo.lastLinks)
        }

    @Test
    fun retry_reloads_links() {
        val repo = FakeProfileRepository(linksResult = Result.failure(RuntimeException("offline")))
        val vm = viewModel(EditProfile(), repository = repo)
        repo.linksResult = Result.success(listOf(savedA))
        vm.handleEvent(EditProfileEvent.RetryLinksLoad)
        assertEquals(2, repo.fetchLinksCalls)
        assertEquals(LinksLoadStatus.Loaded, vm.uiState.value.linksStatus)
        assertEquals(listOf(savedA), vm.uiState.value.links)
    }

    @Test
    fun adding_a_link_normalizes_fetches_favicon_and_marks_dirty() {
        val fetcher = FakeFaviconFetcher(bytes = byteArrayOf(1, 2, 3))
        val vm = viewModel(EditProfile(), faviconFetcher = fetcher)
        vm.handleEvent(EditProfileEvent.AddLinkTapped)
        vm.handleEvent(EditProfileEvent.LinkUrlChanged("patreon.com/sarah"))
        vm.handleEvent(EditProfileEvent.LinkEditorConfirmed)

        val state = vm.uiState.value
        assertNull(state.linkEditor)
        val added = state.links.single()
        assertEquals("https://patreon.com/sarah", added.url)
        assertNull(added.uri)
        assertEquals(PendingIcon(byteArrayOf(1, 2, 3)), added.pendingIcon)
        assertEquals(listOf("https://patreon.com/sarah"), fetcher.fetched)
        assertTrue(state.isDirty)
    }

    @Test
    fun favicon_failure_still_adds_the_link() {
        val vm = viewModel(EditProfile(), faviconFetcher = FakeFaviconFetcher(bytes = null))
        vm.handleEvent(EditProfileEvent.AddLinkTapped)
        vm.handleEvent(EditProfileEvent.LinkUrlChanged("https://example.com"))
        vm.handleEvent(EditProfileEvent.LinkEditorConfirmed)
        assertNull(
            vm.uiState.value.links
                .single()
                .pendingIcon,
        )
    }

    @Test
    fun title_only_edit_keeps_icon_and_skips_the_fetch() {
        val fetcher = FakeFaviconFetcher(bytes = byteArrayOf(9))
        val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA)))
        val vm = viewModel(EditProfile(), repository = repo, faviconFetcher = fetcher)
        vm.handleEvent(EditProfileEvent.EditLinkTapped(savedA.localId))
        vm.handleEvent(EditProfileEvent.LinkTitleChanged("Renamed"))
        vm.handleEvent(EditProfileEvent.LinkEditorConfirmed)

        val edited =
            vm.uiState.value.links
                .single()
        assertEquals("Renamed", edited.title)
        assertEquals(savedA.uri, edited.uri)
        assertNull(edited.pendingIcon)
        assertTrue(fetcher.fetched.isEmpty())
    }

    @Test
    fun url_edit_refetches_and_clears_the_old_preview() {
        val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA.copy(iconPreviewUrl = "https://cdn/old"))))
        val vm = viewModel(EditProfile(), repository = repo, faviconFetcher = FakeFaviconFetcher(bytes = byteArrayOf(7)))
        vm.handleEvent(EditProfileEvent.EditLinkTapped(savedA.localId))
        vm.handleEvent(EditProfileEvent.LinkUrlChanged("https://new.example"))
        vm.handleEvent(EditProfileEvent.LinkEditorConfirmed)

        val edited =
            vm.uiState.value.links
                .single()
        assertEquals("https://new.example", edited.url)
        assertEquals(savedA.uri, edited.uri)
        assertNull(edited.iconPreviewUrl)
        assertEquals(PendingIcon(byteArrayOf(7)), edited.pendingIcon)
    }

    @Test
    fun invalid_and_duplicate_urls_block_confirm() {
        val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA)))
        val vm = viewModel(EditProfile(), repository = repo)
        vm.handleEvent(EditProfileEvent.AddLinkTapped)

        vm.handleEvent(EditProfileEvent.LinkUrlChanged("not a url"))
        assertTrue(vm.editor().isUrlInvalid)
        assertFalse(vm.editor().canConfirm)

        vm.handleEvent(EditProfileEvent.LinkUrlChanged("a.example"))
        assertTrue(vm.editor().isDuplicate)
        assertFalse(vm.editor().canConfirm)
    }

    @Test
    fun over_limit_title_blocks_confirm() {
        val vm = viewModel(EditProfile())
        vm.handleEvent(EditProfileEvent.AddLinkTapped)
        vm.handleEvent(EditProfileEvent.LinkUrlChanged("example.com"))
        vm.handleEvent(EditProfileEvent.LinkTitleChanged("x".repeat(41)))
        assertTrue(vm.editor().isTitleOverLimit)
        assertFalse(vm.editor().canConfirm)
    }

    @Test
    fun shortener_and_http_warn_without_blocking() {
        val vm = viewModel(EditProfile())
        vm.handleEvent(EditProfileEvent.AddLinkTapped)
        vm.handleEvent(EditProfileEvent.LinkUrlChanged("https://bit.ly/abc"))
        assertTrue(vm.editor().isShortener)
        assertTrue(vm.editor().canConfirm)
        vm.handleEvent(EditProfileEvent.LinkUrlChanged("http://example.com"))
        assertTrue(vm.editor().isInsecure)
        assertTrue(vm.editor().canConfirm)
    }

    @Test
    fun add_is_unavailable_at_ten_links() {
        val ten = (1..10).map { savedA.copy(localId = "id$it", url = "https://s$it.example", uri = "at://me/app.bsky.actor.link/$it") }
        val vm = viewModel(EditProfile(), repository = FakeProfileRepository(linksResult = Result.success(ten)))
        assertFalse(vm.uiState.value.canAddLink)
        vm.handleEvent(EditProfileEvent.AddLinkTapped)
        assertNull(vm.uiState.value.linkEditor)
    }

    @Test
    fun removing_then_saving_sends_replaced_with_the_original() =
        runTest {
            val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA, savedB)))
            val vm = viewModel(EditProfile(), repository = repo)
            vm.handleEvent(EditProfileEvent.RemoveLink(savedA.localId))
            assertTrue(vm.uiState.value.isDirty)
            vm.handleEvent(EditProfileEvent.SaveTapped)

            assertEquals(LinksChange.Replaced(links = listOf(savedB), original = listOf(savedA, savedB)), repo.lastLinks)
        }

    @Test
    fun untouched_links_save_as_unchanged() =
        runTest {
            val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA)))
            val vm = viewModel(EditProfile(displayName = "Alice"), repository = repo)
            vm.handleEvent(EditProfileEvent.DisplayNameChanged("Alicia"))
            vm.handleEvent(EditProfileEvent.SaveTapped)
            assertEquals(LinksChange.Unchanged, repo.lastLinks)
        }

    @Test
    fun dismissing_the_editor_discards_it() {
        val vm = viewModel(EditProfile())
        vm.handleEvent(EditProfileEvent.AddLinkTapped)
        vm.handleEvent(EditProfileEvent.LinkUrlChanged("example.com"))
        vm.handleEvent(EditProfileEvent.LinkEditorDismissed)
        assertNull(vm.uiState.value.linkEditor)
        assertTrue(
            vm.uiState.value.links
                .isEmpty(),
        )
        assertFalse(vm.uiState.value.isDirty)
    }

    // --- reorder ---

    @Test
    fun moving_a_link_reorders_the_draft_and_marks_dirty() {
        val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA, savedB)))
        val vm = viewModel(EditProfile(), repository = repo)
        vm.handleEvent(EditProfileEvent.MoveLink(from = 1, to = 0))
        assertEquals(listOf(savedB, savedA), vm.uiState.value.links)
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun moving_back_to_the_original_order_clears_dirty() {
        val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA, savedB)))
        val vm = viewModel(EditProfile(), repository = repo)
        vm.handleEvent(EditProfileEvent.MoveLink(from = 0, to = 1))
        vm.handleEvent(EditProfileEvent.MoveLink(from = 1, to = 0))
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test
    fun out_of_range_and_no_op_moves_are_ignored() {
        val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA, savedB)))
        val vm = viewModel(EditProfile(), repository = repo)
        vm.handleEvent(EditProfileEvent.MoveLink(from = 0, to = 5))
        vm.handleEvent(EditProfileEvent.MoveLink(from = -1, to = 0))
        vm.handleEvent(EditProfileEvent.MoveLink(from = 1, to = 1))
        assertEquals(listOf(savedA, savedB), vm.uiState.value.links)
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test
    fun moves_are_ignored_until_links_load() {
        val repo = FakeProfileRepository(linksResult = Result.failure(RuntimeException("offline")))
        val vm = viewModel(EditProfile(), repository = repo)
        vm.handleEvent(EditProfileEvent.MoveLink(from = 0, to = 1))
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test
    fun reorder_only_save_sends_the_new_order() =
        runTest {
            val repo = FakeProfileRepository(linksResult = Result.success(listOf(savedA, savedB)))
            val vm = viewModel(EditProfile(), repository = repo)
            vm.handleEvent(EditProfileEvent.MoveLink(from = 1, to = 0))
            vm.handleEvent(EditProfileEvent.SaveTapped)
            assertEquals(LinksChange.Replaced(links = listOf(savedB, savedA), original = listOf(savedA, savedB)), repo.lastLinks)
        }

    private fun EditProfileViewModel.editor(): LinkEditorState = checkNotNull(uiState.value.linkEditor)

    private fun viewModel(
        route: EditProfile,
        repository: ProfileRepository = FakeProfileRepository(),
        faviconFetcher: LinkFaviconFetcher = FakeFaviconFetcher(),
    ) = EditProfileViewModel(route = route, repository = repository, faviconFetcher = faviconFetcher)

    private class FakeFaviconFetcher(
        private val bytes: ByteArray? = null,
    ) : LinkFaviconFetcher {
        val fetched = mutableListOf<String>()

        override suspend fun fetch(url: String): ByteArray? {
            fetched += url
            return bytes
        }
    }

    private class FakeProfileRepository(
        private val updateResult: Result<Unit> = Result.success(Unit),
        private val gate: CompletableDeferred<Unit>? = null,
        var linksResult: Result<List<EditableProfileLink>> = Result.success(emptyList()),
    ) : ProfileRepository {
        var fetchLinksCalls = 0
        var lastLinks: LinksChange? = null

        override suspend fun fetchOwnLinks(): Result<List<EditableProfileLink>> {
            fetchLinksCalls++
            return linksResult
        }

        override val ownProfileUpdates = MutableSharedFlow<Unit>()

        override suspend fun resolveVerifiers(refs: ImmutableList<VerifierRef>): Result<ImmutableList<VerifierUi>> = Result.success(persistentListOf())

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
            links: LinksChange,
        ): Result<Unit> {
            updateCalls++
            lastLinks = links
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
