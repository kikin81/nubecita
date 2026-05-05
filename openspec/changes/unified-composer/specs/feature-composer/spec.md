## ADDED Requirements

### Requirement: `:feature:composer:api` exposes exactly one `NavKey`

The system SHALL expose `net.kikin.nubecita.feature.composer.api.ComposerRoute` as the sole `NavKey` for the composer capability. `ComposerRoute` MUST be declared as `data class ComposerRoute(val replyToUri: String? = null) : NavKey`. The field is typed as `String?`, NOT the lexicon `AtUri` value class — keeps `:feature:composer:api` atproto-runtime-free, mirroring the existing `:feature:postdetail:api`'s `PostDetailRoute(postUri: String)` precedent. Consumers wrap to `AtUri` at the call site to the atproto runtime. The `:api` module MUST NOT contain Composables, ViewModels, repositories, Hilt modules, or any dependency on Compose runtime, atproto SDK record types, or `:feature:composer:impl`. A `null` `replyToUri` MUST mean "compose a new top-level post"; a non-null `replyToUri` MUST mean "compose a reply to that post". No second `NavKey` (e.g. `NewPostRoute`, `ReplyRoute`) SHALL exist for either mode.

#### Scenario: Single NavKey for both modes

- **WHEN** the `:feature:composer:api` source tree is searched for types implementing `androidx.navigation3.runtime.NavKey`
- **THEN** the only match SHALL be `ComposerRoute`

#### Scenario: API module has no UI dependencies

- **WHEN** `:feature:composer:api`'s `build.gradle.kts` is inspected
- **THEN** the `dependencies { }` block SHALL declare only `:core:common:navigation` (or the module exporting `NavKey` and `AtUri`) and SHALL NOT depend on Compose, Hilt, or `:feature:composer:impl`

### Requirement: `ComposerViewModel` is the canonical presenter

The system SHALL expose `net.kikin.nubecita.feature.composer.impl.ComposerViewModel` as the only `ViewModel` for the composer screen. It MUST extend `MviViewModel<ComposerState, ComposerEvent, ComposerEffect>`, MUST be `@HiltViewModel(assistedFactory = ComposerViewModel.Factory::class)`-annotated, and MUST receive its `route: ComposerRoute` through Hilt **assisted injection** — the canonical Nav3 pattern in this codebase, mirroring `:feature:postdetail:impl`'s `PostDetailViewModel.Factory`. An earlier draft of this requirement specified `SavedStateHandle`-based delivery; that's been refactored to assisted injection because Nav3 does not auto-populate `SavedStateHandle` with route args, and the rest of the codebase uses assisted injection for this exact case. The screen Composable MUST consume the VM via `hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(creationCallback = { it.create(route) })`; no other class in the project SHALL instantiate or extend `ComposerViewModel`. Process death survival is **explicitly out of V1** — no `SavedStateHandle` plumbing for state persistence (the `:core:drafts` follow-up addresses non-empty drafts surviving via disk persistence).

#### Scenario: Screen consumes ComposerViewModel via assisted injection

- **WHEN** `ComposerScreen` composes
- **THEN** it obtains `ComposerViewModel` via `hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(creationCallback = { factory -> factory.create(route) })` and forwards `ComposerEvent`s through `viewModel::handleEvent`

#### Scenario: replyToUri reaches the VM via the assisted route

- **WHEN** navigation pushes `ComposerRoute(replyToUri = "at://did:plc:abc/app.bsky.feed.post/xyz")`
- **THEN** `ComposerViewModel.Factory.create(route)` constructs the VM with the assisted `route` parameter, and `state.replyToUri == "at://did:plc:abc/app.bsky.feed.post/xyz"` on first emission

### Requirement: `ComposerState` carries text, count, attachments, and submit status as flat UI-ready fields

The system SHALL expose `ComposerState` as a `data class` implementing `UiState` with at minimum:

