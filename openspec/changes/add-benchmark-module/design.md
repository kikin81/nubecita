## Context

Nubecita is a Compose-only Android client whose two headline product claims are "fast cold start" and "120hz scroll." Neither claim has any in-tree measurement. The perf epic (`nubecita-crmi`) collects four related tickets that all depend on a Macrobenchmark harness existing first: this change (`crmi.1`), startup-profile generation (`crmi.2`), extended baseline profile (`crmi.3`), and a Compose-Tracing-instrumented hero benchmark (`crmi.5`).

Current state:

- `:app` is a single-APK module with no separate measurement variant.
- CI has an existing `instrumented` job on `ubuntu-latest` that uses `reactivecircus/android-emulator-runner` against a cached Pixel-6 / API-35 / x86_64 AVD, gated by the `run-instrumented` PR label.
- The version catalog (`gradle/libs.versions.toml`) holds no `androidx.benchmark.*` entries today.
- `:feature:feed:impl/FeedScreen` renders a `LazyColumn` with no stable `testTag` — UIAutomator can only find it via Compose-merged semantics today, which is brittle across recomposition.

Constraints:

- AGP 9 + Kotlin 2.3.21 + JDK 17 (Foojay toolchain). Macrobenchmark library + Gradle plugin minimum versions must be AGP-9-compatible.
- The composite build at `build-logic/convention` is the only place where Android plugin configuration may live; any new Android module type needs a new convention plugin in that roster.
- Macrobenchmark requires a non-debuggable release-equivalent target APK with `profileable` enabled. `:app`'s current `release` build type is not `isDebuggable = true` (correct for production) and not `isProfileable = true` (wrong for benchmark).
- Macrobenchmark's `BaselineProfileRule` writes its output into the producer's `build/outputs` directory; `:app`'s `androidx.baselineprofile` plugin then consumes via the `baselineProfile { productionEnvironment("release") }` DSL. The producer ↔ consumer relationship has to be declared up-front even if no profile is generated yet (`crmi.2` ships the actual generator test).

Stakeholders:

- Author of the perf epic (this is the foundation; everything else blocks on it).
- Future PR reviewers who'll run `gh pr edit <num> --add-label run-bench` to opt in to the new CI job.
- The Play in-app-updates epic (filed but not yet started), which needs a cold-start baseline before any `AppUpdateManager.getAppUpdateInfo()` wiring can be evaluated for regression.

## Goals / Non-Goals

**Goals:**

- Ship a `:benchmark` module that runs `StartupBenchmark` (`COLD`/`WARM`/`HOT` × `StartupTimingMetric`) and `FeedScrollBenchmark` (UIAutomator-driven scroll × `FrameTimingMetric`) locally via `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`.
- Establish the `:benchmark` ↔ `:app` baseline-profile producer/consumer link so `crmi.2` can land a `BaselineProfileGenerator` without re-doing wiring.
- Add exactly one new convention plugin (`nubecita.android.benchmark`) so future macrobench-adjacent modules (if any) have a single source of truth.
- Capture an initial local baseline (cold/warm startup, scroll frame timing) on a known device so follow-up tickets have something to regress against.

**Non-Goals:**

- Microbenchmarks (`androidx.benchmark:benchmark-junit4`) — out of scope; we measure user-perceived metrics only.
- **CI integration of any kind.** Deferred to a follow-up epic — a fake-network/auth layer (Strategy 1 in the deferral-epic notes: asset-backed `benchmark` flavor + DI substitution) is a prerequisite, and cloud-runner CPU variance pushes the gate's role from "precise measurement" to "catastrophic-regression smoke detector" with a different toolchain (`benchmark-action/github-action-benchmark` etc.).
- **Regression detection / PR threshold gating.** Subsumed by the CI deferral.
- **Auth / feed-fixture preset.** Subsumed by the CI deferral — both problems are solved by the same fake-network flavor.
- Compose Tracing / Composition-Tracing per-composable metrics — `crmi.5`'s scope.
- Generating or shipping an actual baseline profile in this change — `:benchmark`'s producer role is declared, but no `BaselineProfileGenerator` test is written here (`crmi.2`'s scope).
- Touching the `release` build type's `isDebuggable` or `isProfileable` — the `androidx.baselineprofile` plugin auto-generates `benchmarkRelease` + `nonMinifiedRelease` variants alongside `release`, so production behavior stays untouched.

## Decisions

### Decision 1 — Macrobenchmark over Microbenchmark

Use `androidx.benchmark:benchmark-macro-junit4`, not `androidx.benchmark:benchmark-junit4`.

**Why:** Macrobenchmark drives a separate-process target APK via UIAutomator and reports user-perceived metrics (startup wall-clock time, frame-render time, jank rate). Microbenchmark measures hot-loop wall time inside the same process and is for "is this allocator faster than the other one" questions — we have no use for that yet. The two are orthogonal; choosing Macrobenchmark doesn't preclude adding Microbenchmark later if a specific in-process question arises.

