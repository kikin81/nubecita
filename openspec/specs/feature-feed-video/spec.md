# feature-feed-video Specification

## Purpose

HLS-backed video playback inside the feed. Covers the data-path mapping for `app.bsky.embed.video#view`, the rendered card surface (poster + optional duration chip + autoplay-muted player surface + mute toggle + tap-to-resume affordance), the single-player coordinator that owns one materialized `ExoPlayer` instance scoped to `FeedScreen`'s composition lifetime and binds it to the most-visible video card, and the lifecycle / inset / audio-focus contracts those moving parts must satisfy.

The spec was authored as the openspec change `add-feature-feed-video-embeds` (archived 2026-04-27) and shipped across three implementation PRs: deps + smoke (`nubecita-sbc.1`, PR #57), data path + thumbnail render (`nubecita-sbc.3`, PR #58), and autoplay coordinator + mute toggle (`nubecita-sbc.4`, PR #59).
## Requirements
### Requirement: `FeedViewPostMapper` dispatches `app.bsky.embed.video#view` to `EmbedUi.Video`

`FeedViewPostMapper.toEmbedUi` MUST recognize the `app.bsky.embed.video#view` discriminator on the embed union and produce an `EmbedUi.Video` instance carrying the optional poster URL, the HLS playlist URL (m3u8), the aspect ratio (`width:height` from the lexicon, defaulting to 16:9 when absent), and the optional alt-text. The `durationSeconds` field MUST be `null` for v1 — the lexicon does not currently expose duration. Posts whose video lexicon is well-formed (contains a non-empty `playlist`) MUST yield a non-null `EmbedUi.Video`. Posts whose video lexicon is missing the required `playlist` field MUST fall through to `EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")` rather than throwing. Posts whose video view omits the optional `thumbnail` MUST still yield `EmbedUi.Video` (with `posterUrl = null`); the render layer handles the null case.

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

PostCard's `EmbedSlot` (in `:designsystem`) MUST dispatch on `EmbedUi.Video` by invoking a host-supplied `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit` lambda. The feed feature MUST supply a `PostCardVideoEmbed` composable (defined in `:feature:feed:impl`) that satisfies all of:

- Renders the poster image filling the card width with the lexicon's aspect ratio. When `posterUrl` is null, renders a gradient placeholder filling the same aspect ratio.
- Renders a duration chip in the bottom-right corner formatted as `m:ss` (or `h:mm:ss` for ≥ 1h) **only when `durationSeconds` is non-null**. The lexicon does not currently expose duration, so v1 ships with no chip rendered for any post; the chip code path stays in place for the future when duration is sourced.
- When the post is the coordinator's currently-bound post (most-visible video card whose `visible-fraction > 0.6`), renders `PlayerSurface(player = coordinator.player, surfaceType = SURFACE_TYPE_TEXTURE_VIEW)` underneath the poster and cross-fades the poster out as the first frame arrives. Other video cards in the same composition tree MUST pass `player = null` to their `PlayerSurface` (or omit it) so only one surface holds the shared `ExoPlayer`.
- Renders a mute / unmute icon overlay in the top-right corner, driven by `coordinator.isUnmuted: StateFlow<Boolean>`. Tapping the icon MUST call `coordinator.toggleMute()` directly (PostCardVideoEmbed lives in `:feature:feed:impl`, same module as the coordinator — no `PostCallbacks` round-trip needed).
- Tap on the card body (anywhere outside the mute icon's hit area) MUST invoke `PostCallbacks.onTap(post)` — the existing PostCard tap callback — which the feed feature wires to navigate to the post-detail screen.

The card MUST NOT render a centered play affordance, a play/pause button, or a progress bar in v1. The mute icon SHALL be the only inline control; videos play automatically while bound, and the detail screen owns the full controls + audio focus.

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

The system's `FeedVideoPlayerCoordinator` MUST bind the player to the topmost feed item whose visible-fraction exceeds 0.6 AND which carries an addressable video target. There is NO separate user gesture required to "play" a video; binding is purely scroll-driven. When the bound card scrolls below the threshold (no visible video card meets the criterion), the coordinator MUST `pause()` the player AND release audio focus IF currently held (i.e. `isUnmuted` was `true` — the user had unmuted this card). The `isUnmuted` state MUST also reset to `false` on this scroll-away — unmute does not carry over to the next visible video; the user must explicitly unmute the new bound card if they want audio.

A feed item is "addressable" for video binding when ANY of:

- its `embed is EmbedUi.Video` (parent video), OR
- its `embed is EmbedUi.RecordWithMedia` whose `media is EmbedUi.Video` (recordWithMedia media video), OR
- its `embed is EmbedUi.RecordWithMedia` whose `record is EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video` (recordWithMedia nested quoted-post video), OR
- its `embed is EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video` (quoted-post video).

When a feed item is addressable through more than one of these paths simultaneously, the resolver MUST apply this precedence:

1. Parent video (top-level `EmbedUi.Video`) wins over everything else.
2. RecordWithMedia.media video wins over the nested quoted-post video inside the same `RecordWithMedia`.
3. Top-level `EmbedUi.Record`'s quoted-post video and `EmbedUi.RecordWithMedia.record`'s quoted-post video are at the same precedence level — they don't co-occur structurally (a post's `embed` is exactly one of `Record` or `RecordWithMedia`), so the precedence question is moot.

The reasoning for media beating nested quoted: the media is the user's primary upload — explicitly attached to THIS post — while the nested quoted-post-video is contextual content authored by someone else. Letting the nested quoted-video win would create disjointed UX (large frozen poster at the top with a smaller autoplaying video tucked inside the quoted card below).

The `VideoBindingTarget(postId, playlistUrl)` data class shape MUST NOT change. The bind identity (`postId`) MUST be the URI of the post whose video plays:

- For a parent video: `postId = post.id` (the parent's AT URI; unchanged from prior behavior).
- For a RecordWithMedia media video: `postId = post.id` (same item, same identity — precedence above guarantees only one of parent or media-video binds at a time per item).
- For a quoted-post video (whether top-level `Record` or inside `RecordWithMedia.record`): `postId = quotedPost.uri` (the quoted post's AT URI).

This makes bind identities naturally distinct between a parent-side video and a quoted-side video for the same feed item, and across different feed items' quoted videos, without a `Source` tag or a synthetic `#quoted` suffix on the bind key. The coordinator's existing "is this the same target as before?" rebind logic continues to work unchanged.

The visibility math MUST remain at the parent feed-item granularity — it MUST NOT use `Modifier.onGloballyPositioned` callbacks, sub-rect computation, or any per-composable position reporting. A nested video binds when its parent feed item passes the existing 0.6 visible-fraction threshold; a sub-rect refinement (true "where on screen is the nested video") is explicitly out of scope and is the natural promotion path if real-world feedback shows the parent-item-granular bind picks the wrong target.

The internal `videoBindingFor(post: PostUi): VideoBindingTarget?` resolver MUST consult the `EmbedUi.quotedRecord` extension property (from `:data:models`) when looking for the quoted-post-video path — single source of truth for "where do quotes hide" across feature-feed and feature-feed-video.

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

The autoplay flow (most-visible card binds + plays at `volume = 0`) MUST NOT request audio focus and MUST NOT register the BECOMING_NOISY receiver. Opening the app to read the feed while listening to music MUST NOT interrupt the user's audio. Audio focus is requested ONLY when the user explicitly taps the unmute icon on the bound card; it is released on user mute, scroll-away from an unmuted card, or screen exit.

When focus IS held (post-unmute) and the OS signals loss (`AUDIOFOCUS_LOSS_TRANSIENT` from incoming call, music app gaining focus; `ACTION_AUDIO_BECOMING_NOISY` from headphones unplug), the coordinator MUST: pause the player; set `volume = 0`; release audio focus; unregister the BECOMING_NOISY receiver; set `coordinator.playbackHint = FocusLost`; LEAVE `coordinator.isUnmuted == true` (user's intent preserved). The bound card collects `playbackHint` directly and renders a localized "tap to resume" overlay when non-`None`. Tapping the overlay calls `coordinator.resume()` which: reacquires focus, re-registers BECOMING_NOISY, sets `volume = 1`, resumes player, clears `playbackHint = None`. The hint MUST NOT round-trip through the VM event stream or `FeedEffect`.

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

The system SHALL configure the HLS data source via `media3-exoplayer-hls`'s default `HlsMediaSource.Factory`. The `DefaultTrackSelector` MUST be configured with `setForceLowestBitrate(true)` for the initial selection. **After 10 seconds of sustained playback on a single video** (NOT after the first segment loads — that would unlock ABR for videos the user merely glanced at and scrolled past), the coordinator MUST clear the `forceLowestBitrate` flag, allowing ABR to upgrade based on observed throughput. If the bound video changes (scroll-driven rebind to a different card) before the 10-second mark, the next playback session restarts the 10-second timer at the lowest variant.

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
