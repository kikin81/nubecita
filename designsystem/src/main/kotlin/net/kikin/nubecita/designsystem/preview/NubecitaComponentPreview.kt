package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Component-level preview/screenshot wrapper. Use via
 * `@PreviewWrapper(NubecitaComponentPreview::class)` on any `@Preview` /
 * `@PreviewTest` fixture that previously inlined
 * `NubecitaTheme(dynamicColor = false) { ... }`.
 *
 * Wraps content in [NubecitaTheme] + a `Surface(surfaceContainer)`
 * ancestor so:
 *
 * - `LocalContentColor` resolves to `onSurface`, fixing dark-mode text
 *   that previously fell back to `Color.Black` against a missing Surface
 *   ancestor (the bug nubecita-f6pz catalogues — `Acyn @acyn.bsky.social`
 *   rendering as black-on-dark in PostCard's `AuthorLine`).
 * - The backdrop matches the production composition: feed posts render
 *   inside a `Surface(surfaceContainer)` card, so `surfaceContainer` is
 *   the *right* visual context for component baselines too.
 *
 * **NO `Modifier.fillMaxSize()`.** That's [NubecitaCanvasPreviewTheme]'s
 * contract. Component fixtures want the Surface to wrap their natural
 * bounds — atoms like avatars and buttons would balloon to the preview
 * viewport otherwise (the regression that motivated reverting fixtures
 * from `NubecitaCanvasPreviewTheme` in PR #290).
 *
 * **Discovery contract** — verified against:
 *
 * - Compose BOM `2026.05.01` (`ui-tooling`'s
 *   `PreviewUtils.android.kt:106` calls `instantiatePreviewWrapperProvider`)
 * - AGP screenshot plugin `0.0.1-alpha15`
 *
 * The plugin reflects the `wrapper` class out of the annotation,
 * instantiates it, and calls [Wrap] around the previewed Composable
 * before rendering. Confirmed end-to-end with a magenta-Surface diagnostic
 * fixture during the f6pz scoping experiment.
 *
 * Naming: paired with `NubecitaCanvasPreviewTheme` (full-bleed screen
 * canvas) and `@PreviewNubecitaScreenPreviews` (device-size annotation).
 * The three-way split is documented in
 * `docs/design-system/surface-roles.md`.
 */
class NubecitaComponentPreview : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) {
        NubecitaTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                content()
            }
        }
    }
}
