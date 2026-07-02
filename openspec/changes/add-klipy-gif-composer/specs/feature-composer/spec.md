## ADDED Requirements

### Requirement: Attach a KLIPY GIF or sticker
The composer SHALL let a user pick a KLIPY GIF or sticker and attach it to the post as a single previewed embed.

#### Scenario: Pick a GIF from the picker
- **WHEN** a user opens the KLIPY picker from the composer and selects an item
- **THEN** the picker closes and the composer shows an animated preview of the selected item with a remove affordance

#### Scenario: Remove the attached GIF
- **WHEN** a user removes the attached GIF from the composer
- **THEN** the post no longer carries the GIF embed and the picker can be reopened

### Requirement: One embed per post
The composer SHALL treat a KLIPY GIF as mutually exclusive with photo attachments and quote/link-card embeds, because a post carries exactly one embed.

#### Scenario: Photos block the GIF entry point
- **WHEN** the composer already has photo attachments
- **THEN** the GIF entry point is disabled

#### Scenario: An attached GIF blocks adding photos
- **WHEN** the composer already has a GIF attached
- **THEN** adding photos is blocked

### Requirement: Publish a GIF as a recognizable external embed
When a post with an attached KLIPY GIF is published, the composer SHALL write it as an `app.bsky.embed.external` whose URI is the KLIPY CDN media URL carrying pixel-dimension parameters, with an uploaded thumbnail, so that GIF-aware clients render it inline-animated.

#### Scenario: Posting a GIF produces the recognized embed shape
- **WHEN** a user posts with an attached KLIPY GIF
- **THEN** the created record's external embed URI has host `static.klipy.com`, a path beginning `/ii/`, and positive `hh`/`ww` parameters, and carries an uploaded thumbnail blob

### Requirement: KLIPY branding and reporting in the picker
The picker SHALL present the KLIPY-required branding and a content-report affordance.

#### Scenario: Branding is visible
- **WHEN** the picker is shown
- **THEN** the search field uses a "Search KLIPY" placeholder and a "Powered by KLIPY" mark is visible

#### Scenario: Report from preview
- **WHEN** a user opens an item's preview and reports it with a reason
- **THEN** the report is submitted for that item
