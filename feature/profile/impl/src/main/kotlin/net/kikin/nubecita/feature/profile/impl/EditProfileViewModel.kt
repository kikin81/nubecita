package net.kikin.nubecita.feature.profile.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.common.text.GraphemeCounter
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.impl.data.EditableProfileLink
import net.kikin.nubecita.feature.profile.impl.data.ImageChange
import net.kikin.nubecita.feature.profile.impl.data.LinkFaviconFetcher
import net.kikin.nubecita.feature.profile.impl.data.LinksChange
import net.kikin.nubecita.feature.profile.impl.data.PendingIcon
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileUpdateError
import net.kikin.nubecita.feature.profile.impl.links.normalizeLinkUrl

/**
 * Presenter for the own-profile edit screen: display name, bio, avatar, and
 * banner. Pre-fills from the [EditProfile] route, tracks dirtiness + grapheme
 * limits + per-image edit state, drives the crop surface, and writes through
 * [ProfileRepository.updateProfile] — only changed images are uploaded
 * ([ImageChange.Replaced]); untouched images stay [ImageChange.Unchanged];
 * cleared images become [ImageChange.Removed].
 *
 * Links load asynchronously from the owner's repo ([LinksLoadStatus]). The
 * draft is diffed against the loaded baseline; Save sends
 * [LinksChange.Replaced] only when the links loaded AND changed — an unloaded
 * or failed list always goes out as [LinksChange.Unchanged], so it can never
 * be mistaken for "remove every link". Favicons are fetched when the link form
 * is confirmed and uploaded by the repository during Save.
 */
