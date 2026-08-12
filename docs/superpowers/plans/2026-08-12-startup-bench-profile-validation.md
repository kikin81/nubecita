# Startup-Bench Profile Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the nightly Macrobench's `COLD-BaselineProfile` cell measure an APK that actually contains Nubecita's baseline profile, and make it impossible for that to silently regress again.

**Architecture:** Four changes delivered as one `gh stack`, landed atomically. The nightly workflow generates the bench-flavor profile before measuring (nothing committed); a Gradle task fails any release variant whose merged ART profile lacks app rules; `StartupBenchmark` gains JIT/ClassInit trace metrics that measure profile effectiveness behaviourally; docs record the two lanes and correct three false claims.

**Tech Stack:** AGP 9 + `androidx.baselineprofile` (consumer on `:app`, producer on `:benchmark`), AndroidX Macrobenchmark 1.5.0-alpha07, Gradle with Configuration Cache enabled, GitHub Actions + `benchmark-action/github-action-benchmark`, `reactivecircus/android-emulator-runner`.

**Spec:** `docs/superpowers/specs/2026-08-11-startup-bench-profile-validation-design.md`
**Epic:** `nubecita-6row` — children `.1` `.2` `.4` `.3` (stack order)

## Global Constraints

- **Root cause is confirmed, not hypothesised.** Spike on 2026-08-11: creating `app/src/benchRelease/generated/baselineProfiles/` took `benchBenchmarkRelease`'s merged ART profile from **13,645 rules / 0 app rules** to **61,501 rules / 5,941 app rules**. Build on this; do not re-litigate it.
- **Commit no baseline profile.** `app/src/benchRelease/generated/` is generated, never committed. Task 1 adds the gitignore entry.
- **Configuration Cache is on** (`gradle.properties:12`, `org.gradle.configuration-cache = true`). Never resolve a file path at configuration time — no `.get()`, no `File(...)`, no `project` reference inside a task action.
- **No cross-project task dependencies.** The guard registers inside `:app` only. Do NOT add a `dependsOn` from `:benchmark:connectedBenchmarkReleaseAndroidTest` to an `:app` task — it conflicts with Project Isolation.
- **Fail loud, never skip.** Any check that cannot find its input must fail. A guard that silently passes when its input is missing recreates the exact bug this epic fixes.
- **App rule floor is 500.** Deliberately absence-only; immune to the ~4% run-to-run generator non-determinism. Partial loss is out of scope.
- **PR titles must avoid `feat`, `fix`, and `perf`.** Default `conventionalcommits` rules release on all three. Use `ci` / `test` / `test` / `docs` as titled below. The epic must cut no release.
- **Flavored module task names.** `:app` carries the `bench`/`production` flavor dimension — use flavored task names (`:app:lintProductionDebug`, not `:app:lintDebug`).
- **Measured timing.** Bench-flavor generation took **16m24s** on a Pixel Fold. `macrobench.yaml:82` sets `timeout-minutes: 60` for the whole job.
- **Never commit with `--no-verify`.** Hooks may rewrite files (the EOF fixer rewrites profile files); re-stage and commit again.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `.gitignore` | Ignore the generated bench profile dir | 1 |
| `.github/workflows/macrobench.yaml` | Generate profile before measuring; split trend charts | 1, 4 |
| `app/build.gradle.kts` | Register the guard for 3 variants; fix stale comment | 2, 5 |
| `benchmark/src/main/kotlin/net/kikin/nubecita/benchmark/BaselineProfileMetrics.kt` | **New.** Effectiveness metric definitions | 3 |
| `benchmark/src/main/kotlin/net/kikin/nubecita/benchmark/StartupBenchmark.kt` | Consume the metrics | 3 |
| `benchmark/README.md` | Two lanes; remove false `requiresPhysicalDevice` claim | 5 |
| `.claude/skills/run-startup-bench/SKILL.md` | Two lanes for the operator workflow | 5 |

---

## Plan Validation (run BEFORE Task 1)

The spec's premise is verified, but three assumptions in *this plan* are not. Each is cheap to check and expensive to discover mid-stack. Do these first; if any fails, stop and revise the plan.

- [ ] **V1: Confirm the guard's input path and task name exist**

```bash
cd /Users/francisco/code/nubecita
./gradlew :app:tasks --all | grep -i "ArtProfile" | head
ls app/build/intermediates/merged_art_profile/
```

Expected: a `mergeBenchBenchmarkReleaseArtProfile`-style task exists, and `merged_art_profile/<variant>/` directories are present. If the task name differs from `merge<Variant>ArtProfile`, Task 2's `tasks.named(...)` string must change to match.

**Result 2026-08-12: PASS.** All three exist — `mergeBenchBenchmarkReleaseArtProfile`, `mergeProductionBenchmarkReleaseArtProfile`, `mergeProductionReleaseArtProfile`.

