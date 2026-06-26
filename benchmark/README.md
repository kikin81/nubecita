# `:benchmark` — AndroidX Macrobenchmark suite

Measures user-perceived performance of the Nubecita app: cold/warm/hot
startup latency and Feed scroll frame timing. Foundation for the
`nubecita-crmi` performance epic — baseline profile generation,
Compose Tracing hero benchmarks, and any future perf-regression
gating all build on this module.

## What's measured

| Benchmark | What | Metric |
|-----------|------|--------|
| `StartupBenchmark` | `MainActivity` cold / warm / hot launch, parameterized over `CompilationMode.None` + `Partial(BaselineProfileMode.Require)` | `StartupTimingMetric` (`timeToInitialDisplayMs`, `timeToFullDisplayMs`) |
| `FeedScrollBenchmark` | UIAutomator-driven fling on the loaded feed `LazyColumn` | `FrameTimingMetric` (`frameDurationCpuMs`, `frameOverrunMs` — p50/p95/p99) |
| `BaselineProfileGenerator` | Drives Splash → MainShell → first Feed frame for the `BaselineProfileRule` collector | Emits `startup-prof.txt` (R8 DEX layout) + `baseline-prof.txt` (ART AOT) into `:app:src/release/generated/baselineProfiles/` |

`StartupBenchmark` reports two cells per startup mode so the bundled
baseline profile's effect is directly visible in one bench run:

- `None` (label: `None`) — runs `cmd package compile --reset` before
  each iteration, neutralizing whatever ART has cached. The "no profile"
  baseline.
- `CompilationMode.Partial(BaselineProfileMode.Require)` (label:
  `BaselineProfile`) — installs the APK-bundled profile via
  `profileinstaller` and warms ART with `cmd package compile -m
  speed-profile`. Fails the test if the APK doesn't ship a profile,
  doubling as an assertion that `:app`'s producer wiring is intact.

**Test-name label.** The parameterized name comes from
`CompilationMode.toString()`. `None.toString()` returns `None`;
`Partial(BaselineProfileMode.Require).toString()` returns
`BaselineProfile` (not `Partial` — the Require discriminator
resolves to the more descriptive label). So the emitted cells are
`startup[COLD-None]`, `startup[COLD-BaselineProfile]`, etc. Compare
the same `StartupMode` across the two labels for the profile's effect.

`FeedScrollBenchmark` is still single-`CompilationMode.None`; the
post-startup baseline profile lands in `nubecita-crmi.3` and that
ticket adds the matching axis here.

## Run locally

