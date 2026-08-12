# Startup bench: make the BaselineProfile cell measure a real app profile

**Date:** 2026-08-11 (revised same day after reviewing NiA and `macrobench.yaml`)
**Epic:** `nubecita-6row` (children `.1`, `.2`, `.3`, `.4`)
**Status:** design approved in conversation; this spec is itself under review
(PR #890). Implementation not started.

## Problem

`StartupBenchmark`'s `COLD-BaselineProfile` cell measures an APK that contains
**zero** of the app's own baseline-profile rules. Every profile regeneration to
date has been signed off against a number that does not measure the profile
being regenerated, and every nightly Macrobench datapoint has the same blind
spot.

Discovered on 2026-08-11 while regenerating the production profile for 1.338.x
(`nubecita-24yr`, PR #889). The bench reported a healthy result:

```
startup[COLD-None]                 median 390.61 ms   CoV 3.3%
startup[COLD-BaselineProfile]      median 320.67 ms   CoV 4.5%   -> 17.9%
```

That 17.9% is real, but it is produced entirely by the **library** baseline
profiles. The merged ART profile inside the benchmarked APK held 13,645 rules
and none of them were `net/kikin/nubecita`.

### Why the existing safety net did not catch it

`StartupBenchmark` uses `CompilationMode.Partial(BaselineProfileMode.Require)`,
whose KDoc claims it "doubles as an assertion that `:app`'s producer wiring is
intact". `Require` only asserts that *a* profile was installed. A profile of
library rules alone satisfies it. The assertion is real but weaker than its
comment claims, which is why the gap survived every regen.

## Root cause

Per-variant merged ART profile contents, measured 2026-08-11:

| Variant | Rules | App rules | Flavor-specific generated dir |
|---|---|---|---|
| `productionBenchmarkRelease` | 74,926 | **6,452** | `src/productionRelease/…` exists |
| `benchBenchmarkRelease` | 13,645 | **0** | `src/benchRelease/…` **missing** |

The `androidx.baselineprofile` consumer plugin appears to wire the
**flavor-specific** generated directory into each benchmark variant.
`src/release/generated/baselineProfiles/` is build-type-only, so it reaches the
`benchRelease` variant through ordinary source-set merging but never reaches
`benchmarkRelease`, a different build type. The bench flavor has never had a
profile generated for it, so its benchmark variant gets nothing.

This explains every number in the table. **It is a hypothesis, not a verified
fact** — see the acceptance test in child `.1`, which exists to falsify it
before anything else is built.

### Three false claims in `macrobench.yaml`'s header

The nightly workflow's own documentation misdescribes what it does. All three
are corrected by child `.3`:

1. *"It only USES the committed baseline profile at
   `app/src/release/generated/baselineProfiles/baseline-prof.txt`."* — It does
   not. The variant it builds carries 0 app rules.
2. *"baseline-profile generation … is gated behind `requiresPhysicalDevice`"* —
   there is no such gate. `requiresPhysicalDevice` is not a Gradle setting, not
   an AGP or `androidx.baselineprofile` option, and appears nowhere in
   `BaselineProfileGenerator` or any build config. It exists **only as prose, in
   two documents** that repeat each other: `macrobench.yaml:14` and
   `benchmark/README.md:107`. Child `.3` must correct both — an earlier draft of
   this spec claimed it appeared in exactly one place, which was wrong.
3. The workflow is described in conversation as weekly. It is **nightly**
   (`cron: "0 7 * * *"`), so the blind spot has been accumulating ~7× faster
   than assumed.

### Correction to an earlier assumption

`app/src/release/generated/baselineProfiles/` (40,876 rules, generated
2026-05-22) is **not** dead weight — it feeds the bench flavor's `benchRelease`
variant. An earlier note on `nubecita-6row` called it redundant; that was wrong.

The shipping path was never affected. `productionRelease`'s merged profile
carries 6,957 app rules including the new `AppThemeState` and `Autoplay`
entries. This is a measurement gap, not a shipping bug.

## Prior art: Now in Android, and why we cannot copy it

Read from the local clone at `~/code/nowinandroid`, not from GitHub.

NiA never has this problem because it regenerates on every release build:

```kotlin
// app/build.gradle.kts
baselineProfile { automaticGenerationDuringBuild = false }
release { baselineProfile.automaticGenerationDuringBuild = true }
```

with `benchmarks/build.gradle.kts` using Gradle Managed Devices
(`pixel6Api33`, `useConnectedDevices = false`). No committed artifact, no
staleness, no variant-mismatch window.

Two concrete confirmations from the clone:

- **NiA commits no baseline profiles at all** — nothing under
  `generated/baselineProfiles`, nothing git-tracked. Independent evidence for
  child `.1`, arrived at from a different direction than our build-time /
  dead-weight reasoning.
- **`.github/workflows/NightlyBaselineProfiles.yaml`** generates them nightly on
  a GMD: `:benchmarks:pixel6Api33Setup`, then `:app:assemble` with
  `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=baselineprofile`.
  A working reference for child `.1`'s workflow step.

**That is not portable to our production flavor.** NiA's `prod` flavor has no
authentication and runs unattended. Our production cold start goes through
OAuth, Tink session decrypt, and real Ktor/atproto calls, none of which can be
driven headlessly. `automaticGenerationDuringBuild` on `productionRelease` would
hang at Login or profile the login screen.

**It is portable to our bench flavor**, and that is what this design borrows.
The bench flavor fakes `SignedIn` at boot (`FakeSessionStateProvider`,
`FakeAuthRepository`), so `BaselineProfileGenerator`'s hard `awaitSignedInFeed`
gate passes fully offline — no OAuth, no physical device.

## Design

Four pieces, one per child issue.

### 1. Generate the bench profile in the Macrobench workflow (`nubecita-6row.1`)

The nightly runs `:app:generateBenchReleaseBaselineProfile` on the existing
emulator before measuring. **Nothing is committed.**

Rejected alternative — committing `app/src/benchRelease/generated/…` — because
it buys nothing that justifies a second multi-megabyte artifact:

- It does **not** improve build times. Baseline profiles are consumed only by
  release-type builds; debug builds ignore them entirely.
- It never ships. The bench flavor is a test-only variant.
- Its one genuine benefit — local bench runs measuring a real profile — is
  delivered better by `.2`'s actionable failure message.

`app/src/benchRelease/generated/` gets gitignored. A developer running the bench
locally generates once per checkout; the files persist on disk and subsequent
runs pass the guard.

**Acceptance test, to be run first.** Generate the bench profile, rebuild, and
inspect
`app/build/intermediates/merged_art_profile/benchBenchmarkRelease/…/baseline-prof.txt`.
App-rule count must go from 0 to thousands. If it does not, the root-cause
hypothesis is wrong and this design must be revised before the remaining
children are built.

**Accepted risk:** a freshly generated profile each night makes the
`BaselineProfile` cell's input vary run to run, adding variance to a trend whose
alert threshold is 150%. Judged comfortably absorbed, but it is a real trade
against a fixed committed profile.

### 2. Gradle verification task — the guard (`nubecita-6row.2`)

A per-variant task — `:app:verify<Variant>BaselineProfileRules`, registered for
both `benchBenchmarkRelease` and `productionBenchmarkRelease` — reads that
variant's merged ART profile, counts `net/kikin/nubecita` rules, and fails below
a floor of **500**.

With `.1` committing nothing, this guard is load-bearing rather than
belt-and-braces: it is the only thing standing between a developer and a silent
0-rule local bench run.

Three non-negotiable properties:

- **Runs before device work, without cross-project coupling.** Registered
  entirely inside `:app` and hooked into `:app`'s own lifecycle for the
  benchmark variants (the package/assemble task), *not* wired as a `dependsOn`
  of `:benchmark:connectedBenchmarkReleaseAndroidTest`. A cross-project task
  dependency would couple `:benchmark` to `:app`'s internals and conflict with
  Project Isolation — and this repo runs with `org.gradle.configuration-cache =
  true`, so that is a live constraint, not a hypothetical one. Because the bench
  run has to build and install the app APK anyway, hooking `:app`'s package task
  still fires the guard before any device work: a broken profile costs seconds,
  not the ~3-minute bench run that hid this bug.
- **Takes its input from the producing task's output, not a hardcoded path.**
  Consume `merge<Variant>ArtProfile`'s output through a Gradle lazy provider so
  the dependency is declared and the location tracks AGP rather than being
  re-derived by string. Caveat to settle during implementation:
  `MERGED_ART_PROFILE` is an AGP *internal* artifact type with no public Variant
  API accessor, so this will likely resolve via `tasks.named("merge…ArtProfile")`
  and its output provider — which pins a task name rather than a full path.
  Better than a hardcoded path, still not a public contract, which is exactly
  why the next property stays.
- **Fails when it cannot find its input — never skips.** Even with a lazy
  provider, an AGP upgrade could rename or restructure the producing task. A
  guard that silently passes when its input is missing is the precise failure
  mode this epic exists to eliminate.
- **The failure message is actionable**, naming the exact command:
  `./gradlew :app:generateBenchReleaseBaselineProfile`.

Floor rationale: 500 catches total absence (wiring broken, wrong variant,
profile never generated) while staying immune to the ~4% run-to-run
non-determinism of the generator. A **partial** loss — 6,452 dropping to 900 —
passes. Deliberate, chosen over a proportional or exact-count floor that would
cry wolf on every regen.

### 4. Profile-effectiveness metrics — the ground truth (`nubecita-6row.4`)

Adopted from NiA's `BaselineProfileMetrics`. Add trace-section metrics to
`StartupBenchmark` alongside the existing `StartupTimingMetric`:

```kotlin
val jitCompilationMetric = TraceSectionMetric("JIT Compiling %", label = "JIT compilation")
val classInitMetric      = TraceSectionMetric("L%/%;", label = "ClassInit")
metrics = listOf(StartupTimingMetric(), jitCompilationMetric, classInitMetric)
```

**This is the check that would have caught the bug, and TTID did not.** With 0
app rules, the `BaselineProfile` cell's JIT-compilation time stays level with the
`None` cell — nothing of ours is being AOT-compiled. The 17.9% TTID gain from
library profiles masked that completely. These metrics move the failure into the
reported number instead of requiring someone to go spelunking in
`merged_art_profile`.

**Complementary to `.2`, not a replacement.** They fail in different ways and at
different times:

| | `.2` guard | `.4` metrics |
|---|---|---|
| When | seconds, before device work | after a full ~3-min bench run |
| Kind | structural (counts rules) | behavioural (measures AOT effect) |
| Coupling | AGP intermediates / task name | none |
| Tuning | a floor to choose | none |

Given this bug hid behind a healthy-looking number for months, carrying both is
proportionate.

Three things to verify during implementation rather than assume:

- `TraceSectionMetric` is `@ExperimentalMetricApi` and needs an opt-in.
- Confirm the ART trace sections actually emit on the **hosted emulator** the
  nightly uses (swiftshader). If they do not, the metric is emulator-blind and
  that limitation must be documented rather than silently reporting zeros.
- `macrobench.yaml`'s jq converter already walks `.metrics` generically via
  `to_entries` and maps names ending in `Ms` to milliseconds, so the new metrics
  should land on the gh-pages trend as new series with no workflow change.
  Confirm; do not assume.

### 3. Documentation — two lanes, three corrections (`nubecita-6row.3`)

| Lane | Command | Answers |
|---|---|---|
| Bench flavor (default) | `:benchmark:connectedBenchmarkReleaseAndroidTest` | Did startup regress? Deterministic, offline, CI-able. |
| Production flavor | same + `-PbaselineProfileEnvironment=production` | Does the *shipped* profile help? Real signed-in cold start, manual, on-device. |

Run the production lane before shipping a profile regen; the bench lane for
routine trend tracking. The production lane can never be automated — OAuth is
not mockable — and the docs should say so plainly rather than leaving it as an
unstated gap.

Updates `benchmark/README.md` and `.claude/skills/run-startup-bench/SKILL.md`,
corrects the three false claims in `macrobench.yaml`'s header, removes the
duplicated `requiresPhysicalDevice` claim from `benchmark/README.md:107`, and
fixes `app/build.gradle.kts:399`, which still documents the generator as writing
into `app/src/release/generated/baselineProfiles/`.

## Delivery

An epic → one `gh stack` of four PRs, landed atomically, in the order
`.1` → `.2` → `.4` → `.3`.

Ordering matters: the guard from `.2` would fail the nightly if it landed before
`.1` taught the workflow to generate a profile, and `.4`'s metrics are only
meaningful once a real profile is present. `.3` sits last because it documents
the finished behaviour of the three below it. A stack keeps `main` from ever
seeing an intermediate state and cuts one release for the whole epic.

All four child PR titles must stay off `feat`, `fix`, and **`perf`** — the
default `conventionalcommits` rules release on `perf` as a patch. Child `.1` is
therefore `ci(bench):`, not `perf(...)`. As titled (`ci` / `test` / `test` /
`docs`) this epic cuts no release, which is correct: nothing user-facing
changes.

## Explicit non-goals

- **Detecting partial profile degradation via the `.2` floor.** The floor is
  absence-only, by choice. Note `.4`'s JIT metric partially covers this gap from
  the other side: a profile that degrades enough to matter should show rising
  JIT time even while the rule count stays above 500.
- **Automating the production lane.** OAuth cannot be mocked or driven
  headlessly. It stays a manual, on-device, pre-ship check. This is the one
  place we diverge from NiA and it is not fixable.
- **Changing what ships.** The production profile already reaches users
  correctly.
- **Re-running the 1.338.x profile validation.** PR #889 merged on the strength
  of content analysis (`AppTheme` 0 → 37 rules including
  `MainActivity_MembersInjector->injectAppThemeState`). Once this epic lands a
  production-lane run can confirm it at runtime — follow-up, not a blocker.
