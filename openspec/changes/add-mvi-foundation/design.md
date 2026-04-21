## Context

Nubecita's architecture docs claim MVI on `ViewModel + StateFlow`, but no base class or conventions exist yet. The only current VM (`MainScreenViewModel`) hand-rolls a `Loading/Error/Success` sealed interface on a vanilla `ViewModel` — a pattern that would get copy-pasted across every future feature (auth, feed, compose, search, notifications) and drift.

The author has a reference implementation in a sibling project (`~/code/slabsnap`) — a 33-LOC `MviViewModel<S, E, F>` using `MutableStateFlow` for state and a buffered `Channel` for effects, with VMs inlining `.catch { setState(isLoading = false); sendEffect(ShowError(...)) }` at each call site. The author also asked to evaluate Airbnb's **Mavericks** for comparison. Mavericks solves the same problem with a framework — state dispatcher queue, `@PersistState` reflection, Fragment DSL, immutability validator — at a cost (dependency surface, cognitive load, bindings to a View-era Android) that doesn't pay off for a Compose-first Bluesky client.

Project north star from `CLAUDE.md`: "100% native; 120hz scrolling is a hard requirement … without having to use dozens and dozens of libraries." The design target is a **~60-LOC foundation** built from Jetpack + coroutines primitives, one real consumer (`MainScreenViewModel` migrated in the same change), and explicit non-goals to keep later pressure to reinvent the wheel at bay.

## Goals / Non-Goals

**Goals:**

- Single source of truth for MVI roles (`UiState` / `UiEvent` / `UiEffect`) so every feature package looks the same.
- A base class that captures the boring plumbing (expose `StateFlow`, expose effect `Flow`, atomic reducer, one-shot effect emission) in one place so feature VMs are mostly `handleEvent` logic.
- Flat, UI-ready state per screen — composables read concrete fields, never a VM-layer sum type.
- One migrated screen (`MainScreenViewModel`) merged in the same PR so the foundation ships with proof it works.
- Near-zero dependency surface: only what's already on the classpath (`lifecycle-viewmodel`, `kotlinx-coroutines-core`), plus `kotlinx-collections-immutable` for `@Stable` list fields.

**Non-Goals:**

- Adopting or mimicking a third-party MVI framework (Mavericks, Orbit, MVIKotlin).
- `Async<T>` / `Result<T>` wrapper types at the VM→UI boundary.
- `launchSafe` / `collectSafely` helpers on the base — VMs inline the error path.
- Process-death / `SavedStateHandle` persistence in the base.
- Event-ingress `SharedFlow` / debounce / throttle in the base — screens wire their own when needed.
- Compose-side helpers (`rememberEventSink`, `collectEffects`). Views call `vm::handleEvent`, `collectAsStateWithLifecycle`, and a single `LaunchedEffect { viewModel.effects.collect { ... } }` directly until a real pattern emerges.
- New test-stack libraries (Turbine, MockK, JUnit 5). Tracked in a separate bd issue.
- Migrating anything beyond `MainScreenViewModel` — nothing else in the app uses `ViewModel` today.

## Decisions

### Decision: StateFlow for state, buffered Channel for effects

**What:** `_uiState: MutableStateFlow<S>` exposed as `StateFlow<S>`. `_effects: Channel<F>(Channel.BUFFERED)` exposed as `effects: Flow<F>` via `receiveAsFlow()`.

**Why:** State and effects have fundamentally different semantics:

| | State | Effect |
|---|---|---|
| Lifetime | Always has a current value | Fire-and-forget, one-shot |
| Re-delivery | New subscribers get latest | Must not be re-delivered (no re-navigation, no re-toast) |
| Conflation | OK (equivalent states collapse) | Never (each effect matters) |
| Backpressure | N/A | Must buffer so emissions before collector attach are not lost |

`StateFlow` is the canonical match for state. For effects, the common mistake is `SharedFlow(replay=0)` — which *drops* events emitted while no collector is attached (e.g., between `onStart` and `repeatOnLifecycle` re-attach after config change). A `Channel(BUFFERED).receiveAsFlow()` turns the channel into a flow that buffers until a *single* collector reads, then removes consumed items — which is exactly the "deliver each effect exactly once to whoever is listening" semantics we want.

**Alternatives considered:**

