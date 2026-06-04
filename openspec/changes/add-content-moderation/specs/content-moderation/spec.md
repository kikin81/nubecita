## ADDED Requirements

### Requirement: Content-label moderation decision
The system SHALL resolve a per-media moderation decision from a piece of content's labels and the viewer's content-filter preferences, covering the four global categories (adult content, sexually suggestive, graphic media, non-sexual nudity) and producing one of: show, warn (overridable cover), or filter-from-feed.

#### Scenario: Label set to Warn
- **WHEN** content carries a label whose category visibility is "warn"
- **THEN** the decision is a warn cover that the viewer can override ("show anyway")

#### Scenario: Label set to Hide
- **WHEN** content carries a label whose category visibility is "hide"
- **THEN** the decision is filter-from-feed (the content is removed from feed/search/profile lists)

#### Scenario: Label set to Show
- **WHEN** content carries a label whose category visibility is "show"
- **THEN** the decision is show (the media renders normally)

#### Scenario: Strongest decision wins
- **WHEN** content carries multiple labels with different visibilities
- **THEN** the strictest applies (any hide → filter; else any warn → warn; else show)

#### Scenario: The viewer's own content is never moderated
- **WHEN** the labeled content's author is the viewer
- **THEN** the decision is show

### Requirement: Adult-content master gate
The system SHALL gate the adult categories (adult content, sexually suggestive, graphic media) behind an "enable adult content" preference that defaults to off, and non-sexual nudity SHALL NOT be gated by it.

#### Scenario: Adult content disabled forces hide with no override
- **WHEN** the master gate is off and content carries an adult-category label
- **THEN** the decision is filter-from-feed, and where the media is shown directly it is covered with no "show anyway" override — regardless of that category's individual visibility setting

#### Scenario: Non-sexual nudity ignores the master gate
- **WHEN** the master gate is off and content carries the non-sexual-nudity label
- **THEN** the decision follows that category's own visibility setting (default show), unaffected by the gate

### Requirement: Content-filter preferences fetch, cache, and sync
The system SHALL read the viewer's content-filter preferences from their account, expose them as observable cached state, and persist changes back to the account without losing other preference kinds.

#### Scenario: Defaults when no preference is stored
- **WHEN** the account has no content-label or adult-content preference entries
- **THEN** the cached preferences use the platform defaults (adult disabled; porn=hide, sexual=warn, graphic-media=warn, nudity=show)

#### Scenario: Saving a change preserves other preferences
- **WHEN** the viewer changes a category visibility or the master gate
- **THEN** only the corresponding preference entries are updated and all other (unmodelled) preference kinds are written back unchanged

#### Scenario: Cached state updates after a change
- **WHEN** a preference change is saved
- **THEN** the cached preferences state reflects the new value without requiring a re-fetch
