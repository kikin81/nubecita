package net.kikin.nubecita.core.posttranslation

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.TranslateError
import net.kikin.nubecita.core.analytics.TranslatePost
import net.kikin.nubecita.core.analytics.TranslationDownloadOutcome
import net.kikin.nubecita.core.analytics.TranslationErrorReason
import net.kikin.nubecita.core.analytics.TranslationModelDownload
import net.kikin.nubecita.core.analytics.TranslationModelReadiness
import net.kikin.nubecita.core.analytics.UserProperty
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
 * The decision order that governs whether a post is offered, translated, or
 * left alone.
 *
 * Most of these pin a case the obvious implementation gets wrong.
 */
internal class PostTranslationCoordinatorTest {
    /** What the last [resolve] call returned. */
    private var outcome: ResolveOutcome? = null

    @Test
    fun `a post in a language the reader reads is left alone`() =
        runTest {
            val states = resolve(post(langs = listOf("en")))

            assertTrue(states.isEmpty())
        }

    @Test
    fun `a region variant still counts as the reader's language`() =
        runTest {
            // `es-419` must match a reader who reads `es`. Treating them as
            // different languages would offer to translate posts the reader
            // understands perfectly well.
            val states = resolve(post(langs = listOf("es-419")), reader = listOf("es"))

            assertTrue(states.isEmpty())
        }

    @Test
    fun `a foreign post is offered, not translated, when it is not on the auto list`() =
        runTest {
            val states = resolve(post(langs = listOf("ja")))

            assertEquals(listOf(PostTranslationUi.Offered("ja")), states)
        }

    @Test
    fun `the offer appears with automatic translation off`() =
        runTest {
            // This is what makes the feature discoverable at zero cost: with
            // the master switch off, detection is a string comparison against
            // the declared language, and the identifier is never touched.
            val identifier = mockk<LanguageIdentifier>()
            val states = resolve(post(langs = listOf("ja")), enabled = false, identifier = identifier)

            assertEquals(listOf(PostTranslationUi.Offered("ja")), states)
        }

    @Test
    fun `an undeclared post is ignored while automatic translation is off`() =
        runTest {
            // No declaration and no inference allowed means nothing to say —
            // and, crucially, no identification model loaded.
            val identifier = mockk<LanguageIdentifier>()
            val states = resolve(post(langs = emptyList()), enabled = false, identifier = identifier)

            assertTrue(states.isEmpty())
        }

    @Test
    fun `an undeclared post falls back to identification when enabled`() =
        runTest {
            val identifier = mockk<LanguageIdentifier> { coEvery { identify(any()) } returns "ja" }

            val states = resolve(post(langs = emptyList()), enabled = true, identifier = identifier)

            assertEquals(listOf(PostTranslationUi.Offered("ja")), states)
        }

    @Test
    fun `a declared language wins over what identification would say`() =
        runTest {
            // The motivating post: declared `de`, but a German greeting
            // followed by nine English hashtags identifies as English. If
            // identification won, the reader would get no affordance at all.
            val identifier = mockk<LanguageIdentifier> { coEvery { identify(any()) } returns "en" }

            val states = resolve(post(langs = listOf("de")), enabled = true, identifier = identifier)

            assertEquals(listOf(PostTranslationUi.Offered("de")), states)
        }

    @Test
    fun `a post declaring the reader's language among others is not foreign`() =
        runTest {
            val states = resolve(post(langs = listOf("ja", "en")))

            assertTrue(states.isEmpty())
        }

    @Test
    fun `an auto-listed language translates on its own`() =
        runTest {
            val states = resolve(post(langs = listOf("ja")), auto = setOf("ja"), downloaded = setOf("ja"))

            assertEquals(PostTranslationUi.InProgress("ja"), states.first())
            val shown = assertInstanceOf(PostTranslationUi.Shown::class.java, states.last())
            assertEquals("translated", shown.text)
        }

    @Test
    fun `tapping the chip translates a language that is not auto-listed`() =
        runTest {
            val states = resolve(post(langs = listOf("ja")), downloaded = setOf("ja"), userInitiated = true)

            assertInstanceOf(PostTranslationUi.Shown::class.java, states.last())
        }

    @Test
    fun `a missing pack on a metered connection parks in waiting-for-wifi`() =
        runTest {
            // Not an error and not silence: a 62MB download is not ours to
            // start on someone's cellular plan, and a chip that does nothing
            // when tapped reads as a broken feature.
            val states =
                resolve(
                    post(langs = listOf("ja")),
                    downloaded = emptySet(),
                    metered = true,
                    userInitiated = true,
                )

            assertEquals(PostTranslationUi.WaitingForWifi("ja"), states.last())
        }

