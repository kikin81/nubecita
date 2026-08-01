package net.kikin.nubecita.feature.login.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.oauth.OAuthDiscoveryException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.PublicActorSearch
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.Login
import net.kikin.nubecita.core.analytics.LoginErrorReason
import net.kikin.nubecita.core.analytics.LoginFailed
import net.kikin.nubecita.core.analytics.LoginRedirectLaunched
import net.kikin.nubecita.core.analytics.LoginRedirectReturned
import net.kikin.nubecita.core.analytics.LoginStage
import net.kikin.nubecita.core.analytics.OAuthRedirectKind
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.logging.CrashReporter
import net.kikin.nubecita.core.push.NotificationsPromptDecider
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

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
        private val publicActorSearch: PublicActorSearch,
        private val notificationsPromptDecider: NotificationsPromptDecider,
        private val analytics: AnalyticsClient,
        private val crashReporter: CrashReporter,
        broker: OAuthRedirectBroker,
    ) : MviViewModel<LoginState, LoginEvent, LoginEffect>(LoginState()) {
        // Declared ABOVE init: init calls startTypeaheadPipeline(), which
        // collects this flow. Kotlin initializes properties in declaration
        // order, so below init it would still be null and every construction
        // would NPE — and the compiler cannot see it through the call.
        // The login field routes keystrokes through handleEvent rather than a
        // TextFieldState, so the pipeline is fed here instead of from a
        // snapshotFlow.
        //
        // replay = 1 is load-bearing, not a buffer-size guess: the collector is
        // launched from init but does not actually subscribe until the coroutine
        // is dispatched, and a SharedFlow with no replay silently DROPS anything
        // emitted before then. Without it the very first keystroke can vanish.
        //
        // DROP_OLDEST: only the newest query matters, and emitting must never
        // suspend the UI event handler.
        //
        // Deliberately a separate flow rather than deriving from uiState.handle:
        // selecting a suggestion also writes the handle, and that write must NOT
        // kick off a fresh search for the row the user just picked.
        private val queryFlow =
            MutableSharedFlow<String>(
                replay = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        init {
            startTypeaheadPipeline()
            // Long-running collection of redirect URIs published by MainActivity.
            // Cancels automatically when the VM is cleared. Each emission triggers
            // completeLogin; success → single LoginSucceeded effect carrying the
            // POST_NOTIFICATIONS prompt decision; failure → state errorMessage.
            viewModelScope.launch {
                broker.redirects.collect { redirectUri ->
                    val redirectKind = redirectKindOf(redirectUri)
                    // Funnel: the browser handed a redirect back to the app. Pairs
                    // with LoginRedirectLaunched to expose the silent drop between
                    // launching the auth page and a callback actually returning.
                    analytics.log(LoginRedirectReturned(redirectKind))
                    // Show progress during the token exchange: once the Custom Tab
                    // closes the login form is interactive again, so a spinner here
                    // both signals work-in-flight and (with the submitLogin guard)
                    // blocks a double-submit while completeLogin runs.
                    setState { copy(isLoading = true, errorMessage = null) }
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
                            val kind = failure.classifyLoginFailure()
                            val error = kind.toLoginError(handle)
                            logLoginFailure(
                                stage = LoginStage.Complete,
                                kind = kind,
                                error = error,
                                cause = failure,
                                redirectKind = redirectKind,
                            )
                            setState { copy(isLoading = false, errorMessage = error) }
                        }
                }
            }
        }

        private fun startTypeaheadPipeline() {
            queryFlow
                .distinctUntilChanged()
                .mapLatest { raw ->
                    val query = raw.trim().removePrefix("@")
                    if (query.length < MIN_QUERY_LENGTH) {
                        persistentListOf()
                    } else {
                        // The delay sits INSIDE mapLatest, not in an upstream
                        // .debounce(): that way a newer keystroke cancels both the
                        // in-flight request AND a still-pending debounce, so a stale
                        // result cannot land after a newer one. Same reasoning as
                        // the composer's typeahead.
                        delay(TYPEAHEAD_DEBOUNCE)
                        publicActorSearch
                            .searchTypeahead(query, limit = SUGGESTION_LIMIT)
                            .getOrDefault(emptyList())
                            .map {
                                HandleSuggestion(
                                    did = it.did,
                                    handle = it.handle,
                                    displayName = it.displayName,
                                    avatarUrl = it.avatarUrl,
                                )
                            }.toPersistentList()
                    }
                }.onEach { suggestions ->
                    setState { copy(suggestions = suggestions) }
                    // Resolve hosts only after the rows are on screen. Each costs a
                    // DID-document fetch, so blocking the list on them would make
                    // the typeahead feel broken.
                    if (suggestions.isNotEmpty()) resolveHosts(suggestions)
                }.launchIn(viewModelScope)
        }

        /**
         * Fills in each suggestion's network line as it resolves, so the list
         * fills in progressively instead of appearing all at once. Runs in the
         * pipeline's coroutine, so a newer query cancels these too.
         */
        private suspend fun resolveHosts(suggestions: List<HandleSuggestion>) {
            coroutineScope {
                suggestions
                    .map { suggestion ->
                        async {
                            val host = publicActorSearch.resolvePdsHost(suggestion.did) ?: return@async
                            setState {
                                copy(
                                    suggestions =
                                        this.suggestions
                                            .map { if (it.did == suggestion.did) it.copy(pdsHost = host) else it }
                                            .toPersistentList(),
                                )
                            }
                        }
                    }.awaitAll()
            }
        }

        /**
         * Adopt a tapped suggestion and sign in with it. The handle still goes
         * through the same normalization a typed one does — the suggestion list is
         * a convenience, not a second code path into beginLogin.
         */
        private fun onSuggestionSelected(handle: String) {
            setState { copy(handle = handle, suggestions = persistentListOf(), errorMessage = null) }
            submitLogin()
        }

        override fun handleEvent(event: LoginEvent) {
            when (event) {
                is LoginEvent.HandleChanged -> {
                    setState { copy(handle = event.handle, errorMessage = null) }
                    queryFlow.tryEmit(event.handle)
                }
                is LoginEvent.SuggestionSelected -> onSuggestionSelected(event.handle)
                LoginEvent.ClearError -> setState { copy(errorMessage = null) }
                LoginEvent.SubmitLogin -> submitLogin()
                LoginEvent.OpenSignup -> sendEffect(LoginEffect.LaunchCustomTab(BLUESKY_SIGNUP_URL))
                // No browser handled the VIEW intent — surface a recoverable error
                // instead of letting ActivityNotFoundException crash the app.
                LoginEvent.CustomTabLaunchFailed ->
                    setState { copy(isLoading = false, errorMessage = LoginError.BrowserUnavailable) }
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
            // Ignore re-taps while a login is in flight (beginLogin, or the
            // post-redirect token exchange which also sets isLoading) — prevents
            // concurrent beginLogin calls and duplicate LoginRedirectLaunched events.
            if (uiState.value.isLoading) return
            // Normalize before resolving: '@alice', bare 'alice', and 'Alice.Bsky.Social'
            // all fail atproto handle resolution as typed and are the dominant
            // `handle_not_found` login failure in the field (nubecita-mbzp). Custom-domain
            // handles pass through untouched; a DID keeps its case-sensitive identifier
            // (only its scheme/method are lowercased) — see [normalizeLoginIdentifier].
            val handle = normalizeLoginIdentifier(uiState.value.handle)
            if (handle.isBlank()) {
                setState { copy(errorMessage = LoginError.BlankHandle) }
                return
            }
            // Reflect the normalized identifier back into the field so the user sees what
            // we resolve against, and any HandleNotFound message names the same value.
            setState { copy(handle = handle, isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                authRepository
                    .beginLogin(handle)
                    .onSuccess { url ->
                        setState { copy(isLoading = false) }
                        // Funnel: the OAuth authorization URL is going to the Custom
                        // Tab. Only the sign-in path emits this — OpenSignup opens a
                        // generic page and is not part of the auth funnel.
                        analytics.log(LoginRedirectLaunched)
                        sendEffect(LoginEffect.LaunchCustomTab(url))
                    }.onFailure { failure ->
                        val kind = failure.classifyLoginFailure()
                        val error = kind.toLoginError(handle)
                        logLoginFailure(stage = LoginStage.Begin, kind = kind, error = error, cause = failure)
                        setState { copy(isLoading = false, errorMessage = error) }
                    }
            }
        }

        /**
         * Record a login failure to analytics, Crashlytics, and logcat.
         *
         * Analytics: emit the PII-free `login_error` event (reason + stage) for
         * funnel failure-rate reporting. [LoginError.BlankHandle] is client-side
         * validation, not a real attempt, and never reaches this path — so every
         * failure here logs a concrete [LoginErrorReason].
         *
         * Crashlytics: the coarse [LoginError.Generic] bucket — both `oauth_config`
         * (upstream server misconfig) and `unexpected` (genuine unknown) — records
         * ONE grouped [LoginFailureException] non-fatal with diagnostic custom keys
         * (stage, reason, exception class, redirect transport) attached. The
         * non-fatal is recorded directly via [crashReporter] (not `Timber.e` → the
         * planted tree) so the keys land on the same report and we don't
         * double-record. Expected failures ([LoginError.Network] offline,
         * [LoginError.HandleNotFound] typo) are breadcrumb-only — no non-fatal.
         *
         * PII-free: only the exception class name + bucketed reason/stage/redirect
         * kind are attached, never the handle, token, or redirect URI.
         */
        private fun logLoginFailure(
            stage: LoginStage,
            kind: LoginFailureKind,
            error: LoginError,
            cause: Throwable,
            redirectKind: OAuthRedirectKind? = null,
        ) {
            val reason = kind.toAnalyticsReason()
            analytics.log(LoginFailed(reason = reason, stage = stage))
            if (error is LoginError.Generic) {
                // Keys are set immediately before the recordException they annotate
                // (Crashlytics keys are sticky) so they can't bleed onto an unrelated
                // later crash from the breadcrumb-only path.
                crashReporter.setCustomKey(KEY_STAGE, stage.wire)
                crashReporter.setCustomKey(KEY_REASON, reason.wire)
                crashReporter.setCustomKey(KEY_EXCEPTION, cause::class.simpleName ?: "Unknown")
                // Always overwrite — Crashlytics keys are sticky, so a stale
                // Complete-stage value must not bleed onto a later Begin-stage
                // report (where redirectKind is null).
                crashReporter.setCustomKey(KEY_REDIRECT_KIND, redirectKind?.wire ?: "none")
                crashReporter.recordException(LoginFailureException(reason, cause))
                Timber.tag(TAG).w(cause, "login failed (stage=%s, reason=%s)", stage.wire, reason.wire)
            } else {
                Timber.tag(TAG).w(cause, "login failed (stage=%s, error=%s)", stage.wire, error::class.simpleName)
            }
        }

        private fun redirectKindOf(redirectUri: String): OAuthRedirectKind =
            // Only two registered redirect shapes: the verified App Link
            // (https://nubecita.app/oauth-redirect) and the legacy custom scheme
            // (app.nubecita:/oauth-redirect). Scheme alone disambiguates them.
            if (redirectUri.startsWith("https://", ignoreCase = true)) {
                OAuthRedirectKind.AppLink
            } else {
                OAuthRedirectKind.CustomScheme
            }

        private companion object {
            // Logcat tag stays under the 23-char Android Log ceiling so it
            // shows up unmangled in `adb logcat -s LoginViewModel`.
            const val TAG = "LoginViewModel"

            // Crashlytics custom-key names (≤40 chars; PII-free dimensions).
            const val KEY_STAGE = "login_stage"
            const val KEY_REASON = "login_reason"
            const val KEY_EXCEPTION = "login_exception"
            const val KEY_REDIRECT_KIND = "oauth_redirect_kind"
        }
    }

/**
 * Wrapper recorded for every actionable login non-fatal so Crashlytics clusters
 * them under one issue (grouped by this constructor's frame in `logLoginFailure`)
 * rather than scattering by the underlying [cause] type. The original throwable
 * is preserved as the cause; the message carries only the bucketed reason wire
 * value (no PII).
 */
private class LoginFailureException(
    reason: LoginErrorReason,
    cause: Throwable,
) : Exception("login failed: ${reason.wire}", cause)

// Prefix-match against the upstream OAuthDiscoveryException message is brittle by
// design — when atproto-kotlin grows typed exceptions (HandleNotFound, NetworkReach,
// etc.), replace the checks here and drop the unit tests that pin the literal strings.
// Single failure taxonomy behind BOTH the coarse UI [LoginError] and the finer
// analytics [LoginErrorReason]. One classifier keeps the two mappings from
// drifting: the UI deliberately collapses OauthConfig + Unexpected into one
// neutral "Generic" message, while analytics keeps them apart for the funnel.
private enum class LoginFailureKind { HandleNotFound, Network, OauthConfig, Unexpected }

private fun Throwable.classifyLoginFailure(): LoginFailureKind {
    if (this is OAuthDiscoveryException) {
        if (isHandleNotFoundDiscoveryMessage()) return LoginFailureKind.HandleNotFound
        if (isNetworkError() || isReachabilityDiscoveryMessage()) return LoginFailureKind.Network
        return LoginFailureKind.OauthConfig
    }
    return if (isNetworkError()) LoginFailureKind.Network else LoginFailureKind.Unexpected
}

private fun LoginFailureKind.toLoginError(handle: String): LoginError =
    when (this) {
        LoginFailureKind.HandleNotFound -> LoginError.HandleNotFound(handle)
        LoginFailureKind.Network -> LoginError.Network
        // The UI shows one neutral message for both — the throwable detail never
        // reaches the user. Analytics tells them apart below.
        LoginFailureKind.OauthConfig, LoginFailureKind.Unexpected -> LoginError.Generic
    }

private fun LoginFailureKind.toAnalyticsReason(): LoginErrorReason =
    when (this) {
        LoginFailureKind.HandleNotFound -> LoginErrorReason.HandleNotFound
        LoginFailureKind.Network -> LoginErrorReason.Network
        LoginFailureKind.OauthConfig -> LoginErrorReason.OauthConfig
        LoginFailureKind.Unexpected -> LoginErrorReason.Unexpected
    }

private fun OAuthDiscoveryException.isHandleNotFoundDiscoveryMessage(): Boolean = message?.startsWith("Failed to resolve handle", ignoreCase = true) == true

/**
 * Recognizes [OAuthDiscoveryException] messages that indicate a reachability /
 * protocol-level failure (captive-portal hijacking, slow network forcing a
 * timeout that the underlying Ktor exception doesn't surface as an
 * `IOException` cause, upstream returning a non-2xx status, malformed JSON
 * from a middlebox returning HTML instead of metadata). These should surface
 * to the user as `LoginError.Network` ("Trouble connecting, try again") not
 * `LoginError.Generic` ("Could not start sign-in"), because:
 *
 *  - The user can fix it by switching networks / waiting / retrying
 *  - Telling them it's a config problem when it's actually their WiFi sends
 *    them down the wrong debugging path (and clutters Crashlytics / support
 *    with would-be-bug reports)
 *
 * Negative cases that stay [LoginError.Generic] (upstream config problems
 * that retrying won't fix):
 *  - `authorization_endpoint missing` / `token_endpoint missing` etc.
 *  - `Unsupported DID method: …`
 *  - `DID document … has no #atproto_pds service`
 *  - `Resource server metadata … has empty authorization_servers array`
 */
private fun OAuthDiscoveryException.isReachabilityDiscoveryMessage(): Boolean {
    val m = message ?: return false
    return m.startsWith("Failed to fetch", ignoreCase = true) ||
        m.startsWith("Failed to parse", ignoreCase = true) ||
        REACHABILITY_STATUS_REGEX.containsMatchIn(m)
}

// "DID document fetch for '…' returned 502 Bad Gateway" / "Auth server metadata
// at https://… returned 503 Service Unavailable" / etc. Pin to a 3-digit HTTP
// status after " returned " so unrelated messages with the word "returned"
// don't get reclassified. The \b after the 3 digits rejects partial matches on
// longer numbers (e.g. " returned 5020" must not match as "502"); IGNORE_CASE
// guards against upstream casing changes in the word "returned".
private val REACHABILITY_STATUS_REGEX = Regex(""" returned \b\d{3}\b""", RegexOption.IGNORE_CASE)

private fun Throwable.isNetworkError(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is IOException || t is UnknownHostException || t is SocketTimeoutException) return true
        t = t.cause
    }
    return false
}

private const val MIN_QUERY_LENGTH = 2
private const val SUGGESTION_LIMIT = 6
private val TYPEAHEAD_DEBOUNCE = 200.milliseconds
