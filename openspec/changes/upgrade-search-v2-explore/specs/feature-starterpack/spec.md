# Spec Delta

## Purpose

Lets a user open a Bluesky starter pack inside the app and see who and what it contains, so discovery surfaces that list packs have a native destination.

## ADDED Requirements

### Requirement: A starter pack opens in a native screen

Opening a starter pack by its AT-URI SHALL show a screen with the pack's name, creator and description, its members, and its feeds. The screen SHALL take the detail-pane role on Medium and Expanded widths and render full-screen on Compact width.

#### Scenario: Pack loads

- **WHEN** the user opens a starter pack
- **THEN** the header shows the pack's name, creator and description
- **AND** the first page of members is listed
- **AND** the pack's feeds are listed when it has any

#### Scenario: Long member lists paginate

- **WHEN** the user scrolls to the end of the loaded members and more exist
- **THEN** the next page of members is appended

#### Scenario: Pack cannot be loaded

- **WHEN** the pack does not exist or the request fails
- **THEN** an error state with a retry action is shown

### Requirement: Members can be followed one at a time

Each member row SHALL offer a Follow action that updates immediately and rolls back with a message if the request fails. The screen SHALL NOT offer a "follow all" action.

#### Scenario: Follow succeeds

- **WHEN** the user taps Follow on a member
- **THEN** the row shows the followed state immediately and keeps it after the request succeeds

#### Scenario: Follow fails

- **WHEN** the follow request fails
- **THEN** the row returns to its previous state
- **AND** an error message is shown

### Requirement: Member and feed taps navigate

Tapping a member SHALL open that profile, and tapping a feed SHALL open that feed, each stacked above the pack in the same pane.

#### Scenario: Member tapped

- **WHEN** the user taps a member row outside its Follow button
- **THEN** the member's profile opens
- **AND** system back returns to the pack
