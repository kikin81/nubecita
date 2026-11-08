package net.kikin.nubecita.core.auth

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SessionClearTelemetryRecoveryTest {
    private val telemetry = mockk<SessionTelemetry>(relaxed = true)
    private val refreshTelemetry = mockk<TokenRefreshTelemetry>(relaxed = true)

    @Test
    fun `start runs the lost-clear recovery pass`() =
        runTest {
            SessionClearTelemetryRecovery(telemetry, refreshTelemetry, backgroundScope).start()

            runCurrent()

            coVerify(exactly = 1) { telemetry.reportLostClearTelemetry() }
        }

    @Test
    fun `start also reports a refresh the previous process never finished`() =
        runTest {
            SessionClearTelemetryRecovery(telemetry, refreshTelemetry, backgroundScope).start()

            runCurrent()

            coVerify(exactly = 1) { refreshTelemetry.reportRefreshDiedInFlight() }
        }

    @Test
    fun `start is a one-shot - a second call never re-reports the same clear`() =
        runTest {
            val recovery = SessionClearTelemetryRecovery(telemetry, refreshTelemetry, backgroundScope)

            recovery.start()
            runCurrent()
            recovery.start()
            runCurrent()

            coVerify(exactly = 1) { telemetry.reportLostClearTelemetry() }
        }

    @Test
    fun `start does not block its caller - the pass runs off the initializer thread`() =
        runTest {
            SessionClearTelemetryRecovery(telemetry, refreshTelemetry, backgroundScope).start()

            // Nothing has run yet: start() only schedules, so the app's
            // single-threaded AppInitializer pass is never held up by disk IO.
            coVerify(exactly = 0) { telemetry.reportLostClearTelemetry() }
        }
}
