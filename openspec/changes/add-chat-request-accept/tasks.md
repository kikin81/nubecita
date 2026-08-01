## 1. Bench fixtures (`nubecita-1ts5.1`)

Prerequisite: no bench fixture models a request-status conversation today, so nothing below is screenshot- or bench-verifiable until this lands.

- [ ] 1.1 Add a request-status field to `BenchConvoDto` and seed `chats.json` with one **direct** and one **group** request conversation. The group case is required — it is the only thing that exercises the "Accept and join" label variant. Test: extend the existing bench DTO parsing test to assert both fixtures deserialize with the new field.
- [ ] 1.2 Split `BenchFakeChatRepository.ensureLoaded()` so request-status rows publish to `requestConvosFlow` instead of the accepted flow, replacing the hard-coded empty list. Test: bench-flavor unit test asserting the Requests flow emits exactly the seeded requests and the Chats flow excludes them.
- [ ] 1.3 Implement `BenchFakeChatRepository.acceptConvo` to move a row from the request flow to the front of the accepted flow, replacing today's success no-op. Test: unit test asserting a row moves between the two flows and the accepted flow's ordering puts it first.

## 2. Shared mapper: carry convo status (`nubecita-1ts5.2` / `nubecita-1ts5.3`)

Land once; both UI tasks depend on it. Keeping it in one task is what prevents the list and the thread disagreeing about the same conversation.

- [ ] 2.1 Carry `ConvoView.status` through `ConvoMapper` into both the row model and the thread model. Treat only `"request"` as pending; any other value, including unrecognised ones, maps to accepted (design D2 / the open-string risk). Test: `ConvoMapperTest` cases for `"request"`, `"accepted"`, `null`, and an unknown value.
- [ ] 2.2 Stop `canViewerPost` reporting a pending request as postable, keeping the existing membership and group-lock rules intact. Test: mapper unit tests covering request × member, accepted × non-member, and accepted × locked-group, so the new rule cannot mask the existing two.

## 3. Thread accept surface (`nubecita-1ts5.2`)

- [ ] 3.1 Add the request flag to `ChatScreenViewState` and an accept event to `ChatEvent`; have `ChatViewModel` populate the flag from the loaded conversation. Test: `ChatViewModelTest` asserting state reflects a request-status convo on load.
- [ ] 3.2 Build the accept surface composable in `feature/chats/impl` (feature-private, alongside `CannotPostNotice` — design D4): `Surface(surfaceContainerHigh)`, a full-width `NubecitaPrimaryButton` reusing its built-in `isLoading`, and a decline text button beneath. Accept and decline only, no block (design D9). Not a connected button group (design D3). Test: screenshot tests for direct and group variants in light and dark.
- [ ] 3.3 Add the third bottomBar branch in `ChatScreenContent` for pending requests, inside the existing inset-owning `Column` so the bar remains the single IME owner. Test: screenshot test of the thread showing message history above the accept surface with no composer present.
- [ ] 3.4 Wire accept to `ChatRepository.acceptConvo`, driving the button's in-flight state and routing failure through the screen's existing `UiEffect` snackbar path. Test: `ChatViewModelTest` for success, failure (surface stays, effect emitted), and the already-accepted response whose `rev` is absent.
- [ ] 3.5 Cross-fade the accept surface to the composer on success using `AnimatedContent` with the expressive spatial spring (design D6). Test: unit-test the state transition to composer-visible; the animation itself is not asserted.
- [ ] 3.6 Wire decline to the existing deferred-undo leave path — no new destructive path. Test: `ChatViewModelTest` asserting decline defers the network call and undo cancels it.
- [ ] 3.7 Add `es-419` and `pt-BR` translations for every new string in the same commit, then run `:feature:chats:impl:lint` — the `:app` lint task does not catch `MissingTranslation` in a feature module. Verify the group label at its longest translation does not truncate (design D3).

## 4. Requests list row actions (`nubecita-1ts5.3`)

- [ ] 4.1 Extend `ConvoListItem` with an actions slot shown only for request rows, following the existing `JoinRequestRow` shape (`request, inFlight, onApprove, onReject`) rather than inventing a new pattern. Test: screenshot tests of a request row and an accepted row proving the actions appear only on the former.
- [ ] 4.2 Add per-row accept and decline events to `ChatsContract` / `ChatsViewModel`, reusing the existing bulk accept and deferred-undo leave paths so there is one implementation of each action. Test: `ChatsViewModelTest` for row accept moving the convo between segments, and row decline deferring with undo.
- [ ] 4.3 Track per-row in-flight state so a row's actions disable while its call is outstanding and other rows stay interactive. Test: `ChatsViewModelTest` asserting two concurrent row actions track independently.
- [ ] 4.4 Give each row action an accessibility label naming the action and its conversation. Test: `ConvoListItem` unit/screenshot test asserting the labels, following the existing convention of matching the on-click label rather than the content description.

## 5. Verification

- [ ] 5.1 Run `./gradlew jacocoTestReportAggregated` — the CI task; the root `testDebugUnitTest` skips flavored modules and `:feature:chats:impl` is flavored.
- [ ] 5.2 Compile both flavors (`compileBenchDebugKotlin` and `compileProductionDebugKotlin`) — the bench source set has its own chat fakes that the mapper change can break without the production build noticing.
- [ ] 5.3 Regenerate and review the affected screenshot baselines, confirming the new images differ from the accepted-conversation baselines rather than matching them.
- [ ] 5.4 Device pass on the foldable against a real pending request: open it, confirm no composer, accept, confirm the composer appears and the conversation leaves the Requests segment. This is the only step that exercises the real `acceptConvo` response.
