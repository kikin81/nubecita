package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.ThreadPositionUi
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/** Id of the inline slot [withThreadPositionSlot] appends to a post body. */
internal const val THREAD_POSITION_INLINE_ID = "thread-position"

private val BadgeHorizontalPadding = 6.dp
private val BadgeVerticalPadding = 1.dp

/**
 * The "3/5" pill marking a post's place in its author's thread.
 *
 * Not interactive. Tabular figures keep the pill the same width across the
 * posts of one thread, so it does not jitter as a reader scrolls 1/9 … 9/9.
 * Announced as "Post 3 of 5".
 */
@Composable
fun ThreadPositionBadge(
    position: ThreadPositionUi,
    modifier: Modifier = Modifier,
) {
    val spoken = stringResource(R.string.thread_position_a11y, position.index, position.count)
    Text(
        text = stringResource(R.string.thread_position_label, position.index, position.count),
        style = threadPositionTextStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .padding(horizontal = BadgeHorizontalPadding, vertical = BadgeVerticalPadding)
                .semantics { contentDescription = spoken },
    )
}

@Composable
private fun threadPositionTextStyle(): TextStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")

/**
 * This body with a slot for the thread pill appended after its last word.
 *
 * Trailing whitespace is dropped first, so a post ending in a newline does not
 * leave a blank line above the pill, and a no-break space keeps the pill on the
 * same line as the word it follows. The text itself is never rebuilt, so every
 * tappable range keeps its offsets and none of them reaches the slot.
 *
 * [spokenPosition] stands in for the pill in the string, which is what a
 * screen reader hears ("… Post 3 of 5").
 */
internal fun AnnotatedString.withThreadPositionSlot(spokenPosition: String): AnnotatedString {
    val body = subSequence(0, text.trimEnd().length)
    return buildAnnotatedString {
        append(body)
        if (body.isNotEmpty()) append(' ')
        appendInlineContent(THREAD_POSITION_INLINE_ID, spokenPosition)
    }
}

/**
 * The inline content that draws [position] into the slot from
 * [withThreadPositionSlot], or an empty map when there is no position — in
 * which case the `Text` it is handed to behaves exactly as if it had none.
 */
@Composable
internal fun rememberThreadPositionInlineContent(position: ThreadPositionUi?): Map<String, InlineTextContent> {
    if (position == null) return emptyMap()
    val label = stringResource(R.string.thread_position_label, position.index, position.count)
    val style = threadPositionTextStyle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(label, style, density, measurer) {
        val text = measurer.measure(text = label, style = style, maxLines = 1, softWrap = false)
        val placeholder =
            with(density) {
                Placeholder(
                    width = (text.size.width + BadgeHorizontalPadding.roundToPx() * 2).toSp(),
                    height = (text.size.height + BadgeVerticalPadding.roundToPx() * 2).toSp(),
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                )
            }
        mapOf(
            THREAD_POSITION_INLINE_ID to
                InlineTextContent(placeholder) {
                    // The slot's own text already says "Post 3 of 5".
                    ThreadPositionBadge(position = position, modifier = Modifier.clearAndSetSemantics {})
                },
        )
    }
}

@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "ThreadPositionBadge — 1 of 5")
@Composable
private fun ThreadPositionBadgeShortPreview() {
    ThreadPositionBadge(position = ThreadPositionUi(index = 1, count = 5))
}

@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "ThreadPositionBadge — 12 of 240")
@Composable
private fun ThreadPositionBadgeLongPreview() {
    ThreadPositionBadge(position = ThreadPositionUi(index = 12, count = 240))
}
