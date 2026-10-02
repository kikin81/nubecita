package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.semanticColors
import net.kikin.nubecita.designsystem.spacing

/*
 * Controls drawn on top of media.
 *
 * The problem these exist to fix: `VideoRailAction` and `VideoPlayerChrome`
 * both drew white icons with no backing at all, which disappear against a
 * bright frame — a daylit race track, snow, a white studio. Two unrelated
 * implementations of the same idea, neither legible (nubecita-6rdb.16).
 *
 * ## The backing treatment is NOT part of the public API
 *
 * Deliberate, and load-bearing (design.md D2). No parameter here names a
 * scrim, an alpha, a blur, a quality mode or a backdrop source. Callers say
 * what a control *is* — its icon, label, count, toggled state, action — and
 * the component decides how to back it.
 *
 * That is what lets the treatment change later without touching a single call
 * site. Background blur was specced first and deferred (design.md D1); when it
 * lands it is a change *inside this file*, and no feature module recompiles
 * against a changed signature. Adding a treatment parameter here would quietly
 * convert that follow-up from additive to a refactor.
 *
 * ## Why these tokens
 *
 * `videoOverlayScrim` / `onVideoOverlay` are theme-INVARIANT: the contrast
 * target is user-supplied video pixels, not the page background, so a scheme
 * colour would carry no contrast guarantee at all. Same pair `MediaPlayBadge`
 * already paints with, so the two overlay families cannot drift apart.
 *
 * Measured: `videoOverlayScrim` is black at 80% alpha. Composited over a
 * worst-case white frame that is sRGB 0.2, relative luminance 0.033, so against
 * pure-white `onVideoOverlay` the contrast ratio is **12.6:1** — comfortably
 * past the 4.5:1 floor the capability spec requires, with room for the scrim to
 * be lightened later if it reads too heavy. Re-measure if either token moves.
 */

/**
 * An icon-only control over media — the fullscreen player's back, skip, mute
 * and pop-out buttons.
 *
 * Set [toggleable] for controls with an on/off state; leave it false for
 * one-shot actions (back, skip). See [NubecitaOverlayStatButton] for the
 * accessibility contract, which is identical and deliberately so.
 *
 * [size] and [glyphSize] exist because the fullscreen chrome's transport
 * controls are deliberately bigger than its utility controls — skip ±10s is
 * 52dp/28dp against 44dp/24dp for back, mute and pop-out. They are *layout*,
 * not treatment: sizing a control is the caller's business, how it is backed is
 * not, so this does not weaken the D2 rule that the backing stays private.
 */
@Composable
fun NubecitaOverlayIconButton(
    icon: NubecitaIconName,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    toggleable: Boolean = false,
    size: Dp = ICON_BUTTON_SIZE,
    glyphSize: Dp = ICON_BUTTON_GLYPH_SIZE,
    testTag: String? = null,
    onClickLabel: String? = null,
) {
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.semanticColors.videoOverlayScrim, CircleShape)
                .border(HAIRLINE_WIDTH, hairlineColor(), CircleShape)
                .overlayInteraction(
                    onClick = onClick,
                    active = active,
                    toggleable = toggleable,
                    onClickLabel = onClickLabel,
                ).then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        NubecitaIcon(
            name = icon,
            contentDescription = accessibilityLabel,
            filled = active,
            tint = MaterialTheme.semanticColors.onVideoOverlay,
            opticalSize = glyphSize,
        )
    }
}

/**
 * An icon with an optional count beneath it — the trending video feed's
 * right-hand rail (like, repost, bookmark, reply, share, overflow, mute).
 *
 * The scrim wraps the icon **and** the count rather than sitting behind the
 * icon alone. The capability spec's contrast floor covers "icon and any count
 * label", and a backed icon above an unbacked white number would leave exactly
 * half the control invisible over a bright frame — which is the bug being
 * fixed, not a smaller version of it.
 *
 * ## Accessibility contract
 *
 * Carried over verbatim from `VideoRailAction`; already correct, already
 * tested, not up for redesign here.
 *
 * - **[toggleable] = true** (like, repost, bookmark, mute) →
 *   `Modifier.toggleable(role = Role.Switch)`, with the label carried as the
 *   icon's `contentDescription`. `toggleable` accepts no label parameter, so an
 *   `onClickLabel` here would be silently dropped. TalkBack announces
 *   "<label>, switch, on/off".
 * - **[toggleable] = false** (reply, share, overflow) → `Modifier.clickable(role
 *   = Role.Button, onClickLabel = …)`, and the icon stays decorative so the
 *   label isn't announced twice. TalkBack announces "Double-tap to <label>".
 *
 * [accessibilityLabel] is therefore always the plain **noun** ("Like", "Mute"),
 * never the inverse verb ("Unlike", "Unmute") — on a toggle the state comes
 * from the switch semantics, not from the wording.
 *
 * Bookmark and overflow are easy to forget: the rail has seven cells, not five.
 */
