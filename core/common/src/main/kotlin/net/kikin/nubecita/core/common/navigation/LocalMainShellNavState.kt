package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * `CompositionLocal` exposing the active [MainShellNavState] to any
 * Composable hosted inside `MainShell`'s inner `NavDisplay`. The shell
 * provides it via `CompositionLocalProvider(LocalMainShellNavState provides
 * mainShellNavState) { … }`; descendants read it via
 * `LocalMainShellNavState.current`.
 *
 * The local has **no default value** — reading it from a Composable not
 * hosted by `MainShell` SHALL throw [IllegalStateException]. This makes
 * the boundary explicit: tab-internal navigation only flows through the
 * shell's state holder, never through some out-of-band fallback.
 *
 * `ViewModel`s cannot reach this; `CompositionLocal` is a Composable-
 * scoped concept. By design — see the change `add-adaptive-navigation-shell`'s
 * design.md decision D2: `MainShellNavState` is Compose-owned, and the
 * MVI-effect pattern (`UiEffect.Navigate(target)`) is the canonical way
 * for a VM to influence tab-internal navigation. Threading the holder
 * through every screen Composable's signature would defeat the purpose
 * — the local is a deliberate seam, not an implicit-dependency cliff.
 *
 * Allowlisted in `.editorconfig`'s `compose_allowed_composition_locals`
 * so ktlint's `compose:compositionlocal-allowlist` rule lets it through,
 * matching the convention used by `LocalClock` and `LocalNubecitaTokens`.
 */
val LocalMainShellNavState: ProvidableCompositionLocal<MainShellNavState> =
    compositionLocalOf {
        error(
            "MainShellNavState not provided. Wrap your composable in MainShell or call " +
                "CompositionLocalProvider(LocalMainShellNavState provides …).",
        )
    }
