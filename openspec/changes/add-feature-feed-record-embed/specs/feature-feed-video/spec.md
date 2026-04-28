## MODIFIED Requirements

### Requirement: Coordinator binds to the most-visible video card based purely on scroll position

The system's `FeedVideoPlayerCoordinator` MUST bind the player to the topmost feed item whose visible-fraction exceeds 0.6 AND which carries an addressable video target. There is NO separate user gesture required to "play" a video; binding is purely scroll-driven. When the bound card scrolls below the threshold (no visible video card meets the criterion), the coordinator MUST `pause()` the player AND release audio focus IF currently held (i.e. `isUnmuted` was `true` — the user had unmuted this card). The `isUnmuted` state MUST also reset to `false` on this scroll-away — unmute does not carry over to the next visible video; the user must explicitly unmute the new bound card if they want audio.

A feed item is "addressable" for video binding when EITHER:

- its `embed is EmbedUi.Video` (parent video), OR
- its `embed is EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video` (quoted-post video).

When a feed item carries both a parent video and a quoted-post video (vanishingly rare in real-world data — a video post that quotes another video post), the parent video MUST win — this is the "B-lite" precedence rule per the design.

The `VideoBindingTarget(postId, playlistUrl)` data class shape MUST NOT change. The bind identity (`postId`) MUST be the URI of the post whose video plays:

- For a parent video: `postId = post.id` (the parent's AT URI; unchanged from prior behavior).
- For a quoted-post video: `postId = quotedPost.uri` (the quoted post's AT URI).

This makes bind identities naturally distinct between a parent video and a quoted video for the same feed item, and across different feed items' quoted videos, without a `Source` tag or a synthetic `#quoted` suffix on the bind key. The coordinator's existing "is this the same target as before?" rebind logic continues to work unchanged.

The visibility math MUST remain at the parent feed-item granularity — it MUST NOT use `Modifier.onGloballyPositioned` callbacks, sub-rect computation, or any per-composable position reporting. A quoted video binds when its parent feed item passes the existing 0.6 visible-fraction threshold; a sub-rect refinement (true "where on screen is the quoted video") is explicitly out of scope for this change and is the natural promotion path if real-world feedback shows the parent-item-granular bind picks the wrong target.

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

#### Scenario: Quoted-post video binds when the parent has no own video

- **WHEN** the topmost feed item meeting the 0.6 visibility threshold has `embed is EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video` with `playlistUrl = "https://video.bsky.app/.../q.m3u8"`
- **THEN** `mostVisibleVideoTarget` returns `VideoBindingTarget(postId = quotedPost.uri, playlistUrl = "https://video.bsky.app/.../q.m3u8")`; the coordinator binds the player to this target

#### Scenario: Parent video wins over quoted video on the same feed item

- **WHEN** the topmost feed item meeting the 0.6 visibility threshold has BOTH `embed is EmbedUi.Video` (parent) with `playlistUrl = "p.m3u8"` AND a quoted post that also carries a `QuotedEmbedUi.Video` with `playlistUrl = "q.m3u8"`
- **THEN** `mostVisibleVideoTarget` returns `VideoBindingTarget(postId = post.id, playlistUrl = "p.m3u8")` — the parent video; the quoted video is NOT considered

#### Scenario: Topmost rule applies across mixed parent/quoted videos

- **WHEN** post `A` (parent video, offset 0) and post `B` (quoted video, offset 800) are both visible above the 0.6 threshold simultaneously
- **THEN** `mostVisibleVideoTarget` returns `A`'s parent-video target — topmost wins, regardless of whether the candidate is a parent or quoted video