A connected emulator or device (API 28+, Pixel 6 or equivalent
recommended — the project's `minSdk` is 28) is required. From the
repo root:

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
```

Results are written to:

```
benchmark/build/outputs/connected_android_test_additional_output/<variant>/connected/<device>/net.kikin.nubecita.benchmark-benchmarkData.json
```

## Run in CI

Wired by `.github/workflows/macrobench.yaml` (crmi.6 Section C).
The workflow runs `:benchmark:connectedBenchmarkReleaseAndroidTest`
against the bench-flavor `:app` (fake-network Feed) on a hosted
emulator, then pipes the per-cell JSON through
`benchmark-action/github-action-benchmark@v1` to build a historical
trend on the `gh-pages` branch.

That action has no `androidx` parser, so an inline `jq` step in the
workflow first maps the AndroidX `benchmarkData.json` into the action's
`customSmallerIsBetter` array — one point per benchmark×metric, keyed on
each metric's median (`jq` is preinstalled on the `ubuntu-latest`
runner). The conversion is metric-agnostic: on a swiftshader emulator
only `timeToInitialDisplayMs` (the COLD/WARM startup cells) and
`frameCount` capture, but frame-duration metrics flow through
automatically on a real-hardware runner.

**Triggers.** Nightly at 07:00 UTC (the canonical trend source),
`workflow_dispatch` (manual — used to seed the gh-pages baseline
immediately instead of waiting for the first cron), and PR opt-in via
the `run-bench` label (mirrors the `instrumented` job's
`run-instrumented` gate).

**Alert behaviour.** Scheduled/manual runs `auto-push` the new data
point to gh-pages and `fail-on-alert` at a `150%` threshold — a
"catastrophic-regression smoke detector", not a fine-grained gate,
because Macrobench's absolute numbers are 30–50 % noisy on
hosted GitHub runners. PR runs never mutate the trend (`auto-push: false`,
`save-data-file: false`) and never auto-fail; they post a
comment-on-alert so the author sees the delta without blocking merge.

**Emulator config.** API 35 / `google_apis` / `pixel_6` / x86_64 with
KVM, sharing the AVD cache key (`avd-cache-v1-api35-pixel6`) with the
`instrumented` job so the snapshot is reused. The HOT
`StartupBenchmark` cells are filtered out (`tests_regex` negative
lookahead on `HOT`) because they fail on emulators with "Unable to
read any metrics" — tracked as `nubecita-vuny`, unrelated to the
pipeline.

> **Platform anchoring.** Frame timing is genuinely platform-divergent
> (CI Linux x86_64 emulator vs. local arm64 hardware), so — unlike the
> screenshot baselines — the gh-pages history is deliberately
> CI-runner-anchored and **not** cross-platform-comparable. Only compare
> a CI cell to the prior CI cell; the on-device numbers posted to the
> `nubecita-crmi` epic thread live on a separate track.

> **Baseline profile in CI.** The workflow only *uses* the committed
> `app/src/release/generated/baselineProfiles/baseline-prof.txt`; it
> never regenerates it. Profile generation is blocked on hosted runners
> by `requiresPhysicalDevice` and the signed-in OAuth cold-start path —
> the `androidx.baselineprofile` plugin routes `BaselineProfileGenerator`
> to the `nonMinifiedRelease` variant, so the `benchmarkRelease`
> connected-test task only runs `StartupBenchmark` + `FeedScrollBenchmark`.
> Regen stays the manual on-device task documented below.

### Re-baselining the gh-pages trend

When an *intentional* perf change shifts the numbers (a Compose runtime
bump, a deliberate Feed-layout rework, a benchmark-cell rename), the old
history becomes a false-positive generator — every subsequent run alerts
against a baseline that no longer reflects reality. Clear the stale
points so the next scheduled run re-seeds from the new normal:

1. **Targeted edit** — for a single shifted metric, check out the
   `gh-pages` branch, open `dev/bench/data.js`, and delete the trailing
   entries for the affected benchmark from the `window.BENCHMARK_DATA`
   array. Commit and push. The next scheduled run appends fresh points
   that diff only against what remains.

   ```bash
   git fetch origin gh-pages
   git switch gh-pages
   # edit dev/bench/data.js — trim the stale entries
   git commit -am "chore(bench): re-baseline <metric> after intentional perf change"
   git push origin gh-pages
   git switch -
   ```

2. **Full wipe + re-seed** — for a broad shift (toolchain bump touching
   every cell), delete `dev/bench/data.js` entirely (or delete the
   `gh-pages` branch), then run the workflow via `workflow_dispatch`. The
   first run with no history re-seeds the baseline from scratch.

Always note the re-baseline (commit subject + reason) so a later
investigator doesn't mistake the discontinuity for a measurement bug.

Until a few nightly cycles accumulate, also run the bench locally on a
physical device and post numbers to the `nubecita-crmi` epic comment
thread — the on-device track is the higher-signal trend while the CI
history is still short.

## Regenerating the startup baseline profile

`:benchmark` runs against one of `:app`'s two `environment` flavors,
selected by the `baselineProfileEnvironment` Gradle property (default
`bench`). **Which flavor you generate against matters a lot** — they
produce materially different profiles:

- **`production` — ship this.** The real signed-in cold-start path
  (decrypt the stored OAuth session → token refresh → first Feed fetch →
  Pro entitlement check). Covers the Ktor / atproto-SDK / Tink /
  `kotlinx.serialization` / billing classes a real user actually loads on
  launch — the JIT-expensive I/O layer baseline profiles help most.
  Non-deterministic (real network), so it's a **manual real-device run on
  a ~weekly cadence**, not CI:

  ```bash
  ./gradlew :app:generateProductionReleaseBaselineProfile -PbaselineProfileEnvironment=production
  ```

  Pre-requisite — **the device must be signed in** on the
  `productionNonMinifiedRelease` build before the run. The generator
  waits for `feed_list` and fails fast if the cold-start path routes
  Splash → Login instead of Splash → MainShell (see
  `BaselineProfileGenerator`'s KDoc for the diagnostic message).

- **`bench` — the default; validation only.** The deterministic offline
  path (fake repos, fake `SignedIn`, mock feed). No sign-in needed and it
  never routes to Login, so it's what CI Macrobench / `StartupBenchmark`
  measurement and profile *validation* use, where stable numbers matter.
  But it is **not** representative of the real launch path — it omits
  ~13k network/crypto/serialization rules (measured: production-gen
  startup profile is +41% vs bench-gen), so **do not ship a
  bench-generated profile**.

  ```bash
  ./gradlew :app:generateBenchReleaseBaselineProfile   # bench (validation only; writes only bench src)
  ```

Outputs land in `app/src/productionRelease/generated/baselineProfiles/`:

| File | Consumer |
|------|----------|
| `startup-prof.txt` | R8 — lays out the listed classes contiguously in the DEX so the cold-start path stops faulting across pages |
| `baseline-prof.txt` | ART — pre-AOT-compiles the listed methods at install time so the cold-start path doesn't pay the JIT warm-up tax |

Both files are committed to the repo (`saveInSrc = true` is the
plugin's default in 1.5.x), so the next `:app:assembleRelease` /
`:app:bundleRelease` picks them up automatically — no extra wiring.

**Cadence.** Regenerate the `production` profile on a real signed-in
device roughly **weekly**, and on major Feed / Splash / Login / startup
or dependency (Ktor, atproto, billing) merges. Not in CI — cloud runners
can't carry the signed-in OAuth session the `production` generator
depends on (this is the deliberate determinism-vs-representativeness
split: CI measures the `bench` flavor; the shipped profile is generated
from `production`). CI auto-gen is deferred to `nubecita-crmi.6`.

After regeneration, re-run `StartupBenchmark` on the same device and
post the new cell medians to the `nubecita-crmi` epic comment thread
alongside the prior numbers so the historical trend is recoverable.

The pre-profile reference number on Pixel 10 Pro XL (`crmi.1`,
2026-05-22) is **COLD TTID 253.75 ms median, `CompilationMode.None`,
`:app:benchmarkRelease`, fresh-install / signed-out cold start
(Splash → Login first frame)**. The current bench bundles a profile,
so the comparable today cell is `COLD-BaselineProfile`, not
`COLD-None`. Compare only same-cell to same-cell — see the
"Comparing results" section below for the discipline.

## Running benches and comparing results

`StartupBenchmark`'s `(StartupMode, CompilationMode)` cross-product
is built to answer two distinct questions in one bench run:

| Question | What to compare |
|----------|-----------------|
| "Does the bundled startup profile actually help?" | Same `StartupMode`, `None` cell vs `BaselineProfile` cell, *within the same run*. The delta is the profile's effect. |
| "Did anything regress since the last regen?" | Same `StartupMode-CompilationMode` cell, today's number vs the last number posted to the `nubecita-crmi` epic comment thread. Apples-to-apples across runs. |

Never compare `None` from run A to `BaselineProfile` from run B —
device thermal state, ART cache warmth, and OS background work all
drift across runs, so cross-run comparisons need the same cell.

### Pre-flight

Run these checks once before any bench session — they prevent the
two failure modes that ate hours during `nubecita-crmi.2`:

1. **Stay-awake on USB.** `:benchmark`'s install + iterate cycle can
   run 5–10 min; the device sleeping mid-run drops ADB and burns the
   whole run.
   ```bash
   adb -s <serial> shell settings put global stay_on_while_plugged_in 7
   ```
2. **Exactly one device connected.** A second device showing up
   mid-run (e.g. a Pixel Tablet auto-connecting over Wi-Fi ADB) makes
   gradle's device picker bail with "No connected devices!" on the
   *next* test. List with `adb devices -l`; disconnect TCP devices
   via `adb disconnect <ip:port>` if needed.
3. **USB cable, not hub.** The Pixel 10 Pro XL drops more often
   through unpowered hubs than direct host ports.
4. **Use serial filter to be specific.**
   ```
   -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.targetDeviceSerial=<serial>
   ```

### Run

Full cross-product (6 cells, ~10 min wall-clock — risk of USB drop):

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.kikin.nubecita.benchmark.StartupBenchmark
```

Single mode only (e.g. COLD, both compilation modes — ~3 min, almost
never drops):

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.kikin.nubecita.benchmark.StartupBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.tests_regex='startup.COLD.*'
```

The single-mode form is the recommended default when iterating —
under the USB-drop threshold and exercises the two cells you most
care about.

### Read the output

Per-run JSON lands at:

```
benchmark/build/outputs/connected_android_test_additional_output/
  benchmarkRelease/connected/<device-name>/
  net.kikin.nubecita.benchmark-benchmarkData.json
```

Extract the cells with a one-line `python3` reader (no extra deps):

```bash
python3 -c "
import json, sys
data = json.load(open(sys.argv[1]))
for b in data['benchmarks']:
    if b['className'].endswith('StartupBenchmark'):
        m = b['metrics'].get('timeToInitialDisplayMs')
        if m:
            print(f\"{b['name']:32s} TTID median={m['median']:7.2f} ms  CoV={m['coefficientOfVariation']*100:.1f}%\")
" "benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/Pixel 10 Pro XL - 16/net.kikin.nubecita.benchmark-benchmarkData.json"
```

The reported `median` is the per-cell metric of record. `CoV` (coefficient
of variation) gauges confidence — anything ≥ 15 % means the median is
noisy enough that small deltas can't be trusted; rerun on a cooler device
before drawing conclusions.

### Post to the epic thread

Append a single comment to the `nubecita-crmi` epic with:

```
<YYYY-MM-DD> Pixel 10 Pro XL — :app:benchmarkRelease

COLD-None             median XXX.XX ms  (CoV X.X%)
COLD-BaselineProfile  median XXX.XX ms  (CoV X.X%)
WARM-None             median XXX.XX ms  (CoV X.X%)
WARM-BaselineProfile  median XXX.XX ms  (CoV X.X%)

Context: <what changed since the last entry — feature merges, dependency
bumps, regen of the startup profile, etc.>
```

This is the historical-trend log; it's the only way to detect slow
drift across releases.

### Known failure modes

- **HOT cell fails with "Unable to read any metrics"** — preexisting
  issue tracked as `nubecita-vuny`, unrelated to the profile workflow.
  Filter HOT out with a `tests_regex` of `startup.(COLD|WARM).*`.
- **Install cycle wipes app data** — the plugin uninstalls + reinstalls
  the target APK at the start of every test run, so any in-app state
  (OAuth session, preferences) is wiped. The bench measures *un*authenticated
  cold start. Don't sign in expecting it to persist across runs.
- **"No connected devices!"** mid-run — see pre-flight item 2.

## Verifying the bundled profile

After regenerating, build and install the release variant and ask
PackageManager what profiles it sees:

```bash
./gradlew :app:installRelease
adb shell pm dump net.kikin.nubecita | grep -iE 'profile|baseline'
```

The output should mention an installed reference profile under
`/data/misc/profiles/ref/net.kikin.nubecita/` once Android has
copied the bundled `assets/dexopt/baseline.prof` into place.

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
