package net.kikin.nubecita.feature.composer.impl.state

import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.core.common.mvi.UiEffect

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
     * Show a transient error message (typically a Snackbar). The
     * screen Composable resolves [stringResId] against its
     * `Resources` for localization. Positional [args] are passed to
     * `Resources.getString(id, args)` for parameterized strings.
     *
     * Sticky error state, if needed for a future screen, would go
     * into the flat state explicitly (e.g. `errorBanner: String? = null`)
     * — the default is non-sticky snackbar via this effect.
     */
    data class ShowError(
        val stringResId: Int,
        val args: List<Any> = emptyList(),
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
