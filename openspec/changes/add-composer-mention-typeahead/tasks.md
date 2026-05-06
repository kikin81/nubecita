## 1. `:core:posting` — `ActorTypeaheadRepository`

- [ ] 1.1 Add `ActorTypeaheadUi(did, handle, displayName, avatarUrl)` `@Stable data class` in `:core:posting`.
- [ ] 1.2 Add `ActorTypeaheadRepository` interface with `suspend fun searchTypeahead(query: String): Result<List<ActorTypeaheadUi>>` in `:core:posting`.
- [ ] 1.3 Add `DefaultActorTypeaheadRepository` implementation in `:core:posting/internal/`, calling `ActorService.searchActorsTypeahead(SearchActorsTypeaheadRequest(q = query, limit = 8))` via the existing `XrpcClientProvider.authenticated()`.
- [ ] 1.4 Bind `ActorTypeaheadRepository → DefaultActorTypeaheadRepository` `@Singleton` in the existing `:core:posting` Hilt module (`PostingModule` or sibling).
- [ ] 1.5 Unit test `ActorTypeaheadRepositoryTest` (Ktor MockEngine): asserts `q=…` and `limit=8` query params, asserts `term=` is absent, exercises happy-path mapping (with displayName + avatar), exercises blank-displayName-becomes-null, exercises null-avatar-becomes-null, asserts `IOException` surfaces as `Result.failure`.

## 2. `:feature:composer:impl` — token detection helper

- [ ] 2.1 Add pure helper `currentMentionToken(text: CharSequence, cursor: Int): String?` in `:feature:composer:impl/internal/MentionTokenDetector.kt`.
- [ ] 2.2 Unit test `MentionTokenDetectorTest`: bare `@`, single-char token, mid-word `@`, email-context rejection, multi-byte chars in token, cursor inside an existing token, cursor after a complete `@handle.bsky.social` followed by space, cursor at position 0, cursor when text contains no `@`.

## 3. `:feature:composer:impl` — typeahead state

- [ ] 3.1 Add `TypeaheadStatus` sealed interface in `:feature:composer:impl/state/TypeaheadStatus.kt` with `Idle`, `Querying(query)`, `Suggestions(query, results)`, `NoResults(query)` variants. `Suggestions.results` typed as `ImmutableList<ActorTypeaheadUi>`.
- [ ] 3.2 Add `typeahead: TypeaheadStatus = TypeaheadStatus.Idle` field to `ComposerState`.
- [ ] 3.3 Add `TypeaheadResultClicked(actor: ActorTypeaheadUi)` variant to `ComposerEvent`.
- [ ] 3.4 Remove `text: String` field from `ComposerState`. Remove `TextChanged(text: String)` variant from `ComposerEvent`. (Both will be re-sourced from `TextFieldState` in step 4.)

## 4. `:feature:composer:impl` — `TextFieldState` migration in `ComposerViewModel`

- [ ] 4.1 Add `val textFieldState: TextFieldState` to `ComposerViewModel` (constructed in init with `initialText = ""`).
- [ ] 4.2 Inject `ActorTypeaheadRepository` into `ComposerViewModel`'s constructor (after `PostingRepository`, before `ParentFetchSource` to keep the append-only contract; if order conflicts with the documented contract, append at the end and update the contract note in the VM Kdoc to reflect the new fixed order).
- [ ] 4.3 In `init`, launch a `snapshotFlow { textFieldState.text.toString() to textFieldState.selection }` collector that updates `state.graphemeCount` / `state.isOverLimit` and emits the active token (or sentinel) into a per-VM `MutableSharedFlow<String>`.
- [ ] 4.4 Wire the typeahead pipeline: `queryFlow.debounce(150.milliseconds).distinctUntilChanged().mapLatest { repo.searchTypeahead(it) }.onEach { setState { copy(typeahead = …) } }.launchIn(viewModelScope)`. Map results → `Suggestions` / `NoResults`; map failures → `Idle`. Emit `Querying(query)` synchronously before invoking the repo.
- [ ] 4.5 Implement `handleTypeaheadResultClicked(actor)`: snapshot text + selection, re-locate the active `@`-position via the same logic as `currentMentionToken`, no-op if not found, otherwise `textFieldState.edit { replace(@-pos, cursor, "@${actor.handle} "); placeCursorBeforeCharAt(end-of-insertion) }`.
- [ ] 4.6 Refactor `handleSubmit` to read text from `textFieldState.text.toString()` instead of `current.text`. Update `canSubmit(state)` → `canSubmit(state, text: String)`.
- [ ] 4.7 Update `handleEvent` to dispatch `TypeaheadResultClicked` and remove the `TextChanged` branch.
- [ ] 4.8 Update `ComposerViewModel` Kdoc with the documented MVI exception note + the append-only constructor contract update.

