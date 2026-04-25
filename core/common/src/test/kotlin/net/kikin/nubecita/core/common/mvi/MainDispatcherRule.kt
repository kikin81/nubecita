package net.kikin.nubecita.core.common.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Installs a [TestDispatcher] as `Dispatchers.Main` for the duration of a test.
 *
 * The rule exposes its [dispatcher] so tests can align `runTest`'s scheduler
 * with Main's. Use it as `runTest(mainDispatcherRule.dispatcher) { ... }`;
 * otherwise `runTest` creates its own [kotlinx.coroutines.test.TestCoroutineScheduler]
 * that is disjoint from Main's, and calls to `advanceUntilIdle()` /
 * `runCurrent()` will not drive coroutines dispatched to
 * [androidx.lifecycle.viewModelScope] (which runs on Main), producing hangs
 * or false-negative assertions.
 *
 * NOTE: duplicated across `:core:common`, `:app`, and `:feature:*` test source sets
 * because we don't have a `:core:testing` module or `java-test-fixtures` set up yet.
 * Consolidate once we hit the next consumer (likely 4th or 5th test source set).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
