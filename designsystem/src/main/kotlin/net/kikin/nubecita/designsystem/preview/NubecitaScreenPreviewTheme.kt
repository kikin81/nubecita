package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot + preview theme wrapper. Paints the screen-canvas role
 * (`MaterialTheme.colorScheme.surface`) at full size so dark-mode `@Preview`
 * fixtures stop fracturing into transparent components over the IDE's
 * default white canvas.
 *
 * Use this in every `*ScreenshotTest.kt` fixture instead of [NubecitaTheme]
 * directly. Production composables and androidTest interaction tests stay
 * on [NubecitaTheme] — the production `Scaffold` / modal `Surface` paints
 * the canvas in real app code; this wrapper exists only to substitute for
 * that paint in headless preview/screenshot rendering.
 *
 * Two pinned defaults:
 *
 * - `dynamicColor = false` — Layoutlib's dynamic-color fallback varies
 *   across emulator configurations, so screenshot baselines stay
 *   deterministic only with the brand color scheme.
 * - `Modifier.fillMaxSize()` on the `Surface` — guarantees full canvas
 *   coverage so a component using a custom `LayoutModifier` or edge-to-edge
 *   drawing can't leave a transparent gutter the IDE renderer paints white.
 *
 * Full role contract: `docs/design-system/surface-roles.md`.
 *
 * `compose:modifier-missing-check` is suppressed deliberately: this wrapper
 * paints the full preview canvas via `Modifier.fillMaxSize()` and that's
 * the contract. Allowing callers to override the `Surface` modifier would
 * defeat the purpose (a caller-supplied `.size(...)` would shrink the
 * canvas and reintroduce the IDE-white-gutter regression we're fixing).
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun NubecitaScreenPreviewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    NubecitaTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            content()
        }
    }
}
