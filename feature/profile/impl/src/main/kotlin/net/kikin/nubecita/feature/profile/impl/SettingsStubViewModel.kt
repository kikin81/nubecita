package net.kikin.nubecita.feature.profile.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.common.mvi.MviViewModel
import javax.inject.Inject

/**
 * Presenter for the Settings stub sub-route. Owns the sign-out flow
 * via [AuthRepository.signOut]: tap → confirmation dialog → confirm →
 * loading state → success (screen unmounts when SessionStateProvider
 * transitions and MainActivity replaces to Login) OR failure (error
 * snackbar).
 *
 * Per `openspec/.../design.md` Decision 6 + Bead F design, the
 * Settings sub-route ships as a one-screen stub with Sign Out only.
 * When the real Settings screen graduates (follow-up bd 7.6), this
 * class is renamed and re-shaped; the contract surface is small
 * enough that the rename is mechanical.
 */
@HiltViewModel
internal class SettingsStubViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : MviViewModel<SettingsStubViewState, SettingsStubEvent, SettingsStubEffect>(
            SettingsStubViewState(),
        ) {
        override fun handleEvent(event: SettingsStubEvent) {
            when (event) {
                SettingsStubEvent.SignOutTapped ->
                    setState { copy(confirmDialogOpen = true) }
                SettingsStubEvent.DismissDialog ->
                    setState { copy(confirmDialogOpen = false) }
                SettingsStubEvent.ConfirmSignOut ->
                    runSignOut()
            }
        }

        private fun runSignOut() {
            // Single-flight: ignore a second Confirm tap while the first
            // request is still in flight.
            if (uiState.value.status is SettingsStubStatus.SigningOut) return
            setState { copy(status = SettingsStubStatus.SigningOut) }
            viewModelScope.launch {
                authRepository
                    .signOut()
                    .onFailure {
                        setState {
                            copy(
                                confirmDialogOpen = false,
                                status = SettingsStubStatus.Idle,
                            )
                        }
                        sendEffect(SettingsStubEffect.ShowSignOutError)
                    }
                // No onSuccess: SessionStateProvider transitions →
                // MainActivity's reactive collector replaces to Login →
                // this VM is scrapped. setState after success would
                // race the unmount.
            }
        }
    }