- `SharedFlow(replay = 0, extraBufferCapacity = 64, onBufferOverflow = SUSPEND)` — multiple collectors see the same effect, which is wrong for navigation/toasts. Rejected.
- `SharedFlow(replay = 1)` — effects would be re-delivered to new subscribers on every config change. Hard bugs. Rejected.
- Keeping effects in the state (`showError: String?`) and clearing after consumption — requires an explicit `eventConsumed` reducer, couples UI state to UI actions, doesn't handle navigation effects. Rejected.

### Decision: Empty marker interfaces, per-screen sealed hierarchies

**What:** `UiState`, `UiEvent`, `UiEffect` are empty interfaces. Features declare their own `sealed interface FooEvent : UiEvent` etc.

**Why:** The markers exist only to constrain the base class's generics (`MviViewModel<S : UiState, …>`). Putting anything on them (IDs, timestamps, logging hooks) adds weight every feature pays regardless of need. `sealed interface` in the feature package gives exhaustive `when` in `handleEvent` for free — which is the actual design goal.

**Alternatives considered:**

- Sealed-class base with a built-in `id: String` — every VM now has to cook up unique IDs; gains nothing for Compose. Rejected.
- `UiIntent` instead of `UiEvent` — "intent" is overloaded (Android `Intent`, MVI "intent" in Orbit nomenclature, user-intent in conversational UIs). "Event" is unambiguous in Compose-land. Keeping slabsnap's name.

### Decision: Flat UI-ready state, effect-based errors

**What:** Feature `FooState` uses concrete fields — `isLoading: Boolean`, `items: ImmutableList<T>`, `selected: Foo?`. Errors from remote sources emit a `FooEffect.ShowError(message)`, collected once in the screen's outermost composable and surfaced as a Snackbar. No `Async<T>` / `Result<T>` wrapping the data.

**Why:** An earlier draft of this change introduced `Async<T>` (`Uninitialized`/`Loading`/`Success`/`Failure`) as a shared state vocabulary. That forced every composable that renders remote data to `import net.kikin.nubecita.ui.mvi.Async` and `when`-match on a presentation-layer sum type the UI should not care about — a foundation abstraction leaking into Compose. Slabsnap, the reference, does not do this: state is flat, errors route through effects, composables read `state.isLoading` and `state.items` directly. That pattern reads cleaner, keeps the foundation smaller (~60 LOC vs. ~100), and matches what the Android team documents.

The trade-off is that errors are non-sticky by default — a snackbar fires once, and if the user misses it there is nothing on screen to look at. Screens that need a sticky error banner declare an explicit concrete field on `FooState` (`errorBanner: String? = null`) and clear it via an event. This is an intentional opt-in per screen rather than a foundation-level behavior.

**Alternatives considered:**

- Ship `Async<T>` anyway and rely on convention to keep Compose from importing it — a rule that leaks is a rule that drifts. Rejected.
- Expose a nullable `error: Throwable?` on state by default — couples every state to error rendering; non-UI VMs (background sync, etc.) pay for it. Rejected.

### Decision: No `launchSafe` / `collectSafely` helpers on the base

**What:** The base class provides only `setState` and `sendEffect`. Feature VMs inline `Flow.onEach { }.catch { }.launchIn(viewModelScope)` and `viewModelScope.launch { try { } catch { } }` at each call site.

**Why:** A wrapper helper (e.g. `collectSafely(onError = { FooEffect.ShowError(...) }) { ... }`) compresses the happy path nicely but hides the *state-recovery* shape — `setState { copy(isLoading = false) }` on failure — which is the part that actually varies by screen. With helpers, that recovery lives inside the `onError` lambda and becomes easy to forget; without helpers, the recovery is visible in the `.catch { }` block at every call site. Slabsnap takes the inline route and it reads cleaner across ~5 VMs.

The rule of thumb in `CLAUDE.md` is "promote to a helper only when ≥3 screens share the identical shape." When that happens, the helper will belong in a feature module, not in the foundation.

**Alternatives considered:**

- Keep `launchSafe` / `collectSafely` with an `onError: (Throwable) -> F` lambda (the original draft) — two real concerns (state recovery and effect emission) get squeezed into one `onError` and diverge by screen. Rejected after the first migration exposed the awkwardness.
- Keep helpers but require `onError` to return `Pair<S.() -> S, F>` — ugly signature, still hides the shape. Rejected.

### Decision: Direct `handleEvent(E)` call, no inbound event Flow

