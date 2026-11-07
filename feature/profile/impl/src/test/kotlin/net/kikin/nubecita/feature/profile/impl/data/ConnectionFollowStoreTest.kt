package net.kikin.nubecita.feature.profile.impl.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ConnectionFollowStoreTest {
    @Test
    fun `the next account doesn't inherit follow changes`() =
        runTest {
            val store = ConnectionFollowStore()
            store.record("did:plc:alice", "at://follow")

            store.clearSession()

            assertTrue(store.committed.value.isEmpty())
        }
}
