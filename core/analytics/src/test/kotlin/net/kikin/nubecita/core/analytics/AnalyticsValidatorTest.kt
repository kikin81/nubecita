package net.kikin.nubecita.core.analytics

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AnalyticsValidatorTest {
    @Test
    fun `all v1 events pass validation`() {
        val events =
            listOf(
                Login(),
                ViewFeed(FeedType.Following),
                ViewFeed(FeedType.Video),
                InteractPost(PostAction.Like, PostSurface.Feed),
                InteractPost(PostAction.Repost, PostSurface.PostDetail),
                CreatePost(hasMedia = true, isReply = true, isQuote = true, hasExternal = true),
                SearchPerform(SearchScope.Top, fromRecent = false),
                SearchPerform(SearchScope.Feeds, fromRecent = true),
            )
        events.forEach { event ->
            assertDoesNotThrow({ AnalyticsValidator.requireValid(event) }, "v1 event ${event.name}")
        }
    }

    @Test
    fun `all v1 user properties pass validation`() {
        val properties =
            listOf(
                Theme(ThemePreference.System),
                SelfHosted(true),
                NotificationsEnabled(false),
            )
        properties.forEach { property ->
            assertDoesNotThrow({ AnalyticsValidator.requireValid(property) }, "v1 property ${property.name}")
        }
    }

    @Test
    fun `a well-formed custom event name passes`() {
        assertDoesNotThrow { AnalyticsValidator.requireValidEventName("custom_event_42") }
    }

    @Test
    fun `uppercase event name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidEventName("ViewFeed")
        }
    }

    @Test
    fun `event name starting with a digit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidEventName("1st_event")
        }
    }

    @Test
    fun `event name starting with an underscore is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidEventName("_event")
        }
    }

    @Test
    fun `empty event name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidEventName("")
        }
    }

    @Test
    fun `event name over 40 chars is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidEventName("a".repeat(41))
        }
    }

    @Test
    fun `reserved firebase prefix on event name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidEventName("firebase_thing")
        }
    }

    @Test
    fun `reserved google prefix on event name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidEventName("google_thing")
        }
    }

    @Test
    fun `reserved ga prefix on event name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidEventName("ga_thing")
        }
    }

    @Test
    fun `reserved event name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidEventName("session_start")
        }
    }

    @Test
    fun `invalid param key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidParamName("BadKey")
        }
    }

    @Test
    fun `param key over 40 chars is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidParamName("a".repeat(41))
        }
    }

    @Test
    fun `reserved prefix on param key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidParamName("ga_param")
        }
    }

    @Test
    fun `well-formed user property name passes`() {
        assertDoesNotThrow { AnalyticsValidator.requireValidUserPropertyName("custom_property") }
    }

    @Test
    fun `user property name over 24 chars is rejected even though it fits the 40-char event cap`() {
        // 25 lowercase letters: a valid event name, but over the tighter user-property cap.
        val name = "abcdefghijklmnopqrstuvwxy"
        check(name.length == 25) { "fixture must be 25 chars, was ${name.length}" }
        assertDoesNotThrow { AnalyticsValidator.requireValidEventName(name) }
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidUserPropertyName(name)
        }
    }

    @Test
    fun `reserved prefix on user property name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsValidator.requireValidUserPropertyName("firebase_prop")
        }
    }
}