**What:** Composables call `viewModel::handleEvent(event)` synchronously. The base class exposes no `MutableSharedFlow<E>`.

**Why:** The operators that would warrant a flow — `debounce`, `distinctUntilChanged`, `sample`, `conflate` — apply to a minority of Bluesky surfaces (typeahead search, mention autocomplete, draft autosave, firehose sampling, rapid-tap like). All of those are *per-VM* concerns: the screen that needs debouncing builds its own `MutableSharedFlow<E>` inside that VM. Baking a flow into every VM costs every VM a little and buys most nothing.

**Alternatives considered:**

- Channel-based event bus with `trySend` in Compose and `consumeAsFlow()` in the VM — meaningful backpressure benefit only for sustained-volume events (which the UI doesn't produce). Rejected as premature.

### Decision: Migrate `MainScreenViewModel` in the same change

**What:** The existing `MainScreenViewModel` (vanilla `ViewModel` with inline `MainScreenUiState`) is rewritten onto the new base, its state split into `MainScreenState / Event / Effect` files, and `MainScreen.kt` updated to call `handleEvent`, read flat `state.items` / `state.isLoading`, and collect effects into a `Scaffold` + `SnackbarHost`.

**Why:** Shipping a foundation without a real consumer is how patterns get adopted that nobody has stress-tested. One migration proves the API, exposes awkward corners before they're frozen (the `Async<T>` rollback is exactly this case), and gives reviewers a concrete before/after.

**Alternatives considered:**

- Ship foundation only, migrate later — risks the foundation needing a breaking revision on the first real migration. Rejected.
- Migrate and add a second dummy consumer — gold-plating; one real consumer is sufficient validation. Rejected.

## Risks / Trade-offs

- [`Channel.BUFFERED` is a bounded default-capacity channel (64 by default), not unbounded, so `send` suspends when the buffer is full.] → Mitigation: UI-level effects (navigation, toasts, one-off errors) emit at human-interaction rates, so the default buffer is ample; documented as an invariant ("do not spam effects in tight loops") with no compile-time enforcement. If a future screen genuinely needs different semantics (e.g., a firehose debug panel), it opts into them locally — `Channel.UNLIMITED` if unbounded buffering is truly intended, or `Channel(capacity = N, onBufferOverflow = DROP_OLDEST)` for bounded-lossy — rather than changing the base.
- [`sendEffect` from inside a `setState { }` reducer would run on the Flow update path.] → Mitigation: `setState` takes `S.() -> S`, a pure transformation — effects are physically impossible inside it. Naming and signature enforce the split.
- [`handleEvent` is synchronous and called directly from Compose; a long-running event handler would block the UI thread.] → Mitigation: The base class's contract is "dispatch, then launch into `viewModelScope`." Inline `viewModelScope.launch { }` or `Flow...launchIn(viewModelScope)` at each call site.
- [Marker interfaces offer no compile-time protection against reusing a `FooEvent` from inside `BarViewModel`.] → Mitigation: Generic bounds on `MviViewModel<S, E, F>` force the declared type per VM; mixing types is a type error. Acceptable.
- [Errors are non-sticky by default (snackbar-only).] → Mitigation: Covered in the flat-state decision above. Screens that need sticky errors add a concrete field to state.
- [Inlining `.catch { }` in every VM risks inconsistent recovery shapes across screens.] → Mitigation: `CLAUDE.md` documents the canonical shape (`setState { copy(isLoading = false) }; sendEffect(ShowError(...))`). When ≥3 screens share the exact shape, a feature-local helper lives in a feature module, not in the foundation.
- [Developers might reach for Mavericks anyway once the first complex screen feels tedious.] → Mitigation: this design.md plus a short section in `CLAUDE.md`'s conventions once the change lands, citing the non-goals and the reasoning.

## Migration Plan

There is nothing to migrate except `MainScreenViewModel`, and that happens in the same PR. Rollback is a single `git revert` — no data migration, no dependency change, no user-visible behavior change beyond the refresh button on `MainScreen` (which today does nothing explicit; after this change it dispatches `MainScreenEvent.Refresh`) and a snackbar surfaced via `Scaffold`.

## Open Questions

- None blocking. Follow-ups (tracked as separate bd issues before this change lands):
  1. Adopt Turbine + MockK + JUnit 5 in the test stack.
  2. Document the `SavedStateHandle` pattern once the first screen needs process-death persistence.
