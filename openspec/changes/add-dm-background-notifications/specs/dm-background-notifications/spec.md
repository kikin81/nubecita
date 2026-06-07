## ADDED Requirements

### Requirement: Background polling for new direct messages
The system SHALL detect new inbound direct messages while the app is backgrounded by polling `chat.bsky.convo` on-device using the signed-in user's own session, and SHALL post a local notification for them. It SHALL NOT use any server, push gateway, or off-device credential.

#### Scenario: New inbound message while backgrounded
- **WHEN** the background poll runs and `chat.bsky.convo.getLog` reports a message-create event whose sender is not the viewer, newer than the stored cursor
- **THEN** the system posts a local notification for that conversation and advances the stored cursor past the processed events

#### Scenario: Only the viewer's own activity since last poll
- **WHEN** the poll runs and the only new events are messages sent by the viewer (or non-message events)
- **THEN** no notification is posted and the cursor still advances

#### Scenario: No new events
- **WHEN** the poll runs and there are no events newer than the stored cursor
- **THEN** no notification is posted and no error is surfaced

### Requirement: Battery-cooperative scheduling
Background work SHALL stay within Google's WorkManager guidelines and cooperate with Doze and App Standby. The system MUST NOT use a foreground service for polling cadence, exact alarms, wakelocks, or request battery-optimization exemptions. Polling SHALL use a periodic `WorkManager` request at the platform-minimum interval with a network constraint, accepting OS-imposed delay.

#### Scenario: Worker scheduled with network constraint
- **WHEN** background DM notifications are enabled and the user is signed in
- **THEN** a unique periodic work request is enqueued requiring network connectivity, at the platform-minimum period

#### Scenario: Device in Doze
- **WHEN** the device is in Doze and defers the work
- **THEN** the system accepts the deferral (the notification may be delayed) and does not attempt to force earlier execution

### Requirement: One message-checking setting gates all polling
A single user setting SHALL control all message-checking, default on. When off, it SHALL disable BOTH the background DM-notification worker AND the foreground unread poller — Nubecita performs no message checks at all (no notifications and no unread-badge updates). All polling SHALL only run for a signed-in user.

#### Scenario: Disabled by the user
- **WHEN** the user turns the message-checking setting off
- **THEN** the background periodic work is cancelled, the foreground unread poll stops, and the unread badge no longer updates

#### Scenario: Signed out
- **WHEN** the user signs out
- **THEN** the background periodic work is cancelled

#### Scenario: Enabled and signed in
- **WHEN** the setting is on and a session is active
- **THEN** the background periodic work is registered and the foreground unread poll runs while foregrounded

### Requirement: No double-notification with the foreground surface
While the app is foregrounded, the background worker SHALL NOT post DM notifications, because the in-app unread surface already reflects new messages. To avoid permanently dropping messages the user never actually saw, the worker SHALL NOT advance its cursor on a foreground-suppressed run; instead, a message that was read in the foreground SHALL be excluded from later notification by the read-state filter (its conversation is no longer unread server-side).

#### Scenario: Poll coincides with the app foregrounded
- **WHEN** the worker runs while the app is in the foreground
- **THEN** it does not post a notification and does not advance the cursor

#### Scenario: Message already seen in the foreground
- **WHEN** the user read a conversation in the foreground (clearing its server-side unread state) and a later background poll re-reads those events
- **THEN** no notification is posted for that conversation, because the read-state filter excludes it

#### Scenario: Foregrounded but conversation not read
- **WHEN** the worker runs while the app is foregrounded on a non-Chats screen, so the user never reads the conversation, and the app is later backgrounded
- **THEN** the held cursor lets the next background poll still notify the unread conversation

### Requirement: Per-conversation notification and dedup
The system SHALL post at most one notification per conversation at a time, keyed by a stable per-conversation identity, so a later message updates the existing notification rather than stacking, and a re-delivered poll does not duplicate a notification.

#### Scenario: Second message in the same conversation
- **WHEN** a second new message arrives in a conversation that already has a pending notification
- **THEN** the existing notification is updated rather than a second one posted

#### Scenario: Re-delivered poll
- **WHEN** the worker runs again over events already notified (e.g. a crash before the cursor was persisted)
- **THEN** the user does not see a duplicate notification

### Requirement: Notification tap opens the conversation
Tapping a DM notification SHALL open the corresponding conversation thread.

#### Scenario: Tap a DM notification
- **WHEN** the user taps a DM notification
- **THEN** the app opens that conversation's thread
