# feature-composer Specification

## Purpose
The unified post composer (`:feature:composer:api` + `:impl`) for new posts and replies: an MVI `ComposerViewModel` over `TextFieldState`, flat UI-ready `ComposerState`, and sealed status sums for the submit / parent-fetch / typeahead / external-link lifecycles. Covers text entry with a 300-grapheme limit, image attachments via the system picker, `@`-mention typeahead, per-post language + audience, quoted posts, **paste-a-link external preview cards** (CardyB), adaptive full-screen/dialog hosting, and parallel blob upload on submit. Renders as an `@MainShell` Nav3 entry.
## Requirements
### Requirement: `:feature:composer:api` exposes exactly one `NavKey`

The system SHALL expose `net.kikin.nubecita.feature.composer.api.ComposerRoute` as the sole `NavKey` for the composer capability: `data class ComposerRoute(val replyToUri: String? = null, val quotePostUri: String? = null, val mentionHandle: String? = null, val sharedText: String? = null, val sharedImageUri: String? = null) : NavKey`. Fields use `String?` rather than `AtUri`. The `:api` module MUST NOT contain UI components, ViewModels, or atproto SDK dependencies.

#### Scenario: Single NavKey for both modes

- **WHEN** the `:feature:composer:api` source tree is searched for types implementing `androidx.navigation3.runtime.NavKey`
- **THEN** the only match SHALL be `ComposerRoute`

#### Scenario: API module has no UI dependencies

- **WHEN** `:feature:composer:api`'s `build.gradle.kts` is inspected
- **THEN** the `dependencies { }` block SHALL declare `androidx.navigation3.runtime` (the module exporting `NavKey`) and `kotlinx.serialization.json` as `api` deps, and SHALL NOT depend on Compose, Hilt, or `:feature:composer:impl`. The module does not need an `AtUri` dependency because every AT-URI-bearing field is typed `String?`, not the SDK's `AtUri`.

#### Scenario: Existing composer entry points unaffected

- **WHEN** the composer is opened from the feed FAB, a reply, a quote, or a mention (no shared params)
- **THEN** behavior is identical to before this change; `sharedText` / `sharedImageUri` are null and ignored.

### Requirement: `ComposerViewModel` is the canonical presenter

The system SHALL expose `net.kikin.nubecita.feature.composer.impl.ComposerViewModel` as the only `ViewModel` for the composer screen. It MUST extend `MviViewModel<ComposerState, ComposerEvent, ComposerEffect>`, be `@HiltViewModel`-annotated with assisted factory `ComposerViewModel.Factory`, and receive `route: ComposerRoute` via assisted injection. It MUST expose `val textFieldState: TextFieldState` as the canonical text input source.

#### Scenario: Screen consumes ComposerViewModel via assisted injection

- **WHEN** `ComposerScreen` composes
- **THEN** it obtains `ComposerViewModel` via `hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(creationCallback = { factory -> factory.create(route) })` and forwards `ComposerEvent`s through `viewModel::handleEvent`

#### Scenario: replyToUri reaches the VM via the assisted route

- **WHEN** navigation pushes `ComposerRoute(replyToUri = "at://did:plc:abc/app.bsky.feed.post/xyz")`
- **THEN** `ComposerViewModel.Factory.create(route)` constructs the VM with the assisted `route` parameter, and `state.replyToUri == "at://did:plc:abc/app.bsky.feed.post/xyz"` on first emission

#### Scenario: Screen wires textFieldState into OutlinedTextField

- **WHEN** `ComposerScreen` composes the primary text input
- **THEN** the `OutlinedTextField` call SHALL pass `state = viewModel.textFieldState` (no `value` / `onValueChange` parameters)

### Requirement: `ComposerState` carries count, attachments, and submit status as flat UI-ready fields

The system SHALL expose `ComposerState` implementing `UiState` with flat UI-ready fields: `graphemeCount: Int`, `isOverLimit: Boolean` (`graphemeCount > 300`), `attachments: ImmutableList<ComposerAttachment>` (max 4), `replyToUri: String?`, `replyParentLoad: ParentLoadStatus?`, and `submitStatus: ComposerSubmitStatus`. `ComposerState` MUST NOT contain a `text: String` field or remote data wrappers like `Async<T>` or `Result<T>`.

#### Scenario: Default state has zero count and idle submit

- **WHEN** `ComposerViewModel` emits its initial state in new-post mode
- **THEN** `state.graphemeCount == 0`, `state.isOverLimit == false`, `state.attachments.isEmpty()`, `state.replyToUri == null`, `state.replyParentLoad == null`, and `state.submitStatus == ComposerSubmitStatus.Idle`

#### Scenario: Reply mode initializes with parent load in progress

- **WHEN** `ComposerViewModel` emits its initial state with `replyToUri` non-null
- **THEN** `state.replyToUri` matches the route argument, `state.replyParentLoad == ParentLoadStatus.Loading`, and `state.submitStatus == ComposerSubmitStatus.Idle`

#### Scenario: `isOverLimit` mirrors grapheme count

- **WHEN** the user types text whose `graphemeCount` is exactly 301
- **THEN** the next emitted state has `state.isOverLimit == true`

### Requirement: Submission lifecycle is modeled as a sealed status sum

The system SHALL declare `sealed interface ComposerSubmitStatus` with variants `Idle`, `Submitting`, `Success`, and `Error(val cause: ComposerError)`. Reducer MUST NOT set multiple states simultaneously or introduce flat boolean mirrors. Submission transitions `Idle -> Submitting` on valid input, `Submitting -> Success` on post creation, and `Submitting -> Error(cause)` on failure.

