## ADDED Requirements

### Requirement: Watch text input
The watch SHALL take text through the system `RemoteInput` (`RemoteInputIntentHelper`), which offers voice, keyboard and emoji, and SHALL request no microphone permission of its own. Every input SHALL be shown on a review screen with the text, a grapheme counter and Send, before anything is queued. Cancelling the system input SHALL send nothing.

#### Scenario: Input cancelled
- **WHEN** the user opens the input and swipes it away
- **THEN** nothing is queued and the previous screen shows again

### Requirement: Short text posts from the watch
The watch SHALL let the user write a text-only post of up to 300 graphemes and send it as an urgent `create-post` action. It SHALL show the queued post as "Posting…", clear it on success, and show "Not posted — tap to retry" on any failure. A signed-out or switched account SHALL drop it.

#### Scenario: Post delivered in time
- **WHEN** the user posts "Finished my first 10k" from the watch and the phone receives it within an hour
- **THEN** the post is created on the account, and the watch clears "Posting…"

### Requirement: Phone runs watch posts like the composer
The phone SHALL run `create-post` through `PostingRepository.createPost`, with facets detected and `langs` picked as for the composer, and SHALL re-check the 300-grapheme limit. It SHALL NOT post one older than 1 hour; it SHALL answer `failed: expired` instead.

#### Scenario: Post expired
- **WHEN** a watch post reaches the phone more than one hour after it was written
- **THEN** it is not posted, and the watch shows "Not posted — tap to retry"

### Requirement: Posting gated on phone support
The watch SHALL offer posting only while a node advertises `nubecita_phone_post_actions`. A phone build that doesn't run `create-post` SHALL answer it `failed: unsupported`.

#### Scenario: Older phone
- **WHEN** the paired phone runs a build without watch posting
- **THEN** the watch shows no post entry point
