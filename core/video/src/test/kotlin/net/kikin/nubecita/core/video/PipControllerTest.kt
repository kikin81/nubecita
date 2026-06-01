package net.kikin.nubecita.core.video

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.billing.EntitlementRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class PipControllerTest {
    private class FakeEntitlement(
        initialPro: Boolean,
    ) : EntitlementRepository {
        val proFlow = MutableStateFlow(initialPro)
        override val isPro: StateFlow<Boolean> get() = proFlow

        override suspend fun refresh() = Unit
    }

    private fun controller(
        deviceSupportsPip: Boolean,
        isPro: Boolean = false,
        scope: CoroutineScope,
    ): Pair<PipController, FakeEntitlement> {
        val entitlement = FakeEntitlement(isPro)
        return PipController(deviceSupportsPip, entitlement, scope) to entitlement
    }

    // isEnabled truth table: enabled only when BOTH device support and Pro hold.

    @Test
    fun `isEnabled is false when the device does not support PiP even if Pro`() =
        runTest {
            val (pip, _) = controller(deviceSupportsPip = false, isPro = true, scope = backgroundScope)
            assertFalse(pip.isEnabled.value)
        }

    @Test
    fun `isEnabled is false when the device supports PiP but not Pro`() =
        runTest {
            val (pip, _) = controller(deviceSupportsPip = true, isPro = false, scope = backgroundScope)
            assertFalse(pip.isEnabled.value)
        }

    @Test
    fun `isEnabled is true only when device support and Pro both hold`() =
        runTest {
            val (pip, _) = controller(deviceSupportsPip = true, isPro = true, scope = backgroundScope)
            assertTrue(pip.isEnabled.value)
        }

    @Test
    fun `isEnabled reacts to isPro transitions on a supported device`() =
        runTest(UnconfinedTestDispatcher()) {
            val (pip, entitlement) = controller(deviceSupportsPip = true, isPro = false, scope = backgroundScope)

            pip.isEnabled.test {
                assertFalse(awaitItem()) // not Pro yet

                entitlement.proFlow.value = true
                assertTrue(awaitItem())

                entitlement.proFlow.value = false
                assertFalse(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isEnabled never flips on an unsupported device regardless of Pro changes`() =
        runTest(UnconfinedTestDispatcher()) {
            val (pip, entitlement) = controller(deviceSupportsPip = false, isPro = false, scope = backgroundScope)

            entitlement.proFlow.value = true

            assertFalse(pip.isEnabled.value)
        }

    // isInPip is the Activity-driven flag (set by the PiP bridge in a later task).

    @Test
    fun `isInPip defaults to false and reflects setInPip`() =
        runTest {
            val (pip, _) = controller(deviceSupportsPip = true, scope = backgroundScope)
            assertFalse(pip.isInPip.value)

            pip.setInPip(true)
            assertTrue(pip.isInPip.value)

            pip.setInPip(false)
            assertFalse(pip.isInPip.value)
        }
}
