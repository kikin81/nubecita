package net.kikin.nubecita.feature.login.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.oauth.OAuthDiscoveryException
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.common.mvi.MviViewModel
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

internal const val BLUESKY_SIGNUP_URL = "https://bsky.app/signup"

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
                            val handle = uiState.value.handle.trim()
                            setState { copy(isLoading = false, errorMessage = failure.toLoginError(handle)) }
                        }
                }
            }
        }

        override fun handleEvent(event: LoginEvent) {
            when (event) {
                is LoginEvent.HandleChanged -> setState { copy(handle = event.handle, errorMessage = null) }
                LoginEvent.ClearError -> setState { copy(errorMessage = null) }
                LoginEvent.SubmitLogin -> submitLogin()
                LoginEvent.OpenSignup -> sendEffect(LoginEffect.LaunchCustomTab(BLUESKY_SIGNUP_URL))
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
                        setState { copy(isLoading = false, errorMessage = failure.toLoginError(handle)) }
                    }
            }
        }
    }

// Prefix-match against the upstream OAuthDiscoveryException message is brittle by
// design — when atproto-kotlin grows a typed HandleNotFoundException, replace the
// check here and drop the unit test that pins the literal string.
private fun Throwable.toLoginError(handle: String): LoginError =
    when {
        this is OAuthDiscoveryException &&
            message?.startsWith("Failed to resolve handle") == true -> LoginError.HandleNotFound(handle)
        isNetworkError() -> LoginError.Network
        else -> LoginError.Generic
    }

private fun Throwable.isNetworkError(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is IOException || t is UnknownHostException || t is SocketTimeoutException) return true
        t = t.cause
    }
    return false
}
