package net.kikin.nubecita.feature.notifications.impl

import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SelectedNotificationTargetTest {
    @Test
    fun `returns null when NotificationsTab is not in stack`() {
        assertNull(findSelectedPostUri(emptyList()))
        assertNull(findSelectedProfileHandle(emptyList()))
    }

    @Test
    fun `ignores routes from earlier tabs when NotificationsTab has no subroutes`() {
        val stack = listOf(PostDetailRoute("at://feed-post"), NotificationsTab)
        assertNull(findSelectedPostUri(stack))
        assertNull(findSelectedProfileHandle(stack))
    }

    @Test
    fun `resolves postUri when PostDetailRoute is pushed on NotificationsTab`() {
        val stack =
            listOf(
                NotificationsTab,
                PostDetailRoute("at://notif-post"),
            )
        assertEquals("at://notif-post", findSelectedPostUri(stack))
        assertNull(findSelectedProfileHandle(stack))
    }

    @Test
    fun `resolves profile handle when Profile is pushed on NotificationsTab`() {
        val stack =
            listOf(
                NotificationsTab,
                Profile(handle = "did:plc:bob"),
            )
        assertEquals("did:plc:bob", findSelectedProfileHandle(stack))
        assertNull(findSelectedPostUri(stack))
    }

    @Test
    fun `resolves topmost post when multiple subroutes are present`() {
        val stack =
            listOf(
                NotificationsTab,
                PostDetailRoute("at://post-1"),
                PostDetailRoute("at://post-2"),
            )
        assertEquals("at://post-2", findSelectedPostUri(stack))
    }
}
