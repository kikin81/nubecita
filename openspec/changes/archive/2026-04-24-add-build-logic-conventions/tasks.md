## 1. Scaffold `build-logic/` composite

- [ ] 1.1 Create `build-logic/settings.gradle.kts` with `dependencyResolutionManagement { repositories { google(); mavenCentral(); gradlePluginPortal() }; versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } } }` and `rootProject.name = "build-logic"`, `include(":convention")`.
- [ ] 1.2 Create `build-logic/convention/build.gradle.kts` applying `kotlin-dsl` plugin. Dependencies: `compileOnly(libs.android.gradle.plugin)`, `compileOnly(libs.kotlin.gradle.plugin)`, `compileOnly(libs.ksp.gradle.plugin)`, `compileOnly(libs.hilt.gradle.plugin)` (pin exact catalog aliases after verifying they exist in `gradle/libs.versions.toml`; add any missing aliases per §1.3).
- [ ] 1.3 If any of `android-gradle-plugin`, `kotlin-gradle-plugin`, `ksp-gradle-plugin`, `hilt-gradle-plugin` library aliases are missing from `gradle/libs.versions.toml`, add them (libraries only — not plugin aliases — since these are compileOnly classpath deps inside `build-logic`).
- [ ] 1.4 Configure `gradlePlugin { plugins { register("androidLibrary") { id = "nubecita.android.library"; implementationClass = "net.kikin.nubecita.buildlogic.AndroidLibraryConventionPlugin" } ... } }` with one `register` block per plugin (5 total).
- [ ] 1.5 Create `build-logic/.gitignore` if not covered by the root ignore globs — typically `build/` and `.gradle/` are already ignored via `**/build/` / `**/.gradle/`. Verify by inspecting `.gitignore` at the repo root.
- [ ] 1.6 Edit root `settings.gradle.kts`: add `includeBuild("build-logic")` inside `pluginManagement { }` at the top.
- [ ] 1.7 Verify `./gradlew build-logic:convention:tasks --all` lists the plugin identifier for each registered plugin.

## 2. Shared helpers + base `library` plugin

- [ ] 2.1 Create `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/ProjectExtensions.kt`: `internal val Project.libs: VersionCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("libs")`.
- [ ] 2.2 Create `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/KotlinAndroid.kt`: `internal fun Project.configureKotlinAndroid(ext: CommonExtension<*, *, *, *, *, *>) { ... }` — sets `compileSdk = 37`, `defaultConfig.minSdk = 26`, `compileOptions` Java 17, `kotlinOptions`/`kotlin { jvmToolchain(17) }`.
- [ ] 2.3 Create `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/AndroidLibraryConventionPlugin.kt`. Apply `com.android.library` and `com.squareup.sort-dependencies`. Configure `LibraryExtension`: call `configureKotlinAndroid(this)`, set `defaultConfig.consumerProguardFiles("consumer-rules.pro")`, set default `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`. Add release build type matching the existing inline config (`isMinifyEnabled = false`, `proguardFiles(getDefaultProguardFile(...), "proguard-rules.pro")`).
- [ ] 2.4 Manually test in the composite: `./gradlew build-logic:convention:assemble` compiles clean.

## 3. `library.compose` plugin

- [ ] 3.1 Create `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/AndroidLibraryComposeConventionPlugin.kt`. Apply `nubecita.android.library` (via `pluginManager.apply("nubecita.android.library")`) + `org.jetbrains.kotlin.plugin.compose`. Configure `LibraryExtension.buildFeatures { compose = true }`. Add deps via `libs`: `implementation(platform(libs.findLibrary("androidx.compose.bom").get()))`, `implementation(libs.findLibrary("androidx.compose.ui").get())`, `implementation(libs.findLibrary("androidx.compose.material3").get())`, `implementation(libs.findLibrary("androidx.compose.ui.tooling.preview").get())`, `debugImplementation(libs.findLibrary("androidx.compose.ui.tooling").get())`.

## 4. `hilt` plugin

- [ ] 4.1 Create `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/AndroidHiltConventionPlugin.kt`. Apply `com.google.dagger.hilt.android` + `com.google.devtools.ksp`. Add `implementation(libs.findLibrary("hilt.android").get())` and `ksp(libs.findLibrary("hilt.android.compiler").get())`.

## 5. `feature` plugin

- [ ] 5.1 Create `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/AndroidFeatureConventionPlugin.kt`. Apply `nubecita.android.library`, `nubecita.android.library.compose`, `nubecita.android.hilt` in that order. **Defer** the `implementation(project(":core:common"))` line until `:core:common` actually exists — the feature-login change creates it. For now, add `implementation(project(":designsystem"))` + the lifecycle / nav3 / hilt-nav-compose / kotlinx-collections-immutable deps only; add a `// TODO: add :core:common dep after add-feature-login lands` comment marking the spot.
- [ ] 5.2 Alternatively (cleaner): **do** add `implementation(project(":core:common"))` in this plugin but gate on `:core:common` existing via `project.rootProject.subprojects.any { it.path == ":core:common" }`, or just fail loudly on missing project. **Decision at implementation time.**

## 6. `application` plugin

- [ ] 6.1 Create `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/AndroidApplicationConventionPlugin.kt`. Apply `com.android.application` + `com.squareup.sort-dependencies`. Configure `ApplicationExtension`: `configureKotlinAndroid(this)`, `defaultConfig.targetSdk = 37`, `buildFeatures { buildConfig = true; compose = true }`, default release build type, default test instrumentation runner. Also apply `org.jetbrains.kotlin.plugin.compose`, `com.google.dagger.hilt.android`, `com.google.devtools.ksp`. Add Compose BOM + base Compose deps (same as `library.compose`) + Hilt deps (same as `hilt`) + `androidx.hilt.navigation.compose`.

