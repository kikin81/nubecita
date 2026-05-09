## Context

The unified composer (`unified-composer` change, `nubecita-wtq` epic) currently holds the post text as `ComposerState.text: String` in `ComposerViewModel`, mutated by a `ComposerEvent.TextChanged` reducer. The screen wires it as the classic Compose round-trip:

```kotlin
OutlinedTextField(
    value = state.text,
    onValueChange = { vm.handleEvent(ComposerEvent.TextChanged(it)) },
    ...
)
```

`nubecita-wtq.11` (just merged via PRs #122/#123) adds a submit-time facet extractor in `:core:posting/internal/FacetExtractor.kt`. It walks the final text, finds `@handle` and `https://...` matches, calls `app.bsky.identity.resolveHandle` for each mention, and emits `app.bsky.richtext.facet` objects with byte-offset indices. Mentions that fail `resolveHandle` are silently dropped per the AT Protocol docs — which is the failure mode this change exists to make non-fragile.

Compose BOM 2026.04.01 ships Foundation 1.7+ which exposes the `BasicTextField`/`OutlinedTextField` overload taking a `TextFieldState`. This is the modern idiom for editor surfaces; the value/onValueChange overload remains supported but its round-trip-through-state shape is the canonical source of cursor-jump bugs once the reducer does any non-trivial work.

The composer is the most interactive surface in the app. Doing the text-input architecture migration once, while the screen is small and well-tested, is materially cheaper than retrofitting it later.

## Goals / Non-Goals

**Goals:**

- Eliminate the value/onValueChange round-trip from the composer's hot path so the IME and cursor are decoupled from VM-side work (token detection, debounced network calls, future editor features).
- Add a typeahead surface that makes mention authoring discoverable and typo-resistant: every accepted suggestion produces a canonical handle that `wtq.11`'s extractor will resolve at submit time.
- Keep the typeahead UX consistent with the platform precedent (Bluesky / Threads / X mobile): inline list above the IME, bordered card, avatar + display name + `@handle` row, tap-to-insert with cursor placement after the inserted handle plus a trailing space.
- Stay inside the documented MVI baseline for everything except the editor's text ownership — which is a single, documented exception with a clear blast radius.
- Zero new third-party dependencies. No new modules.

**Non-Goals:**

- Hashtag typeahead, URL typeahead, manual-disable affordances on individual tokens, hardware-keyboard navigation of the suggestion list, pre-submit styled rendering of mentions inside the editable field, and process-death survival of the typeahead pipeline. All deferred. (See `proposal.md` Non-goals.)
- Refactoring `:core:posting`'s existing `HandleResolver` or `FacetExtractor`. The typeahead repository is additive; submit-time resolution is unchanged.
- Generalizing the text-state ownership exception to other screens. This is editor-specific.
- A new module for actor reads (`:core:actor`). Defer until other actor-facing reads land that justify it; until then, `ActorTypeaheadRepository` lives in `:core:posting` next to the existing `HandleResolver`.

## Decisions

### 1. Text-state ownership: VM-held `TextFieldState`, observed via `snapshotFlow`

`ComposerViewModel` exposes a public `val textFieldState: TextFieldState` constructed in the VM's init with `initialText = ""`. The screen wires `OutlinedTextField(state = vm.textFieldState, ...)`. The VM observes:

```kotlin
init {
    snapshotFlow { textFieldState.text.toString() to textFieldState.selection }
        .onEach { (text, selection) ->
            // Drive the existing grapheme counter
            val count = GraphemeCounter.count(text)
            setState { copy(graphemeCount = count, isOverLimit = count > MAX_GRAPHEMES) }
            // Drive typeahead token detection + lookup
            val token = currentMentionToken(text, selection.end)
            handleTokenObservation(token)
        }
        .launchIn(viewModelScope)
}
```

`ComposerState.text: String` is **removed**. `ComposerEvent.TextChanged` is **removed**. Submit reads `textFieldState.text.toString()` directly:

```kotlin
private fun handleSubmit() {
    val text = textFieldState.text.toString()
    if (!canSubmit(uiState.value, text)) return
    ...
    postingRepository.createPost(text = text, attachments = ..., replyTo = ...)
}
```

**Why:** `TextFieldState` keeps the IME's view of the text and the field's view in lock-step — there is no value/onValueChange round-trip to lag behind. `snapshotFlow` is the canonical Compose↔non-Compose bridge; it emits on the next snapshot frame after a write, never racing the IME's composition. Submit reading directly from `TextFieldState` keeps the read site explicit (no risk of reading stale mirrored state).

**Alternatives considered:**

- *VM-owned `TextFieldValue`* (option B from brainstorming). Migrates selection into state but keeps the round-trip — the cursor problem is mitigated, not eliminated. Rejected: the round-trip is the actual culprit.
- *Composable-owned `TextFieldValue` with debounced VM sync* (option C). Eliminates round-trip but creates two sources of truth during the debounce window; submit needs special-cased read from local state. Rejected: the seam is fragile and obscures the submit boundary.
- *Holding `TextFieldState` in the screen as `remember { TextFieldState() }` and passing it to the VM as a parameter*. Rejected: the VM needs a stable reference for `init`'s `snapshotFlow`; rotation/recomposition would re-create the screen-local state and decouple it from the VM's collector.

**Why VM-held vs Composable-held `TextFieldState`:** The VM survives configuration changes; the composable's `remember { ... }` survives them too via `rememberSaveable`-ish semantics on `TextFieldState`, but holding it in the VM keeps the snapshotFlow collector co-located with the consumer (the typeahead pipeline). This is the same trade-off `:feature:postdetail`'s `LazyListState` resolves the other way (composable-held) — but `LazyListState` has no VM collector. Where the VM observes the state, the VM should own it.

### 2. Documented MVI exception + scope of the deviation

The repo's MVI section in CLAUDE.md says the VM owns canonical state and the UI is a pure projection. This change deliberately moves the canonical text out of `ComposerState` and into a Compose primitive held by the VM. The exception is:

- **Bounded to editor surfaces**: this exemption applies to `TextFieldState`-backed editors only. ViewModels for non-editor screens continue to own their state.
- **Documented inline**: the `ComposerViewModel` Kdoc gains a section explaining the choice and pointing back to this design.
- **CLAUDE.md augmentation**: a one-paragraph "MVI exceptions" subsection is added beneath the existing MVI conventions block, naming `TextFieldState` as the only sanctioned exception and the rationale.

This keeps the deviation discoverable without inviting drift on screens where the round-trip cost is irrelevant.

### 3. Token detection: pure helper run on every snapshot

`currentMentionToken(text: CharSequence, cursor: Int): String?` lives in `:feature:composer:impl/internal/MentionTokenDetector.kt`. Logic:

1. Walk back from `cursor - 1` to find the nearest `@`. Stop walking on whitespace or another `@`.
2. If no `@` found in the walk, return null.
3. Reject if the `@` is preceded by a regex word char `[A-Za-z0-9_]` (so `email@host` doesn't fire — matches the `[$|\W]` boundary in the official AT Protocol handle regex).
4. The token is `text.substring(@-position + 1, cursor)`. If empty (cursor right after a bare `@`), return null — don't fire on naked `@`.
5. Otherwise, return the token (without the leading `@`).

Pure, no Compose, no coroutines — unit tests are JVM-only. Edge cases tested: bare `@`, single-char token, mid-word `@` after whitespace, email pattern, multi-byte UTF-8 in the token, cursor in the middle of an existing `@token`, cursor immediately after a complete `@handle.bsky.social` followed by a space (no token at cursor).

**Why "every snapshot" instead of a separate `SelectionChanged` event:** `snapshotFlow` already emits whenever text or selection changes — there is no extra event surface to invent. Tapping the cursor inside an existing `@token` (selection-only change) triggers detection just as typing a char does.

### 4. Typeahead pipeline: per-VM `MutableSharedFlow<String>` with debounce + `mapLatest`

The VM holds `private val queryFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)`. `handleTokenObservation` emits the new token (or signals "no active token" via a sentinel that drives state to `Idle` without emitting). The pipeline:

```kotlin
queryFlow
    .debounce(150.milliseconds)
    .distinctUntilChanged()
    .mapLatest { query ->
        setState { copy(typeahead = TypeaheadStatus.Querying(query)) }
        repo.searchTypeahead(query).fold(
            onSuccess = { actors ->
                if (actors.isEmpty()) TypeaheadStatus.NoResults(query)
                else TypeaheadStatus.Suggestions(query, actors.toImmutableList())
            },
            onFailure = { TypeaheadStatus.Idle /* bd issue: hide on error */ },
        )
    }
    .onEach { status -> setState { copy(typeahead = status) } }
    .launchIn(viewModelScope)
```

**Why these operators:**
- `debounce(150ms)` matches Bluesky's mobile clients' feel; 150ms is below the 200ms-or-so threshold where suggestions feel laggy and above the ~100ms threshold where every keystroke fires a query.
- `distinctUntilChanged` prevents firing on text changes that don't change the active token (e.g., typing `@a`, deleting, retyping — the token strings re-emit but the deduper drops repeats).
- `mapLatest` cancels in-flight slow queries when a new token arrives. Bounded concurrency by construction; never two concurrent queries.
- Errors collapse to `Idle` per the bd issue's "keep dropdown hidden on error rather than surfacing." A flapping connection during typeahead would be more annoying than helpful as a snackbar — and the mention will simply not auto-link if the user posts without picking a suggestion (same as today).

**Why a `SharedFlow` and not an in-VM channel:** `SharedFlow` matches the natural shape (one producer, one consumer collected by `launchIn(viewModelScope)`, replays nothing). A `Channel` would also work but `SharedFlow` is the project's house style.

### 5. Suggestion-row replacement semantics

On `TypeaheadResultClicked(actor)`:

1. Snapshot the current `textFieldState.text.toString()` and `textFieldState.selection.end`.
2. Locate the active `@`-position the same way the detector does. (If the position can't be re-located — concurrent edit — no-op.)
3. Replace the substring `[@-position, cursor)` with `@<actor.handle> ` (trailing space).
4. Set the cursor to the end of the inserted segment.
5. The next `snapshotFlow` emission will see no active token (whitespace boundary), set `typeahead = Idle`, and the dropdown disappears.

Done via `textFieldState.edit { replace(...) ; placeCursorBeforeCharAt(...) }` (the new `TextFieldBuffer` API). This is atomic from the IME's perspective — single edit, single cursor reposition, single recomposition.

**Why a trailing space:** every social client does this. Without it, the user types one more char and the new char appends to the canonical handle, immediately invalidating it. With it, the next char starts a new word.

**Why not insert without re-locating:** the user could have moved the cursor between the suggestion arriving and the tap. Re-locating defends against a stale insertion; if it can't be located, dropping the click is safer than corrupting the text.

### 6. UI rendering: inline `OutlinedCard` + `LazyColumn` with `heightIn(max = 240.dp)`

Rendered inside `ComposerScreenContent`'s `Column` between the `OutlinedTextField` and `ComposerAttachmentRow`, conditional on `state.typeahead is Suggestions || state.typeahead is NoResults`. (Querying is hidden — flicker on every-keystroke.) Structure:

```kotlin
OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
        when (val ts = state.typeahead) {
            is TypeaheadStatus.Suggestions -> itemsIndexed(ts.results, key = { _, a -> a.did }) { i, actor ->
                if (i > 0) HorizontalDivider()
                ComposerSuggestionRow(actor = actor, onClick = { onTypeaheadClick(actor) })
            }
            is TypeaheadStatus.NoResults -> item { ComposerSuggestionEmptyRow(query = ts.query) }
            else -> {}
        }
    }
}
```

`ComposerSuggestionRow` layout: `Row` with horizontal padding 12.dp, vertical 12.dp; `NubecitaAsyncImage` 40.dp circular avatar; `Spacer(8.dp)`; `Column { displayName (titleSmall, primary) ; @handle (bodySmall, onSurfaceVariant) }`.

**Why `OutlinedCard` not `Surface`/`Card`:** matches the screenshot from the official client (visible 1dp stroke). M3 Expressive's elevation tonal mapping under dark mode would otherwise blend into the composer surface.

**Why `key = { _, a -> a.did }`:** DIDs are stable across queries; using them as keys lets the `LazyColumn` survive re-emissions (same handle re-appearing after a typeahead refresh) without re-laying-out the row.

**Why hide on `Querying`:** rendering a "loading" state on every keystroke creates a flicker pattern that's worse than a brief stale list. Suggestions stay visible until the next response replaces them; new query just starts the debounce window again.

### 7. `ActorTypeaheadRepository` placement and shape

Lives in `:core:posting/ActorTypeaheadRepository.kt` (interface) and `:core:posting/internal/DefaultActorTypeaheadRepository.kt` (impl). The interface:

```kotlin
interface ActorTypeaheadRepository {
    suspend fun searchTypeahead(query: String): Result<List<ActorTypeaheadUi>>
}

@Stable
data class ActorTypeaheadUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
)
```

`DefaultActorTypeaheadRepository` injects the same `XrpcClientProvider` the rest of `:core:posting` uses, calls `ActorService.searchActorsTypeahead(SearchActorsTypeaheadRequest(q = query, limit = 8))`, and maps `ProfileViewBasic` → `ActorTypeaheadUi`. Failures wrap into `Result.failure(...)` — the VM's pipeline collapses any failure to `Idle`.

**Why `Result<List<...>>` instead of typed errors:** the typeahead has exactly one failure-handling strategy (hide). Distinguishing network from auth from server errors here adds taxonomy without consumer-side branching. If a future surface needs the distinction (e.g., a "typeahead unavailable" toast), promote to a sealed error type then.

**Why `:core:posting` not `:core:actor`:** the bd issue explicitly defers `:core:actor` until other actor-facing reads land. `HandleResolver` already lives in `:core:posting` and is morally adjacent. Co-located until volume justifies the split.

### 8. Migration of the existing reducer + grapheme counter

The `GraphemeCounter` logic moves from `handleTextChanged` to the `snapshotFlow` collector. `ComposerState.graphemeCount` and `ComposerState.isOverLimit` stay as VM-projected fields (not derived in the Composable) — so the existing `ComposerCharacterCounter` and `canPost` UI gates continue reading them unchanged. The screen reads `state.graphemeCount` exactly as today.

`OutlinedTextField`'s `enabled = state.submitStatus !is Submitting` is preserved — `TextFieldState` honors the field's `enabled` flag (no edits while submit is in flight).

## Risks / Trade-offs

- **VM holds Compose primitive (`TextFieldState`).** → The :feature:composer:impl module already depends on Compose Foundation; the VM already references `ImmutableList`. Adding `TextFieldState` is a small extension of that surface, not a new principle. Documented inline + in CLAUDE.md so it doesn't read as drift.
- **`snapshotFlow` semantics on disabled fields.** When `enabled = false` (mid-submit), the IME can't write to the field — but `snapshotFlow` still emits if anyone else mutates the state. → Confirmed in tests: nothing else mutates `textFieldState` during submit. The risk is theoretical; instrumented test will exercise the disabled-mid-submit case.
- **Snapshot-based grapheme counting on every char vs. the previous reducer-driven path.** Same work, same frequency — `snapshotFlow` collapses ≥1 mutation per snapshot frame, so this is at most as frequent as the previous path. → No expected perf regression. Microbench can confirm if needed.
- **`mapLatest` cancellation racing with snapshot ordering.** If user types `@al` → debounce expires → query starts; user types `i` → `mapLatest` cancels query for `@al`, starts query for `@ali`. If the cancelled query was about to set `Suggestions(@al, ...)`, it must NOT win after `Suggestions(@ali, ...)` lands. → `mapLatest` guarantees this — the upstream cancellation throws inside the suspending block, the `setState` on the cancelled branch never runs.
- **Token detection re-locating at insertion time.** If the user moves the cursor between suggestion-arrival and tap, the suggestion would otherwise insert at the wrong position. → Re-locate the `@`-position from the current snapshot at insertion time; if not found, drop the click. Documented in the spec.
- **Submit reads from `TextFieldState`, not state.** Two read sources (`uiState.value` for status fields + `textFieldState.text.toString()` for text) is more surface area than one. → The submit code path is small and tested; the alternative is mirroring text into state on every snapshot, which re-introduces the round-trip we just removed for a different reason.
- **Screenshot fixtures for the existing composer get re-baselined.** The `OutlinedTextField` migration may shift internal layout/padding by a pixel or two even with no UX change. → Re-baseline as part of the change; treat as expected churn, not regression.

## Migration Plan

This is a code-only change to a feature in active development. No data migration. No staged rollout. No feature flag — the change ships atomically when the PR merges.

Within the PR, the migration order is:

1. Land `ActorTypeaheadRepository` interface + implementation + tests in `:core:posting`. No consumer yet — verifies the SDK call independently.
2. Add `TypeaheadStatus`, `currentMentionToken`, and the new `TypeaheadResultClicked` event in `:feature:composer:impl/state` and `:feature:composer:impl/internal`. Tests for the helper land in this commit.
3. Migrate `ComposerViewModel` from `state.text` + `TextChanged` reducer to `TextFieldState` + `snapshotFlow` collector. Wire the typeahead pipeline. Update `ComposerViewModelTest` in the same commit (the old tests don't compile against the new contract).
4. Migrate `ComposerScreen` / `ComposerScreenContent` to the `TextFieldState` overload. Add the `OutlinedCard`/`LazyColumn` rendering. Update the screenshot fixtures (existing fixtures re-baseline; add the new typeahead fixtures).
5. Add the CLAUDE.md "MVI exceptions" subsection.

Each commit is independently reviewable; the PR squashes to a single Conventional Commit on merge. Rollback is `git revert` of the squash commit.

## Open Questions

- **CLAUDE.md edit scope.** The current CLAUDE.md MVI section is concrete. Should the exception note live as a new subsection beneath the existing block, or as an inline note inside the existing block? Default: new subsection so the existing rule reads cleanly first. Confirm during PR review if a different placement reads better.
- **Suggestion limit.** `searchActorsTypeahead` accepts `limit` 1–10, default unspecified by the lexicon. We'll request `limit = 8` to match Bluesky's mobile UX (visible ~3.5 with scroll for the rest). Confirm if 8 is the right number after manual testing — easy to tune.
- **Debounce window.** 150ms is a defensible default. If manual feel during the PR build suggests 100ms or 200ms reads better, tune in-PR; not worth re-spec'ing.
- **Existing screenshot fixture diffs.** Until the `TextFieldState` migration is built, we don't know whether existing composer fixtures need re-baselining or whether they're pixel-stable. Plan for re-baseline; verify in PR.
