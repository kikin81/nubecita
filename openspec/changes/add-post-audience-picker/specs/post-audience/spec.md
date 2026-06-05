## ADDED Requirements

### Requirement: Composer audience chip
The composer SHALL display an audience chip in the options row of a new top-level post, summarizing the selected reply/quote audience, and SHALL hide it on replies.

#### Scenario: Default audience on a new post
- **WHEN** the user opens the composer for a new top-level post and has not changed the audience
- **THEN** the chip reads "Visible to all" with the globe icon

#### Scenario: Restricted audience on a new post
- **WHEN** the selected audience is anything other than the default (anyone can reply, quotes allowed)
- **THEN** the chip reads "Interaction limited"

#### Scenario: Chip hidden on replies
- **WHEN** the composer is opened to reply to an existing post (`replyToUri != null`)
- **THEN** the audience chip is not shown

### Requirement: Audience picker
Tapping the chip SHALL open an adaptive picker that lets the user choose who can reply and whether quotes are allowed, holding its own draft until confirmed. The picker SHALL present as a bottom sheet on Compact width and a centered popup on Medium/Expanded width, without dismissing the composer draft.

#### Scenario: Choose who can reply
- **WHEN** the picker is open
- **THEN** the user can select **Anyone**, **Nobody**, or a combination of **Your followers** / **People you follow** / **People you mention**, where selecting any combination checkbox clears Anyone/Nobody and vice versa

#### Scenario: Toggle quote posts
- **WHEN** the picker is open
- **THEN** the user can toggle "Allow quote posts", defaulting to on

#### Scenario: Confirm commits, cancel discards
- **WHEN** the user confirms the picker
- **THEN** the selected `PostAudience` is committed to the composer state; **WHEN** the user cancels or dismisses, the composer audience is unchanged

#### Scenario: Reset to default
- **WHEN** the user taps reset in the picker
- **THEN** the picker draft returns to the default (anyone can reply, quotes allowed)

### Requirement: Apply audience on post
When a post is created with a non-default audience, the app SHALL write the corresponding `app.bsky.feed.threadgate` and/or `app.bsky.feed.postgate` records at the post's rkey. These writes SHALL be best-effort and MUST NOT fail the already-created post.

#### Scenario: Everyone + quotes allowed writes no gate records
- **WHEN** the audience is the default (Everyone, quotes allowed)
- **THEN** no threadgate or postgate record is written

#### Scenario: Nobody can reply
- **WHEN** the reply audience is Nobody
- **THEN** a threadgate record is written with `allow` defined as an empty list, at the post's rkey

#### Scenario: Combination of reply rules
- **WHEN** the reply audience is a combination of followers/following/mentioned
- **THEN** a threadgate record is written with `allow` containing exactly the rules for the checked options, at the post's rkey

#### Scenario: Quotes disabled
- **WHEN** quote posts are not allowed
- **THEN** a postgate record is written with a disable-embedding rule, at the post's rkey

#### Scenario: Gate write failure does not fail the post
- **WHEN** the post is created successfully but the threadgate or postgate write fails
- **THEN** the post is reported as successful (its URI returned) and the user sees a non-blocking notice that audience settings could not be applied

### Requirement: Saved audience default
The app SHALL persist the user's chosen default audience to the synced `app.bsky.actor.defs#postInteractionSettingsPref` preference, preserving all foreign preference entries, and SHALL pre-fill the composer audience from it.

#### Scenario: Save as default
- **WHEN** the user checks "save these options for next time" and posts
- **THEN** the `postInteractionSettingsPref` entry is updated to reflect the selected audience while every other preference entry is preserved

#### Scenario: Pre-fill from default
- **WHEN** the composer opens for a new post
- **THEN** its audience is initialized from the synced default (anyone + quotes-on when the preference is absent)

#### Scenario: Optimistic update with revert on failure
- **WHEN** saving the default fails
- **THEN** the optimistic local value is reverted and the user is notified, with the next refresh reconciling to the server value

#### Scenario: Reset on sign-out
- **WHEN** the user signs out
- **THEN** the cached default is reset to the fail-safe default so a subsequent account never reads the previous account's value
