package net.kikin.nubecita.core.common.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DefaultNetworkStatusTest {
    @Test
    fun `no active network is metered`() {
        // The case the platform gets backwards: ConnectivityManager reports
        // isActiveNetworkMetered = false when there is no network at all, so
        // reading it alone tells an offline device its connection is free.
        assertTrue(DefaultNetworkStatus.isMetered(hasActiveNetwork = false, reportedMetered = false))
        assertTrue(DefaultNetworkStatus.isMetered(hasActiveNetwork = false, reportedMetered = true))
    }

    @Test
    fun `an active network reports what the platform says`() {
        assertTrue(DefaultNetworkStatus.isMetered(hasActiveNetwork = true, reportedMetered = true))
        assertFalse(DefaultNetworkStatus.isMetered(hasActiveNetwork = true, reportedMetered = false))
    }
}
