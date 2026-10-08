# feature-feed-video Specification

## Purpose

HLS-backed video playback inside the feed. Covers the data-path mapping for `app.bsky.embed.video#view`, the rendered card surface (poster + optional duration chip + autoplay-muted player surface + mute toggle + tap-to-resume affordance), the single-player coordinator that owns one materialized `ExoPlayer` instance scoped to `FeedScreen`'s composition lifetime and binds it to the most-visible video card, and the lifecycle / inset / audio-focus contracts those moving parts must satisfy.

The spec was authored as the openspec change `add-feature-feed-video-embeds` (archived 2026-04-27) and shipped across three implementation PRs: deps + smoke (`nubecita-sbc.1`, PR #57), data path + thumbnail render (`nubecita-sbc.3`, PR #58), and autoplay coordinator + mute toggle (`nubecita-sbc.4`, PR #59).
## Requirements
### Requirement: `FeedViewPostMapper` dispatches `app.bsky.embed.video#view` to `EmbedUi.Video`

`FeedViewPostMapper.toEmbedUi` MUST map `app.bsky.embed.video#view` to `EmbedUi.Video` carrying poster URL, HLS playlist URL, aspect ratio (`width:height`, defaulting to 16:9), and optional alt-text (`durationSeconds` is null in v1). Posts with missing required `playlist` MUST fall through to `EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")` without throwing. Missing optional `thumbnail` yields `EmbedUi.Video` with `posterUrl = null`.

#### Scenario: Well-formed video view produces EmbedUi.Video

- **WHEN** `toEmbedUi` is called on a `PostViewEmbedUnion` whose discriminator is `app.bsky.embed.video#view` and whose payload contains playlist + thumbnail + aspect ratio
- **THEN** the result is `EmbedUi.Video` with `playlistUrl`, `posterUrl`, and `aspectRatio` populated; `durationSeconds` is `null`; `EmbedUi.Unsupported` is NOT produced

#### Scenario: Video view without thumbnail still produces EmbedUi.Video

- **WHEN** `toEmbedUi` is called on a video view whose optional `thumbnail` field is absent
- **THEN** the result is `EmbedUi.Video(posterUrl = null, ...)`; the render layer renders a gradient placeholder

#### Scenario: Video view without aspect ratio uses 16:9 fallback

- **WHEN** `toEmbedUi` is called on a video view whose optional `aspectRatio` field is absent
- **THEN** the result is `EmbedUi.Video(aspectRatio = 1.777f, ...)`; the render layer never observes a null aspect ratio

#### Scenario: Malformed video view falls through to Unsupported

- **WHEN** `toEmbedUi` is called on a video view whose required `playlist` field is missing or empty
- **THEN** the result is `EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")`; the call does NOT throw

### Requirement: PostCard's video slot autoplays muted on scroll-into-view; mute/unmute icon is the only inline control

`PostCard`'s `EmbedSlot` MUST dispatch on `EmbedUi.Video` via host `videoEmbedSlot`. `PostCardVideoEmbed` in `:feature:feed:impl` MUST render poster image with aspect ratio, bound `PlayerSurface` when `visible-fraction > 0.6` (other cards pass `player = null`), and a mute/unmute overlay driven by `coordinator.isUnmuted` (tapping calls `coordinator.toggleMute()`). Tapping card body calls `PostCallbacks.onTap(post)`. Mute icon is the sole inline control without progress bar or play button.

#### Scenario: Card autoplays muted on scroll-into-view

- **WHEN** an `EmbedUi.Video` post scrolls into the most-visible-video position (visible-fraction > 0.6)
- **THEN** the coordinator binds the shared `ExoPlayer` to the card, the player begins playback at `volume = 0`, the card's `PlayerSurface` cross-fades over the poster as the first frame arrives, NO audio focus is requested

#### Scenario: Tap on mute icon claims audio focus and unmutes

- **WHEN** the user taps the mute icon on the bound video card while `coordinator.isUnmuted == false`
- **THEN** the coordinator claims `AUDIOFOCUS_GAIN_TRANSIENT`, registers the BECOMING_NOISY receiver, sets `volume = 1`, and `coordinator.isUnmuted` transitions to `true`; the icon updates to the unmuted variant; if a music app held audio focus, it pauses

#### Scenario: Tap on unmute icon releases focus and mutes

- **WHEN** the user taps the unmute icon on the bound video card while `coordinator.isUnmuted == true`
- **THEN** the coordinator releases audio focus, unregisters the BECOMING_NOISY receiver, sets `volume = 0`, and `coordinator.isUnmuted` transitions to `false`; the icon updates to the muted variant; if a music app was paused due to focus loss, it resumes

#### Scenario: Tap on card body navigates to detail; does not toggle mute

- **WHEN** the user taps anywhere on a video card OUTSIDE the mute icon's hit area
- **THEN** `PostCallbacks.onTap(post)` is invoked (the feed feature wires this to navigate to the post-detail screen); `coordinator.isUnmuted` does NOT change; mute icon is not toggled

### Requirement: At most one ExoPlayer instance is materialized at any time

The system SHALL maintain at most one materialized `ExoPlayer` instance across the entire `FeedScreen` lifecycle. The coordinator MUST reuse the same instance across re-bindings (rebind = `PlayerSurface` parameter swap, NOT `exoPlayer = ExoPlayer.Builder(...).build()`). The instance is released when `FeedScreen` exits via the screen's `DisposableEffect(Unit) { onDispose { coordinator.release() } }`.

#### Scenario: Scrolling past N video posts does not multiply player instances

- **WHEN** the user scrolls through a feed page containing 5 video posts
- **THEN** `dumpsys media.player` reports at most 1 active player at any sample, regardless of which video post is currently bound

#### Scenario: Screen exit releases the player and audio focus

- **WHEN** the user navigates away from `FeedScreen` (back press, route change)
- **THEN** the coordinator's `release()` is invoked synchronously in `DisposableEffect.onDispose`, the player instance is destroyed, audio focus is abandoned (if held), the BECOMING_NOISY receiver is unregistered (if registered)

### Requirement: Coordinator binds to the most-visible video card based purely on scroll position

`FeedVideoPlayerCoordinator` MUST bind the player to the topmost feed item whose visible-fraction exceeds 0.6 with an addressable video target (`EmbedUi.Video`, `RecordWithMedia.media`, or quoted-post video). Binding is scroll-driven. Scrolling below threshold MUST pause, release focus, and reset `isUnmuted` to false. Precedence: parent video > RecordWithMedia media video > quoted video. Target identity `postId` uses `quotedPost.uri` for quotes and `post.id` otherwise.

#### Scenario: Scroll between two video cards (both muted)

- **WHEN** video card `A` is the most-visible and bound to the player; the user scrolls so card `B` becomes the most-visible (`A`'s visible-fraction drops below 0.6)
- **THEN** the coordinator unbinds `A` (its `PlayerSurface` flips `player` back to `null`), binds `B` with `player = coordinator.player`, `B` plays from position 0 muted; NO audio focus state changes (neither A nor B was unmuted)

#### Scenario: Scroll-away from an unmuted card auto-mutes

- **WHEN** the user has unmuted video card `A` (`coordinator.isUnmuted == true`) and then scrolls so `A` leaves the most-visible position
- **THEN** the coordinator: pauses the player, abandons audio focus, unregisters BECOMING_NOISY, sets `volume = 0`, transitions `isUnmuted` to `false`; rebinds to the new most-visible video card `B` and starts `B` playing muted

#### Scenario: Scroll back to a previously-unmuted card resumes muted (unmute does NOT persist)

- **WHEN** the user unmuted card `A`, scrolled past it (auto-muted), then scrolls back so `A` is again most-visible
- **THEN** `A` is rebound to the player and plays MUTED from position 0 (or from wherever HLS resumes — implementation detail). The user must tap the unmute icon again if they want audio. Rationale: unmute is a per-card user gesture, not a session-wide preference

#### Scenario: Bind decisions gated on scroll state, not a time-based debounce

- **WHEN** the user is actively scrolling (`LazyListState.isScrollInProgress == true`) and `visibleItemsInfo` emits 20 times in 1.5 seconds
- **THEN** the coordinator MUST perform ZERO bind/unbind operations during the active scroll
- **AND** the instant `isScrollInProgress` flips to `false`, the coordinator binds to the resting most-visible video card (if any) — no time-based debounce delay between settle and bind

#### Scenario: Quoted-post video binds when the parent has no own video (top-level Record)

- **WHEN** the topmost feed item meeting the 0.6 visibility threshold has `embed is EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video` with `playlistUrl = "https://video.bsky.app/.../q.m3u8"`
- **THEN** `mostVisibleVideoTarget` returns `VideoBindingTarget(postId = quotedPost.uri, playlistUrl = "https://video.bsky.app/.../q.m3u8")`; the coordinator binds the player to this target

#### Scenario: RecordWithMedia.media video binds when the parent has no own video

- **WHEN** the topmost feed item meeting the 0.6 visibility threshold has `embed is EmbedUi.RecordWithMedia` whose `media is EmbedUi.Video` with `playlistUrl = "https://video.bsky.app/.../m.m3u8"`
- **THEN** `mostVisibleVideoTarget` returns `VideoBindingTarget(postId = post.id, playlistUrl = "https://video.bsky.app/.../m.m3u8")` — bind identity is the parent post's id (the media is "on" the parent post)

#### Scenario: RecordWithMedia.media video wins over nested quoted-post video on the same item

- **WHEN** the topmost feed item meeting the 0.6 visibility threshold has `embed is EmbedUi.RecordWithMedia` whose `media is EmbedUi.Video` AND whose `record` is `EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video`
- **THEN** `mostVisibleVideoTarget` returns the media video's target (`postId = post.id`); the nested quoted video is NOT considered

#### Scenario: RecordWithMedia.record.quotedPost video binds when the media is non-video

- **WHEN** the topmost feed item meeting the 0.6 visibility threshold has `embed is EmbedUi.RecordWithMedia` whose `media is EmbedUi.Images` AND whose `record` is `EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video` with `playlistUrl = "https://video.bsky.app/.../q.m3u8"`
- **THEN** `mostVisibleVideoTarget` returns `VideoBindingTarget(postId = quotedPost.uri, playlistUrl = "https://video.bsky.app/.../q.m3u8")` — the nested quoted-post video binds since the media is not a video

#### Scenario: Parent video wins over recordWithMedia.media video on the same feed item (structurally inexpressible — defensive)

- **WHEN** a `PostUi` is structurally constructed with `embed = EmbedUi.Video` (this case alone is reachable; `EmbedUi.RecordWithMedia` and `EmbedUi.Video` are mutually exclusive on `PostUi.embed`)
- **THEN** `videoBindingFor` returns the parent-video target. The "parent vs recordWithMedia.media" precedence is structurally unreachable through the public mapper (a post's embed is exactly one slot, not two), but the `videoBindingFor` resolver's first-match-wins ordering documents it for defensive consistency.

#### Scenario: Topmost rule applies across mixed parent/quoted videos

- **WHEN** post `A` (parent video, offset 0) and post `B` (quoted video — top-level `Record` or inside `RecordWithMedia.record`, offset 800) are both visible above the 0.6 threshold simultaneously
- **THEN** `mostVisibleVideoTarget` returns `A`'s parent-video target — topmost wins, regardless of where in the embed tree the candidates live

### Requirement: Audio focus is claimed ONLY on explicit user unmute; never on autoplay

Autoplay (muted at `volume = 0`) MUST NOT request audio focus or register BECOMING_NOISY. Audio focus is requested ONLY when the user taps unmute, and released on mute, scroll-away, or screen exit. On focus loss, coordinator MUST pause player, set `volume = 0`, release focus, unregister receiver, and set `playbackHint = FocusLost` while keeping `isUnmuted == true`. Tapping "tap to resume" calls `coordinator.resume()` to reacquire focus, restore volume, and resume playback.

#### Scenario: App cold-start while music is playing does NOT interrupt audio

- **WHEN** the user is playing music in another app, opens nubecita, lands on `FeedScreen`, and a video post becomes most-visible
- **THEN** the video plays muted, NO `requestAudioFocus` call is made, the user's music continues uninterrupted

#### Scenario: Scrolling past N video cards (all muted, autoplay) does NOT touch audio focus

- **WHEN** the user scrolls through a feed page containing 5 video cards while music is playing in another app, with no card unmuted
- **THEN** the coordinator binds each card in turn as it becomes most-visible, the user's music continues uninterrupted throughout, `requestAudioFocus` is never called

#### Scenario: Incoming call while unmuted surfaces FocusLost hint

- **WHEN** the user has unmuted a video and the system delivers an `AUDIOFOCUS_LOSS_TRANSIENT` (e.g. incoming call)
- **THEN** the coordinator pauses the player, releases focus, mutes (`volume = 0`), keeps `isUnmuted == true`, sets `playbackHint = FocusLost`; the bound card surfaces the "tap to resume" overlay; NO `FeedEvent` is dispatched and NO `FeedEffect` is emitted

#### Scenario: Headphones unplugged while unmuted surfaces FocusLost hint

- **WHEN** wired headphones are unplugged during unmuted playback (`AudioManager.ACTION_AUDIO_BECOMING_NOISY`)
- **THEN** the coordinator pauses, releases focus, mutes, keeps `isUnmuted == true`, sets `playbackHint = FocusLost`; the bound card surfaces the resume overlay

#### Scenario: Tap on the resume overlay reacquires focus and resumes

- **WHEN** `coordinator.playbackHint == FocusLost` and the user taps the "tap to resume" overlay on the bound card
- **THEN** the coordinator reacquires audio focus, re-registers BECOMING_NOISY, sets `volume = 1`, resumes the player, clears `playbackHint` to `None`

### Requirement: `PostCardVideoEmbed` applies `Modifier.aspectRatio(lexiconRatio)` to its outer container before the poster loads

The video card's outermost container MUST set `Modifier.aspectRatio(post.embed.aspectRatio)` BEFORE `NubecitaAsyncImage` (or any poster-image composable) begins loading. This locks the card height during the LazyColumn's initial measurement pass so the poster's eventual resolution does not trigger a height jump that propagates as a visible scroll-position shift.

#### Scenario: First compose measures the card at the correct height

- **WHEN** `PostCardVideoEmbed` enters composition for the first time with `EmbedUi.Video(aspectRatio = 1.777f, ...)`
- **THEN** the card's outer container measures at `cardWidth / 1.777f` height immediately, before the poster image has loaded
- **AND** when the poster eventually loads, the LazyColumn does NOT shift scroll position to accommodate a height change

### Requirement: HLS playback starts at the lowest variant; ABR upgrade unlocked after 10 seconds of sustained playback per video

The system SHALL configure the HLS data source via `HlsMediaSource.Factory` with `DefaultTrackSelector.setForceLowestBitrate(true)` initially. After 10 seconds of sustained playback on a single video, the coordinator MUST clear `forceLowestBitrate` to unlock ABR upgrades. Rebinding to another video before 10 seconds restarts the timer at the lowest variant.

#### Scenario: First playback segment is the lowest variant

- **WHEN** a video starts playback for the first time
- **THEN** the first segment loaded is the lowest-bitrate variant available in the HLS playlist

#### Scenario: Force flag stays set during the first 10 seconds

- **WHEN** a video has been playing for less than 10 seconds
- **THEN** `setForceLowestBitrate` remains `true` and the player MUST NOT upgrade to a higher variant

#### Scenario: Force flag clears after 10 seconds; ABR upgrade unlocked

- **WHEN** a video has been playing continuously for ≥ 10 seconds on a Wi-Fi connection capable of higher bitrates
- **THEN** the coordinator clears `setForceLowestBitrate` and ABR MAY upgrade to a higher variant (no spec-level guarantee about which variant — the platform selector is authoritative)

#### Scenario: Scroll-driven rebind before the 10-second mark resets the timer

- **WHEN** the user scrolls through video `A` (muted autoplay) for 5 seconds, then scrolls to video `B` which becomes the most-visible
- **THEN** video `B`'s playback starts at the lowest variant and the 10-second timer restarts; the force flag stays set until `B` has played continuously for 10 seconds
