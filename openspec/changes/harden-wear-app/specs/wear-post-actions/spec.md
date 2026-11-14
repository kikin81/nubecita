# Spec Delta

## Purpose

Likes, reposts, bookmarks and "Open on phone" from the watch, and how their state is shown and delivered through the phone.

## ADDED Requirements

### Requirement: Post counts use icons that follow the text
Reply, repost and like counts SHALL each be shown as an icon with a number. Icons SHALL take the colour of their own count. Only a count the viewer has acted on SHALL be emphasised, and toggling an action SHALL NOT shift the row.

#### Scenario: Liking a post
- **WHEN** the user likes a post
- **THEN** the like icon and its number are emphasised, the reply and repost counts keep their colour, and the row does not move

### Requirement: Open on phone reports what happened
"Open on phone" SHALL show the success confirmation only after the phone accepted the request. When the request fails, the watch SHALL show a failure confirmation.

#### Scenario: Phone reachable
- **WHEN** the user taps "Open on phone" and the phone accepts the request
- **THEN** the "opened on phone" confirmation is shown

#### Scenario: Phone unreachable
- **WHEN** the user taps "Open on phone" and the request fails
- **THEN** a failure confirmation is shown and no success confirmation appears

### Requirement: Actions survive a transient failure
When the phone cannot complete a watch action because it has no connectivity, it SHALL keep the action unanswered and try it again the next time it is already awake for watch sync. It SHALL NOT schedule new background work to do so, and SHALL NOT perform the same action twice. A like, repost or bookmark SHALL stay shown as pending on the watch meanwhile.

#### Scenario: Like while the phone is offline
- **WHEN** the user likes a post while the phone has no network, and later opens the watch app with the phone online
- **THEN** the like is delivered once and shows as confirmed

#### Scenario: A reply is never sent twice
- **WHEN** the phone is interrupted after starting to send a watch reply and later retries leftover actions
- **THEN** the reply is not sent a second time

### Requirement: An orphaned pending action is cleared
When a pending action has neither a queued request nor a result, the watch SHALL clear it: a toggle returns to its synced state, and a reply or post is shown as failed with retry. A pending action whose request is still queued is not orphaned: it SHALL stay pending until the phone answers. Likes, reposts and bookmarks do not expire; replies and posts keep their one-hour expiry.

#### Scenario: An action lost in transit
- **WHEN** the watch app opens and finds a pending like with no queued request and no result
- **THEN** the like control is enabled again and shows the synced state

#### Scenario: A like queued while the phone is away
- **WHEN** a like has been queued for a day because the phone was unreachable, and the phone returns
- **THEN** the like was shown as pending throughout and is delivered once
