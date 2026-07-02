package net.kikin.nubecita.feature.widgets.impl.entitlement

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WidgetEntitlementGateTest {
    @Test
    fun `always-allowed gate permits the configurable widget`() =
        runTest {
            assertTrue(AlwaysAllowedWidgetEntitlementGate().isConfigurableWidgetAllowed())
        }
}
