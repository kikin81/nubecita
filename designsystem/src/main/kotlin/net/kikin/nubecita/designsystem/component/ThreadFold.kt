package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The "View full thread" fold — sits between non-adjacent posts in a
 * thread cluster, indicating that intermediate posts have been elided.
 *
 * Visual: the connector line continues unbroken from top to bottom
 * (same `gutterX` as `Modifier.threadConnector`), with three small
 * dots stacked along the connector and a "View full thread" label to
 * the right. Optionally shows an elision count (e.g. "· 5 more") when
 * `count > 0`.
 *
 * Used in cluster renderings where the feed payload contains the root
 * + immediate parent of a reply, and at least one intermediate post
 * was elided. Tapping the fold typically navigates to a post-detail
 * screen that fetches the full thread via `app.bsky.feed.getPostThread`
 * — until that screen exists, callers can wire `onClick` to a no-op or
 * a "coming soon" toast.
 *
 * @param count The number of intermediate posts elided. `0` (the
 *   default) hides the count and shows only the "View full thread"
 *   label — useful when the feed API doesn't surface a precise count
 *   (e.g. only `replyRef.grandparentAuthor` is populated, signalling
 *   "at least one post above parent" without a number).
 * @param onClick Tap handler — typically navigates to the post-detail
 *   thread view.
 * @param modifier Additional modifier applied to the outer Row. The
 *   connector line is drawn via `drawWithContent` on this `Row`, so
 *   the modifier can add padding or background but should not replace
 *   the connector behavior.
 * @param gutterX The x-coordinate of the connector line. Must match
 *   the `gutterX` passed to `Modifier.threadConnector` on the posts
 *   above and below this fold so the line is unbroken.
 */
@Composable
fun ThreadFold(
    modifier: Modifier = Modifier,
    count: Int = 0,
    gutterX: Dp = 42.dp,
    onClick: () -> Unit = {},
) {
    val connectorColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = MaterialTheme.colorScheme.outline

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .drawWithContent {
                    drawContent()
                    val xPx = gutterX.toPx()
                    drawLine(
                        color = connectorColor,
                        start = Offset(xPx, 0f),
                        end = Offset(xPx, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) {
                Box(
                    modifier =
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                )
            }
        }
        Row(
            modifier = Modifier.heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "View full thread",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (count > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "· $count more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
