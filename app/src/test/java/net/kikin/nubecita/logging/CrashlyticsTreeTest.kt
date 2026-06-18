package net.kikin.nubecita.logging

import android.util.Log
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrashlyticsTreeTest {
    @Test
    fun `only error and above are loggable`() {
        val tree = CrashlyticsTree(RecordingReporter())

        assertFalse(tree.isLoggable(tag = null, priority = Log.VERBOSE))
        assertFalse(tree.isLoggable(tag = null, priority = Log.DEBUG))
        assertFalse(tree.isLoggable(tag = null, priority = Log.INFO))
        assertFalse(tree.isLoggable(tag = null, priority = Log.WARN))
        assertTrue(tree.isLoggable(tag = null, priority = Log.ERROR))
        assertTrue(tree.isLoggable(tag = null, priority = Log.ASSERT))
    }

    @Test
    fun `error with a throwable records that throwable as the non-fatal`() {
        val reporter = RecordingReporter()
        val tree = CrashlyticsTree(reporter)
        val cause = IllegalStateException("boom")

        tree.log(priority = Log.ERROR, tag = "Tag", message = "it broke", t = cause)

        assertSame(cause, reporter.recorded.single())
        assertEquals("[Tag] it broke", reporter.logged.single())
    }

    @Test
    fun `message-only error synthesizes an exception carrying the message`() {
        val reporter = RecordingReporter()
        val tree = CrashlyticsTree(reporter)

        tree.log(priority = Log.ERROR, tag = null, message = "no cause here", t = null)

        val recorded = reporter.recorded.single()
        assertInstanceOf(Exception::class.java, recorded)
        assertEquals("no cause here", recorded.message)
        // No tag → message forwarded verbatim as the breadcrumb.
        assertEquals("no cause here", reporter.logged.single())
    }

    private class RecordingReporter : CrashReporter {
        val logged = mutableListOf<String>()
        val recorded = mutableListOf<Throwable>()

        override fun log(message: String) {
            logged += message
        }

        override fun recordException(throwable: Throwable) {
            recorded += throwable
        }
    }
}
