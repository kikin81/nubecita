package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * `CompositionLocal` exposing the outer-shell [Navigator] to descendant
 * composables — primarily the entry-provider blocks contributed by
 * feature modules that need to push or pop on the outer back stack
 * (e.g., the fullscreen media viewer escaping `MainShell`'s chrome).
 *
 * Provided by `:app/Navigation.kt` at the root of `MainNavigation`'s
 * composition; reads anywhere below get the same singleton-scoped
 * [Navigator] instance Hilt also injects into ViewModels for the
 * Splash/Login/Main outer-shell lifecycle.
 *
 * The local has **no default value** — reading it from a Composable not
 * hosted under `MainNavigation` SHALL throw [IllegalStateException].
 * Feature modules' entry blocks always render under `MainNavigation`'s
 * provider, so a missing provider indicates a wiring bug worth surfacing
 * immediately.
 *
 * Allowlisted in `.editorconfig`'s `compose_allowed_composition_locals`
 * so ktlint's `compose:compositionlocal-allowlist` rule lets it through,
 * matching the convention used by [LocalMainShellNavState] /
 * [LocalScrollToTopSignal] / [LocalComposerLauncher].
 */
@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalAppNavigator: ProvidableCompositionLocal<Navigator> =
    compositionLocalOf {
        error(
            "LocalAppNavigator not provided. Wrap your composable in MainNavigation or call " +
                "CompositionLocalProvider(LocalAppNavigator provides …).",
        )
    }