Also verified, and it changed the design: the release pipeline ships an **AAB** (`fastlane/Fastfile:118` → `bundleProductionRelease`). `./gradlew :app:bundleProductionRelease --dry-run` shows `:app:mergeProductionReleaseArtProfile` present and `:app:packageProductionRelease` **absent** (count 0). Task 2 therefore hooks the merge task, not the package task — hooking the package task would leave the shipped artifact unguarded.

- [ ] **V2: Confirm `productionRelease` currently passes the 500 floor**

```bash
grep -c 'net/kikin/nubecita' \
  app/build/intermediates/merged_art_profile/productionRelease/*/baseline-prof.txt
```

Expected: a number well above 500 (was 6,347). If it is below 500, Task 2 would land red on the shipping variant — stop and investigate before registering the guard there.

**Result 2026-08-12: PASS** — 6,347 app rules.

- [ ] **V3: Confirm the emulator emits ART JIT trace slices**

This is the one genuine unknown in the plan. `TraceSectionMetric` on a SwiftShader emulator may report zero if the slices never emit. Deferred to Task 3 Step 6, which measures it directly on a real run rather than guessing. Note here so it is not forgotten.

---

## Task 1: Generate the bench profile in the nightly (`nubecita-6row.1`)

**Branch:** `ci/nubecita-6row.1-generate-bench-profile-in-nightly`
**PR title:** `ci(bench): generate the bench baseline profile in the Macrobench workflow`

**Files:**
- Modify: `.gitignore`
- Modify: `.github/workflows/macrobench.yaml:142` (insert a step before `Run Macrobench`)

**Interfaces:**
- Consumes: nothing (first task in the stack)
- Produces: `app/src/benchRelease/generated/baselineProfiles/{startup,baseline}-prof.txt` exists at bench-run time in CI. Task 2's guard depends on this being true, or the nightly fails.

- [ ] **Step 1: Add the gitignore entry**

Append to `.gitignore`:

```gitignore
# Bench-flavor baseline profile. Generated per-run (nightly in CI, on demand
# locally) and never committed — see docs/superpowers/specs/2026-08-11-
# startup-bench-profile-validation-design.md. The production profile at
# app/src/productionRelease/generated/ IS committed; this one is not.
app/src/benchRelease/generated/
```

- [ ] **Step 2: Verify the spike artifacts are now ignored**

```bash
git status --porcelain | grep benchRelease
```

Expected: **no output**. Before this step `?? app/src/benchRelease/` appeared; now it is ignored. This confirms the pattern matches the real directory rather than a guess at its name.

- [ ] **Step 3: Wire the generated dir into `clean`**

In `app/build.gradle.kts`, inside the existing top-level `tasks` configuration (or add one if absent), add:

```kotlin
// The bench-flavor baseline profile is gitignored and regenerated on demand,
// so it survives branch switches. A stale profile from another branch can mask
// newly-broken generation on this one — `clean` must reset it. See
// nubecita-6row.1.
tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("src/benchRelease/generated"))
}
```

- [ ] **Step 4: Verify `clean` removes it**

```bash
ls app/src/benchRelease/generated/baselineProfiles/ | wc -l   # expect 2
./gradlew :app:clean
ls app/src/benchRelease/generated 2>&1                        # expect: No such file or directory
```

Expected: the directory is gone. **This deletes the spike artifacts** — that is intended: CI regenerates them every night via Step 5, and Step 8 regenerates them locally. Restore with `./gradlew :app:generateBenchReleaseBaselineProfile` (~16 min).

- [ ] **Step 5: Add the generation step to the workflow**

In `.github/workflows/macrobench.yaml`, insert immediately **before** the `- name: Run Macrobench` step at line 142:

```yaml
      # Generate the bench-flavor baseline profile before measuring. Without
      # this, `benchBenchmarkRelease` carries ZERO app rules and the
      # COLD-BaselineProfile cell measures only library profiles — the bug
      # tracked as nubecita-6row. Verified 2026-08-11: generating this profile
      # takes benchBenchmarkRelease from 13,645 rules / 0 app rules to
      # 61,501 / 5,941.
      #
      # Nothing is committed: app/src/benchRelease/generated/ is gitignored and
      # this runner is a fresh checkout every night.
      #
      # No retry and no fallback. If generation fails, the workflow fails —
      # proceeding to measure without a profile would silently recreate the
      # exact bug this step exists to prevent.
      - name: Generate bench baseline profile
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 35
          target: google_apis
          arch: x86_64
          profile: pixel_6
          force-avd-creation: false
          emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          disable-animations: true
          script: ./gradlew :app:generateBenchReleaseBaselineProfile
```

- [ ] **Step 6: Raise the job timeout**

Change `.github/workflows/macrobench.yaml:82` from `timeout-minutes: 60` to:

```yaml
    # 90, not 60: profile generation alone measured 16m24s on a Pixel Fold
    # (nubecita-6row.1) and emulators are slower. Generation + AVD boot +
    # build + the bench run no longer fit the old budget.
    timeout-minutes: 90
```

