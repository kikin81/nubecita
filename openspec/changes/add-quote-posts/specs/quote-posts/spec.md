## ADDED Requirements

### Requirement: Author a quote post
The unified composer SHALL allow authoring a post that embeds another post as a quote (`app.bsky.embed.record`), using a single optional quote input alongside the existing reply input. The composer MUST NOT be duplicated for this purpose.

#### Scenario: Quote a post from the repost menu
- **WHEN** the user selects "Quote post" for a post
- **THEN** the composer opens with that post attached as a quote card
- **AND** submitting creates a `app.bsky.feed.post` whose `embed` is `app.bsky.embed.record` referencing the quoted post's `uri` + `cid`

#### Scenario: Empty-text quote post is submittable
- **WHEN** a quote is attached and loaded and the text is blank and there are no image attachments
- **THEN** the composer SHALL treat the loaded quote as content and allow submission
- **AND** the created post has empty `text` and a `record` embed

### Requirement: Quote combined with images
When the composer has both a loaded quote and one or more image attachments, the created post SHALL embed `app.bsky.embed.recordWithMedia` (quote as `record`, images as `media`).

#### Scenario: Quote plus images
- **WHEN** the user attaches a quote and adds at least one image, then submits
- **THEN** the post embed is `recordWithMedia` carrying the quote ref and the uploaded image blobs

### Requirement: Reply and quote coexist
The composer SHALL allow a single post to be a reply and carry a quote simultaneously, modeling reply target and quote target as independent optional inputs. Neither input SHALL preclude the other.

#### Scenario: Quote while replying
- **WHEN** the user is replying to post A and attaches a quote of post B
- **THEN** the created post has a `reply` ref to A and an `embed` (`record` or `recordWithMedia`) referencing B
- **AND** the composer shows the reply context on top and the quote card at the bottom

### Requirement: Paste-a-link quote detection
While composing, when the user enters a Bluesky post URL (`bsky.app/profile/{handle-or-did}/post/{rkey}`) or an `at://` post URI, the composer SHALL detect it, resolve the post via `getPost`, and attach it as a quote. The raw URL SHALL be removed from the text only upon successful resolution; on failure the URL SHALL be left intact.

#### Scenario: Successful paste resolution
- **WHEN** the user pastes a valid Bluesky post URL and resolution succeeds
- **THEN** the quote card is shown in the loaded state
- **AND** the pasted URL is removed from the text field

#### Scenario: Failed paste resolution keeps the URL
- **WHEN** the user pastes a post URL and resolution fails (network error, deleted post, or quoting disabled)
- **THEN** the quote enters a failed state and the pasted URL remains in the text field unchanged

#### Scenario: One quote maximum
- **WHEN** a quote is already attached and the user pastes a second post URL
- **THEN** the second URL is ignored for quote attachment

### Requirement: Dismiss an attached quote
The composer SHALL let the user remove an attached quote, clearing the quote input without affecting the reply context or text.

#### Scenario: Remove the quote card
- **WHEN** the user taps the quote card's dismiss control
- **THEN** the quote input is cleared and the quote card is removed
- **AND** any reply context and entered text are preserved

### Requirement: Repost button affordance
The repost affordance on a post SHALL distinguish two gestures: a single tap opens a menu, and a long-press performs the repost toggle instantly. The repost icon SHALL reflect whether the viewer has reposted the post.

#### Scenario: Single tap opens the menu (not yet reposted)
- **WHEN** the viewer single-taps the repost affordance on a post they have not reposted
- **THEN** a menu appears offering "Repost" and "Quote post"

#### Scenario: Single tap opens the menu (already reposted)
- **WHEN** the viewer single-taps the repost affordance on a post they have already reposted
- **THEN** a menu appears offering "Undo repost" and "Quote post"

#### Scenario: Long-press toggles repost instantly
- **WHEN** the viewer long-presses the repost affordance
- **THEN** the post is reposted if not already reposted, or the repost is undone if already reposted, with no menu shown

### Requirement: Honor others' quote gates
The system SHALL derive `canViewerQuote` from the wire `viewer.embeddingDisabled` (absent meaning quoting is allowed) and SHALL prevent quoting a post whose author has disabled quoting.

#### Scenario: Quote option hidden when quoting disabled
- **WHEN** a post has quoting disabled for the viewer
- **THEN** the "Quote post" item is not shown in that post's repost menu

#### Scenario: Paste-quoting a gated post is rejected
- **WHEN** the user pastes a URL that resolves to a post with quoting disabled
- **THEN** the quote is not attached
- **AND** the composer surfaces an error indicating quotes are turned off for that post

### Requirement: Single embed-construction seam
Post embed construction SHALL be centralized in `:core:posting` behind a single intent-to-embed resolver, so the composer declares an embed intent (image attachments and/or a quote ref) without referencing embed-union variants, and new embed types can be added without a second composer or write-path changes elsewhere.

#### Scenario: Resolver selects the embed variant
- **WHEN** a post is created with no attachments and no quote
- **THEN** the resolver emits no embed
- **WHEN** a post is created with images only
- **THEN** the resolver emits an `images` embed
- **WHEN** a post is created with a quote only
- **THEN** the resolver emits a `record` embed
- **WHEN** a post is created with images and a quote
- **THEN** the resolver emits a `recordWithMedia` embed