- `text: String` — the current composer text, never `null`, defaulting to `""`.
- `graphemeCount: Int` — the SDK-derived grapheme count of `text`. Recomputed on every `TextChanged` event.
- `isOverLimit: Boolean` — `true` iff `graphemeCount > 300`. Derived; reducer MUST keep it consistent with `graphemeCount` on every state update.
- `attachments: ImmutableList<ComposerAttachment>` from `kotlinx.collections.immutable`, capped at 4. Default `persistentListOf()`.
- `replyToUri: String?` — copied from the route argument (carries the AT URI of the parent post when in reply mode); `null` for new-post mode.
- `replyParentLoad: ParentLoadStatus?` — `null` in new-post mode; non-null in reply mode.
- `submitStatus: ComposerSubmitStatus` — defaulting to `ComposerSubmitStatus.Idle`.

`ComposerState` MUST NOT expose any `Async<T>`, `Result<T>`, or generic remote-data wrapper. Composables MUST read these fields directly without a `when` on a sum-type wrapper at the UI boundary.

#### Scenario: Default state has empty text and idle submit

- **WHEN** `ComposerViewModel` emits its initial state in new-post mode
- **THEN** `state.text == ""`, `state.graphemeCount == 0`, `state.isOverLimit == false`, `state.attachments.isEmpty()`, `state.replyToUri == null`, `state.replyParentLoad == null`, and `state.submitStatus == ComposerSubmitStatus.Idle`

#### Scenario: Reply mode initializes with parent load in progress

- **WHEN** `ComposerViewModel` emits its initial state with `replyToUri` non-null
- **THEN** `state.replyToUri` matches the route argument, `state.replyParentLoad == ParentLoadStatus.Loading`, and `state.submitStatus == ComposerSubmitStatus.Idle`

#### Scenario: `isOverLimit` mirrors grapheme count

- **WHEN** the user types text whose `graphemeCount` is exactly 301
- **THEN** the next emitted state has `state.isOverLimit == true`

### Requirement: Submission lifecycle is modeled as a sealed status sum

The system SHALL declare `sealed interface ComposerSubmitStatus` with exactly four variants: `Idle`, `Submitting`, `Success`, and `Error(val cause: ComposerError)`. The reducer MUST never set two of these states simultaneously and MUST NOT introduce flat boolean mirrors (`isSubmitting`, `submitError`) of the same information. Transitions follow:

- `Idle → Submitting` on `Submit` event when `text.isNotBlank() || attachments.isNotEmpty()`, `!isOverLimit`, and (in reply mode) `replyParentLoad is ParentLoadStatus.Loaded`.
- `Submitting → Success` on successful record creation.
- `Submitting → Error(cause)` on any failure (blob upload, record creation, network).
- `Error(_) → Submitting` on a subsequent `Submit` event (retry replaces the prior error).
- `Success` is terminal; the screen Composable consumes it to dismiss and emits no further `Submit` events.

#### Scenario: Submit transitions to Submitting

- **WHEN** `state.submitStatus == Idle`, `state.text == "hello"`, `state.isOverLimit == false`, and a `Submit` event is dispatched
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

The system SHALL count characters as Unicode extended grapheme clusters — what AT Protocol's `app.bsky.richtext.facet` `MAX_GRAPHEMES = 300` measures, NOT Java/Kotlin `String.length` (UTF-16 code units) or codepoint count. The atproto-kotlin 5.3.0 SDK does not ship a grapheme-counting helper; V1 wraps `java.text.BreakIterator.getCharacterInstance()` in a small `GraphemeCounter` utility inside `:feature:composer:impl`. JVM/Android Unicode-version skew on ZWJ-joined emoji sequences is a known limitation — the JVM's bundled tables predate Unicode 15+ emoji_zwj_sequences, so JVM unit tests cover platform-stable cases (ASCII boundary + BMP-pair emoji); Android's runtime ICU-backed `BreakIterator` (API 24+, our minSdk) handles them correctly. Future swap to ICU4J or a Unicode-version-pinned segmenter is a backlog task; the contract on the counter ("Unicode extended grapheme cluster count") doesn't change. The Post button MUST be disabled when `state.isOverLimit == true` OR when `state.text.isBlank() && state.attachments.isEmpty()`. Submission MUST NOT silently truncate text that exceeds 300 graphemes.

