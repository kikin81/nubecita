## ADDED Requirements

### Requirement: Fullscreen player resolves the full post and carries its social state

The fullscreen player MUST resolve its post (reached via
`VideoPlayerRoute(postUri)`) through `:core:posts`
`PostRepository.getPost(uri)`, so player state carries the complete `PostUi` —
author (`AuthorUi`), social counts (`PostStatsUi`: like/repost/reply), viewer
interaction state (`ViewerStateUi`: like/repost flags + record URIs), and the
`EmbedUi.Video` (playlist, poster, aspect ratio, alt-text). The player MUST NOT
re-project `PostView` locally; the embed-only `VideoPostResolver` /
`ResolvedVideoPost` is removed. The mutually-exclusive load lifecycle
(`Resolving` → `Ready` / `Error`) is preserved; social fields populate when the
fetch resolves, and a fetch failure routes to the retryable `Error` state.

#### Scenario: Resolution hydrates playback and social data from one read

- **WHEN** the player resolves `VideoPlayerRoute(postUri)`
- **THEN** it calls `PostRepository.getPost(postUri)` and, on success, state
  exposes the video playlist/poster/aspect for playback AND the author, like/
  repost/reply counts, and viewer like/repost state — without a second network
  call and without a local `PostView` projection

#### Scenario: A post with no video embed is a resolution error

- **WHEN** `getPost` succeeds but the post carries no `EmbedUi.Video`
- **THEN** the player enters the `Error` state with a retryable message rather
  than rendering an empty surface

### Requirement: Transport cluster is a large morphing play/pause flanked by skip controls

The center transport MUST render a large filled play/pause button flanked by
replay-10s and forward-10s skip buttons. The play/pause button MUST visibly
**morph its shape on press** (round at rest, squircle while pressed) using
Material 3 `MaterialShapes`. The skip buttons MUST seek the player by −10s /
+10s, clamped to `[0, duration]`. Play/pause MUST toggle playback.

#### Scenario: Skip buttons seek by ten seconds and clamp

- **WHEN** the viewer taps forward-10s with 5s remaining
- **THEN** playback position advances to the end (clamped), not past duration;
  tapping replay-10s near 0:00 clamps to 0:00

#### Scenario: Play/pause morphs on press

- **WHEN** the play/pause button is pressed
- **THEN** its shape animates from round toward a squircle for the duration of
  the press and returns to round on release

### Requirement: Seek bar is a wavy slider with drag-to-commit seeking

The seek bar MUST render as a Material 3 wavy slider — the played portion drawn
with `LinearWavyProgressIndicator` (wave behind the thumb), a flat track ahead
of the thumb — built on `Slider` so it retains thumb interaction and
accessibility. Scrubbing MUST commit a single `seekTo` on gesture end (not per
touch frame), matching the existing HLS-safe drag-to-commit behavior. Position
must be clamped into the slider's value range so it never escapes `0..1` near
end-of-stream.

#### Scenario: A scrub gesture produces exactly one seek

- **WHEN** the viewer drags the thumb across the bar and lifts
- **THEN** the player receives exactly one `seekTo` (at the release position),
  not one per intermediate touch frame

### Requirement: Mute and picture-in-picture live in a utility row under play/pause

Mute/unmute and the pop-out (picture-in-picture) affordance MUST render together
in a secondary control row beneath the play/pause button — grouped as player
controls, distinct from the post-action group. The PiP affordance MUST remain
gated by the existing device × Pro capability flag: it is shown only where the
device supports PiP, and a tap enters PiP for entitled (Pro) users or routes a
non-entitled user to the paywall (the `resolvePopOut` decision stays in the
Compose layer, never the ViewModel). When PiP is unsupported, the row shows mute
alone.

#### Scenario: Non-Pro pop-out tap upsells the paywall

- **WHEN** a non-entitled viewer on a PiP-capable device taps the pop-out button
- **THEN** the paywall is presented and PiP is NOT entered

#### Scenario: Mute toggles audio without affecting playback state

- **WHEN** the viewer taps mute while the video is playing
- **THEN** audio is muted and playback continues (play/pause state unchanged)

### Requirement: An author chip in the top bar navigates to the author's profile

The top bar MUST render an author chip (avatar + display name + handle) sourced
from the resolved post's `AuthorUi`. Tapping the chip MUST navigate to that
author's Profile.

#### Scenario: Tapping the author chip opens Profile

