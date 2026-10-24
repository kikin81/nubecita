package net.kikin.nubecita.core.posting.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Parity tests for [TagDetector] against the official client. The two case
 * tables are ported verbatim from `bluesky-social/atproto`
 * `packages/api/tests/rich-text-detection.test.ts` ("correctly detects tags
 * inline" / "correctly detects cashtags inline"), including their expected
 * UTF-8 byte ranges — so a pass here means Nubecita emits the same facets as
 * bsky.app for the same text.
 */
class TagDetectorTest {
    @ParameterizedTest(name = "hashtags in \"{0}\"")
    @MethodSource("hashtagCases")
    fun detectsHashtagsLikeTheOfficialClient(case: Case) = assertParity(case) { !it.tag.startsWith("$") }

    @ParameterizedTest(name = "cashtags in \"{0}\"")
    @MethodSource("cashtagCases")
    fun detectsCashtagsLikeTheOfficialClient(case: Case) = assertParity(case) { it.tag.startsWith("$") }

    private fun assertParity(
        case: Case,
        filter: (DetectedTag) -> Boolean,
    ) {
        val detected = TagDetector.detect(case.text).filter(filter)
        assertEquals(case.tags, detected.map { it.tag }, "tags")
        assertEquals(
            case.byteRanges,
            detected.map { utf8Offset(case.text, it.start) to utf8Offset(case.text, it.endExclusive) },
            "byte ranges",
        )
    }

    private fun utf8Offset(
        text: String,
        utf16Index: Int,
    ): Int = text.substring(0, utf16Index).toByteArray(Charsets.UTF_8).size

    data class Case(
        val text: String,
        val tags: List<String>,
        val byteRanges: List<Pair<Int, Int>>,
    ) {
        override fun toString(): String = text
    }

    companion object {
        @JvmStatic
        fun hashtagCases(): List<Case> = HASHTAG_CASES

        @JvmStatic
        fun cashtagCases(): List<Case> = CASHTAG_CASES

        private val HASHTAG_CASES =
            listOf(
                Case("#a", listOf("a"), listOf(0 to 2)),
                Case("#a #b", listOf("a", "b"), listOf(0 to 2, 3 to 5)),
                Case("#1", listOf(), listOf()),
                Case("#1a", listOf("1a"), listOf(0 to 3)),
                Case("#tag", listOf("tag"), listOf(0 to 4)),
                Case("body #tag", listOf("tag"), listOf(5 to 9)),
                Case("#tag body", listOf("tag"), listOf(0 to 4)),
                Case("body #tag body", listOf("tag"), listOf(5 to 9)),
                Case("body #1", listOf(), listOf()),
                Case("body #1a", listOf("1a"), listOf(5 to 8)),
                Case("body #a1", listOf("a1"), listOf(5 to 8)),
                Case("#", listOf(), listOf()),
                Case("#?", listOf(), listOf()),
                Case("text #", listOf(), listOf()),
                Case("text # text", listOf(), listOf()),
                Case("body #thisisa64characterstring_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", listOf("thisisa64characterstring_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), listOf(5 to 70)),
                Case("body #thisisa65characterstring_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab", listOf(), listOf()),
                Case("body #thisisa64characterstring_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!", listOf("thisisa64characterstring_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), listOf(5 to 70)),
                Case("its a #double#rainbow", listOf("double#rainbow"), listOf(6 to 21)),
                Case("##hashash", listOf("#hashash"), listOf(0 to 9)),
                Case("##", listOf(), listOf()),
                Case("some #n0n3s@n5e!", listOf("n0n3s@n5e"), listOf(5 to 15)),
                Case("works #with,punctuation", listOf("with,punctuation"), listOf(6 to 23)),
                Case("strips trailing #punctuation, #like. #this!", listOf("punctuation", "like", "this"), listOf(16 to 28, 30 to 35, 37 to 42)),
                Case("strips #multi_trailing___...", listOf("multi_trailing"), listOf(7 to 22)),
                Case("works with #\uD83E\uDD8B emoji, and #butter\uD83E\uDD8Bfly", listOf("\uD83E\uDD8B", "butter\uD83E\uDD8Bfly"), listOf(11 to 16, 28 to 42)),
                Case("#same #same #but #diff", listOf("same", "same", "but", "diff"), listOf(0 to 5, 6 to 11, 12 to 16, 17 to 22)),
                Case("this #\uFE0F\u20E3tag should not be a tag", listOf(), listOf()),
                Case("this ##\uFE0F\u20E3tag should be a tag", listOf("#\uFE0F\u20E3tag"), listOf(5 to 16)),
                Case("this #t\nag should be a tag", listOf("t"), listOf(5 to 7)),
                Case("no match (\\u200B): #\u200B", listOf(), listOf()),
                Case("no match (\\u200Ba): #\u200Ba", listOf(), listOf()),
                Case("match (a\\u200Bb): #a\u200Bb", listOf("a"), listOf(18 to 20)),
                Case("match (ab\\u200B): #ab\u200B", listOf("ab"), listOf(18 to 21)),
                Case("no match (\\u20e2tag): #\u20E2tag", listOf(), listOf()),
                Case("no match (a\\u20e2b): #a\u20E2b", listOf("a"), listOf(21 to 23)),
                Case("match full width number sign (tag): \uFF03tag", listOf("tag"), listOf(36 to 42)),
                Case("match full width number sign (tag): \uFF03#\uFE0F\u20E3tag", listOf("#\uFE0F\u20E3tag"), listOf(36 to 49)),
                Case("no match 1?: #1?", listOf(), listOf()),
            )
        private val CASHTAG_CASES =
            listOf(
                Case("\$AAPL", listOf("\$AAPL"), listOf(0 to 5)),
                Case("\$aapl", listOf("\$AAPL"), listOf(0 to 5)),
                Case("\$A", listOf("\$A"), listOf(0 to 2)),
                Case("\$a", listOf("\$A"), listOf(0 to 2)),
                Case("\$BTC \$ETH", listOf("\$BTC", "\$ETH"), listOf(0 to 4, 5 to 9)),
                Case("\$100", listOf(), listOf()),
                Case("\$GOOGL", listOf("\$GOOGL"), listOf(0 to 6)),
                Case("\$TOOLONG", listOf(), listOf()),
                Case("check \$LEGO now", listOf("\$LEGO"), listOf(6 to 11)),
                Case("(\$GOOG)", listOf("\$GOOG"), listOf(1 to 6)),
                Case("\$AAPL.", listOf("\$AAPL"), listOf(0 to 5)),
                Case("\$AAPL, \$MSFT!", listOf("\$AAPL", "\$MSFT"), listOf(0 to 5, 7 to 12)),
                Case("no\$SPACE", listOf(), listOf()),
                Case("\$", listOf(), listOf()),
                Case("\$ AAPL", listOf(), listOf()),
                Case("\$123ABC", listOf(), listOf()),
                Case("\$ABC12", listOf("\$ABC12"), listOf(0 to 6)),
                Case("\$ABC123", listOf(), listOf()),
            )
    }
}
