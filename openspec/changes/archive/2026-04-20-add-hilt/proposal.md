## Why

The app baseline calls for Hilt as the DI container, but the project ships without it. As soon as we wire the first non-trivial dependency graph (atproto-kotlin client, Room database, repositories feeding ViewModels), hand-rolled construction starts leaking into composables and tests. Adopting Hilt now — before any of those dependencies land — keeps the cost low and avoids a retroactive refactor across every screen.

## What Changes

- Add Hilt (`com.google.dagger:hilt-android`) and KSP to the version catalog and apply both plugins in `app/build.gradle.kts`.
- Introduce `NubecitaApplication` annotated with `@HiltAndroidApp` and register it in `AndroidManifest.xml`.
- Annotate `MainActivity` with `@AndroidEntryPoint`.
- Convert `MainScreenViewModel` to `@HiltViewModel` with `@Inject constructor(...)`; switch the call site in `MainScreen.kt` from `viewModel()` to `hiltViewModel()`.
- Provide `DataRepository` (currently constructed inline in `MainScreen`) via a Hilt module bound to `DefaultDataRepository`.
- Establish the convention: every ViewModel is `@HiltViewModel`; every Application-scoped singleton is provided by a `@Module @InstallIn(SingletonComponent::class)`.

## Capabilities

### New Capabilities

- `dependency-injection`: defines how Application, Activity, and ViewModel scopes are bootstrapped via Hilt; how repositories and Application-scoped singletons are bound; and how tests substitute fakes.

### Modified Capabilities

(None — no existing capability specs in `openspec/specs/` yet.)

## Impact

- **Build**: `app/build.gradle.kts` gains `hilt-android-gradle-plugin` and KSP; `gradle/libs.versions.toml` gains Hilt + KSP versions and library entries. First-build time grows modestly (annotation processing).
- **Code**: new `NubecitaApplication` class; `AndroidManifest.xml` declares `android:name`. `MainActivity`, `MainScreenViewModel`, `MainScreen`, and `DataRepository` get touched.
- **Tests**: `MainScreenViewModelTest` continues to use direct constructor injection (it never went through Hilt). Future androidTest paths that need fakes will use `@HiltAndroidTest` + `HiltAndroidRule`; out of scope for this change.
- **Non-goals**: no Room, no atproto-kotlin client, no networking module, no Hilt navigation entry-points beyond `hiltViewModel()`. Those land in their own changes.

## Non-goals

- Wiring atproto-kotlin, Room, or Coil — separate changes.
- Introducing Hilt test infrastructure (`@HiltAndroidTest`, custom test runner) — defer until the first test that actually needs a fake graph.
- Splitting modules into a `:di` Gradle module — premature for a single-module app.
