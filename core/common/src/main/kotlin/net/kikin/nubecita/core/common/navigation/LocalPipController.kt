package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * `CompositionLocal` exposing the active [PipBridge] (design D5; mirrors
 * [LocalMainShellNavState]). `:app`'s Activity provides the concrete bridge near
 * the root — above BOTH `NavDisplay`s — because the fullscreen video player is
 * an `@OuterShell` route, outside `MainShell`. The video screen reads
 * `LocalPipController.current` to publish PiP params and observe `isEnabled`.
 *
 * Unlike [LocalMainShellNavState] (which `error()`s when unprovided), this
 * defaults to [NoOpPipBridge]: PiP is an optional Pro perk, so a composable
 * rendered outside the Activity (previews, screenshot tests, the bench flavor)
 * SHALL render inert rather than crash. Uses `compositionLocalOf` to match the
 * sibling locals in this package; the provided bridge is Activity-stable, so
 * read-tracking cost is negligible.
 *
 * Allowlisted in `.editorconfig`'s `compose_allowed_composition_locals`.
 */
public val LocalPipController: ProvidableCompositionLocal<PipBridge> =
    compositionLocalOf { NoOpPipBridge }
