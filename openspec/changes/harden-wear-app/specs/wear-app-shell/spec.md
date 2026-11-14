# Spec Delta

## Purpose

The watch app's shell: its structure, layout rules on round screens, theming, ambient behaviour and launch states that every watch screen shares.

## ADDED Requirements

### Requirement: Nothing is clipped by the round edge
On small and large round devices, at every supported font scale and in every shipped locale, no text or control SHALL be cut by the screen edge or by another control when a screen is at rest at its start or its end.

#### Scenario: Last controls on a post
- **WHEN** a post is scrolled to its end on the small round device at the largest font scale
- **THEN** the like, repost and bookmark controls and the "Open on phone" label are fully visible

#### Scenario: A gate message with no button
- **WHEN** a gate message without an action is scrolled to its end
- **THEN** its last line sits clear of the bottom edge

### Requirement: The clock never covers readable content
When a screen is at rest, the time SHALL NOT be drawn over text or controls the user can read. The watch SHALL show exactly one clock.

#### Scenario: A screen that opens scrolled
- **WHEN** a conversation or the text review screen opens away from its top
- **THEN** no line of content is visible underneath the time

### Requirement: Edge buttons take part in scrolling
A button that hugs the bottom edge of a scrolling screen SHALL allow a vertical drag that starts on it to scroll the screen.

#### Scenario: Dragging from Reply
- **WHEN** the user drags upward starting on the Reply button in a conversation
- **THEN** the conversation scrolls

### Requirement: List items transform at the edges
Items in a scrolling list SHALL scale and fade individually as they approach the top and bottom edges. An item at rest in the readable area SHALL be shown at full opacity.

#### Scenario: Second row of a short picker
- **WHEN** a picker with two rows is shown at rest
- **THEN** both rows are fully opaque

### Requirement: Every colour role comes from the brand scheme
All colour roles used by watch screens and the launcher widget SHALL come from one scheme generated from the Nubecita brand colour. The scheme SHALL NOT follow the watch face.

#### Scenario: Card beside an accent
- **WHEN** a feed card with a primary-coloured accent is shown
- **THEN** the card background and the accent belong to the same brand scheme

### Requirement: Ambient rendering
When the watch enters ambient mode with the app visible, the app SHALL hide images, replace filled surfaces with outlines, and update displayed times only on the ambient tick. On return to interactive mode it SHALL restore the same screen and scroll position.

#### Scenario: Feed in ambient
- **WHEN** the watch enters ambient mode on the feed
- **THEN** thumbnails and avatars are hidden and most of the screen is black

#### Scenario: Waking
- **WHEN** the watch returns to interactive mode
- **THEN** the feed shows the same post in the same position, with images

### Requirement: Loading is visible and ends
While the watch is determining whether the phone app is present and signed in, it SHALL show a progress indicator if that takes longer than a brief moment. If that check fails, the watch SHALL show the signed-out message rather than stay blank.

#### Scenario: A slow check
- **WHEN** the phone-presence check takes several seconds
- **THEN** a progress indicator is shown until it completes

#### Scenario: A failed check
- **WHEN** the phone-presence check fails
- **THEN** the watch shows the "sign in on your phone" message

### Requirement: Watch app ships a startup profile
The release build of the watch app SHALL include a baseline profile and a startup profile that cover launch, the landing screen, feed scrolling, opening a post and opening messages.

#### Scenario: Installed release build
- **WHEN** the release watch app has been installed and background optimization has run
- **THEN** its compiled code status reports profile-guided compilation and the profile contains the app's own classes
