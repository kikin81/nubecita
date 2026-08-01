## ADDED Requirements

### Requirement: A request conversation is identified from its own status

The conversation thread SHALL determine whether it is showing a pending request from the `status` field of the `ConvoView` returned by `chat.bsky.convo.getConvo`, not from any value supplied by the caller that navigated to it. A conversation whose status is `"request"` SHALL be treated as pending; any other value, including one this app does not recognise, SHALL be treated as accepted.

#### Scenario: Request status drives the thread regardless of entry point

- **WHEN** a conversation whose status is `"request"` is opened
- **THEN** the thread presents it as a pending request
- **AND** this holds whether it was opened from the Requests segment, a notification, or a deep link

#### Scenario: An unrecognised status is treated as accepted

- **WHEN** `getConvo` returns a status that is neither `"request"` nor `"accepted"`
- **THEN** the thread presents the conversation as accepted and allows posting

#### Scenario: Membership and lock rules still apply

- **WHEN** a conversation is accepted but the viewer is not a member, or the group is locked
- **THEN** the thread continues to show the existing cannot-post notice rather than the accept surface

### Requirement: A pending request replaces the composer with an accept surface

While a conversation is a pending request, the thread SHALL NOT present a message composer. The bottom bar SHALL instead present an accept surface offering a primary accept action plus a decline action. Because no text field is presented, sending a message into an unaccepted conversation SHALL be unrepresentable in the UI.

#### Scenario: Opening a request shows the accept surface

- **WHEN** the user opens a conversation whose status is `"request"`
- **THEN** the bottom bar shows the accept surface
- **AND** no message text field or send control is present

#### Scenario: Accepted conversations are unaffected

- **WHEN** the user opens a conversation whose status is `"accepted"` and posting is otherwise permitted
- **THEN** the bottom bar shows the normal composer
- **AND** the accept surface is not present

#### Scenario: The accept surface owns the keyboard inset

- **WHEN** the accept surface is shown
- **THEN** it occupies the same single window-inset-owning position as the composer it replaces
- **AND** no second keyboard inset layer is introduced

#### Scenario: Message history stays readable

- **WHEN** a request is pending
- **THEN** the messages already sent in that conversation remain visible and scrollable

### Requirement: The accept action is labelled for the conversation kind

The primary accept action SHALL be labelled for a direct conversation in a way that reflects accepting a message, and for a group conversation in a way that reflects joining the group, derived from the conversation's `kind`.

#### Scenario: Direct conversation

- **WHEN** the pending request is a direct conversation
- **THEN** the primary action is labelled as accepting the request

#### Scenario: Group conversation

- **WHEN** the pending request is a group conversation
- **THEN** the primary action is labelled as accepting and joining, because accepting adds the user to the group

### Requirement: Accepting from the thread yields a usable conversation

Accepting SHALL call `chat.bsky.convo.acceptConvo` for the conversation. On success the thread SHALL replace the accept surface with the normal composer in place, without requiring the user to navigate away and back, and the conversation SHALL move from the Requests segment to the Chats segment without a manual refresh.

#### Scenario: Successful accept

- **WHEN** the user activates the accept action and the call succeeds
- **THEN** the accept surface is replaced by the message composer
- **AND** the user can send a message immediately

#### Scenario: The conversation list reflects the accept

- **WHEN** an accept succeeds from the thread
- **THEN** the conversation no longer appears in the Requests segment
- **AND** it appears in the Chats segment

#### Scenario: Accept is in flight

- **WHEN** the accept call has been issued and has not yet completed
- **THEN** the primary action shows a loading state and cannot be activated again
- **AND** its accessible label continues to describe the action

#### Scenario: Accept fails

- **WHEN** the accept call fails
- **THEN** the conversation remains a pending request with its accept surface intact
- **AND** the failure is surfaced as a transient message, consistent with the thread's other error handling

#### Scenario: Already accepted elsewhere

- **WHEN** the conversation was already accepted on another device and the user activates accept
- **THEN** the call is treated as successful and the composer is shown

### Requirement: The thread offers accept only; declining is a list action

The accept surface SHALL offer accept as its only committing action. It SHALL NOT offer decline or block.

Declining is offered on the conversation row in the Requests segment, where it is recoverable through the existing deferred-undo leave. Offering it in the thread as well would mean either duplicating that undo machinery in a second presenter or handing a deferred action across screens; a decline that is undoable from one surface and immediate from another is worse than a decline that lives in one place.

Blocking is likewise not offered: it has no single meaning for a group request, and the Requests segment does not offer it today. Safety actions against an individual remain available through the existing profile surface.

#### Scenario: The thread does not commit a decline

- **WHEN** a pending request is open in the thread
- **THEN** no decline or block action is presented
- **AND** the user can leave the request untouched by navigating back

#### Scenario: Declining is reachable from the list

- **WHEN** the user wants to decline a request
- **THEN** the decline action on the request's row in the Requests segment performs it
- **AND** it is recoverable for the same window as leaving a conversation
