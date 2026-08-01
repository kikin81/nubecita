## 1. Bench fixtures (`nubecita-1ts5.1`)

Prerequisite: no bench fixture models a request-status conversation today, so nothing below is screenshot- or bench-verifiable until this lands.

- [x] 1.1 Add a request-status field to `BenchConvoDto` and seed `chats.json` with one **direct** and one **group** request conversation. The group case is required — it is the only thing that exercises the "Accept and join" label variant. Test: `BenchChatsFixtureTest` in a new `src/testBench` source set — there was no existing bench DTO test, and `src/test` is shared with the production flavor where `BenchConvoDto` does not exist. Request fixtures omit `avatarUrl`: no matching avatar assets exist, and reusing another person's would put one face on two identities in screenshots.
- [x] 1.2 Route request-status rows to `requestConvosFlow`. Requests stay in the single `convosCache` with a separate id set rather than a second cache, so `getConvo`/`resolveConvo` can still open a request thread — which is where task 3 accepts one. `refreshRequestConvos` now actually publishes instead of returning a no-op, and the flow starts `null` (not loaded) rather than an empty list. Test: `BenchChatsFixtureTest` pins the routing rule against the real fixture, so a mistyped status is caught.
- [x] 1.3 Implement `BenchFakeChatRepository.acceptConvo` to move a row from the request flow to the accepted flow, and make `leaveConvo` drop a declined request from both. Accepting an unknown or already-accepted convo is a no-op success, matching how production treats a response with no `rev`.

  Two deviations from this task as originally written, both deliberate:
  - **Ordering.** Production prepends an accepted convo to the accepted cache; the bench fake publishes a map sorted by `sentAt`, so an accepted request lands by recency rather than first. Matching production would mean tracking accept order in a fixture whose purpose is determinism. Not worth it — noted so the difference is not mistaken for a bug.
  - **Test.** Instantiating `BenchFakeChatRepository` needs an Android `Context` for `assets`, and the repo has no Robolectric. The routing rule is unit-tested; the actual move between flows is covered by the device pass in 5.4.

## 2. Shared mapper: carry convo status (`nubecita-1ts5.2` / `nubecita-1ts5.3`)

Land once; both UI tasks depend on it. Keeping it in one task is what prevents the list and the thread disagreeing about the same conversation.

- [x] 2.1 Carry `ConvoView.status` through `ConvoMapper` into both the row model and the thread model. Treat only `"request"` as pending; any other value, including unrecognised ones, maps to accepted (design D2 / the open-string risk). Test: `ConvoMapperTest` cases for `"request"`, `"accepted"`, `null`, and an unknown value.
- [x] 2.2 Stop `canViewerPost` reporting a pending request as postable, keeping the existing membership and group-lock rules intact. Test: mapper unit tests covering request × member, accepted × non-member, and accepted × locked-group, so the new rule cannot mask the existing two.

## 3. Thread accept surface (`nubecita-1ts5.2`)

- [x] 3.1 Add the request flag to `ChatScreenViewState` and an accept event to `ChatEvent`; have `ChatViewModel` populate the flag from the loaded conversation. Test: `ChatViewModelTest` asserting state reflects a request-status convo on load.
- [x] 3.2 Build the accept surface composable in `feature/chats/impl` (feature-private, alongside `CannotPostNotice` — design D4): `Surface(surfaceContainerHigh)`, a full-width `NubecitaPrimaryButton` reusing its built-in `isLoading`, and a decline text button beneath. Accept and decline only, no block (design D9). Not a connected button group (design D3). Test: screenshot tests for direct and group variants in light and dark.
- [x] 3.3 Add the third bottomBar branch in `ChatScreenContent` for pending requests, inside the existing inset-owning `Column` so the bar remains the single IME owner. Test: screenshot test of the thread showing message history above the accept surface with no composer present.
- [x] 3.4 Wire accept to `ChatRepository.acceptConvo`, driving the button's in-flight state and routing failure through the screen's existing `UiEffect` snackbar path. Test: `ChatViewModelTest` for success, failure (surface stays, effect emitted), and the already-accepted response whose `rev` is absent.
- [x] 3.5 ~~Cross-fade with `AnimatedContent`~~ — the bottomBar swap already reads as a clean replacement on device (verified on the Pixel Fold), and wrapping the bar in `AnimatedContent` would animate the single IME-owning slot, which is the one thing the inset contract says not to touch. The state transition to composer-visible is unit-tested; deferred as a polish item rather than shipped untested.
- [x] 3.6 ~~Wire decline in the thread~~ — dropped. See design D5 (revised): the deferred-undo leave is not reusable from the thread presenter, and a decline that is undoable from the list but immediate from the thread is a worse contract than one that lives in a single place. Declining is task 4.2 on the list row.
- [x] 3.7 Add `es-419` and `pt-BR` translations for every new string in the same commit, then run `:feature:chats:impl:lint` — the `:app` lint task does not catch `MissingTranslation` in a feature module. Verify the group label at its longest translation does not truncate (design D3).

## 4. Requests list row actions (`nubecita-1ts5.3`)

- [x] 4.1 Extend `ConvoListItem` with an actions slot shown only for request rows, following the existing `JoinRequestRow` shape (`request, inFlight, onApprove, onReject`) rather than inventing a new pattern. Test: screenshot tests of a request row and an accepted row proving the actions appear only on the former.
- [x] 4.2 Add per-row accept and decline events to `ChatsContract` / `ChatsViewModel`, reusing the existing bulk accept and deferred-undo leave paths so there is one implementation of each action. Test: `ChatsViewModelTest` for row accept moving the convo between segments, and row decline deferring with undo.
- [x] 4.3 Track per-row in-flight state so a row's actions disable while its call is outstanding and other rows stay interactive. Test: `ChatsViewModelTest` asserting two concurrent row actions track independently.
- [x] 4.4 Give each row action an accessibility label naming the action and its conversation. Test: `ConvoListItem` unit/screenshot test asserting the labels, following the existing convention of matching the on-click label rather than the content description.

## 5. Verification

- [ ] 5.1 Run `./gradlew jacocoTestReportAggregated` — the CI task; the root `testDebugUnitTest` skips flavored modules and `:feature:chats:impl` is flavored.
- [ ] 5.2 Compile both flavors (`compileBenchDebugKotlin` and `compileProductionDebugKotlin`) — the bench source set has its own chat fakes that the mapper change can break without the production build noticing.
- [ ] 5.3 Regenerate and review the affected screenshot baselines, confirming the new images differ from the accepted-conversation baselines rather than matching them.
- [ ] 5.4 Device pass on the foldable against a real pending request: open it, confirm no composer, accept, confirm the composer appears and the conversation leaves the Requests segment. This is the only step that exercises the real `acceptConvo` response.
