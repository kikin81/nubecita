package net.kikin.nubecita.designsystem.adaptive

import androidx.compose.runtime.staticCompositionLocalOf

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
