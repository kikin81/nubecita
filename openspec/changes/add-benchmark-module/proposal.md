## Why

Nubecita ships a 120hz scroll target and a "fast cold start" promise, but we have **no way to measure either**. Every claim about startup latency, baseline-profile effectiveness, or Feed scroll jank is currently engineering inference — we can't tell whether a refactor regressed cold-start by 200ms, whether the baseline profile is even being shipped/used, or whether a Compose-side change introduced frame drops on the timeline scroll.

The next three perf-epic tickets (`nubecita-crmi.2` startup profile, `nubecita-crmi.3` extended baseline profile, `nubecita-crmi.5` Compose-Tracing hero benchmark) all need a macrobenchmark harness to produce or consume measurements. This change lands that harness as the foundation.

There is also an external forcing function: the planned Play in-app-updates integration ([related work](https://developer.android.com/guide/playcore/in-app-updates)) has no published latency benchmarks for `AppUpdateManager.getAppUpdateInfo()`. We need a cold-start baseline on Nubecita **as-is** so a follow-up epic can attribute or rule out any regression introduced by a deferred `onResume` update check.

## What Changes

### New module: `:benchmark`

- New `:benchmark` module using `androidx.benchmark:benchmark-macro-junit4` (the Macrobenchmark library) under the `com.android.test` module shape, registered in `settings.gradle.kts` and built off a new `nubecita.android.benchmark` convention plugin so the configuration stays consistent with the rest of the build-logic roster.
- A `targetProjectPath = ":app"` link so `:benchmark` exercises the real release APK (the only configuration where R8 / startup-profile / baseline-profile effects show up).
- `:app` adds the reciprocal `androidx.baselineprofile` plugin so `:benchmark` is wired as a baseline-profile producer for the next ticket in the epic (`nubecita-crmi.2`). No profile is generated or shipped in this change — `:benchmark`'s role as a producer is just declared.
- **No hand-rolled `benchmark` build type** on either `:app` or `:benchmark`. The `androidx.baselineprofile` plugin auto-generates `benchmarkRelease` (R8-minified, profileable — the actual macrobench target) and `nonMinifiedRelease` (used by a future profile generator) variants off `:app`'s `release` build type; mutating `release` or adding a duplicate build type would collide with the plugin's naming. Production `release` stays untouched (non-profileable, non-debuggable).

### Initial benchmark suite

- `StartupBenchmark` — parameterized over `StartupMode.COLD` / `WARM` / `HOT`, reporting `StartupTimingMetric` (`timeToInitialDisplay` + `timeToFullDisplay`). `MainActivity` is the target. `CompilationMode.None`, `Partial(BaselineProfileMode.Disable)`, and (once nubecita-crmi.2 lands) `Full` will be parameterized in a follow-up; this change ships `None` only to keep the harness scope tight.
- `FeedScrollBenchmark` — launches the app, waits for the Feed list to appear (via a UIAutomator selector keyed on a stable `testTag` we add to `FeedScreen`'s `LazyColumn`), then performs a fixed-count scroll gesture and reports `FrameTimingMetric` (`frameDurationCpuMs` / `frameOverrunMs` p50/p95/p99).

### CI integration — deferred

CI macrobench is **deferred to a follow-up epic** (filed separately). Running Macrobenchmark on cloud CI runners is a meaningfully larger problem than wiring up a job, because:

- The `:benchmark` module runs in a separate process against the release-flavored APK. Real-network FeedScrollBenchmarks against the live AT Protocol have unbounded variance from network latency + media decode + flaky server responses — frame metrics become unusable as a regression signal.
- A signed-out CI install can never measure FeedScroll because there's no feed. We need an asset-backed `benchmark` flavor with a fake auth/feed layer that bypasses Bluesky entirely (handled via flavors + DI substitution at `:app:src/benchmark/...`).
- Cloud runner CPU contention skews absolute startup numbers by 30–50% even between adjacent runs of the same commit; CI can only catch *catastrophic* regressions (e.g. a misbehaving SDK init), not the 10–20 ms shifts we care about locally. The shape of the CI gate is "relative tracker / smoke detector" via something like `benchmark-action/github-action-benchmark@v1`, ideally a nightly scheduled run against `main` rather than per-PR opt-in.

These are real architectural questions, not config tweaks. Out of scope for this change.

### Initial reference numbers

Captured locally on a Pixel 10 Pro XL (Android 16, SDK 36), `:app:benchmarkRelease`, `CompilationMode.None`, 5 iterations per run:

- StartupBenchmark — COLD `timeToInitialDisplayMs` median 253.75 (235.82 / 289.12 min/max), WARM 63.35 (58.99 / 77.43). HOT mode failed to read metrics — known follow-up.
- FeedScrollBenchmark — `frameDurationCpuMs` P50 3.96 / P95 6.38 / P99 10.45 ms; `frameOverrunMs` P95 −5.28 (negative = under the 8.33 ms 120 Hz budget).

These numbers will be posted as a comment on `nubecita-crmi` so follow-up tickets have a documented baseline.

## Capabilities

### New Capabilities

- `benchmark-macrobenchmark`: defines the existence of a `:benchmark` Macrobenchmark module, the StartupBenchmark + FeedScrollBenchmark suite, the `androidx.baselineprofile`-plugin-driven variant contract on `:app` (`benchmarkRelease` + `nonMinifiedRelease` auto-generated, no hand-rolled build type), the baseline-profile producer relationship, and the screen-side `testTag` contract `FeedScreen` exposes for the scroll bench. CI integration is intentionally NOT in scope — deferred to a follow-up epic.

### Modified Capabilities

None — this is a brand-new capability surface. `:feature:feed:impl` does gain a stable `testTag` constant on its `LazyColumn`, but that's an additive testing affordance, not a behavioral change to the `feature-feed` capability's user-facing requirements.

## Impact

- **New modules**: `:benchmark` (added to `settings.gradle.kts`); new `nubecita.android.benchmark` convention plugin in `build-logic/convention`.
- **Affected modules**: `:app` (applies `androidx.baselineprofile`; the plugin auto-adds `benchmarkRelease` + `nonMinifiedRelease` variants off `release` — no hand-rolled build type), `:feature:feed:impl` (one new `testTag` constant on `FeedScreen`'s `LazyColumn`).
- **New deps in `gradle/libs.versions.toml`**:
  - `androidx.benchmark:benchmark-macro-junit4` (Macrobenchmark JUnit4 runner)
  - `androidx.test.uiautomator:uiautomator` (gesture driving + waitForObject)
  - `androidx.baselineprofile` Gradle plugin alias (producer wiring)
  - `androidx.benchmark.macro` Gradle plugin alias (consumer wiring on `:benchmark`)
- **CI workflow**: NOT touched in this change. CI macrobench (and the `run-bench` label) are deferred to the follow-up epic so they can be tackled with the fake-network-layer prerequisite.
- **Backwards compatibility**: additive. No existing module's classpath, ABI, or test runner changes. Existing CI jobs are untouched.
- **Deviation from defaults**: none. Macrobenchmark is the AndroidX-recommended (and only AndroidX-blessed) path for these measurements; we're not introducing any non-standard perf-measurement framework.

## Non-goals

- **Microbenchmarks (`androidx.benchmark:benchmark-junit4`).** Macrobenchmark covers the user-perceived metrics we actually care about (cold start, frame timing). Microbench is for in-process hot-loop measurement, which we have no use for today.
- **CI integration of any kind.** Deferred to a follow-up epic. The prerequisites — a `benchmark` product flavor with fake-network layer, a regression-comparison action, decisions about per-PR vs nightly scheduled cadence, emulator-side perf-warning suppression — are large enough to deserve their own scope.
- **Regression detection / threshold gating.** Subsumed by the CI-integration deferral. Also needs ≥2 historical data points to set tolerances against.
- **Compose Tracing / per-composable metrics.** Filed separately as `nubecita-crmi.5` ("Feed Hero benchmark with Compose Tracing"). The harness in this change is the prerequisite.
- **Auth / feed-fixture setup hooks.** `FeedScrollBenchmark` today requires a manually signed-in install. Wiring a deterministic preset is part of the CI-integration epic (it's the same mock-network problem).
- **HOT-mode startup measurement.** The current `StartupBenchmark.HOT` parameterization fails to read metrics on Pixel 10 Pro XL (Splash routing completes faster than the metric collector can observe). Smaller follow-up filed separately.
- **Gradle Managed Devices (GMD).** Same reasoning as before — not relevant now that there's no CI runner to optimize for.
- **Baseline profile generation itself.** `:benchmark` is declared as a baseline-profile producer here; the actual `BaselineProfileGenerator` test + `:app`'s consumption of the generated `baseline-prof.txt` are `nubecita-crmi.2`'s scope.
- **A `:benchmark` namespace under `net.kikin.nubecita.*`.** The module sits at the repo root (`:benchmark`, not `:core:benchmark` or `:test:benchmark`) — Macrobenchmark conventions place it as a top-level sibling of `:app`, and AndroidX's templates assume that layout. We follow the template.
