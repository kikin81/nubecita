# Spec Delta

## Purpose

The unread-DMs complication the watch app offers to watch faces.

## ADDED Requirements

### Requirement: Long-text complication
The complication SHALL also offer a long-text type that states the unread count in words, with preview data.

#### Scenario: A long-text slot
- **WHEN** the user assigns the complication to a long-text slot with three unread messages
- **THEN** the slot reads as three new messages in the watch's language

### Requirement: The complication is tappable without data
Before the first sync and while signed out, the complication SHALL show the Nubecita messages icon and SHALL open the watch app when tapped.

#### Scenario: Signed out
- **WHEN** the phone app is signed out and the user taps the complication
- **THEN** the watch app opens and explains that sign-in is needed

### Requirement: Image types are distinct and ambient-safe
Each image type the complication offers SHALL be suitable for that type, and the monochromatic image SHALL provide an ambient variant that avoids large filled areas.

#### Scenario: Ambient watch face
- **WHEN** the watch face is in ambient mode
- **THEN** the complication's icon is drawn as an outline
