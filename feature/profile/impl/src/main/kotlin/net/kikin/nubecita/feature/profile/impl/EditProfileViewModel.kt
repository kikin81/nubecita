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
 * Presenter for the own-profile edit screen (text-first slice: display name +
 * bio). Pre-fills from the [EditProfile] route's values, tracks dirtiness +
 * grapheme limits, and writes through [ProfileRepository.updateProfile] with
 * both images left [ImageChange.Unchanged] (avatar/banner editing lands in
 * `nubecita-qr1q.6`).
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
            ),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: EditProfile): EditProfileViewModel
        }

        private val initialDisplayName = route.displayName.orEmpty()
        private val initialDescription = route.description.orEmpty()

        override fun handleEvent(event: EditProfileEvent) {
            when (event) {
                is EditProfileEvent.DisplayNameChanged ->
                    setState {
                        copy(
                            displayName = event.value,
                            displayNameGraphemes = GraphemeCounter.count(event.value),
                            isDirty = isDirtyFor(event.value, description),
                        )
                    }

                is EditProfileEvent.DescriptionChanged ->
                    setState {
                        copy(
                            description = event.value,
                            descriptionGraphemes = GraphemeCounter.count(event.value),
                            isDirty = isDirtyFor(displayName, event.value),
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
            if (state.isDirty && !state.isSaving) {
                setState { copy(showDiscardDialog = true) }
            } else {
                sendEffect(EditProfileEffect.NavigateBack)
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
                        avatar = ImageChange.Unchanged,
                        banner = ImageChange.Unchanged,
                    ).onSuccess {
                        // Keep isSaving = true; the screen pops on NavigateBack.
                        sendEffect(EditProfileEffect.NavigateBack)
                    }.onFailure { throwable ->
                        setState { copy(isSaving = false) }
                        sendEffect(EditProfileEffect.ShowError(throwable.toSaveError()))
                    }
            }
        }

        private fun isDirtyFor(
            displayName: String,
            description: String,
        ): Boolean = displayName != initialDisplayName || description != initialDescription

        private fun Throwable.toSaveError(): SaveError =
            when (this) {
                ProfileUpdateError.SwapConflict -> SaveError.SwapConflict
                ProfileUpdateError.Unauthorized -> SaveError.Unauthorized
                else -> SaveError.Network
            }
    }