- [ ] **Step 7: Validate the workflow file parses**

```bash
cd /Users/francisco/code/nubecita
pre-commit run --files .github/workflows/macrobench.yaml
```

Expected: `check yaml` and `Lint GitHub Actions workflow files` both **Passed**.

- [ ] **Step 8: Regenerate locally and prove the fix end-to-end**

```bash
./gradlew :app:generateBenchReleaseBaselineProfile
./gradlew :app:assembleBenchBenchmarkRelease
grep -c 'net/kikin/nubecita' \
  app/build/intermediates/merged_art_profile/benchBenchmarkRelease/*/baseline-prof.txt
```

Expected: **a number in the thousands** (spike measured 5,941). If it is 0, stop — the premise has broken and Tasks 2 and 4 are invalid.

Note: this takes ~16 minutes.

- [ ] **Step 9: Commit**

```bash
git add .gitignore app/build.gradle.kts .github/workflows/macrobench.yaml
git commit -m "$(cat <<'EOF'
ci(bench): generate the bench baseline profile in the Macrobench workflow

benchBenchmarkRelease carried zero app baseline-profile rules, so the
nightly's COLD-BaselineProfile cell measured only library profiles.

Generate the bench-flavor profile on the existing emulator before
measuring, commit nothing, and gitignore the output. Verified: this takes
benchBenchmarkRelease from 13,645 rules / 0 app rules to 61,501 / 5,941.

Wire the generated dir into clean, because a gitignored profile survives
branch switches and a stale one can mask newly-broken generation.

Raise the job timeout to 90 minutes: generation alone measured 16m24s on
a physical device and emulators are slower.

Refs: nubecita-6row.1
EOF
)"
```

---

## Task 2: Guard release variants against a missing app profile (`nubecita-6row.2`)

**Branch:** `test/nubecita-6row.2-guard-missing-app-profile-rules`
**PR title:** `test(bench): fail the build when a release variant carries no app profile rules`

**Files:**
- Modify: `app/build.gradle.kts` (add task registration near the existing `baselineProfile` wiring at line ~397)

**Interfaces:**
- Consumes: Task 1's guarantee that `app/src/benchRelease/generated/` is populated before a bench build.
- Produces: task `:app:verify<Variant>BaselineProfileRules` for `benchBenchmarkRelease`, `productionBenchmarkRelease`, `productionRelease`. Each fails the build when app rules < 500 or the input is missing.

- [ ] **Step 1: Write the failing check by deleting the profile**

Reproduce the bug state so the guard has something real to catch:

```bash
cd /Users/francisco/code/nubecita
mv app/src/benchRelease/generated /tmp/benchprof-backup
./gradlew :app:assembleBenchBenchmarkRelease
grep -c 'net/kikin/nubecita' \
  app/build/intermediates/merged_art_profile/benchBenchmarkRelease/*/baseline-prof.txt
```

Expected: `0`, and the build **succeeds**. That success is the bug — the guard must turn it into a failure.

- [ ] **Step 2: Add the verification task**

In `app/build.gradle.kts`, add near the `"baselineProfile"(project(":benchmark"))` wiring:

