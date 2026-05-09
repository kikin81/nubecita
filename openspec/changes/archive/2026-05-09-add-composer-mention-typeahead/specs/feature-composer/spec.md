## ADDED Requirements

### Requirement: Composer text input is owned by Compose `TextFieldState`

The system SHALL hold the canonical composer text in a `androidx.compose.foundation.text.input.TextFieldState` exposed by `ComposerViewModel` as a public `val textFieldState: TextFieldState`. `ComposerScreen` MUST consume the field via the `OutlinedTextField(state = vm.textFieldState, ...)` overload from Compose Foundation 1.7+. The legacy `value: String` / `onValueChange: (String) -> Unit` overload SHALL NOT be used for the composer's text input. `ComposerState` SHALL NOT contain a `text: String` field; `ComposerEvent` SHALL NOT contain a `TextChanged(text: String)` variant. The IME's writes to the field MUST NOT round-trip through `MviViewModel.handleEvent` or `MviViewModel.setState`.

#### Scenario: Screen wires TextFieldState directly

- **WHEN** `ComposerScreenContent` renders its primary `OutlinedTextField`
- **THEN** the call SHALL pass `state = viewModel.textFieldState` and SHALL NOT pass `value` / `onValueChange` parameters

#### Scenario: TextChanged event is removed from the contract

- **WHEN** `ComposerEvent` is searched for variants matching the substring "TextChanged"
- **THEN** there SHALL be zero matches

#### Scenario: Composer state does not mirror the text

- **WHEN** `ComposerState` is inspected for fields named `text` of type `String` or `CharSequence`
- **THEN** there SHALL be zero matches

### Requirement: ViewModel observes `TextFieldState` via `snapshotFlow`

`ComposerViewModel` MUST observe `textFieldState` via `snapshotFlow { textFieldState.text.toString() to textFieldState.selection }` collected in `viewModelScope` from `init`. The collector SHALL drive both: (a) the grapheme counter (`state.graphemeCount`, `state.isOverLimit`); and (b) the typeahead pipeline (active `@`-token detection and the downstream query flow). No other path SHALL mutate `state.graphemeCount` or `state.isOverLimit`.

#### Scenario: Grapheme counter updates from the snapshot collector

- **WHEN** the user types a character into the composer
- **THEN** `state.graphemeCount` reflects `GraphemeCounter.count(textFieldState.text.toString())` after the next snapshot frame, with no `ComposerEvent` dispatched

#### Scenario: Submit reads from TextFieldState

- **WHEN** `ComposerViewModel.handleSubmit` constructs the create-post call
- **THEN** the `text` argument SHALL be sourced from `textFieldState.text.toString()` AND NOT from any field on `ComposerState`

### Requirement: Active mention token is detected by a pure helper

The system SHALL expose `net.kikin.nubecita.feature.composer.impl.internal.currentMentionToken(text: CharSequence, cursor: Int): String?` returning the active mention token at the cursor position, without the leading `@`, or `null` when no token is active. The function MUST be pure (no Compose, no coroutines, no I/O). The function MUST treat the following as "no active token": (a) cursor is at position 0 or no `@` precedes the cursor before a whitespace/second-`@` boundary; (b) the candidate `@` is preceded by a regex word char `[A-Za-z0-9_]` (email-like context — matches the `[$|\W]` boundary in the official AT Protocol handle regex); (c) the substring between the `@` and the cursor is empty (cursor immediately after a bare `@`). The walk-back from `cursor - 1` toward `0` MUST stop on whitespace or a second `@` — those characters terminate the candidate token.

#### Scenario: Cursor after a single-character token

- **WHEN** `currentMentionToken("@a", 2)` is called
- **THEN** it SHALL return `"a"`

#### Scenario: Cursor after a bare `@`

- **WHEN** `currentMentionToken("@", 1)` is called
- **THEN** it SHALL return `null`

#### Scenario: Cursor inside an email-like context

- **WHEN** `currentMentionToken("hi alice@host.com", 17)` is called
- **THEN** it SHALL return `null`

#### Scenario: Cursor in the middle of an existing token

- **WHEN** `currentMentionToken("@alice.bsky.social trailing", 6)` is called
- **THEN** it SHALL return `"alice"`

#### Scenario: Cursor after a complete handle followed by a space

- **WHEN** `currentMentionToken("@alice.bsky.social ", 19)` is called
- **THEN** it SHALL return `null`

#### Scenario: Multi-byte token characters

- **WHEN** `currentMentionToken("@aliçe", 6)` is called
- **THEN** it SHALL return `"aliçe"` (token detection is character-based, not byte-based)