#### Scenario: Submit transitions to Submitting

- **WHEN** `state.submitStatus == Idle`, `textFieldState.text == "hello"`, `state.isOverLimit == false`, and a `Submit` event is dispatched
- **THEN** the next state has `submitStatus == ComposerSubmitStatus.Submitting`

#### Scenario: Successful submission transitions to Success

- **WHEN** `state.submitStatus == Submitting` and the underlying `PostingRepository.createPost` returns `Result.success`
- **THEN** the next state has `submitStatus == ComposerSubmitStatus.Success`

#### Scenario: Failed submission transitions to Error with cause

- **WHEN** `state.submitStatus == Submitting` and `PostingRepository.createPost` returns `Result.failure(IOException(...))`
- **THEN** the next state has `submitStatus == ComposerSubmitStatus.Error(cause)` where `cause` is a typed `ComposerError` mapped from the exception

#### Scenario: Retry from Error replaces the error

- **WHEN** `state.submitStatus == ComposerSubmitStatus.Error(...)` and a `Submit` event is dispatched
- **THEN** the next state has `submitStatus == ComposerSubmitStatus.Submitting` and the prior error is no longer observable in state

### Requirement: Reply parent fetch lifecycle is modeled as a sealed status sum

The system SHALL declare `sealed interface ParentLoadStatus` with variants `Loading`, `Loaded(val post: ParentPostUi)`, and `Failed(val cause: ComposerError)`. In reply mode, `ComposerViewModel` MUST kick off a parent-post fetch on initialization. The fetch MUST resolve both the immediate parent reference and the thread root reference (required to construct the AT Protocol `reply` field). Submission MUST be blocked unless `replyParentLoad is ParentLoadStatus.Loaded`.

#### Scenario: Reply mode emits Loading then Loaded

- **WHEN** `ComposerRoute(replyToUri = parentUri)` is opened and the parent fetch succeeds
- **THEN** the state transitions through `replyParentLoad == ParentLoadStatus.Loading`, then `replyParentLoad == ParentLoadStatus.Loaded(post)` where `post.parentRef.uri.toString() == parentUri`

#### Scenario: Parent fetch failure blocks submission

- **WHEN** `state.replyParentLoad == ParentLoadStatus.Failed(_)` and the user has typed valid text
- **THEN** dispatching `Submit` SHALL NOT transition `submitStatus` to `Submitting` and SHALL NOT call `PostingRepository`

#### Scenario: Parent fetch retry from Failed

- **WHEN** `state.replyParentLoad == ParentLoadStatus.Failed(_)` and a `RetryParentLoad` event is dispatched
- **THEN** the next state has `replyParentLoad == ParentLoadStatus.Loading` and the parent fetch is reattempted

### Requirement: Character limit is enforced at 300 Unicode extended grapheme clusters

The system SHALL count characters as Unicode extended grapheme clusters (`MAX_GRAPHEMES = 300`) using `java.text.BreakIterator.getCharacterInstance()`. The Post button MUST be disabled when `state.isOverLimit == true` or when `textFieldState.text.isBlank() && state.attachments.isEmpty()`. Submission MUST NOT silently truncate text exceeding 300 graphemes.

#### Scenario: Counter matches grapheme count for emoji input

- **WHEN** the user enters a string containing a ZWJ-joined emoji sequence whose `String.length` is 11 UTF-16 units but whose grapheme count is 1
- **THEN** `state.graphemeCount` SHALL equal 1, not 11

#### Scenario: Post button disabled when over limit

- **WHEN** `state.isOverLimit == true`
- **THEN** the rendered Post button has `enabled == false` and dispatching `Submit` is a no-op

#### Scenario: Post button disabled when empty

- **WHEN** `textFieldState.text.isBlank() && state.attachments.isEmpty() && state.submitStatus == Idle`
- **THEN** the Post button has `enabled == false`

#### Scenario: Submission preserves full text

- **WHEN** `textFieldState.text` is exactly 300 graphemes (boundary, not over) and `Submit` succeeds
- **THEN** the record passed to `PostingRepository.createPost` has `text` equal to `textFieldState.text` byte-for-byte with no truncation

### Requirement: Image attachments cap at 4 and use the system photo picker

The system SHALL allow up to 4 image attachments per composition using `PickMultipleVisualMedia` configured with `maxItems = 4 - state.attachments.size` (falling back to `PickVisualMedia` when remaining capacity is 1). The Add Image affordance MUST be disabled when `attachments.size == 4`. The reducer MUST defensively cap attachments at 4.

#### Scenario: Picker invocation respects the cap

- **WHEN** `state.attachments.size == 2` and the user taps "Add image"
- **THEN** the launched picker is configured with `maxItems = 2` (remaining capacity), not the absolute cap of 4

#### Scenario: Reducer enforces the cap defensively

- **WHEN** `state.attachments.size == 3` and an `AddAttachments` event arrives carrying 3 URIs
- **THEN** the next state has `attachments.size == 4` (one new URI accepted, two dropped)

#### Scenario: Add-image affordance disabled at the cap

- **WHEN** `state.attachments.size == 4`
- **THEN** the rendered "Add image" affordance has `enabled == false`

#### Scenario: Attachment removal mutates state

- **WHEN** `state.attachments` contains three items and a `RemoveAttachment(index = 1)` event is dispatched
- **THEN** the next state has `attachments.size == 2` and item at original index 1 is absent

### Requirement: Submission uploads blobs in parallel before creating the record