```kotlin
/**
 * Fails the build when a release variant's merged ART profile carries no
 * meaningful app rules.
 *
 * This exists because `CompilationMode.Partial(BaselineProfileMode.Require)`
 * does NOT catch it: `Require` only asserts that *a* profile installed, and a
 * library-only profile satisfies it. That gap let benchBenchmarkRelease ship 0
 * app rules into every nightly measurement (nubecita-6row).
 *
 * Floor is absence-only by design: it catches a broken wiring, the wrong
 * variant, or a profile that was never generated, while staying immune to the
 * ~4% run-to-run non-determinism of the generator.
 */
abstract class VerifyBaselineProfileRulesTask : DefaultTask() {
    // ConfigurableFileCollection + @InputFiles rather than the @InputDirectory
    // the spec sketches: `tasks.named(...).map { it.outputs.files }` yields a
    // file collection, and this form consumes it directly while still carrying
    // the task dependency. Same Configuration Cache guarantee, less plumbing.
    @get:InputFiles
    abstract val artProfile: ConfigurableFileCollection

    @get:Input
    abstract val variantName: Property<String>

    @TaskAction
    fun verify() {
        val file = artProfile.files.firstOrNull { it.name == "baseline-prof.txt" }
            ?: error(
                "Baseline-profile guard could not find baseline-prof.txt for " +
                    "${variantName.get()}. This is a FAILURE, not a skip: an AGP " +
                    "upgrade may have moved or renamed the merged-ART-profile " +
                    "output. Fix the wiring in app/build.gradle.kts — do not " +
                    "disable this check (nubecita-6row.2).",
            )
        val appRules = file.useLines { lines ->
            lines.count { it.contains("net/kikin/nubecita") }
        }
        if (appRules < MIN_APP_PROFILE_RULES) {
            error(
                "Variant ${variantName.get()} has only $appRules app baseline-profile " +
                    "rules (floor $MIN_APP_PROFILE_RULES). Its APK would be measured or " +
                    "shipped with a library-only profile.\n" +
                    "  Fix: ./gradlew :app:generateBenchReleaseBaselineProfile\n" +
                    "  Background: docs/superpowers/specs/" +
                    "2026-08-11-startup-bench-profile-validation-design.md",
            )
        }
        logger.lifecycle("Baseline profile OK for ${variantName.get()}: $appRules app rules")
    }

    companion object {
        const val MIN_APP_PROFILE_RULES = 500
    }
}

// Registered for the two benchmark variants AND productionRelease. Guarding
// only the benchmark variants would protect the measurement while leaving the
// shipped product unguarded.
listOf(
    "benchBenchmarkRelease",
    "productionBenchmarkRelease",
    "productionRelease",
).forEach { variant ->
    val capitalized = variant.replaceFirstChar { it.uppercase() }
    val verify = tasks.register<VerifyBaselineProfileRulesTask>("verify${capitalized}BaselineProfileRules") {
        // flatMap the producing task's output — never resolve a path at
        // configuration time, or Configuration Cache rejects the build.
        artProfile.from(
            tasks.named("merge${capitalized}ArtProfile").map { it.outputs.files },
        )
        variantName.set(variant)
    }
    // finalizedBy the MERGE task, not the package task.
    //
    // The release pipeline ships an AAB (`fastlane/Fastfile:118` runs
    // `bundleProductionRelease`), and `packageProductionRelease` is NOT in the
    // bundle task graph — verified with `bundleProductionRelease --dry-run`.
    // Hooking the package task would leave the artifact users actually receive
    // unguarded, which is the whole point of registering productionRelease.
    //
    // `merge<Variant>ArtProfile` IS in both the APK and AAB graphs, and running
    // right after the profile is merged means the guard fires earlier than
    // packaging — well before any device work.
    tasks.named("merge${capitalized}ArtProfile") { finalizedBy(verify) }
}
```

- [ ] **Step 3: Run the guard against the broken state — expect FAILURE**

```bash
./gradlew :app:assembleBenchBenchmarkRelease
```

Expected: **build FAILS** with `has only 0 app baseline-profile rules (floor 500)` and the `generateBenchReleaseBaselineProfile` fix line. If it passes, the guard is not wired into the task graph.

- [ ] **Step 4: Restore the profile and re-run — expect PASS**

```bash
mv /tmp/benchprof-backup app/src/benchRelease/generated
./gradlew :app:assembleBenchBenchmarkRelease
```

Expected: **build SUCCEEDS**, log line `Baseline profile OK for benchBenchmarkRelease: 5941 app rules` (exact count may vary).

- [ ] **Step 5: Verify the shipping variant passes**

```bash
./gradlew :app:assembleProductionRelease -PdebugSignedRelease=true
```

Expected: SUCCEEDS with `Baseline profile OK for productionRelease: <thousands> app rules`.

- [ ] **Step 6: Verify Configuration Cache compatibility**

```bash
./gradlew :app:assembleBenchBenchmarkRelease --configuration-cache
./gradlew :app:assembleBenchBenchmarkRelease --configuration-cache
```

Expected: the second run prints `Reusing configuration cache.` and **no** configuration-cache problems are reported. If it reports a violation, the path was resolved at configuration time — revisit the `flatMap`.

- [ ] **Step 7: Lint the module**

```bash
./gradlew :app:lintProductionDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts
git commit -m "$(cat <<'EOF'
test(bench): fail the build when a release variant carries no app rules

CompilationMode.Partial(BaselineProfileMode.Require) does not catch a
library-only profile: Require asserts only that *a* profile installed.
That gap let benchBenchmarkRelease feed 0 app rules into every nightly
measurement.

Add :app:verify<Variant>BaselineProfileRules for benchBenchmarkRelease,
productionBenchmarkRelease and productionRelease. The third matters most
— guarding only the benchmark variants would protect the measurement
while leaving the shipped product unguarded.

The task fails, never skips, when it cannot find its input: the merged
ART profile is an AGP internal and an upgrade could move it.

Refs: nubecita-6row.2
EOF
)"
```

---

## Task 3: Profile-effectiveness metrics (`nubecita-6row.4`)

**Branch:** `test/nubecita-6row.4-profile-effectiveness-metrics`
**PR title:** `test(bench): measure baseline-profile effectiveness with JIT and ClassInit metrics`

**Files:**
- Create: `benchmark/src/main/kotlin/net/kikin/nubecita/benchmark/BaselineProfileMetrics.kt`
- Modify: `benchmark/src/main/kotlin/net/kikin/nubecita/benchmark/StartupBenchmark.kt:62`

