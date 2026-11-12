package net.kikin.nubecita.wear.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LauncherWidgetModelTest {
    @Test
    fun neverSynced_showsNoCount() {
        assertEquals(LauncherWidgetModel(countLabel = null, hasUnread = false, signedIn = true), launcherWidgetModel(signedIn = true, unread = null))
    }

    @Test
    fun zero_isNotUnread() {
        assertEquals(LauncherWidgetModel(countLabel = "0", hasUnread = false, signedIn = true), launcherWidgetModel(signedIn = true, unread = 0))
    }

    @Test
    fun counts_upTo99_showAsIs() {
        assertEquals(LauncherWidgetModel(countLabel = "1", hasUnread = true, signedIn = true), launcherWidgetModel(signedIn = true, unread = 1))
        assertEquals(LauncherWidgetModel(countLabel = "99", hasUnread = true, signedIn = true), launcherWidgetModel(signedIn = true, unread = 99))
    }

    @Test
    fun countsAbove99_cap() {
        assertEquals(LauncherWidgetModel(countLabel = "99+", hasUnread = true, signedIn = true), launcherWidgetModel(signedIn = true, unread = 100))
    }

    @Test
    fun signedOut_promptsToSignIn_whateverIsStored() {
        // WO-V9: a signed-out tile prompts sign-in instead of showing stale tiles.
        assertEquals(LauncherWidgetModel(countLabel = null, hasUnread = false, signedIn = false), launcherWidgetModel(signedIn = false, unread = null))
        assertEquals(LauncherWidgetModel(countLabel = null, hasUnread = false, signedIn = false), launcherWidgetModel(signedIn = false, unread = 5))
    }
}
