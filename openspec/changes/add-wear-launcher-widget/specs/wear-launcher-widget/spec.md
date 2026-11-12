## Purpose

Put the Nubecita watch app one swipe from the watch face: a carousel widget (shown as a full-screen tile where widgets aren't supported) that shows the unread DM count and opens the app at Messages, Home or New post.

## ADDED Requirements

### Requirement: Launcher widget availability

The watch app SHALL offer one Nubecita launcher surface in the system carousel. On watches that support partial-height widgets it SHALL appear as a large widget; on all other supported watches (Wear OS 4 and later, including Pixel Watch) it SHALL appear as a full-screen tile with the same content.

#### Scenario: Pixel Watch shows it as a tile

- **WHEN** the user opens the tile/widget picker on a Pixel Watch
- **THEN** Nubecita is listed with a preview showing its tiles
- **AND** once added, it fills the screen as a tile with the Messages card, Home and New post

#### Scenario: Partial-height Wear OS 7 watch shows it as a widget

- **WHEN** the user adds Nubecita on a Wear OS 7 watch that supports partial-height widgets
- **THEN** it appears as a large widget card with the same three tiles

### Requirement: Launcher layout

The surface SHALL show the Nubecita name, a Messages card and two pills labelled Home and New post. It SHALL NOT show message text, sender names, avatars or feed content.

#### Scenario: Content is a launcher, not a preview

- **GIVEN** the user has unread messages
- **WHEN** the surface is shown
- **THEN** it shows the unread count on the Messages card and no message text or sender

### Requirement: Unread count states

The Messages card SHALL show the signed-in account's synced unread DM total: the number with a pluralized "new message(s)" label when above zero, capped as "99+" above 99; "No new messages" at zero; and just "Messages" with no number before DMs have ever synced or when signed out.

#### Scenario: Some unread

- **GIVEN** 3 unread messages are synced
- **WHEN** the surface is shown
- **THEN** the Messages card reads "3" and "new messages"

#### Scenario: One unread

- **GIVEN** 1 unread message is synced
- **WHEN** the surface is shown
- **THEN** the Messages card reads "1" and "new message"

#### Scenario: More than 99

- **GIVEN** 120 unread messages are synced
- **WHEN** the surface is shown
- **THEN** the Messages card reads "99+"

#### Scenario: Nothing unread

- **GIVEN** DMs are synced with 0 unread
- **WHEN** the surface is shown
- **THEN** the Messages card reads "No new messages"

#### Scenario: Never synced or signed out

- **GIVEN** DMs have never synced, or the user signed out on the phone
- **WHEN** the surface is shown
- **THEN** the Messages card reads "Messages" with no number

### Requirement: Push-only refresh

The surface SHALL update only when the synced DMs change on the watch (a new snapshot, or the DMs being cleared on sign-out or an account switch). It SHALL NOT refresh on a timer or perform background work of its own.

#### Scenario: New messages arrive

- **GIVEN** the surface shows "No new messages"
- **WHEN** the phone syncs a snapshot with 2 unread
- **THEN** the surface shows "2" without the user opening the app

#### Scenario: Sign-out clears the count

- **GIVEN** the surface shows "5"
- **WHEN** the user signs out on the phone and the watch clears its DMs
- **THEN** the surface shows "Messages" with no number

### Requirement: Tiles open their destination

Tapping the Messages card SHALL open the conversation list, Home SHALL open the feed, and New post SHALL open the post text input. Each SHALL start the app fresh at that destination with the landing screen underneath, so back returns to the landing screen, regardless of what the app last showed.

#### Scenario: Messages from a different screen

- **GIVEN** the app was last left inside a conversation
- **WHEN** the user taps the Messages card
- **THEN** the conversation list opens
- **AND** back shows the landing screen

#### Scenario: Home

- **WHEN** the user taps Home
- **THEN** the feed opens with the landing screen underneath

#### Scenario: New post

- **GIVEN** the phone supports posting from the watch
- **WHEN** the user taps New post
- **THEN** the app opens straight into the post text input

### Requirement: New post when posting is unavailable

When New post is tapped but posting from the watch isn't available, the app SHALL stay on the landing screen and SHALL NOT open the text input later on its own.

#### Scenario: Posting unavailable

- **GIVEN** the phone app can't post from the watch
- **WHEN** the user taps New post
- **THEN** the landing screen is shown and the text input does not open

### Requirement: Deep links are not replayed

A destination opened from the surface SHALL NOT be re-opened when the app is recreated (rotation, process-death restore); the restored screens SHALL be shown instead.

#### Scenario: Restore after New post

- **GIVEN** the user opened New post from the surface and backed out to the landing screen
- **WHEN** the app is killed in the background and reopened
- **THEN** the text input does not open again
