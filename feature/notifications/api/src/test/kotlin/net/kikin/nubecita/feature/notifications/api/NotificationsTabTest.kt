package net.kikin.nubecita.feature.notifications.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationsTabTest {
    @Test
    fun notificationsTab_isANavKey() {
        val key: NavKey = NotificationsTab

        // Compile-time + runtime conformance — the assignment proves the
        // type relationship; the assertion documents intent.
        assertEquals(NotificationsTab::class, key::class)
    }

    @Test
    fun notificationsTab_serializesAndRoundTrips() {
        val encoded = Json.encodeToString(NotificationsTab.serializer(), NotificationsTab)
        val decoded = Json.decodeFromString(NotificationsTab.serializer(), encoded)

        assertEquals(NotificationsTab, decoded)
    }
}
