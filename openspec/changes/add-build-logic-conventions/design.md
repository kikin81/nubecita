## Context

Three Gradle modules, each with 25-130 lines of `build.gradle.kts` that repeat the same Android block. Seven modules are imminent. Google solved this in Now in Android with composite-build convention plugins (reference: `android/nowinandroid/build-logic/convention/`, migrated from an earlier `buildSrc/` approach).

The project is already Kotlin-DSL throughout, uses a version catalog (`gradle/libs.versions.toml`), and has Spotless applied from the root build script. Convention plugins slot in naturally: they become the *third* centralization mechanism alongside the version catalog (for dep versions) and Spotless (for formatting).

Constraints and invariants:
- **No behavior change.** Every existing module must rebuild to byte-identical output (modulo timestamps) before and after this change. Verified by `./gradlew :app:assembleDebug :designsystem:assembleDebug :core:auth:assembleDebug` on both sides and by running the full test suite.
- **Version catalog remains the source of truth for library versions.** Convention plugins look up versions via the catalog, never hard-code them.
- **Plugin IDs use dot-separated reverse-domain-ish form** — `nubecita.android.library`, not `NubecitaAndroidLibrary`. Matches the AGP / Gradle native plugin naming convention.

Downstream: the paused `add-feature-login` openspec change resumes on top of this foundation. `:core:common`, `:feature:login:api`, `:feature:login:impl` scaffold against the new plugins and each lands in ~5-10 line build files.

## Goals / Non-Goals

**Goals:**

- Extract every piece of boilerplate repeated across two or more module build scripts into a convention plugin.
- Keep module build files to ~5-20 lines containing only the genuinely module-specific pieces: namespace, module-specific deps, optional build-type tweaks.
- Establish the five plugin IDs (`library`, `library.compose`, `hilt`, `feature`, `application`) as the canonical extension points. New modules pick one or two and declare their namespace.
- Produce a byte-identical build before and after — this change introduces no new compiled output.

**Non-Goals:**

- **Eliminating all duplication.** Some module-local config genuinely belongs per-module: the namespace, the module-specific dep list, Jacoco wiring on `:app`, the lint baseline on `:app`, the atproto dep set on `:core:auth`. Don't force these into plugins.
- **Supporting non-Android (pure-JVM) modules.** We have none; we might add a `nubecita.jvm.library` when the need arises. Not today.
- **Plugin versioning / publishing.** The plugins are project-internal (`version = "unspecified"` in the catalog). They are never published to a Maven repository.
- **Interactive / abstract build config.** No `extensions.create(...)` DSLs exposing typed config options — plugins read the version catalog directly. Reconsider only if per-module options multiply.

## Decisions

### 1. Composite build, not `buildSrc/`

**Decision:** Create `build-logic/` as a sibling directory with its own `settings.gradle.kts`, pulled into the root build via `pluginManagement { includeBuild("build-logic") }`.

**Alternatives considered:**
- **`buildSrc/` with precompiled script plugins.** Works. Downside: `buildSrc` changes invalidate the entire build cache; every edit to a convention plugin forces a full reconfiguration of all modules. For a multi-person project with frequent plugin touches, that overhead compounds.
- **`buildSrc/` with class-based plugins.** Same cache-invalidation problem regardless of plugin style.

**Rationale:** Matches Now in Android's current layout. The cache-hygiene benefit is tangible — convention plugin edits only reconfigure modules that actually apply the changed plugin.

### 2. Class-based `Plugin<Project>` implementations, not pre-compiled script plugins

**Decision:** Each convention plugin is a Kotlin class implementing `Plugin<Project>`, e.g.

```kotlin
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("com.squareup.sort-dependencies")
            extensions.configure<LibraryExtension> { configureKotlinAndroid(this) }
        }
    }
}
```

registered in `build-logic/convention/build.gradle.kts`:

```kotlin
gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "nubecita.android.library"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidLibraryConventionPlugin"
        }
        // ... one register block per plugin
    }
}
```

**Alternatives considered:**
- **Pre-compiled script plugins** (`build-logic/convention/src/main/kotlin/nubecita.android.library.gradle.kts`). More terse. Downside: refactoring, IDE navigation, and shared helpers are worse — script-form plugins can't easily share top-level Kotlin utilities, which forces duplication (ironic given our goal).

**Rationale:** Class-based plugins are what NiA ships and what the Gradle docs recommend for non-trivial conventions. IDE refactoring works fully; shared helpers (e.g. `configureKotlinAndroid(CommonExtension)`) can be top-level functions.

### 3. Version-catalog lookup inside plugins via `versionCatalogs.named("libs")`

**Decision:** Convention plugins needing library refs use the built-in extension:

```kotlin
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies.add("implementation", libs.findLibrary("androidx-core-ktx").get())
```

A single helper `Project.libs: VersionCatalog` encapsulates this so plugin code reads naturally.

**Alternatives considered:**
- **Hard-code versions inside plugins.** Defeats the whole point of the catalog.
- **Pass the catalog explicitly through plugin options.** Gradle has no clean DSL for this; the extension lookup is the canonical form.

