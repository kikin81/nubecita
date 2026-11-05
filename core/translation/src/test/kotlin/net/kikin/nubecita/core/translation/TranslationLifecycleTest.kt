package net.kikin.nubecita.core.translation

import androidx.lifecycle.LifecycleOwner
import dagger.Lazy
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The background-release contract.
 *
 * Worth a test of its own because the failure it prevents is invisible in use:
 * nothing breaks if the models are never released, the process just sits in
 * the background holding hundreds of megabytes until the low-memory killer
 * takes it — and the reader experiences that as the app "restarting itself",
 * nowhere near translation.
 */
internal class TranslationLifecycleTest {
    @Test
    fun `backgrounding releases both models`() {
        val engine = FakeTranslationEngine()
        val identifier = FakeLanguageIdentifier()
        val subject = TranslationLifecycle(Lazy { engine }, Lazy { identifier })

        subject.onStop(mockk<LifecycleOwner>(relaxed = true))

        assertEquals(1, engine.releaseCount)
        assertEquals(1, identifier.releaseCount)
    }

    @Test
    fun `repeated backgrounding releases again`() {
        // Release has to be idempotent-but-repeatable: the app can background
        // and foreground many times in a session, reloading models in between.
        val engine = FakeTranslationEngine()
        val identifier = FakeLanguageIdentifier()
        val subject = TranslationLifecycle(Lazy { engine }, Lazy { identifier })
        val owner = mockk<LifecycleOwner>(relaxed = true)

        subject.onStop(owner)
        subject.onStop(owner)

        assertEquals(2, engine.releaseCount)
        assertEquals(2, identifier.releaseCount)
    }

    @Test
    fun `constructing the lifecycle does not construct the models`() {
        // The point of the Lazy wrappers: a reader who never translates must
        // never build an engine, and start() runs for everyone.
        var built = 0
        val lazyEngine =
            Lazy<TranslationEngine> {
                built++
                FakeTranslationEngine()
            }
        val lazyIdentifier =
            Lazy<LanguageIdentifier> {
                built++
                FakeLanguageIdentifier()
            }

        TranslationLifecycle(lazyEngine, lazyIdentifier)

        assertEquals(0, built)
    }
}

/** Minimal [Lazy] so the test does not need a Dagger component. */
private fun <T> Lazy(factory: () -> T): Lazy<T> =
    object : Lazy<T> {
        private val value by lazy(factory)

        override fun get(): T = value
    }
