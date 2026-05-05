package net.kikin.nubecita.feature.composer.impl.state

import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.posting.ComposerError

/**
 * One-shot effects emitted by the composer VM. Collected once in the
 * screen's outermost composable via a single `LaunchedEffect`. Per
 * the MVI convention, navigation flows through here (NOT through a
 * `Hilt`-injected navigator) because the screen-side
 * `LocalMainShellNavState` is reachable only via `CompositionLocal`,
 * which a ViewModel can't access.
 */
sealed interface ComposerEffect : UiEffect {
    /**
     * Pop the composer from its host. At Compact width, the screen
     * Composable invokes `LocalMainShellNavState.current.removeLast()`.
     * At Medium/Expanded width, the screen Composable transitions
     * the `MainShell`-scoped composer-launcher state holder to
     * `Closed`.
     */
    data object NavigateBack : ComposerEffect

    /**
     * Show a transient error message (typically a Snackbar). Carries
     * the typed [ComposerError] from `:core:posting`; the screen
     * Composable maps each variant to a string resource pre-resolved
     * at composition time, matching `FeedEffect.ShowError(error: FeedError)`
     * and `PostDetailEffect.ShowError(error: PostDetailError)`. That
     * pattern keeps locale + dark-mode changes participating in
     * recomposition (the `LocalContextGetResourceValueCall` lint
     * gate) without forcing the VM to know anything about Android
     * resources.
     *
     * Sticky error state, if a future screen needs it, would go into
     * the flat state explicitly (e.g. `errorBanner: String? = null`)
     * — the default is non-sticky snackbar via this effect.
     */
    data class ShowError(
        val error: ComposerError,
    ) : ComposerEffect

    /**
     * The submit completed successfully. Carries the new post's AT
     * URI so the screen can optimistically slot it into the feed
     * (currently V1 doesn't optimistic-insert — submit closes the
     * composer and the feed refresh on next pull picks up the post).
     */
    data class OnSubmitSuccess(
        val newPostUri: AtUri,
    ) : ComposerEffect
}
