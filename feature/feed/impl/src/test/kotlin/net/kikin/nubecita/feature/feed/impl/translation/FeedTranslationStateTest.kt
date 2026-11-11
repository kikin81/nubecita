package net.kikin.nubecita.feature.feed.impl.translation

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.TranslateBatch
import net.kikin.nubecita.core.analytics.TranslateError
import net.kikin.nubecita.core.analytics.TranslationErrorReason
import net.kikin.nubecita.core.analytics.UserProperty
import net.kikin.nubecita.core.posttranslation.PostTranslationCoordinator
import net.kikin.nubecita.core.preferences.ReaderLanguages
import net.kikin.nubecita.core.preferences.TranslationPreferences
import net.kikin.nubecita.core.translation.LanguageIdentifier
import net.kikin.nubecita.core.translation.TranslationEngine
import net.kikin.nubecita.core.translation.TranslationModelRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.DownloadedLanguage
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostTranslationUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.TranslationModelState
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Blast-radius tests for a throw inside resolution.
 *
 * The scope these build is deliberately `CoroutineScope(Job() + dispatcher)` —
 * a *plain* `Job`, which is what `rememberCoroutineScope` actually hands the
 * screen (`createCompositionCoroutineScope` → `RememberedCoroutineScope`,
 * whose context is built with `Job(parentContext[Job])`). Under a
 * `SupervisorJob` — or under `runTest`'s own scope — a sibling failure is
 * contained for free and every test here would pass whether or not the guard
 * exists.
 */
class FeedTranslationStateTest {
    @Test
    fun `one post throwing does not stop the others translating`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val state = FeedTranslationState(coordinator(analytics), scope)

            state.onSettled(listOf(unlabelledPost(), japanesePost()))
            advanceUntilIdle()

            // The unguarded throw used to cancel the shared scope, so this
            // assertion is the whole point: the second post still resolved.
            assertInstanceOf(
                PostTranslationUi.Shown::class.java,
                state.forPost(japanesePost()),
                "the second post should still translate after the first threw",
            )
            assertTrue(scope.isActive, "the screen's translation scope should survive one failure")
        }

    @Test
    fun `a settle counts only the translations it performed`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val state = FeedTranslationState(coordinator(analytics), scope)

            // Two settles over the same post, the second dispatched before the
            // first has run — a page load under a stationary reader. The
            // second's duplicate request returns at once through the inFlight
            // guard and must contribute nothing.
            state.onSettled(listOf(japanesePost()))
            state.onSettled(listOf(japanesePost()))
            advanceUntilIdle()

            // Exactly one batch, of one. Counting by re-reading the state map
            // after joining instead reported this post twice, because by then
            // the first settle had stored its result for the second to find.
            assertEquals(
                listOf(TranslateBatch(translatedCount = 1, sourceLanguage = "ja")),
                analytics.events.filterIsInstance<TranslateBatch>(),
            )
        }

    @Test
    fun `a failed preference write does not take the screen down`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            // `ja` is absent from the auto list and unoffered, so a tap raises
            // the offer; the write that records the answer then throws.
            val state =
                FeedTranslationState(
                    coordinator(analytics, auto = emptySet(), offeredWriteFails = true),
                    scope,
                )

            state.translate(japanesePost())
            advanceUntilIdle()
            assertEquals("ja", state.autoOffer, "the tap should have raised the offer")

            state.declineAutoOffer()
            advanceUntilIdle()

            // A DataStore write can fail. On a plain-Job scope an escaping
            // throw cancels every later translation on the screen, so the
            // reader would lose translation entirely for declining an offer.
            assertTrue(scope.isActive, "a failed preference write must not cancel the screen's scope")
        }

    @Test
    fun `a throw is reported rather than swallowed`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))

            FeedTranslationState(coordinator(analytics), scope).onSettled(listOf(unlabelledPost()))
            advanceUntilIdle()

            // Without this the failure is invisible twice over: nothing on the
            // device says so, and translate_post never fires either — so the
            // dashboard reads a broken feature as an unused one.
            assertEquals(
                listOf(TranslateError(TranslationErrorReason.Unexpected, "unknown", "unknown")),
                analytics.events,
            )
        }

    @Test
    fun `a post that threw is not retried on every later settle`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val state = FeedTranslationState(coordinator(analytics), scope)

            repeat(3) {
                state.onSettled(listOf(unlabelledPost()))
                advanceUntilIdle()
            }

            // A persistent failure would otherwise log once per settle, which
            // for a stationary reader is unbounded.
            assertEquals(1, analytics.events.size, "a failing post should be reported once")
        }

    /** Resolves `ja` happily; throws on the post with no declared language. */
    private fun coordinator(
        analytics: RecordingAnalyticsClient,
        auto: Set<String> = setOf("ja"),
        offeredWriteFails: Boolean = false,
    ): PostTranslationCoordinator {
        val identifier =
            mockk<LanguageIdentifier> {
                // Generic stub first: MockK resolves to the LAST matching
                // declaration, so declaring `any()` afterwards would shadow
                // the specific one and nothing would throw — which passes the
                // scope-survival test vacuously.
                coEvery { identify(any()) } returns null
                coEvery { identify(UNLABELLED_TEXT) } throws IllegalStateException("boom")
            }
        val engine =
            mockk<TranslationEngine> {
                every { supports(any(), any()) } returns true
                coEvery { translate(any(), any(), any()) } returns Result.success("translated")
            }
        val models =
            mockk<TranslationModelRepository>(relaxed = true) {
                coEvery { isDownloaded(any()) } returns true
                every { states } returns flowOf(emptyMap<String, TranslationModelState>())
                every { downloaded } returns flowOf(listOf(DownloadedLanguage("ja", 1)))
            }
        val prefs =
            mockk<TranslationPreferences>(relaxed = true) {
                every { autoTranslateEnabled } returns flowOf(true)
                every { autoTranslateLanguages } returns flowOf(auto)
                every { neverTranslateLanguages } returns flowOf(emptySet())
                every { autoTranslateOffered } returns flowOf(emptySet())
                if (offeredWriteFails) {
                    coEvery { setAutoTranslateOffered(any()) } throws IllegalStateException("datastore down")
                }
            }
        val languages =
            mockk<ReaderLanguages> {
                every { this@mockk.languages } returns flowOf(listOf("en"))
                every { targetLanguage } returns flowOf("en")
            }
        return PostTranslationCoordinator(engine, identifier, models, prefs, languages, analytics, EmptyTranslationCache)
    }

    private fun unlabelledPost() = post(id = "at://post/throws", text = UNLABELLED_TEXT, langs = emptyList())

    private fun japanesePost() = post(id = "at://post/ja", text = "こんにちは", langs = listOf("ja"))

    private fun post(
        id: String,
        text: String,
        langs: List<String>,
    ) = PostUi(
        id = id,
        cid = "bafy",
        author = AuthorUi("did:plc:a", "a.bsky.social", "A", null),
        createdAt = Instant.fromEpochSeconds(0),
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(),
        viewer = ViewerStateUi(),
        repostedBy = null,
        langs = langs.toImmutableList(),
    )

    private class RecordingAnalyticsClient : AnalyticsClient {
        val events = mutableListOf<AnalyticsEvent>()

        override fun log(event: AnalyticsEvent) {
            events += event
        }

        override fun setUserProperty(property: UserProperty) = Unit

        override fun logScreen(screen: AnalyticsScreen) = Unit
    }

    private companion object {
        const val UNLABELLED_TEXT = "no declared language"
    }
}
