# CLAUDE.md

## Project overview

Nubecita — a fast, lightweight, native Android client for [Bluesky](https://bsky.app) and the AT Protocol. Kotlin + Jetpack Compose with Material 3 Expressive, MVI on `ViewModel` + `StateFlow`, Hilt for DI. 100% native; 120hz scrolling is a hard requirement.

Pending: Room (persistence), Coil (images), `atproto-kotlin` (networking) — listed in the README stack, not yet wired.

## Key commands

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew spotlessCheck lint
./gradlew :app:jacocoTestReport

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

### Module conventions

Non-UI shared capabilities live under `:core:*` (e.g. `:core:auth`). Feature modules follow the Navigation 3 api/impl split: `:feature:<name>:api` holds `NavKey` types only, `:feature:<name>:impl` holds screens, ViewModels, and Hilt modules that contribute `@IntoSet EntryProviderInstaller` multibindings. `:app` stays a thin shell that aggregates the DI graph, wires `NavDisplay`, and hosts `MainActivity`.

Every Android module's `build.gradle.kts` applies one of five convention plugins shipped by the `build-logic/` composite build. The plugins centralize SDK versions, JVM toolchain, Compose wiring, and Hilt wiring. Modules declare only their namespace and module-specific deps. Plugin roster and "how to add a new module" recipe: `build-logic/README.md`.

| Plugin | Module type |
|--------|-------------|
| `nubecita.android.library` | Non-UI library (`:core:*` without Compose) |
| `nubecita.android.library.compose` | Compose-using library (`:designsystem`) |
| `nubecita.android.hilt` | Add-on for library modules that use Hilt |
| `nubecita.android.feature` | `:feature:*:impl` modules (meta: library + compose + hilt + common feature deps) |
| `nubecita.android.application` | `:app` only |

#### Feature-module sequencing: `:api`-only stubs

When a feature is named in a navigation surface (e.g. a tab in `MainShell`) before its real screens are written, ship the `:feature:<name>:api` module first — `NavKey` types only — and let `:app` register a placeholder Composable for that key under `@MainShell`. The full `:feature:<name>:impl` module lands later in the feature's own epic. This keeps the navigation chrome shippable independently of any feature's content readiness, and the placeholder rendering migrates cleanly when `:impl` arrives (delete the `:app`-side placeholder provider, add the new module's `@MainShell` provider — no bridging artifacts).

#### Two-shell `EntryProviderInstaller` qualifier convention

`:app` hosts two `NavDisplay` instances:

- The **outer** `NavDisplay` in `app/Navigation.kt` (`Splash → Login → Main`).
- The **inner** `NavDisplay` inside `MainShell` (the four top-level tabs and any sub-routes pushed onto a tab's stack).

Each `:feature:*:impl` module that contributes a `@Provides @IntoSet EntryProviderInstaller` MUST annotate the provider with exactly one of `@OuterShell` or `@MainShell` (defined in `:core:common:navigation`). The qualifier decides which `NavDisplay` collects the entry — an unqualified provider is dropped by both. Login goes on `@OuterShell`; everything tab-related (Feed, Search, Chats, Profile, sub-routes like Settings or PostDetail) goes on `@MainShell`.

### MVI conventions

Every screen's presenter extends `net.kikin.nubecita.ui.mvi.MviViewModel<S, E, F>`. Declare a per-screen `data class FooState : UiState`, `sealed interface FooEvent : UiEvent`, and `sealed interface FooEffect : UiEffect`.

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
