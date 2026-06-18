package net.kikin.nubecita.feature.login.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.oauth.OAuthDiscoveryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.Login
import net.kikin.nubecita.core.analytics.LoginErrorReason
import net.kikin.nubecita.core.analytics.LoginFailed
import net.kikin.nubecita.core.analytics.LoginStage
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.push.NotificationsPromptDecider
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

// The unauthenticated bsky.app SPA shows "Create account" + "Sign in" buttons
// directly; `bsky.app/signup` is NOT a real path (it 404s as of 2026-05-20).
// When nubecita-lq9t.3.5 ships the OAuth `prompt=create` flow, this static URL
// goes away in favor of a PAR'd authorization URL the user comes back from
// already signed in.
internal const val BLUESKY_SIGNUP_URL = "https://bsky.app/"

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val notificationsPromptDecider: NotificationsPromptDecider,
        private val analytics: AnalyticsClient,
        broker: OAuthRedirectBroker,
    ) : MviViewModel<LoginState, LoginEvent, LoginEffect>(LoginState()) {
        init {
            // Long-running collection of redirect URIs published by MainActivity.
            // Cancels automatically when the VM is cleared. Each emission triggers
            // completeLogin; success → single LoginSucceeded effect carrying the
            // POST_NOTIFICATIONS prompt decision; failure → state errorMessage.
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
                            // GA4 login event — fires once per successful OAuth
                            // completion (default method = OAuth).
                            analytics.log(Login())
                            sendEffect(
                                LoginEffect.LoginSucceeded(
                                    requestPostNotificationsPermission = resolvePostNotificationsPrompt(),
                                ),
                            )
                        }.onFailure { failure ->
                            val handle = uiState.value.handle.trim()
                            val error = failure.toLoginError(handle)
                            logLoginFailure(stage = LoginStage.Complete, error = error, cause = failure)
                            setState { copy(isLoading = false, errorMessage = error) }
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

        /**
         * Best-effort decision on whether the screen should launch the
         * POST_NOTIFICATIONS system dialog. Returns `false` if either
         * [NotificationsPromptDecider.shouldPrompt] or
         * [NotificationsPromptDecider.markPrompted] throws (both
         * touch DataStore and can fail on transient IO or — pre-corruption-
         * handler — schema mismatches). A DataStore exception MUST NOT
         * cancel the login-success coroutine: the session is already
         * persisted by the time we reach this branch, and worst case the
         * user can re-enable notifications from system settings. We also
         * keep `markPrompted()` paired with `shouldPrompt() == true` so a
         * future read-side failure can't flip the gate without the screen
         * actually seeing the prompt request.
         */
        private suspend fun resolvePostNotificationsPrompt(): Boolean =
            try {
                val shouldPrompt = notificationsPromptDecider.shouldPrompt()
                if (shouldPrompt) {
                    // Mark the gate BEFORE the screen acts on the effect:
                    // even if the user declines the system dialog (or the
                    // LaunchedEffect collector is torn down mid-dispatch),
                    // we've recorded the attempt and won't loop-prompt on
                    // every subsequent login.
                    notificationsPromptDecider.markPrompted()
                }
                shouldPrompt
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                Timber.tag(TAG).w(failure, "POST_NOTIFICATIONS prompt gate threw; skipping prompt this login")
                false
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
                        val error = failure.toLoginError(handle)
                        logLoginFailure(stage = LoginStage.Begin, error = error, cause = failure)
                        setState { copy(isLoading = false, errorMessage = error) }
                    }
            }
        }

        /**
         * Record a login failure to analytics and logs.
         *
         * Analytics: emit the PII-free `login_error` event (reason + stage) for
         * funnel failure-rate reporting — skipped for [LoginError.BlankHandle],
         * which is client-side validation, not a real attempt (and never reaches
         * this path anyway).
         *
         * Logs: route by level so non-fatals stay high-signal. [LoginError.Network]
         * (offline) and [LoginError.HandleNotFound] (typo) are expected → `w`
         * (breadcrumb only). Only the unclassified [LoginError.Generic] is a
         * genuinely-unexpected failure worth a non-fatal → `e`. The error's class
         * name is logged, never the handle (PII).
         */
        private fun logLoginFailure(
            stage: LoginStage,
            error: LoginError,
            cause: Throwable,
        ) {
            error.toAnalyticsReason()?.let { reason ->
                analytics.log(LoginFailed(reason = reason, stage = stage))
            }
            if (error is LoginError.Generic) {
                Timber.tag(TAG).e(cause, "login failed (unexpected, stage=%s)", stage.wire)
            } else {
                // Pass cause for the local logcat stack trace; the CrashlyticsTree
                // breadcrumb uses only the message (no handle), and WARN never records
                // a non-fatal, so the throwable's message can't reach Crashlytics.
                Timber.tag(TAG).w(cause, "login failed (stage=%s, error=%s)", stage.wire, error::class.simpleName)
            }
        }

        private companion object {
            // Logcat tag stays under the 23-char Android Log ceiling so it
            // shows up unmangled in `adb logcat -s LoginViewModel`.
            const val TAG = "LoginViewModel"
        }
    }

// Prefix-match against the upstream OAuthDiscoveryException message is brittle by
// design — when atproto-kotlin grows a typed HandleNotFoundException, replace the
// check here and drop the unit test that pins the literal string.
// Map the UI error to the bucketed analytics reason. BlankHandle returns null
// (not reported — it's client-side validation and never reaches the failure path).
private fun LoginError.toAnalyticsReason(): LoginErrorReason? =
    when (this) {
        LoginError.Network -> LoginErrorReason.Network
        is LoginError.HandleNotFound -> LoginErrorReason.HandleNotFound
        LoginError.Generic -> LoginErrorReason.Unexpected
        LoginError.BlankHandle -> null
    }

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