@HiltViewModel(assistedFactory = EditProfileViewModel.Factory::class)
internal class EditProfileViewModel
    @AssistedInject
    constructor(
        @Assisted route: EditProfile,
        private val repository: ProfileRepository,
        private val faviconFetcher: LinkFaviconFetcher,
    ) : MviViewModel<EditProfileViewState, EditProfileEvent, EditProfileEffect>(
            EditProfileViewState(
                displayName = route.displayName.orEmpty(),
                description = route.description.orEmpty(),
                displayNameGraphemes = GraphemeCounter.count(route.displayName.orEmpty()),
                descriptionGraphemes = GraphemeCounter.count(route.description.orEmpty()),
                avatar = ImageSlot.Original(route.avatarUrl),
                banner = ImageSlot.Original(route.bannerUrl),
            ),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: EditProfile): EditProfileViewModel
        }

        private val initialDisplayName = route.displayName.orEmpty()
        private val initialDescription = route.description.orEmpty()
        private val initialAvatar: ImageSlot = ImageSlot.Original(route.avatarUrl)
        private val initialBanner: ImageSlot = ImageSlot.Original(route.bannerUrl)

        /** The links as loaded — the dirty-check baseline and the save diff's "original". */
        private var initialLinks: ImmutableList<EditableProfileLink> = persistentListOf()
        private var linksLoadJob: Job? = null
        private var faviconJob: Job? = null
        private var newLinkCounter = 0

        init {
            loadLinks()
        }

        override fun handleEvent(event: EditProfileEvent) {
            when (event) {
                is EditProfileEvent.DisplayNameChanged ->
                    setState {
                        copy(
                            displayName = event.value,
                            displayNameGraphemes = GraphemeCounter.count(event.value),
                            isDirty = dirty(event.value, description, avatar, banner, links),
                        )
                    }

                is EditProfileEvent.DescriptionChanged ->
                    setState {
                        copy(
                            description = event.value,
                            descriptionGraphemes = GraphemeCounter.count(event.value),
                            isDirty = dirty(displayName, event.value, avatar, banner, links),
                        )
                    }

                is EditProfileEvent.ImagePicked ->
                    setState { copy(croppingTarget = CropRequest(uri = event.uri, slot = event.slot)) }

                is EditProfileEvent.ImageCropped ->
                    setState {
                        val cropped = ImageSlot.Cropped(bytes = event.bytes, mimeType = event.mimeType)
                        val nextAvatar = if (event.slot == ImageSlotKind.Avatar) cropped else avatar
                        val nextBanner = if (event.slot == ImageSlotKind.Banner) cropped else banner
                        copy(
                            avatar = nextAvatar,
                            banner = nextBanner,
                            croppingTarget = null,
                            isDirty = dirty(displayName, description, nextAvatar, nextBanner, links),
                        )
                    }

                EditProfileEvent.CropCancelled ->
                    setState { copy(croppingTarget = null) }

                is EditProfileEvent.RemoveImage ->
                    setState {
                        val nextAvatar = if (event.slot == ImageSlotKind.Avatar) ImageSlot.Removed else avatar
                        val nextBanner = if (event.slot == ImageSlotKind.Banner) ImageSlot.Removed else banner
                        copy(
                            avatar = nextAvatar,
                            banner = nextBanner,
                            isDirty = dirty(displayName, description, nextAvatar, nextBanner, links),
                        )
                    }

                EditProfileEvent.SaveTapped -> save()

                EditProfileEvent.BackPressed -> onBackPressed()

                EditProfileEvent.DiscardConfirmed -> {
                    setState { copy(showDiscardDialog = false) }
                    sendEffect(EditProfileEffect.NavigateBack)
                }

                EditProfileEvent.DiscardDismissed ->
                    setState { copy(showDiscardDialog = false) }

                EditProfileEvent.RetryLinksLoad -> loadLinks()

                EditProfileEvent.AddLinkTapped ->
                    if (uiState.value.canAddLink) setState { copy(linkEditor = LinkEditorState()) }

                is EditProfileEvent.EditLinkTapped -> openEditor(event.localId)

                is EditProfileEvent.RemoveLink ->
                    if (uiState.value.canEditLinks) {
                        updateLinks { links -> links.filterNot { it.localId == event.localId } }
                    }

                is EditProfileEvent.LinkUrlChanged ->
                    setState {
                        val editor = linkEditor ?: return@setState this
                        copy(linkEditor = editor.copy(urlInput = event.value, isDuplicate = isDuplicate(event.value, editor.editingId)))
                    }

                is EditProfileEvent.LinkTitleChanged ->
                    setState {
                        val editor = linkEditor ?: return@setState this
                        copy(linkEditor = editor.copy(titleInput = event.value, titleGraphemes = GraphemeCounter.count(event.value)))
                    }

                EditProfileEvent.LinkEditorConfirmed -> confirmEditor()

                EditProfileEvent.LinkEditorDismissed -> {
                    faviconJob?.cancel()
                    setState { copy(linkEditor = null) }
                }
            }
        }

        private fun loadLinks() {
            linksLoadJob?.cancel()
            setState { copy(linksStatus = LinksLoadStatus.Loading) }
            linksLoadJob =
                viewModelScope.launch {
                    repository
                        .fetchOwnLinks()
                        .onSuccess { loaded ->
                            initialLinks = loaded.toImmutableList()
                            setState { copy(links = initialLinks, linksStatus = LinksLoadStatus.Loaded) }
                        }.onFailure {
                            setState { copy(linksStatus = LinksLoadStatus.Failed) }
                        }
                }
        }

        private fun openEditor(localId: String) {
            val state = uiState.value
            if (!state.canEditLinks) return
            val link = state.links.firstOrNull { it.localId == localId } ?: return
            val title = link.title.orEmpty()
            setState {
                copy(
                    linkEditor =
                        LinkEditorState(
                            editingId = localId,
                            urlInput = link.url,
                            titleInput = title,
                            titleGraphemes = GraphemeCounter.count(title),
                        ),
                )
            }
        }

        private fun confirmEditor() {
            val editor = uiState.value.linkEditor ?: return
            if (!editor.canConfirm) return
            val url = editor.normalizedUrl ?: return
            val existing = editor.editingId?.let { id -> uiState.value.links.firstOrNull { it.localId == id } }
            val title = editor.titleInput.trim().takeIf { it.isNotEmpty() }
            if (existing != null && existing.url == url) {
                // Title-only edit: keep the stored icon, no favicon fetch.
                applyLink(existing.copy(title = title))
                return
            }
            setState { copy(linkEditor = editor.copy(isFetchingIcon = true)) }
            faviconJob =
                viewModelScope.launch {
                    val icon = faviconFetcher.fetch(url)?.let(::PendingIcon)
                    val link =
                        existing?.copy(url = url, title = title, iconPreviewUrl = null, pendingIcon = icon)
                            ?: EditableProfileLink(localId = "new-${++newLinkCounter}", url = url, title = title, pendingIcon = icon)
                    applyLink(link)
                }
        }

        /** Replaces the link with the same localId, or appends a new one; closes the form. */
        private fun applyLink(link: EditableProfileLink) {
            updateLinks { links ->
                if (links.any { it.localId == link.localId }) {
                    links.map { if (it.localId == link.localId) link else it }
                } else {
                    links + link
                }
            }
            setState { copy(linkEditor = null) }
        }

        private fun updateLinks(transform: (List<EditableProfileLink>) -> List<EditableProfileLink>) {
            setState {
                val next = transform(links).toImmutableList()
                copy(links = next, isDirty = dirty(displayName, description, avatar, banner, next))
            }
        }

        /** True when [input] normalizes to a URL another draft link (not [editingId]) already has. */
        private fun isDuplicate(
            input: String,
            editingId: String?,
        ): Boolean {
            val url = normalizeLinkUrl(input) ?: return false
            return uiState.value.links.any { it.localId != editingId && it.url.equals(url, ignoreCase = true) }
        }

        private fun onBackPressed() {
            val state = uiState.value
            when {
                // Ignore back while a save is in flight so the write can finish
                // and emit its success/failure effect — popping here would
                // dispose the VM and cancel the viewModelScope write.
                state.isSaving -> Unit
                state.isDirty -> setState { copy(showDiscardDialog = true) }
                else -> sendEffect(EditProfileEffect.NavigateBack)
            }
        }

        private fun save() {
            val state = uiState.value
            if (!state.canSave) return
            setState { copy(isSaving = true) }
            viewModelScope.launch {
                repository
                    .updateProfile(
                        displayName = state.displayName,
                        description = state.description,
                        avatar = state.avatar.toImageChange(),
                        banner = state.banner.toImageChange(),
                        links = state.toLinksChange(),
                    ).onSuccess {
                        // Keep isSaving = true; the screen pops on NavigateBack.
                        sendEffect(EditProfileEffect.NavigateBack)
                    }.onFailure { throwable ->
                        setState { copy(isSaving = false) }
                        sendEffect(EditProfileEffect.ShowError(throwable.toSaveError()))
                    }
            }
        }

        private fun dirty(
            displayName: String,
            description: String,
            avatar: ImageSlot,
            banner: ImageSlot,
            links: List<EditableProfileLink>,
        ): Boolean =
            displayName != initialDisplayName ||
                description != initialDescription ||
                avatar != initialAvatar ||
                banner != initialBanner ||
                (uiState.value.linksStatus == LinksLoadStatus.Loaded && links != initialLinks)

        /** Only a loaded, changed list is ever written; anything else is [LinksChange.Unchanged]. */
        private fun EditProfileViewState.toLinksChange(): LinksChange =
            if (linksStatus == LinksLoadStatus.Loaded && links != initialLinks) {
                LinksChange.Replaced(links = links, original = initialLinks)
            } else {
                LinksChange.Unchanged
            }

        private fun ImageSlot.toImageChange(): ImageChange =
            when (this) {
                is ImageSlot.Original -> ImageChange.Unchanged
                is ImageSlot.Cropped -> ImageChange.Replaced(bytes = bytes, mimeType = mimeType)
                ImageSlot.Removed -> ImageChange.Removed
            }

        private fun Throwable.toSaveError(): SaveError =
            when (this) {
                ProfileUpdateError.SwapConflict -> SaveError.SwapConflict
                ProfileUpdateError.Unauthorized -> SaveError.Unauthorized
                else -> SaveError.Network
            }
    }
