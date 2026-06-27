## ADDED Requirements

### Requirement: Auto-detected external link preview card
When the user types or pastes a URL into the post text that is an `http(s)` URL and is NOT a Bluesky quote-link, the composer SHALL automatically fetch a link preview and display a link card below the text. The preview SHALL be fetched from the CardyB service (`cardyb.bsky.app/v1/extract`), mapping its `title`, `description`, and `image` into the card. Only the first eligible URL SHALL produce a card.

#### Scenario: Pasting a URL shows a preview card
- **WHEN** the user pastes `https://example.com/article` into the composer text
- **THEN** the composer fetches a preview and renders a link card with the title, description, domain, and thumbnail below the text field

#### Scenario: A Bluesky quote-link is not treated as an external card
- **WHEN** the pasted URL is a Bluesky post link (e.g. `https://bsky.app/profile/.../post/...`)
- **THEN** it is handled by quote-link detection and does NOT produce an external link card

#### Scenario: Only the first eligible URL produces a card
- **WHEN** the text contains two eligible URLs
- **THEN** only the first detected URL produces a card

#### Scenario: A page with a title but no description still shows a card
- **WHEN** the fetched preview has a non-empty title and a blank description
- **THEN** a card is shown (title + thumbnail, no description row) and the embed carries `description = ""`

#### Scenario: A shortened link resolves to its final destination
- **WHEN** the user pastes a shortened URL (e.g. `bit.ly/…`) and CardyB returns a redirect-resolved `url`
- **THEN** the `external.uri` of the posted embed is the resolved destination URL, not the shortened string the user typed

### Requirement: Link card is dismissable and does not re-pop
The link card SHALL provide a dismiss affordance. Dismissing it SHALL remove the card and memoize the URL so that the same URL still present in the text does NOT immediately produce a new card.

#### Scenario: Dismissing a card removes it
- **WHEN** the user taps the card's dismiss control
- **THEN** the card is removed and the same URL remaining in the text does not re-create a card

### Requirement: Link card mutual-exclusion with images, coexistence with quote
A link card SHALL be mutually exclusive with image/gallery attachments: while images are attached the composer SHALL NOT fetch or show a card, and adding images SHALL clear any existing card. A link card MAY coexist with a quoted post.

#### Scenario: Images suppress the card
- **WHEN** the composer has image attachments and the user pastes a URL
- **THEN** no link card is fetched or shown

#### Scenario: Adding images clears an existing card
- **WHEN** a link card is shown and the user adds an image attachment
- **THEN** the link card is cleared

#### Scenario: Removing images restores the card
- **WHEN** a card was auto-cleared by adding images, and the user then removes all images while the URL is still in the text
- **THEN** the card is re-detected and restored automatically (the image-induced clear did not memoize the URL)

#### Scenario: Card coexists with a quoted post
- **WHEN** the composer has a quoted post and a link card
- **THEN** both are retained

### Requirement: External embed is built on submit
When a post is submitted with a link card and no image attachments, the composer SHALL build an `app.bsky.embed.external` record from the card's uri/title/description. When a quoted post is also present, the embed SHALL be `app.bsky.embed.recordWithMedia` with the external as its media. When image attachments are present, the link card SHALL be dropped (images take the media slot).

#### Scenario: Card-only post emits an external embed
- **WHEN** a post with a link card and no images is submitted
- **THEN** the wire embed is `app.bsky.embed.external`

#### Scenario: Card plus quote emits recordWithMedia
- **WHEN** a post with a link card and a quoted post is submitted
- **THEN** the wire embed is `app.bsky.embed.recordWithMedia` with the external as media

#### Scenario: Images win over a card
- **WHEN** a post somehow carries both images and a card at submit
- **THEN** the images embed is emitted and the external is dropped

### Requirement: Thumbnail upload is best-effort
The link card's thumbnail SHALL be uploaded as the external embed's `thumb` blob at post time on a best-effort basis. If the thumbnail cannot be fetched or uploaded, the post SHALL still be created with the external embed minus the thumbnail. A thumbnail failure SHALL NOT block or fail the post.

#### Scenario: Thumbnail failure still posts the card
- **WHEN** the thumbnail download or upload fails at submit
- **THEN** the post is created with an external embed carrying uri/title/description and no thumb

### Requirement: Preview-fetch failure is silent
When the preview fetch returns no usable data (CardyB error, blank fields, network failure, or timeout), the composer SHALL NOT show a card and SHALL NOT surface an error to the user, and SHALL memoize the URL so it does not retry-loop.

#### Scenario: Failed fetch shows no card and no error
- **WHEN** the preview fetch fails for a pasted URL
- **THEN** no card is shown, no error message is surfaced, and the URL is not re-fetched while it remains in the text
