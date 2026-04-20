## 1. Version catalog and Gradle plugins

- [ ] 1.1 Add `hilt = "2.57"` (or current stable) and `ksp = "2.3.20-2.0.4"` (matching Kotlin 2.3.20) to `gradle/libs.versions.toml [versions]`.
- [ ] 1.2 Add `hilt-android` and `hilt-android-compiler` library entries; add `androidx-hilt-navigation-compose` library entry for `hiltViewModel()`.
- [ ] 1.3 Add `hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }` and `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }` to `[plugins]`.
- [ ] 1.4 Apply both plugins in `app/build.gradle.kts` via `alias(libs.plugins.hilt)` and `alias(libs.plugins.ksp)`.
- [ ] 1.5 Wire `implementation(libs.hilt.android)`, `ksp(libs.hilt.android.compiler)`, and `implementation(libs.androidx.hilt.navigation.compose)` in the dependencies block.

## 2. Application bootstrap

- [ ] 2.1 Create `app/src/main/java/net/kikin/nubecita/NubecitaApplication.kt` annotated with `@HiltAndroidApp`.
- [ ] 2.2 Add `android:name=".NubecitaApplication"` to `<application>` in `app/src/main/AndroidManifest.xml`.
- [ ] 2.3 Annotate `MainActivity` with `@AndroidEntryPoint`.

## 3. Convert MainScreenViewModel and DataRepository wiring

- [ ] 3.1 Annotate `MainScreenViewModel` with `@HiltViewModel`; mark its constructor `@Inject constructor(...)`.
- [ ] 3.2 Switch `MainScreen` to obtain the VM via `hiltViewModel()` from `androidx.hilt.navigation.compose`.
- [ ] 3.3 Create `app/src/main/java/net/kikin/nubecita/data/DataModule.kt`: a `@Module @InstallIn(SingletonComponent::class) abstract class` binding `DataRepository` → `DefaultDataRepository` via `@Binds`.
- [ ] 3.4 Make `DefaultDataRepository` `@Inject constructor()`-able (singleton-scoped if it holds state worth caching).
- [ ] 3.5 Remove inline construction of `DefaultDataRepository` from `MainScreen` (it currently passes one as a default param).

## 4. Verify and commit

- [ ] 4.1 `./gradlew :app:assembleDebug` succeeds (Hilt KSP runs, no missing-binding errors).
- [ ] 4.2 `./gradlew testDebugUnitTest` — `MainScreenViewModelTest` still passes (it constructs the VM directly with a fake repo; Hilt is not in the path).
- [ ] 4.3 `./gradlew :app:validateDebugScreenshotTest` — existing previews still render (no behavior change).
- [ ] 4.4 `./gradlew spotlessCheck lint` clean.
- [ ] 4.5 Manual smoke: launch on emulator, confirm the data list still renders.

## 5. Documentation

- [ ] 5.1 Update `CLAUDE.md` "Project overview" stack line — Hilt is no longer aspirational once this lands.
- [ ] 5.2 README "Stack" bullet for Hilt stays as-is (already lists it).
