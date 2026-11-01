## ADDED Requirements

### Requirement: Viewer pin and thread-mute state and the reply root are mapped from the wire

`toViewerStateUi` SHALL map `app.bsky.feed.defs#viewerState.pinned` to `ViewerStateUi.isPinned` and `viewerState.threadMuted` to `ViewerStateUi.isThreadMuted`, each defaulting to `false` when absent. `toPostUiCore` SHALL populate the post's thread-root URI from the post record's `reply.root.uri`, and leave it `null` for a post that is not a reply. These fields are server-sourced; no mapper or VM may derive them from other state.

#### Scenario: Pinned own post

- **WHEN** a `postView` whose `viewer.pinned = true` is mapped
- **THEN** the resulting `PostUi.viewer.isPinned` is `true`

#### Scenario: Absent flags default to false

- **WHEN** a `postView` whose `viewer` omits `pinned` and `threadMuted` is mapped
- **THEN** `isPinned` and `isThreadMuted` are both `false`

#### Scenario: Reply carries its thread root

- **WHEN** a post whose record has `reply.root.uri = "at://did:plc:a/app.bsky.feed.post/root"` is mapped
- **THEN** the `PostUi`'s thread-root URI equals `"at://did:plc:a/app.bsky.feed.post/root"`

#### Scenario: Top-level post has no reply root

- **WHEN** a post whose record has no `reply` is mapped
- **THEN** the `PostUi`'s thread-root URI is `null`
