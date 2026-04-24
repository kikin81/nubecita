# build-logic-conventions Specification

## Purpose
TBD - created by archiving change add-build-logic-conventions. Update Purpose after archive.
## Requirements
### Requirement: `build-logic/` composite build hosts all convention plugins

The repository SHALL contain a `build-logic/` directory as a sibling of `app/`. `build-logic/` SHALL be a standalone Gradle build with its own `settings.gradle.kts` and a single included project named `:convention` at `build-logic/convention/`. The root build's `settings.gradle.kts` SHALL include `pluginManagement { includeBuild("build-logic") }` so the convention plugins are resolvable from any module's `plugins { }` block.

#### Scenario: Plugin resolution from a module

- **WHEN** any module's `build.gradle.kts` declares `alias(libs.plugins.nubecita.android.library)` (or any other `nubecita.android.*` alias)
- **THEN** Gradle SHALL resolve the plugin from the `build-logic/convention/` composite build without requiring any Maven or Gradle Plugin Portal lookup

#### Scenario: `build-logic` is independent of the main build cache

- **WHEN** a file in `build-logic/convention/src/main/kotlin/` is edited
- **THEN** only modules that apply the edited plugin (or plugins that depend on it) SHALL reconfigure; unrelated modules SHALL retain their configuration-cache entries where applicable

### Requirement: Five convention plugins are registered with canonical IDs

`build-logic/convention/build.gradle.kts` SHALL register exactly the following five plugin IDs, each pointing at a `Plugin<Project>` implementation class under `net.kikin.nubecita.buildlogic`:

| Plugin ID | Implementation class |
|---|---|
| `nubecita.android.library` | `AndroidLibraryConventionPlugin` |
| `nubecita.android.library.compose` | `AndroidLibraryComposeConventionPlugin` |
| `nubecita.android.hilt` | `AndroidHiltConventionPlugin` |
| `nubecita.android.feature` | `AndroidFeatureConventionPlugin` |
| `nubecita.android.application` | `AndroidApplicationConventionPlugin` |

Each class SHALL be a Kotlin class implementing `org.gradle.api.Plugin<org.gradle.api.Project>`. Pre-compiled script plugins (`.gradle.kts` files) SHALL NOT be used.

#### Scenario: Plugin registration inspection

- **WHEN** `./gradlew build-logic:convention:tasks --all` is run
- **THEN** each of the five plugin IDs SHALL appear in the output

#### Scenario: No pre-compiled script plugins

- **WHEN** `find build-logic/convention/src/main/kotlin -name '*.gradle.kts'` is run
- **THEN** it SHALL return no results

### Requirement: `nubecita.android.library` applies baseline Android library config

The `nubecita.android.library` convention plugin SHALL apply `com.android.library` and `com.squareup.sort-dependencies`, configure `compileSdk = 37`, `minSdk = 26`, Java 17 source/target compatibility, JVM 17 toolchain, `consumerProguardFiles("consumer-rules.pro")`, and a release build type with standard ProGuard files and `isMinifyEnabled = false`. It SHALL NOT set the module `namespace` — that remains the consumer's responsibility.

#### Scenario: Module applies library plugin and sets only namespace

- **WHEN** a library module's `build.gradle.kts` applies `nubecita.android.library` and sets only `android { namespace = "net.kikin.nubecita.some.module" }`
- **THEN** `./gradlew <module>:assembleDebug` SHALL succeed, producing an AAR with `compileSdk=37`, `minSdk=26`, and JVM-17-compiled bytecode

#### Scenario: Plugin does not override namespace

- **WHEN** the plugin is applied to a module that sets `android { namespace = "..." }` in its own build file
- **THEN** the plugin SHALL NOT override or clobber the namespace

### Requirement: `nubecita.android.library.compose` adds Compose wiring on top of library

The `nubecita.android.library.compose` convention plugin SHALL apply everything `nubecita.android.library` applies, plus: `org.jetbrains.kotlin.plugin.compose`, `buildFeatures { compose = true }`, and the base Compose dependency set (`platform(androidx.compose.bom)`, `androidx.compose.ui`, `androidx.compose.material3`, `androidx.compose.ui.tooling.preview`, and `androidx.compose.ui.tooling` as `debugImplementation`).