**Alternatives considered:**

- Skia/Choreographer manual instrumentation — would let us emit `FrameMetrics` from the running app, but it doesn't generalize to cold-start measurement (the app isn't running yet) and would have to be conditionally compiled out of release builds. Macrobenchmark sidesteps both problems by running in a separate test process against the unmodified app APK.
- Firebase Test Lab Game Loop tests — runs against real devices, but the cost model is per-minute-per-device and the result format is a video, not parseable metrics. Not a fit for PR-opt-in CI runs.

### Decision 2 — CI macrobench is deferred to a separate epic

Originally scoped here. The local run on Pixel 10 Pro XL surfaced two facts that make CI a meaningfully larger problem than wiring up a job:

1. **FeedScrollBenchmark requires a signed-in install.** A fresh CI install routes to Login, the feed list never appears, the `feed_list` testTag selector times out. Solving this needs an asset-backed `benchmark` product flavor in `:app` — a `FakeBlueskyAuthRepository` + `FakeFeedRepository` under `src/benchmark/` that returns hardcoded JSON, swapped via DI when the benchmark flavor is active. Plus `flavorSelection { missingDimensionStrategy("environment", "benchmark") }` on `:benchmark`.
2. **Cloud runner variance overwhelms the signal.** Macrobench expects a physical, locked device for usable absolute numbers. On a noisy `ubuntu-latest` runner, startup TTID fluctuates 30–50% between adjacent runs of the same commit. CI's role has to shift from "what's the number" to "did we just 3× a metric vs. baseline" via `benchmark-action/github-action-benchmark@v1` (or similar) comparing against a stored gh-pages history with `alert-threshold: '150%'`-style guard rails. That's a CI-pipeline architecture choice that wants its own epic.

Local runs on physical hardware (Pixel 10 Pro XL, etc.) remain the source of absolute truth for this PR's baseline.

**Alternatives considered:**

- Per-PR opt-in via `run-bench` label, like the existing `run-instrumented`. Rejected because per-PR runs still pay the cloud-variance tax, and the more useful cadence is a **nightly scheduled job against `main`** that builds a daily historical graph — the regression introduced by a merged PR shows up as a step in the graph the next morning.
- Gradle Managed Devices. Doesn't fix the auth-state or variance problems, just changes the emulator image.
- Self-hosted runner with a real Pixel. Best signal/noise, but adds physical-hardware operational surface that's hard to justify before we have a single regression caught by CI.

### Decision 3 — Let `androidx.baselineprofile` generate the benchmark variants; do NOT hand-roll a `benchmark` build type

`:app` applies the `androidx.baselineprofile` plugin (alongside `nubecita.android.application`). The plugin auto-generates two variants off the existing `release` build type:

- `benchmarkRelease` — R8-minified, profileable, debug-signed. The actual Macrobenchmark target.
- `nonMinifiedRelease` — non-R8-minified, profileable, debug-signed. Used by a future `BaselineProfileGenerator` test (filed as `nubecita-crmi.2`).

`:benchmark` declares no build types of its own — the matching producer-side variants flow through automatically when `targetProjectPath = ":app"` is set.

**Why:** This is the AndroidX-canonical setup. A hand-rolled `benchmark` build type would either duplicate the plugin's `benchmarkRelease` (and collide with the plugin's naming, producing awkward `assembleBenchmarkBenchmark`-style task names verified during local bring-up) or sit unused. Production `release` stays untouched (non-profileable, non-debuggable) — the plugin operates by *adding* variants alongside it, not by mutating its flags.

**Why NOT a flavor:** Flavors multiply the entire variant matrix (`debug × prod`, `release × prod`, …). The auto-generated build-type variants are strictly cheaper. Flavors stay reserved for the eventual `benchmark` *environment* flavor that the CI follow-up (`nubecita-crmi.6`) introduces to swap real network for fake repositories — orthogonal to this decision.

**Alternatives considered:**

