# CLAUDE.md

## Project overview

Nubecita — a fast, lightweight, native Android client for [Bluesky](https://bsky.app) and the AT Protocol. Kotlin 2.3 + Jetpack Compose with Material 3 Expressive, MVI on `ViewModel` + `StateFlow`, Hilt for DI. 100% native; 120hz scrolling is a hard requirement.

Stack: Coil 3 (images), Media3 (video), `atproto-kotlin` SDK (networking), DataStore + Tink (encrypted OAuth sessions), Room (offline caches — `:core:database` is wired), Navigation 3. Firebase Analytics + Crashlytics + App Check are integrated.

## Key commands

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew spotlessCheck lint :app:checkSortDependencies
./gradlew jacocoTestReportAggregated
./gradlew :designsystem:validateDebugScreenshotTest     # validate against committed screenshot baselines
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest  # Macrobenchmark, needs connected device/emulator

# Compose Compiler stability + recomposition reports (perf audits)
./gradlew :app:assembleRelease -PcomposeReports=true -PdebugSignedRelease=true
# reports land at <module>/build/compose_compiler/

pre-commit run --all-files
```

## Workflow

All work is tracked in [beads](https://github.com/steveyegge/beads) (`bd`). Every change starts from a bd issue and lands through a short-lived branch + PR. When using Claude Code, the `bd-workflow` skill automates the ceremony; without it, the steps below are the convention.

**Working from a second machine?** Read `docs/beads-multi-machine.md` **first**. The bd database syncs across machines via the DoltHub remote (`bd dolt push`/`pull`), and a stale/shadowed `dolt` binary will silently fork it (the symptom is `table has unknown fields`). That runbook covers the required per-machine `dolt` setup, the pull-first/push-last session loop, and fork recovery — the canonical source of truth is the Dolt remote, never `.beads/issues.jsonl`.

### Loop

1. `bd ready` — pick an unblocked issue.
2. `bd update <id> --claim`.
3. Create a branch named `<type>/<id>-<slug>` off `main`.
4. Commit with Conventional Commits; reference the bd id in the body footer.
5. Open a PR with `Closes: <id>` in the body.
6. `bd close <id>` after merge.

### Branch names

`<type>/<bd-id>-<slug>` where `<type>` is a Conventional Commit type (`feat`, `fix`, `chore`, `refactor`, `docs`, `test`, `perf`, `ci`, `build`, `style`). Infer from the bd issue type: `feature`→`feat`, `bug`→`fix`, `task`/`chore`→`chore`, `decision`→`docs`. Slug = kebab-cased title, capped at 50 chars. Never branch off an epic — work a child issue.

Example: `feat/nubecita-aew-create-mviviewmodel-base-class-and-mar`

### Commits

Conventional Commits via commitizen/commitlint. One bd issue per branch; reference it in the footer:

```
feat(mvi): add MviViewModel base class and marker interfaces

Introduce UiState/UiEvent/UiEffect markers plus abstract
generic MviViewModel<S,E,F> exposing StateFlow + buffered
effects Channel.

Refs: nubecita-aew
```

Use `Refs:` on work-in-progress commits. `Closes: <bd-id>` goes in the **PR body only**, not in every commit — otherwise a squash-merge double-closes.

### Rules of thumb

- One bd issue per branch. If scope grows, spawn a new bd issue.
- PR title should be Conventional — on squash-merge it becomes the commit subject. (Currently enforced only by the local `commit-msg` pre-commit hook, not CI.)
- `bd close` only after the PR merges.

## Conventions

- JDK 17 (`.java-version`), auto-downloaded via Foojay.
- Spotless + ktlint 1.4.1 + Compose rules.
- Conventional Commits enforced by commitlint.
- `main` is protected; feature branches + PRs only.
- Add `run-instrumented` label to a PR to trigger the CI instrumented-test job (off by default).

### Module conventions

Non-UI shared capabilities live under `:core:*`. Feature modules follow the Navigation 3 api/impl split: `:feature:<name>:api` holds `NavKey` types only, `:feature:<name>:impl` holds screens, ViewModels, and Hilt modules that contribute `@IntoSet EntryProviderInstaller` multibindings. `:app` stays a thin shell that aggregates the DI graph, wires `NavDisplay`, and hosts `MainActivity`.

Every Android module's `build.gradle.kts` applies one of the convention plugins shipped by the `build-logic/` composite build. The plugins centralize SDK versions, JVM toolchain, Compose wiring, and Hilt wiring. Modules declare only their namespace and module-specific deps. Plugin roster and "how to add a new module" recipe: `build-logic/README.md`.

| Plugin | Module type |
|--------|-------------|
| `nubecita.android.library` | Non-UI library (`:core:*` without Compose) |
| `nubecita.android.library.compose` | Compose-using library (`:designsystem`, `:core:common`) |
| `nubecita.android.hilt` | Add-on for library modules that use Hilt |
| `nubecita.android.feature` | `:feature:*:impl` modules (meta: library + compose + hilt + common feature deps) |
| `nubecita.android.application` | `:app` only |
| `nubecita.android.benchmark` | `:benchmark` only (AGP test variant + baseline profile producer) |
| `nubecita.android.room` | Add-on for modules that own Room entities/DAOs (`:core:database`) |
| `nubecita.android.jacoco` | Applied transitively by `library` and `application` (not `benchmark`) — all library and application modules get it automatically |

#### Feature-module sequencing: `:api`-only stubs

When a feature is named in a navigation surface (e.g. a tab in `MainShell`) before its real screens are written, ship the `:feature:<name>:api` module first — `NavKey` types only — and let `:app` register a placeholder Composable for that key under `@MainShell`. The full `:feature:<name>:impl` module lands later in the feature's own epic. This keeps the navigation chrome shippable independently of any feature's content readiness, and the placeholder rendering migrates cleanly when `:impl` arrives (delete the `:app`-side placeholder provider, add the new module's `@MainShell` provider — no bridging artifacts).

#### Two-shell `EntryProviderInstaller` qualifier convention

`:app` hosts two `NavDisplay` instances:

- The **outer** `NavDisplay` in `app/Navigation.kt` (`Splash → Login → Main`).
- The **inner** `NavDisplay` inside `MainShell` (the four top-level tabs and any sub-routes pushed onto a tab's stack).

Each `:feature:*:impl` module that contributes a `@Provides @IntoSet EntryProviderInstaller` MUST annotate the provider with exactly one of `@OuterShell` or `@MainShell` (defined in `:core:common:navigation`). The qualifier decides which `NavDisplay` collects the entry — an unqualified provider is dropped by both. Login goes on `@OuterShell`; everything tab-related (Feed, Search, Chats, Profile, sub-routes like Settings or PostDetail) goes on `@MainShell`.

### Module map

```
app/                     thin shell; aggregates DI, hosts NavDisplay + MainActivity
build-logic/             composite build with eight Gradle convention plugins
core/
  auth/                  OAuth session storage + token refresh (Tink-encrypted DataStore)
  billing/               SDK-agnostic Pro entitlement + purchase boundary (RevenueCat impl, anonymous appUserID)
  common/                MVI base, navigation qualifiers, coroutine dispatchers, time utils
  database/              Room database, entities, DAOs, migrations
  feed-mapping/          atproto wire types → UI model mappers (PostUi, EmbedUi, AuthorUi)
  post-interactions/     like / repost / follow primitives
  posting/               post-creation domain (ComposerError, ComposerAttachment)
  posts/                 post fetching repositories
  preferences/           DataStore preferences (non-encrypted, user settings)
  profile/               profile fetching (getProfile XRPC)
  push/                  FCM token registration + notification handling
  testing/               JVM test helpers (MainDispatcherExtension)
  testing-android/       androidTest helpers (HiltTestRunner, HiltTestActivity, MockEngineModule)
  video/                 Media3 / ExoPlayer coordinator (single-player, HLS)
data/
  models/                @Stable UI data classes (PostUi, AuthorUi, EmbedUi, etc.)
designsystem/            M3 Expressive tokens, components, preview wrappers
feature/
  chats/{api,impl}       conversation list + DM thread
  composer/{api,impl}    post composer (grapheme counter, language picker, mention typeahead)
  feed/{api,impl}        Following timeline with paginated scroll
  login/{api,impl}       OAuth login (outer shell)
  mediaviewer/{api,impl} zoomable image / HLS video lightbox (telephoto)
  moderation/{api,impl}  moderation actions
  onboarding/{api,impl}  onboarding flow
  paywall/{api,impl}     custom Compose Nubecita Pro paywall (plan picker, purchase/restore)
  postdetail/{api,impl}  thread view (ancestors + focus + replies)
  profile/{api,impl}     user profile with hero + Posts/Replies/Media tabs
  search/{api,impl}      search with typeahead, posts/people/feeds tabs, and recent search
  settings/{api,impl}    settings screen
  videoplayer/{api,impl} inline video player
benchmark/               Macrobenchmark + BaselineProfile generator
openspec/                specs/ and changes/ for design decisions
docs/                    design system docs, OAuth docs
```

### MVI conventions

Every screen's presenter extends `net.kikin.nubecita.core.common.mvi.MviViewModel<S, E, F>`. Declare a per-screen `data class FooState : UiState`, `sealed interface FooEvent : UiEvent`, and `sealed interface FooEffect : UiEffect`.

State is **flat** and UI-ready: concrete fields (`isLoading: Boolean`, `items: ImmutableList<T>`, etc.), never a VM-layer sum type like `Async<T>`. Composables read `state.isLoading` / `state.items` directly — no `when` on a remote-data wrapper at the UI boundary. List-typed fields use `ImmutableList` from `kotlinx.collections.immutable` so Compose can treat them as `@Stable` and skip recomposition.

The flat-fields rule applies to **independent** flags — fields whose values can vary independently (e.g., `isLoading` and `errorMessage` both true mid-error-clear). For **mutually-exclusive view modes** — sets of states where exactly one is active and multiple-true combinations would be invalid (e.g., a feed's `idle / initial-loading / refreshing / appending / initial-error` lifecycle) — declare a per-screen `sealed interface FooStatus` (or `FooLoadStatus`, `FooMode`) and expose it as a single field on `FooState`. The host composable branches via `when (state.status)` and the type system makes invalid combinations unrepresentable. The decision rule: flat boolean when two flags can legitimately coexist; sealed status sum when the flags are mutually exclusive and a "exactly one true at a time" invariant would otherwise need to be enforced by reducer code. This is NOT a license to wrap remote data in `Async<T>` — sealed status sums are per-screen, named after the screen's specific lifecycle (`FeedLoadStatus`, not `FetchState<T>`), and may carry per-variant payloads (e.g., `data class InitialError(val error: FeedError) : FeedLoadStatus`).

Errors route through a `sealed interface FooEffect : UiEffect` (typically `ShowError(val message: String)`), collected once in the screen's outermost composable via a single `LaunchedEffect` and surfaced as a Snackbar/Scaffold. Sticky error state, if needed for a screen, goes into the flat state explicitly (e.g. `errorBanner: String? = null`) — but the default is non-sticky snackbar via effect.

**Tab-internal navigation also flows through `UiEffect`.** When a screen inside `MainShell` needs to push a sub-route (e.g. tapping an author handle in a Feed post → Profile screen), the ViewModel emits `NavigateTo(target: NavKey)` (or a per-screen sealed effect with a similar shape). The screen Composable collects the effect and calls `LocalMainShellNavState.current.add(target)`. **ViewModels never inject the navigation state holder** — `MainShellNavState` is reachable only via `CompositionLocal`, which a ViewModel can't access. This keeps the MVI boundary clean and matches the error-routing pattern. The outer `:core:common:Navigator` (Splash/Login/Main routing) remains Hilt-injectable into ViewModels for that pre-shell lifecycle.

Inline `Flow.onEach { setState }.catch { setState(isLoading = false); sendEffect(ShowError(...)) }.launchIn(viewModelScope)` for remote data; inline `viewModelScope.launch { try { ... } catch { sendEffect(...) } }` for one-shot commands. Don't wrap these in a foundation helper until we have ≥3 screens using the identical shape.

Non-goals of the base class (do not add these without a separate proposal):
- No `Async<T>` / `Result<T>` wrapper types at the VM→UI boundary.
- No `launchSafe` / `collectSafely` helpers on the base — inline the `.catch` / `try { } catch { }` at each call site so the state-recovery shape stays visible.
- No Mavericks / Orbit / MVIKotlin or any MVI framework — stay on Jetpack + coroutines primitives.
- No `SavedStateHandle` plumbing in the base. Screens that need process-death persistence inject `SavedStateHandle` directly via Hilt.
- No inbound event `SharedFlow` / debounce / throttle in the base. Per-screen concerns (search typeahead, autocomplete, draft autosave, firehose sampling) build their own `MutableSharedFlow<E>` inside the feature VM.

#### Sanctioned MVI exception: editor surfaces own a Compose `TextFieldState`

Editor screens (text input where the IME and cursor must stay in lock-step with the field) MAY hold a Compose `androidx.compose.foundation.text.input.TextFieldState` as a public `val` on the ViewModel and observe it via `snapshotFlow`, instead of routing every keystroke through `handleEvent` / `setState`. The screen Composable wires the `OutlinedTextField(state = vm.textFieldState)` overload directly. This is the **only** sanctioned departure from "the VM owns canonical state and the UI is a pure projection" — it exists because the value/onValueChange round-trip is the canonical source of cursor-jump bugs once the reducer does any non-trivial work (e.g. cursor-aware token detection, debounced typeahead queries).

The exception is bounded:

- It applies to editor surfaces only — non-editor screens continue to own their state in the VM and project it through `UiState`.
- The text field's text and selection are the only state that lives outside `UiState`. Derived projections like `graphemeCount` / `isOverLimit` stay on `UiState`, updated by the VM's `snapshotFlow` collector so existing UI gates don't change shape.
- Tests that mutate `textFieldState` programmatically MUST drive the snapshot system manually via `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()` — there is no recomposer in unit tests.

Reference implementation: `:feature:composer:impl/ComposerViewModel`. Rationale: `openspec/changes/add-composer-mention-typeahead/design.md` (decisions §1 and §2).

### Tab re-tap / scroll-to-top convention

`MainShell` provides `LocalTabReTapSignal` — a `CompositionLocal<SharedFlow<Unit>>`. Any feature screen that wants to respond to a tab re-tap (scroll to top, etc.) reads this local and launches a `collectLatest` in a `LaunchedEffect`. **ViewModels do not observe this signal** — it terminates at the screen Composable only, because scroll state is a Compose runtime concern, not a VM state field.

### Keyboard / IME insets inside `MainShell`

Screens hosted by `MainShell`'s inner `NavDisplay` can be top-level tabs or sub-routes (e.g. Chat thread, PostDetail, Settings):

- **Sub-routes on Compact Width (Phones):** `MainShell` detects if the active route is a sub-route (not in `TopLevelDestinations`) and overrides the navigation suite layout type to `NavigationSuiteType.None`. This hides the bottom navigation bar.
- **Sub-routes on Medium/Expanded (Tablets):** The navigation suite uses `NavigationSuiteType.NavigationRail` which sits on the left side of the screen, so there is no bottom bar to interfere.

Because the bottom navigation bar is hidden on phone sub-routes and placed on the side on tablets, the inner sub-routes receive correct system insets directly. They should use the standard Jetpack Compose edge-to-edge pattern:
- **Prerequisite — `MainActivity` must declare `android:windowSoftInputMode="adjustResize"` on the `<activity>` element** (NOT on `<application>`, where the attribute is silently ignored — it's activity-scoped). Without it the window defaults to `ADJUST_PAN`, and opening the IME translates the *entire* window up, shoving the `TopAppBar` off the top of the screen. Verify at runtime with `adb shell dumpsys window windows | grep -A1 MainActivity | grep sim=` → must read `adjust=resize`, not `adjust=pan` (`nubecita-j4i5`).
- A **bottom-pinned input** (like `ChatComposerRow`) goes in the Scaffold **`bottomBar` slot**, not in the content `Column`. Per M3 Scaffold's contract, a present `bottomBar` owns the bottom inset (the body's bottom padding becomes the bar's measured height, not the raw inset), so exactly one layer handles the IME. Give the bar `Modifier.navigationBarsPadding().imePadding()` — chained so each `windowInsetsPadding` consumes the previous: closed → nav-bar height; open → full IME (which subsumes the nav-bar area). Set the screen `Scaffold`'s `contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime)` — i.e. the full safe-drawing area (status bar **and display cutout**) minus only the IME, which the `bottomBar` owns. Do NOT narrow this to `WindowInsets.systemBars`: that silently drops `displayCutout`, so with no orientation lock + `layoutInDisplayCutoutMode=always` a landscape side cutout would occlude the body content.
- The body content block applies `.padding(innerPadding).consumeWindowInsets(innerPadding)` to prevent nested double-padding.
- Do NOT re-add per-element IME correction hacks (e.g. a measure-phase `padding(bottom = min(ime, navBar))`). Those existed to compensate for the IME being applied at the wrong layer; with `adjustResize` + the single-owner `bottomBar` pattern above, the keyboard is a clean inset and no compensation is needed. Reference: `:feature:chats:impl/ChatScreenContent`.

**Top-level tabs** continue to show the bottom bar (on phones) or rail (on tablets), and their internal content insets are managed by the `NavigationSuiteScaffold`'s default insets consumption. If a tab-home screen has a text field at the top (like `SearchScreen`), standard `Scaffold` padding handles it correctly.

**Outer-shell** screens (Login, Onboarding — `@OuterShell`, no `NavigationSuiteScaffold`) are unaffected and handle the IME themselves via `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` / `safeDrawingPadding()`.

### Adaptive layouts (phone full-screen / tablet dialog)

The canonical "two layouts" need — a route that is **full-screen on phone (Compact) and a centered dialog on tablet/foldable (Medium/Expanded)** — is handled by a **Navigation 3 scene strategy**, NOT a hand-rolled per-feature launcher + overlay state + `CompositionLocal` + Dialog host. (That pattern was ~10 files per surface and is an anti-pattern here — don't reintroduce it.)

To make any `@MainShell` sub-route adaptive, tag its `entry` with `adaptiveDialog()` (from `:core:common:navigation`) — that is the **entire** opt-in:

```kotlin
entry<EditProfile>(metadata = adaptiveDialog()) { route -> /* the normal full-screen screen */ }
```

The shared `AdaptiveDialogSceneStrategy` (in `:app` at `app/src/main/java/.../shell/adaptive/AdaptiveDialogSceneStrategy.kt`, wired into `MainShell`'s `NavDisplay(sceneStrategies = …)` **before** the list-detail strategy — overlay strategies must come first) reads that metadata and renders the entry as a centered `Dialog` (scrim + 640dp card) at Medium/Expanded, and **declines** (`calculateScene` returns `null`) at Compact so it falls through to the full-screen single-pane scene. The push site is a plain `navState.add(route)` at every width — no width check, no launcher. The screen/ViewModel stay feature-`internal`; the previous destination stays composed underneath (`OverlayScene.overlaidEntries`), so a screen behind the dialog keeps its state and observers live (e.g. the profile's `ownProfileUpdates` refresh while the editor is open).

Reference impls: `EditProfileNavigationModule` and `ComposerNavigationModule` both register their route with one `adaptiveDialog()` metadata tag and are pushed via `navState.add(...)` (the composer was migrated off the old hand-rolled launcher/overlay in `nubecita-11st`). Mirror this scene-strategy approach for any new adaptive surface. Full contract + the Google I/O 2026 "scene strategies over per-form-factor layouts" rationale + the alpha-version note: `docs/adaptive-layouts.md`. Background recipes live in the `/navigation-3` and `/adaptive` Claude CLI skills (nav3 Dialog / BottomSheet / list-detail scene recipes).

### Design system conventions

#### Surface token roles

Every `surface*` token maps to exactly one depth role. Pick by role, never by token name:

| Depth role | M3 token | What lives there |
|---|---|---|
| Screen canvas | `surface` | `Scaffold` root, modal root, full-screen routes |
| Item card | `surfaceContainer` | Post cards, settings section cards, chat convo rows |
| Recessed inset | `surfaceContainerLow` | Quoted posts, external link embeds, unavailable-post placeholders |
| Raised affordance | `surfaceContainerHigh` | Chat message bubbles, day-separator chips, video-poster gradients |
| Strong fill | `surfaceContainerHighest` | Thumbnail placeholders, shimmer base, character-counter track |
| Reserved | `surfaceDim`, `surfaceBright`, `surfaceContainerLowest` | Do not use |

- Every `Scaffold(` call MUST set `containerColor = MaterialTheme.colorScheme.surface` explicitly.
- Use `colorScheme.surface`, not `colorScheme.background` (`background` and `surface` are identical in the brand scheme; `surface` is canonical).
- Tonal elevation lift is allowed **only** on windowed surfaces (`Dialog`, `BottomSheet`, modal `Surface`). For in-layout depth, use the explicit `surfaceContainer*` token and skip the elevation knob.

Full contract: `docs/design-system/surface-roles.md`.

#### Preview / screenshot wrappers

Three wrappers in `designsystem/src/main/kotlin/.../designsystem/preview/`:

| Wrapper | Surface paint | Sizing | When to use |
|---|---|---|---|
| `@PreviewNubecitaScreenPreviews` | (handled by callee) | Phone / Foldable / Tablet × Light / Dark | Full-screen composables needing device-size sweep |
| `NubecitaCanvasPreviewTheme { … }` | `surface` | `fillMaxSize()` | Screen-level, dialog, or pane-level fixtures |
| `@PreviewWrapper(NubecitaComponentPreview::class)` | `surfaceContainer` | Content natural bounds | Component-level fixtures (atoms, rows, isolated cards) |

`NubecitaTheme` directly is the escape hatch for fixtures without a Surface ancestor — rare, add a comment explaining why.

### Database conventions (`:core:database`)

- Reads return `Flow<T>`; writes are `suspend fun`. Multi-statement writes use `@Transaction suspend fun`.
- Entities are never exposed past the repository layer. Each entity has a same-file `fun FooEntity.asExternalModel(): Foo` extension returning a `:data:models` type. Feature modules depend on their per-domain `:core:<domain>` repositories, **not** on `:core:database` directly.
- Schema export is on (`exportSchema = true`). Committed schema JSON lives under `core/database/schemas/`. Every schema bump must commit the new `{N}.json` and add an `@AutoMigration` or register a manual `Migration` in `Migrations.kt`.
- Prefer `@AutoMigration`; hand-write `Migration` only when AutoMigration cannot resolve the diff.

### `:data:models` conventions

- No service abstractions — no `atproto:runtime`, `atproto:oauth`, `atproto:compose` deps.
- `atproto:models` primitives (`Facet`, `FacetByteSlice`, etc.) ARE allowed directly as field types; they're closer to `String` than to `PostView`.
- Every type is `@Stable` or `@Immutable`. Collections use `ImmutableList<T>`.
- The only Compose dependency is `compose-runtime` for stability annotations — never `compose-ui` or `material3`.
- Provide fixture factories alongside model definitions (mirror `PostUiFixtures.kt`) for use in downstream test and preview code.

### Billing conventions (`:core:billing`)

Nubecita Pro is an auto-renewing Google Play subscription mediated by RevenueCat. The provider is swappable by rewriting one module:

- **SDK-agnostic boundary.** No RevenueCat type (`CustomerInfo`, `Offerings`, `Package`, …) leaks past `:core:billing`. The rest of the app sees only `:data:models` types (`SubscriptionOffering`, `SubscriptionPlan`, `ActiveSubscription`) and the two repository interfaces. Wire→model translation lives in `RevenueCatMappers.kt` (pure, unit-tested by mocking only SDK value types).
- **Two repositories.** `EntitlementRepository` exposes entitlement *state* — `isPro: StateFlow<Boolean>` (the on/off gate) and `activeSubscription: StateFlow<ActiveSubscription?>` (plan + store product id, for the Settings manage/label surface). `BillingRepository` *initiates transactions* — `loadPlans` / `purchase` / `restorePurchases`. State is read from the former; the latter never exposes state.
- **Gate through `isPro`, never per-call-site.** Feature code reacts to the `isPro` stream; it must never synchronously one-shot-check entitlement (cold-start latency). PiP folds device-capability × `isPro` into the single `PipController.isEnabled` flag; the Supporter badge and Settings Pro section read `isPro` directly. New Pro-gated surfaces add a flag, not a branch.
- **Identity = anonymous Play `appUserID`, NOT the Bluesky DID** (design D3). No DID is sent to the provider; Pro follows the Google account across devices via Restore.
- **Inert without the key.** `RevenueCatInitializer.initialize(...)` skips `Purchases.configure` when the API key is blank, and the bench flavor never registers the initializer at all (only `:app`'s production-flavor `AppInitializer` calls it) — so keyless/bench builds construct the Hilt bindings but issue zero SDK/network calls and stay `isPro = false`. Never synchronously assume Pro.
- **Custom Compose paywall** (`:feature:paywall`), not RevenueCat's drop-in `purchases-ui` — live prices still come from `Offerings`. The `Activity` that Play's purchase needs is passed from the Composable layer, never injected into a ViewModel.
- **Tests** fake the boundary against the interfaces: `:core:billing`'s own JVM unit tests construct the in-memory `FakeEntitlementRepository` / `FakeBillingRepository` (in its `src/test` — not shared test-fixtures, so other modules can't reference them), while downstream feature tests mock `EntitlementRepository` / `BillingRepository` with MockK (drive `isPro` via a `MutableStateFlow`).

### Testing conventions

#### Unit tests (`:core:testing`)

- JUnit Jupiter (`junit.jupiter.api`) is the default test runner for JVM tests.
- `MainDispatcherExtension` (`@ExtendWith(MainDispatcherExtension::class)`) installs `UnconfinedTestDispatcher` as `Dispatchers.Main` for ViewModel tests.
- Turbine (`turbine`) for Flow assertions.
- MockK (`mockk`) for mocking.
- Coroutines test: `kotlinx.coroutines.test` (always present via `:core:testing`).

#### Android / instrumented tests (`:core:testing-android`)

- `HiltTestRunner` replaces the standard `AndroidJUnitRunner`; needed for `@HiltAndroidTest` tests.
- `MockEngineModule` (`@TestInstallIn(replaces = [NetworkEngineModule::class])`) swaps in a Ktor `MockEngine` for all network calls in `androidTest`.
- `FixtureLoader` reads JSON fixtures from `androidTest/assets/`.

#### Screenshot tests

Run via the AGP `com.android.compose.screenshot` plugin. Baseline images are committed under `src/screenshotTest/`. Update baselines with:
```bash
./gradlew :designsystem:updateDebugScreenshotTest
# (or the equivalent :feature:*:impl task)
```

Feature modules that ship UI-touching tasks MUST include `@Preview` annotations and screenshot tests alongside unit tests. Add `run-instrumented` label to the PR if the task also needs instrumented tests.

### CI jobs

| Job | Trigger | What it runs |
|---|---|---|
| `lint` | PR, push to `main` | pre-commit, Spotless, Android Lint, checkSortDependencies, semantic-release dry-run |
| `test` | PR, push to `main` | `testDebugUnitTest`, JaCoCo coverage, madrapps/jacoco-report comment |
| `build` | PR, push to `main` | `assembleDebug` |
| `screenshot` | PR, push to `main` | `validateDebugScreenshotTest` against committed baselines |
| `instrumented` | push to `main`, `workflow_dispatch`, PR with `run-instrumented` label | Connected device tests (runs on `ubuntu-latest` with emulator) |
| Release | push to `main` | semantic-release → GitHub release → Google Play upload |

## OpenSpec workflow

Design decisions and in-flight changes live under `openspec/`:
- `openspec/specs/<capability>/` — canonical requirements for a capability area.
- `openspec/changes/<change-name>/` — per-change design docs, tasks, and archive.
- `openspec/references/` — reference code checked in for inspiration; NOT built or linted.

Use the `openspec-propose`, `openspec-explore`, `openspec-apply-change`, and `openspec-archive-change` skills to interact with this workflow.