### Requirement: Typeahead state is a sealed status sum on `ComposerState`

`ComposerState` SHALL contain a non-null field `typeahead: TypeaheadStatus` defaulting to `TypeaheadStatus.Idle`. `TypeaheadStatus` MUST be a `sealed interface` in `:feature:composer:impl/state` with exactly these variants:

- `data object Idle : TypeaheadStatus`
- `data class Querying(val query: String) : TypeaheadStatus`
- `data class Suggestions(val query: String, val results: ImmutableList<ActorTypeaheadUi>) : TypeaheadStatus`
- `data class NoResults(val query: String) : TypeaheadStatus`

Additional internal states (e.g., transient errors) MUST collapse to `Idle` before being assigned to `state.typeahead`.

#### Scenario: Initial state is Idle

- **WHEN** `ComposerViewModel` is constructed
- **THEN** `state.typeahead` SHALL equal `TypeaheadStatus.Idle`

#### Scenario: Typeahead pipeline transition through Querying → Suggestions

- **WHEN** the user types `@a`, the debounce window expires, the repository returns one or more actors
- **THEN** `state.typeahead` SHALL transition `Idle → Querying("a") → Suggestions("a", [...])` in that order

#### Scenario: Typeahead returns NoResults on empty actor list

- **WHEN** the typeahead repository returns an empty `actors` list for query `"zzz"`
- **THEN** `state.typeahead` SHALL equal `TypeaheadStatus.NoResults("zzz")`

#### Scenario: Typeahead returns Idle on repository failure

- **WHEN** the typeahead repository returns `Result.failure(...)` for any query
- **THEN** `state.typeahead` SHALL equal `TypeaheadStatus.Idle` AND no `ComposerEffect.ShowError` SHALL be emitted

### Requirement: Typeahead pipeline uses debounce + distinctUntilChanged + mapLatest

`ComposerViewModel` MUST drive the typeahead lookup from a per-VM `MutableSharedFlow<String>` with operators `.debounce(150.milliseconds).distinctUntilChanged().mapLatest { repo.searchTypeahead(it) }` collected via `launchIn(viewModelScope)`. `mapLatest` SHALL ensure that an in-flight slow query is cancelled when a newer token arrives.

#### Scenario: mapLatest cancels in-flight queries

- **GIVEN** the user has typed `@al` and the typeahead repository is suspended on the resulting query
- **WHEN** the user types `i` (active token becomes `ali`) and the debounce window for `ali` expires
- **THEN** the suspended `searchTypeahead("al")` SHALL be cancelled, and only the result for `"ali"` SHALL be assigned to `state.typeahead`

#### Scenario: distinctUntilChanged drops duplicate consecutive tokens

- **GIVEN** the active token is `"a"` and `state.typeahead = Suggestions("a", [...])`
- **WHEN** the user deletes a character and immediately retypes the same character (active token returns to `"a"`)
- **THEN** the repository SHALL NOT be called a second time for `"a"`

### Requirement: Selecting a suggestion atomically replaces the active token

The system SHALL provide `ComposerEvent.TypeaheadResultClicked(actor: ActorTypeaheadUi)`. When dispatched, `ComposerViewModel` MUST:

1. Snapshot `textFieldState.text.toString()` and `textFieldState.selection.end`.
2. Locate the active `@`-position via the same logic `currentMentionToken` uses.
3. If the `@`-position cannot be located (concurrent edit raced the click), the event SHALL be a no-op.
4. Otherwise, the substring `[@-position, cursor)` SHALL be replaced with `@<actor.handle> ` (trailing space) via a single `textFieldState.edit { replace(...); placeCursorBeforeCharAt(end-of-insertion) }` block.
5. The next `snapshotFlow` emission SHALL drive `state.typeahead` to `Idle` (the helper sees the trailing whitespace boundary and returns `null`).

#### Scenario: Replacement inserts canonical handle with trailing space

- **GIVEN** the composer text is `"hi @al"` with cursor at position 6
- **WHEN** `TypeaheadResultClicked(ActorTypeaheadUi(handle = "alice.bsky.social", ...))` is dispatched
- **THEN** the field's text SHALL become `"hi @alice.bsky.social "` and the cursor SHALL be at position 22

#### Scenario: Replacement is a no-op when the @-position cannot be re-located

- **GIVEN** between suggestion-arrival and click, the user moved the cursor outside the original `@token`
- **WHEN** `TypeaheadResultClicked(...)` is dispatched
- **THEN** the field's text SHALL be unchanged

#### Scenario: Typeahead returns to Idle after replacement

- **WHEN** a `TypeaheadResultClicked(...)` is processed and the replacement applied
- **THEN** `state.typeahead` SHALL equal `TypeaheadStatus.Idle` after the next snapshot emission

