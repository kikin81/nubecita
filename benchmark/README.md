# `:benchmark` — AndroidX Macrobenchmark suite

Measures user-perceived performance of the Nubecita app: cold/warm/hot
startup latency and Feed scroll frame timing. Foundation for the
`nubecita-crmi` performance epic — baseline profile generation,
Compose Tracing hero benchmarks, and any future perf-regression
gating all build on this module.

## What's measured

| Benchmark | What | Metric |
|-----------|------|--------|
| `StartupBenchmark` | `MainActivity` cold / warm / hot launch | `StartupTimingMetric` (`timeToInitialDisplayMs`, `timeToFullDisplayMs`) |
| `FeedScrollBenchmark` | UIAutomator-driven fling on the loaded feed `LazyColumn` | `FrameTimingMetric` (`frameDurationCpuMs`, `frameOverrunMs` — p50/p95/p99) |

Compilation mode is fixed to `None` in this version. A follow-up
ticket (`nubecita-crmi.2`) lands a baseline profile generator and
parameterizes both benchmarks over `CompilationMode.Partial` so the
profile's impact is measurable.

## Run locally

A connected emulator or device (API 28+, Pixel 6 or equivalent
recommended — the project's `minSdk` is 28) is required. From the
repo root:

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
```

Results are written to:

```
benchmark/build/outputs/connected_android_test_additional_output/<variant>/connected/<device>/net.kikin.nubecita.benchmark.test-benchmarkData.json
```

## Run in CI

**Not wired in this change.** CI macrobench is deferred to a follow-up
epic because it depends on prerequisites that don't ship here:

- A `benchmark` product flavor on `:app` that swaps the real Bluesky
  data layer for an asset-backed fake (so a fresh CI install routes
  past Login and renders a deterministic feed). Without it,
  `FeedScrollBenchmark` can't find `feed_list` on a CI runner.
- A regression-tracking strategy that copes with cloud-runner CPU
  variance — Macrobench's absolute numbers are 30–50 % noisy on
  `ubuntu-latest`, so the CI gate has to be a "catastrophic-regression
  smoke detector" (e.g. `benchmark-action/github-action-benchmark@v1`
  with conservative threshold-on-alert) rather than a fine-grained
  measurement.
- Emulator-side configuration (`androidx.benchmark.suppressErrors`
  meta-data, fixed API target, KVM-enabled runners) so the bench can
  even start under an emulated device.

The follow-up epic tracks all of the above. Until it lands, run the
bench locally on a physical device and post numbers to the
`nubecita-crmi` epic comment thread.

## Target variant

The benchmark targets `:app:benchmarkRelease` — an R8-minified,
profileable variant the `androidx.baselineprofile` plugin auto-generates
off `:app:release`. There's also a `:app:nonMinifiedRelease` twin used
for *generating* baseline profiles (a follow-up ticket). Production
`release` is untouched (non-profileable, non-debuggable).

## Selectors

The Feed scroll benchmark uses the **single-argument**
`By.res("feed_list")` to locate the loaded feed's `LazyColumn`.
Compose's `testTagsAsResourceId = true` (enabled at the root of
`MainActivity`'s composition) surfaces Compose tags as bare
`resource-id` values with no package qualifier — so the two-arg
`By.res(packageName, id)` form (which builds
`packageName:id/<id>`) silently never matches and is **not** used.
The string `"feed_list"` is the contract between `:benchmark` and
`:feature:feed:impl` — intentionally hardcoded (the macrobench
module doesn't depend on the production module).
`:feature:feed:impl/FeedTestTagsTest` pins the matching constant
and surfaces silent renames in unit-test runs.
