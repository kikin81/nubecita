package net.kikin.nubecita.feature.login.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.common.mvi.MviViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : MviViewModel<LoginState, LoginEvent, LoginEffect>(LoginState()) {
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
                        setState { copy(isLoading = false, errorMessage = LoginError.Failure(failure.message)) }
                    }
            }
        }
    }