The system SHALL upload all attached image blobs in parallel (via `coroutineScope { ... awaitAll() }`) before invoking `PostingRepository.createPost`. The record creation call MUST NOT begin until every blob upload has succeeded. Any blob upload failure MUST abort the entire submission and route a `ComposerError.UploadFailed` to `submitStatus`. The record creation call MUST receive the resolved blob CIDs and (in reply mode) both the parent and root references.

#### Scenario: Parallel blob uploads precede record creation

- **WHEN** `state.attachments.size == 3` and `Submit` is dispatched
- **THEN** the test fake's invocation log records 3 `uploadBlob` calls completing before any `createPost` call begins

#### Scenario: Blob upload failure aborts submission

- **WHEN** `state.attachments.size == 2` and one of the two `uploadBlob` calls returns `Result.failure`
- **THEN** `createPost` is never invoked and the next state has `submitStatus == ComposerSubmitStatus.Error(ComposerError.UploadFailed)`

#### Scenario: Reply submission carries parent and root refs

- **WHEN** `state.replyParentLoad == ParentLoadStatus.Loaded(post)` where `post.parentRef` and `post.rootRef` are populated and `Submit` succeeds
- **THEN** the `createPost` call's record has its `reply.parent` set to `post.parentRef` and `reply.root` set to `post.rootRef`

### Requirement: Submitted records carry a `langs` field derived from the device's primary locale

The system SHALL ensure created `app.bsky.feed.post` records carry a non-empty `langs` BCP-47 array, defaulting to the device's primary locale via an injected `LocaleProvider`. When `langs` is explicitly supplied, valid non-empty tags override the default; invalid tags are dropped. When explicitly passed an empty list, the record MUST omit the `langs` field without locale fallback.

#### Scenario: V1 composer's submission carries the device-locale tag

- **WHEN** `ComposerViewModel.handleEvent(Submit)` succeeds with the device's primary locale set to `"ja-JP"` and the composer's `textFieldState.text` is `"こんにちは"`
- **THEN** the record sent to `RepoService.createRecord` has `langs == ["ja-JP"]`

#### Scenario: Caller-supplied langs override the device-locale default

- **WHEN** a caller invokes `PostingRepository.createPost(text, attachments, replyTo, langs = listOf("es-MX"))` while the device's primary locale is `"en-US"`
- **THEN** the record's `langs` array is `["es-MX"]` and the device locale is NOT mixed in

#### Scenario: Invalid BCP-47 tags are dropped silently

- **WHEN** a caller passes `langs = listOf("en-US", "", "!", "es-MX")`
- **THEN** the record's `langs` array is `["en-US", "es-MX"]` (the empty string and bare `"!"` don't round-trip through `Locale.forLanguageTag` and are dropped)

#### Scenario: All-invalid input omits the field rather than emitting an empty array

- **WHEN** every tag in the caller's `langs` list fails BCP-47 validation
- **THEN** the record is created with `langs` omitted entirely (the lexicon does not accept empty `langs` arrays)

#### Scenario: Explicit empty list is honored without device-locale fallback

- **WHEN** a caller passes `langs = emptyList()` explicitly
- **THEN** the record is created with `langs` omitted entirely; the repository MUST NOT substitute the device-locale default

### Requirement: Composer language chip exposes a per-post BCP-47 override

The system SHALL render an M3 `AssistChip` in `ComposerOptionsChipRow` showing current language tags (localized display name or `"+N"` overflow). Tapping opens a multi-select picker (`ModalBottomSheet` on Compact, `Popup` on Medium/Expanded) with up to 3 selections from `BLUESKY_LANGUAGE_TAGS`. Dismissing without confirmation preserves previous state.

#### Scenario: Chip label reflects device-locale fallback when no override is set

- **GIVEN** `ComposerState.selectedLangs == null` and `ComposerViewModel.deviceLocaleTag == "en-US"`
- **WHEN** `ComposerScreen` renders
- **THEN** the chip's label is `"English"`

#### Scenario: Chip label reflects single explicit override

- **GIVEN** `ComposerState.selectedLangs == listOf("ja-JP")`
- **WHEN** `ComposerScreen` renders
- **THEN** the chip's label is `"Japanese"`

#### Scenario: Chip label shows overflow count for multi-language selection

- **GIVEN** `ComposerState.selectedLangs == listOf("en-US", "ja-JP", "es-MX")`
- **WHEN** `ComposerScreen` renders
- **THEN** the chip's label is `"English +2"`

#### Scenario: Cap-of-3 enforced as disabled checkboxes

- **GIVEN** the language picker is open and the user has checked 3 languages
- **WHEN** the user inspects an unchecked language row
- **THEN** that row's checkbox is rendered with `enabled = false`

#### Scenario: Picker dismiss without confirm leaves state unchanged

- **GIVEN** the language picker is open with `state.selectedLangs == null`, the user toggles two checkboxes inside the picker, and then taps `Cancel` (or drags the sheet down)
- **WHEN** the dismiss completes
- **THEN** `ComposerState.selectedLangs` is still `null`

#### Scenario: Submit with non-null selection passes it verbatim to createPost

- **GIVEN** `ComposerState.selectedLangs == listOf("ja-JP", "en-US")` and `Submit` succeeds
- **WHEN** `PostingRepository.createPost` is invoked
- **THEN** the call's `langs` parameter is `listOf("ja-JP", "en-US")`

#### Scenario: Submit with null selection falls back to repo's device-locale default

- **GIVEN** `ComposerState.selectedLangs == null` and `Submit` succeeds
- **WHEN** `PostingRepository.createPost` is invoked
- **THEN** the call's `langs` parameter is `null` (the repository's `LocaleProvider` then derives the device-locale default per `nubecita-wtq.12`'s contract)

