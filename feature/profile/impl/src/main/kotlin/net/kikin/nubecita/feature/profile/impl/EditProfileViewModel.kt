package net.kikin.nubecita.feature.profile.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.common.text.GraphemeCounter
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.impl.data.ImageChange
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileUpdateError

/**
 * Presenter for the own-profile edit screen: display name, bio, avatar, and
 * banner. Pre-fills from the [EditProfile] route, tracks dirtiness + grapheme
 * limits + per-image edit state, drives the crop surface, and writes through
 * [ProfileRepository.updateProfile] — only changed images are uploaded
 * ([ImageChange.Replaced]); untouched images stay [ImageChange.Unchanged];
 * cleared images become [ImageChange.Removed].
 */
@HiltViewModel(assistedFactory = EditProfileViewModel.Factory::class)
internal class EditProfileViewModel
    @AssistedInject
    constructor(
        @Assisted route: EditProfile,
        private val repository: ProfileRepository,
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

        override fun handleEvent(event: EditProfileEvent) {
            when (event) {
                is EditProfileEvent.DisplayNameChanged ->
                    setState {
                        copy(
                            displayName = event.value,
                            displayNameGraphemes = GraphemeCounter.count(event.value),
                            isDirty = dirty(event.value, description, avatar, banner),
                        )
                    }

                is EditProfileEvent.DescriptionChanged ->
                    setState {
                        copy(
                            description = event.value,
                            descriptionGraphemes = GraphemeCounter.count(event.value),
                            isDirty = dirty(displayName, event.value, avatar, banner),
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
                            isDirty = dirty(displayName, description, nextAvatar, nextBanner),
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
                            isDirty = dirty(displayName, description, nextAvatar, nextBanner),
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
            }
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
        ): Boolean =
            displayName != initialDisplayName ||
                description != initialDescription ||
                avatar != initialAvatar ||
                banner != initialBanner

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