### Requirement: Suggestion list renders inline above the IME

`ComposerScreenContent` MUST render the typeahead suggestions inline in the composer's primary `Column`, between the `OutlinedTextField` and the `ComposerAttachmentRow`, **only** when `state.typeahead` is `Suggestions` or `NoResults`. The container SHALL be a Material 3 `OutlinedCard` filling the available width with a `LazyColumn` of `Modifier.heightIn(max = 240.dp)`. Each suggestion row SHALL display an `NubecitaAsyncImage` 40.dp circular avatar, the `displayName` (or the `handle` when displayName is null) styled `MaterialTheme.typography.titleSmall`, and the `@<handle>` styled `MaterialTheme.typography.bodySmall` with `MaterialTheme.colorScheme.onSurfaceVariant`. Rows SHALL be separated by `HorizontalDivider`. The `LazyColumn` items SHALL use `actor.did` as the stable key. The container SHALL NOT render in `Idle` or `Querying` states.

#### Scenario: Suggestions visible only in Suggestions or NoResults

- **WHEN** `state.typeahead` is `Idle` or `Querying(...)`
- **THEN** the typeahead container SHALL NOT be present in the composition

#### Scenario: Suggestion rows tap dispatches TypeaheadResultClicked

- **WHEN** a suggestion row is tapped
- **THEN** the host MUST dispatch `ComposerEvent.TypeaheadResultClicked(actor)` with the actor backing the tapped row

### Requirement: Typeahead does not interact with the submit lifecycle

The submit lifecycle (`ComposerSubmitStatus`) SHALL be independent of `TypeaheadStatus`. While `state.submitStatus is Submitting`, the `OutlinedTextField`'s `enabled = false` flag SHALL prevent further IME edits and therefore further snapshot emissions; the typeahead state at the moment submit started SHALL persist until the next user-driven snapshot change. Submit MUST NOT clear `state.typeahead`.

#### Scenario: Typeahead state persists across submit

- **GIVEN** `state.typeahead` is `Suggestions(...)` and the user taps Post
- **WHEN** submit transitions through `Submitting` and resolves to `Success`
- **THEN** the navigation back / `OnSubmitSuccess` effect SHALL fire regardless of the prior typeahead state, and the screen tear-down SHALL not be gated by `typeahead`

## MODIFIED Requirements

### Requirement: `ComposerViewModel` is the canonical presenter

The system SHALL expose `net.kikin.nubecita.feature.composer.impl.ComposerViewModel` as the only `ViewModel` for the composer screen. It MUST extend `MviViewModel<ComposerState, ComposerEvent, ComposerEffect>`, MUST be `@HiltViewModel(assistedFactory = ComposerViewModel.Factory::class)`-annotated, and MUST receive its `route: ComposerRoute` through Hilt **assisted injection** — the canonical Nav3 pattern in this codebase, mirroring `:feature:postdetail:impl`'s `PostDetailViewModel.Factory`. The screen Composable MUST consume the VM via `hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(creationCallback = { it.create(route) })`; no other class in the project SHALL instantiate or extend `ComposerViewModel`. The VM additionally exposes `val textFieldState: TextFieldState` as the canonical text source for the composer's primary input — the field is constructed in the VM's init block and observed via `snapshotFlow` to drive both the grapheme counter and the typeahead pipeline. Process death survival is **explicitly out of V1** — no `SavedStateHandle` plumbing for state persistence; the in-memory `TextFieldState` is lost on process death along with the rest of the composer's working state. The `:core:drafts` follow-up addresses non-empty drafts surviving via disk persistence.

#### Scenario: Screen consumes ComposerViewModel via assisted injection

- **WHEN** `ComposerScreen` composes
- **THEN** it obtains `ComposerViewModel` via `hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(creationCallback = { factory -> factory.create(route) })` and forwards `ComposerEvent`s through `viewModel::handleEvent`

#### Scenario: replyToUri reaches the VM via the assisted route

- **WHEN** navigation pushes `ComposerRoute(replyToUri = "at://did:plc:abc/app.bsky.feed.post/xyz")`
- **THEN** `ComposerViewModel.Factory.create(route)` constructs the VM with the assisted `route` parameter, and `state.replyToUri == "at://did:plc:abc/app.bsky.feed.post/xyz"` on first emission

#### Scenario: Screen wires textFieldState into OutlinedTextField

- **WHEN** `ComposerScreen` composes the primary text input
- **THEN** the `OutlinedTextField` call SHALL pass `state = viewModel.textFieldState` (no `value` / `onValueChange` parameters)
