## Why

The unified composer's submit-time facet extractor (`nubecita-wtq.11`) only resolves `@handle.bsky.social` strings that match exactly — typos silently drop the facet, leaving the `@`-prefixed text inert in the published record. Without an in-composer affordance, the only signal that a mention failed is the missing blue link in the user's own feed after posting. Bluesky, Threads, and X all solve this with an autocomplete dropdown the moment the user types `@`. Without it, mentions in Nubecita are typo-fragile by construction; with it, every accepted suggestion is guaranteed-resolvable at submit time.

This change is also the right moment to migrate the composer's text input to Compose 1.7+ `TextFieldState`. The existing pattern — VM holds `state.text: String`, every keystroke round-trips through `setState{}` — works only because the reducer is currently trivial. Adding cursor-aware token detection plus a debounced query trigger to that round-trip is the canonical recipe for the cursor-jump bug Compose's value/onValueChange API has had since day one. Migrating now (when the composer is small and well-tested) costs less than retrofitting later, and unblocks a class of future text-input features (selection-based link insertion, typed-character formatting) that would otherwise re-pay this debt.

## What Changes

### Text-input foundation (architectural shift)

- **BREAKING for the composer's internal API** (no public consumers): migrate `OutlinedTextField(value = state.text, onValueChange = ...)` to the Compose 1.7+ `OutlinedTextField(state = vm.textFieldState, ...)` overload. `ComposerState.text: String` is removed; the canonical text lives in a `TextFieldState` held by `ComposerViewModel` as a public `val`. `ComposerEvent.TextChanged(text: String)` is removed. The VM observes via `snapshotFlow { textFieldState.text }` inside `init`, drives the existing grapheme counter from that flow, and feeds the new typeahead pipeline.
- The IME never round-trips through the VM reducer. Cursor preservation is by construction, not by careful reducer design.
- Submit reads from `textFieldState.text.toString()` at the moment of `Submit`, not from a mirrored VM field.

### Typeahead surface (the new capability)

- New `ActorTypeaheadRepository` in `:core:posting` wrapping `app.bsky.actor.searchActorsTypeahead` (atproto-kotlin 5.3.1: `ActorService.searchActorsTypeahead`, `q` parameter — not the deprecated `term`). Maps `ProfileViewBasic` → a small UI-shaped `ActorTypeaheadUi(did, handle, displayName, avatarUrl)`.
- New `ComposerState.typeahead: TypeaheadStatus` sealed sum: `Idle` / `Querying(query)` / `Suggestions(query, results)` / `NoResults(query)` / `Error(query)`. Mutually exclusive variants per the repo's "sealed status sum" convention.
- New `ComposerEvent.TypeaheadResultClicked(actor: ActorTypeaheadUi)` — replaces the in-progress `@token` substring with `@<canonical-handle> ` (trailing space), placing the cursor after the inserted handle. Token detection then sees no active token and the typeahead returns to `Idle`.
- New pure helper `currentMentionToken(text: CharSequence, cursor: Int): String?` — token detection logic, unit-testable in isolation. Lives in `:feature:composer:impl/internal/`.
- Inline `LazyColumn` in `ComposerScreenContent`, rendered between the text field and the attachment row when `state.typeahead is Suggestions`. Bordered M3 `OutlinedCard` container, dividers between rows, `Modifier.heightIn(max = ~240.dp)` to cap at ~3.5 visible rows. Each row: `NubecitaAsyncImage` avatar (40dp circular) + display name + `@handle`. Tap dispatches `TypeaheadResultClicked`.
- Per-VM `MutableSharedFlow<String>` driving the lookup pipeline: `.debounce(150ms).distinctUntilChanged().mapLatest { repo.searchTypeahead(it) }`. `.mapLatest` cancels in-flight slow queries when the user types another character — bounded concurrency by construction.
- Submit lifecycle is unchanged. Typeahead is purely an authoring assist; the post-record source of truth is still the submitted text. `nubecita-wtq.11`'s extractor parses the final text and produces facets; this change just ensures the inserted handles are canonical.

### Non-goals (V1 of typeahead)

