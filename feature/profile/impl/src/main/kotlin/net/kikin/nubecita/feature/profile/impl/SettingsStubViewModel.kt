package net.kikin.nubecita.feature.profile.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Presenter for the Settings stub sub-route. Owns:
 *
 * - The sign-out flow via [AuthRepository.signOut]: tap → confirmation
 *   dialog → confirm → loading state → success (screen unmounts when
 *   SessionStateProvider transitions and MainActivity replaces to
 *   Login) OR failure (error snackbar).
 * - The identity-header state. On init, populates [SettingsStubViewState.handle]
 *   synchronously from [SessionStateProvider.state] and fires a
 *   [ProfileRepository.fetchHeader] coroutine to fill in displayName +
 *   avatarUrl. Fetch failures are silent — the header still renders
 *   (greeting → "Hi!"; avatar → initials disc derived from the handle).
 * - The Manage-Account and Switch-account tap effects (LaunchUri and
 *   ShowSwitchAccountComingSoon respectively).
 *
 * Per CLAUDE.md MVI conventions: state is flat (no Async wrapper);
 * inline `viewModelScope.launch { try ... } catch` rather than a base-
 * class helper.
 */
@HiltViewModel
internal class SettingsStubViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        sessionStateProvider: SessionStateProvider,
        private val profileRepository: ProfileRepository,
    ) : MviViewModel<SettingsStubViewState, SettingsStubEvent, SettingsStubEffect>(
            SettingsStubViewState(),
        ) {
        init {
            // Observe the first SignedIn emission instead of snapshotting
            // .state.value at init time. The earlier snapshot was correct
            // for the typical Login → MainShell handoff (state is already
            // SignedIn by then) but silently left the header empty if the
            // VM was constructed during any window where state is still
            // Loading / SignedOut — process-death restore where MainShell
            // restores Settings on the back stack before the session has
            // rehydrated, future deep-link paths, etc. take(1) makes this
            // a one-shot read once SignedIn arrives; sign-out still
            // unmounts the screen via the outer Navigator before any
            // SignedIn → SignedOut transition matters, so we don't need
            // to keep observing.
            sessionStateProvider.state
                .filterIsInstance<SessionState.SignedIn>()
                .take(1)
                .onEach { signedIn ->
                    setState { copy(handle = signedIn.handle) }
                    fetchHeader(signedIn.did)
                }.launchIn(viewModelScope)
        }

        override fun handleEvent(event: SettingsStubEvent) {
            when (event) {
                SettingsStubEvent.SignOutTapped ->
                    setState { copy(confirmDialogOpen = true) }
                SettingsStubEvent.DismissDialog ->
                    setState { copy(confirmDialogOpen = false) }
                SettingsStubEvent.ConfirmSignOut ->
                    runSignOut()
                SettingsStubEvent.ManageAccountTapped ->
                    sendEffect(SettingsStubEffect.LaunchUri(uri = MANAGE_ACCOUNT_URL))
                SettingsStubEvent.SwitchAccountTapped ->
                    sendEffect(SettingsStubEffect.ShowSwitchAccountComingSoon)
            }
        }

        private fun fetchHeader(actor: String) {
            viewModelScope.launch {
                profileRepository
                    .fetchHeader(actor)
                    .onSuccess { result ->
                        setState {
                            copy(
                                displayName = result.header.displayName,
                                avatarUrl = result.header.avatarUrl,
                            )
                        }
                    }.onFailure { error ->
                        // Header still renders without these fields —
                        // greeting falls back to "Hi!", avatar to the
                        // initials disc. No user-facing error needed.
                        Timber.tag(TAG).w(error, "Settings header profile fetch failed")
                    }
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

        private companion object {
            const val TAG = "SettingsStubVM"
            const val MANAGE_ACCOUNT_URL = "https://bsky.app/settings"
        }
    }
