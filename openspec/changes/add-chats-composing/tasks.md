Sub-flow **A–D** (send in existing convo) = categories 1–4; sub-flow **E–G** (new convo) = categories 5–7. The two sub-flows are independent and may be claimed/landed in parallel [P]. Each category maps to a candidate child issue under epic `nubecita-b6uv`.

## 1. Repository: sendMessage (child A)

- [ ] 1.1 Add `suspend fun sendMessage(convoId: String, text: String): Result<MessageUi>` to the `ChatRepository` interface.
- [ ] 1.2 Implement in `DefaultChatRepository` using `ConvoService(client).sendMessage(...)` (`chat.bsky.convo.sendMessage`), inside the existing `withContext(dispatcher) { runCatching { … }.onFailure { Timber … } }` shape; map the response through the existing `toMessageUi` mapper (status `Sent`).
- [ ] 1.3 Extend `ChatsErrorMapping` (`toChatError`) to cover send-failure wire codes (reuse `ChatError`: `Network` / generic `Unknown`; add a variant only if a distinct UX is needed).
- [ ] 1.4 Add `sendMessage` to `FakeChatRepository` (success + injectable failure) in both `test/` and `androidTest/`.
- [ ] 1.5 Unit-test the success mapping and error mapping for send.

## 2. Composer UI in the thread (child B)

- [ ] 2.1 Add a Compose `TextFieldState` `val` to `ChatViewModel` (sanctioned editor exception) and a `snapshotFlow` collector that updates `isSendEnabled` on `ChatScreenViewState` (false when blank/whitespace).
- [ ] 2.2 Add `ChatEvent.Send` (and wire it in `handleEvent`).
- [ ] 2.3 Render an M3 Expressive composer row in `ChatScreen` / `ChatScreenContent`: text field + end-aligned send button (`surfaceContainerHigh` per surface roles), `imeAction = Send`, send control disabled while `!isSendEnabled`.
- [ ] 2.4 Keep the `reverseLayout` list anchored to the newest message when a message is appended / the composer gains focus.
- [ ] 2.5 Screenshot tests: empty composer, typed (send enabled).

## 3. Optimistic echo + reconcile (child C)

- [ ] 3.1 Add `MessageSendStatus = Sending | Sent | Failed` and a `sendStatus: MessageSendStatus = Sent` field to `MessageUi`; default keeps read-path callers/fixtures unchanged.
- [ ] 3.2 On `Send`: capture + clear the field, append a `ThreadItem.Message` with a `local:<counter>` temp id and `Sending` status (`isOutgoing = true`), then call `repository.sendMessage(convoId, text)`. Requires the resolved `convoId` from the load chain.
- [ ] 3.3 On success: replace the temp row with the returned server `MessageUi` (`Sent`); ensure the next `getMessages` refresh de-dupes on server id so the message isn't doubled.
- [ ] 3.4 Unit test (Turbine): `Send` → optimistic append (`Sending`) → success reconcile leaves exactly one `Sent` row with the server id, field cleared.

## 4. Send error + retry UX (child D)

- [ ] 4.1 Add `ChatEffect.ShowSendError(error: ChatError)`; on send failure flip the temp row to `Failed` and emit it once (snackbar via the screen's effect collector).
- [ ] 4.2 Add `ChatEvent.RetrySend(tempId)` + an inline retry affordance on `Failed` rows (re-issues `sendMessage`, flips back to `Sending`).
- [ ] 4.3 Screenshot tests: sending indicator, failed row with retry.
- [ ] 4.4 Unit test: failure path sets `Failed` + emits `ShowSendError`; `RetrySend` success reconciles to `Sent`.

## 5. New-Chat entry point (child E) [P]

- [ ] 5.1 Add `NewChat` nav key (`@Serializable data object`) to `:feature:chats:api`.
- [ ] 5.2 Add an M3 Expressive small FAB (bottom-end) to `ChatsScreen`; tap emits a new `ChatsEffect.NavigateToNewChat` collected by the screen → `LocalMainShellNavState.current.add(NewChat)`.
- [ ] 5.3 Register the `NewChat` entry in `ChatsNavigationModule` under `@MainShell`.
- [ ] 5.4 Screenshot test: convo list with FAB.

## 6. Recipient picker (child F) [P]

- [ ] 6.1 Create `NewChatContract` / `NewChatViewModel` / `NewChatScreen` (+ `NewChatScreenContent`).
- [ ] 6.2 State: query `TextFieldState` (editor exception), `ImmutableList` of result rows, and sealed `NewChatLoadStatus = Idle | Searching | Results | Empty | Error`.
- [ ] 6.3 Wire `app.bsky.actor.searchActors` (debounced typeahead, NOT chat-proxied) — add to `ChatRepository` or reuse an existing actor-search repository (resolve Open Question in design); map results to a UI row model (avatar + display name + handle + DID).
- [ ] 6.4 Render results as tappable rows.
- [ ] 6.5 Unit-test the VM: debounce, results, empty, error states.
- [ ] 6.6 Screenshot tests: idle, results, empty.

## 7. Resolve existing-or-new convo on selection (child G) [P]

- [ ] 7.1 On result tap: emit `NewChatEffect.NavigateToChat(otherUserDid)` → `LocalMainShellNavState.current.add(Chat(otherUserDid))`; decide whether to pop `NewChat` (Open Question).
- [ ] 7.2 Confirm `ChatViewModel.resolveConvo(otherUserDid)` handles a brand-new convo: empty thread renders "no messages yet" with the composer enabled.
- [ ] 7.3 Verify first `Send` materializes the convo server-side (no extra create call needed — `resolveConvo` already resolves/creates).
- [ ] 7.4 Unit test: `otherUserDid` with no prior messages resolves a convo id and renders an empty thread; first send succeeds.

## 8. Cross-cutting validation

- [ ] 8.1 After a successful send, ensure the convo-list row reflects the latest message + ordering on return (refresh-on-resume is acceptable).
- [ ] 8.2 `./gradlew spotlessCheck lint :app:checkSortDependencies` pass.
- [ ] 8.3 `./gradlew testDebugUnitTest` + relevant `validateDebugScreenshotTest` pass; update baselines as needed.
- [ ] 8.4 Add the `run-instrumented` label on the PR if instrumented coverage for the send / new-chat path is included.
