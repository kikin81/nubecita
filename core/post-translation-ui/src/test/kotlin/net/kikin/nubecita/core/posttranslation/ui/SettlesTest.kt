package net.kikin.nubecita.core.posttranslation.ui

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** When a list counts as settled over something to translate. */
internal class SettlesTest {
    @Test
    fun `nothing is dispatched while scrolling or before anything is laid out`() =
        runTest {
            val signals = MutableSharedFlow<SettleSignal>()

            settles(signals).test {
                signals.emit(signal(scrolling = false, count = 0))
                signals.emit(signal(scrolling = true, count = 5))
                expectNoEvents()

                signals.emit(signal(scrolling = false, count = 5))
                assertEquals(signal(scrolling = false, count = 5), awaitItem())
            }
        }

    @Test
    fun `the same settled window is not dispatched twice`() =
        runTest {
            val signals = MutableSharedFlow<SettleSignal>()

            settles(signals).test {
                signals.emit(signal())
                awaitItem()
                signals.emit(signal())
                expectNoEvents()
            }
        }

    @Test
    fun `a preference change re-settles a list at rest`() =
        runTest {
            // The reader accepts the auto-translate offer and the store bumps
            // its generation. Nothing scrolled, but the visible posts in that
            // language must now translate without a second tap.
            val signals = MutableSharedFlow<SettleSignal>()

            settles(signals).test {
                signals.emit(signal(generation = 0))
                awaitItem()
                signals.emit(signal(generation = 1))
                assertEquals(1, awaitItem().generation)
            }
        }

    private fun signal(
        scrolling: Boolean = false,
        count: Int = 5,
        generation: Int = 0,
    ) = SettleSignal(scrolling = scrolling, firstIndex = 0, count = count, generation = generation)
}
