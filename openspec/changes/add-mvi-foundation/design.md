## Context

Nubecita's architecture docs claim MVI on `ViewModel + StateFlow`, but no base class or conventions exist yet. The only current VM (`MainScreenViewModel`) hand-rolls a `Loading/Error/Success` sealed interface on a vanilla `ViewModel` — a pattern that would get copy-pasted across every future feature (auth, feed, compose, search, notifications) and drift.

The author has a reference implementation in a sibling project (`~/code/slabsnap`) — a 33-LOC `MviViewModel<S, E, F>` using `MutableStateFlow` for state and a buffered `Channel` for effects. That pattern works, but repeats `.catch { sendEffect(ShowError(it.message)) }` in every feature VM. The author also asked to evaluate Airbnb's **Mavericks** for comparison. Mavericks solves the same problem with a framework — state dispatcher queue, `@PersistState` reflection, Fragment DSL, immutability validator — at a cost (dependency surface, cognitive load, bindings to a View-era Android) that doesn't pay off for a Compose-first Bluesky client.

Project north star from `CLAUDE.md`: "100% native; 120hz scrolling is a hard requirement … without having to use dozens and dozens of libraries." The design target is a **~100-LOC foundation** built from Jetpack + coroutines primitives, one real consumer (`MainScreenViewModel` migrated in the same change), and explicit non-goals to keep later pressure to reinvent the wheel at bay.

## Goals / Non-Goals

**Goals:**

- Single source of truth for MVI roles (`UiState` / `UiEvent` / `UiEffect`) so every feature package looks the same.
- A base class that captures the boring plumbing (expose `StateFlow`, expose effect `Flow`, atomic reducer, one-shot effect emission) in one place so feature VMs are mostly `handleEvent` logic.
- A shared `Async<T>` vocabulary so state classes stop reinventing `isLoading: Boolean + errorMessage: String? + data: T?` triplets.
- Error-handling helpers that erase the `.catch { sendEffect(...) }` boilerplate without coupling the base class to a specific `ShowError` type.
- One migrated screen (`MainScreenViewModel`) merged in the same PR so the foundation ships with proof it works.
- Near-zero dependency surface: only what's already on the classpath (`lifecycle-viewmodel`, `kotlinx-coroutines-core`).

**Non-Goals:**

- Adopting or mimicking a third-party MVI framework (Mavericks, Orbit, MVIKotlin).
- Process-death / `SavedStateHandle` persistence in the base.
- Event-ingress `SharedFlow` / debounce / throttle in the base — screens wire their own when needed.
- Compose-side helpers (`rememberEventSink`, `collectEffects`). Views call `vm::handleEvent` and `collectAsStateWithLifecycle` directly until a real pattern emerges.
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

### Decision: `onError` lambda parameter on helpers, not a `ShowError` coupling

**What:**
```kotlin
protected fun launchSafe(
    onError: (Throwable) -> F,
    block: suspend CoroutineScope.() -> Unit,
): Job

protected fun <T> Flow<T>.collectSafely(
    onError: (Throwable) -> F,
    action: suspend (T) -> Unit,
): Job
```
Callers write `launchSafe(onError = { FooEffect.ShowError(it.message ?: "...") }) { … }`.

**Why:** The slabsnap style hard-codes `sendEffect(ShowError(...))` across every VM, meaning every screen must have an effect type that happens to have a `ShowError` case. That's a hidden cross-cutting contract. The lambda makes the contract local to each call site: the VM that emits an `AuthEffect.LoginFailed` gets to do so directly, and a VM that has no user-facing error surface can log instead (`onError = { SilentEffect }`).

**Alternatives considered:**

- Require `UiEffect` to have a `fromError(Throwable): UiEffect` factory — couples every effect hierarchy to error semantics. Rejected.
- Built-in `ErrorEffect(throwable: Throwable)` shipped in `ui/mvi` — forces every screen to consume it or filter it out; also awkward for i18n. Rejected.
- No helper at all (slabsnap status quo) — the whole reason this change exists. Rejected.

### Decision: `Async<T>` lives in `ui/mvi/`

**What:** `Async.kt` in `net.kikin.nubecita.ui.mvi`.

**Why:** It's tightly used by VM state shapes and ships on the same release train. If a non-VM caller ever needs it (e.g., a plain repository that wants to surface load state to a widget), we move the file and leave a typealias — a two-minute refactor. Until then, cohesion beats speculative abstraction.

