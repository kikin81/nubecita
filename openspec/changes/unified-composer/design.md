## Context

Nubecita today is a read-only client. Authoring is the next P0 step — without it the app cannot retain users for any session that involves wanting to participate in a conversation. The same UI surface needs to handle both new posts and replies, because Bluesky's mental model treats them as the same record type (`app.bsky.feed.post`) — only the optional `reply` ref differs. Building two separate composers would duplicate state, layout, validation, screenshot fixtures, and the M3 expressive treatment for no payoff.

The project's MVI baseline is already in place (`MviViewModel<S, E, F>`, flat `UiState`, sealed `UiEvent`/`UiEffect`, tab-internal navigation via `MainShellNavState` from `CompositionLocal`). The data layer baseline (`atproto-kotlin` SDK, session-scoped XRPC client, typed records) is in place as of the `atproto-networking` and `core-auth-*` capabilities. This change builds *on top* of those — it does not introduce new architectural primitives.

Stakeholders: end users (compose UX), `:feature:feed` and (eventually) `:feature:postdetail` owners (entry-point integration), `:core:auth` owner (session token plumbing for posting requests).

## Goals / Non-Goals

**Goals:**

- One screen, one ViewModel, one set of fixtures for both new-post and reply modes.
- Submission UX that visibly succeeds, visibly fails, and never silently drops a draft mid-flight.
- Character-count feedback that is glanceable without reading a number — the counter itself encodes "you are getting close" via M3 expressive color and motion.
- Image attachment that uses the system's modern photo picker (`PickVisualMedia`) — no runtime storage permissions, no custom gallery.
- Reply mode that renders the parent post above the input so the author has visible context, but does not let them edit it or accidentally pull it into the new record.
- 100% testable contract: every state transition has a unit test; every visual state has a screenshot pair.

**Non-Goals:**

- Video, GIFs, polls, threadgate, alt-text editing, mention/link/hashtag toolbars, quote posts.
- Local persistence of drafts. Drafts are in-memory only.
- Process-death survival. No `SavedStateHandle` plumbing in V1.
- Inline thread composition (chained replies in a single submission). One record per submit.
- Rich-text formatting buttons (bold / italic / hashtag toolbar UI). Mention and URL facets are parsed automatically by `:core:posting` on submit (see `nubecita-wtq.11` and the Facet parsing decision section below); bold/italic/hashtag facets remain out of V1 because they need toolbar UX before they're useful.

## Decisions

### One route, one screen, conditional state

`ComposerRoute(replyToUri: String? = null)` is the sole `NavKey` exposed by `:feature:composer:api`. The `replyToUri` field is the *only* mode discriminator. Stored as `String` (not the lexicon `AtUri` value class) to keep `:feature:composer:api` atproto-runtime-free — the VM lifts it to `AtUri` at the call site to the atproto runtime. The screen branches once at the state level (`state.replyParent` is non-null in reply mode) and once at the layout level (parent-post card is hidden in new-post mode). No separate `NewPostRoute` / `ReplyRoute` types.

**Alternatives considered:**

- *Two separate routes (`NewPostRoute`, `ReplyRoute`).* Rejected — duplicates ~95% of state and layout, doubles screenshot fixtures, and forces feed/postdetail callers to know which sealed variant to construct rather than just `ComposerRoute(replyToUri = post.uri)`.
- *One route, two screens (`NewPostScreen`, `ReplyScreen`) reading the same VM.* Rejected — the layouts differ only in whether one card is visible; a `?.let { ParentPostCard(it) }` keeps both flows in one composable without ceremony.

### Submission lifecycle as a sealed status sum, not flat booleans

The proposal calls out that `ComposerState` carries a "submission load status (Idle, Submitting, Success, Error)". These are mutually exclusive — you cannot be Submitting and Error simultaneously, and Success is terminal. Per `CLAUDE.md`'s rule ("sealed status sum when the flags are mutually exclusive"), this is modeled as:

```kotlin
sealed interface ComposerSubmitStatus {
  object Idle : ComposerSubmitStatus
  object Submitting : ComposerSubmitStatus
  object Success : ComposerSubmitStatus
  data class Error(val cause: ComposerError) : ComposerSubmitStatus
}
```

