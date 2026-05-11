package net.kikin.nubecita.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Horizontally flips the modified composable when the surrounding
 * layout direction is RTL. In LTR the modifier is a no-op.
 *
 * Apply to directional icons (back arrow, reply chevron, etc.) — the
 * deprecated `Icons.AutoMirrored.*` namespace did this automatically;
 * Material Symbols Rounded does not, so mirroring is a per-call-site
 * opt-in. Forgetting to apply this on a directional icon means the
 * arrow points the wrong way in RTL locales — caught by RTL
 * screenshot fixtures.
 */
@Composable
fun Modifier.mirror(): Modifier =
    composed {
        if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
            this.then(Modifier.scale(scaleX = -1f, scaleY = 1f))
        } else {
            this
        }
    }
