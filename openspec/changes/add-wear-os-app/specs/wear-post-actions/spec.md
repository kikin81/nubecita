## ADDED Requirements

### Requirement: Watch writes likes, reposts and bookmarks as queued actions
Like, unlike, repost, unrepost, bookmark and unbookmark on the watch SHALL each be written as an urgent action `DataItem` at `/v1/<accountDid>/action/<uuid>`, carrying `createdAt` and `accountDid`. None of these actions SHALL expire, including the undos (unlike, unrepost, unbookmark).

#### Scenario: Like while the phone is away
- **WHEN** the user likes a post with no phone connection and the phone comes back in reach hours later
- **THEN** the queued action is delivered and the phone performs the like

### Requirement: Phone executes watch actions and reports a result
The phone SHALL execute each action (like, repost and bookmark, and their undos) through `LikeRepostRepository` or `BookmarkRepository` via an `@IntoSet` `WearActionHandler`. It SHALL then write a result item (`ok`, or `failed` with a reason) and delete the action item. It SHALL drop any action whose `accountDid` isn't the signed-in account.

#### Scenario: Successful bookmark
- **WHEN** the phone runs a bookmark action successfully
- **THEN** it writes an `ok` result and deletes the action item

#### Scenario: Action for a signed-out account
- **WHEN** the phone receives an action whose `accountDid` isn't the signed-in account
- **THEN** it does not run the action, and it answers `failed` with the reason `account`

### Requirement: Optimistic overlay with silent revert
The watch SHALL show a pending like, repost or bookmark, or a pending undo of one, immediately by overlaying its pending actions on the synced viewer state. On an `ok` result it SHALL keep the state until the next snapshot confirms it. On a `failed` result it SHALL drop the overlay and show the post's previous state, without an error message.

#### Scenario: Phone rejects a like
- **WHEN** a like action comes back `failed` (for example, the post was deleted)
- **THEN** the post's like control returns to unliked and no error is shown

### Requirement: Repost asks for confirmation
A repost on the watch SHALL ask for confirmation first, because it is public and watch taps are easy to misfire. Undoing a repost, and liking or bookmarking, SHALL take a single tap.

#### Scenario: Accidental repost tap
- **WHEN** the user taps repost on a post they haven't reposted
- **THEN** the watch asks "Repost this?" and writes no action unless the user confirms

### Requirement: Actions appear only when the phone supports them
The post screen SHALL show the like, repost and bookmark controls only when the paired phone advertises support for watch actions. A watch paired with an older phone SHALL show the post without them.

#### Scenario: Older phone build
- **WHEN** the paired phone runs a build without watch-action support
- **THEN** the post screen shows no action controls and no error

### Requirement: Open a post on the phone
The post screen SHALL offer "Open on phone", which opens the post's `https://bsky.app/profile/<did>/post/<rkey>` link on the paired phone and shows the Wear open-on-phone confirmation.

#### Scenario: Open a long thread
- **WHEN** the user taps "Open on phone" on a post
- **THEN** the phone opens that post in Nubecita and the watch shows the open-on-phone confirmation

### Requirement: Action controls meet touch-target size
The like, repost and bookmark controls on the watch SHALL each have a touch target of at least 48×48 dp and an accessible label that states the current state.

#### Scenario: Screen reader on a liked post
- **WHEN** TalkBack focuses the like control of a liked post
- **THEN** it announces the control as liked, with an action to unlike
