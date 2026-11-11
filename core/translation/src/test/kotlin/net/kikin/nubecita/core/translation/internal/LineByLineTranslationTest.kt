package net.kikin.nubecita.core.translation.internal

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The contract: a post's lines come back as lines.
 *
 * ML Kit drops every newline, so handing it a whole post fuses the lines into
 * one sentence — and for a language like Japanese, where each line is its own
 * thought, the fused sentence is a mistranslation, not just a reflow.
 */
internal class LineByLineTranslationTest {
    @Test
    fun `each line is translated on its own and the breaks survive`() =
        runTest {
            // Real ML Kit output captured on device (nubecita-qpx6). Whole, the
            // post came back as one fused sentence: "Iwatani is anything that
            // is no longer Cassett gas refrigerator or conversely to cool it".
            val observed =
                mapOf(
                    "イワタニはなんでもあるわね" to "Iwatani is everything",
                    "もはやカセットガス冷蔵庫とか逆に冷やすのもつくれるんちゃうか" to
                        "It will also be a cassette gas refrigerator or conversely, it will be cold",
                )
            val sent = mutableListOf<String>()

            val result =
                translateLineByLine("イワタニはなんでもあるわね\nもはやカセットガス冷蔵庫とか逆に冷やすのもつくれるんちゃうか") { line ->
                    sent += line
                    Result.success(observed.getValue(line))
                }

            assertEquals(
                "Iwatani is everything\nIt will also be a cassette gas refrigerator or conversely, it will be cold",
                result.getOrThrow(),
            )
            assertEquals(observed.keys.toList(), sent)
        }

    @Test
    fun `blank lines are kept and never sent to the engine`() =
        runTest {
            val sent = mutableListOf<String>()

            val result =
                translateLineByLine("Hallo\n\n  \nWelt") { line ->
                    sent += line
                    Result.success(line.uppercase())
                }

            assertEquals("HALLO\n\n  \nWELT", result.getOrThrow())
            assertEquals(listOf("Hallo", "Welt"), sent)
        }

    @Test
    fun `whitespace around a line is the author's and is kept`() =
        runTest {
            // The engine trims what it is given; indentation and the spacing
            // of a list are layout, not words, so they go back around the
            // translation untouched.
            val result = translateLineByLine("  - eins \n\t- zwei") { line -> Result.success("<$line>") }

            assertEquals("  <- eins> \n\t<- zwei>", result.getOrThrow())
        }

    @Test
    fun `a single-line post is one call, as before`() =
        runTest {
            var calls = 0

            val result =
                translateLineByLine("Guten Morgen") {
                    calls++
                    Result.success("Good morning")
                }

            assertEquals("Good morning", result.getOrThrow())
            assertEquals(1, calls)
        }

    @Test
    fun `one failed line fails the whole post rather than half-translating it`() =
        runTest {
            val boom = IllegalStateException("model files not found")
            var calls = 0

            val result =
                translateLineByLine("eins\nzwei\ndrei") { line ->
                    calls++
                    if (line == "zwei") Result.failure(boom) else Result.success(line)
                }

            assertTrue(result.isFailure)
            assertEquals(boom, result.exceptionOrNull())
            // Stops at the failure: a post that will be shown in the original
            // anyway gains nothing from translating its remaining lines.
            assertEquals(2, calls)
        }

    @Test
    fun `windows line endings are kept intact`() =
        runTest {
            val sent = mutableListOf<String>()

            val result =
                translateLineByLine("eins\r\nzwei") { line ->
                    sent += line
                    Result.success(line.uppercase())
                }

            assertEquals("EINS\r\nZWEI", result.getOrThrow())
            assertEquals(listOf("eins", "zwei"), sent)
        }
}