#### Scenario: Compose-using module gets full Compose wiring

- **WHEN** `:designsystem`'s `build.gradle.kts` applies only `nubecita.android.library.compose` and sets `namespace`
- **THEN** Compose code compiles; `LocalDensity.current`, `Modifier`, `Text`, `MaterialTheme` all resolve; `@Preview` annotations work with the tooling dep present in `debug`

### Requirement: `nubecita.android.hilt` adds Hilt + KSP without requiring Compose

The `nubecita.android.hilt` convention plugin SHALL apply `com.google.dagger.hilt.android` and `com.google.devtools.ksp`, declare `hilt-android` as `implementation`, and declare `hilt-android-compiler` as `ksp`. It SHALL NOT apply any Compose plugin or add any Compose dep — modules that want both apply both `library.compose` and `hilt`.

#### Scenario: Non-Compose, Hilt-using module

- **WHEN** `:core:auth`'s `build.gradle.kts` applies `nubecita.android.library` + `nubecita.android.hilt` (not `library.compose`)
- **THEN** `@HiltAndroidApp`, `@HiltViewModel`, `@Inject`, `@Module`, and `@InstallIn` resolve; KSP generates the Hilt component; no Compose dep appears in the compile classpath

### Requirement: `nubecita.android.feature` composes library + compose + hilt plus feature-standard deps

The `nubecita.android.feature` convention plugin SHALL be a meta-plugin applying all of `nubecita.android.library`, `nubecita.android.library.compose`, and `nubecita.android.hilt`, and SHALL additionally declare `implementation(project(":core:common"))`, `implementation(project(":designsystem"))`, plus the common feature dep set: `androidx.lifecycle.viewmodel.compose`, `androidx.lifecycle.runtime.compose`, `androidx.hilt.navigation.compose`, `androidx.navigation3.runtime`, `androidx.navigation3.ui`, `kotlinx-collections-immutable`.

#### Scenario: Feature impl module applies only `feature` plugin

- **WHEN** a `:feature:*:impl` module's `build.gradle.kts` applies only `nubecita.android.feature` and sets its namespace + any api module dep
- **THEN** it SHALL compile a feature module that references `:core:common`, `:designsystem`, lifecycle-viewmodel, hilt-navigation-compose, and navigation3 without re-declaring those deps

### Requirement: `nubecita.android.application` applies application plugin with Compose + Hilt + BuildConfig

The `nubecita.android.application` convention plugin SHALL apply `com.android.application` + `com.squareup.sort-dependencies`, configure `compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`, Java 17 source/target compatibility, JVM 17 toolchain, `buildFeatures { buildConfig = true; compose = true }`, and a release build type with standard ProGuard files and `isMinifyEnabled = false`. It SHALL also apply the Hilt + KSP + Compose Kotlin plugins and declare the base Compose + Hilt + hilt-navigation-compose dependency set. It SHALL NOT set `applicationId`, `versionCode`, `versionName`, or `namespace` — those remain `:app`'s responsibility.

#### Scenario: `:app` declares only what's unique to the application

- **WHEN** `:app/build.gradle.kts` applies `nubecita.android.application` and adds on top: namespace + applicationId + versionCode/versionName + Jacoco config + lint baseline + module-specific deps (atproto, designsystem, core:auth, feature:login:impl, navigation3, ktor)
- **THEN** `:app` builds, `./gradlew :app:assembleDebug` succeeds, and the resulting APK's `AndroidManifest.xml` has `package = "net.kikin.nubecita"` and `versionName = "1.0"` matching the pre-change build

#### Scenario: `buildConfig = true` is the default

- **WHEN** `:app/build.gradle.kts` does not explicitly set `buildFeatures.buildConfig`
- **THEN** `BuildConfig.kt` SHALL be generated at build time

### Requirement: Plugin aliases are registered in the version catalog with `version = "unspecified"`

`gradle/libs.versions.toml` under `[plugins]` SHALL register aliases for each of the five convention plugins:

```toml
nubecita-android-library = { id = "nubecita.android.library", version = "unspecified" }
nubecita-android-library-compose = { id = "nubecita.android.library.compose", version = "unspecified" }
nubecita-android-hilt = { id = "nubecita.android.hilt", version = "unspecified" }
nubecita-android-feature = { id = "nubecita.android.feature", version = "unspecified" }
nubecita-android-application = { id = "nubecita.android.application", version = "unspecified" }
```

Consumers SHALL reference them as `alias(libs.plugins.nubecita.android.*)` in their `plugins { }` block.

#### Scenario: Module uses catalog alias instead of plugin ID string

- **WHEN** a module's `build.gradle.kts` declares `alias(libs.plugins.nubecita.android.library)`
- **THEN** Gradle SHALL resolve the plugin identically to the plain-string form `id("nubecita.android.library")`

### Requirement: Convention plugins read library versions from the shared version catalog

When a convention plugin adds a dependency (e.g. `implementation` of Compose BOM, Hilt runtime, navigation3, etc.), it SHALL look up the version via the root project's `libs` version catalog using `project.extensions.getByType<VersionCatalogsExtension>().named("libs")` (or an equivalent `Project.libs` helper). Versions SHALL NOT be hard-coded inside the convention plugin source.

#### Scenario: Catalog bump propagates without plugin edits

- **WHEN** `gradle/libs.versions.toml` is edited to bump the `kotlin` version (or any versioned alias used by a convention plugin)
- **THEN** `./gradlew <module>:assembleDebug` picks up the new version without any edit to `build-logic/convention/src/main/kotlin/`

#### Scenario: No hard-coded versions in plugin sources

- **WHEN** `grep -E '[0-9]+\.[0-9]+' build-logic/convention/src/main/kotlin/` is run looking for version literals
- **THEN** no matches SHALL be found (except `JavaVersion.VERSION_17` / `jvmToolchain(17)` / `compileSdk = 37` / `minSdk = 26` / `targetSdk = 37` — these are platform version references, not library versions, and may stay literal inside the plugins)

### Requirement: `:app`, `:designsystem`, `:core:auth` migrate to convention plugins in this change

Each of the three existing modules' `build.gradle.kts` files SHALL be rewritten to apply only the relevant convention plugins and declare only module-specific content (namespace, module-specific deps, per-module build-type tweaks where they exist). `./gradlew :app:assembleDebug :designsystem:assembleDebug :core:auth:assembleDebug` SHALL produce functionally equivalent output to the pre-change build — same APK `versionName`, same dependencies on the compile classpath, same manifest attributes, same KSP-generated code.

#### Scenario: A/B build diff

- **WHEN** `./gradlew :app:assembleDebug` is run on the pre-change commit and on the post-change commit
- **THEN** the resulting APKs SHALL contain the same set of classes (excluding build timestamps and the empty `BuildConfig` class which now appears because of design decision §7)

#### Scenario: Module build-file size

- **WHEN** the post-change `:designsystem/build.gradle.kts`, `:core:auth/build.gradle.kts`, and `:app/build.gradle.kts` are inspected
- **THEN** `:designsystem` SHALL be ≤ 25 lines, `:core:auth` SHALL be ≤ 30 lines, and `:app` SHALL be ≤ 60 lines (excluding comment/license headers, blank lines, and closing braces at column 0)

### Requirement: Test suite and lint pass unchanged

Running `./gradlew testDebugUnitTest :core:auth:lint :designsystem:lint :app:lint` after the migration SHALL produce the same pass/fail results as before the migration — identical test counts, identical lint baseline alignment, no new warnings.

#### Scenario: Unit tests count matches pre-migration

- **WHEN** the full `./gradlew testDebugUnitTest` runs after migration
- **THEN** every test that passed before SHALL pass; the total test count SHALL match within +/-0 (no tests added or removed by this change)

#### Scenario: Lint baselines unchanged

- **WHEN** `./gradlew lint` runs
- **THEN** no new lint-baseline entries SHALL be added; existing baselines SHALL remain valid and not rewritten
