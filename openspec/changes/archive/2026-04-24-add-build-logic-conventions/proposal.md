## Why

Nubecita has three Gradle modules today (`:app`, `:designsystem`, `:core:auth`) and is about to quadruple that count. Each library module's `build.gradle.kts` duplicates the same Android block: `compileSdk = 37`, `minSdk = 26`, `compileOptions` with Java 17, `kotlin { jvmToolchain(17) }`, `consumerProguardFiles("consumer-rules.pro")`, the `sort-dependencies` plugin, test instrumentation runner, and release build-type wiring. Compose-using modules repeat the Compose BOM + base deps. Hilt-using modules repeat the plugin + KSP + `hilt-android` + `hilt-android-compiler` quartet.

The pain isn't severe at three modules. It is severe at seven (`:core:common`, `:feature:login:api`, `:feature:login:impl` land next, and more feature modules follow). The cost of extraction scales with module count, so the window to do it cheaply is closing.

Google's Now in Android project hit the same inflection point around the same module count and solved it with a **composite `build-logic/` build containing convention plugins** (reference: `android/nowinandroid/build-logic/convention/src/main/kotlin/`). That pattern is the current Google-endorsed answer and supersedes `buildSrc/`. The key advantage of `build-logic` as an `includeBuild` is cache hygiene: changes to build-logic do not invalidate the main build cache the way `buildSrc` changes do.

This change is a **pure refactor** — zero behavior change to any compiled artifact. Every existing module rebuilds identically before and after. The payoff is: (a) feature module scaffolds drop from ~30 lines to ~5, (b) conventions are edited in one place, (c) we stop rediscovering the same minSdk/JVM toolchain/Spotless wiring for every new module.

## What Changes

- **New composite build at `build-logic/`** (as a sibling to `app/`, `core/`, `designsystem/`). Contains one included project at `build-logic/convention/` with a single `build.gradle.kts` and Kotlin sources under `src/main/kotlin/`.
- **Five convention plugins** published by `:build-logic:convention` and applied by each module:
  - `nubecita.android.library` — `com.android.library`, `sort-dependencies`, `compileSdk`, `minSdk`, Java 17 source/target, JVM 17 toolchain, `consumer-rules.pro`, default test instrumentation runner, release build-type with proguard files.
  - `nubecita.android.library.compose` — depends on `library`, adds `org.jetbrains.kotlin.plugin.compose`, `buildFeatures.compose = true`, Compose BOM platform, and the base Compose dependency set (`ui`, `material3`, `ui-tooling-preview`, plus `ui-tooling` under `debugImplementation`).
  - `nubecita.android.hilt` — `com.google.dagger.hilt.android`, `com.google.devtools.ksp`, `hilt-android` as `implementation`, `hilt-android-compiler` as `ksp`.
  - `nubecita.android.feature` — meta-plugin applying `library` + `library.compose` + `hilt`, plus `implementation(project(":core:common"))` and `implementation(project(":designsystem"))` and a standard feature-module dep set (lifecycle-viewmodel-compose, lifecycle-runtime-compose, hilt-navigation-compose, navigation3-runtime, navigation3-ui, kotlinx-collections-immutable).
  - `nubecita.android.application` — `com.android.application`, `sort-dependencies`, `compileSdk`, `minSdk`, `targetSdk`, Java 17, JVM 17 toolchain, `buildFeatures.buildConfig = true`, default test instrumentation runner. Also applies Hilt + Compose — `:app` is the only application module and always has both.
- **Version catalog plugin aliases** so modules use `alias(libs.plugins.nubecita.android.library)` instead of `id("nubecita.android.library")`. Aliases are declared as `version = "unspecified"` — the canonical pattern for catalog entries backed by local, project-internal plugins.
- **`settings.gradle.kts` gains `pluginManagement { includeBuild("build-logic") }`** so the convention plugins resolve at root-project configuration time.
- **Existing three modules migrated** — `:app`, `:designsystem`, `:core:auth` rewrite their `build.gradle.kts` files to apply the new convention plugins and declare only the truly module-specific pieces (namespace, module-specific deps, applicationId for `:app`).

## Capabilities

### New Capabilities

- `build-logic-conventions`: the centralized Gradle configuration surface. Every Android module in the repo applies one or more convention plugins that encapsulate the SDK versions, JVM toolchain, Compose wiring, and Hilt wiring. Consumers of the capability are every Gradle module in the repo; the capability's contract is the set of plugin IDs, what they configure, and what they intentionally leave to the consumer module (namespace, module-specific deps).

