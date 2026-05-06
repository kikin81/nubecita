## 1. `:core:posting` — `ActorTypeaheadRepository`

- [x] 1.1 Add `ActorTypeaheadUi(did, handle, displayName, avatarUrl)` `data class` in `:core:posting`. (No `@Stable` annotation — `:core:posting` is non-Compose; a data class of `String`/`String?` is implicitly Compose-stable.)
- [x] 1.2 Add `ActorTypeaheadRepository` interface with `suspend fun searchTypeahead(query: String): Result<List<ActorTypeaheadUi>>` in `:core:posting`.
- [x] 1.3 Add `DefaultActorTypeaheadRepository` implementation in `:core:posting/internal/`, calling `ActorService.searchActorsTypeahead(SearchActorsTypeaheadRequest(q = query, limit = 8))` via the existing `XrpcClientProvider.authenticated()`.
- [x] 1.4 Bind `ActorTypeaheadRepository → DefaultActorTypeaheadRepository` `@Singleton` in the existing `:core:posting` Hilt module (`PostingModule` or sibling).
- [x] 1.5 Unit test `ActorTypeaheadRepositoryTest` (Ktor MockEngine): asserts `q=…` and `limit=8` query params, asserts `term=` is absent, exercises happy-path mapping (with displayName + avatar), exercises blank-displayName-becomes-null, exercises null-avatar-becomes-null, asserts `IOException` surfaces as `Result.failure`.

## 2. `:feature:composer:impl` — token detection helper

- [x] 2.1 Add pure helper `currentMentionToken(text: CharSequence, cursor: Int): String?` in `:feature:composer:impl/internal/MentionTokenDetector.kt`.
- [x] 2.2 Unit test `MentionTokenDetectorTest`: bare `@`, single-char token, mid-word `@`, email-context rejection, multi-byte chars in token, cursor inside an existing token, cursor after a complete `@handle.bsky.social` followed by space, cursor at position 0, cursor when text contains no `@`.

## 3. `:feature:composer:impl` — typeahead state (additive only)

Each commit must compile. Removal of `text: String` and `TextChanged` happens atomically with the VM migration in task group 4 — removing them here would leave `ComposerViewModel` un-compilable between commits.

- [x] 3.1 Add `TypeaheadStatus` sealed interface in `:feature:composer:impl/state/TypeaheadStatus.kt` with `Idle`, `Querying(query)`, `Suggestions(query, results)`, `NoResults(query)` variants. `Suggestions.results` typed as `ImmutableList<ActorTypeaheadUi>`.
- [x] 3.2 Add `typeahead: TypeaheadStatus = TypeaheadStatus.Idle` field to `ComposerState` (additive — `text: String` stays for now and is removed in step 4).
- [x] 3.3 Add `TypeaheadResultClicked(actor: ActorTypeaheadUi)` variant to `ComposerEvent`. Add a no-op placeholder branch to `ComposerViewModel.handleEvent` so the sealed-when stays exhaustive; real handler lands in step 4.

## 4. `:feature:composer:impl` — `TextFieldState` migration in `ComposerViewModel`

- [x] 4.1 Add `val textFieldState: TextFieldState` to `ComposerViewModel` (constructed in init with `initialText = ""`).
- [x] 4.2 Inject `ActorTypeaheadRepository` into `ComposerViewModel`'s constructor at the END of the parameter list (preserves the append-only contract documented on the existing class). VM Kdoc updated to list four-deps.
- [x] 4.3 In `init`, launch a `snapshotFlow { textFieldState.text.toString() to textFieldState.selection.end }` collector that updates `state.graphemeCount` / `state.isOverLimit` and emits the active token (or "" sentinel) into a per-VM `MutableSharedFlow<String>`.
- [x] 4.4 Wire the typeahead pipeline: `queryFlow.debounce(150.milliseconds).distinctUntilChanged().mapLatest { repo.searchTypeahead(it) }.onEach { setState { copy(typeahead = …) } }.launchIn(viewModelScope)`. Map results → `Suggestions` / `NoResults`; map failures → `Idle`. Emit `Querying(query)` synchronously before invoking the repo. The empty-string sentinel resolves directly to `Idle` without invoking the repo.
- [x] 4.5 Implement `handleTypeaheadResultClicked(actor)`: snapshot text + selection, re-locate the active `@`-position via `findActiveMentionStart` (extracted from `currentMentionToken` so both share the walk), no-op if not found, otherwise `textFieldState.edit { replace(@-pos, cursor, "@${actor.handle} "); placeCursorBeforeCharAt(end-of-insertion) }`.
- [x] 4.6 Refactor `handleSubmit` to read text from `textFieldState.text.toString()` instead of `current.text`. `canSubmit(state)` → `canSubmit(state, text: String)`.
- [x] 4.7 Update `handleEvent` to dispatch `TypeaheadResultClicked` (replacing the no-op placeholder) and remove the `TextChanged` branch.
- [x] 4.8 Remove the `text: String` field from `ComposerState` and remove the `TextChanged(text: String)` variant from `ComposerEvent`.
- [x] 4.9 Update `ComposerViewModel` Kdoc with the documented MVI exception note + the append-only constructor contract update.

