## Why

Nubecita has crossed ~1.66K device acquisitions but holds only **2 ratings** (3.5★), because the app never asks users to rate it. With such a tiny sample, a single low review swings the public score; prompting engaged users is the fastest way to stabilize and lift it. We will prompt at a genuine moment of delight (just after publishing a post) using Google's native In-App Review flow, gated so only retained users are asked.

## What Changes

- New self-contained **`:core:review`** module wrapping the Google Play In-App Review API behind an SDK-agnostic `ReviewManager` boundary (no Play Core type leaks past the module), mirroring `:core:billing`'s boundary + product-flavor split.
- After a successful post publish (`ComposerEffect.OnSubmitSuccess`), the composer Composable invokes `ReviewManager.onPostPublished(activity)` on the host **Activity's** `lifecycleScope` so the flow survives the composer route popping.
- A **conservative eligibility gate** (DataStore-backed counters in `:core:review`): request only when the user has ≥3 successful posts AND ≥3 days since first launch, at most once per 90 days, lifetime cap of 3 requests.
- A manual **"Rate Nubecita"** row in Settings → About that opens the Play Store listing via a `market://` intent (https fallback) — **not** the in-app API (per Google's guidance that a button-triggered review can render nothing once the quota is hit).
- The flow is **fully fail-silent** and **inert in the bench flavor** (no Play/network calls in keyless/macrobenchmark builds).

### Baseline deviations (called out per convention)

- **New external dependency:** `com.google.android.play:review` (Google Play Core; first Play Core dependency in the app). Test-only `com.google.android.play:review-ktx` for Google's `FakeReviewManager`.
- **DataStore, not Room,** for the four eligibility counters (tiny key/value state owned by `:core:review`, matching how `:core:billing` owns its own state rather than going through `:core:database`).

## Capabilities

### New Capabilities
- `app-review`: In-app rating prompts via the Google Play In-App Review API — the post-publish trigger, the conservative eligibility policy, fail-silent/bench-inert behavior, and the manual Settings → Play Store deep-link entry point.

### Modified Capabilities
<!-- None. The composer continues to emit OnSubmitSuccess and dismiss exactly as before; consuming that effect to drive a review request is implementation wiring, not a spec-level change to feature-composer. -->

## Impact

- **New module:** `:core:review` (`src/main`, `src/production`, `src/bench`); registered in `settings.gradle.kts`, added to the version catalog.
- **`:feature:composer:impl`:** the composer screen Composable gains a side-effect on `OnSubmitSuccess` that calls `ReviewManager.onPostPublished(activity)` (launched on the Activity scope). No composer ViewModel/state changes.
- **`:feature:settings:impl`:** new "Rate Nubecita" row in the About section (relates to `nubecita-37to.7`).
- **`:app`:** production `AppInitializer` stamps `firstLaunchAt` once (alongside `RevenueCatInitializer`); the bench flavor never registers it.
- **Dependencies:** new `com.google.android.play:review`; reuses the already-present `kotlinx-coroutines-play-services` for `Task.await()`.
- **Tracking:** bd `nubecita-ijuv`.
