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

`StartupBenchmark` reports a `None` and a `Partial(Require)` cell per
startup mode so the bundled baseline profile's effect is directly
visible in one bench run. The `None` cell runs `cmd package compile
--reset` before each iteration, neutralizing whatever ART has cached
— this is the "no profile" baseline. The `Partial(Require)` cell
installs the APK-bundled profile via `profileinstaller` and warms
ART against it with `cmd package compile -m speed-profile`. Compare
the same `StartupMode` across the two cells for the profile's effect.

`Require` fails the test if the APK doesn't ship a profile, which
doubles as an assertion that `:app`'s producer wiring (the
`baselineProfile(project(":benchmark"))` configuration) is intact.

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

## Regenerating the startup baseline profile

The `BaselineProfileGenerator` test drives the signed-in cold-start
path (Splash → MainShell → first Feed frame). It is invoked via the
`androidx.baselineprofile` plugin's generation task on `:app`:

```bash
./gradlew :app:generateReleaseBaselineProfile
```

Pre-requisite — **the bench device must be signed in**. The
generator waits for `feed_list` to appear and fails fast if the
cold-start path routes Splash → Login instead of Splash → MainShell
(see `BaselineProfileGenerator`'s KDoc for the diagnostic message).

Outputs land in `app/src/release/generated/baselineProfiles/`:

| File | Consumer |
|------|----------|
| `startup-prof.txt` | R8 — lays out the listed classes contiguously in the DEX so the cold-start path stops faulting across pages |
| `baseline-prof.txt` | ART — pre-AOT-compiles the listed methods at install time so the cold-start path doesn't pay the JIT warm-up tax |

Both files are committed to the repo (`saveInSrc = true` is the
plugin's default in 1.5.x), so the next `:app:assembleRelease` /
`:app:bundleRelease` picks them up automatically — no extra wiring.

**Cadence.** Regenerate on major Feed, Splash, or Login feature
merges. Not on every release (the profile decays slowly relative to
unrelated UI work), and not in CI (cloud runners don't carry the
signed-in OAuth session this generator depends on; CI integration is
deferred to `nubecita-crmi.6`).

After regeneration, re-run `StartupBenchmark` on the same device and
post COLD / WARM / HOT TTID + TTFD numbers to the `nubecita-crmi`
epic comment thread alongside the prior baseline so the historical
trend is recoverable. The pre-profile baseline on Pixel 10 Pro XL
(crmi.1, 2026-05-22) is COLD TTID 253.75 ms median.

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
