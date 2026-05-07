package net.kikin.nubecita.core.common.navigation

/**
 * State of the `MainShell`-scoped composer overlay used at Medium /
 * Expanded widths. Pure data — no Compose, no atproto types.
 *
 * At Compact width the composer is hosted as a Nav3 entry inside
 * `MainShell`'s inner `NavDisplay` and this state is not used. At
 * Medium / Expanded widths, the composer is overlaid via a centered
 * `Dialog` and the overlay's open/close lifecycle flows through this
 * sum.
 *
 * `replyToUri` is carried as a raw `String?` (matching `ComposerRoute`'s
 * shape) so this module stays atproto-runtime-free; the consumer
 * lifts to `AtUri` at the call site to the atproto runtime.
 */
sealed interface ComposerOverlayState {
    /** No overlay rendered. The composer is dismissed. */
    data object Closed : ComposerOverlayState

    /**
     * Overlay visible. [replyToUri] is `null` for new-post mode; non-null
     * for reply mode (the AT-URI of the post being replied to).
     */
    data class Open(
        val replyToUri: String?,
    ) : ComposerOverlayState
}
