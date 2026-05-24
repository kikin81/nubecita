package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot + preview theme wrapper that paints the screen-canvas role
 * (`MaterialTheme.colorScheme.surface`) at full size so dark-mode `@Preview`
 * fixtures stop fracturing into transparent components over the IDE's
 * default white canvas.
 *
 * Use this for **screen-level**, dialog, or pane-level fixtures — anything
 * whose intent is to render against a full-bleed canvas. Component-level
 * fixtures (atoms like avatars, buttons, single rows) should stay on
 * [NubecitaTheme] directly; the wrapper's `Modifier.fillMaxSize()` would
 * cause atoms with intrinsic-fill behavior to balloon to the preview
 * viewport instead of rendering at their natural bounds.
 *
 * Production composables and androidTest interaction tests stay on
 * [NubecitaTheme]. In real app code the canvas paint is owned by the
 * `Scaffold` / modal `Surface`; this wrapper exists only to substitute
 * for that paint in headless preview/screenshot rendering.
 *
 * Naming: this wrapper is paired with — but distinct from — the
 * `@PreviewNubecitaScreenPreviews` multi-preview annotation. The annotation
 * sweeps a *device-screen-size* matrix (Phone/Foldable/Tablet × Light/Dark);
 * this wrapper paints a *screen-canvas* (the backdrop). The "Canvas" in
 * the wrapper name vs "Screen" in the annotation name makes the boundary
 * explicit.
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
 * `ktlint:compose:modifier-missing-check` (ktlint) + `ComposeModifierMissing`
 * (Android Lint) are suppressed deliberately: this wrapper paints the full
 * preview canvas via `Modifier.fillMaxSize()` and that's the contract.
 * Allowing callers to override the `Surface` modifier would defeat the
 * purpose (a caller-supplied `.size(...)` would shrink the canvas and
 * reintroduce the IDE-white-gutter regression we're fixing). Both rules
 * come from `com.slack.lint.compose:compose-lints` but register through
 * different toolchains, so both keys are needed.
 */
@Suppress("ktlint:compose:modifier-missing-check", "ComposeModifierMissing")
@Composable
fun NubecitaCanvasPreviewTheme(
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
