package net.kikin.nubecita.feature.login.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.common.mvi.MviViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        broker: OAuthRedirectBroker,
    ) : MviViewModel<LoginState, LoginEvent, LoginEffect>(LoginState()) {
        init {
            // Long-running collection of redirect URIs published by MainActivity.
            // Cancels automatically when the VM is cleared. Each emission triggers
            // completeLogin; success → LoginSucceeded effect, failure → state errorMessage.
            viewModelScope.launch {
                broker.redirects.collect { redirectUri ->
                    authRepository
                        .completeLogin(redirectUri)
                        .onSuccess {
                            // Defensive: today the submit-path has already cleared isLoading
                            // by the time the redirect arrives, but if a future flow keeps
                            // isLoading true during the Custom Tab roundtrip, the success
                            // handler should still leave a clean slate before navigation.
                            setState { copy(isLoading = false, errorMessage = null) }
                            sendEffect(LoginEffect.LoginSucceeded)
                        }.onFailure { failure ->
                            setState { copy(isLoading = false, errorMessage = LoginError.Failure(failure.message)) }
                        }
                }
            }
        }

        override fun handleEvent(event: LoginEvent) {
            when (event) {
                is LoginEvent.HandleChanged -> setState { copy(handle = event.handle, errorMessage = null) }
                LoginEvent.ClearError -> setState { copy(errorMessage = null) }
                LoginEvent.SubmitLogin -> submitLogin()
            }
        }

        private fun submitLogin() {
            val handle = uiState.value.handle.trim()
            if (handle.isBlank()) {
                setState { copy(errorMessage = LoginError.BlankHandle) }
                return
            }
            setState { copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                authRepository
                    .beginLogin(handle)
                    .onSuccess { url ->
                        setState { copy(isLoading = false) }
                        sendEffect(LoginEffect.LaunchCustomTab(url))
                    }.onFailure { failure ->
                        // The screen resolves null / blank cause to the generic-failure resource
                        // string via LoginError.Failure → displayStringFor mapping.
                        setState { copy(isLoading = false, errorMessage = LoginError.Failure(failure.message)) }
                    }
            }
        }
    }
