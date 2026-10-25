## ADDED Requirements

### Requirement: Published posts carry hashtag facets

When a post is published, the composer's facet extraction SHALL emit an `app.bsky.richtext.facet` with a single `#tag` feature for every hashtag in the text. The facet's `tag` value MUST omit the leading `#`/`＃` and preserve case, and its byte range MUST span the `#` through the end of the tag, in UTF-8 byte offsets.

#### Scenario: A simple hashtag becomes a tag facet
- **WHEN** a post with text `I love #Kotlin today` is published
- **THEN** the record contains a facet whose feature is `#tag` with `tag == "Kotlin"`, and whose byte range covers `#Kotlin`

#### Scenario: Multibyte text gets correct byte offsets
- **WHEN** a post with text `café #día` is published
- **THEN** the tag facet has `tag == "día"`, and `byteStart`/`byteEnd` are the UTF-8 byte offsets of `#día` in the UTF-8 encoding of the text

### Requirement: Hashtag detection follows the official client's rules

A hashtag SHALL be a `#` (U+0023) or `＃` (U+FF03) at the start of the text or after whitespace, followed by non-whitespace characters, of which at least one MUST NOT be a digit, whitespace or punctuation. Trailing punctuation (`\p{P}+$`) MUST be stripped. A tag that is empty, or longer than 64 graphemes after stripping, MUST be dropped, and `#` followed by U+FE0F (`#️⃣`) MUST NOT start a tag. These rules mirror the official `TAG_REGEX` and `TRAILING_PUNCTUATION_REGEX`.

#### Scenario: Trailing punctuation is not part of the tag
- **WHEN** a post with text `Shipping on #Android!` is published
- **THEN** the tag facet has `tag == "Android"`, and its byte range ends before `!`

#### Scenario: Digit-only and keycap forms are not tags
- **WHEN** a post with text `issue #123 and #️⃣ here` is published
- **THEN** the record contains no tag facets

#### Scenario: A hashtag must start a word
- **WHEN** a post with text `see (#notatag) and foo#bar` is published
- **THEN** the record contains no tag facets

#### Scenario: Over-long tags are dropped
- **WHEN** a post contains `#` followed by 65 letters
- **THEN** no tag facet is emitted for it

### Requirement: Published posts carry cashtag facets

The composer's facet extraction SHALL emit a `#tag` facet for every cashtag:

- A cashtag is `$` at the start of the text, or after whitespace or `(`, followed by a letter and then up to 4 more letters or digits.
- It MUST be followed by whitespace, end of text, or one of `. , ; : ! ? ) " ' ’`.
- The facet's `tag` value MUST be `$` followed by the ticker uppercased.
- The byte range MUST cover the `$` and the ticker as typed.

#### Scenario: A cashtag becomes an uppercased tag facet
- **WHEN** a post with text `Watching $btc closely` is published
- **THEN** the record contains a tag facet with `tag == "$BTC"`, whose byte range covers `$btc`

#### Scenario: Prices and long tokens are not cashtags
- **WHEN** a post with text `costs $5 or $TOOLONG` is published
- **THEN** the record contains no tag facets

### Requirement: Tag facets coexist with mention and link facets

Tag facets SHALL be emitted alongside mention and link facets. All facets MUST be sorted by `byteStart`. A tag MUST NOT be emitted when its range overlaps a link facet's range.

#### Scenario: Mixed facets are ordered
- **WHEN** a post with text `@alice.bsky.social check #news https://example.com` is published, and the mention resolves
- **THEN** the record contains three facets (mention, tag, link) in ascending `byteStart` order

#### Scenario: A fragment inside a URL is not a tag
- **WHEN** a post with text `read https://example.com/page #intro` is published, where `#intro` is separated by a space
- **THEN** `#intro` is a tag facet, and no tag facet is created from any part of the URL

### Requirement: Tags in rendered posts are tappable

