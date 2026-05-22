## ADDED Requirements

### Requirement: `:benchmark` module exists as an AndroidX Macrobenchmark suite

The repository SHALL contain a top-level Gradle module at `:benchmark` that applies the `androidx.benchmark.macro.junit4` plugin (via a new `nubecita.android.benchmark` convention plugin in `build-logic/convention`) and is registered in `settings.gradle.kts`. The module:

- MUST set `targetProjectPath = ":app"` so the macrobench tests exercise the real `:app` APK and not a stub.
- MUST declare `experimentalProperties["android.experimental.self-instrumenting"] = true` per Macrobenchmark's required configuration for AGP 9.
- MUST sit at the repo root as a sibling of `:app` (not under `:core:*` or `:test:*`), matching the AndroidX template layout.
- MUST be declared a baseline-profile producer relative to `:app` (`:app` applies the `androidx.baselineprofile` plugin and references `:benchmark` via the `baselineProfile(project(":benchmark"))` dependency). This change does not generate or ship an actual profile; subsequent epic tickets (e.g. `nubecita-crmi.2`) add the `BaselineProfileGenerator` test that produces one.

#### Scenario: Module is registered and resolvable

- **WHEN** `./gradlew projects` is run from the repo root
- **THEN** the printed project tree contains `+--- Project ':benchmark'` as a top-level sibling of `:app`.

#### Scenario: Macrobenchmark plugin is applied

- **WHEN** `./gradlew :benchmark:dependencies` is run
- **THEN** the resolved classpath contains `androidx.benchmark:benchmark-macro-junit4` and `androidx.test.uiautomator:uiautomator` (the only Macrobenchmark-related dependencies declared at the module level).

#### Scenario: Convention plugin centralizes configuration

- **WHEN** a developer inspects `:benchmark/build.gradle.kts`
- **THEN** the file applies exactly one alias (`alias(libs.plugins.nubecita.android.benchmark)`) for the convention; per-module overrides are limited to namespace + module-specific deps. The convention plugin lives at `build-logic/convention/src/main/kotlin/AndroidBenchmarkConventionPlugin.kt` and is registered alongside the existing seven plugins.

### Requirement: `:app` applies the baselineprofile plugin and exposes plugin-generated benchmarking variants

The `:app` module SHALL apply the `androidx.baselineprofile` Gradle plugin alongside `nubecita.android.application`. Doing so causes the plugin to auto-generate two additional variants off `:app`'s `release` build type:

- `benchmarkRelease` — R8-minified, profileable, debug-signed. This is the variant the Macrobenchmark suite measures against.
- `nonMinifiedRelease` — non-R8-minified, profileable, debug-signed. Used by a future `BaselineProfileGenerator` test (filed as `nubecita-crmi.2`) to collect the baseline profile.

The module MUST NOT hand-roll a separate `benchmark` build type. Doing so collides with the plugin's auto-naming (producing awkward `benchmarkBenchmark`-style variants) and provides no functionality the plugin doesn't already offer.

Production `release` is untouched. The plugin operates by adding new variants alongside it, not by mutating its flags.

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

`:benchmark` SHALL contain a `StartupBenchmark` test class that:

- Uses `@RunWith(Parameterized::class)` to run across `StartupMode.COLD`, `StartupMode.WARM`, and `StartupMode.HOT`.
- Targets `MainActivity` (`packageName = "net.kikin.nubecita"`, `intent` resolves the launcher).
- Reports `StartupTimingMetric` so each run produces `timeToInitialDisplay` and `timeToFullDisplay`.
- Uses `CompilationMode.None` for this change. Subsequent tickets in the epic parameterize over compilation modes once a baseline profile exists.
- Runs the default Macrobenchmark iteration count (5) per `StartupMode`.

#### Scenario: Benchmark runs locally and produces JSON

- **WHEN** a developer runs `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` against a connected device/emulator
- **THEN** the task completes successfully and writes a JSON results file under `benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/net.kikin.nubecita.benchmark.test-benchmarkData.json` containing entries for all three startup modes with `timeToInitialDisplayMs` and `timeToFullDisplayMs` fields populated.

#### Scenario: COLD startup is measured against a freshly-killed process

- **WHEN** the `COLD` parameterization runs
- **THEN** Macrobenchmark force-stops the target process between iterations so each iteration measures a true cold launch. The reported `timeToInitialDisplay` reflects wall-clock from `Intent.ACTION_MAIN` dispatch to the first frame drawn.

#### Scenario: Target APK is the plugin-generated benchmarkRelease variant

- **WHEN** the test task launches `MainActivity` from the target APK
- **THEN** the APK installed is `:app:benchmarkRelease` (non-debuggable, minified, profileable). Verified at runtime by `ApplicationInfo.flags & FLAG_DEBUGGABLE == 0`.

### Requirement: `FeedScrollBenchmark` measures Feed scroll frame timing

`:benchmark` SHALL contain a `FeedScrollBenchmark` test class that:

