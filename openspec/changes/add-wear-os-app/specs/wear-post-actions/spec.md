## ADDED Requirements

### Requirement: Watch writes likes and bookmarks as queued actions
Like, unlike, bookmark and unbookmark on the watch SHALL each be written as an urgent action `DataItem` at `/v1/<accountDid>/action/<uuid>`, carrying `createdAt` and `accountDid`. Like and bookmark actions SHALL NOT expire.

#### Scenario: Like while the phone is away
- **WHEN** the user likes a post with no phone connection and the phone comes back in reach hours later
- **THEN** the queued action is delivered and the phone performs the like

### Requirement: Phone executes watch actions and reports a result
The phone SHALL execute each action through `LikeRepostRepository` or `BookmarkRepository` via an `@IntoSet` `WearActionHandler`. It SHALL then write a result item (`ok`, or `failed` with a reason) and delete the action item. It SHALL drop any action whose `accountDid` isn't the signed-in account.

#### Scenario: Successful bookmark
- **WHEN** the phone runs a bookmark action successfully
- **THEN** it writes an `ok` result and deletes the action item

#### Scenario: Action for a signed-out account
- **WHEN** the phone receives an action whose `accountDid` isn't the signed-in account
- **THEN** it does not run the action, and it answers `failed` with the reason `account`

### Requirement: Optimistic overlay with silent revert
The watch SHALL show a pending like or bookmark immediately by overlaying its pending actions on the synced viewer state. On an `ok` result it SHALL keep the state until the next snapshot confirms it. On a `failed` result it SHALL drop the overlay and show the post's previous state, without an error message.

#### Scenario: Phone rejects a like
- **WHEN** a like action comes back `failed` (for example, the post was deleted)
- **THEN** the post's like control returns to unliked and no error is shown

### Requirement: Like and bookmark controls meet touch-target size
The like and bookmark controls on the watch SHALL each have a touch target of at least 48×48 dp and an accessible label that states the current state.

#### Scenario: Screen reader on a liked post
- **WHEN** TalkBack focuses the like control of a liked post
- **THEN** it announces the control as liked, with an action to unlike
