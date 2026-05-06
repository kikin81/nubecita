package net.kikin.nubecita.shell.composer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kikin.nubecita.core.common.navigation.ComposerOverlayState

/**
 * `MainShell`-scoped state holder for the Medium / Expanded composer
 * overlay. Holds a [ComposerOverlayState] in a `mutableStateOf`-backed
 * field; mutators flip the state and `MainShell` reads the current
 * value to decide whether to render the centered `Dialog`.
 *
 * Lifetime is `MainShell`'s composition. Created via `remember { … }`
 * inside the shell so a tear-down of the outer `Navigator` (e.g.
 * `replaceTo(Login)` on logout) discards the state along with the
 * shell.
 *
 * Single-shot semantics: at most one overlay is open at any time.
 * Calling `show(uri)` while an overlay is already open replaces the
 * previous `replyToUri` — V1 doesn't have a UX surface that triggers
 * re-launch while open, but defensively the state is a single
 * variable (not a stack).
 *
 * `@Stable` is correct here: the public surface is one read +
 * `Unit`-returning mutators. Compose's stability heuristic would
 * flag a class with a `var` even though it's `MutableState`-backed,
 * so the annotation makes the contract explicit.
 */
@Stable
class ComposerLauncherState(
    initial: ComposerOverlayState = ComposerOverlayState.Closed,
) {
    var state: ComposerOverlayState by mutableStateOf(initial)
        private set

    /** Open the overlay with [replyToUri] (`null` for new-post mode). */
    fun show(replyToUri: String?) {
        state = ComposerOverlayState.Open(replyToUri = replyToUri)
    }

    /** Close the overlay. Idempotent. */
    fun close() {
        state = ComposerOverlayState.Closed
    }
}
