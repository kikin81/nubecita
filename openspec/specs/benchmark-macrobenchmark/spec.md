# benchmark-macrobenchmark Specification

## Purpose
The `:benchmark` AndroidX Macrobenchmark suite and the baseline-profile plugin wiring in `:app`: cold/warm/hot `MainActivity` startup measurement, Feed scroll frame timing, the stable test tags those benchmarks drive, and how results are captured.
## Requirements
### Requirement: `:benchmark` module exists as an AndroidX Macrobenchmark suite

The repository SHALL contain a top-level Gradle module `:benchmark` configured as an AndroidX Macrobenchmark suite via `nubecita.android.benchmark` convention plugin. The module MUST set `targetProjectPath = ":app"`, declare `experimentalProperties["android.experimental.self-instrumenting"] = true` for AGP 9, sit at repo root as a sibling of `:app`, and be declared a baseline-profile producer relative to `:app`.

#### Scenario: Module is registered and resolvable

- **WHEN** `./gradlew projects` is run from the repo root
- **THEN** the printed project tree contains `+--- Project ':benchmark'` as a top-level sibling of `:app`.

#### Scenario: Macrobenchmark plugin is applied

- **WHEN** `./gradlew :benchmark:dependencies` is run
- **THEN** the resolved classpath contains `androidx.benchmark:benchmark-macro-junit4` and `androidx.test.uiautomator:uiautomator` (the only Macrobenchmark-related dependencies declared at the module level).

#### Scenario: Convention plugin centralizes configuration

- **WHEN** a developer inspects `:benchmark/build.gradle.kts`
- **THEN** the file applies exactly one alias (`alias(libs.plugins.nubecita.android.benchmark)`) for the convention; per-module overrides are limited to namespace + module-specific deps. The convention plugin lives at `build-logic/convention/src/main/kotlin/AndroidBenchmarkConventionPlugin.kt` and is registered alongside the existing seven plugins (bringing the roster to eight after this change).

### Requirement: `:app` applies the baselineprofile plugin and exposes plugin-generated benchmarking variants

The `:app` module SHALL apply `androidx.baselineprofile` alongside `nubecita.android.application` to auto-generate `benchmarkRelease` (R8-minified, profileable, debug-signed) and `nonMinifiedRelease` variants without hand-rolling separate benchmark build types or mutating production `release`.

#### Scenario: Plugin-generated variants exist on :app

- **WHEN** `./gradlew :app:tasks --all` is run
- **THEN** the output contains `assembleBenchmarkRelease` and `assembleNonMinifiedRelease` tasks. Both target build types are produced by the `androidx.baselineprofile` plugin's variant matrix expansion.

#### Scenario: Release variant is unchanged

- **WHEN** a developer diffs the resolved AGP `BuildType` config for the `release` variant before and after this change
- **THEN** `isDebuggable`, `isProfileable`, `isMinifyEnabled`, and `signingConfig` are byte-for-byte identical on `release`. The new flags (profileable, debug-signed) appear only on the plugin-generated `benchmarkRelease` and `nonMinifiedRelease` variants.

#### Scenario: :benchmark resolves :app:benchmarkRelease via targetProjectPath

- **WHEN** the Macrobenchmark Gradle plugin computes the target APK for `:benchmark`'s `connectedBenchmarkReleaseAndroidTest` task
- **THEN** it installs the `:app:benchmarkRelease` APK (not `:app:release` or `:app:debug`) as the target process. The benchmark APK targeting this variant is what the test process runs against.

### Requirement: `StartupBenchmark` measures cold/warm/hot start of `MainActivity`

`:benchmark` SHALL contain a `StartupBenchmark` class parameterized across `StartupMode.COLD`, `WARM`, and `HOT` targeting `MainActivity`. It SHALL report `StartupTimingMetric`, use `CompilationMode.None`, and run 5 iterations per `StartupMode`.

#### Scenario: Benchmark runs locally and produces JSON

- **WHEN** a developer runs `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` against a connected device/emulator
- **THEN** the task completes successfully and writes a JSON results file under `benchmark/build/outputs/connected_android_test_additional_output/<variant>/connected/<device>/net.kikin.nubecita.benchmark.test-benchmarkData.json` (where `<variant>` is the AGP variant the task name resolves to — `benchmarkRelease` for `connectedBenchmarkReleaseAndroidTest`) containing entries for all three startup modes with `timeToInitialDisplayMs` and `timeToFullDisplayMs` fields populated.

#### Scenario: COLD startup is measured against a freshly-killed process

- **WHEN** the `COLD` parameterization runs
- **THEN** Macrobenchmark force-stops the target process between iterations so each iteration measures a true cold launch. The reported `timeToInitialDisplay` reflects wall-clock from `Intent.ACTION_MAIN` dispatch to the first frame drawn.

#### Scenario: Target APK is the plugin-generated benchmarkRelease variant

- **WHEN** the test task launches `MainActivity` from the target APK
- **THEN** the APK installed is `:app:benchmarkRelease` (non-debuggable, minified, profileable). Verified at runtime by `ApplicationInfo.flags & FLAG_DEBUGGABLE == 0`.

### Requirement: `FeedScrollBenchmark` measures Feed scroll frame timing

