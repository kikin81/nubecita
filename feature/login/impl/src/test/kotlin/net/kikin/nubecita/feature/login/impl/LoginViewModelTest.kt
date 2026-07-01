package net.kikin.nubecita.feature.login.impl

import io.github.kikin81.atproto.oauth.OAuthDiscoveryException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.Login
import net.kikin.nubecita.core.analytics.LoginErrorReason
import net.kikin.nubecita.core.analytics.LoginFailed
import net.kikin.nubecita.core.analytics.LoginRedirectLaunched
import net.kikin.nubecita.core.analytics.LoginRedirectReturned
import net.kikin.nubecita.core.analytics.LoginStage
import net.kikin.nubecita.core.analytics.NoOpAnalyticsClient
import net.kikin.nubecita.core.analytics.OAuthRedirectKind
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.logging.CrashReporter
import net.kikin.nubecita.core.push.NotificationsPromptDecider
import net.kikin.nubecita.core.push.NotificationsPromptShownStore
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
internal class LoginViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `initial state is empty handle, not loading, no error`() {
        val vm = newViewModel()
        val state = vm.uiState.value
        assertEquals("", state.handle)
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `HandleChanged updates handle and clears errorMessage`() {
        val vm = newViewModel()

        vm.handleEvent(LoginEvent.SubmitLogin)
        assertEquals(LoginError.BlankHandle, vm.uiState.value.errorMessage)

        vm.handleEvent(LoginEvent.HandleChanged("alice"))
        assertEquals("alice", vm.uiState.value.handle)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `blank-handle SubmitLogin emits BlankHandle error without calling repository`() {
        val fake = FakeAuthRepository(beginLoginResult = Result.success("never returned"))
        val vm = newViewModel(authRepository = fake)

        vm.handleEvent(LoginEvent.SubmitLogin)

        val state = vm.uiState.value
        assertEquals(LoginError.BlankHandle, state.errorMessage)
        assertEquals(false, state.isLoading)
        assertEquals(0, fake.beginLoginInvocations)
    }

    @Test
    fun `whitespace-only handle is treated as blank`() {
        val vm = newViewModel()
        vm.handleEvent(LoginEvent.HandleChanged("   "))
        vm.handleEvent(LoginEvent.SubmitLogin)
        assertEquals(LoginError.BlankHandle, vm.uiState.value.errorMessage)
    }

    @Test
    fun `successful beginLogin emits LaunchCustomTab and clears loading`() =
        runTest(mainDispatcher.dispatcher) {
            val url = "https://bsky.social/oauth/authorize?req=uri:abc"
            val vm = newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.success(url)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            val effect = vm.effects.first()
            assertTrue(effect is LoginEffect.LaunchCustomTab)
            assertEquals(url, (effect as LoginEffect.LaunchCustomTab).url)

            val state = vm.uiState.value
            assertEquals(false, state.isLoading)
            assertNull(state.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException 'Failed to resolve handle' maps to HandleNotFound carrying the handle`() =
        runTest(mainDispatcher.dispatcher) {
            // Pin the upstream message: this prefix is what DiscoveryChain.resolveHandle
            // throws when neither DNS-over-HTTPS nor the HTTP fallback returns a DID.
            // If atproto-kotlin grows a typed HandleNotFoundException, replace both the
            // VM mapping and this test together.
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(
                            beginLoginResult =
                                Result.failure(
                                    OAuthDiscoveryException(
                                        "Failed to resolve handle 'alise.bsky.social': " +
                                            "neither DNS TXT record (_atproto.alise.bsky.social) " +
                                            "nor HTTP (https://alise.bsky.social/.well-known/atproto-did) " +
                                            "returned a valid DID",
                                    ),
                                ),
                        ),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alise.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(false, state.isLoading)
            assertEquals(LoginError.HandleNotFound("alise.bsky.social"), state.errorMessage)

            val effect = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(effect)
        }

    @Test
    fun `direct IOException maps to Network`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(beginLoginResult = Result.failure(IOException("no network"))),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `UnknownHostException maps to Network`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(beginLoginResult = Result.failure(UnknownHostException("no such host"))),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `SocketTimeoutException maps to Network`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(beginLoginResult = Result.failure(SocketTimeoutException("timed out"))),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException wrapping IOException maps to Network via cause-walk`() =
        runTest(mainDispatcher.dispatcher) {
            val wrapped =
                OAuthDiscoveryException(
                    "Failed to fetch resource server metadata from https://example.com",
                    IOException("connect timed out"),
                )
            val vm =
                newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException 'Failed to fetch' with no cause maps to Network`() =
        runTest(mainDispatcher.dispatcher) {
            // atproto-kotlin sometimes throws OAuthDiscoveryException with no
            // underlying cause — e.g. when a captive-portal middlebox returns
            // a 200 with HTML, the HTTP call succeeds, body-parsing fails, and
            // the discovery layer wraps the parse failure in a message-only
            // exception (line 295 in DiscoveryChain.kt: "Resource server
            // metadata at $url returned ${response.status}"). The cause-walk
            // misses these; message-prefix matching catches them.
            val wrapped =
                OAuthDiscoveryException(
                    "Failed to fetch DID document for 'did:plc:abc' from https://example.com",
                )
            val vm =
                newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException 'Failed to parse' from a JsonDecodingException maps to Network`() =
        runTest(mainDispatcher.dispatcher) {
            // Slow / hijacking routers commonly return a captive-portal HTML
            // page instead of JSON; atproto-kotlin's parse step throws
            // JsonDecodingException, wrapped as OAuthDiscoveryException with
            // a "Failed to parse" message. The wrapping kotlinx.serialization
            // exception is NOT an IOException, so a pure cause-walk misses it.
            val wrapped =
                OAuthDiscoveryException(
                    "Failed to parse resource server metadata from https://example.com",
                    cause = RuntimeException("Unexpected JSON token at offset 0: <!doctype html>"),
                )
            val vm =
                newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException with a 'returned' status message maps to Network`() =
        runTest(mainDispatcher.dispatcher) {
            // E.g. "Resource server metadata at $url returned 502 Bad Gateway"
            // — the HTTP call completed but the server (or a middlebox
            // intercepting the response) returned a non-2xx that's clearly
            // a reachability concern, not a config bug on the user's side.
            val wrapped =
                OAuthDiscoveryException(
                    "Auth server metadata at https://example.com returned 502 Bad Gateway",
                )
            val vm =
                newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException 'returned' with a 4-digit number does not match as a 3-digit status`() =
        runTest(mainDispatcher.dispatcher) {
            // The \b word-boundary must reject a partial match on a longer number:
            // " returned 5020" is not an HTTP status and should NOT be reclassified
            // as Network off its leading "502".
            val wrapped =
                OAuthDiscoveryException("DID document fetch for 'did:plc:x' returned 5020 widgets")
            val vm =
                newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Generic, vm.uiState.value.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException reachability match is case-insensitive`() =
        runTest(mainDispatcher.dispatcher) {
            // Guards against an upstream casing change in atproto-kotlin: a
            // lowercased "failed to fetch" must still classify as Network.
            val wrapped = OAuthDiscoveryException("failed to fetch DID document for did:plc:x")
            val vm =
                newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException with a 'missing endpoint' config message stays Generic`() =
        runTest(mainDispatcher.dispatcher) {
            // Negative test — protects against accidentally widening the
            // reachability heuristic to swallow real configuration bugs.
            // "authorization_endpoint missing from auth server metadata"
            // is an upstream server config problem, not a network problem;
            // retrying won't help and the user shouldn't be told to check
            // their connection.
            val wrapped =
                OAuthDiscoveryException(
                    "authorization_endpoint missing from auth server metadata at https://example.com",
                )
            val vm =
                newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Generic, vm.uiState.value.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException 'Unsupported DID method' stays Generic`() =
        runTest(mainDispatcher.dispatcher) {
            // Another negative — a DID method the lib doesn't support is a
            // permanent failure for that handle, not a transient network
            // condition.
            val wrapped = OAuthDiscoveryException("Unsupported DID method: did:example:foo")
            val vm =
                newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Generic, vm.uiState.value.errorMessage)
        }

    @Test
    fun `unknown Throwable maps to Generic and does not leak the library message`() =
        runTest(mainDispatcher.dispatcher) {
            val leakySubstring = "authorization_endpoint missing from auth server metadata"
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(beginLoginResult = Result.failure(IllegalStateException(leakySubstring))),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            val errorMessage = vm.uiState.value.errorMessage
            assertEquals(LoginError.Generic, errorMessage)
            // LoginError.Generic carries no payload, so the library message can't
            // round-trip through the sum; this assertion is belt-and-braces against
            // a future regression that adds a payload field.
            assertFalse(errorMessage.toString().contains(leakySubstring))
        }

    @Test
    fun `OpenSignup emits LaunchCustomTab(bsky_app_signup) without mutating state`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newViewModel()
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            val before = vm.uiState.value

            vm.handleEvent(LoginEvent.OpenSignup)
            advanceUntilIdle()

            val effect = vm.effects.first()
            assertTrue(effect is LoginEffect.LaunchCustomTab)
            assertEquals("https://bsky.app/", (effect as LoginEffect.LaunchCustomTab).url)

            // state is untouched: same handle, not loading, no error.
            assertEquals(before, vm.uiState.value)
        }

    @Test
    fun `CustomTabLaunchFailed surfaces BrowserUnavailable and clears loading`() {
        val vm = newViewModel()

        vm.handleEvent(LoginEvent.CustomTabLaunchFailed)

        assertEquals(LoginError.BrowserUnavailable, vm.uiState.value.errorMessage)
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `ClearError nulls errorMessage`() {
        val vm = newViewModel()
        vm.handleEvent(LoginEvent.SubmitLogin)
        assertEquals(LoginError.BlankHandle, vm.uiState.value.errorMessage)

        vm.handleEvent(LoginEvent.ClearError)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `broker emission with successful completeLogin emits LoginSucceeded`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.success(Unit))
            val vm = newViewModel(authRepository = auth, broker = broker)
            advanceUntilIdle() // let init-time collector subscribe

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            val effect = vm.effects.first()
            // The fixture's default decider returns shouldPrompt = false (sdkInt = 0),
            // so the LoginSucceeded effect carries requestPostNotificationsPermission = false.
            // The true-branch is exercised separately in LoginPostNotificationsPromptTest.
            assertEquals(LoginEffect.LoginSucceeded(requestPostNotificationsPermission = false), effect)
            assertEquals(1, auth.completeLoginInvocations)
            assertNull(vm.uiState.value.errorMessage)
        }

    @Test
    fun `successful completeLogin logs the login event once`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.success(Unit))
            val analytics = RecordingAnalyticsClient()
            newViewModel(authRepository = auth, broker = broker, analytics = analytics)
            advanceUntilIdle() // let init-time collector subscribe

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            // The redirect-returned funnel event precedes the success event; the
            // custom scheme (not https) tags it CustomScheme.
            assertEquals(
                listOf(LoginRedirectReturned(OAuthRedirectKind.CustomScheme), Login()),
                analytics.events,
            )
        }

    @Test
    fun `failed completeLogin logs login_error (not the login success event)`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.failure(java.io.IOException("net")))
            val analytics = RecordingAnalyticsClient()
            newViewModel(authRepository = auth, broker = broker, analytics = analytics)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            assertEquals(
                listOf(
                    LoginRedirectReturned(OAuthRedirectKind.CustomScheme),
                    LoginFailed(reason = LoginErrorReason.Network, stage = LoginStage.Complete),
                ),
                analytics.events,
            )
        }

    @Test
    fun `failed beginLogin logs login_error with the begin stage`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = FakeAuthRepository(beginLoginResult = Result.failure(java.io.IOException("net")))
            val analytics = RecordingAnalyticsClient()
            val vm = newViewModel(authRepository = auth, analytics = analytics)

            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(
                listOf(LoginFailed(reason = LoginErrorReason.Network, stage = LoginStage.Begin)),
                analytics.events,
            )
        }

    @Test
    fun `OAuthDiscoveryException config bug logs login_error with the oauth_config reason`() =
        runTest(mainDispatcher.dispatcher) {
            // A genuine upstream server-config problem (missing endpoint) is neither
            // a network issue nor a user typo. It surfaces in analytics as its own
            // bucket — split out from the catch-all `unexpected` — while the UI still
            // shows the coarse, PII-free Generic message.
            val wrapped =
                OAuthDiscoveryException(
                    "authorization_endpoint missing from auth server metadata at https://example.com",
                )
            val analytics = RecordingAnalyticsClient()
            val vm =
                newViewModel(
                    authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)),
                    analytics = analytics,
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(
                listOf(LoginFailed(reason = LoginErrorReason.OauthConfig, stage = LoginStage.Begin)),
                analytics.events,
            )
            // UI stays coarse: oauth_config and unexpected both show Generic.
            assertEquals(LoginError.Generic, vm.uiState.value.errorMessage)
        }

    @Test
    fun `unknown Throwable logs login_error with the unexpected reason`() =
        runTest(mainDispatcher.dispatcher) {
            val analytics = RecordingAnalyticsClient()
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(beginLoginResult = Result.failure(IllegalStateException("boom"))),
                    analytics = analytics,
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(
                listOf(LoginFailed(reason = LoginErrorReason.Unexpected, stage = LoginStage.Begin)),
                analytics.events,
            )
        }

    @Test
    fun `blank handle submit logs no analytics event`() =
        runTest(mainDispatcher.dispatcher) {
            val analytics = RecordingAnalyticsClient()
            val vm = newViewModel(analytics = analytics)

            vm.handleEvent(LoginEvent.HandleChanged("   "))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
        }

    @Test
    fun `broker emission with generic failure populates errorMessage as Generic and emits no effect`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth =
                FakeAuthRepository(
                    completeLoginResult = Result.failure(IllegalStateException("invalid code")),
                )
            val vm = newViewModel(authRepository = auth, broker = broker)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=bad")
            advanceUntilIdle()

            assertEquals(LoginError.Generic, vm.uiState.value.errorMessage)
            assertEquals(false, vm.uiState.value.isLoading)

            val effect = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(effect)
        }

    @Test
    fun `broker emission with network failure populates errorMessage as Network`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.failure(IOException("connect failed")))
            val vm = newViewModel(authRepository = auth, broker = broker)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=bad")
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `generic completeLogin failure records a structured non-fatal with custom keys`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.failure(IllegalStateException("boom")))
            val crash = RecordingCrashReporter()
            newViewModel(authRepository = auth, broker = broker, crashReporter = crash)
            advanceUntilIdle()

            broker.emit("https://nubecita.app/oauth-redirect?code=bad")
            advanceUntilIdle()

            // One grouped non-fatal, carrying the original cause.
            val recorded = crash.recorded.single()
            assertEquals("boom", recorded.cause?.message)
            // PII-free diagnostic keys attached to the same report.
            assertEquals("complete", crash.keys["login_stage"])
            assertEquals("unexpected", crash.keys["login_reason"])
            assertEquals("IllegalStateException", crash.keys["login_exception"])
            assertEquals("applink", crash.keys["oauth_redirect_kind"])
        }

    @Test
    fun `network completeLogin failure records no non-fatal`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.failure(IOException("offline")))
            val crash = RecordingCrashReporter()
            newViewModel(authRepository = auth, broker = broker, crashReporter = crash)
            advanceUntilIdle()

            broker.emit("https://nubecita.app/oauth-redirect?code=bad")
            advanceUntilIdle()

            assertTrue(crash.recorded.isEmpty(), "Network failures are breadcrumb-only, not non-fatals")
        }

    @Test
    fun `custom-scheme redirect failure tags oauth_redirect_kind as custom_scheme`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.failure(IllegalStateException("x")))
            val crash = RecordingCrashReporter()
            newViewModel(authRepository = auth, broker = broker, crashReporter = crash)
            advanceUntilIdle()

            broker.emit("app.nubecita:/oauth-redirect?code=bad")
            advanceUntilIdle()

            assertEquals("custom_scheme", crash.keys["oauth_redirect_kind"])
        }

    @Test
    fun `successful beginLogin emits login_redirect_launched`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = FakeAuthRepository(beginLoginResult = Result.success("https://pds.example/authorize"))
            val analytics = RecordingAnalyticsClient()
            val vm = newViewModel(authRepository = auth, analytics = analytics)

            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(listOf(LoginRedirectLaunched), analytics.events)
        }

    @Test
    fun `redirect arrival emits login_redirect_returned then login on success`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.success(Unit))
            val analytics = RecordingAnalyticsClient()
            newViewModel(authRepository = auth, broker = broker, analytics = analytics)
            advanceUntilIdle()

            broker.emit("https://nubecita.app/oauth-redirect?code=ok")
            advanceUntilIdle()

            assertEquals(
                listOf(LoginRedirectReturned(OAuthRedirectKind.AppLink), Login()),
                analytics.events,
            )
        }

    @Test
    fun `begin-stage non-fatal tags oauth_redirect_kind none (no stale Complete value bleeds)`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = FakeAuthRepository(beginLoginResult = Result.failure(IllegalStateException("boom")))
            val crash = RecordingCrashReporter()
            val vm = newViewModel(authRepository = auth, crashReporter = crash)

            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            // Begin stage has no redirect; the key is still written ("none") so a
            // prior Complete-stage value can't bleed onto this report.
            assertEquals("none", crash.keys["oauth_redirect_kind"])
        }

    @Test
    fun `completeLogin keeps isLoading true while the token exchange is in flight`() =
        runTest(mainDispatcher.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginGate = gate)
            val vm = newViewModel(authRepository = auth, broker = broker)
            advanceUntilIdle()

            broker.emit("https://nubecita.app/oauth-redirect?code=ok")
            advanceUntilIdle() // parks inside completeLogin at the gate

            assertTrue(vm.uiState.value.isLoading, "screen should show progress during token exchange")

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `double submit while a login is in flight is ignored`() =
        runTest(mainDispatcher.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val auth =
                FakeAuthRepository(
                    beginLoginResult = Result.success("https://pds.example/authorize"),
                    beginLoginGate = gate,
                )
            val analytics = RecordingAnalyticsClient()
            val vm = newViewModel(authRepository = auth, analytics = analytics)
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))

            vm.handleEvent(LoginEvent.SubmitLogin) // starts; parks at the gate (isLoading = true)
            vm.handleEvent(LoginEvent.SubmitLogin) // ignored by the in-flight guard
            advanceUntilIdle()

            assertEquals(1, auth.beginLoginInvocations)

            gate.complete(Unit)
            advanceUntilIdle()
            // Exactly one launch event despite the double tap.
            assertEquals(listOf(LoginRedirectLaunched), analytics.events)
        }
}

private fun newViewModel(
    authRepository: AuthRepository = FakeAuthRepository(),
    broker: OAuthRedirectBroker = FakeOAuthRedirectBroker(),
    notificationsPromptDecider: NotificationsPromptDecider =
        NotificationsPromptDecider(NoopPromptStore, sdkInt = 0),
    analytics: AnalyticsClient = NoOpAnalyticsClient(),
    crashReporter: CrashReporter = RecordingCrashReporter(),
): LoginViewModel =
    LoginViewModel(
        authRepository = authRepository,
        notificationsPromptDecider = notificationsPromptDecider,
        analytics = analytics,
        crashReporter = crashReporter,
        broker = broker,
    )

/** Records crash-seam calls so tests can assert structured non-fatals + keys. */
internal class RecordingCrashReporter : CrashReporter {
    val breadcrumbs = mutableListOf<String>()
    val recorded = mutableListOf<Throwable>()
    val keys = mutableMapOf<String, String>()

    override fun log(message: String) {
        breadcrumbs += message
    }

    override fun recordException(throwable: Throwable) {
        recorded += throwable
    }

    override fun setCustomKey(
        key: String,
        value: String,
    ) {
        keys[key] = value
    }
}

// The base LoginViewModel tests don't exercise the POST_NOTIFICATIONS prompt
// gating — the dedicated [LoginPostNotificationsPromptTest] covers that. Use
// a decider built on top of this no-op store + `sdkInt = 0` so `shouldPrompt`
// returns false (below TIRAMISU) and the broker-success test sees exactly one
// effect (LoginSucceeded) instead of two.
private object NoopPromptStore : NotificationsPromptShownStore {
    override suspend fun read(): Boolean = false

    override suspend fun markShown() = Unit
}

private class FakeAuthRepository(
    private val beginLoginResult: Result<String> = Result.success("ignored"),
    private val completeLoginResult: Result<Unit> = Result.success(Unit),
    // Optional gates: when set, the call suspends until completed, letting tests
    // observe in-flight state (loading, double-submit guard) deterministically.
    private val beginLoginGate: CompletableDeferred<Unit>? = null,
    private val completeLoginGate: CompletableDeferred<Unit>? = null,
) : AuthRepository {
    var beginLoginInvocations: Int = 0
        private set
    var completeLoginInvocations: Int = 0
        private set

    override suspend fun beginLogin(handle: String): Result<String> {
        beginLoginInvocations++
        beginLoginGate?.await()
        return beginLoginResult
    }

    override suspend fun completeLogin(redirectUri: String): Result<Unit> {
        completeLoginInvocations++
        completeLoginGate?.await()
        return completeLoginResult
    }

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}

private class FakeOAuthRedirectBroker : OAuthRedirectBroker {
    private val channel = Channel<String>(Channel.BUFFERED)
    override val redirects: Flow<String> = channel.receiveAsFlow()

    override suspend fun publish(redirectUri: String) {
        channel.send(redirectUri)
    }

    suspend fun emit(redirectUri: String) {
        channel.send(redirectUri)
    }
}