- Launches the app to the `Feed` tab (default landing) and waits for the Feed list to be present.
- Locates the list via `UiDevice.findObject(By.res(packageName, "feed_list"))` — the resource id surfaced by the Compose `testTag` on `FeedScreen`'s top-level `LazyColumn`.
- Performs a fixed scroll gesture (e.g. five `UiObject2.swipe(Direction.UP, percent = 0.8f)` operations with a deterministic gesture duration).
- Reports `FrameTimingMetric`, producing `frameDurationCpuMs` and `frameOverrunMs` distributions (p50 / p95 / p99) for the captured trace.
- Uses `CompilationMode.None` (matching `StartupBenchmark`) for this change.
- Runs the default Macrobenchmark iteration count.

#### Scenario: Bench locates the Feed list by testTag-derived resource id

- **WHEN** the bench's setup phase calls `device.findObject(By.res(packageName, "feed_list"))`
- **THEN** a non-null `UiObject2` representing `FeedScreen`'s `LazyColumn` is returned within the default `Until.findObject` timeout (10s). If `null` is returned, the bench fails fast with a message identifying the missing testTag rather than silently producing a zero-frame trace.

#### Scenario: Frame metrics are emitted

- **WHEN** the scroll bench completes a single iteration
- **THEN** the JSON output for the iteration contains a `FrameTimingMetric` block with `frameDurationCpuMs` p50, p95, p99 fields populated. An iteration with zero frames (e.g. emulator hang) is reported as a failed iteration, not silently passed.

#### Scenario: Bench targets the benchmarkRelease variant of FeedScreen

- **WHEN** the bench attaches to the target process
- **THEN** the process is running the `:app:benchmarkRelease` variant. The release variant's Feed code path (R8-minified, profileable) is what's measured — not debug.

### Requirement: `FeedScreen`'s `LazyColumn` exposes a stable `testTag` for macrobench

`:feature:feed:impl` SHALL declare a `FeedTestTags` object (or equivalent constants holder) exposing at minimum:

```kotlin
object FeedTestTags {
    const val LIST = "feed_list"
}
```

`FeedScreen` SHALL apply `Modifier.testTag(FeedTestTags.LIST)` to its top-level `LazyColumn`. The host composable's root semantics modifier SHALL enable `testTagsAsResourceId = true` so UIAutomator can select via `By.res(packageName, "feed_list")`.

The constant's name and value are part of the testable contract: changing either is a coordinated change spanning `:feature:feed:impl` and `:benchmark`.

#### Scenario: Tag survives FeedScreen refactors

- **WHEN** any PR refactors `FeedScreen`'s structure
- **THEN** the `Modifier.testTag(FeedTestTags.LIST)` MUST remain on the top-level `LazyColumn` of the feed (whichever composable owns it post-refactor). Removing the tag without updating `FeedScrollBenchmark` MUST cause `FeedScrollBenchmark` to fail in the next `run-bench` CI run.

#### Scenario: testTag does not leak into accessibility tree

- **WHEN** a screen reader (TalkBack) traverses `FeedScreen`
- **THEN** the testTag value `"feed_list"` is NOT spoken. The list is announced by its accessibility role and per-item content descriptions only. (`testTag` is surfaced as a resource-id for tests, not a `contentDescription`.)

### Requirement: Macrobench results are captured locally and posted to the epic comment thread

This change SHALL produce an initial set of baseline measurements on a known physical device. The bench operator:

- Runs `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` against a connected device (Pixel 10 Pro XL or equivalent) with a signed-in app install (the `feed_list` selector requires a loaded feed).
- Records the produced JSON's headline numbers (`timeToInitialDisplayMs` median for COLD + WARM startup; `frameDurationCpuMs` P50 / P95 / P99 + `frameOverrunMs` P95 for the scroll bench) as a comment on bd issue `nubecita-crmi` (the perf epic).
- Documents the device model + Android API level so future runs can be compared against like-for-like hardware.

CI runs of the bench are **out of scope for this change** and are filed as a follow-up epic — running Macrobench on cloud runners requires (a) a fake-network/auth flavor so FeedScrollBenchmark has a deterministic feed without hitting the live AT Protocol, and (b) a relative-tracking strategy (e.g. `benchmark-action/github-action-benchmark`) because cloud-runner variance overwhelms absolute Macrobench numbers.

#### Scenario: Operator runs the bench and captures numbers

- **WHEN** the bench operator runs `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` on a connected device with the app signed in
- **THEN** the produced `benchmark/build/outputs/connected_android_test_additional_output/.../*.json` contains entries for `StartupBenchmark.startup[COLD]`, `startup[WARM]`, and `FeedScrollBenchmark.scrollFeed`, each with the expected metric fields populated. The operator posts a summary to `bd comment nubecita-crmi`.

#### Scenario: Bench is documented as local-only in this change

- **WHEN** a contributor looks for "how to run the macrobench in CI"
- **THEN** the change's documentation (`benchmark/README.md` and proposal) state explicitly that CI integration is deferred to a follow-up epic and point to that epic's bd id. No `.github/workflows/ci.yaml` job exists for `:benchmark` after this change merges.
