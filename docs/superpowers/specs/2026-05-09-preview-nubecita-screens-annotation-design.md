# `@PreviewNubecitaScreenPreviews` — design

**Date:** 2026-05-09
**Driver:** `nubecita-da8` (Feed compose FAB at Medium/Expanded). Surfaced when adding an Expanded-width screenshot fixture for the new `LargeFloatingActionButton` branch — manual `widthDp` / `heightDp` constants felt wrong, and stacking AndroidX's `@PreviewScreenSizes` + `@PreviewLightDark` doesn't produce the cross-product we need (concatenation, not multiplication).
**Scope:** A custom multi-preview annotation owned by Nubecita and the migration of the FAB screenshot fixture to use it.

## Why

We have width-class-conditional UI (Feed compose FAB sizing, composer adaptive container, list-detail panes) and need screenshot regression coverage at every M3 width-size-class bucket × theme. The off-the-shelf options fall short:

- `@PreviewScreenSizes` includes a 1920×1080 Desktop fixture we don't ship for, plus Phone-Landscape and Tablet-Landscape that we don't currently target. Adopting it for full-screen tests would inflate our baseline-PNG maintenance for sizes we never deploy.
- Stacking `@PreviewScreenSizes` + `@PreviewLightDark` does NOT cross-multiply. Compose tooling concatenates the lists of `@Preview`s declared by multi-preview annotations; it does not compute the cross-product. Stacking the two yields 6 + 2 = 8 fixtures, none of which cover Foldable-Dark or Tablet-Dark — exactly the regression catches we want. The cross-product has to be hand-rolled inside one annotation.
- Hand-pinning device specs at every fixture site (e.g., `widthDp = 840`) puts magic numbers across the codebase and provides no enforcement that we're hitting M3-meaningful breakpoints.

A Nubecita-owned multi-preview annotation that bundles the cross-product fixes all three.

## Decisions

### 1. Three width buckets — Phone (411dp), Foldable (673dp), Tablet (1280dp)

Maps 1:1 to M3 Compact / Medium / Expanded. We deliberately drop:

- **Desktop (1920dp)** — we don't ship to ChromeOS / desktop. Maintaining baselines for this bucket is pure tech debt.
- **Phone-Landscape and Tablet-Landscape** — we don't have orientation-conditional layouts; Tablet (1280×800) already covers the wide canvas case. If orientation-conditional UI ever ships, that gets its own targeted fixture, not a global sweep.

Foldable (673dp) explicitly stays. The Medium bucket is the highest-risk width class — phones (411dp) are tightly constrained, tablets (1280dp) usually trigger explicit two-pane layouts, but foldables (673dp) sit in an awkward middle where UI elements stretch unnaturally without triggering tablet-level treatment. Skipping the Medium bucket would silently ship regressions to foldable users.

### 2. Bundle Light/Dark inside the annotation (6 hand-rolled `@Preview`s)

Stacking `@PreviewLightDark` + a custom screen-size annotation does NOT cross-multiply — Compose tooling concatenates the lists. Stacked: 3 sizes (light) + 2 themes (default device) = 5 fixtures, with zero foldable-dark or tablet-dark coverage.

To get the cross-product, the 6 `@Preview`s have to be declared explicitly inside one annotation. We pay the verbosity cost in one place (the annotation definition) to avoid the trap at every consumer site.

### 3. Lives in `:designsystem`

`:designsystem` is already a universal upstream — every Compose-using module depends on it for `NubecitaTheme` and post-card primitives. Adding the annotation here makes it visible everywhere with zero new wiring. `androidx-compose-ui-tooling-preview` is already on every Compose module's classpath via the `nubecita.android.library.compose` and `nubecita.android.application` convention plugins, so consumers can import `@Preview` and `Devices` independently.

A new `:core:design-preview` module was considered and rejected for V1: a single ~15-line annotation file doesn't justify the new-module overhead (registration, namespace, README entry). If preview tooling later grows beyond a single annotation (fixture helpers, golden-image utilities, etc.), spinning out a dedicated module is a small refactor.

### 4. Name: `@PreviewNubecitaScreenPreviews`

The name stutters intentionally because two lint rules active in this project disagree:

- **ktlint** `compose:preview-annotation-naming` requires multi-preview annotations to start with the `Preview` prefix (matching AndroidX's `@PreviewScreenSizes` / `@PreviewLightDark` style).
- **Slack `compose-lints`** `ComposePreviewNaming` (`https://slackhq.github.io/compose-lints/rules/#naming-multipreview-annotations-properly`) requires multi-preview annotations to end with the literal `Previews` plural suffix. The `Screens` plural noun (mirroring `@PreviewScreenSizes`) is rejected — the rule wants the literal string `Previews`.

The only name that satisfies both rules is `Preview…Previews`. Alternatives considered and rejected:

- Suppress one of the rules: bumps the cost of every future multi-preview annotation; introduces a per-call-site decision about which lint to disable.
- Rely on lint baselines: hides the conflict but accumulates baseline drift.
- Pick one rule's convention and disable the other globally: a defensible choice, but doing it as part of an unrelated FAB-PR feels out of scope.

A short note inside the annotation's KDoc calls out the stutter so a future reader doesn't try to "clean up" the name.

### 5. `showSystemUi = true`

Matches `@PreviewScreenSizes`'s upstream default. For full-screen tests this inflates the canvas with status bar + gesture bar, so Scaffold inset handling and FAB-vs-nav-bar position are visually validated — exactly the regression class this annotation exists to catch. Existing in-repo fixtures use `showBackground = true` only; we don't force a migration of those — only newly-adopted fixtures pick up `showSystemUi`, and the inconsistency is bounded to the one fixture this PR migrates.

## Annotation source

`designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/PreviewNubecitaScreenPreviews.kt`:

```kotlin
package net.kikin.nubecita.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/**
 * Multi-preview that sweeps the three Material 3 width-size-class buckets
 * Nubecita ships against (Compact / Medium / Expanded) × Light / Dark.
 *
 * Use on full-screen render-state fixtures where width-class branching
 * (FAB sizing, list-detail panes, adaptive containers) needs regression
 * coverage at every bucket. Don't use on standalone components — write
 * `@Preview` directly with the one device spec you need.
 *
 * Nubecita-specific subset of `@PreviewScreenSizes`: drops Phone-Landscape,
 * Tablet-Landscape, and Desktop (we don't target ChromeOS), keeps the
 * Foldable bucket because that's where width-class regressions hide.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Phone Light", device = Devices.PHONE, showSystemUi = true)
@Preview(
    name = "Phone Dark",
    device = Devices.PHONE,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Foldable Light", device = Devices.FOLDABLE, showSystemUi = true)
@Preview(
    name = "Foldable Dark",
    device = Devices.FOLDABLE,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Tablet Light", device = Devices.TABLET, showSystemUi = true)
@Preview(
    name = "Tablet Dark",
    device = Devices.TABLET,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class PreviewNubecitaScreenPreviews
```

## Migration scope (this PR)

| File | Change |
|---|---|
| `designsystem/src/main/kotlin/.../preview/PreviewNubecitaScreenPreviews.kt` | **New file** — the annotation. |
| `feature/feed/impl/src/main/kotlin/.../FeedScreen.kt` | Keep the already-applied `LargeFloatingActionButton` import + `currentWindowAdaptiveInfoV2()` branch. |
| `feature/feed/impl/src/screenshotTest/kotlin/.../FeedScreenScreenshotTest.kt` | Replace the two `@Preview(name = "loaded-with-compose-fab-…")` annotations on `FeedScreenLoadedWithComposeFabScreenshot` with `@PreviewNubecitaScreenPreviews`. Delete the temporary `FeedScreenLoadedWithComposeFabExpandedScreenshot` function. Delete the temporary `EXPANDED_WIDTH_DP` / `EXPANDED_HEIGHT_DP` constants. |
| Existing `loaded-light` / `loaded-refreshing-…` / etc. fixtures | **Not migrating.** Each is a stable baseline; touching them = baseline rebase. Future PR can opt them in one-by-one. |
| Composer / PostDetail / other modules' screenshot tests | **Not migrating.** Out of scope. |
| Module `build.gradle.kts` files | **No changes.** Every Compose module already depends on `:designsystem`. |

**Baseline-PNG impact:** existing 2 baselines (`loaded-with-compose-fab-light/dark`) replaced by 6 (`Phone Light`, `Phone Dark`, `Foldable Light`, `Foldable Dark`, `Tablet Light`, `Tablet Dark`). Net add: +4 PNGs.

## Test plan

1. `./gradlew :designsystem:compileDebugKotlin` — annotation compiles.
2. `./gradlew :feature:feed:impl:compileDebugKotlin :feature:feed:impl:compileDebugScreenshotTestKotlin` — FeedScreen and screenshot test compile against the new annotation.
3. `./gradlew :feature:feed:impl:updateDebugScreenshotTest` — generate the 6 new baselines and remove the 2 old ones.
4. Eyeball the 6 new PNGs:
   - **Phone Light/Dark** (411dp): 56dp `FloatingActionButton` bottom-right.
   - **Foldable Light/Dark** (673dp): 96dp `LargeFloatingActionButton` bottom-right (the spec scenario "Compose FAB scales to Large at Expanded width" — bigger FAB).
   - **Tablet Light/Dark** (1280dp): 96dp `LargeFloatingActionButton` bottom-right.
5. `./gradlew :feature:feed:impl:validateDebugScreenshotTest` — pinned baselines pass.
6. `./gradlew spotlessCheck lint :designsystem:lint :feature:feed:impl:lint` — style + lint clean.

## Out of scope (future bd issues if demanded)

- Migrating the rest of `FeedScreenScreenshotTest.kt`'s fixtures to `@PreviewNubecitaScreenPreviews`.
- Migrating `ComposerScreenScreenshotTest.kt` / `ComposerDiscardDialogScreenshotTest.kt` (the latter has its own width-bucket-specific fixtures that don't cleanly map to a 3-bucket sweep).
- Adding a separate `@PreviewNubecitaScreenPreviewsNoTheme` for screens that need only width-class sweeps without theme variation.
- Adding a `@PreviewNubecitaFontScales` for accessibility regression coverage.
- Promoting `androidx-compose-ui-tooling-preview` to `api` in the `:designsystem` module — not needed today since every Compose module already pulls it via convention plugins.

## Acceptance

- `openspec validate --all --strict` passes (no spec changes in this PR — annotation is code).
- `./gradlew :feature:feed:impl:validateDebugScreenshotTest` passes against the new 6 baselines.
- The Foldable and Tablet baselines visually show the larger 96dp `LargeFloatingActionButton` — visually distinguishable from the Phone fixtures' 56dp FAB.
- No new `widthDp = …` magic numbers in any screenshot test.