## 5. `:feature:composer:impl` — `ComposerScreen` migration + suggestion rendering

- [ ] 5.1 Migrate the primary `OutlinedTextField` in `ComposerScreenContent` from `(value, onValueChange)` to `(state = vm.textFieldState)`. Pass `vm.textFieldState` from `ComposerScreen` into `ComposerScreenContent` as a parameter (so previews can pass an in-memory `TextFieldState`).
- [ ] 5.2 Remove the `onTextChange` lambda hoisting + the `TextChanged` event dispatch in `ComposerScreen`.
- [ ] 5.3 Add `ComposerSuggestionList(state.typeahead, onSuggestionClick)` Composable in `:feature:composer:impl/internal/`: `OutlinedCard` containing a `LazyColumn(Modifier.heightIn(max = 240.dp))` with rows + `HorizontalDivider`. Renders only when `state.typeahead is Suggestions || state.typeahead is NoResults`.
- [ ] 5.4 Add `ComposerSuggestionRow(actor, onClick)` Composable: 40.dp circular `NubecitaAsyncImage` avatar + display name (titleSmall) + `@handle` (bodySmall, onSurfaceVariant). Tap → `onClick(actor)`.
- [ ] 5.5 Add `ComposerSuggestionEmptyRow(query)` Composable for the `NoResults` state — single row with localized "No matches for @{query}" copy.
- [ ] 5.6 Wire `ComposerSuggestionList` into `ComposerScreenContent`'s `Column`, between the `OutlinedTextField` and `ComposerAttachmentRow`. Hoist `onSuggestionClick` lambda via `remember(viewModel)` in `ComposerScreen`.
- [ ] 5.7 Add string resources `composer_typeahead_no_matches` (with `%1$s` for the query) to `:feature:composer:impl/src/main/res/values/strings.xml`.

## 6. Tests — VM contract

- [ ] 6.1 Migrate existing `ComposerViewModelTest` to the new `TextFieldState`-backed VM contract: tests now exercise the VM by mutating its `textFieldState` (via `textFieldState.edit { ... }`) instead of dispatching `TextChanged`. Use `runTest` + `UnconfinedTestDispatcher` per the repo's testing convention. Verify grapheme counter / `isOverLimit` updates fire after `runCurrent()`.
- [ ] 6.2 New `ComposerViewModelTypeaheadTest`: state transitions `Idle → Querying → Suggestions` after debounce; `Idle → Querying → NoResults` for empty actor list; `Idle` after repo failure (and no `ShowError` effect emitted); `mapLatest` cancellation when a new token arrives mid-query (use a `Channel`-backed fake to control completion order); `distinctUntilChanged` drops duplicate consecutive tokens; `TypeaheadResultClicked` inserts canonical handle + trailing space + cursor after insertion; `TypeaheadResultClicked` no-op when `@`-position can't be re-located.

## 7. Screenshot fixtures

- [ ] 7.1 Re-baseline existing `ComposerScreenScreenshotTest` fixtures impacted by the `OutlinedTextField` migration. Run `./gradlew :feature:composer:impl:updateDebugScreenshotTest` and review the diff in PR.
- [ ] 7.2 Add `composer_typeahead_suggestions_visible` fixture (Light + Dark) — composer with 3 deterministic typeahead suggestions visible in the dropdown.

## 8. Docs + workflow

- [ ] 8.1 Add a short "MVI exceptions" subsection to `CLAUDE.md` (under the existing MVI conventions block) calling out the editor's `TextFieldState` ownership exception and the rationale.
- [ ] 8.2 Pre-PR: run `./gradlew :feature:composer:impl:testDebugUnitTest :core:posting:testDebugUnitTest :feature:composer:impl:validateDebugScreenshotTest spotlessCheck lint` and ensure all green.
- [ ] 8.3 Open PR with body containing `Closes: nubecita-k19` and a short note linking to `openspec/changes/add-composer-mention-typeahead/proposal.md`.