### Requirement: Tab-internal navigation flows through `ComposerEffect`, not a Hilt-injected navigator

The system SHALL declare `sealed interface ComposerEffect : UiEffect` with variants `NavigateBack`, `ShowError(val error: ComposerError)`, and `OnSubmitSuccess(val newPostUri: AtUri)`. Effects MUST NOT carry Android resource IDs. The screen Composable collects effects in `LaunchedEffect` and controls `LocalMainShellNavState.current`. `ComposerViewModel` MUST NOT inject navigation controllers.

#### Scenario: VM constructor has no navigation state holder

- **WHEN** `ComposerViewModel`'s constructor is inspected
- **THEN** it SHALL NOT declare a parameter typed `MainShellNavState` or any `Navigator` flavor scoped to the outer shell

#### Scenario: Successful submit emits OnSubmitSuccess and screen pops

- **WHEN** the VM transitions `submitStatus` from `Submitting` to `Success`
- **THEN** the VM emits `ComposerEffect.OnSubmitSuccess(newPostUri)` and the collecting Composable invokes `LocalMainShellNavState.current.removeLast()`

#### Scenario: Back-press while idle pops without confirmation

- **WHEN** `textFieldState.text.isBlank() && state.attachments.isEmpty()` and the system back-press is received
- **THEN** the screen Composable invokes `LocalMainShellNavState.current.removeLast()` without a confirmation dialog

### Requirement: Discard confirmation follows the M3 full-screen-dialog discard pattern

The system SHALL show a "Discard draft?" confirmation when back is pressed on a non-empty composition (`textFieldState.text.isNotBlank() || attachments.isNotEmpty()`), offering `Cancel` and `Discard` actions. Back-press MUST be ignored while `submitStatus == Submitting`. The confirmation card uses `BasicAlertDialog` on Compact width and `Popup` on Medium/Expanded width to ensure exactly one scrim layer without double-dimming.

#### Scenario: Confirmation appears for non-empty draft

- **WHEN** `textFieldState.text == "draft text"` and the system back-press is received
- **THEN** a `ComposerDiscardDialog` is shown overlaid on the composer with `Cancel` and `Discard` actions

#### Scenario: Cancel action dismisses the confirmation, keeps the composer

- **WHEN** the discard confirmation is shown and the user taps `Cancel`
- **THEN** the confirmation is dismissed, the composer remains visible, and `LocalMainShellNavState` is NOT mutated

#### Scenario: Discard action dismisses the composer

- **WHEN** the discard confirmation is shown and the user taps `Discard`
- **THEN** the composer is dismissed: at Compact, the screen Composable invokes `LocalMainShellNavState.current.removeLast()`; at Medium/Expanded, the `MainShell`-scoped composer-launcher state holder transitions to `Closed`

#### Scenario: Compose primitive at Compact is `BasicAlertDialog`

- **WHEN** the source of `ComposerDiscardDialog` is inspected and the active `WindowWidthSizeClass` is `COMPACT`
- **THEN** the rendered confirmation uses `androidx.compose.material3.BasicAlertDialog` wrapping a custom-content card styled with `AlertDialogDefaults` (`shape`, `containerColor`, `tonalElevation`)

#### Scenario: Compose primitive at Medium/Expanded is `Popup`, not `Dialog`

- **WHEN** the source of `ComposerDiscardDialog` is inspected and the active `WindowWidthSizeClass` is `MEDIUM` or `EXPANDED`
- **THEN** the rendered confirmation uses `androidx.compose.ui.window.Popup` wrapping an M3 `Surface`-based dialog card, and does NOT use `androidx.compose.material3.BasicAlertDialog`, `androidx.compose.material3.AlertDialog`, or `androidx.compose.ui.window.Dialog`

#### Scenario: Visible scrim density matches the M3 single-dim spec

- **WHEN** the discard confirmation is shown overlaid on the composer at any width class
- **THEN** the visible scrim covering the area outside the confirmation card is exactly one M3 scrim layer in luminance — equivalent to the composer's solo scrim at Medium/Expanded, or the `BasicAlertDialog`'s solo scrim at Compact — and does NOT visibly darken further when the confirmation appears at Medium/Expanded (which would indicate two stacked Dialog scrims)

#### Scenario: Back-press ignored while submitting

- **WHEN** `state.submitStatus == ComposerSubmitStatus.Submitting` and the system back-press is received
- **THEN** no confirmation is shown and `LocalMainShellNavState` is not mutated

### Requirement: Keyboard auto-focuses the input on screen entry

The system SHALL request focus on the composer text field on first composition such that the IME is visible without user interaction. The screen Composable MUST attach a `FocusRequester` to the text field and call `requestFocus()` from a `LaunchedEffect(Unit)` block. This behavior MUST hold for both new-post and reply modes.

#### Scenario: IME opens on entry

- **WHEN** `ComposerScreen` enters composition for the first time
- **THEN** the text field has focus and `LocalSoftwareKeyboardController.current.show()` has been invoked (or focus alone is sufficient to open the IME on the test device)

### Requirement: Material 3 Expressive treatment for the Post button and counter

The system SHALL render the Post action with expressive M3 styling: standard filled-button at `Idle`, inline wavy progress indicator when `Submitting` (tap disabled), and error-tone surface on `Error`. The counter arc tone shifts at 240 (warning) and 290 (error) graphemes. When `graphemeCount > 300`, the input outline MUST adopt the M3 error tone.