The Post button's visual treatment (`when (state.submitStatus)`) is exhaustive and the type system forbids invalid combinations like "submitting + error". The same applies to reply mode's parent-post fetch:

```kotlin
sealed interface ParentLoadStatus {
  object Loading : ParentLoadStatus
  data class Loaded(val post: ParentPostUi) : ParentLoadStatus
  data class Failed(val cause: ComposerError) : ParentLoadStatus
}
```

In new-post mode, the field is simply `null` — no "not applicable" sentinel.

**Alternatives considered:**

- *Flat `isSubmitting: Boolean` + `submitError: String?`.* Rejected — two booleans/nullables that must be kept consistent, exactly the smell `CLAUDE.md` calls out.
- *Async<T> / Result<T> wrapper types.* Rejected — explicitly listed as a non-goal of the MVI base.

### Posting layered as `:core:posting`, not folded into `:feature:composer:impl`

Even though the composer is the only V1 caller, the record-creation logic lives in a dedicated `:core:posting` module exposing a `PostingRepository` interface. Rationale: (a) future quote-post and direct-message capabilities will reuse it; (b) the composer screen has no business knowing about XRPC payload shapes or blob upload mechanics; (c) it lets the ViewModel test substitute a `FakePostingRepository` without touching atproto types. Keeps `:feature:composer:impl` UI-shaped and `:core:posting` data-shaped.

### Tab-internal navigation via `LocalMainShellNavState`, not Hilt-injected `Navigator`

Per repo convention, ViewModels do not inject `MainShellNavState`. The composer's `ComposerEffect.NavigateBack` and `ComposerEffect.NavigateToPost(AtUri)` are collected by the screen Composable, which calls `LocalMainShellNavState.current.removeLast()` and `add(...)` respectively. The `Navigator` (outer-shell) is not involved — composer always launches inside `MainShell`. The `EntryProviderInstaller` for the composer is qualified `@MainShell`.

### Character-count UX

300 is a hard ceiling enforced by AT Protocol's `app.bsky.richtext.facet` `MAX_GRAPHEMES` constant. The counter renders as a circular progress arc:

- **0–239 chars:** filled in the M3 primary tone.
- **240–289 chars:** filled in the M3 tertiary/warn tone (last 60 chars warning band).
- **290–300 chars:** filled in the M3 error tone.
- **>300 chars:** the Post button moves to disabled and the input is bordered in the M3 error tone. The state field `state.isOverLimit` is a derived flat boolean — fine here because it is independent of submission status (you can be over-limit and idle, or over-limit and have a stale error from a prior submit). The counter never silently truncates.

The grapheme count, not the UTF-16 length, is what bounds the 300 limit — emoji and combining characters count as one. The atproto-kotlin 5.3.0 SDK does NOT ship a grapheme-counting helper (an earlier draft of this design referenced a hypothetical SDK `RichText` helper — that helper does not exist). V1 wraps `java.text.BreakIterator.getCharacterInstance()` in a small `GraphemeCounter` utility inside `:feature:composer:impl`. JVM/Android Unicode-version skew on ZWJ family emoji is a known follow-up — the JVM's bundled tables predate Unicode 15+ emoji_zwj_sequences, so JVM unit tests cover platform-stable cases (ASCII boundary + BMP-pair emoji). Production Android (API 24+) uses the platform's ICU-backed `BreakIterator` which has up-to-date tables. Future swap to ICU4J or a Unicode-version-pinned grapheme-segmenter is filed as a backlog task; the contract on the counter ("Unicode extended grapheme cluster count") doesn't change.

### Facet parsing in `:core:posting` (mentions + URLs)

The Bluesky lexicon's `app.bsky.richtext.facet` is what makes `@alice.bsky.social` and `https://example.com` render as clickable in published records. Without facets, those tokens appear as plain text — a basic social-app expectation broken vs. the official client. The SDK does NOT auto-parse facets (an earlier draft of this design assumed it did — that was wrong). V1 does the parsing in the data layer.

Decision: facet parsing is V1 scope and lives in `:core:posting.DefaultPostingRepository.createPost`, not in the composer's UI layer. Tracked as `nubecita-wtq.11`.

