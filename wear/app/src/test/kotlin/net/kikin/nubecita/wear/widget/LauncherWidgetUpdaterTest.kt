package net.kikin.nubecita.wear.widget

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LauncherWidgetUpdaterTest {
    private val uncaught = mutableListOf<Throwable>()

    // Shaped like the app scope (SupervisorJob), with uncaught errors recorded instead of crashing.
    private fun TestScope.appScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> uncaught += e })

    @Test
    fun aDmSync_refreshesTheWidget() =
        runTest {
            var refreshes = 0
            val updater = LauncherWidgetUpdater(appScope()) { refreshes++ }

            updater.onDmsChanged()
            advanceUntilIdle()

            assertEquals(1, refreshes)
        }

    @Test
    fun aFailingRefresh_neverEscapesIntoTheAppScope() =
        runTest {
            // Updating placed widgets talks to the system and can fail; that must not crash the app.
            val updater = LauncherWidgetUpdater(appScope()) { error("tiles service unavailable") }

            updater.onDmsChanged()
            advanceUntilIdle()

            assertTrue(uncaught.isEmpty(), "uncaught: $uncaught")
        }
}
