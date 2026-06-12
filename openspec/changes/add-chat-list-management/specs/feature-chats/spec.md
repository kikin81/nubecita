# feature-chats — Chat list management (delta)

## ADDED Requirements

### Requirement: Chats and Requests are segmented

The Chats tab home SHALL present two segments — **Chats** (accepted conversations) and **Requests** (pending message requests) — via a pill-tab toggle above the conversation list. The list SHALL show the conversations for the active segment, fetched via `chat.bsky.convo.listConvos` with `status = "accepted"` for Chats and `status = "request"` for Requests.

#### Scenario: Default segment is Chats
- **WHEN** the Chats tab opens
- **THEN** the Chats segment is active and the list shows accepted conversations only

#### Scenario: Switching to Requests
- **WHEN** the user selects the Requests segment
- **THEN** the list shows pending request conversations only

#### Scenario: Requests count badge
- **WHEN** there are pending requests
- **THEN** the Requests segment shows a count badge of the pending-request total
- **AND WHEN** there are no pending requests
- **THEN** no badge is shown

#### Scenario: Requests fetch fails but Chats loads
- **WHEN** the accepted-conversation fetch succeeds and the request-conversation fetch fails
- **THEN** the Chats segment renders its conversations normally
- **AND** the Requests segment renders its own inline error state without failing the whole screen

### Requirement: Conversations can be selected for actions

Long-pressing a conversation row SHALL enter a selection mode in which the top app bar is replaced by a contextual bar showing a close affordance, the selected count, and action icons. While in selection mode, tapping a row SHALL toggle its selection rather than open the thread. Closing the contextual bar (or system back) SHALL exit selection mode.

#### Scenario: Long-press enters selection mode
- **WHEN** the user long-presses a conversation row
- **THEN** selection mode is entered with that conversation selected
- **AND** the contextual app bar replaces the normal top app bar

#### Scenario: Tapping toggles selection while selecting
- **WHEN** selection mode is active and the user taps another conversation row
- **THEN** that conversation's selection is toggled and the thread is not opened

#### Scenario: Exiting selection mode
- **WHEN** the user activates the close affordance or system back while in selection mode
- **THEN** selection mode is exited and the normal top app bar and segment toggle return

### Requirement: Selection actions adapt to count and segment

The available actions SHALL be derived from the number of selected conversations and the active segment, so that invalid combinations cannot be represented. In the Chats segment with exactly one conversation selected, the actions SHALL be Leave, Mute or Unmute, Go to profile, Report, and Block. With two or more selected, only the bulk actions Leave and Mute/Unmute SHALL be offered (single-target actions hidden). In the Requests segment with one selected, the actions SHALL be Accept, Leave (decline), and Go to profile; with two or more, only bulk Accept and Leave.

#### Scenario: Single selection in Chats
- **WHEN** exactly one accepted conversation is selected
- **THEN** Leave, Mute/Unmute, Go to profile, Report, and Block are available

#### Scenario: Multi selection hides single-target actions
- **WHEN** two or more accepted conversations are selected
- **THEN** only Leave and Mute/Unmute (bulk) are available
- **AND** Go to profile, Report, and Block are not shown

#### Scenario: Mixed mute state in a multi-selection
- **WHEN** a multi-selection contains at least one unmuted conversation
- **THEN** the mute action is presented as "Mute"
- **AND WHEN** every selected conversation is already muted
- **THEN** the mute action is presented as "Unmute"

#### Scenario: Request selection offers accept and decline
- **WHEN** one or more request conversations are selected
- **THEN** Accept and Leave (decline) are available
- **AND** Accept moves the conversation(s) into the accepted list

#### Scenario: Report and profile reuse existing surfaces
- **WHEN** Report is invoked on a single selected conversation
- **THEN** the existing moderation report surface is opened for that conversation's account
- **AND WHEN** Go to profile is invoked
- **THEN** the existing profile route is opened for that account

### Requirement: Leaving a conversation is recoverable via deferred undo

Leaving one or more conversations SHALL remove the rows from the list immediately and present a Snackbar with an Undo affordance. The `chat.bsky.convo.leaveConvo` network call SHALL NOT be issued until the Snackbar dismisses. Undo SHALL restore the removed rows without issuing any network call. Only one pending-leave batch SHALL exist at a time; starting a new leave while a batch is pending SHALL commit the pending batch first. A leave that is still pending (uncommitted) when the hosting process is destroyed SHALL be dropped rather than executed.

#### Scenario: Leave shows undo and defers the call
- **WHEN** the user leaves a conversation
- **THEN** the row is removed immediately and a Snackbar with Undo is shown
- **AND** `leaveConvo` is not called while the Snackbar is visible

#### Scenario: Undo restores with no network
- **WHEN** the user activates Undo before the Snackbar dismisses
- **THEN** the removed conversation(s) are restored to the list
- **AND** no `leaveConvo` call is made

#### Scenario: Dismiss commits the leave
- **WHEN** the Snackbar dismisses without Undo
- **THEN** `leaveConvo` is called once per held conversation

#### Scenario: New leave supersedes a pending batch
- **WHEN** a leave is pending and the user initiates another leave
- **THEN** the pending batch is committed immediately before the new batch begins

#### Scenario: Pending leave dropped on process death
- **WHEN** the process is destroyed while a leave is pending and uncommitted
- **THEN** the leave is not executed

#### Scenario: Bulk leave
- **WHEN** the user leaves multiple selected conversations at once
- **THEN** all selected rows are removed and a single Undo Snackbar governs the whole batch

### Requirement: Conversation action failures are non-destructive

Mute, accept, and a committed leave that fail at the network SHALL surface a transient error message and SHALL revert any optimistic change, so the conversation list reflects the true server state.

#### Scenario: Mute failure reverts
- **WHEN** a mute or unmute action fails
- **THEN** a transient error is shown and the conversation's muted state is reverted

#### Scenario: Committed leave failure restores the row
- **WHEN** a committed `leaveConvo` call fails
- **THEN** a transient error is shown and the conversation is restored to the list