**Alternatives considered:**

- `ui/common/Async.kt` — fine, but ships a "common" bucket with one file, which tends to attract grab-bag contents. Defer the bucket until there's a second resident.
- Third-party (Arrow `Resource`, Mavericks `Async`) — pulls in a dependency for a 20-LOC type. Rejected per "stay native."

### Decision: Direct `handleEvent(E)` call, no inbound event Flow

**What:** Composables call `viewModel::handleEvent(event)` synchronously. The base class exposes no `MutableSharedFlow<E>`.

**Why:** The operators that would warrant a flow — `debounce`, `distinctUntilChanged`, `sample`, `conflate` — apply to a minority of Bluesky surfaces (typeahead search, mention autocomplete, draft autosave, firehose sampling, rapid-tap like). All of those are *per-VM* concerns: the screen that needs debouncing builds its own `MutableSharedFlow<E>` inside that VM. Baking a flow into every VM costs every VM a little and buys most nothing.

**Alternatives considered:**

- Channel-based event bus with `trySend` in Compose and `consumeAsFlow()` in the VM — meaningful backpressure benefit only for sustained-volume events (which the UI doesn't produce). Rejected as premature.

### Decision: Migrate `MainScreenViewModel` in the same change

**What:** The existing `MainScreenViewModel` (vanilla `ViewModel` with inline `MainScreenUiState`) is rewritten onto the new base, its state split into `MainScreenState / Event / Effect` files, and `MainScreen.kt` updated to call `handleEvent` and read an `Async<List<String>>`.

**Why:** Shipping a foundation without a real consumer is how patterns get adopted that nobody has stress-tested. One migration proves the API, exposes awkward corners before they're frozen, and gives reviewers a concrete before/after. It's also small (38 LOC current VM; ~60 LOC after split).

**Alternatives considered:**

- Ship foundation only, migrate later — risks the foundation needing a breaking revision on the first real migration. Rejected.
- Migrate and add a second dummy consumer — gold-plating; one real consumer is sufficient validation. Rejected.

## Risks / Trade-offs

- [`Channel.BUFFERED` is unbounded.] → Mitigation: UI-level effects (navigation, toasts, one-off errors) emit at human-interaction rates. Documented as an invariant ("do not spam effects in tight loops"); no compile-time enforcement. If a future screen produces high-volume effects (e.g., a firehose debug panel), it converts to `Channel(capacity = N, onBufferOverflow = DROP_OLDEST)` locally rather than changing the base.
- [`sendEffect` from inside a `setState { }` reducer would run on the Flow update path.] → Mitigation: `setState` takes `S.() -> S`, a pure transformation — effects are physically impossible inside it. Naming and signature enforce the split.
- [`handleEvent` is synchronous and called directly from Compose; a long-running event handler would block the UI thread.] → Mitigation: The base class's contract is "dispatch, then launch into `viewModelScope`." The existing slabsnap implementations already do this; one sentence in the KDoc on `handleEvent` makes it explicit. Not a base-class responsibility to enforce.
- [Marker interfaces offer no compile-time protection against reusing a `FooEvent` from inside `BarViewModel`.] → Mitigation: Generic bounds on `MviViewModel<S, E, F>` force the declared type per VM; mixing types is a type error. Acceptable.
- [`Async.Loading` and `Async.Uninitialized` are both `data object`, so `map` returns the same singleton and loses no information — but a careless author might add a future `LoadingWithProgress(pct: Float)` and break `map` semantics.] → Mitigation: KDoc on `Async` calls out that adding a variant requires updating `map` / `getOrNull`. No compile-time help; acceptable given the API's small surface.
- [Developers might reach for Mavericks anyway once the first complex screen feels tedious.] → Mitigation: this design.md plus a short section in `CLAUDE.md`'s conventions once the change lands, citing the non-goals and the reasoning.

## Migration Plan

There is nothing to migrate except `MainScreenViewModel`, and that happens in the same PR. Rollback is a single `git revert` — no data migration, no dependency change, no user-visible behavior change beyond the refresh button on `MainScreen` (which today does nothing explicit; after this change it dispatches `MainScreenEvent.Refresh`).

## Open Questions

- None blocking. Follow-ups (tracked as separate bd issues before this change lands):
  1. Adopt Turbine + MockK + JUnit 5 in the test stack.
  2. Document the `SavedStateHandle` pattern once the first screen needs process-death persistence.
