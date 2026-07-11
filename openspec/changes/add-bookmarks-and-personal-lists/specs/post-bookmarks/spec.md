## ADDED Requirements

### Requirement: Post model carries bookmark state
The post UI model SHALL expose whether the signed-in viewer has bookmarked a post and the post's bookmark count, derived from the AT Proto `postView` viewer state.

#### Scenario: postView includes bookmark viewer state
- **WHEN** a `postView` with `viewer.bookmarked = true` and `bookmarkCount = 5` is mapped to the post model
- **THEN** the resulting model has `isBookmarked = true` and `bookmarkCount = 5`

#### Scenario: postView omits bookmark viewer state
- **WHEN** a `postView` has no `viewer.bookmarked` field
- **THEN** the model has `isBookmarked = false` and a non-negative `bookmarkCount` (0 when absent)

### Requirement: Bookmark a post
The system SHALL let the signed-in user bookmark or un-bookmark any post, calling `createBookmark` / `deleteBookmark` with the post's strong reference.

#### Scenario: Bookmark an un-bookmarked post
- **WHEN** the user activates bookmark on a post with `isBookmarked = false`
- **THEN** the system calls `createBookmark` for that post's uri/cid and the post becomes `isBookmarked = true`

#### Scenario: Remove a bookmark
- **WHEN** the user activates bookmark on a post with `isBookmarked = true`
- **THEN** the system calls `deleteBookmark` for that post and the post becomes `isBookmarked = false`

### Requirement: Bookmark toggle is optimistic and consistent across surfaces
The bookmark toggle SHALL update the UI optimistically, reflect the new state on every rendered copy of the post via the shared interactions cache, guard against concurrent toggles of the same post, and roll back on failure.

#### Scenario: Optimistic update then success
- **WHEN** the user bookmarks a post
- **THEN** the post shows as bookmarked immediately (before the network call completes) on every surface currently showing that post

#### Scenario: Rollback on failure
- **WHEN** `createBookmark` fails after the optimistic update
- **THEN** the post's bookmark state reverts to its prior value and an error is surfaced

#### Scenario: Concurrent toggle guard
- **WHEN** a bookmark request for a post is already in flight
- **THEN** a second toggle for the same post does not issue an overlapping request

### Requirement: Bookmark action is exposed in the post action row
Each post's action row SHALL render a bookmark control positioned between the like and share controls, shown filled when the post is bookmarked and outlined otherwise, with an accessible action label.

#### Scenario: Row order
- **WHEN** a post card renders its action row
- **THEN** the controls appear in the order reply, repost, like, bookmark, share, overflow

#### Scenario: Filled state reflects bookmark
- **WHEN** a post is bookmarked
- **THEN** the bookmark control renders its filled glyph and exposes the "Remove bookmark" action label; otherwise it renders outlined with the "Bookmark" action label

### Requirement: Bookmark interactions are instrumented
The system SHALL log a post-interaction analytics event distinguishing bookmark from un-bookmark so that action-row usage can be reviewed and the control's placement decided from data.

#### Scenario: Bookmark logs an interaction event
- **WHEN** the user bookmarks a post
- **THEN** an `interact_post` event is logged with `action_type = bookmark` (and `unbookmark` when removing)
