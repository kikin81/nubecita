package net.kikin.nubecita.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Renders [text] with case-insensitive substring matches of [match]
 * styled by [highlightStyle]. When [match] is null or blank, renders
 * [text] unstyled (byte-identical to a plain [Text]).
 *
 * Used by the Search Posts tab to highlight the debounced query
 * inside post bodies + actor display names. Plain-text input only —
 * facets-rendered post bodies use the underlying
 * [withMatchHighlight] helper directly to merge highlights into the
 * facets annotated string. See `:designsystem/component/PostCard.kt`'s
 * `BodyText` for the integration.
 */
@Composable
fun HighlightedText(
    text: String,
    match: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    highlightStyle: SpanStyle =
        SpanStyle(
            background = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
) {
    val annotated = AnnotatedString(text).withMatchHighlight(match, highlightStyle)
    Text(text = annotated, modifier = modifier, style = style, maxLines = maxLines, overflow = overflow)
}

/**
 * Renders [text] with [highlightStyle] applied to exactly [ranges] (end-inclusive
 * [IntRange]s, as produced by a caller-owned matching rule). Out-of-bounds range
 * ends are clamped, and an empty [ranges] renders [text] unstyled.
 */
@Composable
fun HighlightedText(
    text: String,
    ranges: List<IntRange>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    highlightStyle: SpanStyle =
        SpanStyle(
            background = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
) {
    val annotated =
        remember(text, ranges, highlightStyle) {
            buildAnnotatedString {
                append(text)
                ranges.forEach { range ->
                    val start = range.first.coerceAtLeast(0)
                    val end = (range.last + 1).coerceAtMost(text.length)
                    if (start < end) addStyle(highlightStyle, start, end)
                }
            }
        }
    Text(text = annotated, modifier = modifier, style = style, maxLines = maxLines, overflow = overflow)
}

/**
 * Returns a new [AnnotatedString] with [highlightStyle] applied to every
 * case-insensitive occurrence of [match] in this string. When [match] is
 * null or blank, returns `this` unchanged. Preserves any existing styles
 * already attached to the receiver — used by `PostCard.BodyText` to
 * overlay match highlights on top of the facet annotations produced by
 * `rememberBlueskyAnnotatedString`.
 *
 * Implementation: walks plain `text` for matches and re-builds via
 * [buildAnnotatedString], appending the existing receiver as-is between
 * match spans (so mention/link spans survive). The match span overlays
 * on top via [withStyle].
 */
fun AnnotatedString.withMatchHighlight(
    match: String?,
    highlightStyle: SpanStyle,
): AnnotatedString {
    if (match.isNullOrBlank()) return this
    val haystack = this.text
    val needleLower = match.lowercase()
    val haystackLower = haystack.lowercase()
    if (needleLower.isEmpty() || needleLower !in haystackLower) return this

    return buildAnnotatedString {
        var cursor = 0
        while (cursor < haystack.length) {
            val matchStart = haystackLower.indexOf(needleLower, startIndex = cursor)
            if (matchStart < 0) {
                append(this@withMatchHighlight.subSequence(cursor, haystack.length))
                break
            }
            if (matchStart > cursor) {
                append(this@withMatchHighlight.subSequence(cursor, matchStart))
            }
            val matchEnd = matchStart + needleLower.length
            withStyle(highlightStyle) {
                append(this@withMatchHighlight.subSequence(matchStart, matchEnd))
            }
            cursor = matchEnd
        }
    }
}

@Preview(name = "HighlightedText — no match", showBackground = true)
@Composable
private fun HighlightedTextNoMatchPreview() {
    NubecitaTheme {
        HighlightedText(
            text = "The quick brown fox jumps over the lazy dog",
            match = null,
        )
    }
}

@Preview(name = "HighlightedText — single match", showBackground = true)
@Composable
private fun HighlightedTextSingleMatchPreview() {
    NubecitaTheme {
        HighlightedText(
            text = "The quick brown fox jumps over the lazy dog",
            match = "fox",
        )
    }
}

@Preview(name = "HighlightedText — multi match (case-insensitive)", showBackground = true)
@Composable
private fun HighlightedTextMultiMatchPreview() {
    NubecitaTheme {
        HighlightedText(
            text = "Kotlin and KOTLIN and kotlin — same word, three cases",
            match = "kotlin",
        )
    }
}

@Preview(name = "HighlightedText — needle not in haystack", showBackground = true)
@Composable
private fun HighlightedTextNoOccurrencePreview() {
    NubecitaTheme {
        HighlightedText(
            text = "Short string",
            match = "completely-unrelated-very-long-needle",
        )
    }
}