#### Scenario: Counter matches grapheme count for emoji input

- **WHEN** the user enters a string containing a ZWJ-joined emoji sequence whose `String.length` is 11 UTF-16 units but whose grapheme count is 1
- **THEN** `state.graphemeCount` SHALL equal 1, not 11

#### Scenario: Post button disabled when over limit

- **WHEN** `state.isOverLimit == true`
- **THEN** the rendered Post button has `enabled == false` and dispatching `Submit` is a no-op

#### Scenario: Post button disabled when empty

- **WHEN** `state.text.isBlank() && state.attachments.isEmpty() && state.submitStatus == Idle`
- **THEN** the Post button has `enabled == false`

#### Scenario: Submission preserves full text

- **WHEN** `state.text` is exactly 300 graphemes (boundary, not over) and `Submit` succeeds
- **THEN** the record passed to `PostingRepository.createPost` has `text` equal to `state.text` byte-for-byte with no truncation

### Requirement: Image attachments cap at 4 and use the system photo picker

The system SHALL allow up to 4 image attachments per composition. Attachments MUST be added via `androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia` with `maxItems = 4`. The "Add image" affordance MUST be hidden or disabled when `state.attachments.size == 4`. The reducer MUST defensively cap at 4 even if the picker returns more URIs. Removing an attachment MUST be possible from the attachment chip strip.

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

### Requirement: Tab-internal navigation flows through `ComposerEffect`, not a Hilt-injected navigator

The system SHALL declare `sealed interface ComposerEffect : UiEffect` with at minimum `NavigateBack : ComposerEffect`, `ShowError(val error: ComposerError) : ComposerEffect`, and `OnSubmitSuccess(val newPostUri: AtUri) : ComposerEffect`. `ShowError` carries the typed `ComposerError` (from `:core:posting`) — matching `FeedEffect.ShowError(error: FeedError)` and `PostDetailEffect.ShowError(error: PostDetailError)` — so the screen Composable can pre-resolve every error string via `stringResource(...)` at composition time and switch on the sealed-error type inside the collector. The VM MUST NOT carry Android resource ids or pre-localized strings on the effect. The screen Composable MUST collect these effects in a single `LaunchedEffect` block and route navigation calls through `LocalMainShellNavState.current` (e.g. `removeLast()` for back, `add(...)` for forward). `ComposerViewModel` MUST NOT inject `MainShellNavState` or any object backed by it. The outer `Navigator` MUST NOT be injected either.

#### Scenario: VM constructor has no navigation state holder

- **WHEN** `ComposerViewModel`'s constructor is inspected
- **THEN** it SHALL NOT declare a parameter typed `MainShellNavState` or any `Navigator` flavor scoped to the outer shell

#### Scenario: Successful submit emits OnSubmitSuccess and screen pops

- **WHEN** the VM transitions `submitStatus` from `Submitting` to `Success`
- **THEN** the VM emits `ComposerEffect.OnSubmitSuccess(newPostUri)` and the collecting Composable invokes `LocalMainShellNavState.current.removeLast()`

#### Scenario: Back-press while idle pops without confirmation

- **WHEN** `state.text.isBlank() && state.attachments.isEmpty()` and the system back-press is received
- **THEN** the screen Composable invokes `LocalMainShellNavState.current.removeLast()` without a confirmation dialog

### Requirement: Discard confirmation follows the M3 full-screen-dialog discard pattern

