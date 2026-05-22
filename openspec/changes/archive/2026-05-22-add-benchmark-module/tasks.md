## 1. Version catalog + convention plugin

- [x] 1.1 Add Macrobenchmark + UIAutomator + baseline-profile plugin entries to `gradle/libs.versions.toml`: `androidx.benchmark:benchmark-macro-junit4`, `androidx.test.uiautomator:uiautomator`, plugin aliases `androidx.benchmark` and `androidx.baselineprofile`. Pin to AGP-9-compatible versions (verify against Maven Central before locking).
- [x] 1.2 Implement `AndroidBenchmarkConventionPlugin` at `build-logic/convention/src/main/kotlin/AndroidBenchmarkConventionPlugin.kt`. Apply `com.android.test` (AGP 9 brings built-in Kotlin support; `org.jetbrains.kotlin.android` is intentionally NOT applied), `androidx.baselineprofile` (producer side, auto-generates the `benchmarkRelease` + `nonMinifiedRelease` variants on `:app` — no hand-rolled build type on either side), and `com.squareup.sort-dependencies`. Configure compileSdk = 37, minSdk = 28, JVM 17 toolchain, `targetProjectPath = ":app"`, `experimentalProperties["android.experimental.self-instrumenting"] = true`.
- [x] 1.3 Register `nubecita.android.benchmark` in `build-logic/convention/build.gradle.kts`'s `gradlePlugin.plugins` block. Verify `./gradlew :build-logic:convention:tasks` lists the new plugin.
- [x] 1.4 Update `build-logic/README.md`'s plugin table to include `nubecita.android.benchmark` with a one-line description and the "Used by" column pointing to `:benchmark`.

## 2. `:benchmark` module scaffolding

- [x] 2.1 Register `:benchmark` in `settings.gradle.kts` as a top-level sibling of `:app`.
- [x] 2.2 Create `benchmark/build.gradle.kts` applying `alias(libs.plugins.nubecita.android.benchmark)`, setting `android.namespace = "net.kikin.nubecita.benchmark"`, and declaring `implementation`/`androidTestImplementation` only for what's not pulled in by the convention plugin.
- [x] 2.3 Create `benchmark/src/main/AndroidManifest.xml` with the `<instrumentation>` block Macrobenchmark requires (the plugin generates most of this; verify after first sync).
- [x] 2.4 Add `:benchmark` to `.gitignore`'s build-output exclusion list if not already covered by the global `**/build/` pattern. Verify. _Covered by existing `build/` rule — no change needed._

## 3. `:app` benchmark build-type + baseline-profile consumer wiring

- [x] 3.1 In `app/build.gradle.kts`, add `androidx.baselineprofile` plugin alias to the `plugins { ... }` block.
- [x] 3.2 In `app/build.gradle.kts`'s `buildTypes { }`, add `create("benchmark") { initWith(getByName("release")); isDebuggable = false; signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += "release" }`. Do NOT mutate the `release` block. _Revised: the `androidx.baselineprofile` plugin auto-generates `benchmarkRelease` (R8-minified, profileable) + `nonMinifiedRelease` (for profile generation) variants on `:app`. A hand-rolled `benchmark` build type collides with the plugin's naming and is redundant — dropped. `release` stays untouched (non-profileable, non-debuggable)._
- [x] 3.3 Add `baselineProfile(project(":benchmark"))` to `:app`'s `dependencies` block.
- [x] 3.4 Run `./gradlew :app:tasks --all | grep -i benchmark` and confirm `assembleBenchmark` / `bundleBenchmark` appear. Confirm `assembleRelease` is unchanged. _Verified plugin-generated `assembleBenchmarkRelease` + `assembleNonMinifiedRelease` present; `release` task chain intact._
- [~] 3.5 ~~Run `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` before and after the build-type addition; diff the output to verify the `release` variant's classpath did not shift.~~ _Skipped — no `release` mutation made (plugin operates by adding variants alongside)._

## 4. `FeedTestTags` + `FeedScreen` testTag

- [x] 4.1 Add `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedTestTags.kt` exposing `object FeedTestTags { const val LIST = "feed_list" }`. Mark internal or public per the module's existing conventions for testing constants (mirror what `:feature:composer:impl` does if it has one; otherwise public).
- [x] 4.2 Apply `Modifier.testTag(FeedTestTags.LIST)` to the top-level `LazyColumn` in `FeedScreen` (or whichever composable owns the canonical scrollable list). Verify the root semantics modifier enables `testTagsAsResourceId = true` — if it doesn't, add it to the topmost layer that wraps `FeedScreen` (MainShell or FeedScreen itself, whichever already sets root semantics). _Tag on `LoadedFeedContent`'s `LazyColumn`; `testTagsAsResourceId` enabled on MainActivity's root Surface._
- [x] 4.3 Add a unit test asserting `FeedTestTags.LIST == "feed_list"` (paranoid guard so a future rename surfaces in unit tests, not only in the macrobench job).
- [x] 4.4 Run the existing `:feature:feed:impl:test` suite to confirm no Compose-runtime regressions from the `testTag` modifier. _`testDebugUnitTest` green; FeedTestTagsTest + FeedViewModelTest + FeedScreenViewStateTest all pass._
- [x] 4.5 Run the screenshot suite for the Feed via `./gradlew :feature:feed:impl:validateDebugScreenshotTest`. The `testTag` change MUST be byte-for-byte identical at the pixel level — fail the task if any baseline drifts. _Satisfied via CI Screenshot tests job on PR #281 (green); the `testTag` modifier didn't drift any baseline._

