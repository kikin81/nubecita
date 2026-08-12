# Startup bench: make the BaselineProfile cell measure a real app profile

**Date:** 2026-08-11
**Epic:** `nubecita-6row` (children `.1`, `.2`, `.3`)
**Status:** design approved, not yet implemented

## Problem

`StartupBenchmark`'s `COLD-BaselineProfile` cell measures an APK that contains
**zero** of the app's own baseline-profile rules. Every profile regeneration to
date has been signed off against a number that does not measure the profile
being regenerated.

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
and its KDoc claims:

> `Require` (not the lenient default) makes the test fail if the APK doesn't
> ship a profile — which doubles as an assertion that `:app`'s producer wiring
> is intact.

`Require` only asserts that *a* profile was installed. A profile consisting
solely of library rules satisfies it. The assertion is real but weaker than its
comment claims, which is why the gap survived unnoticed across every regen.

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
`benchmarkRelease`, which is a different build type. The bench flavor has never
had a profile generated for it, so its benchmark variant gets nothing.

This explains every number in the table. **It is a hypothesis, not a verified
fact** — see the acceptance test in child `.1`, which exists to falsify it
before anything else is built.

Two related corrections to earlier assumptions recorded here so they are not
repeated:

- `app/src/release/generated/baselineProfiles/` (40,876 rules, last generated
  2026-05-22) is **not** dead weight. It feeds the bench flavor's `benchRelease`
  variant. An earlier note on `nubecita-6row` called it redundant; that was
  wrong.
- The shipping path was never affected. `productionRelease`'s merged profile
  carries 6,957 app rules including the new `AppThemeState` and `Autoplay`
  entries. This is a measurement gap, not a shipping bug.

## Design

Three pieces, one per child issue.

### 1. Bench-flavor baseline profile — the deterministic lane (`nubecita-6row.1`)

```bash
./gradlew :app:generateBenchReleaseBaselineProfile     # no -P flag -> bench env
```

Writes and commits `app/src/benchRelease/generated/baselineProfiles/{startup,baseline}-prof.txt`.

**Acceptance test, to be run first.** Rebuild and inspect
`app/build/intermediates/merged_art_profile/benchBenchmarkRelease/…/baseline-prof.txt`.
App-rule count must go from 0 to thousands. If it does not, the root-cause
hypothesis is wrong and this design must be revised before the remaining
children are built.

Cost, stated plainly: this adds a second multi-megabyte generated artifact with
its own regeneration cadence.

### 2. Gradle verification task — the guard (`nubecita-6row.2`)

A per-variant task — `:app:verify<Variant>BaselineProfileRules`, registered for
both `benchBenchmarkRelease` and `productionBenchmarkRelease` — reads that
variant's merged ART profile, counts `net/kikin/nubecita` rules, and fails below
a floor of **500**.

Two non-negotiable properties:

- **Runs before device work.** Wired as a `dependsOn` of
  `:benchmark:connectedBenchmarkReleaseAndroidTest`, and ordered after the
  variant's `merge<Variant>ArtProfile` task that produces its input. A broken
  profile then costs seconds, not the ~3-minute bench run that hid this bug.
- **Fails when it cannot find its input — never skips.** The path is an AGP
  intermediate and an AGP upgrade could move it. A guard that silently passes
  when its input is missing is the precise failure mode this epic exists to
  eliminate.

Floor rationale: 500 catches total absence (wiring broken, wrong variant,
profile never generated) while staying immune to the ~4% run-to-run
non-determinism of the production generator. A **partial** loss — 6,452 dropping
to 900 — passes. That is a deliberate, accepted limitation, chosen over a
proportional or exact-count floor that would cry wolf on every regen.

### 3. Documentation — the two lanes (`nubecita-6row.3`)

| Lane | Command | Answers |
|---|---|---|
| Bench flavor (default) | `:benchmark:connectedBenchmarkReleaseAndroidTest` | Did startup regress? Deterministic, offline, CI-able. |
| Production flavor | same + `-PbaselineProfileEnvironment=production` | Does the *shipped* profile help? Real signed-in cold start. |

Run the production lane before shipping a profile regen; the bench lane for
routine trend tracking.

Updates `benchmark/README.md` and
`.claude/skills/run-startup-bench/SKILL.md`. Also fixes
`app/build.gradle.kts:399`, which still documents the generator as writing into
`app/src/release/generated/baselineProfiles/`; the flavor-decoupled setup writes
to `src/<flavor>Release/`.

## Delivery

An epic → one `gh stack` of three PRs, landed atomically.

Ordering matters: the guard from `.2` would fail on `main` if it landed before
`.1` created the bench profile. A stack keeps `main` from ever seeing that
intermediate state and cuts one release for the whole epic.

Per repo convention the release type is the maximum across the stack. All three
child PR titles must therefore stay off `feat`, `fix`, and **`perf`** — the
default `conventionalcommits` rules release on `perf` as a patch, which is why
child `.1` is titled `chore(profile):` and not `perf(profile):`. The bench
profile is a bench-only artifact that never reaches a user, so `chore` is also
the honest description. As titled, this epic cuts no release, which is correct:
nothing user-facing changes.

## Explicit non-goals

- **Detecting partial profile degradation.** Floor is absence-only, by choice.
- **Making the production lane deterministic or CI-able.** It is inherently
  non-deterministic (real network, signed-in state). It stays a manual
  pre-ship check.
- **Changing what ships.** The production profile already reaches users
  correctly.
- **Re-running the 1.338.x profile validation.** PR #889 already merged on the
  strength of content analysis (`AppTheme` 0 → 37 rules including
  `MainActivity_MembersInjector->injectAppThemeState`). Once this epic lands, a
  production-lane run can confirm it at runtime, but that is follow-up, not a
  blocker.
