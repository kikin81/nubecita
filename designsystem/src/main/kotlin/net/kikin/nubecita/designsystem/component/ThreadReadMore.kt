package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

private val GutterX = 40.dp
private val RowHorizontalPadding = 20.dp
private val AvatarSlotWidth = 40.dp
private val LineGap = 14.dp
private val RowMinHeight = 48.dp

/**
 * A row that continues a thread the list holds only part of: "Read more
 * replies", "Read earlier posts", "Show more replies".
 *
 * Its label starts where a [PostCard]'s text starts, so it reads as part of
 * the conversation it belongs to. [connectAbove] and [connectBelow] run the
 * thread line (the same one `PostCard` draws down its avatar gutter) into the
 * row from the post over it, or out of it into the post under it; the line
 * stops short of the label's line so the row reads as a break in the thread,
 * not as another post in it.
 *
 * While [isLoading], the row shows the brand progress indicator in the avatar
 * gutter and ignores taps.
 */
@Composable
fun ThreadReadMore(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    connectAbove: Boolean = false,
    connectBelow: Boolean = false,
    isLoading: Boolean = false,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick)
                .drawBehind {
                    if (!connectAbove && !connectBelow) return@drawBehind
                    val x = GutterX.toPx()
                    val stroke = 2.dp.toPx()
                    val gap = LineGap.toPx()
                    val middle = size.height / 2
                    if (connectAbove) drawLine(lineColor, Offset(x, 0f), Offset(x, (middle - gap).coerceAtLeast(0f)), stroke)
                    if (connectBelow) drawLine(lineColor, Offset(x, (middle + gap).coerceAtMost(size.height)), Offset(x, size.height), stroke)
                }.heightIn(min = RowMinHeight)
                .padding(horizontal = RowHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width(AvatarSlotWidth), contentAlignment = Alignment.Center) {
            if (isLoading) NubecitaWavyProgressIndicator(modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "ThreadReadMore — under a post")
@Composable
private fun ThreadReadMoreConnectedAbovePreview() {
    ThreadReadMore(label = "Read more replies", onClick = {}, connectAbove = true)
}

@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "ThreadReadMore — above a post")
@Composable
private fun ThreadReadMoreConnectedBelowPreview() {
    ThreadReadMore(label = "Read earlier posts", onClick = {}, connectBelow = true)
}

@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "ThreadReadMore — loading")
@Composable
private fun ThreadReadMoreLoadingPreview() {
    ThreadReadMore(label = "Show more replies", onClick = {}, isLoading = true)
}
