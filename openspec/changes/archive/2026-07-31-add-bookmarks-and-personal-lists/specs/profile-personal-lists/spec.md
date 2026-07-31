## ADDED Requirements

### Requirement: Your Likes tab on the signed-in user's profile
The signed-in user's own profile SHALL offer a Likes tab that lists the posts they have liked, paginated via `getActorLikes`, alongside the existing Posts / Replies / Media tabs.

#### Scenario: Own profile shows a Likes tab
- **WHEN** the signed-in user views their own profile
- **THEN** a Likes tab is available and, when selected, shows their liked posts newest-first with pagination

#### Scenario: Likes are private to the owner
- **WHEN** the user views another account's profile
- **THEN** no Likes tab is shown

#### Scenario: Empty likes
- **WHEN** the signed-in user has no likes
- **THEN** the Likes tab shows an empty state rather than an error

### Requirement: Bookmarks list on the signed-in user's profile
The signed-in user SHALL be able to open a full-screen Bookmarks list of their bookmarked posts, paginated via `getBookmarks`, reached from an affordance in their own profile's top bar.

#### Scenario: Entry point on own profile
- **WHEN** the signed-in user views their own profile
- **THEN** a Bookmarks affordance is present in the top bar that opens the Bookmarks list

#### Scenario: Bookmarks list content
- **WHEN** the Bookmarks list opens
- **THEN** it shows the user's bookmarked posts (newest-first) with pagination, each rendered as a normal post card with working interactions

#### Scenario: Bookmarks are private to the owner
- **WHEN** viewing another account's profile
- **THEN** no Bookmarks entry point is shown

#### Scenario: Empty bookmarks
- **WHEN** the signed-in user has no bookmarks
- **THEN** the Bookmarks list shows an empty state rather than an error
