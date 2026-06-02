## Context

The fullscreen player (`:feature:videoplayer:impl`) today renders a single
transport `Row` (mute · PiP · play/pause as identical 44dp icon buttons) over a
plain `Slider`, with a boolean `chromeVisible` for tap-to-toggle + 3s auto-hide.
Post resolution goes through `VideoPostResolver`, which extracts **only** the
video embed (`ResolvedVideoPost`: playlist/poster/duration/alt/aspect) and
discards the post's author and social metadata. The route (`VideoPlayerRoute`)
carries just `postUri`. PiP is wired at the Compose layer (design D5) via
`resolvePopOut` (Pro → `PipController.enterPip`, non-Pro → paywall).

The revamp is driven by a checked-in design
(`openspec/references/video-player/`, 4 panels: canonical layout,
play/pause shape study, action-group/avatar placement, scrim auto-hide ladder).
Material 3 `1.5.0-alpha20` is already on the classpath with the Expressive
components we need (`ButtonGroup`, `LinearWavyProgressIndicator`,
`MaterialShapes`), and `PostUi` already carries `PostStatsUi` + `ViewerStateUi`,
so most building blocks exist — the work is composition, a data-source switch,
and one genuinely new interaction (the docked comments sheet).

## Goals / Non-Goals

**Goals:**
- Restyle the chrome to the design's M3 Expressive language without regressing
  the existing playback/seek/PiP/lifecycle contracts.
- Make the player a place to like, repost, share, and read/leave comments
  without leaving the video.
- Keep all social state consistent with the feed/post-detail via the shared
  `:core:post-interactions` optimistic cache.
- Hold 120hz: surface resize + sheet drag must stay in the layout phase, not
  trigger recomposition storms.

**Non-Goals:**
- Chromecast (the mock's cast icon is omitted; separate epic).
- The vertical action-rail layout (design panel 3C).
- Nested reply-to-a-specific-comment inside the sheet (v1 replies to the
  *post*; tapping a comment opens the full thread as a fast-follow).
- Liking/quoting from PiP (PiP shows video only).

## Decisions

### D1 — Resolve the full `PostUi` via `:core:posts`, retire the embed-only resolver
The VM resolves the post through `PostRepository.getPost(uri)` (already the
project's single `getPosts` read surface, mapped via shared
`:core:feed-mapping`). One call yields the `EmbedUi.Video` (playlist/poster/
aspect — same data the player uses today) **and** author + `PostStatsUi` +
`ViewerStateUi`. `VideoPostResolver`/`ResolvedVideoPost` are replaced (the
playlist-extraction logic moves behind `getPost`'s mapper, which already handles
`EmbedUi.Video`). *Alternative — extend `ResolvedVideoPost` with social fields:*
rejected; it would re-project `PostView` locally, exactly the divergence
`core-posts` exists to prevent. *Fast-start nuance:* `getPost` is a single XRPC
round-trip; playback start is unchanged from today (the current resolver already
calls `getPosts`). A future cached-embed fast path (play immediately from a
feed-supplied embed, hydrate social after) is out of scope here.

### D2 — Play/pause morphs round→squircle on press (`MaterialShapes`)
The large filled button animates its shape between `MaterialShapes.Circle` and a
squircle/`MaterialShapes.Cookie`-style rounded shape, driven by the button's
`InteractionSource` pressed state through `animateShape`/a shape `Transition`.
This is the one element that "breaks from the surrounding shape language" (design
panel 2C). *Alternatives — static round (2A) / static squircle (2B):* rejected as
the design explicitly chose the morph; the static shapes remain the trivial
fallback if the morph animation proves janky on low-end devices.

### D3 — Wavy seek bar = `Slider` + custom track slot drawing `LinearWavyProgressIndicator`
Keep `Slider` (it owns the thumb, gesture, a11y, and our existing drag-to-commit
single-`seekTo` semantics) and pass a custom `track = { ... }` slot that renders
`LinearWavyProgressIndicator` for the played fraction and a flat track ahead of
the thumb (matching the design: wave behind the playhead, flat line in front).
Amplitude animates to ~0 while the user is actively dragging so scrubbing reads
cleanly. *Alternatives — hand-rolled Canvas wave:* rejected (re-implements M3
math we already ship); *Slider default expressive track:* rejected (not the
media-wave look the design calls for).

### D4 — Scrim auto-hide is a sealed `ChromeVisibility { Shown, Peeking, Hidden }`
Replace `chromeVisible: Boolean` with a mutually-exclusive sum (MVI carve-out for
view-mode lifecycles). The VM drives transitions on a timer **only while
playing**: tap → `Shown`; after a short idle → `Peeking` (drop center transport +
action counts, lighten scrim); after a longer idle → `Hidden` (no chrome, a
centered tap-to-play + bottom progress hairline). Any tap returns to `Shown`;
pausing pins `Shown`. *Alternative — independent booleans
(`showTransport`, `showCounts`, …):* rejected; the states are mutually exclusive
and invalid combinations (e.g. Hidden-with-counts) shouldn't be representable.

### D5 — Comments sheet is a non-modal two-pane layout on `AnchoredDraggableState`
The sheet is **in-layout state of the player screen**, not a separate `NavKey`,
because it must coexist with the still-playing video in one layout. A `Column`
splits into a **video pane** (height derived from the draggable offset) and a
**comments pane**. `AnchoredDraggableState` anchors: `Dismissed` (closed),
`Half` (~50%, opens here), `Full` (video shrinks to a small top strip). Video
playback never stops; the `PlayerSurface` stays attached and is re-measured, with
the poster layered underneath to avoid a black flash on re-layout (existing
surface-composition rule). *Alternatives:* `ModalBottomSheet` — rejected, scrims
and blocks the video; `BottomSheetScaffold` — rejected, its body isn't resized by
the sheet, so the video would sit *behind* a peeking sheet rather than dock above
it.

### D6 — Lift `PostThreadRepository` (`getPostThread`) into `:core:posts`
The sheet's content is the post's replies. The thread read currently lives in
`:feature:postdetail:impl/data/PostThreadRepository`; lift it to `:core:posts`
(alongside `PostRepository`) so post-detail **and** the comments sheet consume one
interface, preserving the single-`getPostThread`-import discipline.
*Alternatives:* duplicate the fetch in `:feature:videoplayer:impl` — rejected
(reuse/altitude); depend on `:feature:postdetail:impl` — rejected (feature→feature
impl coupling violates the api/impl split). Post-detail is updated to consume the
relocated interface with no behavior change.

### D7 — Inline reply posts a reply to the video's post; reply bar uses the editor `TextFieldState` exception
A compact reply bar pinned to the sheet bottom posts a top-level reply to the
video's post via `:core:posting` (reusing `ReplyRefs` + the grapheme-limit
gate), staying in-player. The field uses the sanctioned editor `TextFieldState`
MVI exception (CLAUDE.md); derived projections (`graphemeCount`, `isOverLimit`)
live on `UiState`. *Alternative — launch the full composer in reply mode:*
rejected for the inline case (it leaves the player, defeating the feature); it
remains the path for **quote-post** (heavier, opens the composer) and for
replying to a *specific* comment (tap → full thread, fast-follow).