#### Scenario: Submitting button shows wavy progress

- **WHEN** `state.submitStatus == ComposerSubmitStatus.Submitting`
- **THEN** the Post button renders an M3 wavy progress indicator and has `enabled == false`

#### Scenario: Counter color band at 240

- **WHEN** `state.graphemeCount` transitions from 239 to 240
- **THEN** the counter arc tone changes to the M3 tertiary/warn token

#### Scenario: Counter color band at 290

- **WHEN** `state.graphemeCount` transitions from 289 to 290
- **THEN** the counter arc tone changes to the M3 error token

#### Scenario: Over-limit input border

- **WHEN** `state.isOverLimit == true`
- **THEN** the text field's outline tone is the M3 error token

### Requirement: Composer registers as an `@MainShell` Nav3 entry for Compact-width hosting

The system SHALL contribute the `ComposerRoute` entry via `@Provides @IntoSet @MainShell EntryProviderInstaller` in `:feature:composer:impl` without `@OuterShell`. It resolves `ComposerScreen` by passing `route` to `ComposerViewModel.Factory.create(route)` via assisted injection. This entry hosts Compact width; Medium/Expanded width uses a Dialog overlay.

#### Scenario: MainShell qualifier on the entry installer

- **WHEN** `:feature:composer:impl` Hilt modules are inspected
- **THEN** exactly one `EntryProviderInstaller` provider exists, qualified with `@MainShell` and not with `@OuterShell`

#### Scenario: Tab-internal push lands the composer at Compact

- **WHEN** the active `WindowWidthSizeClass` is `COMPACT` and code inside `MainShell` invokes `LocalMainShellNavState.current.add(ComposerRoute())`
- **THEN** the inner `NavDisplay` resolves the entry and renders `ComposerScreen` filling the pane

### Requirement: Adaptive container — full-screen route on Compact, centered Dialog on Medium/Expanded

The system SHALL host `ComposerScreen` adaptively: on Compact width, it pushes `ComposerRoute` to `LocalMainShellNavState.current`; on Medium/Expanded width, it opens a centered `Dialog` constrained to `Modifier.widthIn(max = 640.dp)` via `ComposerOverlayState`. `ComposerScreen` Composable MUST NOT branch on width class. Both paths obtain `ComposerViewModel` via assisted injection.

#### Scenario: Compact launches via NavDisplay push

- **WHEN** the active `WindowWidthSizeClass` is `COMPACT` and the Feed FAB is tapped
- **THEN** `LocalMainShellNavState.current.add(ComposerRoute(replyToUri = null))` is invoked exactly once and the composer-launcher state holder remains `Closed`

#### Scenario: Medium launches via Dialog overlay

- **WHEN** the active `WindowWidthSizeClass` is `MEDIUM` and the Feed FAB is tapped
- **THEN** the composer-launcher state holder transitions to `Open(replyToUri = null)` exactly once and `LocalMainShellNavState.current` is NOT mutated

#### Scenario: Expanded launches via Dialog overlay

- **WHEN** the active `WindowWidthSizeClass` is `EXPANDED` and the Feed FAB is tapped
- **THEN** the composer-launcher state holder transitions to `Open(replyToUri = null)` exactly once and `LocalMainShellNavState.current` is NOT mutated

#### Scenario: Dialog overlay caps content width at 640dp

- **WHEN** the composer Dialog is rendered at `EXPANDED` width
- **THEN** the inner content wrapping `ComposerScreen` is constrained by `Modifier.widthIn(max = 640.dp)` and is centered horizontally; the M3 dialog scrim covers the remaining width

#### Scenario: Dialog uses non-platform default width

