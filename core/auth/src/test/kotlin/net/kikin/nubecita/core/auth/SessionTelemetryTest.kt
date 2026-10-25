package net.kikin.nubecita.core.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AuthKeysetRegenerated
import net.kikin.nubecita.core.analytics.SessionClearReason
import net.kikin.nubecita.core.analytics.SessionCleared
import net.kikin.nubecita.core.analytics.SessionReadError
import net.kikin.nubecita.core.analytics.SessionReadErrorCause
import net.kikin.nubecita.core.analytics.SessionReadErrorTerminal
import net.kikin.nubecita.core.logging.CrashReporter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.time.Clock
import kotlin.time.Instant

class SessionTelemetryTest {
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val analytics = mockk<AnalyticsClient>(relaxed = true)

    private val nowMillis = 1_720_000_000_000L
    private val fixedClock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
        }

    private fun telemetry(
        lastLoginEpochMillis: Long? = null,
        forensics: RefreshForensics = FakeRefreshForensics(emptyMap()),
    ) = SessionTelemetry(
        crashReporter = crashReporter,
        analytics = analytics,
        loginTimestamps = FakeLoginTimestampStore(lastLoginEpochMillis),
        clock = fixedClock,
        refreshForensics = forensics,
    )

    @Test
    fun `clear from the SDK refresh path buckets as invalid_grant with days_since_login`() =
        runTest {
            val twoDaysMillis = 2L * 24 * 60 * 60 * 1000
            val marker = markerWithStack("io.github.kikin81.atproto.oauth.DpopAuthProvider", "failRefresh")

            telemetry(lastLoginEpochMillis = nowMillis - twoDaysMillis).onSessionCleared(marker)

            verify { crashReporter.setCustomKey("session_clear_reason", "invalid_grant") }
            verify { crashReporter.recordException(marker) }
            verify { analytics.log(SessionCleared(reason = SessionClearReason.InvalidGrant, daysSinceLogin = 2)) }
        }

    @Test
    fun `clear attaches exact days_since_login and the refresh forensics before recording`() =
        runTest {
            val twoDaysMillis = 2L * 24 * 60 * 60 * 1000
            val marker = markerWithStack("io.github.kikin81.atproto.oauth.DpopAuthProvider", "failRefresh")
            val forensics = FakeRefreshForensics(mapOf("last_lost_refresh_caller" to "dm_poll"))

            telemetry(lastLoginEpochMillis = nowMillis - twoDaysMillis, forensics = forensics).onSessionCleared(marker)

            verifyOrder {
                crashReporter.setCustomKey("days_since_login", "2")
                crashReporter.setCustomKey("last_lost_refresh_caller", "dm_poll")
                crashReporter.recordException(marker)
            }
        }

    @Test
    fun `a throwing refresh forensics read still records the clear`() =
        runTest {
            val marker = markerWithStack("io.github.kikin81.atproto.oauth.DpopAuthProvider", "failRefresh")
            val forensics =
                object : RefreshForensics {
                    override suspend fun clearKeys(): Map<String, String> = throw IllegalStateException("prefs unreadable")
                }

            telemetry(forensics = forensics).onSessionCleared(marker)

            verify { crashReporter.setCustomKey("days_since_login", "unknown") }
            verify { crashReporter.recordException(marker) }
        }

    @Test
    fun `clear from AtOAuth logout buckets as user_sign_out`() =
        runTest {
            val marker = markerWithStack("io.github.kikin81.atproto.oauth.AtOAuth", "logout")

            telemetry(lastLoginEpochMillis = nowMillis).onSessionCleared(marker)

            verify { analytics.log(SessionCleared(reason = SessionClearReason.UserSignOut, daysSinceLogin = 0)) }
        }

    @Test
    fun `clear from an unrecognized call site buckets as unknown and omits days when no timestamp`() =
        runTest {
            val marker = markerWithStack("com.example.SomethingElse", "run")

            telemetry(lastLoginEpochMillis = null).onSessionCleared(marker)

            verify { analytics.log(SessionCleared(reason = SessionClearReason.Unknown, daysSinceLogin = null)) }
        }

    @Test
    fun `future login timestamp clamps days_since_login to zero`() =
        runTest {
            val marker = markerWithStack("io.github.kikin81.atproto.oauth.AtOAuth", "logout")

            telemetry(lastLoginEpochMillis = nowMillis + 60_000).onSessionCleared(marker)

            verify { analytics.log(SessionCleared(reason = SessionClearReason.UserSignOut, daysSinceLogin = 0)) }
        }

    @Test
    fun `read error maps IOException to io and preserves the cause for the non-fatal`() {
        val cause = IOException("simulated")

        telemetry().onSessionReadError(cause)

        verify { analytics.log(SessionReadError(cause = SessionReadErrorCause.Io)) }
        verify { crashReporter.recordException(match { it is SessionReadFailedException && it.cause === cause }) }
    }

    @Test
    fun `read error maps GeneralSecurityException to security`() {
        telemetry().onSessionReadError(GeneralSecurityException("simulated"))

        verify { analytics.log(SessionReadError(cause = SessionReadErrorCause.Security)) }
    }

    @Test
    fun `read error maps SerializationException to serialization`() {
        telemetry().onSessionReadError(SerializationException("simulated"))

        verify { analytics.log(SessionReadError(cause = SessionReadErrorCause.Serialization)) }
    }

    @Test
    fun `a throwing crash reporter never propagates out of any telemetry method`() =
        runTest {
            every { crashReporter.recordException(any()) } throws RuntimeException("crashlytics not initialized")

            telemetry().onSessionCleared(markerWithStack("com.example.X", "run"))
            telemetry().onSessionReadError(IOException("simulated"))
            telemetry().onKeysetRegenerated(GeneralSecurityException("simulated"))
        }

    @Test
    fun `a throwing analytics client never propagates`() =
        runTest {
            every { analytics.log(any()) } throws IllegalStateException("analytics backend down")

            telemetry().onSessionCleared(markerWithStack("com.example.X", "run"))
            telemetry().onSessionReadError(IOException("simulated"))
            telemetry().onKeysetRegenerated(GeneralSecurityException("simulated"))
        }

    @Test
    fun `a throwing login-timestamp read never propagates out of onSessionCleared`() =
        runTest {
            val telemetry =
                SessionTelemetry(
                    crashReporter = crashReporter,
                    analytics = analytics,
                    loginTimestamps = ThrowingLoginTimestampStore,
                    clock = fixedClock,
                    refreshForensics = FakeRefreshForensics(emptyMap()),
                )

            telemetry.onSessionCleared(markerWithStack("com.example.X", "run"))
        }

    @Test
    fun `cancellation propagates out of onSessionCleared untouched`() =
        runTest {
            val telemetry =
                SessionTelemetry(
                    crashReporter = crashReporter,
                    analytics = analytics,
                    loginTimestamps = CancellingLoginTimestampStore,
                    clock = fixedClock,
                    refreshForensics = FakeRefreshForensics(emptyMap()),
                )

            val thrown =
                runCatching { telemetry.onSessionCleared(markerWithStack("com.example.X", "run")) }
                    .exceptionOrNull()
            assertTrue(thrown is CancellationException, "expected CancellationException, got $thrown")
        }

    @Test
    fun `terminal read-error exhaustion records a grouped non-fatal and the terminal event`() {
        val cause = GeneralSecurityException("keystore still unavailable")

        telemetry().onSessionReadErrorTerminal(cause)

        verify {
            crashReporter.recordException(
                match { it is SessionReadTerminalException && it.cause === cause },
            )
        }
        verify { analytics.log(SessionReadErrorTerminal(cause = SessionReadErrorCause.Security)) }
    }

    @Test
    fun `keyset regeneration records a grouped non-fatal and the analytics event`() {
        val cause = GeneralSecurityException("corrupted keyset")

        telemetry().onKeysetRegenerated(cause)

        verify { crashReporter.recordException(match { it is AuthKeysetRegeneratedException && it.cause === cause }) }
        verify { analytics.log(AuthKeysetRegenerated) }
    }

    private fun markerWithStack(
        className: String,
        methodName: String,
    ): SessionClearedException =
        SessionClearedException().apply {
            stackTrace =
                arrayOf(
                    StackTraceElement(
                        "net.kikin.nubecita.core.auth.EncryptedOAuthSessionStore",
                        "clear",
                        "EncryptedOAuthSessionStore.kt",
                        1,
                    ),
                    StackTraceElement(className, methodName, "${className.substringAfterLast('.')}.kt", 1),
                )
        }
}

private class FakeLoginTimestampStore(
    private val value: Long?,
) : LoginTimestampStore {
    override suspend fun record(epochMillis: Long) = Unit

    override suspend fun lastLoginEpochMillis(): Long? = value
}

private object ThrowingLoginTimestampStore : LoginTimestampStore {
    override suspend fun record(epochMillis: Long) = Unit

    override suspend fun lastLoginEpochMillis(): Long? = throw IllegalStateException("prefs unreadable")
}

private object CancellingLoginTimestampStore : LoginTimestampStore {
    override suspend fun record(epochMillis: Long) = Unit

    override suspend fun lastLoginEpochMillis(): Long? = throw CancellationException("scope cancelled")
}

private class FakeRefreshForensics(
    private val keys: Map<String, String>,
) : RefreshForensics {
    override suspend fun clearKeys(): Map<String, String> = keys
}
