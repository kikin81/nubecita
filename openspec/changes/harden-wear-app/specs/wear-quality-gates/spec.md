# Spec Delta

## Purpose

Defines how changes to the watch app are verified before they merge: which states can be reached without a phone, which journeys and screenshots guard them, and which builds and dependency updates CI enforces.

## ADDED Requirements

### Requirement: Bench scenarios reach every non-happy state
The bench build of the watch app SHALL accept a scenario at launch that puts it into a named state without a phone: signed out, phone app missing, paired with an iPhone, update required, no feeds, messages not synced, actions unavailable, posting unavailable, a post that fails, a message that fails, and slow actions. With no scenario it SHALL behave as the signed-in happy path. The production build SHALL NOT contain the scenario mechanism.

#### Scenario: Signed-out gate without a phone
- **WHEN** the bench build is launched with the signed-out scenario
- **THEN** the watch shows the "sign in on your phone" gate and the launcher widget shows its signed-out prompt

#### Scenario: A failed message is reachable
- **WHEN** the bench build is launched with the failing-message scenario and the user sends a quick reply
- **THEN** the reply shows as not sent with a retry affordance

#### Scenario: Default launch is unchanged
- **WHEN** the bench build is launched with no scenario
- **THEN** it opens signed in with the fixture feeds and conversations

### Requirement: A posted text is observable in bench
In the bench build, a post created on the watch SHALL appear at the top of the Following feed, first as pending and then as a posted item, and SHALL remain there for the life of the process.

#### Scenario: New post stays in the feed
- **WHEN** the user posts "Journey test post" in the bench build and returns to the feed a minute later
- **THEN** exactly one post with that text is at the top of the Following feed

### Requirement: Wear journeys cover every shipped flow
The repository SHALL contain watch journeys for launch and landing, feed browsing, post actions, messages and quick replies, creating a post, launcher widget deep links, swipe-to-dismiss back navigation, state retention across screen-off, and gate states. A journey that the app fails SHALL be treated as an app defect, not edited to pass.

#### Scenario: A navigation regression is caught
- **WHEN** a change makes swipe-to-dismiss from a post return to the landing screen instead of the feed
- **THEN** the back-stack journey fails at the step that verifies the feed is visible

### Requirement: Screenshot baselines cover locale, clock and largest font
Watch screenshot baselines SHALL include, for every text-bearing screen, the small round device at the largest supported font scale in es-419 and pt-BR. Screens that open scrolled SHALL have a baseline rendered inside the app scaffold with a fixed clock. Every state a screen can show SHALL have at least one baseline on small and large round devices.

#### Scenario: A long Spanish label that clips
- **WHEN** a change makes "Abrir en el teléfono" overflow its button on the small round device
- **THEN** the es-419 baseline for the post screen differs and the screenshot job fails

#### Scenario: Clock over content
- **WHEN** a change makes the clock overlap the first visible line of a screen that opens scrolled
- **THEN** the clock baseline for that screen differs

### Requirement: Pull requests build the minified watch app
Every pull request SHALL build the release variant of the watch app with code and resource shrinking enabled, and that build SHALL fail the pull request when it fails.

#### Scenario: A resource the watch needs is stripped
- **WHEN** a change causes shrinking to remove the Wear capabilities resource or fail to link
- **THEN** the pull request's build job fails before merge

### Requirement: Wear pre-release libraries stay tracked and reviewed
Dependency automation SHALL propose the newest pre-release of the Wear Compose, Glance Wear and Remote Compose libraries, including a new alpha after a stable release, in a group separate from other updates. That group SHALL NOT merge automatically.

#### Scenario: A stable release followed by a new alpha
- **WHEN** a library the watch uses publishes `1.9.0-alpha01` after `1.8.0`
- **THEN** an update to `1.9.0-alpha01` is proposed in the Wear pre-release group and waits for review

### Requirement: Wear changes follow a written definition of done
The repository SHALL document the checks a watch change must pass: the flavored lint and unit-test tasks, a bench build installed to an emulator by serial, CI-generated screenshot baselines, the journeys for the touched area, and translations for new strings. The document SHALL state that installs never use a task that targets every attached device.

#### Scenario: A contributor verifies a feed change
- **WHEN** a contributor changes the watch feed screen
- **THEN** the document tells them which Gradle tasks, which emulator, and which journeys to run