- Hand-roll a `benchmark` build type with `initWith(getByName("release"))` + `isProfileable = true`. Tried during local bring-up; collides with the plugin's auto-generated `benchmarkRelease` variant and produces task names like `assembleBenchmarkBenchmark`. Dropped.
- Enable `isProfileable = true` on `release` directly. Rejected — ships the production APK with profileability enabled, which Macrobench's own docs warn against (negligible perf impact in practice, but unnecessary attack surface).
- Gate `isProfileable` via a `-PenableProfileable=true` Gradle property on `release`. Works, but obscures the variant identity in IDE pickers and Logcat. The plugin-generated `benchmarkRelease` is easier to grep and matches AndroidX templates.
- Use a product flavor with `applicationIdSuffix = ".benchmark"` so the bench install never collides with a Play install on dev devices. Considered after hitting the signing-collision issue on a Pixel Fold; rolled forward instead because the auto-generated `benchmarkRelease` is debug-signed already and the flavor-with-suffix work belongs in the CI epic (it'll need the suffix for the fake-network variant anyway).

### Decision 4 — Stable `testTag` on `FeedScreen`'s `LazyColumn`

`:feature:feed:impl/FeedScreen` gains a `Modifier.testTag(FeedTestTags.LIST)` (constant `"feed_list"`) on its top-level `LazyColumn`, and a small public-API object `FeedTestTags` exposing the constants. The `FeedScrollBenchmark` uses the **single-arg** `device.findObject(By.res("feed_list"))` — Compose's `testTagsAsResourceId = true` (set on `MainActivity`'s root semantics modifier) surfaces tags as **bare** `resource-id` values with no package qualifier, so the two-arg `By.res(packageName, id)` form (which builds `packageName:id/<id>`) silently never matches.

**Why this seam, not "scroll the whole window":** Macrobenchmark's `device.swipe(...)` against raw screen coordinates is order-dependent on layout and breaks on any future top-bar / sticky-header change. A testTag-anchored UIAutomator selector survives refactors that move the list under different scaffolding.

**Why `testTag` on a single composable, not a semantics merge boundary:** We want UIAutomator to find one specific node, not a merged subtree. Compose-Material3's `LazyColumn` already terminates merge at the list root by default.

**Alternatives considered:**

- Add a `BenchmarkDriver` interop helper inside `:feature:feed:impl` that exposes a `scrollFeed()` extension on `UiDevice`. Rejected — couples the production module to a test-only library and inverts the dependency direction. The macrobench module imports nothing from `:feature:feed:impl`; the contract is a string constant in the source tree and a `testTag` modifier in `FeedScreen`.
- Use Compose's semantics `contentDescription` instead of `testTag`. Rejected — `contentDescription` is an accessibility surface that screen readers will speak. A `testTag` keyed to the resource-id pathway is invisible to users and dedicated to test harnesses.

### Decision 5 — Single benchmark module, two test classes

`:benchmark` contains exactly two `@RunWith(AndroidJUnit4::class)` classes:

- `StartupBenchmark` — `@Parameterized` over `StartupMode.values()`.
- `FeedScrollBenchmark` — single test method.

**Why not split into `:benchmark:startup` / `:benchmark:scroll`:** Macrobenchmark modules are configuration-heavy (each needs its own `androidx.baselineprofile` consumer wiring on `:app` if both are baseline-profile producers; each pays its own emulator-boot tax in CI). A single module with two test classes is the AndroidX-canonical layout and the cost of splitting only pays off if the suite grows past ~10 classes with materially different runtime characteristics.

### Decision 6 — Capture an initial baseline locally, post to the epic comment thread

A baseline run on Pixel 10 Pro XL (Android 16, SDK 36) under `:app:benchmarkRelease` + `CompilationMode.None`, 5 iterations:

- StartupBenchmark: COLD `timeToInitialDisplayMs` median 253.75 (235.82 / 289.12 min/max); WARM 63.35 (58.99 / 77.43). HOT failed to read metrics — see Risks below.
- FeedScrollBenchmark: `frameDurationCpuMs` P50 3.96 / P95 6.38 / P99 10.45 ms; `frameOverrunMs` P95 −5.28 ms (negative ⇒ under the 8.33 ms 120 Hz budget). 5 iterations × ~1188 frames each.

These numbers go in a comment on the `nubecita-crmi` epic so `crmi.2`/`crmi.3`/`crmi.5` have a documented starting point. No PR comment automation here — that's part of the CI deferral.

### Decision 7 — `testTagsAsResourceId = true` is enabled UNCONDITIONALLY at the MainActivity root, NOT gated to a benchmark-only build

The flag is set in `MainActivity.setContent { ... }`'s root `Surface` semantics for every variant the app ships. We do **not** gate it behind a `BuildConfig` field, a source-set substitution, or an injected `AppConfiguration` flag.

**Why:**