Wherever post text is rendered with facets (post cards, quoted posts, post detail), a `#tag` facet SHALL be rendered as a clickable span styled like a link. Tapping it SHALL invoke the host's facet-tap callback with `FacetTarget.Tag`:

- `tag` is the facet's `tag` value with any stray leading `#` removed. Some clients publish the `#` inside the value.
- `authorHandle` is the handle of the account whose text contains the tag, or null when unknown.

#### Scenario: Tapping a tag in a feed post opens the Hashtag screen
- **WHEN** the user taps `#Kotlin` in a post by `@alice.bsky.social` in the Following feed
- **THEN** the app navigates to `Hashtag(tag = "Kotlin", author = "alice.bsky.social")`

#### Scenario: A facet value with a leading hash is normalized
- **WHEN** a post's tag facet has `tag == "#Kotlin"` and the user taps it
- **THEN** the navigation target is `Hashtag(tag = "Kotlin", …)`

#### Scenario: Every post-text host handles tag taps
- **WHEN** the user taps a tag in post detail, profile posts, bookmarks, notifications, search results or a chat message
- **THEN** the Hashtag screen opens. No host ignores the tap.

### Requirement: Hashtag screen shows tagged posts with Top and Latest tabs

The app SHALL provide a `Hashtag(tag: String, author: String? = null)` destination listing posts tagged with `tag`, fetched from `app.bsky.feed.searchPostsV2` with the structured `hashtags = [tag]` filter, not a free-text query. It has two tabs: **Top** (`sort = top`, selected first) and **Latest** (`sort = recent`).

#### Scenario: Opening a hashtag shows Top results
- **WHEN** the user opens `Hashtag(tag = "Kotlin")`
- **THEN** the screen title reads `#kotlin`, the Top tab is selected, and it shows posts returned by `searchPostsV2(hashtags = ["Kotlin"], sort = "top")`

#### Scenario: Switching to Latest
- **WHEN** the user selects the Latest tab
- **THEN** the screen requests `sort = "recent"` for the same tag, and shows those results independently of the Top tab

### Requirement: Hashtag tabs load lazily, page, and are moderated

Each Hashtag tab SHALL load on first selection, page with the response `cursor`, and support pull-to-refresh. Results MUST pass the same moderation filtering as post-search results, and tapping a post MUST behave like a post tap in search results (list-detail on wide windows).

#### Scenario: Paging loads more results
- **WHEN** the user scrolls near the end of a tab whose last response had a non-null cursor
- **THEN** the next page is requested with that cursor and appended without duplicate posts

#### Scenario: Empty and error states
- **WHEN** the search returns zero posts, or fails
- **THEN** the tab shows a localized empty state, or an error state with retry, and other tabs are unaffected

### Requirement: Hashtag screen offers an author filter when opened from a post

When `author` is non-null, the Hashtag screen SHALL show a "From @handle" filter control:

- While the filter is on, every request adds `authors = [author]`.
- The filter MUST default to off. Tapping a tag shows everyone's posts first, as in the official client; the filter is the in-screen equivalent of the official long-press "See #tag posts by user".
- Toggling the filter MUST reload both tabs from the first page.

#### Scenario: Narrowing to the author's posts
- **WHEN** the screen was opened with `author = "alice.bsky.social"` and the user turns on the "From @alice.bsky.social" filter
- **THEN** both tabs reload with `authors = ["alice.bsky.social"]` and show only that author's tagged posts

#### Scenario: No filter without an author
- **WHEN** the screen was opened with `author = null`
- **THEN** no author filter control is shown

### Requirement: Hashtag screen can be shared

The Hashtag screen SHALL offer a share action that opens the system share sheet with `https://bsky.app/hashtag/<url-encoded tag>`. When the author filter is on, the URL SHALL also include `?author=<author>`.

#### Scenario: Sharing a hashtag
- **WHEN** the user taps share on `Hashtag(tag = "Kotlin")` with no filter on
- **THEN** the share sheet receives `https://bsky.app/hashtag/Kotlin`
