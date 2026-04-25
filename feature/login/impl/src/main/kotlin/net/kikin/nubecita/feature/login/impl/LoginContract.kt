package net.kikin.nubecita.feature.login.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

data class LoginState(
    val handle: String = "",
    val isLoading: Boolean = false,
    val errorMessage: LoginError? = null,
) : UiState

/**
 * UI-resolvable error codes emitted by [LoginViewModel]. The VM stays
 * Android-resource-free; the screen maps each variant to a stringResource
 * call when rendering.
 */
sealed interface LoginError {
    /** User submitted with a blank or whitespace-only handle. */
    data object BlankHandle : LoginError

    /**
     * Underlying [AuthRepository.beginLogin] returned a failure. The
     * [cause] message (if non-blank) comes from the network layer / OAuth
     * server and is shown verbatim; the screen falls back to a generic
     * resource string when [cause] is null or blank.
     */
    data class Failure(
        val cause: String?,
    ) : LoginError
}

sealed interface LoginEvent : UiEvent {
    data class HandleChanged(
        val handle: String,
    ) : LoginEvent

    data object SubmitLogin : LoginEvent

    data object ClearError : LoginEvent
}

sealed interface LoginEffect : UiEffect {
    data class LaunchCustomTab(
        val url: String,
    ) : LoginEffect

    /**
     * The OAuth flow completed successfully and a session is now persisted
     * in the store. Carries no payload — the screen decides where to
     * navigate (today: pop the Login destination off the back stack;
     * `nubecita-30c` will add auth-gated routing decisions on top).
     */
    data object LoginSucceeded : LoginEffect
}
