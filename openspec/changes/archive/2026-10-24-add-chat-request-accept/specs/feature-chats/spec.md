## ADDED Requirements

### Requirement: Request rows offer accept and decline directly

A conversation row in the Requests segment SHALL present accept and decline actions on the row itself, without requiring the user to enter selection mode first. These actions are additive: the existing multi-select contextual bar continues to offer Accept and Leave for one or more selected requests, unchanged.

Rows in the Chats segment SHALL NOT present these actions.

#### Scenario: A request row exposes its actions

- **WHEN** the Requests segment is shown
- **THEN** each request row presents an accept action and a decline action
- **AND** activating either requires no prior long-press or selection

#### Scenario: Accepted conversations are unaffected

- **WHEN** the Chats segment is shown
- **THEN** conversation rows present no accept or decline action

#### Scenario: Accepting from the row

- **WHEN** the user activates accept on a request row
- **THEN** that conversation is accepted
- **AND** it leaves the Requests segment and appears in the Chats segment

#### Scenario: Declining from the row

- **WHEN** the user activates decline on a request row
- **THEN** the conversation is removed from the Requests segment with the same recoverable deferred undo as leaving a conversation

#### Scenario: A row action is in flight

- **WHEN** an accept or decline issued from a row has not yet completed
- **THEN** that row's actions show an in-flight state and cannot be activated again

#### Scenario: Multi-select still works

- **WHEN** the user long-presses a request row
- **THEN** selection mode is entered as before
- **AND** the contextual bar offers Accept and Leave for the selection

#### Scenario: Row actions are labelled for assistive technologies

- **WHEN** a request row's accept or decline action is inspected by an accessibility service
- **THEN** each action exposes a label naming the action and the conversation it applies to

### Requirement: The conversation row carries its request status

The conversation row model SHALL carry whether the conversation is a pending request, derived from the `status` field returned by `chat.bsky.convo.listConvos`. This is the same source the conversation thread uses, so a conversation SHALL NOT be presented as a request in one surface and as accepted in the other.

#### Scenario: Status comes from the conversation

- **WHEN** a conversation is fetched with `status = "request"`
- **THEN** its row is marked as a pending request

#### Scenario: The two surfaces agree

- **WHEN** a conversation is shown as a pending request in the list and then opened
- **THEN** the thread also presents it as a pending request
