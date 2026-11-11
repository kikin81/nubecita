## ADDED Requirements

### Requirement: Thread posts translate under the feed's rules
Post detail SHALL apply the feed's translation rules to every ancestor, the focus post and every reply: a language on the auto list translates on settle with the shimmer, another foreign language shows the chip, and the reader's own languages show nothing. Translation SHALL be dispatched only when the thread's list settles, for visible posts only.

#### Scenario: Auto-translate languages translate on open
- **WHEN** the reader opens a thread whose visible replies include posts in a language on the auto list
- **THEN** those replies shimmer and swap to their translations once the list is at rest

#### Scenario: Other foreign languages show the chip
- **WHEN** a visible reply is in a foreign language not on the auto list
- **THEN** it shows the Translate chip and stays untranslated until tapped

#### Scenario: A chip tap makes the once-per-language offer
- **WHEN** the reader taps the chip on a reply in a language never offered before
- **THEN** the reply translates and the auto-translate offer appears, exactly as in the feed

#### Scenario: Nothing translates mid-scroll
- **WHEN** the reader flings through a long thread
- **THEN** no translation starts until the list comes to rest, and then only for posts still visible
