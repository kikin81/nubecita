# `build-logic/` — Gradle convention plugins

Composite build pulled into the root via `pluginManagement { includeBuild("build-logic") }` in `settings.gradle.kts`. Hosts five class-based `Plugin<Project>` implementations that centralize the SDK versions, JVM toolchain, Compose wiring, and Hilt wiring shared across every Android module in the repo.

## The five plugins

| Plugin ID | What it applies / configures | Used by |
|-----------|------------------------------|---------|
| `nubecita.android.library` | `com.android.library`, `sort-dependencies`, `compileSdk = 37`, `minSdk = 26`, Java 17 source/target, JVM 17 toolchain, `consumerProguardFiles("consumer-rules.pro")`, default test instrumentation runner, release build type with standard ProGuard files | `:core:auth` |
| `nubecita.android.library.compose` | Above + `org.jetbrains.kotlin.plugin.compose`, `buildFeatures.compose = true`, Compose BOM + base Compose deps (`ui`, `material3`, `ui-tooling-preview`, `ui-tooling` debug-only) | `:designsystem` |
| `nubecita.android.hilt` | `com.google.dagger.hilt.android`, `com.google.devtools.ksp`, `hilt-android` runtime + `hilt-android-compiler` via KSP | `:core:auth` |
| `nubecita.android.feature` | Meta: `library` + `library.compose` + `hilt`, plus `:designsystem` project dep + `lifecycle-viewmodel-compose` + `lifecycle-runtime-compose` + `hilt-navigation-compose` + `navigation3-runtime` + `navigation3-ui` + `kotlinx-collections-immutable` | `:feature:*:impl` modules (none yet — created by the upcoming `add-feature-login` change) |
| `nubecita.android.application` | `com.android.application`, `sort-dependencies`, Compose + Hilt + KSP plugins, `compileSdk/minSdk/targetSdk`, JVM 17 toolchain, `buildFeatures.buildConfig = true; compose = true`, default release build type, base Compose + Hilt + `hilt-navigation-compose` deps | `:app` |

## Adding a new module

Modules apply one or more conventions via `alias(libs.plugins.nubecita.android.*)` and declare only what's genuinely module-specific (namespace, module-specific deps, optional build-type tweaks).

A typical library module:

```kotlin
plugins {
    alias(libs.plugins.nubecita.android.library)
}

android {
    namespace = "net.kikin.nubecita.core.something"
}

dependencies {
    // module-specific deps only
}
```

A typical Compose-using library module:

```kotlin
plugins {
    alias(libs.plugins.nubecita.android.library.compose)
}

android {
    namespace = "net.kikin.nubecita.something"
}
```

A future feature module's `:impl` half:

```kotlin
plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.something.impl"
}

dependencies {
    api(project(":feature:something:api"))
    // additional module-specific deps
}
```

## Layout

```
build-logic/
├── README.md                                  ← this file
├── settings.gradle.kts                        ← composite root, shares ../gradle/libs.versions.toml
└── convention/
    ├── build.gradle.kts                       ← registers the five plugin IDs
    └── src/main/kotlin/net/kikin/nubecita/buildlogic/
        ├── ProjectExtensions.kt               ← Project.libs helper
        ├── KotlinAndroid.kt                   ← configureKotlinAndroid(commonExtension)
        ├── AndroidLibraryConventionPlugin.kt
        ├── AndroidLibraryComposeConventionPlugin.kt
        ├── AndroidHiltConventionPlugin.kt
        ├── AndroidFeatureConventionPlugin.kt
        └── AndroidApplicationConventionPlugin.kt
```

## How versions are resolved

Convention plugins look up library versions through the shared `libs` catalog:

```kotlin
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    "implementation"(libs.findLibrary("hilt-android").get())
}
```

There is no version hard-coded in the plugin sources beyond the platform-version constants (`compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`, `JavaVersion.VERSION_17`). Bumping a library version in `gradle/libs.versions.toml` propagates through every module without touching `build-logic/`.

## Why composite, not `buildSrc/`

`buildSrc` changes invalidate the entire main-build cache. `includeBuild("build-logic")` keeps cache hygiene scoped to the modules that actually apply the changed plugin. Same pattern Now in Android uses (`android/nowinandroid/build-logic/`).
