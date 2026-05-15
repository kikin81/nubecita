package net.kikin.nubecita.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * One interactive icon + count cell in a [PostCard]'s action row.
 *
 * `active` flips the icon and count to [activeColor] (typically
 * `MaterialTheme.colorScheme.secondary` for like, `tertiary` for repost).
 * Inactive uses `onSurfaceVariant`. The cell is a `Row` clipped to a
 * circle so the ripple matches the icon's affordance.
 *
 * Pass `count = null` for cells that don't show a number (e.g. the
 * share button). Non-null counts are rendered through
 * [AnimatedCompactCount] which formats locale-aware short scale
 * ("1.2K") and applies the digit-roll animation when
 * [animateUserDelta] is true.
 *
 * `toggleable` selects the a11y semantics:
 * - `false` (default) — one-shot action (reply, share). Uses
 *   `Modifier.clickable(role = Role.Button, onClickLabel = accessibilityLabel)`.
 *   TalkBack announces "Double-tap to <label>".
 * - `true` — on/off toggle (like, repost). Uses
 *   `Modifier.toggleable(value = active, role = Role.Switch)` and sets the
 *   Icon's `contentDescription = accessibilityLabel`. TalkBack announces
 *   "<label>, switch, <on|off>, double tap to toggle" so the user gets BOTH
 *   the action and the current state — the implicit-state-via-action-verb
 *   pattern (e.g. "Unlike") was insufficient because it omitted on/off.
 *
 * Optional [onLongClick] adds a long-press gesture (e.g. share → copy
 * permalink). Only honored on non-toggleable cells; passing it
 * alongside `toggleable = true` is ignored so we don't fight a
 * `Role.Switch`'s own long-press semantics. `combinedClickable` fires
 * the system's long-press haptic automatically — no manual
 * `LocalHapticFeedback` plumbing needed. Pair with [onLongClickLabel]
 * so TalkBack announces what long-press will do (e.g. "Copy link")
 * instead of a generic "press and hold."
 */
@Composable
internal fun PostStat(
    name: NubecitaIconName,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    count: Long? = null,
    filled: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    active: Boolean = false,
    toggleable: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    iconAnimation: PostStatIconAnimation = PostStatIconAnimation.None,
    animateUserDelta: Boolean = false,
) {
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Cross-fade the tint on every cell so the active → inactive flip
    // doesn't snap. tween is short (200ms) so the gesture still feels
    // immediate.
    val tint by animateColorAsState(
        targetValue = if (active) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = TINT_ANIM_MS),
        label = "PostStat-tint",
    )
    val popScale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    // `LaunchedEffect(active)` would fire on the *first* composition too,
    // which scrolls a fully-baked feed into view by popping every liked
    // post. Suppress the initial run; let real toggles drive the motion.
    val firstFrame = remember { mutableStateOf(true) }
    LaunchedEffect(active, iconAnimation) {
        if (firstFrame.value) {
            firstFrame.value = false
            return@LaunchedEffect
        }
        when (iconAnimation) {
            PostStatIconAnimation.None -> Unit
            PostStatIconAnimation.Pop -> {
                popScale.animateTo(
                    targetValue = POP_SCALE_PEAK,
                    animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
                )
                popScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessLow),
                )
            }
            PostStatIconAnimation.Spin -> {
                // Twitter / Bluesky-web pattern: spin only on activate. On
                // undo we snap the rotation back to 0 so a rapid undo
                // mid-spin doesn't leave the icon stuck at some
                // intermediate angle (the previous LaunchedEffect's
                // cancellation drops the in-flight animateTo without
                // resetting the underlying Animatable value).
                if (active) {
                    rotation.snapTo(0f)
                    rotation.animateTo(
                        targetValue = 360f,
                        animationSpec = tween(durationMillis = SPIN_DURATION_MS),
                    )
                } else {
                    rotation.snapTo(0f)
                }
            }
        }
    }
    val interactionModifier =
        when {
            toggleable ->
                Modifier.toggleable(
                    value = active,
                    role = Role.Switch,
                    onValueChange = { onClick() },
                )
            onLongClick != null ->
                Modifier.combinedClickable(
                    role = Role.Button,
                    onClickLabel = accessibilityLabel,
                    onLongClickLabel = onLongClickLabel,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            else ->
                Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = accessibilityLabel,
                    onClick = onClick,
                )
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .clip(CircleShape)
                .then(interactionModifier)
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        // For toggleable cells, the Icon carries the contentDescription so
        // TalkBack has a noun to attach to the "switch, on/off" announcement.
        // For non-toggleable cells, the action verb comes via clickable's
        // onClickLabel above and the Icon stays decorative — avoids double-
        // announcement.
        NubecitaIcon(
            name = name,
            contentDescription = if (toggleable) accessibilityLabel else null,
            filled = filled,
            tint = tint,
            opticalSize = STAT_ICON_SIZE,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = popScale.value
                    scaleY = popScale.value
                    rotationZ = rotation.value
                },
        )
        if (count != null) {
            AnimatedCompactCount(
                count = count,
                animateUserDelta = animateUserDelta,
                color = tint,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private val STAT_ICON_SIZE = 18.dp

/** Tint cross-fade duration in ms. */
private const val TINT_ANIM_MS = 200

/** Peak scale during the heart-pop overshoot. 1.18 ≈ +18%. */
private const val POP_SCALE_PEAK = 1.18f

/** Total spin time for the repost activate gesture. */
private const val SPIN_DURATION_MS = 500

/**
 * Per-cell icon motion. Reply / share are static. Like uses [Pop] on
 * both activate and undo. Repost uses [Spin] on activate only — undo
 * relies on the tint cross-fade alone (matches Twitter / Bluesky web).
 */
internal enum class PostStatIconAnimation {
    None,
    Pop,
    Spin,
}

@Preview(name = "PostStat — inactive", showBackground = true)
@Composable
private fun PostStatInactivePreview() {
    NubecitaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PostStat(name = NubecitaIconName.ChatBubble, count = 12L, accessibilityLabel = "Reply")
            PostStat(name = NubecitaIconName.Repeat, count = 4L, accessibilityLabel = "Repost")
            PostStat(name = NubecitaIconName.Favorite, count = 86L, accessibilityLabel = "Like")
            PostStat(name = NubecitaIconName.IosShare, count = null, accessibilityLabel = "Share post")
        }
    }
}

@Preview(name = "PostStat — active like + repost", showBackground = true)
@Composable
private fun PostStatActivePreview() {
    NubecitaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PostStat(name = NubecitaIconName.ChatBubble, count = 12L, accessibilityLabel = "Reply")
            PostStat(
                name = NubecitaIconName.Repeat,
                count = 5L,
                accessibilityLabel = "Undo repost",
                active = true,
                activeColor = MaterialTheme.colorScheme.tertiary,
            )
            PostStat(
                name = NubecitaIconName.Favorite,
                filled = true,
                count = 87L,
                accessibilityLabel = "Unlike",
                active = true,
                activeColor = MaterialTheme.colorScheme.secondary,
            )
            PostStat(name = NubecitaIconName.IosShare, count = null, accessibilityLabel = "Share post")
        }
    }
}
