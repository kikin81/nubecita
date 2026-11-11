package net.kikin.nubecita.core.posttranslation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.TranslateAutoOptIn
import net.kikin.nubecita.core.analytics.UserProperty
import net.kikin.nubecita.core.preferences.ReaderLanguages
import net.kikin.nubecita.core.preferences.TranslationPreferences
import net.kikin.nubecita.core.translation.LanguageIdentifier
import net.kikin.nubecita.core.translation.TranslationEngine
import net.kikin.nubecita.core.translation.TranslationModelRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The offer to translate a whole language automatically, after a chip tap. */
class PostTranslationAutoOfferTest {
    @Test
    fun `a language never offered before is offered`() =
        runTest {
            assertTrue(coordinator().shouldOfferAuto("ja"))
        }

    @Test
    fun `a language already translated automatically is not offered again`() =
        runTest {
            assertFalse(coordinator(auto = setOf("ja")).shouldOfferAuto("ja"))
        }

    @Test
    fun `a language already offered is never offered twice`() =
        runTest {
            // The offer follows a translate tap, so without this a reader who
            // reads one Japanese post a week is asked the same question every
            // week. Declining is an answer, not a deferral.
            assertFalse(coordinator(offered = setOf("ja")).shouldOfferAuto("ja"))
        }

    @Test
    fun `accepting adds the language and turns the master switch on`() =
        runTest {
            val prefs = prefs()
            coordinator(prefs = prefs).acceptAuto("ja")

            // Both, not just the list: the automatic pass gates on the switch,
            // so adding the language alone would change nothing visible and
            // read as the acceptance having been ignored.
            coVerify { prefs.setAutoTranslateLanguages(setOf("ja")) }
            coVerify { prefs.setAutoTranslateEnabled(true) }
        }

    @Test
    fun `accepting is reported, and declining is not`() =
        runTest {
            val accepted = RecordingAnalytics()
            coordinator(analytics = accepted).acceptAuto("ja")
            assertEquals(listOf(TranslateAutoOptIn("ja")), accepted.events)

            val declined = RecordingAnalytics()
            coordinator(analytics = declined).declineAuto("ja")
            assertEquals(emptyList<AnalyticsEvent>(), declined.events)
        }

    @Test
    fun `declining still remembers the language so it is not re-asked`() =
        runTest {
            val prefs = prefs()
            coordinator(prefs = prefs).declineAuto("ja")
            coVerify { prefs.setAutoTranslateOffered(setOf("ja")) }
        }

    private fun prefs(
        auto: Set<String> = emptySet(),
        offered: Set<String> = emptySet(),
    ) = mockk<TranslationPreferences>(relaxed = true) {
        every { autoTranslateEnabled } returns flowOf(false)
        every { autoTranslateLanguages } returns flowOf(auto)
        every { neverTranslateLanguages } returns flowOf(emptySet())
        every { autoTranslateOffered } returns flowOf(offered)
    }

    private fun coordinator(
        auto: Set<String> = emptySet(),
        offered: Set<String> = emptySet(),
        prefs: TranslationPreferences = prefs(auto, offered),
        analytics: AnalyticsClient = RecordingAnalytics(),
    ) = PostTranslationCoordinator(
        engine = mockk<TranslationEngine>(relaxed = true),
        identifier = mockk<LanguageIdentifier> { coEvery { identify(any()) } returns null },
        models = mockk<TranslationModelRepository>(relaxed = true),
        preferences = prefs,
        readerLanguages =
            mockk<ReaderLanguages> {
                every { languages } returns flowOf(listOf("en"))
                every { targetLanguage } returns flowOf("en")
            },
        analytics = analytics,
        cache = InMemoryTranslationCache(),
    )

    private class RecordingAnalytics : AnalyticsClient {
        val events = mutableListOf<AnalyticsEvent>()

        override fun log(event: AnalyticsEvent) {
            events += event
        }

        override fun setUserProperty(property: UserProperty) = Unit

        override fun logScreen(screen: AnalyticsScreen) = Unit
    }
}
