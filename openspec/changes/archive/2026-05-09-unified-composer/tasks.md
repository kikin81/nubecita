## 1. `:core:posting` capability — data layer

- [x] 1.1 Create `:core:posting` module skeleton: `core/posting/build.gradle.kts` applies `nubecita.android.library` + `nubecita.android.hilt`, namespace `net.kikin.nubecita.core.posting`, AndroidManifest.xml shell. Register the module in `settings.gradle.kts`.
- [x] 1.2 Define `PostingRepository` interface in `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/PostingRepository.kt`: `suspend fun createPost(text: String, attachments: List<ComposerAttachment>, replyTo: ReplyRefs?): Result<AtUri, ComposerError>`. Define `ReplyRefs(parent: StrongRef, root: StrongRef)`, `ComposerAttachment(uri: Uri, mimeType: String)`, and `sealed interface ComposerError` (variants: `Network`, `UploadFailed(cause: Throwable)`, `RecordCreationFailed(cause: Throwable)`, `ParentNotFound`, `Unauthorized`).
- [x] 1.3 Implement `DefaultPostingRepository` against the atproto SDK: parallel blob uploads via `coroutineScope { ... awaitAll() }`, then `app.bsky.feed.post` record creation with embedded image refs and optional `reply` field. Hilt-bind `@Provides` in a new `PostingModule`.
- [x] 1.4 Unit tests: `DefaultPostingRepositoryTest` with a fake atproto client. Cover happy path (text only), images-then-record ordering (assert all `uploadBlob` calls complete before `createPost` begins), blob upload failure aborts before `createPost`, reply-mode carries both `parent` and `root` refs to the record.
- [x] 1.5 Run `./gradlew :core:posting:testDebugUnitTest spotlessCheck lint` — ship in **PR 1**.

## 2. `:feature:composer:api` module — `NavKey` only

- [x] 2.1 Create `:feature:composer:api` module: `feature/composer/api/build.gradle.kts` applies `nubecita.android.library` (no Compose, no Hilt), namespace `net.kikin.nubecita.feature.composer.api`. Register in `settings.gradle.kts`.
- [x] 2.2 Define `data class ComposerRoute(val replyToUri: AtUri? = null) : NavKey` in `ComposerRoute.kt`. `Parcelize` annotation if NavKey requires it; depend only on `:core:common:navigation` and the `AtUri` type.
- [x] 2.3 Unit test asserting `:feature:composer:api`'s only NavKey-implementing type is `ComposerRoute` (reflective scan via `kotlin-reflect` test util, or a simple `org.junit.jupiter` test inspecting compiled classes).
- [x] 2.4 Run `./gradlew :feature:composer:api:testDebugUnitTest spotlessCheck lint` — ship in **PR 2**.

## 3. `:feature:composer:impl` skeleton — VM + state machine, no UI

- [x] 3.1 Create `:feature:composer:impl` module: applies `nubecita.android.feature` convention plugin, namespace `net.kikin.nubecita.feature.composer.impl`. Depend on `:feature:composer:api`, `:core:posting`, `:core:common:navigation`, `:core:designsystem`. Register in `settings.gradle.kts`.
- [x] 3.2 Define `ComposerState`, `ComposerEvent`, `ComposerEffect` (sealed interfaces extending `UiState` / `UiEvent` / `UiEffect`), plus `sealed interface ComposerSubmitStatus` and `sealed interface ParentLoadStatus` exactly as the spec dictates. All in `state/` package.
- [x] 3.3 Implement `ComposerViewModel` extending `MviViewModel<ComposerState, ComposerEvent, ComposerEffect>`, `@HiltViewModel` annotated, constructor `@Inject`-resolved `(savedStateHandle: SavedStateHandle, postingRepository: PostingRepository)` — exactly two params per the forward-compat constraint. Implement reducers for `TextChanged`, `AddAttachments`, `RemoveAttachment`, `Submit`, `RetryParentLoad`. Implement parent-fetch on `init` when `replyToUri` is non-null (stub the parent-fetch source for now; wire to a real repo in PR 6).
- [x] 3.4 Add `ParentFetchSource` interface (placeholder for PR 6) so the VM compiles. Hilt-bind a stub implementation that always returns `Loading` indefinitely — replaced in PR 6.
- [x] 3.5 Unit tests covering every scenario in the spec's *Unit-test coverage for the composer state machine* requirement: initial state both modes, TextChanged, grapheme-count boundary at 300 with emoji, AddAttachments cap at 4, RemoveAttachment, Submit happy/error paths, Submit no-op when over-limit, Submit no-op when reply parent not Loaded, retry from Error, reply submission carries both refs.
- [x] 3.6 Run `./gradlew :feature:composer:impl:testDebugUnitTest spotlessCheck lint` — ship in **PR 3**.

