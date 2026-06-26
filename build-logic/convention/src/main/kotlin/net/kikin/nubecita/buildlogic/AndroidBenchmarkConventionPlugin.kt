package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for `:benchmark` — the AndroidX Macrobenchmark module
 * that exercises the real `:app` APK and reports startup + frame-timing
 * metrics. Applies:
 *
 * - `com.android.test` — Android test-only module shape (no production
 *   APK output; the module ships an APK that runs against a separately-
 *   installed target). Under AGP 9 this brings Kotlin support inline, so
 *   `org.jetbrains.kotlin.android` is intentionally NOT applied.
 * - `androidx.baselineprofile` — registers `:benchmark` as a baseline-
 *   profile *producer*. `:app` applies the same plugin as a consumer and
 *   wires `baselineProfile(project(":benchmark"))` to pull generated
 *   profiles. The plugin auto-generates `benchmarkRelease` and
 *   `nonMinifiedRelease` variants off `:app`'s `release` build type, so
 *   this convention does NOT declare its own build types — doing so
 *   collides with the plugin's naming. The actual
 *   `BaselineProfileGenerator` test that produces a profile is a
 *   follow-up ticket (`nubecita-crmi.2`); landing the producer wiring
 *   here keeps that change scoped to "add the generator test" rather
 *   than "add the generator AND rewire build-logic".
 * - `com.squareup.sort-dependencies` — consistency with every other
 *   Android module in the repo.
 *
 * Configures compileSdk = 37 / minSdk = 28 (matching the rest of the
 * Android conventions), Java 17 source/target, JVM 17 toolchain,
 * `targetProjectPath = ":app"`, and the
 * `android.experimental.self-instrumenting` flag Macrobenchmark requires
 * under AGP 9. Pulls in `benchmark-macro-junit4` and
 * `androidx.test.uiautomator` as `implementation` deps so test sources
 * under `src/main/` can `@RunWith(AndroidJUnit4)` directly (the
 * `com.android.test` module type's main sources ARE the test sources —
 * there's no separate `androidTest` source set).
 */
class AndroidBenchmarkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 9 ships built-in Kotlin support for `com.android.*` modules,
            // so `org.jetbrains.kotlin.android` is intentionally NOT applied
            // here — applying it raises a "plugin no longer required" error.
            pluginManager.apply("com.android.test")
            pluginManager.apply("androidx.baselineprofile")
            pluginManager.apply("com.squareup.sort-dependencies")

            // Which `:app` `environment` flavor `:benchmark` targets. Default
            // `bench` (deterministic, offline, fake repos + fake SignedIn) — used
            // by CI Macrobench/StartupBenchmark MEASUREMENT and baseline-profile
            // VALIDATION, where stable numbers matter. Override with
            // `-PbaselineProfileEnvironment=production` for the real-device,
            // signed-in baseline-profile GENERATION run (weekly cadence): only
            // `production` exercises the real Ktor / atproto / Tink-session-decrypt
            // / kotlinx.serialization / billing cold-start path the bench flavor
            // fakes away, so the shipped profile covers what users actually run.
            // See benchmark/README.md ("Generate the baseline profile").
            val baselineProfileEnvironment =
                providers.gradleProperty("baselineProfileEnvironment").orNull ?: "bench"

            extensions.configure<TestExtension> {
                // Shared helper sets compileSdk = 37, minSdk = 28,
                // Java 17 source/target, and the JVM 17 Kotlin
                // toolchain — same wiring every other Android module
                // in the repo gets. The fact that AGP 9 provides
                // built-in Kotlin support for `com.android.test`
                // doesn't remove the `KotlinAndroidProjectExtension`;
                // it just removes the need to apply the standalone
                // `org.jetbrains.kotlin.android` plugin.
                configureKotlinAndroid(this)

                defaultConfig {
                    // Macrobenchmark requires API 23+ at runtime;
                    // matching :app's compileSdk for `targetSdk` keeps
                    // the variant resolver happy when targeting
                    // :app:benchmarkRelease.
                    targetSdk = 37
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

                    // Suppress the standard Macrobench refusal classes so
                    // the bench will run on dev-laptop AVDs + GitHub-hosted
                    // emulator runners (a hard requirement once the CI
                    // workflow under crmi.6 Section C lands; useful today
                    // for local-emulator smoke). Suppressed:
                    //   EMULATOR     — running on an emulator
                    //   LOW-BATTERY  — battery below 25%
                    //   DEBUGGABLE   — debuggable app under test
                    // Other suppressible classes (METHOD-TRACING,
                    // NOT-PROFILEABLE, CODE-COVERAGE, UNLOCKED,
                    // UNSUSTAINED-ACTIVITY-MISSING, ACTIVITY-MISSING)
                    // are NOT suppressed — those would genuinely
                    // invalidate the numbers and want investigation.
                    //
                    // Trade-off: numbers measured under suppressed
                    // conditions aren't perf-stable enough for hard
                    // PR-blocking thresholds. Section C will configure
                    // the regression-tracker for relative trend
                    // tracking, not absolute gating, consistent with
                    // crmi.6's intended use of Macrobench.
                    //
                    // Lives here (not the bench module's manifest)
                    // because the manifest-meta-data form only applies
                    // when declared on the *target app*'s manifest. The
                    // runner-arg form is the canonical recipe in the
                    // androidx.benchmark refusal error message itself.
                    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
                        "EMULATOR,LOW-BATTERY,DEBUGGABLE"

                    // :app gained an `environment` flavor dimension
                    // (`production` / `bench`) under nubecita-crmi.6 — see
                    // `:app/build.gradle.kts` for the flavor decl and
                    // `:feature:feed:impl` / `:core:auth` / `:core:preferences`
                    // for the per-module fake-network source-set splits
                    // landed across PRs #330 + #332.
                    //
                    // `:benchmark` now targets the `bench` variant so the
                    // Macrobench journey hits FakeFeedRepository, the
                    // FakeSessionStateProvider's hardcoded SignedIn, the
                    // bench-source-set AppInitializer (empty — no FCM /
                    // notifications-polling / muted-actor refresh chatter
                    // during cold start), and the bundled `clip-1/2/3.mp4`
                    // assets — deterministic across runs.
                    //
                    // The 2026-05-27 scope refresh on crmi.6 lists 14 repos
                    // total; today only Auth, Preferences, and Feed are
                    // faked. Other journeys (PostDetail, Profile, Search,
                    // Notifications) will hit FakeXrpcClientProvider's
                    // NoSessionException throw and render InitialError if
                    // a future bench journey traverses them — those need
                    // per-feature bench fakes (tracked under crmi.6's
                    // remaining sections). The current bench-flavor
                    // FeedScrollBenchmark + StartupBenchmark exercise only
                    // the bench-quiet path (Splash → MainShell → Feed).
                    // Default `bench`; `-PbaselineProfileEnvironment=production`
                    // selects the real signed-in path for weekly profile gen.
                    missingDimensionStrategy("environment", baselineProfileEnvironment)
                }

                @Suppress("UnstableApiUsage")
                experimentalProperties["android.experimental.self-instrumenting"] = true

                targetProjectPath = ":app"

                // The androidx.baselineprofile plugin auto-generates the
                // matching variants (`benchmark`-qualified plus a
                // `nonMinified`-qualified twin used for profile generation)
                // off :app's build-type matrix. We intentionally don't
                // declare our own build types here — doing so collides
                // with the plugin's naming and produces awkward task
                // names like `assembleBenchmarkBenchmark`.
            }

            dependencies {
                "implementation"(libs.findLibrary("androidx-test-runner").get())
                "implementation"(libs.findLibrary("androidx-test-ext-junit").get())
                "implementation"(libs.findLibrary("androidx-test-uiautomator").get())
                "implementation"(libs.findLibrary("androidx-benchmark-macro-junit4").get())
            }
        }
    }
}