- **WHEN** the viewer taps the author chip
- **THEN** the author's Profile screen is pushed onto the back stack

### Requirement: Chrome fades through a three-state auto-hide ladder

Chrome visibility MUST be a mutually-exclusive lifecycle —
`Shown` / `Peeking` / `Hidden` — not a set of independent booleans.
`Shown` renders all controls (top bar, author chip, center transport, caption,
wavy bar, action group with counts). `Peeking` lightens the scrim and renders
the top bar, author chip, caption, wavy bar, and action glyphs WITHOUT their
counts, with the center transport hidden. `Hidden` renders no chrome — only a
centered tap-to-play affordance and a thin bottom progress hairline. Auto-advance
through the ladder MUST occur only while playing; a tap returns to `Shown`, and
pausing pins `Shown`.

#### Scenario: Idle playback advances Shown → Peeking → Hidden

- **WHEN** the video is playing and the viewer does not interact
- **THEN** chrome advances from `Shown` to `Peeking` to `Hidden` on the idle
  timers; any tap returns it to `Shown`

#### Scenario: Pausing pins chrome visible

- **WHEN** the viewer pauses playback
- **THEN** chrome is `Shown` and does not auto-advance to `Peeking`/`Hidden`
  until playback resumes

### Requirement: A connected action group exposes Like, Repost, Comment, and Share

The player MUST render a Material 3 `ButtonGroup` with Like, Repost, Comment, and
Share actions reflecting the resolved post's counts and viewer state. Like MUST
toggle optimistically through `:core:post-interactions`
(`LikeRepostRepository` + `PostInteractionsCache`) so the player and the feed/
post-detail surfaces stay consistent for the same post. Repost MUST present a
Repost / Quote choice (a plain repost toggles optimistically; Quote opens the
composer in quote mode). Share MUST launch the system share sheet via the
existing `PostShareLauncher`. Comment MUST open the comments sheet.

#### Scenario: Liking in the player reflects in the feed

- **WHEN** the viewer likes the post in the player
- **THEN** the like is applied optimistically and the same post shows as liked
  in the feed/post-detail (shared interaction cache), and an undo on either
  surface reverts both

#### Scenario: Share launches the system share sheet

- **WHEN** the viewer taps Share
- **THEN** the system share sheet is launched for the post via
  `PostShareLauncher` (no in-app reimplementation)

### Requirement: The comments sheet docks the video and keeps it playing

Opening comments MUST present a NON-modal sheet that splits the screen into a
video pane (top) and a comments pane (bottom); the video MUST keep playing and
remain visible while comments are open. The sheet MUST be draggable between a
half-height detent (its open position) and a full detent (video shrinks to a
small top strip), and dismissable by dragging down. The sheet MUST NOT scrim or
block the video. Entering picture-in-picture MUST collapse the sheet.

#### Scenario: Opening comments resizes the video instead of covering it

- **WHEN** the viewer taps Comment while the video is playing
- **THEN** the video resizes into a top pane and continues playing, with the
  replies shown below — the video is never hidden behind a scrim

#### Scenario: Dragging the sheet resizes the video pane

- **WHEN** the viewer drags the sheet from the half detent to the full detent
- **THEN** the video pane shrinks to a top strip as the comments pane grows,
  and dragging the sheet down past the half detent dismisses it

### Requirement: The comments sheet lists replies and supports inline reply and like

The comments sheet MUST list the post's direct replies fetched via `:core:posts`
`PostThreadRepository`. The viewer MUST be able to like a reply (optimistically,
via `:core:post-interactions`) and to post a reply to the video's post from an
inline reply bar pinned to the sheet, without leaving the player. The reply bar
MUST enforce the same grapheme limit as the composer and post through
`:core:posting`. Tapping a reply MUST open the full thread (post-detail) for
deeper / nested replies.

#### Scenario: Inline reply posts without leaving the player

- **WHEN** the viewer types a reply in the sheet's reply bar and submits
- **THEN** the reply is posted to the video's post via `:core:posting` and the
  player/video remain on screen (the full composer is not launched for this
  inline case)

#### Scenario: Over-limit reply is blocked

- **WHEN** the reply text exceeds the grapheme limit
- **THEN** the submit affordance is disabled and an over-limit indication is
  shown, mirroring the composer's limit gate

#### Scenario: Tapping a reply opens the full thread

- **WHEN** the viewer taps a reply in the sheet
- **THEN** the post-detail thread for that reply is opened for nested context