@Composable
fun NubecitaOverlayStatButton(
    icon: NubecitaIconName,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Long? = null,
    active: Boolean = false,
    toggleable: Boolean = false,
    activeColor: Color? = null,
    testTag: String? = null,
) {
    val contentColor = MaterialTheme.semanticColors.onVideoOverlay
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s1),
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .clip(StatShape)
                .background(MaterialTheme.semanticColors.videoOverlayScrim, StatShape)
                .border(HAIRLINE_WIDTH, hairlineColor(), StatShape)
                .overlayInteraction(
                    onClick = onClick,
                    active = active,
                    toggleable = toggleable,
                    onClickLabel = accessibilityLabel,
                ).then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .padding(
                    horizontal = MaterialTheme.spacing.s2,
                    vertical = MaterialTheme.spacing.s2,
                ),
    ) {
        NubecitaIcon(
            name = icon,
            contentDescription = if (toggleable) accessibilityLabel else null,
            filled = active,
            tint = if (active) activeColor ?: contentColor else contentColor,
            opticalSize = MaterialTheme.spacing.s7,
        )
        if (count != null) {
            Text(
                text = rememberCompactCount(count),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

/**
 * The one place the toggle-vs-button semantics are decided, so the two public
 * controls cannot drift apart. Applied BEFORE padding so the touch target
 * covers the scrim, not just the glyph.
 */
private fun Modifier.overlayInteraction(
    onClick: () -> Unit,
    active: Boolean,
    toggleable: Boolean,
    onClickLabel: String? = null,
): Modifier =
    if (toggleable) {
        this.toggleable(
            value = active,
            role = Role.Switch,
            onValueChange = { onClick() },
        )
    } else {
        this.clickable(
            role = Role.Button,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
    }

/**
 * A hairline edge so the control's shape stays readable against media that is
 * as dark as the scrim.
 *
 * Not decoration — it settles design.md Open Question 2 with evidence. The
 * first baselines showed the scrim doing its job over white and mid-tone but
 * vanishing entirely over a black frame: 80%-black-on-black is
 * indistinguishable, so the icon stayed perfectly legible (white on black is
 * ~21:1) while the control stopped reading as a control at all. The capability
 * spec asks for both — legibility AND a discernible shape boundary — and fill
 * alone delivers only the first.
 *
 * Kept at a low alpha on purpose: over white and mid-tone the boundary is
 * already carried by the scrim, so the edge should be invisible there rather
 * than drawing a ring around every control.
 */
@Composable
@ReadOnlyComposable
private fun hairlineColor(): Color = MaterialTheme.semanticColors.onVideoOverlay.copy(alpha = HAIRLINE_ALPHA)

private val HAIRLINE_WIDTH = 1.dp
private const val HAIRLINE_ALPHA = 0.16f

/**
 * A capsule rather than a circle: with a count the cell is taller than it is
 * wide, and at 50% the same shape degrades to a circle when [count] is null —
 * so an icon-only rail cell and a counted one stay visually consistent without
 * branching on shape.
 */
private val StatShape = RoundedCornerShape(percent = 50)

/**
 * Glyph sizes come from the spacing scale (`s6` = 24dp, `s7` = 28dp), matching
 * `MediaPlayBadge`'s precedent of sizing from tokens rather than raw dp. The
 * values are identical to what `VideoPlayerChrome` and `VideoRailAction` already
 * rendered, so adoption does not resize anything.
 *
 * The two literals below deliberately stay off-scale:
 *
 * - [ICON_BUTTON_SIZE] is 44dp visual size, matching what the fullscreen chrome
 *   already used. The touch target is safely expanded to ≥48dp via
 *   `minimumInteractiveComponentSize()` without altering its visual 44dp circle.
 * - [HAIRLINE_WIDTH] is a stroke width, not spacing; the scale's smallest step
 *   is 4dp, which would be a heavy ring rather than a hairline.
 */
private val ICON_BUTTON_SIZE = 44.dp
private val ICON_BUTTON_GLYPH_SIZE = 24.dp