### Modified Capabilities

_None._ No existing `openspec/specs/*/spec.md` owns build-script requirements today. The existing `core-auth-session-storage` spec has a requirement about `:app` not declaring direct deps on Tink/DataStore, which is about module dep hygiene rather than build-script conventions — unchanged here.

## Non-Goals

- **Migrating off `buildSrc/`.** We never had a `buildSrc/`. This change starts fresh with `build-logic/` as a composite build.
- **Introducing any new compiler plugin or Gradle plugin** beyond what modules already use today. The goal is *centralize existing wiring*, not expand it.
- **Moving Spotless configuration into a convention plugin.** Spotless is already centralized via the root `allprojects { apply plugin... }` block in `build.gradle.kts`. Works today; moving it would be scope creep.
- **Moving Jacoco into a convention plugin.** Jacoco is only on `:app` currently. When a second module needs coverage reporting we'll revisit.
- **Consolidating lint baseline config.** `:app`'s `lint { baseline = ... }` is bespoke; each module can declare its own if needed. No convention pressure yet.
- **Pre-compiled script plugins (`.gradle.kts` files in `src/main/kotlin`).** We use Kotlin class-based plugins with `@Suppress("unused") class AndroidLibraryConventionPlugin : Plugin<Project>` — NiA's pattern. More explicit and more refactorable than script-form.
- **Version catalog dependency bumps.** Pure refactor; no version changes.
- **Moving `:core:auth`'s special needs** (datastore-tink, Tink) into a "crypto" convention plugin. Only one module uses crypto today; premature abstraction.

## Deviations from Baseline

- **New top-level directory.** `build-logic/` joins `app/`, `core/`, `designsystem/`, `config/`, `docs/`, `gradle/`, `openspec/`, `scripts/`. It is not a Gradle subproject of the root — it is a separate composite build pulled in via `includeBuild`. Differences from a normal subproject: its plugins are available at root-project configuration time; it has its own `settings.gradle.kts`; its compilation does not participate in the root build cache.
- **`buildConfig = true` applied unconditionally in the application convention plugin.** Currently `:app` has `buildConfig = false`. This change flips it to `true` because the upcoming `feature-login` work (per the drafted but paused `add-feature-login` change) needs `BuildConfig.OAUTH_CLIENT_METADATA_URL`. Flipping it now in the application convention avoids a second round of `:app/build.gradle.kts` edits in the next PR. Cost: ~0 KB in APK size (empty BuildConfig class).

## Impact

- **New directory tree:** `build-logic/`, `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`, `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/` with one Kotlin file per convention plugin plus small shared helpers (e.g. `configureKotlinAndroid(...)` extension on `Project`).
- **`settings.gradle.kts`:** gains `pluginManagement { includeBuild("build-logic") }` at the top of `pluginManagement { }`.
- **`gradle/libs.versions.toml`:** gains five plugin aliases under `[plugins]` — `nubecita-android-library`, `nubecita-android-library-compose`, `nubecita-android-hilt`, `nubecita-android-feature`, `nubecita-android-application`.
- **`:app/build.gradle.kts`:** rewritten, down to ~40 lines from ~130. Keeps the Compose Screenshot plugin, Jacoco, the publish no-op task, the lint config, and the `buildConfigField` (empty now — will be populated under the future feature-login PR).
- **`:designsystem/build.gradle.kts`:** rewritten, ~15 lines. Applies `library` + `library.compose`; declares namespace + Compose text/google-fonts dep.
- **`:core:auth/build.gradle.kts`:** rewritten, ~20 lines. Applies `library` + `hilt`; declares namespace + atproto-oauth (`api`), datastore + tink + kotlinx-serialization (`implementation`) + test deps.
- **Version-control side effects:** composite build introduces a second `build-logic/.gradle/` + `build-logic/build/` during builds. Should be added to `.gitignore` (likely already matches via `**/build/` and `**/.gradle/` globs — verify during implementation).
- **CI:** no changes expected. `./gradlew build` from the root continues to work identically; the composite build is invisible to CI.
- **Downstream unblocks:** the paused `add-feature-login` change resumes on top of this. `:core:common`, `:feature:login:api`, and `:feature:login:impl` scaffold against the new convention plugins from day one.