## 4. Composer screen Composable — text, counter, Post button

- [x] 4.1 Implement `ComposerScreen` in `:feature:composer:impl` `ui/` package: text field with `FocusRequester` + `LaunchedEffect(Unit) { focusRequester.requestFocus() }` for IME auto-focus. Use `OutlinedTextField` or M3 Expressive equivalent. Read state via `hiltViewModel<ComposerViewModel>()` + `collectAsStateWithLifecycle()`.
- [x] 4.2 Implement the circular character counter with M3 expressive tonal bands at 240 / 290 / 300. Use `CircularProgressIndicator` or hand-rolled `Canvas` arc. Color tokens: primary (0–239), tertiary (240–289), error (290–300+). Over-limit also flips the text field outline tone to error.
- [x] 4.3 Implement the Post button: M3 `FilledButton` (or expressive equivalent). `enabled` derived from `state.text.isNotBlank() || state.attachments.isNotEmpty()` AND `!state.isOverLimit` AND submit not in progress. Tap dispatches `ComposerEvent.Submit`. While `submitStatus is Submitting`, show inline M3 wavy progress indicator inside the button and disable taps.
- [x] 4.4 Wire `ComposerEffect` collection in a single `LaunchedEffect`: `NavigateBack` → `LocalMainShellNavState.current.removeLast()` (Compact only — Medium/Expanded uses a different launcher path); `OnSubmitSuccess(uri)` → close composer; `ShowError(text)` → snackbar.
- [x] 4.5 Register the `@MainShell` `EntryProviderInstaller` for `ComposerRoute` in a Hilt module. Multibinding into the @MainShell EntryProviderInstaller set.
- [x] 4.6 Add string resources: `composer_post_action`, `composer_close_action`, `composer_chars_remaining` (for talkback), `composer_error_generic`.
- [x] 4.7 Compose previews + screenshot fixtures: `ComposerScreenEmpty` (Light + Dark) and `ComposerScreenNearLimit` at 295 chars (Light + Dark). 4 fixtures.
- [x] 4.8 ViewModel + screen unit tests for the focus + counter + Post-enabled logic.
- [x] 4.9 Run `./gradlew :feature:composer:impl:validateDebugScreenshotTest :feature:composer:impl:testDebugUnitTest spotlessCheck lint` — ship in **PR 4**.

## 5. Image attachment — picker, chips, blob upload

- [x] 5.1 Add the `PickMultipleVisualMedia` `ActivityResultContracts` launcher to `ComposerScreen`. Configure `maxItems = 4 - state.attachments.size` so the picker enforces remaining capacity. Result URIs feed into `ComposerEvent.AddAttachments`.
- [x] 5.2 Render attachment chips in a horizontally scrolling row below the text field: Coil-loaded thumbnails, X icon to remove (dispatches `ComposerEvent.RemoveAttachment(index)`). Hide / disable the "add image" affordance when `state.attachments.size == 4`.
- [x] 5.3 Wire blob upload into `DefaultPostingRepository.createPost`: parallel `awaitAll()` of blob uploads before record creation. Failures route to `ComposerError.UploadFailed`.
- [x] 5.4 Screenshot fixture: `ComposerScreenWithImages` showing 3 deterministic image placeholders attached + short text (Light + Dark) — 2 more fixtures, total now 6.
- [x] 5.5 Unit + Compose tests for picker `maxItems` derivation, reducer cap enforcement, attachment removal.
- [x] 5.6 Run the full gradle verification — ship in **PR 5**.

