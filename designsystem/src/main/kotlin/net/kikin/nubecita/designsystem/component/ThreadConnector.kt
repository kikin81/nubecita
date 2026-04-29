package net.kikin.nubecita.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a 2dp vertical "thread connector" line through the avatar
 * gutter of the post this `Modifier` decorates. Used to visually link
 * consecutive posts in a thread cluster (same-author chain or
 * cross-author reply context — the chrome is identical, only the
 * `connectAbove` / `connectBelow` flags differ between cluster
 * positions).
 *
 * Geometry diagram (numbers in dp):
 *
 * ```
 *  0   20            64                                   ┐
 *  ├───┤             ├───── post body ──────────────────  │ avatar
 *  │   ┃ y=0  (above-line ends at y=avatarTop)            │ row
 *  │   ●        ← avatar centered at x = gutter+22        ┤
 *  │   ┃ (below-line starts at y=avatarBottom, goes to    │
 *  │   ┃  size.height)                                    │
 *  └───┴────────────────────────────────────────────────  ┘
 * ```
 *
 * The default geometry (`gutterX = 41.dp`, `avatarTop = 12.dp`,
 * `avatarBottom = 56.dp`) matches the reference design system's
 * `ThreadPost` (20dp horizontal padding + 44dp avatar centered = line
 * at 41dp; 12dp top padding gates `avatarTop`; 12dp + 44dp = 56dp gates
 * `avatarBottom`). Override only if your post layout uses different
 * padding/avatar dimensions — the defaults are load-bearing for
 * `PostCard`, `ThreadPost`, and downstream cluster compositions.
 *
 * Pass `connectAbove = false, connectBelow = false` to disable both
 * lines (effectively no-op draw); typically you'd just not apply this
 * modifier in that case, but the no-op form is convenient when the
 * flags are state-driven.
 *
 * @param connectAbove Draws a line from `y = 0` to `y = avatarTop` —
 *   meaning "there's a sibling above this post in the cluster."
 * @param connectBelow Draws a line from `y = avatarBottom` to
 *   `y = size.height` — meaning "there's a sibling below."
 * @param color The line color. Callers typically pass
 *   `MaterialTheme.colorScheme.outlineVariant` — the same color the
 *   reference uses.
 * @param gutterX The x-coordinate of the line in dp. Default 41dp
 *   matches a 20dp horizontal padding + 44dp avatar.
 * @param avatarTop The y-coordinate where the avatar starts. The
 *   `connectAbove` line stops here so it doesn't overlap the avatar.
 * @param avatarBottom The y-coordinate where the avatar ends. The
 *   `connectBelow` line starts here.
 * @param strokeWidth The line thickness. Default 2dp matches the
 *   reference; bsky-web uses the same.
 */
fun Modifier.threadConnector(
    connectAbove: Boolean,
    connectBelow: Boolean,
    color: Color,
    gutterX: Dp = 41.dp,
    avatarTop: Dp = 12.dp,
    avatarBottom: Dp = 56.dp,
    strokeWidth: Dp = 2.dp,
): Modifier =
    drawWithContent {
        drawContent()
        if (!connectAbove && !connectBelow) return@drawWithContent
        val xPx = gutterX.toPx()
        val strokePx = strokeWidth.toPx()
        if (connectAbove) {
            drawLine(
                color = color,
                start = Offset(xPx, 0f),
                end = Offset(xPx, avatarTop.toPx()),
                strokeWidth = strokePx,
            )
        }
        if (connectBelow) {
            drawLine(
                color = color,
                start = Offset(xPx, avatarBottom.toPx()),
                end = Offset(xPx, size.height),
                strokeWidth = strokePx,
            )
        }
    }