    @Test
    fun `a failed translation falls back to the offer, never to an error`() =
        runTest {
            // Showing the original is always a correct thing to show.
            val engine =
                mockk<TranslationEngine> {
                    every { supports(any(), any()) } returns true
                    coEvery { translate(any(), any(), any()) } returns Result.failure(IllegalStateException("boom"))
                }

            val states = resolve(post(langs = listOf("ja")), downloaded = setOf("ja"), userInitiated = true, engine = engine)

            assertEquals(PostTranslationUi.Offered("ja"), states.last())
        }

    @Test
    fun `an unsupported pair says nothing at all`() =
        runTest {
            val engine = mockk<TranslationEngine> { every { supports(any(), any()) } returns false }

            val states = resolve(post(langs = listOf("ja")), engine = engine)

            assertTrue(states.isEmpty())
        }

    @Test
    fun `a chip tap is logged, and the automatic pass is not`() =
        runTest {
            // The whole point of the split: one reader with auto-translate on
            // must not be able to out-count a hundred readers who tapped.
            val tapped = RecordingAnalyticsClient()
            resolve(post(langs = listOf("ja")), downloaded = setOf("ja"), userInitiated = true, analytics = tapped)

            val automatic = RecordingAnalyticsClient()
            resolve(post(langs = listOf("ja")), auto = setOf("ja"), downloaded = setOf("ja"), analytics = automatic)

            assertEquals(
                listOf(TranslatePost("ja", TranslationModelReadiness.Ready, "en")),
                tapped.events.filterIsInstance<TranslatePost>(),
            )
            assertTrue(automatic.events.filterIsInstance<TranslatePost>().isEmpty())
        }

    @Test
    fun `a tap on a metered connection is logged at once, with the deferred download`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            resolve(post(langs = listOf("ja")), metered = true, userInitiated = true, analytics = analytics)