## 6. Reply mode — parent fetch + parent-post card

- [x] 6.1 Implement `ParentFetchSource` against the atproto SDK using `app.bsky.feed.getPostThread` with `depth = 0` and a parent-height sufficient to derive the root. Return a domain `ParentPostUi` carrying `parentRef` and `rootRef`. Hilt-bind in a `ComposerModule`. Replace the PR 3 stub.
- [x] 6.2 In `ComposerScreen`, render the parent-post card above the text field when `state.replyParentLoad is ParentLoadStatus.Loaded`. Use the existing `:core:designsystem` post-card primitives where possible (or build a stripped read-only variant).
- [x] 6.3 Render the loading skeleton when `replyParentLoad is Loading` and an inline retry tile (with `RetryParentLoad` button) when `Failed`.
- [x] 6.4 Verify reply submission carries `parentRef` and `rootRef` to `PostingRepository.createPost`. Hard-disable the Post button when `replyParentLoad !is Loaded`.
- [x] 6.5 Screenshot fixture: `ComposerScreenReplyMode` (Light + Dark) — 2 more fixtures, total now 8.
- [x] 6.6 Unit tests: parent-fetch lifecycle, retry from Failed, submit blocked when not Loaded.
- [x] 6.7 Run gradle verification — ship in **PR 6**.

## 7. Adaptive container — Dialog overlay at Medium/Expanded

- [x] 7.1 Add `ComposerLauncher` state holder + `LocalComposerLauncher` `CompositionLocal` in `:feature:composer:impl` (or `:core:common:navigation` if it needs cross-module access from `feature-feed`). State: `sealed interface ComposerOverlayState { Closed; Open(replyToUri: AtUri?) }`.
- [x] 7.2 In `MainShell`, observe the launcher state. When `Open`, render `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Box(Modifier.widthIn(max = 640.dp)) { ComposerScreen(replyToUri) } }`.
- [x] 7.3 Wire the launcher's `show(replyToUri)` to be the Medium/Expanded path; Compact still uses `LocalMainShellNavState.current.add(ComposerRoute(...))`. Add the width-class branching helper (`launchComposer(replyToUri)`) that the FAB and reply affordances will call in PRs 8 and 9.
- [x] 7.4 Screenshot fixture: `ComposerScreenEmptyExpandedDialog` (Light + Dark) — rendered inside the adaptive Dialog over a stub backing surface, validating the centered/scrim treatment. 2 more fixtures, total now 10.
- [x] 7.5 UI test asserting the launcher branching: at Compact, FAB-launching helper pushes onto NavDisplay; at Expanded, FAB-launching helper toggles the launcher state.
- [x] 7.6 Run gradle verification — ship in **PR 7**.

## 8. Discard confirmation — `AlertDialog` (Compact) + `Popup` (Expanded)