## 7. Plugin aliases in the version catalog

- [ ] 7.1 Add to `gradle/libs.versions.toml` under `[plugins]`:
  ```toml
  nubecita-android-library = { id = "nubecita.android.library", version = "unspecified" }
  nubecita-android-library-compose = { id = "nubecita.android.library.compose", version = "unspecified" }
  nubecita-android-hilt = { id = "nubecita.android.hilt", version = "unspecified" }
  nubecita-android-feature = { id = "nubecita.android.feature", version = "unspecified" }
  nubecita-android-application = { id = "nubecita.android.application", version = "unspecified" }
  ```

## 8. Migrate `:designsystem`

- [ ] 8.1 Rewrite `designsystem/build.gradle.kts`: `plugins { alias(libs.plugins.nubecita.android.library.compose) }`, `android { namespace = "net.kikin.nubecita.designsystem" }`, dependencies keep only `implementation(libs.androidx.compose.ui.text.google.fonts)`, `implementation(libs.androidx.core.ktx)`, `testImplementation(libs.junit)`, `androidTestImplementation(libs.androidx.test.espresso.core)`, `androidTestImplementation(libs.androidx.test.ext.junit)`. Remove all duplicated boilerplate now provided by the convention.
- [ ] 8.2 Verify `./gradlew :designsystem:assembleDebug :designsystem:testDebugUnitTest` passes.
- [ ] 8.3 Diff the resulting AAR's compile classpath vs pre-change to confirm no drift.

## 9. Migrate `:core:auth`

- [ ] 9.1 Rewrite `core/auth/build.gradle.kts`: `plugins { alias(libs.plugins.nubecita.android.library); alias(libs.plugins.nubecita.android.hilt); alias(libs.plugins.kotlin.serialization) }`, `android { namespace = "net.kikin.nubecita.core.auth" }`. Dependencies: `api(libs.atproto.oauth)`, `implementation(libs.androidx.datastore)`, `implementation(libs.androidx.datastore.tink)`, `implementation(libs.kotlinx.serialization.json)`, `implementation(libs.tink.android)`, `testImplementation(libs.junit)`, `testImplementation(libs.kotlinx.coroutines.test)`.
- [ ] 9.2 Verify `./gradlew :core:auth:assembleDebug :core:auth:testDebugUnitTest` passes.
- [ ] 9.3 Confirm `./gradlew :core:auth:dependencies --configuration debugCompileClasspath | grep -E "datastore-tink|atproto:oauth|tink-android"` lists the same versions as before.

## 10. Migrate `:app`

- [ ] 10.1 Rewrite `app/build.gradle.kts`: `plugins { alias(libs.plugins.nubecita.android.application); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.compose.screenshot); jacoco }`. Configure `android { namespace = "net.kikin.nubecita"; defaultConfig { applicationId = "net.kikin.nubecita"; versionCode = 1; versionName = "1.0" } }`, keep the `lint { baseline = file("lint-baseline.xml"); warningsAsErrors = false; abortOnError = true }` block, keep `experimentalProperties["android.experimental.enableScreenshotTest"] = true`. Move all module-specific deps (atproto runtime/models, ktor, navigation3, kotlinx-collections-immutable, lifecycle deps, designsystem project, core:auth project, compose-bom-provided deps) into the dependencies block, removing any that are now provided transitively by the convention plugin. Keep `publish` no-op task, `jacocoTestReport` task, and screenshot-test deps.
- [ ] 10.2 Verify `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint` passes.
- [ ] 10.3 A/B check: assemble debug + release on a pre-change commit, diff the APKs' class lists against post-change. Expect no new classes except the empty `BuildConfig`.

## 11. Documentation

- [ ] 11.1 Create `build-logic/README.md` explaining the composite, the five plugins, what each configures, and the "add a new feature module" recipe.
- [ ] 11.2 Update repo root `CLAUDE.md` under "Conventions": add a `## Module conventions` section documenting (a) the `:core:*` / `:feature:*/{api,impl}` split (deferred under `add-core-auth-session-storage`; land here since we're already in build-script territory) and (b) the five convention plugins as the canonical extension points for new modules.

## 12. Lint + format + pre-commit

- [ ] 12.1 Run `./gradlew spotlessApply` across the whole repo (covers `build-logic/` too via root `allprojects { spotless }`).
- [ ] 12.2 Run `pre-commit run --all-files`. Resolve anything flagged.
- [ ] 12.3 Run `./gradlew build` at the repo root and confirm green end-to-end.

## 13. Bd graph + PR ceremony

- [ ] 13.1 `nubecita-1ry` claimed.
- [ ] 13.2 Branch: `refactor/nubecita-1ry-build-logic-conventions` off `main`.
- [ ] 13.3 Conventional Commit: `refactor(build-logic): introduce convention plugins and migrate existing modules`. Body details the five plugins, the composite-build layout, and the zero-behavior-change guarantee. Footer: `Refs: nubecita-1ry`.
- [ ] 13.4 PR title matches the commit subject. Body includes the A/B APK-diff verification result and `Closes: nubecita-1ry`.
- [ ] 13.5 After merge: `bd close nubecita-1ry` and `openspec archive add-build-logic-conventions -y`.
- [ ] 13.6 After merge: resume the paused `add-feature-login` change, rewriting its §1-§2 tasks to use the new convention plugins for `:core:common`, `:feature:login:api`, and `:feature:login:impl`.
