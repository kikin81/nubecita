## ADDED Requirements

### Requirement: Phone is the only Bluesky client
The Wear app SHALL NOT hold an OAuth session or make network calls to Bluesky. All Bluesky reads and writes SHALL run on the phone through its existing repositories.

#### Scenario: Watch has no network client for Bluesky
- **WHEN** the `:wear:app` dependency graph is inspected
- **THEN** it does not depend on the atproto runtime, OAuth or `:core:auth` modules

### Requirement: Versioned Data Layer contract under /v1/
Phone and watch SHALL exchange only the DTOs defined in `:core:wear-contract`, as `DataItem`s and messages under the `/v1/` path prefix. Every payload SHALL carry a `schemaVersion`. A watch that receives a newer major `schemaVersion` than it understands SHALL keep its last data and ask the user to update the watch app. Images SHALL travel as `Asset`s, never inlined in a `DataItem`.

#### Scenario: Newer schema from the phone
- **WHEN** the watch receives a snapshot whose major `schemaVersion` is higher than the one it supports
- **THEN** it ignores the payload, keeps showing the previous data, and shows "Update Nubecita on your watch"

### Requirement: Capabilities gate publishing
The watch app SHALL advertise `nubecita_wear` and the phone app SHALL advertise `nubecita_phone`. The phone SHALL publish snapshots and run watch-only fetches only while at least one reachable node advertises `nubecita_wear`.

#### Scenario: No watch paired
- **WHEN** the widget refresh or DM poll runs and no node advertises `nubecita_wear`
- **THEN** no watch snapshot is built or published, and no watch-only network call is made

### Requirement: Urgency matches who is waiting
Watch-written action `DataItem`s and the phone's pull-on-open republish SHALL be marked urgent. Snapshots published from the periodic workers SHALL NOT be marked urgent, so the system can batch them.

#### Scenario: Periodic publish is non-urgent
- **WHEN** the 15-minute widget refresh publishes a feed snapshot
- **THEN** the `PutDataRequest` is not urgent

#### Scenario: User action is urgent
- **WHEN** the user likes a post on the watch
- **THEN** the action `PutDataRequest` is urgent

### Requirement: Listeners wake only for Nubecita paths
Each app's `WearableListenerService` SHALL declare intent filters with `scheme="wear"`, `host="*"` and `pathPrefix="/v1/"`, with no catch-all filter.

#### Scenario: Unrelated Data Layer traffic
- **WHEN** a `DataItem` is written under a path outside `/v1/`
- **THEN** Nubecita's listener service is not started

### Requirement: Session snapshot and sign-out wipe
The phone SHALL publish `/v1/session` with the signed-in account's DID, handle and avatar. On sign-out it SHALL publish a signed-out session and delete that account's snapshot items. The watch SHALL delete all locally stored data for an account when its session is signed out or replaced by a different account.

#### Scenario: User signs out on the phone
- **WHEN** the user signs out in the phone app while the watch is reachable
- **THEN** the watch deletes that account's posts, conversations, messages and pending actions, and shows "Sign in on your phone"

### Requirement: Pull on open refreshes feeds without new background work
When the watch app opens, it SHALL send a `/v1/sync/request` message. The phone SHALL answer by immediately republishing its cached feed snapshots (urgently) and enqueuing the existing one-time widget refresh with the `KEEP` policy, tagged `RefreshCaller.Widget`. Pull-on-open SHALL NOT trigger a DM fetch.

#### Scenario: Watch app opened next to the phone
- **WHEN** the user opens the watch app with the phone reachable
- **THEN** the phone republishes the cached feeds urgently and enqueues at most one widget refresh, and no DM network call is made