            assertEquals(
                listOf(
                    TranslatePost("ja", TranslationModelReadiness.NeedsDownload, "en"),
                    TranslationModelDownload(TranslationDownloadOutcome.DeferredWifi, "ja", "en"),
                ),
                analytics.events,
            )
        }

    @Test
    fun `the tap is logged while the pack is still downloading`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            val downloadStarted = CompletableDeferred<Unit>()
            val models =
                mockk<TranslationModelRepository>(relaxed = true) {
                    coEvery { isDownloaded(any()) } returns false
                    // Stands in for a 62MB transfer: `request` suspends and
                    // never returns while the test looks at what was logged.
                    coEvery { request(any()) } coAnswers {
                        downloadStarted.complete(Unit)
                        awaitCancellation()
                    }
                    every { states } returns flowOf(emptyMap())
                    every { this@mockk.downloaded } returns flowOf(emptyList())
                }
            val subject =
                PostTranslationCoordinator(
                    engine =
                        mockk {
                            every { supports(any(), any()) } returns true
                            coEvery { translate(any(), any(), any()) } returns Result.success("translated")
                        },
                    identifier = mockk { coEvery { identify(any()) } returns null },
                    models = models,
                    preferences =
                        mockk {
                            every { autoTranslateEnabled } returns flowOf(true)
                            every { autoTranslateLanguages } returns flowOf(emptySet())
                            every { neverTranslateLanguages } returns flowOf(emptySet())
                        },
                    readerLanguages =
                        mockk {
                            every { languages } returns flowOf(listOf("en"))
                            every { targetLanguage } returns flowOf("en")
                        },
                    analytics = analytics,
                    cache = InMemoryTranslationCache(),
                )

            val job = launch { subject.resolve(post(langs = listOf("ja")), userInitiated = true) {} }
            downloadStarted.await()

            // The reader has tapped and the pack is mid-transfer. Logging the
            // tap after `request` returned lost this entirely whenever they
            // backed out before it finished.
            assertEquals(listOf(TranslatePost("ja", TranslationModelReadiness.NeedsDownload, "en")), analytics.events)
            job.cancel()
        }

    @Test
    fun `a download that never lands is both a failed download and an error`() =
        runTest {
            // `downloaded` stays empty, so the pack is still absent after the
            // request. On device this is silent — the chip just stops doing
            // anything — which is the whole reason the event exists.
            val analytics = RecordingAnalyticsClient()
            resolve(post(langs = listOf("ja")), userInitiated = true, analytics = analytics)

            assertEquals(
                listOf(
                    TranslatePost("ja", TranslationModelReadiness.NeedsDownload, "en"),
                    TranslationModelDownload(TranslationDownloadOutcome.Failed, "ja", "en"),
                    TranslateError(TranslationErrorReason.ModelUnavailable, "ja", "en"),
                ),
                analytics.events,
            )
        }

    @Test
    fun `an engine failure is logged as a translate failure`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            resolve(
                post(langs = listOf("ja")),
                downloaded = setOf("ja"),
                userInitiated = true,
                analytics = analytics,
                engine =
                    mockk {
                        every { supports(any(), any()) } returns true
                        coEvery { translate(any(), any(), any()) } returns Result.failure(IllegalStateException("boom"))
                    },
            )

            assertEquals(
                listOf(TranslateError(TranslationErrorReason.TranslateFailed, "ja", "en")),
                analytics.events.filterIsInstance<TranslateError>(),
            )
        }

    @Test
    fun `an empty translation of a non-empty post is an error, not a success`() =
        runTest {
            // Nothing throws here. Without this branch the body would simply
            // go blank and the telemetry would call it a success.
            val analytics = RecordingAnalyticsClient()
            val states =
                resolve(
                    post(langs = listOf("ja")),
                    downloaded = setOf("ja"),
                    userInitiated = true,
                    analytics = analytics,
                    engine =
                        mockk {
                            every { supports(any(), any()) } returns true
                            coEvery { translate(any(), any(), any()) } returns Result.success("   ")
                        },
                )

            assertEquals(
                listOf(TranslateError(TranslationErrorReason.EmptyResult, "ja", "en")),
                analytics.events.filterIsInstance<TranslateError>(),
            )
            assertEquals(PostTranslationUi.Offered("ja"), states.last())
        }

    /**
     * Miguel's bug: English is ML Kit's pivot, so it reads as present the moment
     * any pack is, and the tap went ahead without the reader's own pack.
     */
    @Test
    fun `a Spanish reader's pack is fetched for an English post`() =
        runTest {
            val requested = mutableListOf<String>()
            val states =
                resolve(
                    post(langs = listOf("en")),
                    reader = listOf("es"),
                    target = "es",
                    userInitiated = true,
                    downloadsLand = true,
                    requested = requested,
                )

            assertEquals(listOf("es"), requested)
            assertTrue(states.last() is PostTranslationUi.Shown)
        }

    @Test
    fun `a pack for the post alone isn't ready for a reader of another language`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            resolve(
                post(langs = listOf("ja")),
                reader = listOf("es"),
                target = "es",
                downloaded = setOf("ja"),
                userInitiated = true,
                analytics = analytics,
            )

            val tap = analytics.events.filterIsInstance<TranslatePost>().single()
            assertEquals(TranslationModelReadiness.NeedsDownload, tap.modelState)
        }

    @Test
    fun `English needs no pack of its own`() =
        runTest {
            val requested = mutableListOf<String>()
            val states =
                resolve(
                    post(langs = listOf("ja")),
                    downloaded = setOf("ja"),
                    userInitiated = true,
                    requested = requested,
                )

            assertTrue(requested.isEmpty())
            assertTrue(states.last() is PostTranslationUi.Shown)
        }

    @Test
    fun `a reader's pack waiting for Wi-Fi parks the post there`() =
        runTest {
            val states =
                resolve(
                    post(langs = listOf("ja")),
                    reader = listOf("es"),
                    target = "es",
                    downloaded = setOf("ja"),
                    metered = true,
                    userInitiated = true,
                )

            assertTrue(states.last() is PostTranslationUi.WaitingForWifi)
        }

    /** Identification can return a region or script; packs and their states are keyed on the bare language. */
    @Test
    fun `an identified tag with a region asks for the bare language's pack`() =
        runTest {
            val requested = mutableListOf<String>()
            resolve(
                post(langs = emptyList()),
                identifier = mockk { coEvery { identify(any()) } returns "ja-JP" },
                userInitiated = true,
                requested = requested,
            )

            assertEquals(listOf("ja"), requested)
        }

    /** A 45MB pack takes a while; a chip that shows nothing until then reads as broken. */
    @Test
    fun `a missing pack shows that it is downloading`() =
        runTest {
            val states =
                resolve(
                    post(langs = listOf("ja")),
                    reader = listOf("es"),
                    target = "es",
                    downloaded = setOf("ja"),
                    userInitiated = true,
                    downloadsLand = true,
                )

            assertEquals(
                listOf(PostTranslationUi.Downloading("ja"), PostTranslationUi.InProgress("ja")),
                states.take(2),
            )
            assertTrue(states.last() is PostTranslationUi.Shown)
        }

    @Test
    fun `a pack already on disk goes straight to translating`() =
        runTest {
            val states = resolve(post(langs = listOf("ja")), downloaded = setOf("ja"), userInitiated = true)

            assertTrue(states.none { it is PostTranslationUi.Downloading })
        }

    /** Without it, a failure for one reader's language looks like everyone's. */
    @Test
    fun `the events carry the reader's language`() =
        runTest {
            val analytics = RecordingAnalyticsClient()
            resolve(
                post(langs = listOf("en")),
                reader = listOf("es"),
                target = "es",
                userInitiated = true,
                analytics = analytics,
            )

            assertEquals(
                "es",
                analytics.events
                    .filterIsInstance<TranslatePost>()
                    .single()
                    .targetLanguage,
            )
            assertEquals(
                "es",
                analytics.events
                    .filterIsInstance<TranslationModelDownload>()
                    .single()
                    .targetLanguage,
            )
            assertEquals(
                "es",
                analytics.events
                    .filterIsInstance<TranslateError>()
                    .single()
                    .targetLanguage,
            )
        }

    @Test
    fun `a post left alone logs nothing at all`() =
        runTest {
            // A reader scrolling their own language past the detection pass
            // must not generate one event per post.
            val analytics = RecordingAnalyticsClient()
            resolve(post(langs = listOf("en")), analytics = analytics)

            assertTrue(analytics.events.isEmpty())
        }

    // ---- the shared cache ------------------------------------------------

    @Test
    fun `a cached translation is shown at once, with no shimmer and no engine call`() =
        runTest {
            // Translated on another surface, or before a restart. Nothing is
            // being worked on, so the card must not shimmer, and the language
            // need not be on the auto list: a translation follows its post.
            val cached = PostTranslationUi.Shown("ja", "Good morning", persistentListOf())
            val engine = engineThatMustNotRun()

            val states =
                resolve(
                    post(langs = listOf("ja")),
                    cache = InMemoryTranslationCache(mapOf(("at://post/1" to "en") to cached)),
                    engine = engine,
                )

            assertEquals(listOf(cached), states)
            assertEquals(ResolveOutcome.Cached("ja"), outcome)
        }

    @Test
    fun `a cached translation into another target language is not served`() =
        runTest {
            // The reader changed their device language: the old translation
            // reads fine in *a* language, which is exactly why it must not show.
            val cached = PostTranslationUi.Shown("ja", "Buenos días", persistentListOf())

            val states =
                resolve(
                    post(langs = listOf("ja")),
                    cache = InMemoryTranslationCache(mapOf(("at://post/1" to "es") to cached)),
                )

            assertEquals(listOf(PostTranslationUi.Offered("ja")), states)
        }

    @Test
    fun `a post the reader reads shows nothing even if a translation of it is cached`() =
        runTest {
            // The reader added Japanese to never-translate after this post was
            // translated. Detection runs first, so the cache can't bring it back.
            val cached = PostTranslationUi.Shown("ja", "Good morning", persistentListOf())
            val cache = InMemoryTranslationCache(mapOf(("at://post/1" to "en") to cached))

            val states = resolve(post(langs = listOf("ja")), reader = listOf("en", "ja"), cache = cache)

            assertTrue(states.isEmpty())
            assertEquals(0, cache.reads)
            assertEquals(ResolveOutcome.Settled, outcome)
        }

    @Test
    fun `a translation is written to the cache once, as shown`() =
        runTest {
            val cache = InMemoryTranslationCache()

            val states = resolve(post(langs = listOf("ja")), auto = setOf("ja"), downloaded = setOf("ja"), cache = cache)

            val shown = assertInstanceOf(PostTranslationUi.Shown::class.java, states.last())
            assertEquals(listOf(Triple("at://post/1", "en", shown)), cache.writes)
            assertEquals(ResolveOutcome.Translated("ja"), outcome)
        }

    @Test
    fun `a failed translation is reported as a failure and cached nowhere`() =
        runTest {
            val cache = InMemoryTranslationCache()
            val engine =
                mockk<TranslationEngine> {
                    every { supports(any(), any()) } returns true
                    coEvery { translate(any(), any(), any()) } returns Result.failure(IllegalStateException("boom"))
                }

            resolve(post(langs = listOf("ja")), downloaded = setOf("ja"), userInitiated = true, engine = engine, cache = cache)

            assertEquals(ResolveOutcome.Failed, outcome)
            assertTrue(cache.writes.isEmpty())
        }

    @Test
    fun `an offer is an ordinary outcome, not a failure`() =
        runTest {
            resolve(post(langs = listOf("ja")))

            assertEquals(ResolveOutcome.Settled, outcome)
        }

    @Test
    fun `re-resolving a reader's request is not counted as a second tap`() =
        runTest {
            // A post the reader asked for is resolved again when its pack
            // lands. It is still their request, so it translates, but the tap
            // was already counted.
            val analytics = RecordingAnalyticsClient()

            val states =
                resolve(
                    post(langs = listOf("ja")),
                    downloaded = setOf("ja"),
                    userInitiated = true,
                    countTap = false,
                    analytics = analytics,
                )

            assertInstanceOf(PostTranslationUi.Shown::class.java, states.last())
            assertTrue(analytics.events.none { it is TranslatePost })
        }

    private fun engineThatMustNotRun(): TranslationEngine =
        mockk {
            every { supports(any(), any()) } throws AssertionError("the engine must not be consulted")
            coEvery { translate(any(), any(), any()) } throws AssertionError("the engine must not run")
        }

    private suspend fun resolve(
        post: PostUi,
        reader: List<String> = listOf("en"),
        target: String = "en",
        enabled: Boolean = true,
        auto: Set<String> = emptySet(),
        downloaded: Set<String> = emptySet(),
        metered: Boolean = false,
        userInitiated: Boolean = false,
        countTap: Boolean = userInitiated,
        cache: InMemoryTranslationCache = InMemoryTranslationCache(),
        downloadsLand: Boolean = false,
        requested: MutableList<String> = mutableListOf(),
        analytics: RecordingAnalyticsClient = RecordingAnalyticsClient(),
        identifier: LanguageIdentifier = mockk { coEvery { identify(any()) } returns null },
        engine: TranslationEngine =
            mockk {
                every { supports(any(), any()) } returns true
                coEvery { translate(any(), any(), any()) } returns Result.success("translated")
            },
    ): List<PostTranslationUi> {
        val models =
            mockk<TranslationModelRepository>(relaxed = true) {
                val onDisk = downloaded.toMutableSet()
                coEvery { isDownloaded(any()) } answers { firstArg<String>() in onDisk }
                coEvery { request(any()) } answers {
                    requested += firstArg<String>()
                    if (downloadsLand) onDisk += firstArg<String>()
                }
                every { states } returns
                    flowOf(
                        if (metered) {
                            listOf("ja", target).associateWith { TranslationModelState.WaitingForUnmeteredNetwork }
                        } else {
                            emptyMap()
                        },
                    )
                every { this@mockk.downloaded } returns
                    flowOf(downloaded.map { DownloadedLanguage(it, 1) })
            }
        val prefs =
            mockk<TranslationPreferences> {
                every { autoTranslateEnabled } returns flowOf(enabled)
                every { autoTranslateLanguages } returns flowOf(auto)
                every { neverTranslateLanguages } returns flowOf(emptySet())
            }
        val languages =
            mockk<ReaderLanguages> {
                every { this@mockk.languages } returns flowOf(reader)
                every { targetLanguage } returns flowOf(target)
            }
        val subject = PostTranslationCoordinator(engine, identifier, models, prefs, languages, analytics, cache)

        val seen = mutableListOf<PostTranslationUi>()
        outcome = subject.resolve(post, userInitiated, countTap) { seen += it }
        return seen
    }

    private fun post(langs: List<String>) =
        PostUi(
            id = "at://post/1",
            cid = "bafy",
            author = AuthorUi("did:plc:a", "a.bsky.social", "A", null),
            createdAt = Instant.fromEpochSeconds(0),
            text = "こんにちは",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
            langs = langs.toImmutableList(),
        )

    /**
     * Records what was logged, in order.
     *
     * A fake rather than a MockK relaxed mock because these assertions are
     * about the *sequence* a single resolve produces — a tap followed by a
     * download outcome followed by an error — and a verify-per-call reads far
     * worse than comparing one list.
     */
    private class RecordingAnalyticsClient : AnalyticsClient {
        val events = mutableListOf<AnalyticsEvent>()

        override fun log(event: AnalyticsEvent) {
            events += event
        }

        override fun setUserProperty(property: UserProperty) = Unit

        override fun logScreen(screen: AnalyticsScreen) = Unit
    }
}