**Interfaces:**
- Consumes: Task 1's generated profile (metrics are meaningless without one).
- Produces: `BaselineProfileMetrics.allMetrics: List<Metric>` — consumed by `StartupBenchmark`, and new `benchmarkData.json` metric keys consumed by Task 4's workflow split.

- [ ] **Step 1: Create the metrics file**

```kotlin
package net.kikin.nubecita.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric

/**
 * Metrics that measure whether the baseline profile is actually *working*,
 * rather than whether one is merely present.
 *
 * TTID alone cannot answer that. When `benchBenchmarkRelease` shipped zero app
 * profile rules (nubecita-6row), the COLD-BaselineProfile cell still showed a
 * 17.9% TTID improvement — entirely from library profiles — and the missing app
 * profile went unnoticed for months.
 *
 * Both metrics should DROP when a profile is applied properly, because AOT
 * compilation removes work the runtime would otherwise do at startup.
 *
 * The `%` is a genuine wildcard: `TraceSectionMetric` builds a SQL `LIKE` query
 * against the Perfetto trace, and SQLite's `LIKE` is ASCII case-insensitive —
 * so "JIT Compiling %" matches the runtime's lowercase "JIT compiling …".
 * See androidx.benchmark.macro 1.5.0-alpha07, Metric.kt:527.
 */
object BaselineProfileMetrics {
    /** Time spent JIT-compiling. Falls when methods are AOT-compiled instead. */
    @OptIn(ExperimentalMetricApi::class)
    val jitCompilationMetric = TraceSectionMetric("JIT Compiling %", label = "JIT compilation")

    /** Time spent initialising classes. Falls when classes are pre-resolved. */
    @OptIn(ExperimentalMetricApi::class)
    val classInitMetric = TraceSectionMetric("L%/%;", label = "ClassInit")

    /** Startup timing plus the two effectiveness metrics. */
    @OptIn(ExperimentalMetricApi::class)
    val allMetrics: List<Metric> = listOf(StartupTimingMetric(), jitCompilationMetric, classInitMetric)
}
```

- [ ] **Step 2: Consume it in StartupBenchmark**

In `benchmark/src/main/kotlin/net/kikin/nubecita/benchmark/StartupBenchmark.kt`, replace line 62:

```kotlin
            metrics = listOf(StartupTimingMetric()),
```

with:

```kotlin
            metrics = BaselineProfileMetrics.allMetrics,
```

Then remove the now-unused import on line 6 (`import androidx.benchmark.macro.StartupTimingMetric`).

- [ ] **Step 3: Compile the benchmark module**

```bash
cd /Users/francisco/code/nubecita
./gradlew :benchmark:compileBenchmarkReleaseKotlin
```

Expected: BUILD SUCCESSFUL. If it fails on the unused import, delete the import line.

- [ ] **Step 4: Run spotless**

```bash
./gradlew spotlessApply
```

- [ ] **Step 5: Run the COLD cells on a device**

```bash
# Confirm exactly one device. Gradle installs to ALL connected devices, and a
# second device makes the runner's picker fail with "No connected devices!".
adb devices -l
adb -s 37201FDHS002UN shell settings put global stay_on_while_plugged_in 7

ANDROID_SERIAL=37201FDHS002UN ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.kikin.nubecita.benchmark.StartupBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.tests_regex='startup.COLD.*' \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.targetDeviceSerial=37201FDHS002UN
```

- [ ] **Step 6: Verify the metrics are non-zero and directional (V3)**

```bash
python3 -c "
import json, glob
f = glob.glob('benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/*/net.kikin.nubecita.benchmark-benchmarkData.json')[0]
for b in json.load(open(f))['benchmarks']:
    print(b['name'])
    for k, v in b['metrics'].items():
        if v.get('median') is not None:
            print(f'   {k:34s} median={v[\"median\"]:9.2f}')
"
```

Expected, and **this is the acceptance criterion for the whole task**:

1. JIT and ClassInit metrics appear at all.
2. Their median in the **`COLD-None`** cell is **non-zero**. A zero here means the trace slices never emitted and the metric is blind — it does NOT mean the profile is perfect. If zero, stop: document the metric as emulator/device-blind rather than shipping a check that always reports success.
3. Their median in **`COLD-BaselineProfile`** is **lower** than in `COLD-None`. That is the profile working.

Record all four numbers — they go in the PR body and the `nubecita-crmi` epic comment.

- [ ] **Step 7: Commit**

```bash
git add benchmark/src/main/kotlin/net/kikin/nubecita/benchmark/BaselineProfileMetrics.kt \
        benchmark/src/main/kotlin/net/kikin/nubecita/benchmark/StartupBenchmark.kt
git commit -m "$(cat <<'EOF'
test(bench): measure baseline-profile effectiveness with JIT and ClassInit

TTID cannot tell whether the app's own profile is working. When
benchBenchmarkRelease shipped zero app rules, COLD-BaselineProfile still
showed a 17.9% TTID gain — all of it from library profiles — and the
missing profile went unnoticed for months.

Add TraceSectionMetric-based JIT-compilation and ClassInit metrics,
modelled on Now in Android's BaselineProfileMetrics. Both fall when a
profile is genuinely applied, so the failure becomes visible in the
reported number instead of requiring a dig through merged_art_profile.

Refs: nubecita-6row.4
EOF
)"
```

