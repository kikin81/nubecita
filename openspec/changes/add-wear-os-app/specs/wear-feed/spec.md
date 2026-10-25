## ADDED Requirements

### Requirement: Phone Watch settings page picks up to three feeds
The phone SHALL show a **Settings → Watch** page only while a node advertising `nubecita_wear` is paired. The page SHALL list the user's pinned feeds as checkboxes, with Following checked by default, and SHALL allow at most 3 checked feeds. Following counts toward the limit. The selection SHALL persist in `:core:preferences`. All strings SHALL ship in en, es-419 and pt-BR.

#### Scenario: Fourth feed is disabled
- **WHEN** three feeds are already checked
- **THEN** the remaining unchecked feeds are disabled and a supporting line reads "Up to 3 feeds"

#### Scenario: No watch paired
- **WHEN** no node advertises `nubecita_wear`
- **THEN** Settings shows no Watch entry

### Requirement: Feed snapshots carry posts, viewer state and thumbnails
For each selected feed, the phone SHALL publish one ordered snapshot of its latest posts (about 50) at `/v1/<accountDid>/feed/<feedKey>`. Each post SHALL carry:
- author name, handle and avatar
- text
- the timestamp
- the viewer's like record URI and bookmark state
- downscaled image thumbnails, or a poster frame for video

Thumbnails and avatars SHALL be sent as `Asset`s.

#### Scenario: Video post on the watch
- **WHEN** a synced post contains a video
- **THEN** the watch shows its poster frame and offers no playback

### Requirement: Watch stores feeds offline and replaces them atomically
The watch SHALL store each feed snapshot in its own Room database, replacing that feed's rows in a single transaction. It SHALL show the stored feed with or without a phone connection, labelled with how long ago it was updated.

#### Scenario: Phone out of reach
- **WHEN** the user opens a synced feed with no phone connection
- **THEN** the last stored posts appear with an "Updated N min ago" label

### Requirement: Watch feed list and post screens
The watch SHALL show a feed switcher when more than one feed is synced, a scrollable feed list, and a post screen. The lists SHALL follow the `wear-app-shell` UI requirement, including rotary scrolling.

#### Scenario: Switching feeds
- **WHEN** two or more feeds are synced and the user picks another feed in the switcher
- **THEN** the list shows that feed's stored posts
