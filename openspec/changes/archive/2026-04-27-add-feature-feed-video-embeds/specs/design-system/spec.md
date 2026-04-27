## ADDED Requirements

### Requirement: `PostCard` exposes a `videoEmbedSlot` lambda for host-supplied video render

`PostCard` MUST accept an optional `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit = {}` parameter. When `EmbedSlot` dispatches and the post's embed is `EmbedUi.Video`, it MUST invoke the slot lambda with the video data; the default empty lambda renders nothing (so a `:designsystem`-only consumer that does NOT supply the slot sees no video render — preserving the v0 behavior where `EmbedUi.Video` would have fallen through). `:designsystem` MUST NOT import or depend on any feature module to render the video; the slot's body lives in `:feature:feed:impl` and is supplied by `FeedScreen` when it constructs PostCard.

`PostCard` adds NO new `PostCallbacks` lambdas for video. Card-body tap on a video card uses the existing `PostCallbacks.onTap(post)` to navigate to detail. Video-specific user gestures (mute / unmute) are handled directly by the host-supplied video composable (which lives in `:feature:feed:impl` and can call into the screen's `FeedVideoPlayerCoordinator` directly without crossing the `:designsystem` boundary). This avoids round-tripping per-card transient gestures through MVI.

#### Scenario: Default slot is no-op

- **WHEN** `PostCard` is invoked without supplying `videoEmbedSlot` and the post's `embed` is `EmbedUi.Video`
- **THEN** the embed slot region renders empty (no crash, no `Unsupported` chip, no fallback)

#### Scenario: Host-supplied slot renders the video composable

- **WHEN** `PostCard` is invoked with `videoEmbedSlot = { video -> PostCardVideoEmbed(video, post, coordinator) }` and the post's `embed` is `EmbedUi.Video`
- **THEN** `PostCardVideoEmbed` is invoked with the `EmbedUi.Video` instance; PostCard does NOT also render its own placeholder for the video region

#### Scenario: `:designsystem` does not depend on `:feature:feed:impl`

- **WHEN** `:designsystem`'s `build.gradle.kts` is inspected
- **THEN** there SHALL be no `implementation(project(":feature:feed:impl"))` (or any other feature module); the slot pattern keeps the dependency direction `:feature:feed:impl → :designsystem` and never the reverse

#### Scenario: `:designsystem` does not import FeedEvent

- **WHEN** the `:designsystem` source tree is searched for `import net.kikin.nubecita.feature.feed.impl.FeedEvent` (or any `feature.*` event type)
- **THEN** there SHALL be no match
