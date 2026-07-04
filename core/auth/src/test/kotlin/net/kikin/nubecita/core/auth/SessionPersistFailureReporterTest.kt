package net.kikin.nubecita.core.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.kikin.nubecita.core.logging.CrashReporter
import org.junit.jupiter.api.Test
import java.io.IOException

class SessionPersistFailureReporterTest {
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val reporter = SessionPersistFailureReporter(crashReporter)

    @Test
    fun `report records a grouped non-fatal preserving the cause`() {
        val cause = IOException("disk full")

        reporter.report(cause)

        verify {
            crashReporter.recordException(
                match { it is SessionPersistFailedException && it.cause === cause },
            )
        }
    }

    @Test
    fun `a throwing crash reporter never propagates back into the SDK refresh path`() {
        every { crashReporter.recordException(any()) } throws RuntimeException("crashlytics not initialized")

        reporter.report(IOException("disk full"))
    }
}
