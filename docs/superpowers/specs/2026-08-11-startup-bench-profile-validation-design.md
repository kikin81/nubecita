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

**Staleness caveat, and why it does not undermine the guard.** Because the
directory is gitignored it survives branch switches, so a developer could
measure branch B against a profile generated on branch A — and `.2` would pass
on stale content. Mitigations, in order of strength:

- CI is immune: every nightly runs on a fresh checkout and regenerates.
- **Required, not optional:** wire the directory into `clean`'s delete set so
  `./gradlew clean` resets it. This is part of `.1`'s definition of done, not a
  nice-to-have — it is the only reliable way for a developer to reset local
  state when results look wrong.
- Document it in `benchmark/README.md` (child `.3`): a local profile is only
  valid for the branch that produced it.

This is a *staleness* risk, not an *absence* risk. It cannot recreate the bug
this epic fixes (measuring zero app rules); it can only mean the profile is out
of date relative to the code — the same condition the committed production
profile lives in permanently between regens.

The sharp version of the danger is not "branch A has more rules than branch B" —
both would be healthy profiles far above the floor. It is that a stale profile
from branch A can **mask newly broken generation on branch B**: the guard passes
on yesterday's artifact while today's code produces nothing.

**Generation failure must fail loudly, never fall back.** If the nightly's
generation step flakes or times out, the workflow fails. That is the correct
behaviour: a fallback that proceeded to measure without a profile would
recreate exactly the silent-zero bug this epic exists to eliminate. A *retry* is
acceptable; a *skip* is not. `macrobench.yaml` currently sets
`timeout-minutes: 60` for the whole job — implementation must confirm that still
fits generation plus the bench run, given production generation took ~18 minutes
on a physical device.

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

A per-variant task — `:app:verify<Variant>BaselineProfileRules` — reads that
variant's merged ART profile, counts `net/kikin/nubecita` rules, and fails below
a floor of **500**. Registered for three variants:

| Variant | Why |
|---|---|
| `benchBenchmarkRelease` | the variant the nightly measures — where the bug was found |
| `productionBenchmarkRelease` | the manual pre-ship validation lane |
| **`productionRelease`** | **the variant that actually ships** |

The third is the highest-value registration and was missing from the first draft
of this design. Guarding only the benchmark variants would protect the
*measurement* while leaving the *product* unguarded: a wiring break that dropped
the app profile from the shipped APK would sail through. `productionRelease`
currently carries 6,957 app rules, comfortably above the floor.

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

  **Configuration Cache is not optional here** (`org.gradle.configuration-cache =
  true`). The mechanic: `flatMap` the provider returned by `tasks.named` straight
  into an `@InputDirectory`/`@InputFile` property on the verification task.
  **Never resolve the path at configuration time** — no `.get()`, no
  `File(...)`, no `project` reference inside the task action. Getting this wrong
  produces a Configuration Cache violation rather than a working guard.
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

**On the `%` wildcard.** Review raised a concern that `TraceSectionMetric` does
exact string matching and would never match `"JIT Compiling %"`. Checked against
`androidx.benchmark.macro` 1.5.0-alpha07's own source
(`androidx/benchmark/macro/Metric.kt:527`):

> `"%"` can be used as a wildcard, as this is supported by the underlying
> `TraceProcessor` query. For example `"JIT %"` will match a section named
> `"JIT compiling int com.package.MyClass.method(int)"`

The query is built with SQL `LIKE`, and SQLite's `LIKE` is ASCII
case-insensitive by default, so the capital `C` in NiA's `"JIT Compiling %"`
matches the runtime's lowercase `"JIT compiling …"`. The concern does not apply.

**These metrics must not alert on the nightly — at least not at first.**
`macrobench.yaml`'s jq converter walks `.metrics` generically via `to_entries`,
so `.4`'s metrics land on the gh-pages trend as new series **with no workflow
change** — and therefore inherit the scheduled run's `alert-threshold: "150%"`
with `fail-on-alert: true`. JIT compilation is CPU-bound, and CPU-bound work on
a SwiftShader emulator is materially noisier than on physical hardware, so those
series could start failing the nightly on variance alone.

The converter's genericity is therefore a hazard here, not only a convenience.
`.4` must split the trend into two named charts via two
`benchmark-action/github-action-benchmark` steps:

| Chart | Metrics | Policy |
|---|---|---|
| existing (timing) | `timeToInitialDisplayMs`, frame metrics | `alert-threshold: 150%`, `fail-on-alert: true` — unchanged |
| new (effectiveness) | JIT compilation, ClassInit | `fail-on-alert: false` initially |

Effectiveness metrics start as *observed, not enforced*. Once enough nightly
history exists to know their real variance on this runner, a threshold can be
set from data instead of guessed. Their value on day one is the behavioural
signal — a `BaselineProfile` cell whose JIT time matches its `None` cell — not
an automated gate.

Things to verify during implementation rather than assume:

- `TraceSectionMetric` is `@ExperimentalMetricApi` and needs an opt-in.
- **The metrics must report non-zero on a real run.** Whatever the API
  guarantees, a metric that silently reports 0 because no slice matched is
  indistinguishable from a profile that isn't working — the same failure shape
  as the bug being fixed. Confirm a non-zero JIT reading in the `None` cell
  before trusting a low reading in the `BaselineProfile` cell.
- Confirm the ART trace sections actually emit on the **hosted emulator** the
  nightly uses (swiftshader). If they do not, the metric is emulator-blind and
  that limitation must be documented rather than silently reporting zeros.

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
