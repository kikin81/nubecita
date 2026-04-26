package net.kikin.nubecita.core.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class MainDispatcherExtensionTest {
    private val swap = StandardTestDispatcher()

    @RegisterExtension
    val extension = MainDispatcherExtension(swap)

    @Test
    fun `coroutines launched on Dispatchers_Main are driven by the test dispatcher`() =
        runTest(swap) {
            // Launch a coroutine explicitly on Dispatchers.Main; if the extension
            // installed `swap` as Main, the test scheduler can advance it. If not,
            // advanceUntilIdle wouldn't drive the launch and `executed` stays false.
            var executed = false
            CoroutineScope(Dispatchers.Main).launch { executed = true }
            advanceUntilIdle()
            assertEquals(true, executed)
        }
}