- [x] 8.1 Implement `ComposerDiscardDialog` Composable in `:feature:composer:impl` taking `actions: ImmutableList<ComposerDialogAction>` and an `onDismiss` lambda. Internally branch on `currentWindowAdaptiveInfo()`: Compact uses `AlertDialog`, Medium/Expanded uses `Popup` wrapping an M3 `Surface` card with rounded corners + `tonalElevation`.
- [x] 8.2 Wire back-press handling in `ComposerScreen`: `BackHandler(state.text.isNotBlank() || state.attachments.isNotEmpty())` shows the confirmation; back-press is ignored entirely while `submitStatus is Submitting`. Empty draft back-press skips the confirmation.
- [x] 8.3 Action set: V1 ships exactly `Cancel` (dismiss confirmation, keep composer) and `Discard` (close composer — at Compact pop NavDisplay, at Medium/Expanded set launcher to `Closed`). M3 destructive tone on Discard.
- [x] 8.4 Visual regression check: take Light + Dark screenshots of confirmation overlaid at Compact and at Expanded, eyeball the scrim density (or write a pixel-luminance assertion in a screenshot test that confirms the canvas-outside-card is one M3 dim layer, not two).
- [x] 8.5 UI tests: confirmation appears for non-empty draft, Cancel keeps composer, Discard closes, back-press ignored during Submitting.
- [x] 8.6 Run gradle verification — ship in **PR 8**.

## 9. Feed FAB swap — scroll-to-top → compose

- [x] 9.1 In `feature/feed/impl/.../FeedScreen.kt`, delete `SCROLL_TO_TOP_FAB_THRESHOLD`, the `derivedStateOf { ... }` visibility gate, the `KeyboardArrowUp` icon import, the `AnimatedVisibility` wrapper around the FAB, and the FAB's `onClick { scrollScope.launch { listState.animateScrollToItem(0) } }`. **Preserve** the `LocalScrollToTopSignal` collector — that's the home-tab retap path and stays.
- [x] 9.2 Replace the FAB content with a compose FAB: at Compact `FloatingActionButton`, at Medium/Expanded `LargeFloatingActionButton`. Icon `Icons.Default.Edit`, contentDescription from new string resource `R.string.feed_compose_new_post`. Visible only when `viewState is FeedScreenViewState.Loaded`.
- [x] 9.3 FAB `onClick` invokes the width-class-conditional `launchComposer(replyToUri = null)` helper from PR 7.
- [x] 9.4 Delete the existing `FeedScreenLoadedWithFabVisibleScreenshot` fixture. Add `FeedScreenLoadedWithComposeFabScreenshot` Light + Dark pair capturing the compose FAB at rest over a populated feed.
- [x] 9.5 Re-baseline `loaded-light` / `loaded-dark` if they captured no FAB at scroll position 0 (FAB is now visible at position 0).
- [x] 9.6 UI tests for both width-class branches of the FAB onClick.
- [x] 9.7 Run gradle verification — ship in **PR 9**.

## 10. Per-post reply affordance

- [x] 10.1 Add a "reply" tap target to each `PostCard` in `feature-feed:impl`'s loaded list (existing card action row, no new card-level state). Surface as an icon button with `R.string.feed_post_reply` content description.
- [x] 10.2 The tap path does NOT pass through `FeedViewModel`. Screen-level handler invokes `launchComposer(replyToUri = post.uri)` directly from the card's `onClick` lambda.
- [x] 10.3 UI tests: reply tap on a `PostCard` whose `PostUi.uri` is X dispatches `launchComposer(replyToUri = X)`; affordance is present on every loaded post.
- [x] 10.4 Run gradle verification — ship in **PR 10**.

## 11. Final integration verification

- [x] 11.1 Manual smoke on a Pixel 8 emulator (Compact) and a Pixel Tablet emulator (Expanded): new-post happy path, reply happy path, character-limit enforcement, image attachment with 1/3/4 images, discard confirmation flows, network-failure path.
- [x] 11.2 Run the aggregated verification: `./gradlew :feature:composer:impl:validateDebugScreenshotTest :feature:composer:impl:testDebugUnitTest :core:posting:testDebugUnitTest :feature:feed:impl:validateDebugScreenshotTest :feature:feed:impl:testDebugUnitTest spotlessCheck lint`.
- [x] 11.3 Open the unified PR for the change archive (or confirm all 10 PRs are merged) and run `openspec archive unified-composer` to fold the deltas back into `openspec/specs/`.
