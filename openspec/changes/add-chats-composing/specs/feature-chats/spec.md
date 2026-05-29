## ADDED Requirements

### Requirement: Send a text message in an existing conversation

The system SHALL let a user compose and send a plain-text message within an open conversation thread, via a composer affordance on the thread screen, persisting the message through `chat.bsky.convo.sendMessage`.

#### Scenario: Sending a non-empty message

- **WHEN** the user has typed non-empty text in the composer and taps Send
- **THEN** the message is sent to the conversation
- **AND** the composer input is cleared
- **AND** the sent message appears in the thread attributed to the user

#### Scenario: Send is disabled for empty input

- **WHEN** the composer is empty or contains only whitespace
- **THEN** the Send control is disabled
- **AND** no send request is issued

### Requirement: Optimistic message delivery

The system SHALL display a sent message in the thread immediately on send, before server confirmation, and reconcile its delivery state with the server result without duplicating the message.

#### Scenario: Message appears immediately while sending

- **WHEN** the user sends a message
- **THEN** the message is appended to the thread immediately with a sending indicator
- **AND** the thread scrolls to show the new message

#### Scenario: Reconcile to sent on confirmation

- **WHEN** the server confirms a sent message
- **THEN** the optimistic message is reconciled to the confirmed server message
- **AND** the sending indicator is replaced with a sent state
- **AND** the message is not duplicated when the thread next refreshes

### Requirement: Send failure and retry

The system SHALL surface a failed send without losing the message and allow the user to retry sending it.

#### Scenario: Send fails

- **WHEN** sending a message fails
- **THEN** the message is marked as failed in the thread
- **AND** a transient error is shown to the user
- **AND** a retry affordance is offered on the failed message

#### Scenario: Retry a failed message

- **WHEN** the user retries a failed message
- **THEN** the message returns to the sending state and is re-sent
- **AND** on success it is reconciled to the confirmed server message

### Requirement: Start a new conversation

The system SHALL let a user start a new conversation from the conversation-list screen by selecting a recipient, without requiring an existing conversation.

#### Scenario: Open the new-chat entry point

- **WHEN** the user taps the new-chat action on the conversation-list screen
- **THEN** a recipient-picker screen is shown

#### Scenario: Search for a recipient

- **WHEN** the user types a query in the recipient picker
- **THEN** matching accounts are shown as selectable results
- **AND** an empty-results state is shown when there are no matches

#### Scenario: Select a recipient

- **WHEN** the user selects an account from the results
- **THEN** the conversation thread for that account is opened

### Requirement: Resolve or create a conversation from a recipient

The system SHALL resolve the existing conversation for a selected recipient, or create one, so the user can send the first message.

#### Scenario: Existing conversation is reused

- **WHEN** the user opens a thread for a recipient who already has a conversation
- **THEN** the existing conversation and its message history are shown

#### Scenario: New conversation has no history

- **WHEN** the user opens a thread for a recipient with no existing conversation
- **THEN** an empty thread is shown with the composer enabled
- **AND** sending the first message materializes the conversation and delivers the message

### Requirement: Conversation list reflects sent messages

The system SHALL update the conversation list to reflect a message the user has just sent.

#### Scenario: List shows the latest sent message

- **WHEN** the user sends a message and returns to the conversation list
- **THEN** the conversation's row reflects the sent message as the latest message
- **AND** the conversation is ordered accordingly
