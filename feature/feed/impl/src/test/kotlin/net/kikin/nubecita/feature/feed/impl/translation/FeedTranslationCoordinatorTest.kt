package net.kikin.nubecita.feature.feed.impl.translation

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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
internal class FeedTranslationCoordinatorTest {
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

    private suspend fun resolve(
        post: PostUi,
        reader: List<String> = listOf("en"),
        target: String = "en",
        enabled: Boolean = true,
        auto: Set<String> = emptySet(),
        downloaded: Set<String> = emptySet(),
        metered: Boolean = false,
        userInitiated: Boolean = false,
        identifier: LanguageIdentifier = mockk { coEvery { identify(any()) } returns null },
        engine: TranslationEngine =
            mockk {
                every { supports(any(), any()) } returns true
                coEvery { translate(any(), any(), any()) } returns Result.success("translated")
            },
    ): List<PostTranslationUi> {
        val models =
            mockk<TranslationModelRepository>(relaxed = true) {
                coEvery { isDownloaded(any()) } answers { firstArg<String>() in downloaded }
                every { states } returns
                    flowOf(
                        if (metered) {
                            mapOf("ja" to TranslationModelState.WaitingForUnmeteredNetwork)
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
        val subject = FeedTranslationCoordinator(engine, identifier, models, prefs, languages)

        val seen = mutableListOf<PostTranslationUi>()
        subject.resolve(post, userInitiated) { seen += it }
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
}