- It's metadata-only. The flag maps Compose `Modifier.testTag(...)` values into `AccessibilityNodeInfo.viewIdResourceName` — the same field View-based apps populate from `android:id` XML attributes. TalkBack does **not** announce that field. The flag is invisible to real users.
- View-based Android apps have always exposed their `android:id` strings to any service that can read the accessibility tree. Compose without the flag is strictly *less* exposed than legacy View code; enabling it brings parity, not a new privacy surface.
- AndroidX explicitly endorses leaving it on in production. The Now-in-Android sample, the Macrobenchmark docs, and Google's perf cookbook all do it the same way.
- Gating via `BuildConfig` would introduce real build-cost pain. `BuildConfig.java` is regenerated whenever any field on it changes, and incremental compilation invalidates every Kotlin compile unit that reads it — over the 40+-module shape of this repo, a build-flag tweak would turn a 2-second incremental into a 45-second rebuild. That's a permanent perf tax to gate a metadata flag that has zero user-visible effect.
- Gating via source-set substitution (`src/production/.../BenchmarkSemantics.kt` no-op vs. `src/benchmark/.../BenchmarkSemantics.kt` setting the flag) would be the cleaner architecture if we needed to gate. But we don't need to, so we don't introduce the indirection. If a future audit decides the flag should be benchmark-only after all, the source-set pathway is the right home for it — owned by the `crmi.6` flavor work, not by this change.

**Alternatives considered:**

- `BuildConfig.IS_BENCHMARK` field on `:app` gating the modifier. Rejected on build-cost grounds (above).
- Per-flavor source-set substitution. Rejected because there are no flavors yet (`crmi.6`'s scope) and adding one solely for this would be premature.
- Injected `AppConfiguration` value via Hilt that returns the flag. Rejected — runtime indirection for a static, build-time-known value.

## Risks / Trade-offs

- **Risk:** The Feed bench's UIAutomator selector breaks if `FeedScreen` is restructured so the `LazyColumn`'s testTag ends up on a different node. → **Mitigation:** The `FeedTestTags` constant becomes part of the `feature-feed` capability's testable contract (covered in the spec). PRs that move the tag should update the bench in the same change. `FeedTestTagsTest` pins the string value as a unit-test guard.

- **Risk:** Compose `testTagsAsResourceId` exposes the tag *without* a package qualifier (`resource-id="feed_list"`, not `"net.kikin.nubecita:id/feed_list"`). UIAutomator's `By.res(packageName, id)` two-arg form silently never matches such tags. → **Mitigation:** Bench uses the single-arg `By.res(id)` form; a code comment in `FeedScrollBenchmark.kt` explains why. Discovered the hard way during the local Pixel 10 Pro XL bring-up — both Macrobench docs and the AndroidX templates assume the View-id pathway.

- **Risk:** `androidx.baselineprofile` (the Gradle plugin) and the `androidx.benchmark:benchmark-macro-junit4` library — both versioned under the `androidxBenchmark` version-ref in the catalog — might not yet support AGP 9. → **Resolution:** Verified empirically — `1.4.1` fails to apply the plugin under AGP 9 ("Module `:app` is not a supported android module"), but `1.5.0-alpha06` works. Pinned the version-ref to `1.5.0-alpha06` until a stable AGP-9-compatible line ships.

- **Risk:** `StartupBenchmark.HOT` fails to read any metrics on Pixel 10 Pro XL — Macrobench can't observe a fresh first-frame because the activity is already in the foreground and Splash routing completes faster than the metric collector. → **Mitigation:** File as a smaller follow-up. COLD and WARM cover the use cases we actually care about today (cold launch + warm resume); HOT was always the lowest-value of the three.

- **Risk:** `FeedScrollBenchmark` requires a manually-signed-in install — Macrobench installs a fresh APK with empty DataStore, so the app routes to Login. → **Mitigation:** Subsumed by the CI-deferral epic. The same fake-network/auth flavor that unblocks CI also primes the bench's setup phase deterministically.

- **Trade-off:** Declaring `:benchmark` as a baseline-profile producer in this change without actually generating a profile means the `:app`-side `baselineProfile` wiring has a slightly-odd "producer wired but no profile yet" state until `crmi.2` lands. → **Acceptance:** This is the AndroidX-recommended split (the plugin's docs are explicit that producer wiring should land first). Alternative would be to land all of `crmi.1` + `crmi.2` in one PR, which is too large.

## Migration Plan

Purely additive. No rollback / migration concern for users, no impact on `main`'s production build outputs:

1. The PR lands `:benchmark` + the convention plugin + the baselineprofile producer wiring + the `FeedTestTags` constant + `testTagsAsResourceId` on MainActivity.
2. Author runs the bench locally (Pixel Fold / Pixel 10 Pro XL / equivalent) and captures the baseline numbers on the `nubecita-crmi` epic comment thread.
3. CI is untouched. The follow-up epic adds the `benchmark` flavor, fake repositories, regression-tracking GitHub Action, and decides per-PR vs scheduled cadence.

Rollback: revert the PR. No persisted state changes, no Play Store impact, no end-user-visible behavior.

## Open Questions

- **Default iteration count: 5 or 10?** Macrobench's default is 5. Going to 10 doubles wall-clock cost per local run. **Resolution:** Stay at the AndroidX default (5) for this change; revisit if variance shows ≥20% run-to-run drift on the same commit.
