# Spec Delta

## Purpose

Direct messages on the watch: the conversation list, the thread, and replies and reactions sent from the wrist.

## ADDED Requirements

### Requirement: The thread stays on the newest message
A conversation SHALL open at its newest message. When the user sends a reply while at the newest message, the new message and the Reply button SHALL both be fully visible without scrolling. When the user has scrolled up to read older messages, an incoming message SHALL NOT move what they are reading.

#### Scenario: After a quick reply
- **WHEN** the user sends a quick reply from a conversation
- **THEN** the conversation shows the reply as its newest message, fully visible above the Reply button

#### Scenario: Reading older messages
- **WHEN** the user has scrolled up and a new message arrives
- **THEN** the message they were reading stays in place

#### Scenario: Opening a short conversation
- **WHEN** a conversation with two messages is opened
- **THEN** its title is visible and is not hidden under the time
