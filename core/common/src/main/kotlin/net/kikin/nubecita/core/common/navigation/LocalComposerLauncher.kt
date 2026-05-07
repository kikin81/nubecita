package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * `CompositionLocal` exposing the active composer-launcher lambda to
 * any Composable hosted inside `MainShell`. The lambda takes a
 * nullable `replyToUri: String?` argument: pass `null` to launch a
 * new-post composer; pass an AT-URI string to launch a reply composer.
 *
 * The lambda is the **width-class-conditional helper** computed by
 * `MainShell`:
 *
 * - At Compact width, the lambda pushes `ComposerRoute(replyToUri)`
 *   onto `MainShell`'s inner `NavDisplay` (full-screen composer
 *   route).
 * - At Medium / Expanded widths, the lambda toggles a `MainShell`-
 *   scoped overlay state to `Open(replyToUri)`, which `MainShell`
 *   observes to overlay a centered `Dialog` containing the composer.
 *
 * Consumers (the Feed FAB, the per-post reply affordance, etc.) read
 * `LocalComposerLauncher.current` and invoke it from `onClick`
 * lambdas. They never need to know which width class they're on, nor
 * which container the composer renders in. The width-class branching
 * is centralized in `MainShell`.
 *
 * Same shape as [LocalMainShellNavState]: no default value — reading
 * outside `MainShell` SHALL throw [IllegalStateException]. The error
 * makes the boundary explicit.
 *
 * Allowlisted in `.editorconfig`'s `compose_allowed_composition_locals`
 * so ktlint's `compose:compositionlocal-allowlist` rule lets it
 * through.
 */
val LocalComposerLauncher: ProvidableCompositionLocal<(String?) -> Unit> =
    compositionLocalOf {
        error(
            "ComposerLauncher not provided. Wrap your composable in MainShell or call " +
                "CompositionLocalProvider(LocalComposerLauncher provides …).",
        )
    }