`:benchmark` SHALL contain a `FeedScrollBenchmark` test class launching to the Feed tab, locating the feed list via single-arg `UiDevice.findObject(By.res("feed_list"))`, performing fixed scroll gestures, and reporting `FrameTimingMetric` distributions (`frameDurationCpuMs`, `frameOverrunMs`) using `CompilationMode.None`.

#### Scenario: Bench locates the Feed list by testTag-derived resource id

- **WHEN** the bench's setup phase calls `device.findObject(By.res("feed_list"))` (single-arg — Compose tags surface without a package qualifier)
- **THEN** a non-null `UiObject2` representing `FeedScreen`'s `LazyColumn` is returned within the default `Until.findObject` timeout (10s). If `null` is returned, the bench fails fast with a message identifying the missing testTag rather than silently producing a zero-frame trace.

#### Scenario: Frame metrics are emitted

- **WHEN** the scroll bench completes a single iteration
- **THEN** the JSON output for the iteration contains a `FrameTimingMetric` block with `frameDurationCpuMs` p50, p95, p99 fields populated. An iteration with zero frames (e.g. emulator hang) is reported as a failed iteration, not silently passed.

#### Scenario: Bench targets the benchmarkRelease variant of FeedScreen

- **WHEN** the bench attaches to the target process
- **THEN** the process is running the `:app:benchmarkRelease` variant. The release variant's Feed code path (R8-minified, profileable) is what's measured — not debug.

### Requirement: `FeedScreen`'s `LazyColumn` exposes a stable `testTag` for macrobench

`:feature:feed:impl` SHALL declare `FeedTestTags.LIST = "feed_list"` and apply `Modifier.testTag(FeedTestTags.LIST)` to its top-level `LazyColumn`. Host semantics SHALL enable `testTagsAsResourceId = true` so UIAutomator can select the node via single-arg `By.res("feed_list")`.

#### Scenario: Tag survives FeedScreen refactors

- **WHEN** any PR refactors `FeedScreen`'s structure
- **THEN** the `Modifier.testTag(FeedTestTags.LIST)` MUST remain on the top-level `LazyColumn` of the feed (whichever composable owns it post-refactor). Removing the tag without updating `FeedScrollBenchmark` MUST cause `FeedScrollBenchmark` to fail in the next `run-bench` CI run.

#### Scenario: testTag does not leak into accessibility tree

- **WHEN** a screen reader (TalkBack) traverses `FeedScreen`
- **THEN** the testTag value `"feed_list"` is NOT spoken. The list is announced by its accessibility role and per-item content descriptions only. (`testTag` is surfaced as a resource-id for tests, not a `contentDescription`.)

### Requirement: Macrobench results are captured locally and posted to the epic comment thread

This change SHALL produce baseline measurements on a physical device by running `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` with a signed-in app, recording output numbers (`timeToInitialDisplayMs`, `frameDurationCpuMs`, `frameOverrunMs`) on issue `nubecita-crmi`. Cloud CI runs remain out of scope for this initial change.

#### Scenario: Operator runs the bench and captures numbers

- **WHEN** the bench operator runs `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` on a connected device with the app signed in
- **THEN** the produced `benchmark/build/outputs/connected_android_test_additional_output/.../*.json` contains entries for `StartupBenchmark.startup[COLD]`, `startup[WARM]`, and `FeedScrollBenchmark.scrollFeed`, each with the expected metric fields populated. The operator posts a summary to `bd comment nubecita-crmi`.

#### Scenario: Bench is documented as local-only in this change

- **WHEN** a contributor looks for "how to run the macrobench in CI"
- **THEN** the change's documentation (`benchmark/README.md` and proposal) state explicitly that CI integration is deferred to a follow-up epic and point to that epic's bd id. No `.github/workflows/ci.yaml` job exists for `:benchmark` after this change merges.

### Requirement: A Macrobenchmark SHALL cover the video overlay with chrome visible

The suite SHALL include a benchmark exercising the trending video feed with its overlay controls
visible, measuring frame timing while video plays and the user swipes between pages.

This is the worst case for any effect drawn over media: the sampled content changes every frame
AND the user is actively interacting, so a benchmark that measures static chrome over a paused
frame does not exercise the cost being guarded.

#### Scenario: Benchmark exercises the overlay under motion

- **WHEN** the video-overlay benchmark runs
- **THEN** it plays video, keeps the overlay controls visible, and swipes between pages
- **AND** it reports frame-timing metrics for that interaction

#### Scenario: Benchmark reports against the 120 Hz budget

- **WHEN** the benchmark completes
- **THEN** its frame-timing output is comparable against the project's 8.33 ms frame budget

### Requirement: A surface adopting a GPU-cost effect MUST carry a before/after measurement

A change introducing a per-frame GPU effect over animating content MUST capture the benchmark on
the **pre-change** build before the effect is adopted, and MUST re-run it after. This covers
background blur, glass, refraction and progressive blur.

An after-number alone is not evidence: without the baseline there is nothing to attribute a
regression to, and the effect's cost cannot be separated from the surface's existing cost.

#### Scenario: Effect is adopted on a surface

- **WHEN** a per-frame GPU effect is proposed for a surface
- **THEN** the benchmark is captured on the pre-change build first
- **AND** re-run after adoption
- **AND** both numbers are recorded together

#### Scenario: Measurement shows the budget is exceeded

- **WHEN** the after-measurement exceeds the frame budget on the reference device
- **THEN** the effect is not adopted
- **AND** the surface keeps its pre-change rendering or a cheaper fallback
