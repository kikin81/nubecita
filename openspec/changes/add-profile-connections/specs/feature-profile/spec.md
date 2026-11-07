## ADDED Requirements

### Requirement: Follower and following counts open the connections screen

The profile stats row SHALL expose the follower count and the following count as separate tappable targets. Tapping either SHALL open the `ProfileConnections` route for that profile on the matching tab. The posts count SHALL remain plain text.

#### Scenario: Tapping the follower count

- **WHEN** the viewer taps the follower count on a loaded profile
- **THEN** the connections screen opens on the Followers tab for that profile

#### Scenario: Tapping the following count

- **WHEN** the viewer taps the following count on a loaded profile
- **THEN** the connections screen opens on the Following tab for that profile

### Requirement: The connections screen lists followers and following

The connections screen SHALL switch between Followers and Following using `ProfilePillTabs`, each labelled with its count, and SHALL page rows in as the viewer scrolls. Each list SHALL keep its loaded rows and scroll position when the viewer switches tabs. Tapping a row SHALL open that account's profile.

#### Scenario: Switching tabs keeps the list

- **WHEN** the viewer scrolls Followers, switches to Following, and switches back
- **THEN** Followers shows the same rows at the same position without reloading

#### Scenario: An empty list

- **WHEN** an account has no followers
- **THEN** the Followers tab shows an empty message

### Requirement: Rows offer a follow action

Each row SHALL show a Follow or Following button, except the viewer's own row and accounts where either side has blocked the other. The button SHALL update immediately and revert with an error if the request fails. Unfollowing SHALL NOT ask for confirmation. Follow state SHALL be shared across both tabs and SHALL survive a page reload.

#### Scenario: Following an account

- **WHEN** the viewer taps Follow on a row
- **THEN** the row shows Following at once, in both tabs where that account appears

#### Scenario: A failed follow

- **WHEN** the follow request fails
- **THEN** the row returns to Follow and one error message is shown

#### Scenario: A reload during server lag

- **WHEN** a page reload reports the old follow state for an account the viewer just changed
- **THEN** the row keeps the state the viewer chose

#### Scenario: A double tap

- **WHEN** the viewer taps the button again while a request is pending
- **THEN** no second request is sent
