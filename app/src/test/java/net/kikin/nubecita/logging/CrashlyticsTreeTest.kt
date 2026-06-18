package net.kikin.nubecita.logging

import android.util.Log
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrashlyticsTreeTest {
    @Test
    fun `only warn and above are loggable`() {
        val tree = CrashlyticsTree(RecordingReporter())

        assertFalse(tree.isLoggable(tag = null, priority = Log.VERBOSE))
        assertFalse(tree.isLoggable(tag = null, priority = Log.DEBUG))
        assertFalse(tree.isLoggable(tag = null, priority = Log.INFO))
        assertTrue(tree.isLoggable(tag = null, priority = Log.WARN))
        assertTrue(tree.isLoggable(tag = null, priority = Log.ERROR))
        assertTrue(tree.isLoggable(tag = null, priority = Log.ASSERT))
    }

    @Test
    fun `warn is a breadcrumb only, not a non-fatal`() {
        val reporter = RecordingReporter()
        val tree = CrashlyticsTree(reporter)

        tree.log(priority = Log.WARN, tag = "Tag", message = "offline", t = IllegalStateException("x"))

        assertEquals("[Tag] offline", reporter.logged.single())
        assertTrue(reporter.recorded.isEmpty(), "WARN must not record a non-fatal")
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

    @Test
    fun `synthetic exception strips timber and tree frames so it groups by call site`() {
        val reporter = RecordingReporter()
        val tree = CrashlyticsTree(reporter)

        tree.log(priority = Log.ERROR, tag = null, message = "boom", t = null)

        // The top frame must be the caller (this test), not the tree/Timber —
        // otherwise Crashlytics would cluster every message-only error into one issue.
        val top =
            reporter.recorded
                .single()
                .stackTrace
                .first()
                .className
        assertFalse(top.startsWith("timber."), "Timber frames should be stripped, got $top")
        assertNotEquals("net.kikin.nubecita.logging.CrashlyticsTree", top, "tree frame should be stripped")
        assertTrue(top.startsWith("net.kikin.nubecita.logging.CrashlyticsTreeTest"))
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