**Rationale:** The `VersionCatalogsExtension` is a stable public API (available since Gradle 7.4) and is exactly how NiA does it.

### 4. Five plugins, not one or three

**Decision:** Plugin boundaries at `library` / `library.compose` / `hilt` / `feature` / `application`. Rationale for each split:

- **`library` is the base** — every Android library module applies it. Separate because non-Compose, non-Hilt libraries exist (`:core:auth` has Hilt but no Compose; `:core:common`, when it lands, has neither).
- **`library.compose` is orthogonal to Hilt** — `:designsystem` has Compose but not Hilt. Keeps the axes independent.
- **`hilt` is orthogonal to Compose** — `:core:auth` uses Hilt without Compose. Same axis argument.
- **`feature` is a deliberate meta-plugin** — feature modules *always* want library + compose + hilt + common deps (designsystem, core:common, lifecycle-viewmodel-compose, navigation3). One line instead of four.
- **`application` is a standalone plugin, not `library` + overrides** — the Android application plugin conflicts with the library plugin (they are mutually exclusive AGP plugins). Separate from the start.

**Alternatives considered:**
- **One `nubecita.android` plugin that configures itself based on what other plugins are applied.** Cleverer but more brittle — the apply order of Gradle plugins matters, and inferring "this module wants Compose" from the presence of `kotlin.plugin.compose` is fragile.
- **Three plugins (`library`, `feature`, `application`), dropping the sub-axes.** Simpler surface but forces the Compose-without-Hilt case (`:designsystem`) to either drag Hilt in or stay on manual config. Wrong trade.

**Rationale:** Five is the minimum that covers all four existing axes (`app` / `designsystem` / `core:auth` / `core:common`) and the four axes in the immediate future (`feature:*:api` / `feature:*:impl`). Simpler than seven, more expressive than three.

### 5. `nubecita.android.feature` depends on `:core:common` and `:designsystem` unconditionally

**Decision:** Every `:feature:*:impl` module that applies `nubecita.android.feature` automatically gets `implementation(project(":core:common"))` and `implementation(project(":designsystem"))` added, along with the common feature dep set (lifecycle-viewmodel-compose, hilt-navigation-compose, navigation3-runtime, navigation3-ui, kotlinx-collections-immutable).

**Alternatives considered:**
- **Leave project deps explicit in each feature module.** More explicit but contradicts the point of a meta-plugin. Every feature will want both modules; defaults should match the universal case.

