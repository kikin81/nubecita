package net.kikin.nubecita.shell.composer

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowSizeClass
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.feature.composer.api.ComposerRoute

/**
 * Returns a width-class-conditional composer launcher lambda.
 *
 * The returned `(String?) -> Unit` lambda dispatches based on the
 * current window width:
 *
 * - **Compact** (`< 600dp` wide): pushes a `ComposerRoute(replyToUri)`
 *   onto [navState]'s back stack. The composer renders as a full-
 *   screen route inside `MainShell`'s inner `NavDisplay`.
 * - **Medium / Expanded** (`>= 600dp` wide): toggles [launcherState]
 *   to `ComposerOverlayState.Open(replyToUri)`. `MainShell` reads
 *   that state and overlays the composer in a centered `Dialog`.
 *
 * Branching logic is delegated to the pure helper [launchComposer]
 * for unit-testable coverage. This Composable wrapper just plumbs
 * the live `isCompact` signal and the live state holders into it.
 *
 * Re-keys on `isCompact` so a foldable unfold or device rotation
 * recomputes the lambda — a tap that started on a Compact phone
 * after rotation would otherwise still push onto the back stack
 * even though we're now in Medium width.
 */
@Composable
internal fun rememberComposerLauncher(
    navState: MainShellNavState,
    launcherState: ComposerLauncherState,
): (String?) -> Unit {
    val isCompact =
        !currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    return remember(isCompact, navState, launcherState) {
        { replyToUri: String? ->
            launchComposer(
                isCompact = isCompact,
                replyToUri = replyToUri,
                onPushRoute = { uri -> navState.add(ComposerRoute(replyToUri = uri)) },
                onShowOverlay = { uri -> launcherState.show(uri) },
            )
        }
    }
}

/**
 * Pure branching helper extracted for unit testing. Determines which
 * launcher path fires based on [isCompact]:
 *
 * - `true` (window width `< 600dp`) → [onPushRoute]
 * - `false` (Medium / Expanded / Large / X-Large) → [onShowOverlay]
 *
 * Foldables in Book / Tabletop posture are not special-cased here —
 * the unified-composer spec defers posture-aware treatment to a
 * follow-up. As long as `currentWindowAdaptiveInfoV2()` reports the
 * current window's effective width, this branch matches the spec.
 */
internal fun launchComposer(
    isCompact: Boolean,
    replyToUri: String?,
    onPushRoute: (String?) -> Unit,
    onShowOverlay: (String?) -> Unit,
) {
    if (isCompact) {
        onPushRoute(replyToUri)
    } else {
        onShowOverlay(replyToUri)
    }
}