### D8 — Action group + interactions reuse existing primitives
The `ButtonGroup` (Like · Repost · Comment · Share) wires Like → optimistic
`LikeRepostRepository` via `PostInteractionsCache` (so player↔feed stay
consistent); Repost → a Repost/Quote menu; Share → `PostShareLauncher`; Comment →
open the sheet. Liking a comment reuses `LikeRepostRepository` on the reply URI.
PiP/mute move to the utility row unchanged (`resolvePopOut`, `MuteClicked`).
Entering PiP collapses the comments sheet.

## Risks / Trade-offs

- **Surface resize jank at 120hz while dragging the sheet** → drive the video
  pane height purely from the draggable offset in the layout phase (one source of
  truth, no per-frame recomposition); validate with the macrobenchmark/jank stats
  on a real device before merge.
- **Black flash when the `PlayerSurface` re-measures on dock** → keep the poster
  image layered under the surface (existing rule) so any surface gap reveals the
  poster, not black.
- **IME ↔ non-modal sheet inset interplay (reply bar)** → follow the chats
  single-owner pattern: the reply bar owns the IME inset
  (`navigationBarsPadding().imePadding()`); the player screen already runs under
  `adjustResize`.
- **Lifting `PostThreadRepository` touches post-detail** → pure relocation, same
  interface; grep all implementors and run the root `testDebugUnitTest` (not just
  the changed module) so post-detail's fakes/tests stay green.
- **`getPost` full hydration vs. immediate playback** → no regression (today's
  resolver already issues `getPosts`); social fields simply populate when the
  call resolves, gated behind the existing `Resolving` state.
- **Morph animation cost on low-end devices** → the static round shape (D2
  alternative) is a one-line fallback if profiling shows dropped frames.

## Migration Plan

Incremental, each task a single shippable PR with its own tests; no feature flag
(the player is replaced in place). Suggested order: chrome restyle (transport +
morph) → wavy seek bar → mute/PiP utility row → scrim ladder + author chip →
data-layer switch to `getPost` (social into state) → action group (like/repost/
share) → `core-posts` thread lift → comments sheet shell (dock + detents) →
comments content + inline reply + like-a-comment. Screenshot baselines are
regenerated per UI task (emoji/vector drift handled via the CI `update-baselines`
label). Rollback is per-PR revert; the data-layer and chrome tasks are
independent of the comments sheet, so the sheet can slip without blocking the
restyle.

## Open Questions

- **Idle thresholds for the scrim ladder** (Shown→Peeking→Hidden) — start with
  ~3s / ~6s, tune on device; pinned to `Shown` whenever paused.
- **Skip increment** — design shows ±10s (`replay_10`/`forward_10`); confirm 10s
  vs a configurable value (defaulting 10s).
- **Repost vs Quote menu surface** — a small `DropdownMenu` anchored to the
  Repost button vs a bottom sheet; leaning `DropdownMenu` for parity with
  existing post UI.
