## ADDED Requirements

### Requirement: `FeedScreen` hosts a `FeedVideoPlayerCoordinator` scoped to its composition lifetime and supplies the `videoEmbedSlot` to PostCard

`FeedScreen` MUST instantiate a `FeedVideoPlayerCoordinator` in `remember { ... }` (no key — composition lifetime IS the screen lifetime). A `DisposableEffect(Unit) { onDispose { coordinator.release() } }` (matched no-key with the `remember`) MUST release the coordinator's resources (ExoPlayer instance, audio focus if held, BECOMING_NOISY receiver if registered, retained `MediaItem` references) when `FeedScreen` leaves composition. The coordinator observes `LazyListState.layoutInfo.visibleItemsInfo` via `snapshotFlow` (gated on `isScrollInProgress` per the feature-feed-video binding requirement) to determine the most-visible video card and bind the player accordingly — no `FeedState` field drives this; binding is purely scroll-driven. `FeedScreen` MUST supply `videoEmbedSlot = { video -> PostCardVideoEmbed(video, post = ..., coordinator = coordinator) }` to PostCard so video embeds render via the feature-impl composable bound to the screen's coordinator. NO `FeedState` or `FeedEvent` additions for video — all video playback state (binding, mute, audio focus, playback hint) is coordinator-internal.

#### Scenario: Screen exit releases the coordinator's resources

- **WHEN** the user navigates away from `FeedScreen` (back press, route change)
- **THEN** the coordinator's `release()` runs synchronously in `DisposableEffect.onDispose`; the ExoPlayer instance is destroyed; audio focus is abandoned (if held); the BECOMING_NOISY receiver is unregistered (if registered)

#### Scenario: Configuration change recreates the coordinator

- **WHEN** the device is rotated while a video is autoplaying
- **THEN** `FeedScreen` is disposed and recreated by the platform (no `android:configChanges` declared on `MainActivity`, so rotation triggers an Activity recreation); the coordinator's `release()` runs in `DisposableEffect.onDispose`; the new `FeedScreen` instance constructs a fresh coordinator + ExoPlayer; the new bound video starts autoplaying from position 0 muted (no audio surprise on rotation, because no audio focus was held)

#### Scenario: FeedState contains no video-specific fields

- **WHEN** `FeedState`'s declaration is inspected
- **THEN** there are NO video-specific fields (`currentlyPlayingPostId`, `unmutedPostId`, etc.) — all video state lives in the coordinator's StateFlows that the bound card collects directly

#### Scenario: FeedEvent contains no video-specific variants

- **WHEN** `FeedEvent`'s sealed hierarchy is inspected
- **THEN** there are NO video-specific variants (`OnVideoPlay`, `OnVideoPause`, etc.) — the coordinator's mute/unmute/resume actions are invoked directly by `PostCardVideoEmbed` without an MVI round-trip
