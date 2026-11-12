package net.kikin.nubecita.wear.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LauncherWidgetModelTest {
    @Test
    fun neverSynced_showsNoCount() {
        assertEquals(LauncherWidgetModel(countLabel = null, hasUnread = false), launcherWidgetModel(null))
    }

    @Test
    fun zero_isNotUnread() {
        assertEquals(LauncherWidgetModel(countLabel = "0", hasUnread = false), launcherWidgetModel(0))
    }

    @Test
    fun counts_upTo99_showAsIs() {
        assertEquals(LauncherWidgetModel(countLabel = "1", hasUnread = true), launcherWidgetModel(1))
        assertEquals(LauncherWidgetModel(countLabel = "99", hasUnread = true), launcherWidgetModel(99))
    }

    @Test
    fun countsAbove99_cap() {
        assertEquals(LauncherWidgetModel(countLabel = "99+", hasUnread = true), launcherWidgetModel(100))
    }
}
