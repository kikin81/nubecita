## ADDED Requirements

### Requirement: DM poll fetches only changed threads for the watch
While a watch is paired, each DM poll run SHALL fetch the last ~20 messages only for the conversations `getLog` reports as changed. The first poll after pairing SHALL seed the ~10 most recent conversations once. The poll SHALL keep its existing gates: no work while the phone app is in the foreground or while message checking is off.

#### Scenario: Quiet poll
- **WHEN** the DM poll runs, a watch is paired, and `getLog` reports no changed conversations
- **THEN** no message fetch is made

#### Scenario: Message checking turned off
- **WHEN** the user turns off message checking on the phone
- **THEN** the watch's DMs stay at the last snapshot, and nothing keeps the poll running for the watch

### Requirement: Phone publishes a cached DM snapshot
The phone SHALL keep the watch's DM snapshot in a phone-side cache, so threads that didn't change keep their messages across process death. After each poll it SHALL publish a non-urgent snapshot at `/v1/<accountDid>/dms` with the conversations, their messages and the unread count.

#### Scenario: One thread changes
- **WHEN** a poll fetches new messages for one conversation
- **THEN** the published snapshot contains the updated conversation and every other conversation's previously cached messages

### Requirement: Watch conversation list and thread
The watch SHALL show the synced conversations with their unread state, and a thread view of each conversation's synced messages. It SHALL work offline.

#### Scenario: Opening a conversation offline
- **WHEN** the user opens a conversation with no phone connection
- **THEN** the thread shows the stored messages

### Requirement: Quick replies and reactions with expiry
From a thread, the watch SHALL offer a fixed list of localized canned replies and the emoji reactions the phone supports. Each one SHALL be sent as an urgent `dm-reply` or `dm-react` action, handled on the phone by `:feature:chats:impl`'s `WearActionHandler`. The thread SHALL show a queued reply as "Sending…". The phone SHALL NOT send a reply or reaction older than 1 hour; it SHALL answer `failed: expired` instead. On any failed result, the watch SHALL show "Not sent — tap to retry".

#### Scenario: Reply delivered in time
- **WHEN** the user sends "On my way" and the phone receives it within an hour
- **THEN** the message is sent in the conversation, and the watch's "Sending…" state clears on the result

#### Scenario: Reply expired
- **WHEN** a quick reply reaches the phone more than one hour after it was created
- **THEN** it is not sent, and the watch shows "Not sent — tap to retry" on that message
