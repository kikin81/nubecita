package net.kikin.nubecita.core.posttranslation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.TranslateBatch
import net.kikin.nubecita.core.analytics.TranslateError
import net.kikin.nubecita.core.analytics.TranslatePost
import net.kikin.nubecita.core.analytics.TranslationErrorReason
import net.kikin.nubecita.core.analytics.UserProperty
import net.kikin.nubecita.core.preferences.ReaderLanguages
import net.kikin.nubecita.core.preferences.TranslationPreferences
import net.kikin.nubecita.core.translation.LanguageIdentifier
import net.kikin.nubecita.core.translation.TranslationEngine
import net.kikin.nubecita.core.translation.TranslationModelRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostTranslationUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.TranslationModelState
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * The shared store: one answer per post for the whole app, kept from going
 * stale for as long as the process lives.
 *
 * Built over the real coordinator with controllable inputs, so these exercise
 * the decision path rather than a mock of it.
 */
internal class PostTranslationsTest {
    // ---- carried over from the feed's state holder ----------------------

    @Test
    fun `one post throwing does not stop the others translating`() =
        storeTest {
            store.onSettled(listOf(throwingPost(), japanesePost()))
            advanceUntilIdle()

            assertInstanceOf(PostTranslationUi.Shown::class.java, store.stateOf(japanesePost()))
        }

    @Test
    fun `a settle counts only the translations it performed`() =
        storeTest {
            // Two settles dispatched before either runs: the duplicate must
            // contribute nothing.
            store.onSettled(listOf(japanesePost()))
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()

            assertEquals(listOf(TranslateBatch(1, "ja")), analytics.events.filterIsInstance<TranslateBatch>())
        }

    @Test
    fun `a throw is reported once, not on every settle`() =
        storeTest {
            repeat(3) {
                store.onSettled(listOf(throwingPost()))
                advanceUntilIdle()
            }

            assertEquals(
                listOf(TranslateError(TranslationErrorReason.Unexpected, "unknown", "unknown")),
                analytics.events,
            )
        }

    @Test
    fun `show original is a toggle that sticks`() =
        storeTest {
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()

            store.toggleOriginal(japanesePost().id)
            assertEquals(PostTranslationUi.OriginalShown("ja"), store.stateOf(japanesePost()))

            store.toggleOriginal(japanesePost().id)
            assertInstanceOf(PostTranslationUi.Shown::class.java, store.stateOf(japanesePost()))
        }

    // ---- the cache --------------------------------------------------------

    @Test
    fun `a cached post is shown without the engine and is not counted as a translation`() =
        storeTest(cached = mapOf(("at://post/ja" to "en") to CACHED)) {
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()

            assertEquals(CACHED, store.stateOf(japanesePost()))
            coVerify(exactly = 0) { engine.translate(any(), any(), any()) }
            assertTrue(analytics.events.none { it is TranslateBatch })
        }

    @Test
    fun `a tap served from the cache still raises the offer`() =
        storeTest(auto = emptySet(), cached = mapOf(("at://post/ja" to "en") to CACHED)) {
            val source = store.translate(japanesePost())
            advanceUntilIdle()

            assertEquals("ja", source.await())
        }

    // ---- one answer, shared ---------------------------------------------

    @Test
    fun `a post already being translated is not translated twice`() =
        storeTest {
            // The feed's settle started it; the reader opens the post and post
            // detail settles over it before the engine has answered.
            val gate = CompletableDeferred<Unit>()
            coEvery { engine.translate(any(), any(), any()) } coAnswers {
                gate.await()
                Result.success("translated")
            }

            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()
            assertEquals(PostTranslationUi.InProgress("ja"), store.stateOf(japanesePost()))

            gate.complete(Unit)
            advanceUntilIdle()

            assertInstanceOf(PostTranslationUi.Shown::class.java, store.stateOf(japanesePost()))
            coVerify(exactly = 1) { engine.translate(any(), any(), any()) }
        }

    // ---- staying fresh for the whole process ------------------------------

    @Test
    fun `a change to the reader's languages clears everything and re-settles`() =
        storeTest {
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()
            val before = store.generation

            // The reader adds Japanese to never-translate.
            languages.value = listOf("en", "ja")
            advanceUntilIdle()

            assertNull(store.stateOf(japanesePost()))
            assertTrue(store.generation > before)
        }

    @Test
    fun `a translation in flight when the languages change is dropped, not written`() =
        storeTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { engine.translate(any(), any(), any()) } coAnswers {
                gate.await()
                Result.success("translated")
            }
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()

