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

The available actions SHALL be derived from the number of selected conversations and the active segment, so that invalid combinations cannot be represented. The bulk-capable actions (Leave, Mute/Unmute) SHALL be presented as inline icons in the contextual bar; the single-only actions (Go to profile, Report, Block) SHALL be grouped under an overflow (⋮) menu that is shown only when exactly one conversation is selected. In the Chats segment with exactly one conversation selected, the actions SHALL be Leave, Mute or Unmute (inline) plus Go to profile, Report, and Block (overflow). With two or more selected, only the inline bulk actions Leave and Mute/Unmute SHALL be offered and the overflow SHALL be hidden. In the Requests segment with one selected, the actions SHALL be Accept and Leave (decline) inline plus Go to profile in the overflow; with two or more selected, only bulk Accept and Leave SHALL be offered.

#### Scenario: Single selection in Chats
- **WHEN** exactly one accepted conversation is selected
- **THEN** Leave and Mute/Unmute are available as inline icons
- **AND** Go to profile, Report, and Block are available under the overflow menu

#### Scenario: Multi selection hides single-target actions
- **WHEN** two or more accepted conversations are selected
- **THEN** only Leave and Mute/Unmute (bulk) are available as inline icons
- **AND** the overflow menu (Go to profile, Report, Block) is not shown

#### Scenario: Contextual action icons are labeled
- **WHEN** the contextual app bar shows icon-only actions
- **THEN** each action exposes a text label via content description
- **AND** a tooltip with that label appears on long-press or pointer hover

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

Leaving one or more conversations SHALL remove the rows from the list immediately and present a Snackbar with an Undo affordance. The `chat.bsky.convo.leaveConvo` network call SHALL NOT be issued until the leave is **committed** — when the Snackbar dismisses, when a new leave supersedes it, or when the hosting screen is destroyed normally (navigated away). Undo SHALL restore the removed rows without issuing any network call. Only one pending-leave batch SHALL exist at a time; starting a new leave while a batch is pending SHALL commit the pending batch first. A committed leave SHALL complete even if the screen's lifecycle scope is torn down during the call. A leave that is still pending (uncommitted) when the hosting process is destroyed — where normal teardown callbacks do not run — SHALL be dropped rather than executed.

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

#### Scenario: Leaving the screen commits a pending leave
- **WHEN** the Chats screen is destroyed normally (e.g. the user navigates away) while a leave is pending and uncommitted
- **THEN** the pending leave is committed (its `leaveConvo` calls are issued), not dropped
- **AND** the commit completes even though the screen's lifecycle scope is torn down

#### Scenario: Pending leave dropped on process death
- **WHEN** the hosting process is destroyed (so normal teardown callbacks do not run) while a leave is pending and uncommitted
- **THEN** the leave is not executed

#### Scenario: Bulk leave
- **WHEN** the user leaves multiple selected conversations at once
- **THEN** all selected rows are removed and a single Undo Snackbar governs the whole batch

#### Scenario: Leaving the open conversation clears the detail pane
- **WHEN** a leave (single or bulk) includes the conversation currently open in the tablet detail pane
- **THEN** the detail pane clears to its empty placeholder
- **AND** undoing the leave restores the row to the list but does not automatically reopen the thread in the detail pane

### Requirement: Conversation action failures are non-destructive

Mute, accept, and a committed leave that fail at the network SHALL surface a transient error message and SHALL revert any optimistic change, so the conversation list reflects the true server state. For a multi-conversation operation, each conversation's outcome SHALL be handled independently — a partial failure reverts only the conversations whose call failed.

#### Scenario: Mute failure reverts
- **WHEN** a mute or unmute action fails
- **THEN** a transient error is shown and the conversation's muted state is reverted

#### Scenario: Committed bulk-leave partial failure restores only the failed conversations
- **WHEN** a committed leave batch is flushed and some (but not all) of its `leaveConvo` calls fail
- **THEN** a transient error is shown
- **AND** only the conversations whose call failed are restored to the list
- **AND** the conversations that were successfully left stay removed
