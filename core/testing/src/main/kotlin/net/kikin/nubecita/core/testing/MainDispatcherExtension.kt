package net.kikin.nubecita.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension that installs a [TestDispatcher] as `Dispatchers.Main`
 * for the duration of a test and restores the original after.
 *
 * Apply via `@ExtendWith(MainDispatcherExtension::class)` on the test class.
 * The [dispatcher] field is exposed so tests can align `runTest`'s
 * scheduler with Main's via `runTest(extension.dispatcher) { ... }`;
 * otherwise `runTest` creates its own scheduler that is disjoint from
 * Main's, and `advanceUntilIdle()` / `runCurrent()` will not drive
 * coroutines dispatched to [androidx.lifecycle.viewModelScope] (which
 * runs on Main), producing hangs or false-negative assertions.
 *
 * For a per-test dispatcher (e.g., one test wants
 * `UnconfinedTestDispatcher`), construct the extension with
 * `@RegisterExtension val ext = MainDispatcherExtension(dispatcher)`
 * instead of `@ExtendWith`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}
