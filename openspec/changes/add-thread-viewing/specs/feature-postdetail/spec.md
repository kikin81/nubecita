# Spec Delta

## MODIFIED Requirements

### Requirement: `ThreadItem` is the sealed projection of a thread response

The system SHALL expose sealed `ThreadItem` with variants `Ancestor(post: PostUi)`, `Focus(post: PostUi)`, `Reply(post: PostUi, depth: Int)`, `MoreParents(postUri)`, `MoreReplies(postUri, count, depth)`, `OtherReplies`, `Blocked(uri, authorDid)`, and `NotFound(uri)`. The mapper MUST produce at most one `Focus`, zero or more `Ancestor`s, and zero or more `Reply`s, preserving blocked and missing posts, and MUST never produce two items with the same key.

#### Scenario: Mapper produces one Focus per thread response

- **WHEN** the mapper consumes a thread response whose anchor post is readable
- **THEN** the resulting `List<ThreadItem>` contains exactly one `Focus` variant

#### Scenario: Blocked sibling preserved as Blocked variant

- **WHEN** the mapper encounters a blocked post among the focus post's replies
- **THEN** the result includes a `Blocked` entry at the position the reply would have occupied

#### Scenario: Truncated reply chain gets a read-more row

- **WHEN** a post reports more replies than were returned and the next item is not its own reply
- **THEN** a `MoreReplies` item for that post follows it

#### Scenario: Truncated ancestors get a read-more row

- **WHEN** the top-most returned ancestor reports more parents
- **THEN** a `MoreParents` item for that ancestor precedes it

## ADDED Requirements

### Requirement: Post detail renders the server's thread order

Post detail SHALL read the thread through `app.bsky.unspecced.getPostThreadV2` with `above = true`, `below = 10`, `branchingFactor = 1`, `sort = top`, and SHALL render the returned items in the order received, without sorting them in the app.

#### Scenario: Opened at the first post of a five-post thread

- **WHEN** post 1 of a 5-post author thread is opened
- **THEN** posts 1, 2, 3, 4, 5 appear in that order, followed by other replies to post 1

#### Scenario: Opened in the middle of a thread

- **WHEN** post 3 of a 5-post author thread is opened
- **THEN** posts 1 and 2 appear above it, posts 4 and 5 directly below it, and other replies to post 3 after them

### Requirement: Post detail falls back to the v1 thread read

When the V2 read fails for a reason other than connectivity (an unknown method, or a response that cannot be decoded), post detail SHALL load the thread through `app.bsky.feed.getPostThread` instead and SHALL log the cause. A connectivity failure MUST NOT trigger the fallback.

#### Scenario: Undecodable V2 response

- **WHEN** the V2 response cannot be decoded
- **THEN** the thread opens from the v1 read, without thread positions, and the failure is logged

#### Scenario: Offline

- **WHEN** the V2 read fails because the device is offline
- **THEN** post detail shows its network error state and does not call the v1 read

### Requirement: The opened post is pinned to the top

Post detail SHALL show the opened post as the first visible row on every load path (cached thread, cached post only, cold load, refresh), with ancestors reachable by scrolling up, and without a visible jump when ancestors arrive after the opened post. It MUST NOT move the list once the reader has scrolled.

#### Scenario: Opening a post deep in a thread

- **WHEN** post 30 of a 40-post thread is opened
- **THEN** post 30 is at the top of the screen and post 29 is revealed by scrolling up

#### Scenario: Short tail

- **WHEN** the opened post has ancestors and too few rows below it to fill the screen
- **THEN** it is still pinned to the top

### Requirement: Thread lines follow the reply structure

Post detail SHALL draw a thread line from each ancestor into the row below it and into the opened post's avatar, SHALL draw no line leaving the opened post downward, and below the opened post SHALL join a reply to the next row when that row is its direct reply. No line SHALL be drawn into or out of a blocked or not-found placeholder.

#### Scenario: Opened at post 3 of 5

- **WHEN** post 3 of a 5-post thread is opened
- **THEN** posts 1, 2 and 3 are joined, posts 4 and 5 are joined, and no line connects post 3 to post 4

### Requirement: Partial threads can be continued

Post detail SHALL let the reader continue a thread the server returned in part: a "Read more replies" row opens the post it follows as its own post detail; a "Read earlier posts" row opens the top-most returned ancestor; a "Show more replies" row, shown when the server reports other replies, loads them and appends them in place without duplicating a reply already shown.

#### Scenario: Thread longer than one response

- **WHEN** a thread has more posts than the response returned
- **THEN** a "Read more replies" row follows the last returned post of the chain, and tapping it opens that post with the rest of the thread below it

#### Scenario: Hidden replies

- **WHEN** the server reports other replies and the reader taps "Show more replies"
- **THEN** those replies are appended in place of the row
