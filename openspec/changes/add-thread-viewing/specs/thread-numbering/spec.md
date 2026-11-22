# Spec Delta

## Purpose

Defines how a post's position within its author's thread ("post 3 of 5") is sourced,
validated and displayed, so every surface shows the same number for the same post.

## ADDED Requirements

### Requirement: Thread positions come only from the server

The system SHALL take a post's thread index and count only from the fields the server
provides on feed entries, quoted-post record views and thread V2 items. The system
MUST NOT compute a count or an index from the posts it happens to have loaded.

#### Scenario: Feed entry carries a position

- **WHEN** a feed entry arrives with thread index 3 and thread count 5
- **THEN** that entry's post shows the position 3 of 5

#### Scenario: Response without numbering

- **WHEN** a post arrives from a response that carries no thread numbering (search,
  notifications, bookmarks)
- **THEN** the post shows no position

### Requirement: A position is shown only when it is valid

The system SHALL treat a position as valid only when the index is at least 1, the
count is at least 1 and the index does not exceed the count. An invalid or partial
pair MUST result in no position, never an error.

#### Scenario: Index above count

- **WHEN** a post arrives with index 6 and count 5
- **THEN** no position is shown for it

#### Scenario: One field missing

- **WHEN** a post arrives with a count but no index
- **THEN** no position is shown for it

### Requirement: Reply context in a feed cluster is numbered from its leaf

When a feed entry is rendered as a reply cluster and its own post has a valid
position, the system SHALL show the cluster's root as position 1 and its parent as
the leaf's index minus one, with the same count. When the same context post also
arrives as its own numbered entry in the page, the server's number for it SHALL win.

#### Scenario: Cluster around post 3 of 5

- **WHEN** a reply cluster's leaf is 3 of 5
- **THEN** its root shows 1 of 5 and its parent shows 2 of 5

#### Scenario: Un-numbered leaf

- **WHEN** a reply cluster's leaf has no position
- **THEN** neither its root nor its parent shows one

### Requirement: The position is displayed as a pill after the post text

The system SHALL display a valid position as a non-interactive pill reading
`index/count`, placed inline at the end of the post's text, on feed cards, quoted-post
embeds and thread posts in post detail. A post with no text SHALL show the pill on its
own line above any media or quote embed. The pill SHALL be announced to assistive
technology as "Post {index} of {count}" in the app's language.

#### Scenario: Post with text

- **WHEN** a post with text and position 1 of 5 is rendered in a feed
- **THEN** `1/5` appears at the end of the last line of its text

#### Scenario: Media-only post

- **WHEN** a post with an image, no text and position 2 of 5 is rendered
- **THEN** `2/5` appears on its own line above the image

#### Scenario: Post without a position

- **WHEN** a post has no position
- **THEN** it renders exactly as it did before this capability existed

### Requirement: Positions survive a cold start

The system SHALL persist positions with the feed cache and the thread cache so that a
cached feed or thread shows the same pills before the network responds. Cache rows
written before positions were stored SHALL render without a pill.

#### Scenario: Cached feed after relaunch

- **WHEN** the app is relaunched and renders the cached feed before refreshing
- **THEN** posts that had a position when cached show it
