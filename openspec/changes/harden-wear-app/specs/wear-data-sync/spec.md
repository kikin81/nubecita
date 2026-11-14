# Spec Delta

## Purpose

How feed, message and session data travel between the phone and the watch over the Wearable Data Layer, and what each side does to keep that cheap.

## ADDED Requirements

### Requirement: Unchanged snapshots are not republished
When the phone is about to publish a feed snapshot whose posts are identical to the one it last published, it SHALL NOT encode images or write to the Data Layer. Identity SHALL ignore the publish time.

#### Scenario: Opening the watch app twice
- **WHEN** the watch app is opened twice and the phone's feed cache did not change in between
- **THEN** the second open causes no feed write and no image encoding on the phone, and no ingest on the watch

#### Scenario: One post changed
- **WHEN** a post's like count changes in the phone's cache
- **THEN** the next publish writes the snapshot

### Requirement: Sync requests are rate-limited
The watch SHALL NOT send a sync request more often than once per cooldown window, regardless of how often the app resumes. The first open after the window SHALL send one.

#### Scenario: Returning from text input
- **WHEN** the user opens the text input and returns to the app within the window
- **THEN** no sync request is sent

### Requirement: The phone resolves its session before answering the watch
When a watch action or sync request arrives while the phone app's session has not finished loading, the phone SHALL wait for it to load before deciding which account the request belongs to.

#### Scenario: Action wakes a cold phone process
- **WHEN** a like from the watch starts the phone app's process
- **THEN** the like is performed for the signed-in account and is not rejected as belonging to another account

### Requirement: Watch actions are an identified token-refresh caller
Network calls the phone makes on behalf of a watch action SHALL be attributed to a watch-action caller in token-refresh telemetry, not left untagged.

#### Scenario: Refresh triggered by a watch action
- **WHEN** a watch action causes an access-token refresh
- **THEN** the refresh is recorded with the watch-action caller

### Requirement: Unchanged images are not rewritten
The watch SHALL store each synced image once per account, identified by its content. Ingesting a snapshot SHALL NOT read or write an image the watch already has, and images no longer referenced SHALL be removed.

#### Scenario: Background sync with the same images
- **WHEN** a snapshot arrives whose images are all already stored
- **THEN** no image file is written

#### Scenario: Same avatar in two feeds
- **WHEN** one author appears in two synced feeds
- **THEN** their avatar is stored once

### Requirement: Catch-up is serialized and off the main thread
The watch SHALL run its catch-up read of Data Layer state at most once per process and never on the main thread. Ingesting SHALL be serialized so that an older snapshot cannot replace a newer one.

#### Scenario: Catch-up races the listener
- **WHEN** catch-up and a live snapshot ingest run at the same time
- **THEN** the stored feed is the newer snapshot

### Requirement: Surfaces refresh only when what they show changed
The watch SHALL ask the launcher widget and the complication to refresh only when the unread total or the signed-in state changed.

#### Scenario: Same session twice
- **WHEN** the same session snapshot is ingested twice
- **THEN** the widget and complication are asked to refresh at most once

### Requirement: Snapshots stay within the payload limit
The phone SHALL keep every snapshot it publishes within the Data Layer item size limit, dropping the oldest messages or shortening text as needed.

#### Scenario: Ten long conversations
- **WHEN** ten conversations each hold twenty messages of maximum length
- **THEN** the published snapshot is within the limit and contains the newest messages

### Requirement: The wire format is pinned
The contract's serialized form for each shipped major version SHALL be pinned by fixtures that the current code reads and writes.

#### Scenario: A field is renamed
- **WHEN** a contract field is renamed without a version change
- **THEN** a contract test fails

### Requirement: Watch-authored rows survive a schema change
A watch database schema change SHALL preserve pending posts, pending replies and pending actions. Synced copies MAY be dropped and re-synced.

#### Scenario: App update with a failed post waiting
- **WHEN** the watch app updates to a new schema while a failed post is waiting for retry
- **THEN** the failed post and its text are still there