## 5. `StartupBenchmark`

- [x] 5.1 Add `benchmark/src/main/AndroidManifest.xml`'s `<queries>` block (or per-class `<intent>` matching) if Macrobenchmark's plugin doesn't auto-generate the launch query for `net.kikin.nubecita`.
- [x] 5.2 Implement `benchmark/src/main/kotlin/net/kikin/nubecita/benchmark/StartupBenchmark.kt` as a `@RunWith(Parameterized::class)` JUnit4 class. Parameter source: `StartupMode.values()`. Body: `benchmarkRule.measureRepeated(packageName, metrics = listOf(StartupTimingMetric()), iterations = DEFAULT_ITERATIONS, startupMode = mode) { pressHome(); startActivityAndWait() }`.
- [x] 5.3 Run `./gradlew :benchmark:connectedBenchmarkAndroidTest --tests StartupBenchmark` against a connected emulator. Verify the produced JSON contains entries for COLD/WARM/HOT with `timeToInitialDisplayMs` populated. _Implicitly satisfied by task 8.4's Pixel 10 Pro XL run (COLD 253.75 ms median, WARM 63.35 ms). HOT failed — tracked as `nubecita-vuny`._

## 6. `FeedScrollBenchmark`

- [x] 6.1 Implement `benchmark/src/main/kotlin/net/kikin/nubecita/benchmark/FeedScrollBenchmark.kt`. Setup: `pressHome()`, `startActivityAndWait()`, `device.wait(Until.hasObject(By.res(packageName, FeedTestTags.LIST)), 10_000)`. Body: locate the list, perform 5x `swipe(Direction.UP, percent = 0.8f, durationMs = 300)`. Metric: `FrameTimingMetric()`. Iterations: default.
- [x] 6.2 Add a guard: if `device.findObject(By.res(packageName, FeedTestTags.LIST))` is `null` after the 10-second wait, throw `AssertionError("FeedScreen testTag '${FeedTestTags.LIST}' missing — did the macrobench module's :app dependency drift?")`. Fail fast over silently-zero-frame results.
- [x] 6.3 Hard-code the literal `"feed_list"` in the bench's `By.res()` call (do not reach across modules into `FeedTestTags.LIST` — the macrobench module deliberately doesn't depend on `:feature:feed:impl`). Add a code comment cross-referencing the spec's "stable testTag" requirement so future renames are caught at PR-review time.
- [x] 6.4 Run `./gradlew :benchmark:connectedBenchmarkAndroidTest --tests FeedScrollBenchmark`. Verify JSON contains `frameDurationCpuMs` p50/p95/p99. _Implicitly satisfied by task 8.4's Pixel 10 Pro XL run (frameDurationCpuMs P50 3.96 / P95 6.38 / P99 10.45 ms)._

## 7. CI workflow integration — DEFERRED

All of section 7 is descoped into a follow-up epic. CI macrobench requires a fake-network/auth flavor (so `FeedScrollBenchmark` has a deterministic feed without hitting the live AT Protocol) and a regression-tracking strategy that copes with cloud-runner variance — both are larger architectural problems than wiring a workflow job. Tracked separately.

- [~] 7.1 ~~Add the `run-bench` PR label~~ — deferred (no CI job to gate).
- [~] 7.2 ~~Add a `benchmark` job to `.github/workflows/ci.yaml`~~ — deferred.
- [~] 7.3 ~~Run-tests step `script:`~~ — deferred.
- [~] 7.4 ~~`actions/upload-artifact@v7` step for benchmark JSON~~ — deferred.
- [~] 7.5 ~~`if: failure()` upload step for reports~~ — deferred.
- [~] 7.6 ~~`actionlint` on the workflow change~~ — N/A, no workflow change.

## 8. Local verification

- [x] 8.1 Run `./gradlew spotlessCheck` — green across `:app`, `:benchmark`, `:feature:feed:impl`, and `build-logic/convention`.
- [x] 8.2 Run `./gradlew :app:assembleDebug` — green; debug build path unaffected by the baselineprofile plugin.
- [x] 8.3 Run `./gradlew :benchmark:assembleBenchmarkRelease` — green; bench APK builds without a connected device.
- [x] 8.4 Run `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` on a connected device (Pixel 10 Pro XL, Android 16 / SDK 36). Captured: COLD TTID median 253.75 ms, WARM 63.35 ms (StartupTimingMetric); scrollFeed P50 3.96 / P95 6.38 / P99 10.45 ms `frameDurationCpuMs` (FrameTimingMetric). HOT-startup mode fails to read metrics — tracked as `nubecita-vuny`.
- [~] 8.5 ~~Open the PR, apply `run-bench` label, verify CI run~~ — deferred with the CI epic.
- [x] 8.6 Post the baseline numbers from 8.4 as a comment on bd issue `nubecita-crmi` so follow-up tickets have a documented reference. _Comment landed on `nubecita-crmi` 2026-05-22 (visible in `bd show nubecita-crmi`)._

## 9. Documentation

- [x] 9.1 Add a "Benchmarks" section to the root `README.md` (or a new `benchmark/README.md` if the root README is already crowded) summarizing: what the module measures, how to run it locally (`./gradlew :benchmark:connectedBenchmarkAndroidTest`), and how to opt in via the `run-bench` PR label. _Created `benchmark/README.md`._
- [x] 9.2 Update `CLAUDE.md`'s "Key commands" section to list `./gradlew :benchmark:connectedBenchmarkAndroidTest` alongside the existing entries.
- [x] 9.3 Run `openspec validate add-benchmark-module --strict` and resolve any schema warnings.
