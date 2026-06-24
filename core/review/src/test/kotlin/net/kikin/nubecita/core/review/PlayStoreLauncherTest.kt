package net.kikin.nubecita.core.review

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayStoreLauncherTest {
    @Test
    fun `marketUri targets the release package`() {
        assertEquals(
            "market://details?id=net.kikin.nubecita",
            PlayStoreLauncher.marketUri(),
        )
    }

    @Test
    fun `webUrl targets the release package`() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=net.kikin.nubecita",
            PlayStoreLauncher.webUrl(),
        )
    }
}
