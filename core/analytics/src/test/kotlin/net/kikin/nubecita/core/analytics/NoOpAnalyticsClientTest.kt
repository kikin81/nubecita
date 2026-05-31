package net.kikin.nubecita.core.analytics

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class NoOpAnalyticsClientTest {
    private val client: AnalyticsClient = NoOpAnalyticsClient()

    @Test
    fun `log swallows every event without throwing`() {
        assertDoesNotThrow {
            client.log(Login())
            client.log(InteractPost(PostAction.Like, PostSurface.Feed))
        }
    }

    @Test
    fun `setUserProperty is inert`() {
        assertDoesNotThrow {
            client.setUserProperty(Theme(ThemePreference.System))
        }
    }

    @Test
    fun `logScreen is inert`() {
        assertDoesNotThrow {
            client.logScreen(AnalyticsScreen.Feed)
        }
    }
}
