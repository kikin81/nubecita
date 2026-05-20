package net.kikin.nubecita.feature.login.impl

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

@Immutable
data class LoginState(
    val handle: String = "",
    val isLoading: Boolean = false,
    val errorMessage: LoginError? = null,
) : UiState

/**
 * UI-resolvable error codes emitted by [LoginViewModel]. The VM stays
 * Android-resource-free; the screen maps each variant to a stringResource
 * call when rendering.
 *
 * Variants are explicitly `@Immutable`-annotated. Compose's stability
 * inference is conservative on sealed-interface implementations declared
 * across module boundaries; the explicit annotation tells the runtime it
 * can treat instances as stability-equivalent across recompositions
 * without rerunning equality checks on each parameter pass.
 */
sealed interface LoginError {
    /** User submitted with a blank or whitespace-only handle. */
    @Immutable
    data object BlankHandle : LoginError

    /**
     * The submitted handle did not resolve to a DID via DNS-over-HTTPS or
     * the HTTP `/.well-known/atproto-did` fallback. The screen interpolates
     * [handle] into the user-facing message so the user can verify spelling
     * without retyping.
     */
    @Immutable
    data class HandleNotFound(
        val handle: String,
    ) : LoginError

    /**
     * A network failure occurred during the login flow — the device is
     * offline, DNS lookup failed, or a socket-level timeout fired. The
     * underlying throwable is never exposed to the UI.
     */
    @Immutable
    data object Network : LoginError

    /**
     * Any unclassified failure (server config error, malformed metadata,
     * unexpected exception). The screen renders a static "try again"
     * resource; the throwable's `message` is never forwarded.
     */
    @Immutable
    data object Generic : LoginError
}

sealed interface LoginEvent : UiEvent {
    data class HandleChanged(
        val handle: String,
    ) : LoginEvent

    data object SubmitLogin : LoginEvent

    data object ClearError : LoginEvent

    /**
     * User tapped the secondary "Create one on Bluesky" affordance. The VM
     * responds by emitting a `LaunchCustomTab` effect pointing at the
     * Bluesky web sign-up flow without mutating state.
     */
    data object OpenSignup : LoginEvent
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