---

## Task 4: Keep the new metrics off the nightly's alert gate (`nubecita-6row.4`, same PR)

**Files:**
- Modify: `.github/workflows/macrobench.yaml:187-243`

**Interfaces:**
- Consumes: Task 3's new metric keys in `benchmarkData.json`.
- Produces: two gh-pages charts — `Nubecita Macrobench` (gated) and `Baseline profile effectiveness` (observed only).

- [ ] **Step 1: Split the converter into two payloads**

In the `Convert to custom benchmark JSON` step (line 187), the existing `jq` emits one array. Replace its body so it writes two files, partitioning on metric name:

```bash
          OUT=benchmark/build/macrobench-custom.json
          OUT_BP=benchmark/build/macrobench-baselineprofile.json
          # Partition: effectiveness metrics (JIT / ClassInit) are observed but
          # NOT gated — they are CPU-bound and noisy on a swiftshader emulator,
          # and would trip the 150% alert threshold on variance alone
          # (nubecita-6row.4). Timing metrics keep the existing gate.
          jq '[ .benchmarks[]
            | (.className | split(".") | last) as $cls
            | .name as $bench
            | .metrics | to_entries[]
            | select(.value.median != null)
            | select(.key | test("JIT|ClassInit") | not)
            | { name: "\($cls).\($bench) / \(.key)",
                unit: (if   (.key | endswith("Ms")) then "ms"
                       elif  .key == "frameCount"    then "frames"
                       else  "" end),
                value: (.value.median * 1000 | round / 1000) }
              + (if .value.coefficientOfVariation != null
                 then { range: "+/- \(.value.coefficientOfVariation * 1000 | round / 10)%" }
                 else {} end) ]' \
            "${{ steps.bench-json.outputs.path }}" > "$OUT"
          jq '[ .benchmarks[]
            | (.className | split(".") | last) as $cls
            | .name as $bench
            | .metrics | to_entries[]
            | select(.value.median != null)
            | select(.key | test("JIT|ClassInit"))
            | { name: "\($cls).\($bench) / \(.key)",
                unit: (if (.key | endswith("Ms")) then "ms" else "" end),
                value: (.value.median * 1000 | round / 1000) }
              + (if .value.coefficientOfVariation != null
                 then { range: "+/- \(.value.coefficientOfVariation * 1000 | round / 10)%" }
                 else {} end) ]' \
            "${{ steps.bench-json.outputs.path }}" > "$OUT_BP"
          COUNT=$(jq 'length' "$OUT")
          if [ "$COUNT" -eq 0 ]; then
            echo "::error::No benchmark metrics found in ${{ steps.bench-json.outputs.path }}"
            exit 1
          fi
          COUNT_BP=$(jq 'length' "$OUT_BP")
          if [ "$COUNT_BP" -eq 0 ]; then
            echo "::error::No baseline-profile effectiveness metrics found. StartupBenchmark should emit JIT/ClassInit metrics (nubecita-6row.4); a silently-empty set means the trace sections did not emit."
            exit 1
          fi
          echo "Converted $COUNT timing metric(s), $COUNT_BP effectiveness metric(s)"
          echo "path=$OUT" >> "$GITHUB_OUTPUT"
          echo "path_bp=$OUT_BP" >> "$GITHUB_OUTPUT"
```

- [ ] **Step 2: Add the ungated effectiveness chart step**

After the existing `Store benchmark result (trend + alert)` step (line 216), add:

```yaml
      # Effectiveness metrics are OBSERVED, NOT ENFORCED. JIT compilation is
      # CPU-bound and materially noisier on a swiftshader emulator than on real
      # hardware, so gating them at the timing chart's 150% threshold would fail
      # the nightly on variance alone. Once enough history exists to know the
      # real variance on this runner, set a threshold from data instead of
      # guessing one (nubecita-6row.4).
      - name: Store baseline-profile effectiveness (trend only)
        if: github.event_name != 'pull_request'
        uses: benchmark-action/github-action-benchmark@v1
        with:
          name: Baseline profile effectiveness
          tool: customSmallerIsBetter
          output-file-path: ${{ steps.bench-convert.outputs.path_bp }}
          github-token: ${{ secrets.GITHUB_TOKEN }}
          gh-pages-branch: gh-pages
          auto-push: true
          fail-on-alert: false
```

- [ ] **Step 3: Validate the workflow and the jq locally**

```bash
pre-commit run --files .github/workflows/macrobench.yaml
```

Expected: `check yaml` and the actionlint hook both **Passed**.