Flow on submit:

1. Parse `text` for `@handle` patterns using the AT Protocol handle regex (per the docs at https://docs.bsky.app/docs/advanced-guides/posts).
2. Parse `text` for `https?://...` URL patterns using the recommended URL regex.
3. For each mention, call `app.bsky.identity.resolveHandle` (new XRPC dependency in `:core:posting` via `IdentityService`). Failed resolutions get **dropped silently** and the handle stays as plain text — the docs explicitly mandate this fallback.
4. Compute byte offsets, **not** character offsets. AT Protocol's `byteStart` / `byteEnd` are UTF-8 byte positions in the encoded text — emoji and multi-byte characters matter. Wrong here means facets render at the wrong positions (or the post is rejected). Encode via `text.toByteArray(Charsets.UTF_8)` and find offsets via byte-array regex matching.
5. Construct `app.bsky.richtext.facet` objects with `index.byteStart` / `byteEnd` and `features` carrying either `app.bsky.richtext.facet#mention` (with `did`) or `app.bsky.richtext.facet#link` (with `uri`).
6. Attach to `Post.facets` via `AtField.Defined(...)` when the parsed list is non-empty; `AtField.Missing` otherwise.

Why in `:core:posting` and not the VM:

- The VM (`:feature:composer:impl`) stays UI-shaped — it carries `text: String` in state, not `facets: List<...>`. Adding facet semantics to the VM means the state must round-trip through `SavedStateHandle` (process death) and stay consistent across reducer events. Putting parsing in the repo keeps the VM contract small.
- The `resolveHandle` XRPC call lives at the network boundary — natural fit for `:core:posting`.
- Tests for facet correctness (UTF-8 byte offsets, emoji handling, failed-resolution fallback) are pure-function and cleanly testable in `DefaultPostingRepositoryTest`-style fixtures.

Out of V1 (deferred):

- **Bold / italic facets**: lexicon supports them, but they need a toolbar UX to be useful, and we explicitly excluded mention/link toolbars from V1.
- **Hashtag facets** (`app.bsky.richtext.facet#tag`): same — useful only with discovery UX (tag-feed pages, tap-to-search), which isn't in scope.
- **Real-time facet rendering in the composer text input** (showing handles/URLs as styled BEFORE submit). This is a UI affordance over the typing experience; the underlying record correctness is already fixed by the data-layer parser. Composer V1 ships with plain-styled input.
- **Manual override** (user explicitly turns off auto-link for a token). Future polish; the docs don't standardize this and it's rare.

### Image attachment

Up to 4 images via `PickVisualMedia` (`ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)`). State holds `attachments: ImmutableList<ComposerAttachment>` where `ComposerAttachment` carries a content URI and a derived MIME type. Coil renders the previews. On submit, each attachment is uploaded via `com.atproto.repo.uploadBlob` *before* the `app.bsky.feed.post` record is created — the post record references the returned blob CIDs in its `embed` field. Failed blob uploads abort the whole submit and route a `ComposerError.UploadFailed` to `submitStatus`.

The 4-image cap is enforced by the picker's `maxItems`, by the ViewModel's `RemoveAttachment` / `AddAttachments` reducer, and by the disabled state of the "add image" button when `attachments.size == 4`. Three layers of enforcement is deliberate — the picker can be replaced in tests, the reducer is the source of truth, and the UI gate prevents the user from trying.

### Reply parent fetch

When `replyToUri` is non-null, on `init` the ViewModel kicks off a fetch: get the parent post (`app.bsky.feed.getPostThread` with `depth = 0`) to populate `state.replyParent`. The fetch lifecycle uses `ParentLoadStatus`:

- `Loading` → skeleton card above the input.
- `Loaded(post)` → render the post card.
- `Failed(cause)` → render an inline retry tile and disable the Post button (we will not let the user submit a reply when we cannot prove the parent exists, because the reply ref needs the parent's `cid`).

The reply ref structure (`reply.parent` and `reply.root`) requires the *root* of the thread, not just the immediate parent. `getPostThread` returns enough context to derive both. We carry the resolved `parentRef` and `rootRef` into the eventual `createRecord` call.

### Keyboard auto-focus

The text field grabs focus on screen entry via a `FocusRequester` triggered from a `LaunchedEffect(Unit)`. The IME opens automatically. This is a hard requirement — composer-then-tap-to-focus is friction we will not ship.

### Feed FAB slot swap, not addition

The `feature-feed` Scaffold already has a `floatingActionButton` slot occupied by a scroll-to-top FAB (added in `add-feed-scroll-to-top`, gated to appear once the user has scrolled five items past the top). That FAB becomes redundant the moment we ship `LocalScrollToTopSignal`, because the same behavior is already reachable by retapping the active home-tab in `MainShell` — a more discoverable, more native Android pattern than a corner FAB. Two FABs in the same Scaffold slot is impossible; two affordances for the same action is friction.

Decision: the Feed Scaffold's `floatingActionButton` slot is repurposed from scroll-to-top to compose-new-post. The compose FAB is visible whenever the feed is in `Loaded` state (i.e., the user has something to write *into* a feed of posts). It is not gated on scroll position — there is no "compose only after scrolling" UX argument, and gating would just hide a primary action.

The scroll-to-top *signal* path (`LocalScrollToTopSignal` collector inside `FeedScreen` calling `listState.animateScrollToItem(0)`) remains intact. We only delete the FAB visibility logic (`derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_TO_TOP_FAB_THRESHOLD }`), the threshold constant, the `KeyboardArrowUp` icon, the FAB's `onClick`, the `AnimatedVisibility` wrapper around it, and the screenshot fixture that captured the FAB-visible state.

The compose FAB's icon is `Icons.Default.Edit` (or M3's create-equivalent expressive icon when available); content description is a new localized `R.string.feed_compose_new_post`. Tapping it pushes `ComposerRoute()` (no `replyToUri`) onto `LocalMainShellNavState`. The Feed VM is not involved — the FAB's `onClick` calls the nav-state add directly, mirroring how existing tab-internal navigation buttons work.

**Alternatives considered:**

- *Keep both FABs (compose primary + scroll-to-top secondary, e.g., M3 split FAB or stacked).* Rejected — the home-tab retap fully covers scroll-to-top; a stacked or split FAB just adds visual weight to a redundant action. We would not have shipped scroll-to-top as a FAB if the tab-retap signal had landed first.
- *Compose FAB on every tab.* Rejected for V1 — Search and Profile have no obvious "post from here" semantic. Composing from those tabs is an MVI navigation story we can ship later if discoverability data calls for it. Feed-only matches Bluesky's reference UX.
- *Gate the compose FAB on scroll position (e.g., hide while scrolled deep).* Rejected — composing is the primary creative action; hiding it during reading creates a "where did the button go" moment whenever the user scrolls back up.

### Adaptive container: full-screen route on Compact, centered Dialog on Medium/Expanded

The composer's container behavior branches on `WindowWidthSizeClass`:

- **Compact** (phones in any orientation, `< 600dp` wide): `ComposerRoute` is pushed onto the inner `NavDisplay` and `ComposerScreen` renders inside a normal `Scaffold` filling the pane. The route is added via `LocalMainShellNavState.current.add(ComposerRoute(...))`. This is the existing path; nothing about the back-stack story changes.
- **Medium / Expanded** (`>= 600dp` wide — tablets, unfolded foldables, large landscape phones): the launching surface (Feed FAB, post-card reply affordance) does NOT push onto `NavDisplay`. Instead, a `MainShell`-scoped composer-launcher state holder is toggled, and `MainShell` overlays a `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` whose content is `ComposerScreen` wrapped in `Modifier.widthIn(max = 640.dp)`. The Dialog renders its own scrim. Compose's `Dialog` is a top-level compositing primitive, so the underlying `MainShell` content stays composed beneath the scrim — preserving feed/thread context the way Bluesky web's modal does.

The 640dp cap matches `BottomSheetDefaults.SheetMaxWidth`, the M3 default for adaptive containers, and lands in the upper end of the M3 dialog spec range (560dp standard, up to ~720dp for content-heavy variants). Wider would feel like a wasted canvas; narrower would push images and parent-post previews into uncomfortably tight columns at three-image rows.

Critically: `ComposerScreen` itself is **the same Composable in both modes**. Only the *launcher* differs, not the screen. ViewModel, state machine, screenshot fixtures, and event/effect plumbing are identical. The launching dichotomy is a `MainShell`-level decision implemented as:

```kotlin
fun launchComposer(replyToUri: String?) {
    if (currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT) {
        navState.add(ComposerRoute(replyToUri))
    } else {
        composerLauncher.show(replyToUri)
    }
}
```

The `composerLauncher` is a tiny `MainShell`-scoped state holder (`var composerOverlay: ComposerOverlayState by mutableStateOf(Closed)`) that `MainShell` reads to decide whether to render the Dialog overlay.

**Alternatives considered:**

- *Single `ModalBottomSheet` path on Medium/Expanded.* The free 640dp default-centering was tempting but the M3 spec rationale for content-creation surfaces ("components requiring an IME, no real-time saves, batch operation") aligns with Dialog, not with a bottom sheet. Sheets are M3's transient/contextual surface; dialogs are the modal-creation surface. We pick the surface that matches the M3 intent.
- *Reuse phone full-screen on tablet (current state of Bluesky-Android, Mastodon clients, Threads).* Rejected — wastes the canvas, reviews and bug trackers consistently flag this as the worst tablet experience for this app category. Also collides with our adaptive-nav investment in `NavigationSuiteScaffold` and list-detail scenes — being adaptive everywhere except the composer would be inconsistent.
- *`Dialog` with `usePlatformDefaultWidth = true` (relying on M3 to clamp width).* Refuted by research: `usePlatformDefaultWidth` pins width to the Android platform theme's `windowMinWidthMajor/Minor` (~320dp), not the M3 spec's 560dp. We must set it `false` and apply our own `widthIn(max = 640.dp)`.
- *Cross-NavKey container transform (FAB → composer via `SharedTransitionLayout`).* Deferred. Stable in `androidx.compose.animation` only as of Compose 1.10.x; wiring it cleanly across two NavKeys requires lifting `SharedTransitionLayout` to wrap the inner `NavDisplay` and threading `LocalNavAnimatedContentScope` through every screen — a structural shell change that costs more than V1 is willing to spend on a polish animation. A follow-up bd issue captures this for once the BOM stabilizes; V1 ships with the default Nav3 transition on Compact and a fade-in scrim on Medium/Expanded.

**Foldables (deferred to V1.1, not blocking V1):** Per the M3 adaptive guidance, `currentWindowAdaptiveInfo().windowPosture` exposes `Book` (hinge vertical, half-opened) and `Tabletop` (hinge horizontal). The eventual treatment: in Book posture split parent-post / preview on one half and editor+counter+attachments on the other, avoiding placing the textfield over `foldingFeature.bounds`; in Tabletop pin Post button and counter below the fold. V1 treats both postures as plain Medium/Expanded (centered Dialog with 640dp cap). A follow-up bd issue captures the posture-aware refinement.

### Forward-compatibility: drafts

Drafts are explicitly out of V1 (the unified-composer change ships text + images + reply with in-memory state only — process kill loses the draft). The official Bluesky client offers per-target draft persistence with auto-save, and we will land that capability in a follow-up `:core:drafts` epic. The V1 design must not preclude that follow-up. Five forward-compat commitments, made in writing now even though no draft code ships in V1:

1. **Per-target semantics, not single-slot.** The future `DraftRepository` keys drafts by `replyToUri ?: NEW_POST_SENTINEL`. A draft for `reply to @alice` is a separate record from a draft for `reply to @bob` is a separate record from the new-post draft. The alternative — a single global draft slot — invites a catastrophic context-collapse failure: a half-written sensitive reply to one author auto-populating a fresh reply to a different author. Per-target sandboxing is non-negotiable.

2. **Auto-save direction, debounced.** When drafts ship, the model is auto-save while typing (debounced ~500ms after the user stops typing) plus auto-save on dismiss. There is no "Save draft" button. The user never has to *remember* to save. This means the discard-on-back flow becomes a no-op in the drafts world (there's nothing to discard — it's already saved); the back-press confirmation dialog gets a "Discard saved draft?" copy update at that point, with three actions: {Discard, Keep editing, Continue later (= just close)}.

3. **Three-button-ready discard dialog.** The V1 confirmation dialog ships with two actions (`Discard` / `Keep editing`) but is implemented as a `ComposerEffect`-routed dialog that accepts an arbitrary action set rather than hard-coded buttons. Adding a third action later is a lambda-list extension, not a layout rewrite.

4. **`ComposerViewModel` constructor seam.** The V1 constructor signature is `class ComposerViewModel @Inject constructor(savedStateHandle: SavedStateHandle, postingRepository: PostingRepository, parentFetchSource: ParentFetchSource)`. Adding `draftRepository: DraftRepository` later is a one-line **appended** parameter — no other constructor parameter or call-site shift. The contract is append-only, not "exactly two params" (an earlier draft said the latter; corrected once the `ParentFetchSource` separation was designed). Hilt resolves new dependencies automatically.

5. **Top-bar slot reserved for the drafts entry point.** Bluesky's official client puts a clipboard / "drafts" icon in the composer's top-bar action row to access saved drafts. V1's `ComposerScreen` top app bar action row contains `{close, post}` actions; the layout MUST leave room for one additional action between them so the future drafts icon can slot in without forcing a layout reshuffle. Cheap to honor: just don't squeeze the top bar against its `maxLines = 1` minimum.

6. **FAB component leaves room for a badge.** The official Bluesky client badges the compose FAB when drafts exist (like a notification badge). V1 ships a non-badged FAB, but the component choice MUST be wrappable in `BadgedBox` later. `LargeFloatingActionButton` and `FloatingActionButton` both wrap; `ExtendedFloatingActionButton` with a label is harder to badge cleanly, so V1 picks the icon-only variant.

These commitments do not change V1's shipped behavior. They constrain how V1 is *built* so that the drafts epic is purely additive — a new `:core:drafts` module, a new `feature-drafts` capability, and small additive deltas against `feature-composer` (constructor param, dialog-button addition, top-bar icon, FAB badge).

## Risks / Trade-offs

- **Risk:** Dropping unsent text on back-press feels punishing for long replies. → **Mitigation:** Back-press while `state.text.isNotBlank() || state.attachments.isNotEmpty()` shows a "Discard draft?" confirmation dialog. The dialog itself is a `ComposerEffect`-routed transient — not stored in state. Local draft persistence is still out of V1, but the user cannot lose work to an accidental swipe.
- **Risk:** Blob upload on submit serializes the network round-trips (4 uploads + 1 record create), which on slow connections looks like a long "Submitting" state. → **Mitigation:** Run blob uploads in parallel via `coroutineScope { attachments.map { async { upload(it) } }.awaitAll() }`. Record creation still waits for all blobs but the wall-clock time is bounded by the slowest upload, not the sum.
- **Risk:** Optimistic UX (close the screen on tap, show a system snackbar on success/failure later) would feel snappier, but it complicates the Feed/PostDetail integration (the new post needs to slot into the right list at the right position) and risks losing user content on failure. → **Mitigation:** V1 stays pessimistic — the screen remains visible during `Submitting` and only dismisses on `Success`. Optimistic posting is a follow-up once we have a "pending posts" surface to render failed sends from.
- **Risk:** Character-count derivation drifting from the server's truth. The grapheme count must match `app.bsky.richtext.facet`'s `MAX_GRAPHEMES` exactly or the user sees a confusing "you are at 299, but the server rejects." → **Mitigation:** `GraphemeCounter` wraps `java.text.BreakIterator.getCharacterInstance()` (the JVM stdlib's grapheme cluster iterator, ICU-backed on Android API 24+). Pin unit tests that assert the boundary at 300 exact graphemes for ASCII (platform-stable). For ZWJ family emoji the JVM's older Unicode tables miscount — Android's runtime is correct, the JVM tests are not. The follow-up to swap to ICU4J or a Unicode-version-pinned segmenter is filed as a backlog task; for V1 the worst case is occasional off-by-N counting on bleeding-edge emoji.
- **Risk:** The `PickVisualMedia` activity-result API requires a registered launcher in the Composable, which couples picker invocation to UI rather than VM. The VM cannot directly trigger the picker. → **Mitigation:** The screen owns the launcher and emits an `ComposerEvent.AddAttachments(uris)` to the VM after the picker returns. The VM is the source of truth for the attachment list; the screen is just a transport for the picker callback. This is the standard Compose pattern and matches the repo's existing event-flow convention.
- **Trade-off:** Two layers of enforcement on the 4-image cap (picker `maxItems` + reducer guard) is mildly redundant. Accepted — defensive depth against picker behavior changes across OEMs.
- **Trade-off:** Not persisting drafts to Room means a process kill loses the draft. Accepted for V1; the follow-up `:core:drafts` epic owns this. The V1 design has been audited against forward-compat (see *Forward-compatibility: drafts* above) so the next epic is purely additive — no V1 architecture choices preclude per-target auto-saved drafts landing on top.

## Migration Plan

This is additive. No spec deletions, no behavior changes to existing capabilities beyond the small `feature-feed` reply-affordance hookup and a `MainShell` FAB.

Rollout order (as separate PRs, all on `main` via squash-merge):

1. `:core:posting` module + `PostingRepository` interface + atproto-backed implementation + unit tests (no UI).
2. `:feature:composer:api` module + `ComposerRoute` `NavKey`.
3. `:feature:composer:impl` module skeleton: ViewModel + state types + Hilt module, no Composables yet, full unit-test coverage of state machine.
4. Composer screen Composable: text input, character counter, Post button, focus behavior, screenshot fixtures.
5. Image attachment: picker integration, attachment chips, blob upload on submit, screenshot fixture for "with images".
6. Reply mode: parent-post card, parent fetch lifecycle, root/parent ref derivation, screenshot fixture for "reply mode".
7. Swap the Feed FAB: delete the scroll-to-top FAB visibility logic, threshold constant, icon, `onClick`, and `AnimatedVisibility` wrapper from `FeedScreen.kt`. Replace the Scaffold `floatingActionButton` content with the compose FAB (`Icons.Default.Edit`, `R.string.feed_compose_new_post`, `onClick` pushing `ComposerRoute()` onto `LocalMainShellNavState.current`). Drop the `FeedScreenLoadedWithFabVisibleScreenshot` fixture and add a `FeedScreenLoadedWithComposeFabScreenshot` fixture pair (Light + Dark) capturing the compose FAB at rest over a populated feed. Confirm the `LocalScrollToTopSignal` collector path is untouched.
8. Wire the per-post reply affordance: each `PostCard` in `feature-feed:impl` (and later `feature-postdetail:impl`) gets a reply tap target whose action emits a `ComposerEffect`-equivalent navigation effect, collected in the screen Composable and routed through `LocalMainShellNavState.current.add(ComposerRoute(replyToUri = post.uri))`.

Rollback strategy: each PR lands behind no flag because the feature is gated by its own entry points. Reverting step 8 leaves the FAB-driven new-post flow live but disables in-feed reply entry. Reverting step 7 restores the scroll-to-top FAB but leaves the composer unreachable from the Feed; the home-tab retap path keeps scroll-to-top covered either way. Steps 1–6 are pure additions and revert independently.

## Open Questions

- **Which of `app.bsky.feed.getPostThread` or `app.bsky.feed.getPosts` should we use to resolve the reply parent + root refs?** `getPosts` is cheaper but does not include thread context, so we cannot derive the root. `getPostThread` with `depth = 0` and `parentHeight = <large>` returns ancestors. Decision likely lands on `getPostThread`; confirming exact param shape with the SDK before tasks lands.
- **Optimistic insertion into the Feed on `Success`?** Out of V1 scope per the trade-off above, but worth deciding before the feed integration PR whether the new post is fetched fresh or inserted optimistically.
- **What is the backpress UX when `submitStatus == Submitting`?** Likely: ignore back-press while submitting (mirrors the Compose convention for in-flight network calls). Confirming with the design pass.
- **Do we surface upload progress per-image, or only an aggregate "submitting" indicator?** V1 leans aggregate (one wavy progress on the Post button). Per-image progress is a polish follow-up if testing reveals long uploads feel stuck.