            // The device language changes while the engine is still working.
            languages.value = listOf("es")
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertNull(store.stateOf(japanesePost()), "an answer for the old target must not land")
        }

    @Test
    fun `an auto-list change drops offers and keeps translations`() =
        storeTest(auto = emptySet()) {
            store.translate(japanesePost())
            store.onSettled(listOf(koreanPost()))
            advanceUntilIdle()
            assertEquals(PostTranslationUi.Offered("ko"), store.stateOf(koreanPost()))
            val before = store.generation

            auto.value = setOf("ko")
            advanceUntilIdle()

            assertNull(store.stateOf(koreanPost()), "the offer goes, so the next settle translates it")
            assertInstanceOf(PostTranslationUi.Shown::class.java, store.stateOf(japanesePost()))
            assertTrue(store.generation > before)

            store.onSettled(listOf(koreanPost()))
            advanceUntilIdle()
            assertInstanceOf(PostTranslationUi.Shown::class.java, store.stateOf(koreanPost()))
        }

    @Test
    fun `a post waiting for Wi-Fi translates once its pack arrives`() =
        storeTest(downloaded = emptySet()) {
            modelStates.value = mapOf("ja" to TranslationModelState.WaitingForUnmeteredNetwork)
            val source = store.translate(japanesePost())
            advanceUntilIdle()
            assertEquals(PostTranslationUi.WaitingForWifi("ja"), store.stateOf(japanesePost()))
            assertNull(source.await())

            // The repository finishes the download on Wi-Fi.
            onDisk += "ja"
            modelStates.value = mapOf("ja" to TranslationModelState.Downloaded(1))
            advanceUntilIdle()
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()

            assertInstanceOf(PostTranslationUi.Shown::class.java, store.stateOf(japanesePost()))
            // Still the reader's one request: the tap was counted once.
            assertEquals(1, analytics.events.count { it is TranslatePost })
        }

    @Test
    fun `a failed post gets one fresh try when a surface appears`() =
        storeTest {
            coEvery { engine.translate(any(), any(), any()) } returns Result.failure(IllegalStateException("boom"))
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()
            coVerify(exactly = 1) { engine.translate(any(), any(), any()) }

            coEvery { engine.translate(any(), any(), any()) } returns Result.success("translated")
            store.onSurfaceEntered()
            store.onSettled(listOf(japanesePost()))
            advanceUntilIdle()

            assertInstanceOf(PostTranslationUi.Shown::class.java, store.stateOf(japanesePost()))
        }

    // ---- the bound ----------------------------------------------------------

    @Test
    fun `memory is bounded and an evicted translation comes back from the cache`() =
        storeTest {
            val first = japanesePost(id = "at://post/0")
            store.onSettled(listOf(first))
            advanceUntilIdle()
            store.toggleOriginal(first.id)

            // Push 500 newer translated posts through, one settle at a time.
            (1..500).forEach { index ->
                store.onSettled(listOf(japanesePost(id = "at://post/$index")))
                advanceUntilIdle()
            }
            assertNull(store.stateOf(first), "the oldest entry should have been evicted")

            store.onSettled(listOf(first))
            advanceUntilIdle()

            // Back from the cache, not the engine, and still showing the
            // original the reader chose: that choice is outside the bound.
            assertEquals(PostTranslationUi.OriginalShown("ja"), store.stateOf(first))
            coVerify(exactly = 501) { engine.translate(any(), any(), any()) }
        }

    // ---- harness ------------------------------------------------------------

    private class Harness(
        val store: PostTranslations,
        val engine: TranslationEngine,
        val analytics: RecordingAnalyticsClient,
        val languages: MutableStateFlow<List<String>>,
        val auto: MutableStateFlow<Set<String>>,
        val modelStates: MutableStateFlow<Map<String, TranslationModelState>>,
        val onDisk: MutableSet<String>,
        testScope: TestScope,
    ) : CoroutineScope by testScope {
        val testScope = testScope

        fun advanceUntilIdle() = testScope.advanceUntilIdle()
    }

    private fun storeTest(
        auto: Set<String> = setOf("ja"),
        downloaded: Set<String> = setOf("ja", "ko"),
        cached: Map<Pair<String, String>, PostTranslationUi.Shown> = emptyMap(),
        body: suspend Harness.() -> Unit,
    ) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // A SupervisorJob, as the application scope is in production; the
        // per-post containment is what's under test, not the scope's.
        val appScope = CoroutineScope(SupervisorJob() + dispatcher)
        val analytics = RecordingAnalyticsClient()
        val languages = MutableStateFlow(listOf("en"))
        val autoFlow = MutableStateFlow(auto)
        val modelStates = MutableStateFlow<Map<String, TranslationModelState>>(emptyMap())
        val onDisk = downloaded.toMutableSet()
        val engine =
            mockk<TranslationEngine> {
                every { supports(any(), any()) } returns true
                coEvery { translate(any(), any(), any()) } returns Result.success("translated")
            }
        val identifier =
            mockk<LanguageIdentifier> {
                coEvery { identify(any()) } returns null
                coEvery { identify(THROWING_TEXT) } throws IllegalStateException("boom")
            }
        val models =
            mockk<TranslationModelRepository>(relaxed = true) {
                coEvery { isDownloaded(any()) } answers { firstArg<String>() in onDisk }
                every { states } returns modelStates
            }
        val prefs =
            mockk<TranslationPreferences>(relaxed = true) {
                every { autoTranslateEnabled } returns MutableStateFlow(true)
                every { autoTranslateLanguages } returns autoFlow
                every { neverTranslateLanguages } returns MutableStateFlow(emptySet())
                every { autoTranslateOffered } returns MutableStateFlow(emptySet())
            }
        val reader =
            mockk<ReaderLanguages> {
                every { this@mockk.languages } returns languages
                every { targetLanguage } returns languages.map { it.first() }
            }
        val coordinator =
            PostTranslationCoordinator(
                engine,
                identifier,
                models,
                prefs,
                reader,
                analytics,
                InMemoryTranslationCache(cached),
            )
        val store = PostTranslations(coordinator, prefs, reader, models, appScope, dispatcher)
        advanceUntilIdle()
        try {
            Harness(store, engine, analytics, languages, autoFlow, modelStates, onDisk, this).body()
        } finally {
            appScope.cancel()
        }
    }

    private fun japanesePost(id: String = "at://post/ja") = post(id = id, text = "こんにちは", langs = listOf("ja"))

    private fun koreanPost() = post(id = "at://post/ko", text = "안녕하세요", langs = listOf("ko"))

    /** No declared language, so identification runs, and it throws. */
    private fun throwingPost() = post(id = "at://post/throws", text = THROWING_TEXT, langs = emptyList())

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
        const val THROWING_TEXT = "no declared language"
        val CACHED = PostTranslationUi.Shown("ja", "Good morning", persistentListOf())
    }
}
