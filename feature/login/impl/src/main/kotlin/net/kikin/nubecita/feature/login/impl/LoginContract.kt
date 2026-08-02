package net.kikin.nubecita.feature.login.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi

@Immutable
data class LoginState(
    val handle: String = "",
    val isLoading: Boolean = false,
    /**
     * Account suggestions for what has been typed so far. Empty hides the
     * dropdown — there is no separate "querying" state on purpose, so the list
     * does not flicker in and out on every keystroke while a request is in
     * flight (the same choice the composer's typeahead makes).
     */
    val suggestions: ImmutableList<HandleSuggestion> = persistentListOf(),
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

    /**
     * The OAuth authorization page / sign-up link could not be opened because
     * the device has no Activity able to handle the `VIEW` intent — no browser
     * or Custom Tabs provider is installed or enabled. Set when the screen's
     * effect collector catches `ActivityNotFoundException` from `launchUrl`, so
     * the app surfaces a recoverable message instead of crashing (nubecita-ywme).
     */
    @Immutable
    data object BrowserUnavailable : LoginError
}

sealed interface LoginEvent : UiEvent {
    data class HandleChanged(
        val handle: String,
    ) : LoginEvent

    data object SubmitLogin : LoginEvent

    /** A suggestion was tapped: adopt its handle and sign in with it. */
    data class SuggestionSelected(
        val handle: String,
    ) : LoginEvent

    data object ClearError : LoginEvent

    /**
     * User tapped the secondary "Create one on Bluesky" affordance. The VM
     * responds by emitting a `LaunchCustomTab` effect pointing at the
     * Bluesky web sign-up flow without mutating state.
     */
    data object OpenSignup : LoginEvent

    /**
     * The screen failed to launch the Custom Tab / browser for a
     * `LaunchCustomTab` effect (no Activity handles the `VIEW` intent —
     * `ActivityNotFoundException`). The VM responds by surfacing
     * [LoginError.BrowserUnavailable] so the failure is recoverable instead of
     * a crash. Reported from the Composable because the launch is an
     * Activity/Compose concern the VM never touches (nubecita-ywme).
     */
    data object CustomTabLaunchFailed : LoginEvent
}

sealed interface LoginEffect : UiEffect {
    data class LaunchCustomTab(
        val url: String,
    ) : LoginEffect

    /**
     * The OAuth flow completed successfully and a session is now persisted
     * in the store. Post-login navigation is owned by `MainActivity`'s
     * reactive `SessionStateProvider.SignedIn` observer — once
     * `completeLogin` succeeds and the state transitions to `SignedIn`,
     * MainActivity calls `navigator.replaceTo(Main)`. The screen only acts
     * on this effect to launch the POST_NOTIFICATIONS runtime prompt when
     * [requestPostNotificationsPermission] is true (Android 13+, first
     * sign-in on this install, gated by `NotificationsPromptDecider`).
     *
     * The prompt decision is packed into this effect rather than emitted
     * as a separate side-effect because `MviViewModel.sendEffect` launches
     * a fresh coroutine per call — back-to-back emissions only happen to
     * be ordered today because viewModelScope runs on
     * `Dispatchers.Main.immediate`. Folding the prompt into a single
     * atomic effect removes the dispatcher dependency entirely and keeps
     * the screen's collector branchless on the order of arrival.
     */
    data class LoginSucceeded(
        val requestPostNotificationsPermission: Boolean,
    ) : LoginEffect
}

/**
 * One row of the login typeahead.
 *
 * Wraps [ActorUi] rather than copying its fields: the row is rendered by the
 * shared `NubecitaActorRow`, which takes an actor, and duplicating did / handle
 * / displayName / avatarUrl here would be four fields to keep in step for no
 * gain. [pdsHost] is the only thing the AppView response does not carry.
 *
 * [pdsHost] is resolved separately and arrives after the row is already on
 * screen — resolving a DID document costs 300-1100ms, far too long to hold the
 * list back — so it is nullable and the row renders without it.
 */
data class HandleSuggestion(
    val actor: ActorUi,
    val pdsHost: String? = null,
) {
    val did: String get() = actor.did

    val handle: String get() = actor.handle
}