- **Hashtag typeahead** (`#topic`). The lexicon supports `app.bsky.richtext.facet#tag`; tag autocomplete is a separate UX surface — defer to its own change.
- **Pre-submit styled rendering** of mentions inside the editable text field (e.g., `@alice` colored blue while typing). Compose's editable styled-runs are non-trivial and visually noisy mid-typing; keep V1 plain text. The screenshot of the official client renders styled mentions, but that's a polish layer we can add later without re-architecting the typeahead.
- **Manual override** to disable auto-link on a token. Future polish.
- **URL typeahead.** URLs aren't fuzzy-matched.
- **Hardware-keyboard navigation** (arrow keys / Tab) of the suggestion list. V1 is touch-only; D-pad/keyboard support can layer on later via `Modifier.onKeyEvent`.
- **Saving the typeahead pipeline state across process death.** Same posture as the composer at large — the existing `:core:drafts` follow-up addresses non-empty drafts.

### Deviation from baselines

- **MVI baseline:** the VM no longer owns the canonical text. This is a deliberate, targeted exception for the editor surface — required to eliminate IME round-trip lag — and does NOT generalize to other screens. The `:feature:composer:impl` package gains a documented inline note; CLAUDE.md's MVI section is augmented (see `## Impact`) to call this exception out so it doesn't read as drift.
- No new third-party deps. Uses existing `atproto-kotlin` SDK, Compose Foundation 1.7+ (already on classpath via Compose BOM 2026.04.01), and the existing `NubecitaAsyncImage` from `:designsystem`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `feature-composer`: Adds the typeahead state machine, event, effect mapping, screen rendering of the inline `LazyColumn`, and the `TextFieldState`-based input foundation. Removes `ComposerState.text: String` and `ComposerEvent.TextChanged` from the canonical contract.
- `core-posting`: Adds the `ActorTypeaheadRepository` interface + `DefaultActorTypeaheadRepository` implementation backed by `ActorService.searchActorsTypeahead`. Defines the `ActorTypeaheadUi` value class as the boundary type returned to consumers.

## Impact

- **Code**: New files in `:feature:composer:impl` (typeahead state, event variant, effect variant, token-detection helper, `ComposerSuggestionList` Composable, `ComposerSuggestionRow` Composable). New files in `:core:posting` (`ActorTypeaheadRepository` interface, `DefaultActorTypeaheadRepository`, `ActorTypeaheadUi`). Migration in `ComposerViewModel`, `ComposerScreen`, `ComposerScreenContent`, `ComposerState`, `ComposerEvent`. Test files updated in step.
- **APIs**: New `ActorTypeaheadRepository` Kotlin interface in `:core:posting`. New `ActorTypeaheadUi` value class. Existing `PostingRepository` is unchanged. `ComposerEvent` loses `TextChanged`, gains `TypeaheadResultClicked`. `ComposerState` loses `text` and `graphemeCount` fields driven by the old reducer; both become derived from the `TextFieldState` snapshot. (Naming the latter as a state field is unchanged from the consumer's perspective — the screen still reads `state.graphemeCount` — but it's now sourced from the `snapshotFlow`.)
- **Dependencies**: No new third-party deps. Adds `androidx.compose.foundation:foundation` (already pulled by the Compose BOM, but verified for `BasicTextField`/`OutlinedTextField` `TextFieldState` overload availability).
- **Permissions**: None new. `searchActorsTypeahead` is unauthenticated per the lexicon but is routed through the same `XrpcClientProvider.authenticated()` for consistency, so no manifest changes.
- **Build**: No new modules. No convention-plugin changes.
- **Testing**:
  - Unit tests: `CurrentMentionTokenTest` (pure helper), `ComposerViewModelTypeaheadTest` (state transitions, replacement insertion, `mapLatest` cancellation, NoResults vs empty Suggestions), `ActorTypeaheadRepositoryTest` (Ktor MockEngine, `q` wiring, `ProfileViewBasic` → `ActorTypeaheadUi` mapping). Existing `ComposerViewModelTest` is migrated to the new `TextFieldState`-based VM contract.
  - Screenshot fixtures: composer with 3 deterministic typeahead suggestions visible (Light + Dark = 2 fixtures). Existing composer fixtures are re-baselined where the migration shifts pixels.
- **Docs**: Add a short `MVI exception` section to `CLAUDE.md` calling out the editor's text-state ownership departure so it's discoverable from the repo entry doc.
- **Out-of-scope cleanup**: None.
