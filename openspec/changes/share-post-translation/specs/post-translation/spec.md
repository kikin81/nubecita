## ADDED Requirements

### Requirement: One translation state per post, shared by every surface
The app SHALL hold a single translation state per post, shared by every surface that displays it. A post translated, offered, or reverted on one surface SHALL appear the same way on every other surface showing that post, without a second engine call.

#### Scenario: Feed translation carries into post detail
- **WHEN** the feed has translated a post and the reader opens it
- **THEN** post detail shows the translation immediately, with no shimmer and no engine call

#### Scenario: Post detail translation carries back to the feed
- **WHEN** the reader translates a reply in post detail and returns to a feed that shows the same post
- **THEN** the feed shows that post translated, with no engine call

### Requirement: Completed translations persist in the cache
Every completed translation SHALL be written to the `post_translation` cache keyed by post URI and target language. Before calling the engine, the cache SHALL be consulted, and a hit SHALL be shown without an in-progress state.

#### Scenario: A translation survives a restart
- **WHEN** a post was translated, the app process ends, and the post is shown again
- **THEN** the cached translation is shown and no engine call is made

#### Scenario: A changed target language is not served stale
- **WHEN** the device language changes while the app is running, after a post was translated
- **THEN** the old translation is not shown, from memory or the cache, and the post is resolved again for the new target

#### Scenario: A cache hit is not counted as a translation
- **WHEN** a settle shows posts from the cache
- **THEN** the batch event counts only translations the engine performed in that settle

### Requirement: A translation follows its post, not the auto list
A post with a cached or in-memory translation into the current target SHALL be shown translated on every surface, whether or not its language is on the reader's auto-translate list.

#### Scenario: A hand-translated post stays translated
- **WHEN** the reader taps Translate on a post in a language not on the auto list, then sees that post on another surface or after a restart
- **THEN** it is shown translated, while other posts in that language still show the chip

### Requirement: Preference changes reach posts already on screen
When the auto list, the master toggle, the reader's languages or the target language change, or a waited-for pack arrives, posts those changes affect SHALL be resolved again, and a surface on screen SHALL re-settle without a scroll. A post in one of the reader's languages SHALL show nothing, even when a translation of it is cached.

#### Scenario: Accepting the offer translates the rest of the thread
- **WHEN** the reader accepts the offer for a language in post detail
- **THEN** the other visible posts in that language translate without another tap

#### Scenario: A never-translate language stops translating at once
- **WHEN** the reader adds a language to never-translate after posts in it were translated
- **THEN** those posts show their original with no chip, on every surface and after a restart

#### Scenario: A post waiting for Wi-Fi translates when the pack arrives
- **WHEN** an auto-list post showed "waiting for Wi-Fi" and its pack finishes downloading on Wi-Fi
- **THEN** the post translates without the reader leaving the screen or restarting the app

#### Scenario: A failed translation gets a fresh try on the next visit
- **WHEN** a post's translation failed and the reader later opens a surface showing it
- **THEN** that surface tries it once more

#### Scenario: A language added in Settings takes effect without restarting
- **WHEN** the reader adds a language in Settings and returns to the feed
- **THEN** visible posts in that language translate, instead of keeping the chip they had before

### Requirement: Translation work outlives the screen that started it
A translation started on one surface SHALL complete and be stored even if that surface leaves composition first, so another surface showing the post receives the result.

#### Scenario: Opening a post mid-translation
- **WHEN** a feed post is still translating and the reader opens it
- **THEN** post detail shows the shimmer and then the translation, without starting a second translation

### Requirement: Show original is shared for the session
The reader's choice to show a post's original SHALL apply on every surface for the rest of the process, and SHALL NOT persist across process death.

#### Scenario: Reverting in post detail reverts in the feed
- **WHEN** the reader taps "Show original" in post detail and returns to the feed
- **THEN** the feed shows that post's original with the option to show the translation

### Requirement: Any post-list surface adopts translation through one helper
A surface SHALL gain translation by calling the shared Compose helper with its list state and visible posts and passing the result to `PostCard`. No feature module SHALL contain translation decision logic, its own translation state, or its own copy of the auto-translate offer.

#### Scenario: Post detail adopts without copying
- **WHEN** post detail gains translation
- **THEN** it adds no translation state class, coordinator or offer dialog of its own

### Requirement: Memory held by the shared state is bounded
In-memory translation state SHALL be bounded, evicting the least recently used posts first. An evicted completed translation SHALL be recovered from the cache rather than re-translated.

#### Scenario: A long session does not grow without limit
- **WHEN** a session scrolls past thousands of foreign-language posts
- **THEN** in-memory entries stay within the bound, and a revisited evicted post is served from the cache
