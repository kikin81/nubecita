# Baseline measurements — video overlay controls

Captured for `nubecita-6rdb.15`. The **before** number, taken on the pre-change build: the
`:designsystem` overlay components do not exist yet and neither surface has been adopted, so these
timings are the trending video feed with its current background-less controls.

## Run 1 — pre-change baseline

| | |
|---|---|
| Date | 2026-09-28 |
| Commit | `4fef7ed8` (branch `chore/nubecita-6rdb.15-…`) |
| Task | `:benchmark:connectedBenchmarkReleaseAndroidTest` |
| Benchmark | `VideoFeedScrollBenchmark.scrollVideoFeed` |
| Device | `sdk_gphone16k_arm64` emulator (Pixel_10_Pro AVD), SDK 37 |
| Result | passed, 0 failures, run time 176.5 s |

**`frameDurationCpuMs`** — CPU time per frame:

| P50 | P90 | P95 | P99 |
|---|---|---|---|
| 2.21 | 3.83 | 4.23 | 6.20 |

**`frameOverrunMs`** — time over the frame deadline (negative = finished early):

| P50 | P90 | P95 | P99 |
|---|---|---|---|
| −13.08 | −11.15 | −10.82 | −8.65 |

`frameCount` per iteration: min 32, median 34, max 35.

## How to read these — three limits, all load-bearing

**1. This is a relative instrument, not a budget gate.** Per design.md Open Question 1 the reference
device is the emulator, chosen for availability. Emulator frame timings do not map to real GPU
behavior. These numbers answer "did adopting the scrim move it", which is what `nubecita-6rdb.18`
needs. They do not answer "does this fit the frame budget".

**2. The deadline here is ~60 Hz, not 120 Hz.** `frameOverrunMs` sits around −13 ms against a
`frameDurationCpuMs` P50 of 2.2 ms, which puts the deadline near 16.7 ms. The emulator is
presenting at 60 Hz, so the 8.33 ms budget the project actually targets is **not being exercised at
all**. Any future claim that a change "fits the 120 Hz budget" cannot be supported by this
instrument — that is the gate `nubecita-e8at` must run on real 120 Hz hardware.

**3. `cpuLocked=false`, `sustainedPerformanceMode=false`, ~34 frames per iteration.** Clocks are
not pinned and the sample is small, so run-to-run noise is real. Treat a difference as meaningful
only if it clearly exceeds the spread between repeat runs — re-run the baseline rather than
trusting a single pair.

## Run 2 — after adoption

Not captured yet. Belongs to `nubecita-6rdb.18`, after `VideoRailAction` adopts the shared
component. Re-run the identical command on the same AVD and append here.

Expectation: unchanged within noise. The scrim adds one draw per control — no capture, no
sampling, no per-frame GPU work. A real regression here means something other than a scrim was
introduced, so investigate rather than accept it.

## Assertion mutation check

A passing assertion is not evidence until it has been seen to fail, so the rail guard was
mutation-tested against this same run:

| Constant value | Result |
|---|---|
| `video_feed_like` (real) | `tests=1 failures=0` — passed |
| `MUTATION_TEST_absent_tag` | `tests=1 failures=1` — **failed as intended** |
| `video_feed_like` (restored) | green again |

The failure carried the intended diagnostic rather than a generic null dereference:

```
AssertionError: Overlay rail ('MUTATION_TEST_absent_tag') not on screen before measuring.
The frame timings would exclude the controls this benchmark is the baseline for.
    at VideoFeedScrollBenchmark.scrollVideoFeed(VideoFeedScrollBenchmark.kt:97)
```

Scope of what this proves: the mutation changed the *selector*, not the rendered UI, so it
exercises the assertion's mechanism (lookup returns null → throw with a useful message) rather
than a genuinely absent rail. That is the part that could silently rot; whether the rail actually
renders is covered by `VideoFeedPageScreenshotTest`.