The system SHALL show a "Discard draft?" confirmation when a back-press is received and the composition is non-empty (`state.text.isNotBlank() || state.attachments.isNotEmpty()`). The confirmation MUST follow the canonical M3 full-screen-dialog discard pattern as specified at [m3.material.io/components/dialogs/guidelines](https://m3.material.io/components/dialogs/guidelines): a small basic dialog card overlaid on the composer surface, presenting V1 actions `Cancel` (dismisses the confirmation, leaves the composer open) and `Discard` (destructive — dismisses the composer). The confirmation MUST NOT appear when both `text` and `attachments` are empty. Back-press MUST be ignored entirely while `submitStatus == Submitting`.

The Compose primitive backing the confirmation card SHALL differ by width class to avoid double-scrim regressions:

- **Compact width**: the confirmation is rendered via `androidx.compose.material3.AlertDialog`. Because the composer at Compact is a full-screen Nav3 route (not a Compose `Dialog`), the `AlertDialog` adds exactly one `Window`-level scrim — matching the M3 spec's single-dim layer.
- **Medium / Expanded widths**: the confirmation is rendered via `androidx.compose.ui.window.Popup` shaped as an M3 dialog card (rounded `Surface` with `tonalElevation`, `Modifier.padding`, and standard M3 dialog typography), NOT via `Dialog` / `AlertDialog`. Because the composer at Medium/Expanded is itself a Compose `Dialog` with its own `Window` and scrim, stacking a second `Dialog` on top would composite two scrims (no `scrimColor` knob exists on `DialogProperties`) and visibly darken the canvas beyond the M3 spec's single-dim layer. `Popup` does not add a scrim — it overlays as a content layer on the existing composer Dialog's Window, so the user sees one scrim total. The visual treatment is indistinguishable from `AlertDialog`; only the underlying Compose primitive differs.

A future contributor MUST NOT swap the Medium/Expanded `Popup` for a `Dialog` / `AlertDialog` without simultaneously solving the double-scrim issue (e.g., a hand-rolled Dialog with `WindowManager.LayoutParams.dimAmount = 0f` on the inner Window). The `Popup` choice is an intentional Compose-implementation detail in service of the M3 visual spec, not an arbitrary primitive pick.

#### Scenario: Confirmation appears for non-empty draft

- **WHEN** `state.text == "draft text"` and the system back-press is received
- **THEN** a `ComposerDiscardDialog` is shown overlaid on the composer with `Cancel` and `Discard` actions

#### Scenario: Cancel action dismisses the confirmation, keeps the composer

- **WHEN** the discard confirmation is shown and the user taps `Cancel`
- **THEN** the confirmation is dismissed, the composer remains visible, and `LocalMainShellNavState` is NOT mutated

#### Scenario: Discard action dismisses the composer

- **WHEN** the discard confirmation is shown and the user taps `Discard`
- **THEN** the composer is dismissed: at Compact, the screen Composable invokes `LocalMainShellNavState.current.removeLast()`; at Medium/Expanded, the `MainShell`-scoped composer-launcher state holder transitions to `Closed`

#### Scenario: Compose primitive at Compact is `AlertDialog`

- **WHEN** the source of `ComposerDiscardDialog` is inspected and the active `WindowWidthSizeClass` is `COMPACT`
- **THEN** the rendered confirmation uses `androidx.compose.material3.AlertDialog` (or a thin wrapper directly delegating to it)

#### Scenario: Compose primitive at Medium/Expanded is `Popup`, not `Dialog`

- **WHEN** the source of `ComposerDiscardDialog` is inspected and the active `WindowWidthSizeClass` is `MEDIUM` or `EXPANDED`
- **THEN** the rendered confirmation uses `androidx.compose.ui.window.Popup` wrapping an M3 `Surface`-based dialog card, and does NOT use `androidx.compose.material3.AlertDialog` or `androidx.compose.ui.window.Dialog`

#### Scenario: Visible scrim density matches the M3 single-dim spec

- **WHEN** the discard confirmation is shown overlaid on the composer at any width class
- **THEN** the visible scrim covering the area outside the confirmation card is exactly one M3 scrim layer in luminance — equivalent to the composer's solo scrim at Medium/Expanded, or the `AlertDialog`'s solo scrim at Compact — and does NOT visibly darken further when the confirmation appears at Medium/Expanded (which would indicate two stacked Dialog scrims)

#### Scenario: Back-press ignored while submitting

- **WHEN** `state.submitStatus == ComposerSubmitStatus.Submitting` and the system back-press is received
- **THEN** no confirmation is shown and `LocalMainShellNavState` is not mutated

### Requirement: Keyboard auto-focuses the input on screen entry

The system SHALL request focus on the composer text field on first composition such that the IME is visible without user interaction. The screen Composable MUST attach a `FocusRequester` to the text field and call `requestFocus()` from a `LaunchedEffect(Unit)` block. This behavior MUST hold for both new-post and reply modes.

#### Scenario: IME opens on entry

- **WHEN** `ComposerScreen` enters composition for the first time
- **THEN** the text field has focus and `LocalSoftwareKeyboardController.current.show()` has been invoked (or focus alone is sufficient to open the IME on the test device)

### Requirement: Material 3 Expressive treatment for the Post button and counter

The system SHALL render the Post action as an expressive M3 component (FilledButton or expressive FAB) whose visual treatment morphs across submit states:

- `Idle` (with valid input): standard expressive filled-button presentation.
- `Submitting`: button morphs to display a wavy M3 progress indicator inline; tap is disabled.
- `Error`: button returns to enabled-filled but the inline error state is reflected in surface/border tone.

The character counter MUST render as a circular progress arc whose tonal band shifts at 240 graphemes (warning) and 290 graphemes (error). At `graphemeCount > 300`, the input field MUST also adopt the M3 error border tone.

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

The system SHALL contribute the `ComposerRoute` entry via a Hilt `@Provides @IntoSet @MainShell EntryProviderInstaller` declared in `:feature:composer:impl`. The provider MUST NOT also be qualified `@OuterShell`. The contributed entry MUST resolve `ComposerScreen` against the `ComposerRoute` argument by handing it to `ComposerViewModel.Factory.create(route)` via the assisted-inject `creationCallback` of `hiltViewModel(...)`. This entry is the hosting path for **Compact** widths only — at Medium/Expanded widths the composer is overlaid as a Dialog (see *Adaptive container* requirement) and is not pushed onto `NavDisplay`.

#### Scenario: MainShell qualifier on the entry installer

- **WHEN** `:feature:composer:impl` Hilt modules are inspected
- **THEN** exactly one `EntryProviderInstaller` provider exists, qualified with `@MainShell` and not with `@OuterShell`

#### Scenario: Tab-internal push lands the composer at Compact

- **WHEN** the active `WindowWidthSizeClass` is `COMPACT` and code inside `MainShell` invokes `LocalMainShellNavState.current.add(ComposerRoute())`
- **THEN** the inner `NavDisplay` resolves the entry and renders `ComposerScreen` filling the pane

### Requirement: Adaptive container — full-screen route on Compact, centered Dialog on Medium/Expanded

The system SHALL host `ComposerScreen` in a width-class-adaptive container:

- **Compact width** (`WindowWidthSizeClass.COMPACT`): the launching surface (Feed FAB, in-feed reply affordance) MUST push `ComposerRoute` onto `LocalMainShellNavState.current`. `ComposerScreen` renders inside its own `Scaffold` filling the inner `NavDisplay` pane.
- **Medium / Expanded widths** (`WindowWidthSizeClass.MEDIUM` and `EXPANDED`): the launching surface MUST NOT push onto `NavDisplay`. Instead it MUST toggle a `MainShell`-scoped composer-launcher state holder (e.g. `ComposerOverlayState`). `MainShell` MUST observe this state and overlay a `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false))` whose content wraps `ComposerScreen` in `Modifier.widthIn(max = 640.dp)`. The Dialog's default scrim is the only background dimming; `ComposerScreen` itself is the same Composable used at Compact, with no Compose-level mode flag.

`ComposerViewModel` MUST be obtained via `hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(creationCallback = { factory -> factory.create(route) })` in both code paths. The `route: ComposerRoute` argument is constructed at the launching surface (Compact: the `entry<ComposerRoute>` block receives it from Nav3; Medium/Expanded: the `MainShell`-scoped composer-launcher state holder constructs `ComposerRoute(replyToUri = state.replyToUri)` at overlay time). The VM's constructor MUST NOT branch on width class.

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

The "Discard draft?" confirmation dialog SHALL be implemented such that its action set is supplied as a list/lambda-collection rather than hard-coded button slots. V1 ships exactly two actions (`Cancel`, `Discard`) per the M3 full-screen-dialog discard pattern; the dialog implementation MUST NOT statically encode a two-button layout that would resist the addition of a third action (e.g. `Save as draft`) in the follow-up `:core:drafts` epic. The dialog MUST be implementable such that adding a third action is a pure addition to a list, not a layout rewrite. The same data-driven action list MUST drive both the Compact `AlertDialog` rendering and the Medium/Expanded `Popup`-based rendering.

#### Scenario: Action set is data-driven

- **WHEN** the source of the discard confirmation dialog is inspected
- **THEN** its actions are rendered from an iterable / list parameter (e.g. `actions: ImmutableList<ComposerDialogAction>`) rather than hard-coded `confirmButton` / `dismissButton` slots

#### Scenario: V1 ships exactly two actions

- **WHEN** the discard dialog is rendered in V1
- **THEN** the rendered action list contains exactly two items: `Cancel` and `Discard`

#### Scenario: Same action list drives both renderings

- **WHEN** the same `ImmutableList<ComposerDialogAction>` is passed into the discard confirmation at Compact and at Medium/Expanded
- **THEN** the rendered actions, their labels, their order, and their `onClick` lambdas are identical across both width classes — only the wrapping Compose primitive differs (`AlertDialog` vs. `Popup`)

### Requirement: Top-bar action row reserves space for a future drafts entry point

`ComposerScreen`'s top app bar SHALL position its V1 actions (`close` on the navigation slot, `post` or post-related controls on the action slot) such that one additional `IconButton`-equivalent slot can be inserted between them in a follow-up change without forcing a relayout of either action. The reservation is documented in code via a top-level `// reserved for drafts entry point (see :core:drafts follow-up)` comment near the action row, and the layout MUST NOT pin actions to the absolute edges of the top bar in a way that would push existing actions off-screen when a new icon is added.

#### Scenario: Top-bar layout has room to grow

- **WHEN** a hypothetical third `IconButton` is inserted between the existing actions in `ComposerScreen`'s top app bar
- **THEN** all three actions render fully visible at Compact width without truncation or overflow

### Requirement: `ComposerViewModel` constructor leaves room for a future `DraftRepository`

`ComposerViewModel`'s constructor SHALL be declared such that adding a `draftRepository: DraftRepository` parameter in a follow-up change is a pure addition — no existing parameter is repositioned, renamed, or removed. V1 ships with one `@Assisted` parameter (`route: ComposerRoute`) plus two Hilt-resolved parameters (`PostingRepository` and `ParentFetchSource`). The contract that matters is *append-only*: future Hilt-resolved additions go to the end of the parameter list, after `parentFetchSource`. The type MUST remain `@HiltViewModel(assistedFactory = ComposerViewModel.Factory::class)`-annotated so Hilt resolves new dependencies without binding-graph rewiring.

#### Scenario: V1 constructor signature

- **WHEN** the source of `ComposerViewModel` is inspected
- **THEN** its primary constructor declares one `@Assisted` parameter (`route: ComposerRoute`) followed by two `@Inject`-resolved parameters (`postingRepository: PostingRepository` and `parentFetchSource: ParentFetchSource`), in that order, via `@AssistedInject`

### Requirement: FAB component on launching surfaces is badge-wrappable

Any FloatingActionButton that launches the composer (V1: the Feed-tab compose FAB) SHALL be implemented using a component that supports `BadgedBox` wrapping — concretely, `FloatingActionButton`, `LargeFloatingActionButton`, or `SmallFloatingActionButton`. The launching FAB MUST NOT use `ExtendedFloatingActionButton` with a text label, because the M3 spec for `BadgedBox` does not cleanly support badging an extended/labeled FAB. This constraint reserves the ability to add a "drafts available" badge in the follow-up `:core:drafts` epic.

#### Scenario: Feed compose FAB is icon-only and wrappable

- **WHEN** the source of `feature-feed:impl`'s compose FAB is inspected
- **THEN** the FAB is one of `FloatingActionButton`, `LargeFloatingActionButton`, or `SmallFloatingActionButton` — it is NOT `ExtendedFloatingActionButton`

### Requirement: `:feature:composer:impl` follows the standard module conventions

The `:feature:composer:impl` module SHALL apply the `nubecita.android.feature` convention plugin. It MUST declare a unique `namespace` of `net.kikin.nubecita.feature.composer.impl`. It MUST depend on `:feature:composer:api`, `:core:posting`, `:core:common:navigation`, `:core:designsystem`, and (transitively or directly) the atproto SDK identifier types only as needed; it MUST NOT depend on `:app`. The `:feature:composer:api` module SHALL apply `nubecita.android.library` and depend only on `:core:common:navigation`.

#### Scenario: impl applies the feature convention plugin

- **WHEN** `:feature:composer:impl/build.gradle.kts` is inspected
- **THEN** the `plugins { }` block applies `nubecita.android.feature`

#### Scenario: api applies the library convention plugin

- **WHEN** `:feature:composer:api/build.gradle.kts` is inspected
- **THEN** the `plugins { }` block applies `nubecita.android.library` and does NOT apply `nubecita.android.library.compose` or `nubecita.android.hilt`

### Requirement: Screenshot test contract covers four content states plus an adaptive-Dialog baseline

The system SHALL ship Compose screenshot tests covering these five fixtures, each rendered in Light and Dark themes (10 images total):

- **Empty composer** (new-post mode, no text, no attachments, idle, Compact width).
- **Near-limit composer** (new-post mode, `graphemeCount` in the warn band — fixture pins to 295 — no attachments, idle, Compact width).
- **Composer with attached images** (new-post mode, 3 attached image fixtures, short text, idle, Compact width).
- **Reply mode** (reply mode, `replyParentLoad == ParentLoadStatus.Loaded(...)`, parent post card rendered above the input, short text, idle, Compact width).
- **Empty composer at Expanded width as Dialog overlay** (new-post mode, no text, no attachments, idle, rendered inside the adaptive Dialog with `widthIn(max = 640.dp)` over a stub backing surface to validate the centered/scrim treatment).

All fixtures MUST use deterministic `ComposerState` values (no live data, no real picker URIs), MUST run under `android.experimental.enableScreenshotTest`, and MUST be co-located with the `:feature:composer:impl` `screenshotTest` source set. The remaining width-class × content-state combinations (near-limit at Expanded, with-images at Expanded, reply at Expanded, plus all foldable postures) are explicitly deferred to follow-up changes — V1 pins one Expanded-width fixture as the adaptive-Dialog baseline rather than a full matrix.

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

#### Scenario: All fixtures present in both themes

- **WHEN** the screenshot test directory is enumerated
- **THEN** there are exactly 10 fixture images: {empty, near-limit, with-images, reply, empty-expanded-dialog} × {light, dark}

### Requirement: Unit-test coverage for the composer state machine

The system SHALL ship JUnit unit tests for `ComposerViewModel` covering at minimum:

- Initial state in new-post mode.
- Initial state in reply mode (Loading → Loaded path).
- Initial state in reply mode (Loading → Failed path).
- `TextChanged` updates `text`, `graphemeCount`, and `isOverLimit`.
- Grapheme counting boundary at 300 with emoji input.
- `AddAttachments` cap enforcement at 4.
- `RemoveAttachment` mutation.
- `Submit` transitions Idle → Submitting → Success on happy path.
- `Submit` transitions Idle → Submitting → Error on `PostingRepository` failure.
- `Submit` is a no-op when `isOverLimit`.
- `Submit` is a no-op when reply parent is not `Loaded`.
- Retry from `Error` re-enters `Submitting`.
- Reply mode `Submit` carries both `parentRef` and `rootRef` to the repository.

Tests MUST use a fake `PostingRepository` and a fake parent-fetch source — never the live atproto client.

#### Scenario: Test suite enumerates the canonical state transitions

- **WHEN** the `:feature:composer:impl` test source set is enumerated
- **THEN** at least one `@Test` method exists for each item in the list above (named or annotated such that the mapping is unambiguous)

#### Scenario: Tests run without network

- **WHEN** the composer unit tests are executed under `./gradlew :feature:composer:impl:testDebugUnitTest`
- **THEN** no real `HttpClient` or atproto `XrpcClient` is instantiated and the suite passes offline
