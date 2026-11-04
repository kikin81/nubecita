package net.kikin.nubecita.designsystem.adaptive

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/**
 * How many pixels of the current bottom sheet's content sit below the window.
 *
 * A partially-expanded `ModalBottomSheet` measures its content at the sheet's
 * FULL height and slides the whole thing down, so content anchored to its own
 * bottom is laid out off-screen. Anything that must stay reachable — the docked
 * comment pill — shifts itself up by this much.
 *
 * It is a **lambda, not an Int**, on purpose. The value changes on every frame
 * of a drag; handing out the number would recompose every reader each frame,
 * while calling this inside `Modifier.offset { … }` defers the read to the
 * placement phase, which neither recomposes nor remeasures (nubecita-970o).
 *
 * Zero whenever there is no sheet, or the sheet is fully expanded.
 */
val LocalSheetHiddenHeightPx = staticCompositionLocalOf<() -> Int> { { 0 } }

/**
 * Normalise a sheet offset into a usable hidden height.
 *
 * Pulled out of the provider so the edge cases are testable without a sheet:
 * a `null` offset (the sheet has not been laid out yet), a non-finite one, and
 * the negative values an over-drag produces — all of which must read as "no
 * hidden height" rather than shoving content off in the wrong direction.
 */
internal fun hiddenSheetHeightPx(offset: Float?): Int = offset?.takeIf { it.isFinite() }?.roundToInt()?.coerceAtLeast(0) ?: 0
