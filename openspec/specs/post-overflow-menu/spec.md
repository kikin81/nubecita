# post-overflow-menu Specification

## Purpose
The shared post overflow menu: one grouped bottom sheet, opened from `PostCard` and the vertical video page, whose rows come from a single pure rule function of `PostUi`. It also owns the thread mute write path — `app.bsky.graph.muteThread` / `unmuteThread` on the thread root — with optimistic state across every cached post in the thread.
## Requirements
### Requirement: Post overflow opens one shared grouped bottom sheet

Tapping "…" on a `PostCard`, or the overflow on a vertical-video page, SHALL open the same `:designsystem` bottom sheet (an M3 `ModalBottomSheet`). The sheet renders rows as rounded group cards, with each row's label on the left and its icon on the right. The `DropdownMenu` in `PostOverflowAffordance` and the one in `VideoOverflowMenu` SHALL be removed.

#### Scenario: Post card opens the sheet

- **WHEN** the user taps "…" on any `PostCard` in Feed, Post detail or Profile
- **THEN** a bottom sheet opens with the grouped rows for that post, and no `DropdownMenu` is shown

#### Scenario: Tapping a row closes the sheet and dispatches once

- **WHEN** the user taps a row
- **THEN** the sheet is dismissed and the row's `PostOverflowAction` is dispatched exactly once

### Requirement: Both surfaces share one row rule function

The rows SHALL come from one pure function of `PostUi` that is shared by `PostCard` and the vertical-video page. Neither surface may keep its own copy of the row rules.

#### Scenario: Video page shows the same rows as a post card

- **WHEN** the user opens the overflow on a vertical-video page for post `P`
- **THEN** the rows equal those a `PostCard` shows for `P`

### Requirement: Own posts group as pin, utility, thread, delete

For a post with `viewer.isOwnPost = true`, the rule function SHALL return these groups, in order:

1. Pin to your profile / Unpin from profile (added by `post-pinning`)
2. Translate, Copy post text
3. Mute thread / Unmute thread
4. Delete post

It MUST NOT include Report post, Report @handle, Mute/Unmute @handle or Block/Unblock @handle.

#### Scenario: Own post omits self-aimed moderation rows

- **WHEN** the rows are computed for a post with `isOwnPost = true`
- **THEN** they contain no Report, Mute @handle, Unmute @handle, Block @handle or Unblock @handle row, and Delete post is the last row

### Requirement: Others' posts group as utility, thread, moderation

For a post with `viewer.isOwnPost = false`, the rule function SHALL return these groups, in order:

1. Translate, Copy post text
2. Mute thread / Unmute thread
3. Mute/Unmute @handle, Block/Unblock @handle, Report post, Report @handle

There is no Delete row.

#### Scenario: Others' post ends with moderation

- **WHEN** the rows are computed for a post with `isOwnPost = false`
- **THEN** there is no Delete row, and the last group ends with Report post, Report @handle

### Requirement: Toggle rows and Translate follow post state

Exactly one row of each toggle pair SHALL appear, keyed on its flag:

- Mute / Unmute @handle on `isAuthorMutedByViewer`
- Block / Unblock @handle on `isAuthorBlockedByViewer`
- Mute thread / Unmute thread on `isThreadMuted`

Translate SHALL appear only when the post's text is not blank.

#### Scenario: Thread-muted post offers Unmute thread

- **WHEN** the rows are computed for a post with `isThreadMuted = true`
- **THEN** the thread row is "Unmute thread" and dispatches `PostOverflowAction.UnmuteThread`

#### Scenario: Media-only post has no Translate

- **WHEN** the rows are computed for a post whose text is blank
- **THEN** there is no Translate row

### Requirement: A closed overflow costs nothing on the scroll path

While the sheet is closed, a `PostCard` MUST NOT compose a `ModalBottomSheet`, sheet state, or any row content. A test SHALL pin this laziness, replacing `DropdownMenuLazinessTest`.

#### Scenario: Closed card composes no sheet

- **WHEN** a `PostCard` is composed with its overflow closed
- **THEN** no sheet or row nodes exist in the composition

### Requirement: Mute thread and Unmute thread call the server on the thread root

`DefaultPostInteractionHandler` SHALL call `app.bsky.graph.muteThread` for `MuteThread`, and `unmuteThread` for `UnmuteThread`. The `root` is the post's thread-root URI, or its own URI if it is not a reply. Neither action may emit the coming-soon effect; `UnblockAuthor` keeps it. On success it emits "Thread muted" or "Thread unmuted".

#### Scenario: Muting from a reply mutes the root

- **WHEN** the user taps "Mute thread" on a reply whose thread root is `R`
- **THEN** `muteThread(root = R)` is called, not `muteThread(root = <reply uri>)`

### Requirement: Thread mute is optimistic across the whole thread

The handler SHALL flip `isThreadMuted` right away on every cached post whose thread key (`threadRootUri ?: id`) equals the root — the root post itself included. The flipped value is kept as a local override, in both directions, until a server refresh agrees with it.

On failure, it MUST restore every flipped post and emit an error snackbar. A repeat tap on the same root while a write is in flight is ignored.

#### Scenario: Optimistic flip across the thread

- **WHEN** "Mute thread" is tapped on a post whose root is `R`, and the cache holds three posts with root `R`
- **THEN** all three show `isThreadMuted = true` before the call returns

#### Scenario: The root post itself flips

- **WHEN** "Mute thread" is tapped on a reply whose root `R` is also cached (with `threadRootUri = null`)
- **THEN** the cached root post `R` shows `isThreadMuted = true` too

#### Scenario: Failure rolls back

- **WHEN** `muteThread` fails
- **THEN** every post with root `R` returns to `isThreadMuted = false`, and an error snackbar is emitted

#### Scenario: Stale refresh does not undo a successful mute

- **WHEN** `muteThread` succeeds and a later refresh returns the root post with `threadMuted` absent
- **THEN** the cached post still shows `isThreadMuted = true`, and the override clears once a refresh reports `threadMuted = true`

### Requirement: An open sheet dispatches exactly one action

The sheet SHALL accept only the first row tap while open: rows stay tappable during the hide animation, so later taps MUST be ignored. Dispatch happens after `hide()` completes, sequentially on the composition scope.

#### Scenario: A second tap during the hide animation is ignored

- **WHEN** the user taps "Copy post text" and then "Translate post" before the sheet finishes hiding
- **THEN** only `CopyPostText` is dispatched

### Requirement: The sheet body scrolls

The sheet body SHALL be vertically scrollable so every row stays reachable on short windows or with enlarged text.

#### Scenario: Short landscape window

- **WHEN** the own-post sheet (up to eight rows) opens in a window too short to show every row
- **THEN** the user can scroll to the last row

### Requirement: Mute and Unmute send the requested value

`MuteThread` / `UnmuteThread` SHALL pass the requested muted value to the cache rather than toggling the cached state; when the cache already holds that value with no pending override, nothing is written.

#### Scenario: Stale Mute on an already-muted thread

- **WHEN** "Mute thread" is dispatched for a thread the cache already shows muted
- **THEN** `muteThread` is not called and the call succeeds
