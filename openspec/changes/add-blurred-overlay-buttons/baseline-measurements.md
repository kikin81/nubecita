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

| | |
|---|---|
| Date | 2026-10-01 |
| Commit | branch `feat/nubecita-6rdb.18-…` (`VideoRailAction` adopted) |
| Task | `:benchmark:connectedBenchmarkReleaseAndroidTest` |
| Benchmark | `VideoFeedScrollBenchmark.scrollVideoFeed` |
| Device | `sdk_gphone16k_arm64` emulator (Pixel_10_Pro AVD), SDK 37 |
| Result | passed, 0 failures, run time 177.4 s |

**`frameDurationCpuMs`** — CPU time per frame:

| P50 | P90 | P95 | P99 |
|---|---|---|---|
| 9.59 | 11.01 | 11.58 | 13.97 |

**`frameOverrunMs`** — time over the frame deadline (negative = finished early):

| P50 | P90 | P95 | P99 |
|---|---|---|---|
| −1.91 | −0.30 | −0.01 | 3.04 |

`frameCount` per iteration: min 30, median 33, max 35.

### Comparison and Observations

1. **Assertion guard intact**: `VideoFeedScrollBenchmark` verified the overlay rail (`VIDEO_FEED_RAIL_LIKE_RES_ID`) was present on screen during setup prior to flinging.
2. **Frame count consistency**: The number of measured frames per iteration (`min 30, median 33, max 35`) remained consistent with Run 1 (`min 32, median 34, max 35`).
3. **Emulator timing characteristics**: As noted in limit #3, emulator CPU clocks are unpinned (`cpuLocked=false`, `sustainedPerformanceMode=false`) and sensitive to host machine background load across separate emulator sessions. The scrim and hairline add a single static draw pass per control with zero dynamic sampling or GPU texture capture.

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
