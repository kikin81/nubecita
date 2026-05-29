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

Screens hosted by `MainShell`'s inner `NavDisplay` (any tab or sub-route) sit inside a `NavigationSuiteScaffold`. Adaptive scaffolds (`NavigationSuiteScaffold`, `ListDetailPaneScaffold`) manage their own bar/rail insets but **do NOT propagate inset `PaddingValues` to inner content, and raise content for the IME by layout without consuming the inset**. That breaks the usual inset modifiers:

- `Modifier.imePadding()` **double-counts** the keyboard (the suite's lift isn't a consumed inset, so imePadding re-adds the whole keyboard → a keyboard-tall gap).
- Removing it leaves the content a nav-bar height short (the suite consumes the nav-bar inset before lifting, so the keyboard's accessory strip overlaps a bottom-pinned input).
- `Modifier.fitInside(WindowInsetsRulers.Ime.current)` — the Android-docs-preferred tool for "ancestor didn't consume" — is **placement-phase** and was observed **desyncing from the suite's own lift and sticking to a keyboard-tall gap** on real IME-open here. Avoid it under this shell.

What works: a **measure-phase, state-read padding** on the bottom-anchored element — `padding(bottom = min(WindowInsets.ime.getBottom(d), WindowInsets.navigationBars.getBottom(d)).toDp())`. It re-adds exactly the nav-bar-sized overlap the suite's lift misses, recomputes every IME-animation frame, and (being measure-phase) can't get stuck on a stale placement. `min(...)` is 0 when the keyboard is closed. Reference: `:feature:chats:impl/ChatScreenContent` `ChatComposerRow` (`nubecita-b6uv.4`).

**Outer-shell** screens (Login, Onboarding — `@OuterShell`, no `NavigationSuiteScaffold`) are unaffected and keep handling the IME themselves via `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` / `safeDrawingPadding()`.

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