**Rationale:** The opt-out cost (a feature module that genuinely doesn't want `:designsystem`) is zero — `implementation` deps don't force usage, and the unused-dep lint is trivial to suppress per-module. The opt-in cost (every new feature remembers to declare four project deps + four dep aliases) is perpetual overhead. Default to the common case.

### 6. `nubecita.android.application` also applies Compose and Hilt unconditionally

**Decision:** `AndroidApplicationConventionPlugin` applies `com.google.dagger.hilt.android`, KSP, `org.jetbrains.kotlin.plugin.compose`, and wires up Compose BOM + base deps + Hilt deps inside the same plugin.

**Alternatives considered:**
- **Make `:app` apply `application` + `library.compose` + `hilt` separately.** More composable in theory. In practice, `:app` is singular — there is only one application module in the project, and it will always want Compose + Hilt. Splitting saves nothing and adds three lines per application (we have one).

**Rationale:** YAGNI. When there's exactly one consumer, bake in what the consumer needs.

### 7. Flip `buildConfig = true` in the application convention plugin

**Decision:** `nubecita.android.application` sets `buildFeatures.buildConfig = true` by default.

**Alternatives considered:**
- **Leave it `false` and let `:app` override.** Currently `:app`'s `build.gradle.kts` sets `buildConfig = false`. But the paused `add-feature-login` change needs a BuildConfig field (`OAUTH_CLIENT_METADATA_URL`), which would require flipping this the moment that change resumes.

**Rationale:** The empty `BuildConfig` class is ~50 bytes in the APK. Flipping once in the convention avoids a second round of edits. If an application module ever genuinely wants to skip BuildConfig generation, it can override — but there's no real-world Android app that doesn't use BuildConfig.

### 8. Don't migrate Spotless into a convention plugin

**Decision:** Leave Spotless where it is — in the root `build.gradle.kts` under `allprojects { apply plugin "com.diffplug.spotless"; extensions.configure<SpotlessExtension> { ... } }`. The library convention plugin does not touch Spotless.

**Alternatives considered:**
- **Apply Spotless inside `nubecita.android.library` and `nubecita.android.application`.** Cleaner in the sense that each plugin is self-contained. Downside: Spotless covers every module (including the `build-logic` composite itself), and `allprojects { }` does that in one place. Splitting it per-plugin introduces opportunities for drift between library and application Spotless config.

**Rationale:** The root `allprojects` block is already the right abstraction for "every Gradle project in this build." Moving it would be motion without direction.

### 9. Plugin IDs registered with `version = "unspecified"` in the version catalog

**Decision:** Plugin aliases in `libs.versions.toml` use:

```toml
nubecita-android-library = { id = "nubecita.android.library", version = "unspecified" }
```

**Alternatives considered:**
- **Omit from the catalog and have modules write `id("nubecita.android.library")` directly.** Works. Downside: modules lose the catalog indirection; a rename of a convention plugin becomes a multi-file edit instead of a single catalog change. Small difference but consistent with how every other plugin in the catalog is referenced.

**Rationale:** `version = "unspecified"` is the canonical catalog form for locally-provided plugins (documented in Gradle's version-catalog guide). Modules stay on the uniform `alias(libs.plugins.*)` surface.

### 10. Convention plugins are free to read / mutate / re-apply — module scripts can override

**Decision:** Plugins set defaults (`compileSdk`, `minSdk`, Java 17, toolchain, etc.). Module scripts can re-enter the `android { }` block to override — standard Gradle extension mechanics.

**Alternatives considered:**
- **Typed config extensions** (`nubecita { library { minSdk = 28 } }`). Overengineering for a three-person project with one SDK baseline. Revisit if we have fragmented minSdk requirements.

**Rationale:** Modules that genuinely need different values (none today) can override via standard Gradle mechanics. No DSL needed.

## Risks / Trade-offs

- **Risk: a convention plugin silently stops applying the expected config** (e.g., a typo in the plugin id, a classpath issue in `build-logic/`). → Mitigation: every migrated module's build file ends with the expected behavior verified by `./gradlew <module>:dependencies` listing the expected aliases and by `:app:assembleDebug` + tests still passing byte-for-byte.
- **Risk: included-build circular dependency** — `build-logic` tries to use the version catalog from the root, but the root's catalog file lives inside the main build. → Mitigation: the pattern is well-trodden in NiA and many other repos; `build-logic/settings.gradle.kts` declares `dependencyResolutionManagement { versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } } }` to share the catalog.
- **Risk: plugin apply order surprises.** AGP is picky about when `android { }` is configured vs when certain Kotlin / Compose plugins apply. → Mitigation: follow NiA's plugin ordering exactly: apply Android plugin first, then configure via `extensions.configure<...Extension>`, then apply Kotlin/Compose/Hilt add-ons.
- **Risk: Gradle version skew between main build and `build-logic`.** → Mitigation: `build-logic` uses the same JVM and Kotlin via its own `settings.gradle.kts` + `build.gradle.kts`. Explicit versions (not version-ranges) in `build-logic/convention/build.gradle.kts`.
- **Risk: regression in release-build config or release-build ProGuard.** `:app`'s release build type currently is `isMinifyEnabled = false` with standard proguard files — the `nubecita.android.application` plugin must preserve this. → Mitigation: plugin sets the same release block; assemble release via `./gradlew :app:assembleRelease` and diff the APK against the pre-change build to confirm.
- **Trade-off: cost of a new developer learning the layout.** A reader unfamiliar with `build-logic` composite builds has to find the plugin class to understand what `alias(libs.plugins.nubecita.android.library)` does. → Mitigation: `build-logic/README.md` documents the plugin roster and what each configures.

## Migration Plan

Pure refactor; no runtime migration. Verification is an A/B diff of build outputs.

1. Ship the convention plugins and `settings.gradle.kts` include on a feature branch.
2. Migrate modules one at a time in a single PR (easier review than one-per-PR) with commit-level boundaries: (a) scaffold `build-logic/`, (b) add plugin aliases to catalog, (c) migrate `:designsystem`, (d) migrate `:core:auth`, (e) migrate `:app`.
3. After each migration commit, run `./gradlew :<module>:assembleDebug :<module>:testDebugUnitTest` and confirm green.
4. After all migrations, run the full `./gradlew build` and confirm green.
5. Rollback: if anything breaks, revert the migration commit of the offending module and temporarily leave it on inline config. Plugin code is otherwise independent and doesn't need to roll back.

## Open Questions

- **Do the convention plugins need to apply Spotless themselves, or is the root `allprojects` block sufficient for the `build-logic` composite?** Decided by inspection at implementation time. If `build-logic`'s Kotlin files are not Spotless-formatted by the root config, we extend the root config — not move Spotless into a convention plugin.
- **Does `:app`'s `jacocoTestReport` custom task need to move into a convention plugin?** Not for this change. Left in `:app/build.gradle.kts`. Revisit if/when a second module wants coverage.
- **Should `nubecita.android.library` automatically add the `kotlinx-collections-immutable` dep?** It's used by `:app` and `:feature:*` modules but not by `:core:auth` or `:designsystem`. Decided: no — `nubecita.android.feature` adds it; pure libraries opt in explicitly.
- **Navigation 3 deps in `nubecita.android.feature` — which exact artifacts?** Will be `navigation3-runtime` and `navigation3-ui` at minimum; possibly `lifecycle-viewmodel-navigation3`. Pin exact set during implementation based on what the feature-login follow-up actually imports.