- **WHEN** the composer Dialog is rendered
- **THEN** its `DialogProperties` has `usePlatformDefaultWidth == false` (relying on `widthIn(max = 640.dp)` rather than the Android theme's `windowMinWidthMajor/Minor`)

#### Scenario: ComposerScreen Composable is identical across widths

- **WHEN** the source of `ComposerScreen` is inspected
- **THEN** it does NOT branch on `WindowWidthSizeClass`, does NOT read `currentWindowAdaptiveInfo()`, and does NOT take a "isDialog" parameter — width-class branching lives only in `MainShell` / launcher code

#### Scenario: Reply launch from feed picks the same width-conditional path

- **WHEN** the user taps the reply affordance on a `PostCard` and the active `WindowWidthSizeClass` is `MEDIUM` or `EXPANDED`
- **THEN** the launching code transitions the composer-launcher state holder to `Open(replyToUri = post.uri)` and does NOT push onto `LocalMainShellNavState.current`

### Requirement: Discard confirmation dialog uses an extensible action set

The "Discard draft?" dialog SHALL render its actions from an iterable parameter (e.g. `ImmutableList<ComposerDialogAction>`) rather than hard-coded button slots. V1 MUST supply exactly two actions (`Cancel`, `Discard`) driven by the same data-driven list across both Compact (`BasicAlertDialog`) and Medium/Expanded (`Popup`) implementations.

#### Scenario: Action set is data-driven

- **WHEN** the source of the discard confirmation dialog is inspected
- **THEN** its actions are rendered from an iterable / list parameter (e.g. `actions: ImmutableList<ComposerDialogAction>`) rather than hard-coded `confirmButton` / `dismissButton` slots

#### Scenario: V1 ships exactly two actions

- **WHEN** the discard dialog is rendered in V1
- **THEN** the rendered action list contains exactly two items: `Cancel` and `Discard`

#### Scenario: Same action list drives both renderings

- **WHEN** the same `ImmutableList<ComposerDialogAction>` is passed into the discard confirmation at Compact and at Medium/Expanded
- **THEN** the rendered actions, their labels, their order, and their `onClick` lambdas are identical across both width classes — only the wrapping Compose primitive differs (`BasicAlertDialog` vs. `Popup`)

### Requirement: Top-bar action row reserves space for a future drafts entry point

`ComposerScreen`'s top app bar SHALL position its close and post actions so that an additional icon button can be inserted between them without forcing a relayout or pushing actions off-screen. The reservation MUST be noted with `// reserved for drafts entry point` near the action row.

#### Scenario: Top-bar layout has room to grow

- **WHEN** a hypothetical third `IconButton` is inserted between the existing actions in `ComposerScreen`'s top app bar
- **THEN** all three actions render fully visible at Compact width without truncation or overflow

### Requirement: `ComposerViewModel` constructor leaves room for a future `DraftRepository`

`ComposerViewModel`'s constructor SHALL declare `@Assisted route: ComposerRoute` followed by `@Inject` parameters `postingRepository: PostingRepository` and `parentFetchSource: ParentFetchSource` in order. Future dependencies MUST append after `parentFetchSource` without repositioning existing parameters.

#### Scenario: V1 constructor signature

- **WHEN** the source of `ComposerViewModel` is inspected
- **THEN** its primary constructor declares one `@Assisted` parameter (`route: ComposerRoute`) followed by two `@Inject`-resolved parameters (`postingRepository: PostingRepository` and `parentFetchSource: ParentFetchSource`), in that order, via `@AssistedInject`

### Requirement: FAB component on launching surfaces is badge-wrappable

Any FloatingActionButton launching the composer SHALL use a component supporting `BadgedBox` (`FloatingActionButton`, `LargeFloatingActionButton`, or `SmallFloatingActionButton`). It MUST NOT use `ExtendedFloatingActionButton`, reserving support for drafts badges.

#### Scenario: Feed compose FAB is icon-only and wrappable

- **WHEN** the source of `feature-feed:impl`'s compose FAB is inspected
- **THEN** the FAB is one of `FloatingActionButton`, `LargeFloatingActionButton`, or `SmallFloatingActionButton` — it is NOT `ExtendedFloatingActionButton`

### Requirement: `:feature:composer:impl` follows the standard module conventions

`:feature:composer:impl` SHALL apply `nubecita.android.feature` and declare namespace `net.kikin.nubecita.feature.composer.impl`. It depends on `:feature:composer:api`, `:core:posting`, `:core:common:navigation`, and `:core:designsystem`, but MUST NOT depend on `:app`. `:feature:composer:api` applies `nubecita.android.library`.

#### Scenario: impl applies the feature convention plugin

- **WHEN** `:feature:composer:impl/build.gradle.kts` is inspected
- **THEN** the `plugins { }` block applies `nubecita.android.feature`

#### Scenario: api applies the library convention plugin

- **WHEN** `:feature:composer:api/build.gradle.kts` is inspected
- **THEN** the `plugins { }` block applies `nubecita.android.library` and does NOT apply `nubecita.android.library.compose` or `nubecita.android.hilt`

### Requirement: Screenshot test contract covers five content states plus an adaptive-Dialog baseline

The system SHALL maintain Compose screenshot tests in `:feature:composer:impl` covering six fixtures in Light and Dark themes (12 images): empty composer, near-limit (295 graphemes), submitting, attached images (3 chips), reply mode, and empty composer at Expanded width as Dialog overlay (`widthIn(max = 640.dp)`). All fixtures MUST use deterministic test data.

#### Scenario: Empty fixture pair exists

- **WHEN** the `screenshotTest` source set of `:feature:composer:impl` is enumerated
- **THEN** there exist two screenshot tests rendering an empty `ComposerState`, one in Light theme and one in Dark theme

#### Scenario: Near-limit fixture pins at 295

- **WHEN** the near-limit screenshot test is loaded
- **THEN** the `ComposerState` fixture has `graphemeCount == 295` and `isOverLimit == false`

#### Scenario: Attached-images fixture renders 3 chips

- **WHEN** the attached-images screenshot test is loaded
- **THEN** the `ComposerState` fixture has `attachments.size == 3` and the rendered output shows 3 attachment thumbnails

#### Scenario: Reply fixture renders parent card

- **WHEN** the reply-mode screenshot test is loaded
- **THEN** the `ComposerState` fixture has `replyParentLoad is ParentLoadStatus.Loaded` and the rendered output shows a parent-post card above the input field

#### Scenario: Adaptive Dialog fixture renders with width cap

- **WHEN** the Expanded-width Dialog screenshot test is loaded
- **THEN** the rendered Dialog content is constrained by `Modifier.widthIn(max = 640.dp)`, is centered horizontally, and is overlaid on a scrim against a stub backing surface

#### Scenario: Submitting fixture pins mid-submission state

- **WHEN** the Submitting screenshot test is loaded
- **THEN** the `ComposerState` fixture has `submitStatus == ComposerSubmitStatus.Submitting`

#### Scenario: All fixtures present in both themes

- **WHEN** the screenshot test directory is enumerated
- **THEN** there are exactly 12 fixture images: {empty, near-limit, submitting, with-images, reply, empty-expanded-dialog} × {light, dark}

### Requirement: Unit-test coverage for the composer state machine

The system SHALL maintain unit tests in `:feature:composer:impl` covering: initial states (new post, reply loading/loaded/failed), `snapshotFlow` text tracking and limit derivation, grapheme counter emoji boundary, attachment cap and removal, submission transitions (idle/submitting/success/error), retry, and reply refs. Tests MUST use fakes and run offline.

#### Scenario: Test suite enumerates the canonical state transitions

- **WHEN** the `:feature:composer:impl` test source set is enumerated
- **THEN** at least one `@Test` method exists for each item in the list above (named or annotated such that the mapping is unambiguous)

#### Scenario: Tests run without network

- **WHEN** the composer unit tests are executed under `./gradlew :feature:composer:impl:testDebugUnitTest`
- **THEN** no real `HttpClient` or atproto `XrpcClient` is instantiated and the suite passes offline

### Requirement: Composer text input is owned by Compose `TextFieldState`

`ComposerViewModel` SHALL expose canonical text in `val textFieldState: TextFieldState`. `ComposerScreen` MUST use `OutlinedTextField(state = vm.textFieldState, ...)`. Legacy `value`/`onValueChange` overloads SHALL NOT be used. `ComposerState` MUST NOT contain a `text` field, and `ComposerEvent` MUST NOT contain a `TextChanged` variant.

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

The system SHALL expose pure function `net.kikin.nubecita.feature.composer.impl.internal.currentMentionToken(text: CharSequence, cursor: Int): String?` returning the active mention token without leading `@`, or `null`. It returns `null` at position 0, after bare `@`, after word chars (e.g. email context), or across whitespace/second-`@` boundaries.

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

`ComposerState` SHALL contain `typeahead: TypeaheadStatus` (default `Idle`). `TypeaheadStatus` MUST be a sealed interface with variants `Idle`, `Querying(val query: String)`, `Suggestions(val query: String, val results: ImmutableList<ActorTypeaheadUi>)`, and `NoResults(val query: String)`. Transient errors collapse to `Idle`.

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

### Requirement: Typeahead pipeline guarantees debounce semantics + distinctUntilChanged + mapLatest

`ComposerViewModel` MUST drive typeahead queries using `.distinctUntilChanged().mapLatest { token -> if (token.isNotEmpty()) delay(150.milliseconds); repo.searchTypeahead(token) }`. The pipeline guarantees 150ms debounce for non-empty tokens, suppression of consecutive duplicate queries, and cancellation of in-flight lookups when newer tokens arrive.

#### Scenario: mapLatest cancels in-flight queries

- **GIVEN** the user has typed `@al` and the typeahead repository is suspended on the resulting query
- **WHEN** the user types `i` (active token becomes `ali`) and the debounce window for `ali` expires
- **THEN** the suspended `searchTypeahead("al")` SHALL be cancelled, and only the result for `"ali"` SHALL be assigned to `state.typeahead`

#### Scenario: distinctUntilChanged drops duplicate consecutive tokens

- **GIVEN** the active token is `"a"` and `state.typeahead = Suggestions("a", [...])`
- **WHEN** the user deletes a character and immediately retypes the same character (active token returns to `"a"`)
- **THEN** the repository SHALL NOT be called a second time for `"a"`

### Requirement: Selecting a suggestion atomically replaces the active token

`ComposerViewModel` SHALL handle `ComposerEvent.TypeaheadResultClicked(actor: ActorTypeaheadUi)` by replacing the active mention substring `[@-position, cursor)` with `@<actor.handle> ` via `textFieldState.edit`. If the `@`-position cannot be found, it no-ops. After replacement, the typeahead state transitions to `Idle`.

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

When `state.typeahead` is `Suggestions` or `NoResults`, `ComposerScreenContent` SHALL render suggestions inline in an M3 `OutlinedCard` (`LazyColumn(heightIn(max = 240.dp))`) between the text field and attachment row. Each row shows avatar, display name, and `@handle`, tapping which dispatches `TypeaheadResultClicked`. It does not render in `Idle` or `Querying`.

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

### Requirement: Auto-detected external link preview card

When the user types or pastes a URL into the post text that is an `http(s)` URL and is NOT a Bluesky quote-link, the composer SHALL automatically fetch a link preview and display a link card below the text. The preview SHALL be fetched from the CardyB service (`cardyb.bsky.app/v1/extract`), mapping its `title`, `description`, and `image` into the card. Only the first eligible URL SHALL produce a card.

#### Scenario: Pasting a URL shows a preview card

- **WHEN** the user pastes `https://example.com/article` into the composer text
- **THEN** the composer fetches a preview and renders a link card with the title, description, domain, and thumbnail below the text field

#### Scenario: A Bluesky quote-link is not treated as an external card

- **WHEN** the pasted URL is a Bluesky post link (e.g. `https://bsky.app/profile/.../post/...`)
- **THEN** it is handled by quote-link detection and does NOT produce an external link card

#### Scenario: Only the first eligible URL produces a card

- **WHEN** the text contains two eligible URLs
- **THEN** only the first detected URL produces a card

#### Scenario: A page with a title but no description still shows a card

- **WHEN** the fetched preview has a non-empty title and a blank description
- **THEN** a card is shown (title + thumbnail, no description row) and the embed carries `description = ""`

#### Scenario: A shortened link resolves to its final destination

- **WHEN** the user pastes a shortened URL (e.g. `bit.ly/…`) and CardyB returns a redirect-resolved `url`
- **THEN** the `external.uri` of the posted embed is the resolved destination URL, not the shortened string the user typed

### Requirement: Link card is dismissable and does not re-pop

The link card SHALL provide a dismiss affordance. Dismissing it SHALL remove the card and memoize the URL so that the same URL still present in the text does NOT immediately produce a new card.

#### Scenario: Dismissing a card removes it

- **WHEN** the user taps the card's dismiss control
- **THEN** the card is removed and the same URL remaining in the text does not re-create a card

### Requirement: Link card mutual-exclusion with images, coexistence with quote

A link card SHALL be mutually exclusive with image/gallery attachments: while images are attached the composer SHALL NOT fetch or show a card, and adding images SHALL clear any existing card. A link card MAY coexist with a quoted post.

#### Scenario: Images suppress the card

- **WHEN** the composer has image attachments and the user pastes a URL
- **THEN** no link card is fetched or shown

#### Scenario: Adding images clears an existing card

- **WHEN** a link card is shown and the user adds an image attachment
- **THEN** the link card is cleared

#### Scenario: Removing images restores the card

- **WHEN** a card was auto-cleared by adding images, and the user then removes all images while the URL is still in the text
- **THEN** the card is re-detected and restored automatically (the image-induced clear did not memoize the URL)

#### Scenario: Card coexists with a quoted post

- **WHEN** the composer has a quoted post and a link card
- **THEN** both are retained

### Requirement: External embed is built on submit

When a post is submitted with a link card and no image attachments, the composer SHALL build an `app.bsky.embed.external` record from the card's uri/title/description. When a quoted post is also present, the embed SHALL be `app.bsky.embed.recordWithMedia` with the external as its media. When image attachments are present, the link card SHALL be dropped (images take the media slot).

#### Scenario: Card-only post emits an external embed

- **WHEN** a post with a link card and no images is submitted
- **THEN** the wire embed is `app.bsky.embed.external`

#### Scenario: Card plus quote emits recordWithMedia

- **WHEN** a post with a link card and a quoted post is submitted
- **THEN** the wire embed is `app.bsky.embed.recordWithMedia` with the external as media

#### Scenario: Images win over a card

- **WHEN** a post somehow carries both images and a card at submit
- **THEN** the images embed is emitted and the external is dropped

### Requirement: Thumbnail upload is best-effort

The link card's thumbnail SHALL be uploaded as the external embed's `thumb` blob at post time on a best-effort basis. If the thumbnail cannot be fetched or uploaded, the post SHALL still be created with the external embed minus the thumbnail. A thumbnail failure SHALL NOT block or fail the post.

#### Scenario: Thumbnail failure still posts the card

- **WHEN** the thumbnail download or upload fails at submit
- **THEN** the post is created with an external embed carrying uri/title/description and no thumb

### Requirement: Preview-fetch failure is silent

When the preview fetch returns no usable data (CardyB error, blank fields, network failure, or timeout), the composer SHALL NOT show a card and SHALL NOT surface an error to the user, and SHALL memoize the URL so it does not retry-loop.

#### Scenario: Failed fetch shows no card and no error

- **WHEN** the preview fetch fails for a pasted URL
- **THEN** no card is shown, no error message is surfaced, and the URL is not re-fetched while it remains in the text

### Requirement: Attach a KLIPY GIF or sticker
The composer SHALL let a user pick a KLIPY GIF or sticker and attach it to the post as a single previewed embed.

#### Scenario: Pick a GIF or sticker from the picker
- **WHEN** a user opens the KLIPY picker from the composer and selects an item
- **THEN** the picker closes and the composer shows an animated preview of the selected item with a remove affordance

#### Scenario: Remove the attached item
- **WHEN** a user removes the attached KLIPY item from the composer
- **THEN** the post no longer carries the KLIPY embed and the picker can be reopened

### Requirement: One embed per post
A post carries exactly one media embed, so the composer SHALL treat a picked KLIPY item (GIF or sticker) as mutually exclusive with photo attachments, SHALL have it replace an auto-detected link-card embed (they share the single external-embed slot), and MAY combine it with a quote (published as `app.bsky.embed.recordWithMedia`).

#### Scenario: Photos block the KLIPY entry point
- **WHEN** the composer already has photo attachments
- **THEN** the KLIPY picker entry point is disabled

#### Scenario: An attached KLIPY item blocks adding photos
- **WHEN** the composer already has a KLIPY item attached
- **THEN** adding photos is blocked

#### Scenario: A picked KLIPY item replaces an auto-detected link card
- **WHEN** a link-card embed is showing and the user picks a KLIPY item
- **THEN** the link card is cleared and the KLIPY item takes the external-embed slot

#### Scenario: A KLIPY item may coexist with a quote
- **WHEN** the composer has a quote attached and the user picks a KLIPY item
- **THEN** both are kept, and the post is published as record-with-media

### Requirement: Publish a KLIPY item as a recognizable external embed
When a post with an attached KLIPY item (GIF or sticker) is published, the composer SHALL write it as an `app.bsky.embed.external` whose URI is the KLIPY CDN media URL carrying pixel-dimension parameters, with an uploaded thumbnail, so that GIF-aware clients render it inline-animated.

#### Scenario: Posting a KLIPY item produces the recognized embed shape
- **WHEN** a user posts with an attached KLIPY item
- **THEN** the created record's external embed URI has host `static.klipy.com`, a path beginning `/ii/`, and positive `hh`/`ww` parameters, and carries an uploaded thumbnail blob

### Requirement: KLIPY branding and reporting in the picker
The picker SHALL present the KLIPY-required branding and a content-report affordance.

#### Scenario: Branding is visible
- **WHEN** the picker is shown
- **THEN** the search field shows the required "Search KLIPY" prompt and a "Powered by KLIPY" mark is visible

#### Scenario: Report from preview
- **WHEN** a user opens an item's preview and reports it with a reason
- **THEN** the report is submitted for that item
