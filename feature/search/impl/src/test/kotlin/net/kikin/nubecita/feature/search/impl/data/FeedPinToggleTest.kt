package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.feature.search.impl.FakePinnedFeedsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedPinToggleTest {
    @Test
    fun `a settled call is forgotten, so the next tap on the same feed is accepted`() =
        runTest {
            val repo = FakePinnedFeedsRepository()
            val toggle = FeedPinToggle(repo)
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val outcomes = mutableListOf<PinOutcome>()

            assertTrue(toggle.toggle(scope, "at://feed/a", isPinned = false, onOptimistic = {}) { outcomes += it })
            runCurrent()

            assertTrue(toggle.inFlightCalls().isEmpty(), "a finished call must not stay in the in-flight map")
            assertTrue(toggle.toggle(scope, "at://feed/a", isPinned = true, onOptimistic = {}) { outcomes += it })
            runCurrent()
            assertEquals(listOf(PinOutcome.Pinned, PinOutcome.Unpinned), outcomes)
            assertTrue(toggle.inFlightCalls().isEmpty())
        }

    /** The private in-flight map, read reflectively so the class exposes nothing for the test. */
    private fun FeedPinToggle.inFlightCalls(): Map<*, *> =
        FeedPinToggle::class.java
            .getDeclaredField("activeJobs")
            .apply { isAccessible = true }
            .get(this) as Map<*, *>
}
