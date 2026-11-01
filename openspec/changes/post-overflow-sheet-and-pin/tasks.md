Epic `nubecita-iwjz`. One `gh stack`, one PR per section 1–3, landed together with
`gh stack merge`.

## 1. Overflow sheet + mapping — `nubecita-iwjz.3` (PR 1, base `main`)

- [ ] 1.1 Add `isPinned` and `isThreadMuted` to `ViewerStateUi`, and a thread-root URI to `PostUi`, in `:data:models`. Update the fixtures. Test: extend the existing `:data:models` fixture tests to cover the new defaults.
- [ ] 1.2 Map `viewerState.pinned`, `viewerState.threadMuted` and `record.reply.root.uri` in `:core:feed-mapping`. Test: mapper unit tests for the four `core-feed-mapping` scenarios (pinned, absent flags, reply root, top-level).
- [ ] 1.3 Add the pure grouped-rows rule function in `:designsystem`, covering own vs others' posts, blank text, and the muted / blocked / thread-muted toggle pairs. Test: unit tests for every `post-overflow-menu` grouping scenario.
- [ ] 1.4 Build the grouped sheet composable (`ModalBottomSheet` + `NubecitaListGroup` cards, label left, icon right). Add any missing Material Symbols glyphs by codepoint. Test: screenshot previews, light and dark, for own-post and others'-post sheets, with the muted, blocked and thread-muted variants.
- [ ] 1.5 Replace `PostOverflowAffordance`'s `DropdownMenu` with the sheet. Compose it only while open. Test: a sheet laziness test replacing `DropdownMenuLazinessTest`; existing `PostCard` baselines must stay unchanged.
- [ ] 1.6 Move `VideoOverflowMenu` onto the shared rule function and sheet, and delete its local rules. Test: update the videos rule and unit tests to assert parity with `PostCard`.
- [ ] 1.7 Update the bench fixtures: one own post with `pinned = true`, one post with `threadMuted = true`. Test: compile the bench variant, then smoke-test on device that the sheet opens on Feed, Post detail, Profile and Videos.

## 2. Real thread mute — `nubecita-iwjz.4` (PR 2, stacked on PR 1)

- [ ] 2.1 Add `ThreadMuteRepository` (`mute(rootUri)` / `unmute(rootUri)` over `MuteThreadRequest` / `UnmuteThreadRequest`) with a production implementation and a bench fake. Test: repository unit tests with a mocked XRPC client.
- [ ] 2.2 Add an optimistic `isThreadMuted` flag to `PostInteractionsCache`, keyed by root URI, with the same in-flight and after-success rule as likes, and rollback. Test: cache unit tests for the flip across the thread, the rollback, and survival of a stale refresh.
- [ ] 2.3 Route `MuteThread` / `UnmuteThread` in `DefaultPostInteractionHandler` off the coming-soon branch (`UnblockAuthor` stays). Add the success and failure snackbar effects and the repeat-tap guard. Test: handler unit tests for muting the root from a reply, failure rollback, and the repeat-tap guard.
- [ ] 2.4 Add the strings "Thread muted", "Thread unmuted", "Couldn't mute thread" and "Couldn't unmute thread" with es-419 and pt-BR. Test: the touched module's own lint (`MissingTranslation`).
- [ ] 2.5 Bench device pass: mute a thread from a reply, confirm the root post's sheet reads "Unmute thread", then unmute. Confirm the persisted state with a screenshot.

## 3. Pin / Unpin your own post — `nubecita-iwjz.2` (PR 3, stacked on PR 2)

- [ ] 3.1 Extract the profile-record read-modify-write (`getRecord` → modify → `putRecord` with `swapRecord`) into `:core:profile`, and make `:feature:profile:impl`'s `updateProfile` reuse it. Test: the existing `DefaultProfileRepository` tests stay green, and new writer tests check that every other field is preserved and `swapRecord` is sent.
- [ ] 3.2 Add a pin writer: `pin(strongRef)` and `unpin(postUri)` with compare-and-clear. Test: writer unit tests for pin, unpin of the pinned post, and unpin when the record points elsewhere (no write).
- [ ] 3.3 Add `PostOverflowAction.PinPost` / `UnpinPost`, and the "Pin to your profile" / "Unpin from profile" group as the first group on own posts in the rule function. Test: rule-function unit tests (own reply is pinnable, pinned shows Unpin, others' posts show no pin row). Screenshot: the own-post sheet, pinned and unpinned.
- [ ] 3.4 Add an optimistic `isPinned` flag to `PostInteractionsCache`: pinning B clears any other pinned post, rollback restores both, and a stale refresh doesn't undo it. Route Pin/Unpin in the handler with the repeat-tap guard and the "Post pinned" / "Post unpinned" / failure snackbars. Test: handler and cache unit tests for the A→B move, rollback, and the repeat-tap guard.
- [ ] 3.5 `ProfileViewModel` sets and clears `ProfileState.pinnedPost` from cache state, with no new event stream. Test: a `ProfileViewModelTest` case where a pin from another surface updates the slot.
- [ ] 3.6 Add the pin strings with es-419 and pt-BR. Test: the touched modules' own lint.
- [ ] 3.7 Full gate on the top branch: `:app:assembleDebug`, every touched module's lint, and the compose-expert review on the cumulative `git diff origin/main...HEAD`. Bench device pass: pin from Feed → Profile shows it pinned and the sheet reads "Unpin" → unpin. Confirm the persisted state with screenshots.