## 5. `:feature:composer:impl` — `ComposerScreen` migration + suggestion rendering

- [x] 5.1 Migrate the primary `OutlinedTextField` in `ComposerScreenContent` from `(value, onValueChange)` to `(state = vm.textFieldState)`. Pass `vm.textFieldState` from `ComposerScreen` into `ComposerScreenContent` as a parameter (so previews can pass an in-memory `TextFieldState`). `canPost` also takes `textFieldState` since it reads `text.isBlank()`.
- [x] 5.2 Remove the `onTextChange` lambda hoisting + the `TextChanged` event dispatch in `ComposerScreen`.
- [x] 5.3 Add `ComposerSuggestionList(typeahead, onSuggestionClick)` Composable in `:feature:composer:impl/internal/`: `OutlinedCard` containing a `LazyColumn(Modifier.heightIn(max = 240.dp))` with rows + `HorizontalDivider`. Renders only when `typeahead is Suggestions || typeahead is NoResults`.
- [x] 5.4 Add `ComposerSuggestionRow(actor, onClick)` Composable (private inside `ComposerSuggestionList.kt`): 40.dp circular `NubecitaAsyncImage` avatar + display name (titleSmall, falls back to bare handle when null) + `@handle` (bodySmall, onSurfaceVariant). Tap on the Row → `onClick(actor)`. Avatar `contentDescription = null` so the merged Row text doesn't double-announce.
- [x] 5.5 Add `ComposerSuggestionEmptyRow(query)` Composable (private) for the `NoResults` state — single row with localized "No matches for @{query}" copy.
- [x] 5.6 Wire `ComposerSuggestionList` into `ComposerScreenContent`'s `Column`, between the `OutlinedTextField` and `ComposerAttachmentRow`. Hoist `onSuggestionClick` lambda via `remember(viewModel)` in `ComposerScreen`. Pass through to `ComposerScreenContent` as a parameter; screenshot fixtures pass `{}`.
- [x] 5.7 Add string resource `composer_typeahead_no_matches` (with `%1$s` for the query) to `:feature:composer:impl/src/main/res/values/strings.xml`.

## 6. Tests — VM contract

- [x] 6.1 Migrate existing `ComposerViewModelTest` to the new `TextFieldState`-backed VM contract: tests mutate the VM's `textFieldState` via a `setComposerText(vm, text)` helper that calls `setTextAndPlaceCursorAtEnd(...)` then `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()` to flush the snapshot to the VM's collector (no Compose recomposer in tests). Drop assertions on `state.text` (gone); for the submit-snapshot test, drop the text-during-submit case (gated at the UI layer via `enabled = false`, not by the VM reducer) and keep the attachment-during-submit case.
- [x] 6.2 New `ComposerViewModelTypeaheadTest`: state transitions `Idle → Querying → Suggestions` after debounce; `Idle → Querying → NoResults` for empty actor list; `Idle` after repo failure (and no `ShowError` effect emitted); `mapLatest` cancellation when a new token arrives mid-query (uses a `CompletableDeferred`-gated `ControllableTypeaheadRepository` fake to orchestrate completion order); `distinctUntilChanged` drops duplicate consecutive tokens; `TypeaheadResultClicked` inserts canonical handle + trailing space + cursor after insertion; `TypeaheadResultClicked` no-op when `@`-position can't be re-located; replacement transitions back to `Idle` on the next snapshot.

## 7. Screenshot fixtures

- [x] 7.1 Re-baseline existing `ComposerScreenScreenshotTest` fixtures impacted by the `OutlinedTextField` migration. **No diffs** — the `OutlinedTextField` migration kept the rendering pixel-stable. All 8 existing baselines validated unchanged.
- [x] 7.2 Add typeahead-suggestions fixture (Light + Dark) — composer with 3 deterministic typeahead suggestions (Alice / Alex / Alvin) visible in the inline `OutlinedCard`. Avatars render as placeholder ColorPainters (null `avatarUrl` → `NubecitaAsyncImage` falls back to its standard placeholder). 2 new baselines under `screenshotTestDebug/reference/.../ComposerScreenScreenshotTestKt/ComposerScreenTypeaheadSuggestionsScreenshot_*`.

## 8. Docs + workflow

- [x] 8.1 Add a "Sanctioned MVI exception: editor surfaces own a Compose `TextFieldState`" subsection to `CLAUDE.md` (under the MVI conventions block) calling out the editor's `TextFieldState` ownership, the bounded scope of the exception, the test-side gotcha (`Snapshot.sendApplyNotifications()` + `runCurrent()`), and the reference implementation.
- [x] 8.2 Pre-PR: ran `./gradlew :feature:composer:impl:testDebugUnitTest :core:posting:testDebugUnitTest :feature:composer:impl:validateDebugScreenshotTest spotlessCheck lint`. All green; the 5 lint warnings are pre-existing (NewerVersionAvailable, ObsoleteSdkInt on mipmap-anydpi-v26).
- [ ] 8.3 Open PR with body containing `Closes: nubecita-k19` and a short note linking to `openspec/changes/add-composer-mention-typeahead/proposal.md`.
