# klipy-media Specification

## Purpose
Fetching KLIPY GIFs and stickers (search, trending, categories, recents) and the
mandated view/share/report engagement tracking, behind an SDK-agnostic `:core:klipy`
repository that returns `:data:models` types. It underpins the composer's GIF/sticker
picker (see `feature-composer`) while keeping the KLIPY API key, Ktor client, and wire
DTOs internal to the module — no KLIPY SDK type crosses the boundary.

## Requirements
### Requirement: Search KLIPY media
The system SHALL fetch KLIPY media (GIFs and stickers) for a text query through an SDK-agnostic `:core:klipy` repository that returns `:data:models` types, with paged results.

#### Scenario: Query returns a page of results
- **WHEN** a caller searches a media type with a non-empty query and a page number
- **THEN** the repository returns that page of media items plus whether a next page exists

#### Scenario: Blank query falls back to trending
- **WHEN** the search query is blank
- **THEN** the repository returns the trending feed for that media type instead of an empty result

### Requirement: Browse categories, trending, and recents
The system SHALL expose trending, category, and per-user recents browsing for each media type.

#### Scenario: Recents and Trending lead the category list
- **WHEN** the categories for a media type are requested
- **THEN** the result presents Recents and Trending ahead of the server-provided categories

### Requirement: Interaction tracking
The system SHALL report a view when a user previews an item and a share when a user selects an item to send, per KLIPY's engagement contract.

#### Scenario: Previewing an item reports a view
- **WHEN** a user previews a KLIPY item
- **THEN** the repository reports a view for that item's slug

#### Scenario: Selecting an item reports a share
- **WHEN** a user selects a KLIPY item to attach to a post
- **THEN** the repository reports a share for that item's slug, and the item appears in the user's Recents

#### Scenario: Tracking survives the caller's lifecycle
- **WHEN** a tracking call is in flight and the calling screen is torn down
- **THEN** the tracking call completes on an application-scoped context rather than being cancelled

### Requirement: Report and hide-from-recents
The system SHALL let a user report an item with a predefined reason and remove an item from their Recents.

#### Scenario: Reporting an item
- **WHEN** a user reports an item with a reason
- **THEN** the repository submits the report for that item's slug

#### Scenario: Removing an item from Recents
- **WHEN** a user removes an item from their Recents
- **THEN** the repository hides the item from the user's Recents list

### Requirement: Stable customer identifier
The system SHALL send a stable per-user identifier on fetch and tracking calls, persisted across launches.

#### Scenario: Identifier is stable across launches
- **WHEN** KLIPY calls are made in two separate app launches
- **THEN** the same customer identifier is sent both times

### Requirement: API key confidentiality
Because the KLIPY API key is embedded in the request URL path, the system SHALL NOT emit the full request URL to logs.

#### Scenario: Network logging does not leak the key
- **WHEN** KLIPY requests are logged
- **THEN** the URL path segment carrying the key is sanitized or omitted from the log output
