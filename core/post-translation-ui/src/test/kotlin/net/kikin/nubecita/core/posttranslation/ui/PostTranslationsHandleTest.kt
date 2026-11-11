package net.kikin.nubecita.core.posttranslation.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.posttranslation.PostTranslations
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * The screen's half of translation: the auto-translate offer.
 *
 * The scope is deliberately `CoroutineScope(Job() + dispatcher)`, a plain
 * `Job`, which is what `rememberCoroutineScope` hands a screen. Under a
 * `SupervisorJob` a failure would be contained for free and the containment
 * tests here would pass whether or not the guard exists.
 */
internal class PostTranslationsHandleTest {
    @Test
    fun `the offer appears only once the translation has landed`() =
        runTest {
            val landed = CompletableDeferred<String?>()
            val store = store(translated = landed)
            val handle = StorePostTranslationsHandle(store, CoroutineScope(Job() + StandardTestDispatcher(testScheduler)))

            handle.translate(post())
            advanceUntilIdle()
            assertNull(handle.autoOffer, "no offer before the reader has seen a translation")

            landed.complete("ja")
            advanceUntilIdle()
            assertEquals("ja", handle.autoOffer)
        }

    @Test
    fun `a language already offered is not offered again`() =
        runTest {
            val store = store(translated = CompletableDeferred("ja"), shouldOffer = false)
            val handle = StorePostTranslationsHandle(store, CoroutineScope(Job() + StandardTestDispatcher(testScheduler)))

            handle.translate(post())
            advanceUntilIdle()

            assertNull(handle.autoOffer)
        }

    @Test
    fun `a post that wasn't translated raises no offer`() =
        runTest {
            val store = store(translated = CompletableDeferred(null))
            val handle = StorePostTranslationsHandle(store, CoroutineScope(Job() + StandardTestDispatcher(testScheduler)))

            handle.translate(post())
            advanceUntilIdle()

            assertNull(handle.autoOffer)
        }

    @Test
    fun `accepting clears the offer and records the answer`() =
        runTest {
            val store = store(translated = CompletableDeferred("ja"))
            val handle = StorePostTranslationsHandle(store, CoroutineScope(Job() + StandardTestDispatcher(testScheduler)))
            handle.translate(post())
            advanceUntilIdle()

            handle.acceptAutoOffer()
            advanceUntilIdle()

            assertNull(handle.autoOffer)
            coVerify { store.acceptAuto("ja") }
        }

    @Test
    fun `a failed preference write does not take the screen down`() =
        runTest {
            val store = store(translated = CompletableDeferred("ja"))
            coEvery { store.declineAuto(any()) } throws IllegalStateException("datastore down")
            val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val handle = StorePostTranslationsHandle(store, scope)
            handle.translate(post())
            advanceUntilIdle()

            handle.declineAutoOffer()
            advanceUntilIdle()

            // On a plain-Job scope an escaping throw cancels every later chip
            // tap on the screen: the reader would lose translation entirely
            // for declining an offer.
            assertTrue(scope.isActive, "a failed preference write must not cancel the screen's scope")
        }

    @Test
    fun `a failed offer check does not take the screen down`() =
        runTest {
            val store = store(translated = CompletableDeferred("ja"))
            coEvery { store.shouldOfferAuto(any()) } throws IllegalStateException("datastore down")
            val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val handle = StorePostTranslationsHandle(store, scope)

            handle.translate(post())
            advanceUntilIdle()

            assertTrue(scope.isActive)
            assertNull(handle.autoOffer)
        }

    private fun store(
        translated: CompletableDeferred<String?>,
        shouldOffer: Boolean = true,
    ): PostTranslations =
        mockk(relaxed = true) {
            every { translate(any()) } returns translated
            coEvery { shouldOfferAuto(any()) } returns shouldOffer
        }

    private fun post() =
        PostUi(
            id = "at://post/ja",
            cid = "bafy",
            author = AuthorUi("did:plc:a", "a.bsky.social", "A", null),
            createdAt = Instant.fromEpochSeconds(0),
            text = "こんにちは",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
