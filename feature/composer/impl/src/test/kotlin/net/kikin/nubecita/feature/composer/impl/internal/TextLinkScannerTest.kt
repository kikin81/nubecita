package net.kikin.nubecita.feature.composer.impl.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextLinkScannerTest {
    // A trivial detector: the whole text is the link if it starts with "x".
    private fun scanner(
        handled: () -> Boolean = { false },
        onDetected: (String, String) -> Unit,
    ) = TextLinkScanner(
        detect = { text, exclude ->
            if (text.startsWith("x") && text !in exclude) {
                TextLinkScanner.Match(matchedText = text, value = "value:$text")
            } else {
                null
            }
        },
        alreadyHandled = handled,
        onDetected = onDetected,
    )

    @Test
    fun detects_once_then_memoizes_same_text() {
        val hits = mutableListOf<Pair<String, String>>()
        val scanner = scanner { matched, value -> hits.add(matched to value) }

        scanner.scan("xhello")
        scanner.scan("xhello") // same text — already attempted, no re-fire

        assertEquals(listOf("xhello" to "value:xhello"), hits)
    }

    @Test
    fun alreadyHandled_skips_detection() {
        val hits = mutableListOf<String>()
        val scanner = scanner(handled = { true }) { matched, _ -> hits.add(matched) }
        scanner.scan("xhello")
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun pruning_allows_redetect_after_text_removed_then_returns() {
        val hits = mutableListOf<String>()
        val scanner = scanner { matched, _ -> hits.add(matched) }

        scanner.scan("xhello") // detected + memoized
        scanner.scan("") // text cleared → attempted pruned
        scanner.scan("xhello") // back → detected again

        assertEquals(listOf("xhello", "xhello"), hits)
    }

    @Test
    fun forget_allows_redetect_while_text_still_present() {
        val hits = mutableListOf<String>()
        val scanner = scanner { matched, _ -> hits.add(matched) }

        scanner.scan("xhello") // detected + memoized
        scanner.scan("xhello") // memoized, no re-fire
        scanner.forget("xhello") // non-memoizing clear
        scanner.scan("xhello") // re-detected even though text never changed

        assertEquals(listOf("xhello", "xhello"), hits)
    }
}
