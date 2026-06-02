## Why

The fullscreen video player works but feels utilitarian: a single cramped
transport row crowds mute, picture-in-picture, and play/pause into identical
44dp icon buttons, the seek bar is a plain `Slider`, and there is **no way to
like, repost, share, or read comments without leaving the video**. A
Claude-authored design (checked in at
`openspec/references/video-player/`) reimagines the surface around
Material 3 Expressive — a large morphing play/pause, a wavy seek bar, a
connected social action group, and a scrim that fades through full → peeking →
hidden as you watch. The headline interaction is a comments sheet that docks
the video to the top and keeps it playing while you read and reply, the way
TikTok and YouTube do it. This change makes the player a place you stay, not a
lightbox you dismiss.

## What Changes

- **M3 Expressive chrome.** Replace the flat transport row with: a large filled
  play/pause that **morphs round→squircle on press** (`MaterialShapes`) flanked
  by replay-10s / forward-10s skip buttons; a **wavy seek bar** (`Slider` with a
  custom track slot drawing `LinearWavyProgressIndicator`, preserving the
  existing drag-to-commit single-`seekTo` semantics); and an **author chip**
  (avatar + name/handle) in the top bar that taps through to Profile.
- **Mute + PiP utility row.** Move mute and the pop-out/PiP affordance into a
  small secondary row beneath play/pause (grouped as *player controls*, not post
  actions). PiP keeps its existing device × Pro `isEnabled` gating and
  `resolvePopOut` (Pro → enter PiP, non-Pro → paywall). This intentionally
  diverges from the design mock, which kept mute in the top bar.
- **Scrim auto-hide ladder.** Replace the boolean `chromeVisible` with a
  three-state lifecycle — **Shown** (everything), **Peeking** (lighter scrim;
  top bar + author chip + caption + wavy bar + action glyphs *without counts*;
  center transport hidden), **Hidden** (no chrome; a single centered tap-to-play
  + a thin bottom progress hairline).
- **Social data through the data layer.** Resolve the post via
  `:core:posts` `PostRepository.getPost()` so player state carries the full
  `PostUi` (author, `PostStatsUi` counts, `ViewerStateUi` like/repost state, the
  video embed) instead of the embed-only `ResolvedVideoPost`. Optimistic
  like/repost route through the existing `:core:post-interactions` cache, so an
  interaction in the player reflects in the feed and vice-versa.
- **Connected action group.** A Material 3 `ButtonGroup` of **Like · Repost ·
  Comment · Share**: like via `LikeRepostRepository`; repost opens a Repost /
  Quote menu; share via the existing `PostShareLauncher`; comment opens the
  sheet.
- **Comments sheet (centerpiece).** A **non-modal** docked sheet: the video
  resizes into a top pane and keeps playing while replies fill the lower pane,
  draggable between Half and Full detents and dismissable by drag-down. Content
  is the post's replies thread; an inline reply bar lets the viewer post a reply
  to the video's post without leaving the player.

## Capabilities

### New Capabilities
- `feature-videoplayer`: The fullscreen video player surface — post resolution
  and HLS playback lifecycle, the M3 Expressive control chrome (morphing
  play/pause + skip, wavy seek bar, mute + PiP utility row, author chip), the
  scrim auto-hide ladder, the Like · Repost · Comment · Share action group wired
  to the existing interaction/share repositories, and the non-modal docked
  comments sheet with inline reply. (No spec exists for this surface today; the
  player shipped under `nubecita-zak` without an OpenSpec capability — this
  documents it in its revamped form.)

### Modified Capabilities
- `core-posts`: Relocate the post-thread read (`getPostThread` → focus +
  replies) from `:feature:postdetail:impl/data/PostThreadRepository` into
  `:core:posts` so both post-detail and the new comments sheet consume one
  shared interface, preserving the single-`getPostThread`-import discipline.
  (Post-detail consuming the relocated interface is an impl-only change — its
  spec captures no thread-repo requirement — so it is listed under Impact, not
  as a modified capability.)

## Impact

- **Code (new/changed):** `:feature:videoplayer:impl` (chrome, contract, VM,
  comments sheet, action group); `:core:posts` (thread read interface + impl
  relocated in); `:feature:postdetail:impl` (consume relocated repo, delete
  local copy); `:data:models` may gain a small reply/comment UI projection if
  the thread model needs one.
- **Reused, unchanged:** `:core:post-interactions` (`LikeRepostRepository`,
  `PostInteractionsCache`, `PostShareLauncher`), `:core:video`
  (`SharedVideoPlayer`, `PipController`), `:feature:paywall` (PiP upsell).
- **Dependencies:** none new — Material 3 `1.5.0-alpha20` already ships
  `ButtonGroup`, `LinearWavyProgressIndicator`, `MaterialShapes`.
- **Deviations from baseline (per OpenSpec rules):**
  - The comments sheet is a **non-modal** sheet built on `AnchoredDraggableState`
    rather than `ModalBottomSheet` — a modal sheet scrims and blocks the video;
    keeping playback + resizing the surface requires a manual two-pane layout.
  - The inline reply bar uses the **sanctioned editor `TextFieldState` MVI
    exception** (CLAUDE.md) for the text field; all other player state stays in
    `UiState`.
  - `chromeVisibility` is a per-screen sealed status sum (mutually-exclusive
    Shown/Peeking/Hidden) per the MVI carve-out for view-mode lifecycles — not a
    set of independent booleans.