Then dry-run the partition against the real JSON from Task 3 Step 5:

```bash
JSON=$(find benchmark/build/outputs/connected_android_test_additional_output -name '*-benchmarkData.json' | head -1)
jq '[.benchmarks[] | .metrics | to_entries[] | select(.value.median != null) | select(.key | test("JIT|ClassInit")) | .key]' "$JSON"
```

Expected: a **non-empty** array of the JIT/ClassInit keys. Empty means the partition regex does not match the real metric names — fix the regex to match what Task 3 Step 6 actually printed.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/macrobench.yaml
git commit -m "$(cat <<'EOF'
ci(bench): chart profile-effectiveness metrics without gating on them

The jq converter walks .metrics generically, so the new JIT/ClassInit
metrics would land on the existing trend and inherit its 150% alert
threshold with fail-on-alert. Those metrics are CPU-bound and noisy on a
swiftshader emulator, so they would fail the nightly on variance alone.

Partition the payload: timing keeps the gate, effectiveness gets its own
ungated chart. Set a real threshold later from observed history.

Refs: nubecita-6row.4
EOF
)"
```

---

## Task 5: Documentation and corrections (`nubecita-6row.3`)

**Branch:** `docs/nubecita-6row.3-two-benchmark-lanes`
**PR title:** `docs(bench): document the two benchmark lanes`

**Files:**
- Modify: `.github/workflows/macrobench.yaml:1-30` (header comment)
- Modify: `benchmark/README.md:104-112`
- Modify: `app/build.gradle.kts:397-402` (comment only)
- Modify: `.claude/skills/run-startup-bench/SKILL.md`

**Interfaces:**
- Consumes: the finished behaviour of Tasks 1–4.
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Correct the workflow header**

In `.github/workflows/macrobench.yaml`, replace the `WHAT THIS WORKFLOW USES vs. NEVER DOES` block with:

```yaml
# WHAT THIS WORKFLOW DOES
# -----------------------
# - It GENERATES the bench-flavor baseline profile on the emulator before
#   measuring (see the "Generate bench baseline profile" step), then measures
#   against it. Nothing is committed: app/src/benchRelease/generated/ is
#   gitignored and this runner is a fresh checkout every night.
# - It does NOT generate the *production* profile. That needs the signed-in
#   OAuth cold-start path, which cannot be automated — production regen stays a
#   manual on-device task (see benchmark/README.md).
#
# Before nubecita-6row this workflow measured an APK containing ZERO app
# baseline-profile rules, so its COLD-BaselineProfile cell reflected library
# profiles only.
```

- [ ] **Step 2: Correct `benchmark/README.md`**

Replace the `> **Baseline profile in CI.**` block at lines 104-112 with:

```markdown
> **Baseline profile in CI.** The workflow *generates* the bench-flavor
> profile on the emulator before measuring — see the "Generate bench
> baseline profile" step. Nothing is committed; `app/src/benchRelease/generated/`
> is gitignored.
>
> It does **not** generate the production profile: that requires the
> signed-in OAuth cold-start path, which cannot run unattended. Production
> regen stays the manual on-device task documented below.
>
> (Historical note: this section previously claimed generation was blocked
> by `requiresPhysicalDevice`. No such setting exists — not in Gradle, AGP,
> or `androidx.baselineprofile`. Bench-flavor generation runs fine
> unattended because the bench flavor fakes `SignedIn` at boot.)
```

- [ ] **Step 3: Add the two-lane table to `benchmark/README.md`**

Immediately after the block from Step 2:

```markdown
### Two benchmark lanes

| Lane | Command | Answers |
|---|---|---|
| **Bench** (default) | `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` | Did startup regress? Deterministic, offline, CI-able. Runs nightly. |
| **Production** | same, plus `-PbaselineProfileEnvironment=production` | Does the *shipped* profile help? Real signed-in cold start. Manual, on-device, before shipping a profile regen. |

The production lane can never be automated — OAuth is not mockable. Use the
bench lane for trend tracking and the production lane to validate a regen
before it ships.

A locally-generated bench profile is only valid for the branch that produced
it. `./gradlew :app:clean` resets it.
```

- [ ] **Step 4: Fix the stale comment in `app/build.gradle.kts`**

Replace lines 397-402 with:

```kotlin
    // Baseline profile producer wiring — `:benchmark`'s
    // `BaselineProfileGenerator` writes startup-prof.txt + baseline-prof.txt
    // into `app/src/<flavor>Release/generated/baselineProfiles/` (plugin
    // default `saveInSrc = true`), and the androidx.baselineprofile plugin
    // picks them up at release assembly.
    //
    // productionRelease/ IS committed; benchRelease/ is gitignored and
    // regenerated (nightly in CI, on demand locally). Production regen is
    // manual and on-device — see benchmark/README.md for cadence.
```

- [ ] **Step 5: Update the run-startup-bench skill**

In `.claude/skills/run-startup-bench/SKILL.md`, under `## Commands`, add before the existing generate command:

```markdown
**Which lane?** The bench flavor answers "did startup regress" (deterministic,
offline, runs nightly). The production flavor answers "does the shipped profile
help" (real signed-in cold start, manual only — OAuth cannot be automated). Run
the production lane before shipping a profile regen.

**Generate the BENCH profile** (needed before any local bench run — it is
gitignored, so a fresh checkout has none, and `:app:verify…BaselineProfileRules`
will fail the build until you run this):

```bash
./gradlew :app:generateBenchReleaseBaselineProfile
```
```

- [ ] **Step 6: Verify no stale claims remain**

```bash
cd /Users/francisco/code/nubecita
grep -rn "requiresPhysicalDevice" . 2>/dev/null | grep -v "/build/" | grep -v "\.git/" | grep -v "docs/superpowers"
```

Expected: **no output** outside the spec/plan docs. Any hit in `macrobench.yaml` or `benchmark/README.md` means Steps 1-2 missed one.

```bash
grep -rn "src/release/generated" app/build.gradle.kts .github/workflows/macrobench.yaml benchmark/README.md
```

Expected: **no output**.

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/macrobench.yaml benchmark/README.md app/build.gradle.kts \
        .claude/skills/run-startup-bench/SKILL.md
git commit -m "$(cat <<'EOF'
docs(bench): document the two benchmark lanes

Record the split: the bench lane answers "did startup regress"
(deterministic, offline, nightly) and the production lane answers "does
the shipped profile help" (real signed-in cold start, manual only,
because OAuth cannot be automated).

Correct three false claims in the Macrobench workflow header: it does now
generate a profile rather than only using a committed one; there is no
requiresPhysicalDevice gate anywhere in this repo; and the schedule is
nightly. The requiresPhysicalDevice claim was duplicated into
benchmark/README.md, so both copies are fixed.

Also fix app/build.gradle.kts, which documented the generator as writing
to src/release/ rather than src/<flavor>Release/.

Refs: nubecita-6row.3
EOF
)"
```

---

## Implementation Validation (run BEFORE `gh stack merge`)

Per CLAUDE.md, the top branch runs the full gate against the **cumulative** stack diff.

- [ ] **Step 1: Full build**

```bash
cd /Users/francisco/code/nubecita
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Lint every touched module**

```bash
./gradlew :app:lintProductionDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Prove the guard still catches the original bug**

The single most important check — it verifies the epic actually fixed what it claims:

```bash
mv app/src/benchRelease/generated /tmp/benchprof-verify
./gradlew :app:assembleBenchBenchmarkRelease   # expect FAILURE with the 500-rule message
mv /tmp/benchprof-verify app/src/benchRelease/generated
./gradlew :app:assembleBenchBenchmarkRelease   # expect SUCCESS
```

- [ ] **Step 4: Confirm the stack cuts no release**

```bash
gh pr list --head <top-branch> --json title
```

Every PR title in the stack must start with `ci(`, `test(`, or `docs(` — never `feat`, `fix`, or `perf`. Confirm the release bot comment on each PR reads **"No new release will be created."**

- [ ] **Step 5: Confirm every PR has a review newer than its last commit**

Neither bot re-reviews on push, so a restack silently invalidates reviews on every branch above it.

```bash
for n in <pr numbers>; do
  echo "PR #$n"
  gh api /repos/kikin81/nubecita/pulls/$n/commits --jq '.[-1].commit.committer.date' | sed 's/^/  last commit: /'
  gh pr view $n --json reviews --jq '[.reviews[].submittedAt] | max' | sed 's/^/  newest review: /'
done
```

If a review predates the last commit, re-request with `gh pr comment <n> --body "/gemini review"`.

- [ ] **Step 6: Land the stack**

```bash
gh stack merge --squash --yes
gh run list --workflow=release.yaml --limit 5
```

Expected: exactly **one** Release run (confirming the atomic-merge assumption), and it should conclude without publishing a version.

- [ ] **Step 7: Close the bd issues and log the result**

```bash
bd close nubecita-6row.1 nubecita-6row.2 nubecita-6row.3 nubecita-6row.4 nubecita-6row
bd comment nubecita-crmi "<post-epic StartupBenchmark numbers, including the new JIT/ClassInit medians for both COLD cells>"
```

- [ ] **Step 8: Verify the next nightly actually worked**

The epic is not proven until a real nightly runs. The morning after landing:

```bash
gh run list --workflow=macrobench.yaml --limit 3
```

Then open the run log and confirm: the `Generate bench baseline profile` step succeeded, `Baseline profile OK for benchBenchmarkRelease: <thousands> app rules` appears, the job finished inside 90 minutes, and both gh-pages charts received a point.

If the JIT metric reports zero in **both** COLD cells on the emulator, the metric is emulator-blind — file a follow-up to document that limitation rather than leaving a check that always reports success.
