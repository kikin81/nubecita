## Why

Nubecita can read Bluesky but cannot author it. Without a composer, the app is read-only — users must leave to a different client to post, reply, or join a conversation, which collapses session value and blocks every social loop the app already renders. A unified composer that handles both new posts and direct replies is the smallest single feature that turns Nubecita from a viewer into a client.

## What Changes

- New feature module pair `:feature:composer:api` and `:feature:composer:impl`, following the established Navigation 3 api/impl split.
- New navigation route `ComposerRoute(replyToUri: String? = null)` in `:feature:composer:api`. A `null` URI means "new post"; a non-null URI means "reply to this post". One route, one screen, two modes. Stored as `String` (not the lexicon `AtUri` value class) to keep `:feature:composer:api` atproto-runtime-free — consumers wrap to `AtUri` at the call site to the atproto runtime, mirroring the existing `:feature:postdetail:api`'s `PostDetailRoute(postUri: String)` precedent.
- New `ComposerScreen` (Compose) and `ComposerViewModel` (`MviViewModel<ComposerState, ComposerEvent, ComposerEffect>`) in `:feature:composer:impl`.
- New `:core:posting` capability covering the data-layer side: a `PostingRepository` that creates `app.bsky.feed.post` records (root and reply variants) and uploads image blobs via `com.atproto.repo.uploadBlob`.
- Entry points wired through existing `MainShell` surfaces: the Feed tab's existing scroll-to-top FAB is **replaced** by a "compose new post" FAB that launches `ComposerRoute(replyToUri = null)`; a "reply" affordance on `feature-feed` posts and `feature-postdetail` launches `ComposerRoute(replyToUri = <post>)`. Both navigate via `LocalMainShellNavState.current.add(...)` per the repo's tab-internal nav convention. The scroll-to-top *behavior* survives — it is already covered by the home-tab retap signal (`LocalScrollToTopSignal`) shipped in `add-feed-scroll-to-top`. The FAB slot was the only redundant surface.
- Material 3 Expressive treatment with an adaptive container: full-screen `Scaffold` route on Compact widths; on Medium/Expanded widths the same `ComposerScreen` Composable is overlaid as a centered `Dialog(usePlatformDefaultWidth = false)` with `Modifier.widthIn(max = 640.dp)` so the canvas behind stays visible through the M3 scrim. Prominent expressive Post button that morphs into a wavy M3 progress indicator while submitting, and a tactile circular character counter that crosses to error color as it approaches 300.
- Screenshot test contract: empty composer, near-limit composer, composer with attached images, and reply-mode composer (parent post rendered above input). Light + Dark pairs for each, in line with the repo's UI test coverage convention.

### Non-goals (V1)

- Video uploads. Images only.
- Quote posts. Direct replies only; quote-posting ships in a later epic.
- Local draft persistence. Backing out of the composer drops the in-flight draft. **V1 is explicitly architected to not preclude an additive `:core:drafts` capability** — per-target draft semantics (keyed by `replyToUri ?: NEW_POST_SENTINEL`), an auto-save direction with no manual save button, a three-button-ready discard dialog, a `ComposerViewModel` constructor seam for a future `DraftRepository`, a reserved top-bar slot for the drafts entry point, and a non-extended FAB component (badge-wrappable later) are all committed in writing in `design.md` even though no draft code ships in V1.
- Rich-text controls (mentions, links, hashtags) as toolbar buttons. **Mention and URL facets are parsed automatically on submit by `:core:posting`** so `@alice.bsky.social` and `https://example.com` produce clickable links in the published record (without this, posts would render mentions and URLs as plain text — broken vs. official-client expectations); the parsing happens in the data layer (regex extraction → `app.bsky.identity.resolveHandle` for handle→DID resolution → byte-offset facet objects with `app.bsky.richtext.facet#mention` / `#link` features), not via a composer UI affordance. Tracked as `nubecita-wtq.11` and is V1-blocking. Bold / italic / hashtag facets remain out of V1 — the lexicon supports them but they need toolbar UX before they're useful.
- Threadgate / reply-gating UI.
- Alt-text editing on attached images (V1 ships images without alt text; alt-text editing is a follow-up).
- Saving composer state across process death (`SavedStateHandle` is out of scope for V1).

## Capabilities

### New Capabilities

- `feature-composer`: Unified compose surface for creating new posts and direct replies, including text input, character-limit feedback, image attachment (up to 4), parent-post context for reply mode, and submission lifecycle.
- `core-posting`: Data-layer capability for authoring AT Protocol records — creating `app.bsky.feed.post` records (root and reply), uploading image blobs, and surfacing a typed submission result to callers.

### Modified Capabilities

- `app-navigation-shell`: `MainShell` gains a new top-level entry point (Feed-tab FAB) that pushes `ComposerRoute` onto the inner `NavDisplay`. The composer is registered with `@MainShell` `EntryProviderInstaller`. No outer-shell changes.
- `feature-feed`: The Feed tab's `Scaffold.floatingActionButton` slot is repurposed from scroll-to-top to compose-new-post. The scroll-to-top FAB requirement (icon, threshold gating, `animateScrollToItem(0)` onClick) is removed; the slot now hosts a compose FAB visible whenever the feed is in `Loaded` state, regardless of scroll position. Each post in the feed additionally exposes a "reply" tap target whose action emits a `ComposerEffect`-style navigation effect (per repo convention, ViewModels do not touch `MainShellNavState` directly). The `LocalScrollToTopSignal` collector inside `FeedScreen` and all list rendering / pagination behavior remain unchanged — only the FAB slot's content swaps.

## Impact

- **Code**: New modules `:feature:composer:api`, `:feature:composer:impl`, `:core:posting`. Touches `:app` (DI aggregation, no new shell wiring beyond multibinding pickup), `:feature:feed:impl` (reply affordance + nav effect), `:feature:postdetail:impl` if/when present (reply affordance).
- **APIs**: New `PostingRepository` Kotlin interface in `:core:posting`. New `ComposerRoute` `NavKey` in `:feature:composer:api`.
- **Dependencies**: No new third-party deps. Uses existing `atproto-kotlin` SDK (record creation + blob upload), Coil (image preview), and the standard Compose/Hilt baseline. If image picking requires `ActivityResultContracts.PickMultipleVisualMedia`, that is AndroidX-only and already on the classpath.
- **Permissions**: No new manifest permissions. `PickVisualMedia` is permission-less on supported SDKs (24+).
- **Build**: Two new feature module conventions (`nubecita.android.feature` for `:feature:composer:impl`, `nubecita.android.library` for `:feature:composer:api` and `:core:posting`). Hilt enabled on `:feature:composer:impl` and `:core:posting`.
- **Testing**: New unit tests for `ComposerViewModel` (state transitions, character-count edges, submission lifecycle, reply-parent load), Compose previews, and four screenshot fixtures × Light/Dark per the UI coverage convention.
- **Out of MVI baseline**: None. This change stays inside the documented MVI foundation. No `Async<T>` wrappers, no framework adoption, no base-class additions. Submission lifecycle uses a per-screen `sealed interface ComposerSubmitStatus` (mutually-exclusive states) and a per-screen `sealed interface ParentLoadStatus` for reply mode — both consistent with the "sealed status sum for mutually-exclusive view modes" rule in `CLAUDE.md`.
