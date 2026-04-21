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

### MVI conventions

Every screen's presenter extends `net.kikin.nubecita.ui.mvi.MviViewModel<S, E, F>`. Declare a per-screen `data class FooState : UiState`, `sealed interface FooEvent : UiEvent`, and `sealed interface FooEffect : UiEffect`.

State is **flat** and UI-ready: concrete fields (`isLoading: Boolean`, `items: ImmutableList<T>`, etc.), never a VM-layer sum type like `Async<T>`. Composables read `state.isLoading` / `state.items` directly — no `when` on a remote-data wrapper at the UI boundary. List-typed fields use `ImmutableList` from `kotlinx.collections.immutable` so Compose can treat them as `@Stable` and skip recomposition.

Errors route through a `sealed interface FooEffect : UiEffect` (typically `ShowError(val message: String)`), collected once in the screen's outermost composable via a single `LaunchedEffect` and surfaced as a Snackbar/Scaffold. Sticky error state, if needed for a screen, goes into the flat state explicitly (e.g. `errorBanner: String? = null`) — but the default is non-sticky snackbar via effect.

Inline `Flow.onEach { setState }.catch { setState(isLoading = false); sendEffect(ShowError(...)) }.launchIn(viewModelScope)` for remote data; inline `viewModelScope.launch { try { ... } catch { sendEffect(...) } }` for one-shot commands. Don't wrap these in a foundation helper until we have ≥3 screens using the identical shape.

Non-goals of the base class (do not add these without a separate proposal):
- No `Async<T>` / `Result<T>` wrapper types at the VM→UI boundary.
- No `launchSafe` / `collectSafely` helpers on the base — inline the `.catch` / `try { } catch { }` at each call site so the state-recovery shape stays visible.
- No Mavericks / Orbit / MVIKotlin or any MVI framework — stay on Jetpack + coroutines primitives.
- No `SavedStateHandle` plumbing in the base. Screens that need process-death persistence inject `SavedStateHandle` directly via Hilt.
- No inbound event `SharedFlow` / debounce / throttle in the base. Per-screen concerns (search typeahead, autocomplete, draft autosave, firehose sampling) build their own `MutableSharedFlow<E>` inside the feature VM.
