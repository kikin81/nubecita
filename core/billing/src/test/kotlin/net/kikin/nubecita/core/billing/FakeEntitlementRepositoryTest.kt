package net.kikin.nubecita.core.billing

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FakeEntitlementRepositoryTest {
    @Test
    fun `isPro starts false`() {
        assertFalse(FakeEntitlementRepository().isPro.value)
    }

    @Test
    fun `setPro flips isPro and emits the transition`() =
        runTest {
            val repo = FakeEntitlementRepository()

            repo.isPro.test {
                assertFalse(awaitItem()) // initial

                repo.setPro(true)
                assertTrue(awaitItem())

                repo.setPro(false)
                assertFalse(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refresh leaves the in-memory entitlement unchanged`() =
        runTest {
            val repo = FakeEntitlementRepository()
            repo.setPro(true)

            repo.refresh()

            assertTrue(repo.isPro.value)
        }
}
