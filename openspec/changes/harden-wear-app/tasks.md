# Tasks

Three epics, each landed as one `gh stack` (one PR per group, in order): **A** `nubecita-z9s4` (groups 1–11), **B** `nubecita-8x3y` (groups 12–17), **C** `nubecita-8ynp` (groups 18–21). Audit evidence with file and line is in each bd issue. Every group also meets the definition of done in `docs/wear-testing.md` (written in group 2): flavored lint and unit tests, a bench build installed to the emulator by serial, baselines from the `update-baselines` bot, and the journeys for the touched area.

## 1. Bench scenarios (nubecita-z9s4.1)

- [ ] 1.1 Add a bench-only scenario holder set from a `MainActivity` launch extra and read by the `Bench*` fakes (session, phone status, capabilities, feeds, DMs, post and DM outcomes, action delay); verify a new JVM test parses every scenario name and rejects an unknown one, and that `src/production` has no reference to it (`git grep`).
- [ ] 1.2 Keep a posted text at the top of the bench Following feed (pending for at least 10 s, then posted) and stop bench from calling the real Data Layer on resume; verify on the emulator that each scenario shows its screen via `am start … --es scenario <name>` and that `WearMarketingScreenshotJourney` still compiles (`:wear:app:compileBenchDebugAndroidTestKotlin`).

## 2. Journeys and definition of done (nubecita-z9s4.2)

- [ ] 2.1 Add `wear/app/src/journeysTest/` with the nine journeys (launch and landing, feed browse, post actions, messages and quick reply, new post by keyboard, widget deep links, swipe-dismiss back stack, screen-off state retention, gate states); verify each is evaluated on `Wear_OS_Large_Round` with the bench build and its result file is attached to the PR, with any failing step filed as a bug.
- [ ] 2.2 Write `docs/wear-testing.md` (device pass commands, AVD recipe for small round Wear OS 7 and a Wear OS 6 image, launch actions and scenarios, key-tap text entry, definition of done) and link it from `CLAUDE.md`; verify every documented command runs as written against the emulator serial.

## 3. CI and dependency guardrails (nubecita-z9s4.3)

- [ ] 3.1 Add `:wear:app:assembleProductionRelease -PdebugSignedRelease=true` to the pull-request build job and `:wear:data` to the nightly instrumented matrix; verify the step is green and `aapt2 dump resources` on its APK lists `android_wear_capabilities`.
- [ ] 3.2 Add the non-automerged `wear-prerelease` Renovate rule; verify `npx renovate-config-validator .github/renovate.json` passes.
- [ ] 3.3 Swap to `androidx.hilt:hilt-lifecycle-viewmodel-compose`, remove `ACCESS_NETWORK_STATE` from the watch manifest, bump Robolectric to 4.18 and correct the "target ≥ 34" comment and spec line; verify `:wear:app:testProductionDebugUnitTest` passes, the APK has no `androidx.navigation_` entries, and images still load on the emulator.

## 4. Screenshot coverage (nubecita-z9s4.4)

- [ ] 4.1 Add a small-round, largest-font multipreview in `b+es+419` and `pt-rBR` and apply it to the text-bearing screens; verify `:wear:app:compileProductionDebugScreenshotTestKotlin` and that each new baseline is hash-distinct from its English sibling.
- [ ] 4.2 Add a screenshot-only app-scaffold wrapper with a fixed clock for landing, feed, post, thread and review as they open, plus the missing states (single-feed header, empty conversation list, video and sensitive-media cards, attachment-only message, Open-on-phone dialog, review with the Post label) and missing font-scale variants; verify CI's `screenshot` job is green on bot-generated baselines and list in the PR which baselines pin a known defect.

## 5. Thread follows the newest message (nubecita-z9s4.5)

- [ ] 5.1 Reverse the thread layout so it opens on and stays at the newest message, with the title visible on short threads; verify a new preview with a pending reply shows the bubble and Reply fully visible on small and large round, and the messages journey passes.

## 6. Edge buttons and end-of-list padding (nubecita-z9s4.6)

- [ ] 6.1 Make Reply and Send scrollable, use the scaffold overload without an edge button when there is none, and add minimum vertical content padding to first and last items and the action row; verify the gate, post and feed baselines at the largest font show nothing cut by the edge, and that dragging from Reply and Send scrolls on the emulator.
- [ ] 6.2 Open the review screen on its text and make the "Open on phone" label fit on small round at the largest font in all three locales; verify the review and post baselines (en, es-419, pt-BR) and the new-post journey.

## 7. Transformations and the clock (nubecita-z9s4.7)

- [ ] 7.1 Remove the full-list fade from all-surface lists, build message bubbles on the library card with surface transformation, and apply content transformation to plain text where it reads better; verify picker and reaction baselines show the second row opaque, and long post text is not cropped on the emulator.
- [ ] 7.2 Use the system status bar instead of a custom time text, pin the clock through the time-source local in bench and screenshots, and delete both `WearTimeText` shims; verify the clock baselines show no content under the time, one clock on Wear OS 7, and the time still shown on a Wear OS 6 image.

## 8. Brand colour scheme (nubecita-z9s4.8)

- [ ] 8.1 Generate all colour roles from the Sky seed for the watch theme and the widget fallback; verify a unit test asserts AA contrast for each on-colour and surface pair in use, and that the PR's baseline diff is colour-only.

## 9. Counts, button groups and large-font cards (nubecita-z9s4.9)

- [ ] 9.1 Replace glyph counts with icon-and-number pairs and move the reaction grid and post action row to button groups with transformation; verify the counts and reaction baselines, and the post-actions journey.
- [ ] 9.2 Keep two lines of text and the author on feed cards at the largest font, collapse blank lines in card text, widen the landing group, replace fixed dp with percentages and theme shapes, and unify the es-419 repost wording; verify feed baselines at 1.24, a pressed-state check on the emulator, and a unit test for the blank-line collapse.

## 10. Ambient mode (nubecita-z9s4.10)

- [ ] 10.1 Provide the ambient mode manager at the root, render the ambient variants (no images, outlines, ambient tick), and gate the relative-time ticker on resumed and interactive; verify the ticker test covers ambient, ambient previews exist for feed and post, and the screen-off journey passes with an ambient screenshot attached.

## 11. Open-on-phone result and gate loading (nubecita-z9s4.11)

- [ ] 11.1 Await the remote-activity result and route success or failure through a ViewModel effect; show progress after a brief delay in the gate's loading state and fall back to signed-out on failure; verify ViewModel tests for success, failure, slow and throwing phone connections, previews for the failure dialog and loading state, and the gate journey.
- [ ] 11.2 Top-of-stack gate for epic A: `:app:assembleDebug`, `:wear:app:assembleBenchDebug`, `:wear:app:assembleProductionRelease -PdebugSignedRelease=true`, touched modules' lint, compose-expert review of `git diff origin/main...HEAD`, and all nine journeys on large and small round.

## 12. Skip unchanged republishes (nubecita-8x3y.1)

- [ ] 12.1 Compare the new feed snapshot with the last published one, ignoring the publish time, and skip encode and put when equal; verify `DefaultWearFeedSyncTest` covers "unchanged → no encode, no put" and a per-field mutation case, and update the existing "second open republishes" test on purpose.
- [ ] 12.2 Rate-limit the watch's sync request with a monotonic cooldown; verify a `WearGateViewModelTest` case, and on a paired device compare `dumpsys activity service WearableService` counts before and after five opens.

## 13. Session resolution and refresh caller (nubecita-8x3y.2)

- [ ] 13.1 Refresh the session provider when it is still loading before handling a watch action or sync request, and add `RefreshCaller.WearAction`; verify dispatcher and handler tests with a provider that resolves on refresh, the monitor mapping test, and that the bench flavor compiles.

## 14. Action retry (nubecita-8x3y.3)

- [ ] 14.1 Catch Data Layer failures in the action handler, keep the item on connectivity-class failures, drain leftovers on sync request and at the end of the widget-refresh run, and guard with a per-action started marker; verify dispatcher tests for kept item, drain, and no double send.
- [ ] 14.2 Clear watch pending rows that have neither a request nor a result, and add AutoMigrations for the three pending tables; verify an ingester test, a migration test with a failed post, the committed schema JSON, and a paired-device pass with the phone in airplane mode.

## 15. Content-addressed images (nubecita-8x3y.4)

- [ ] 15.1 Store watch images by content in one directory per account, skip existing files, and prune unreferenced ones after the database switch; verify an ingester test where a repeated snapshot loads no asset, a store test for pruning, and a device check that one periodic sync writes no image file.

## 16. Catch-up and surface refreshes (nubecita-8x3y.5)

- [ ] 16.1 Run catch-up once per process off the main thread, carry the publish time outside the JSON, and serialize ingest; verify a two-coroutine ingester test and no StrictMode disk read on resume in a debug build.
- [ ] 16.2 Notify the widget and complication only on a changed unread total or signed-in state, finishing the refresh inside the listener; verify "same session twice → one notification" and the existing updater tests.

## 17. Payload bound and wire fixtures (nubecita-8x3y.6)

- [ ] 17.1 Bound the DM snapshot below the item size limit and add golden JSON fixtures for contract v1; verify the max-length test and that renaming a field makes a fixture test fail (checked once by mutation).
- [ ] 17.2 Top-of-stack gate for epic B: `:app:assembleDebug`, `:wear:app:assembleBenchDebug`, flavored unit tests for `:core:wear-sync`, `:wear:data` and `:wear:app`, and one paired phone-and-watch pass (open, like, reply, airplane-mode like).

## 18. Widget background, session and targets (nubecita-8ynp.1)

- [ ] 18.1 Branch the document background on container type, read signed-in state from the watch's own store with a timeout floor, and give every target 48dp with merged semantics; verify the render test for a never-completing session source, round and squircle baselines without a black container, and an `android run --type=WEAR_WIDGET` capture on the Wear OS 7 emulator.

## 19. Small container and previews (nubecita-8ynp.2)

- [ ] 19.1 Add the SMALL container as one tappable Messages row and switch previews to the all-sizes providers for squircle, round and rectangular; verify a render test for the small container, all six shape and size baselines, and the widget journey in both sizes.
- [ ] 19.2 Generate the 448×168 and 464×288 picker images and a 400×400 tile preview from the previews and add a test that the shipped images match; verify the test fails when a shipped image is altered, and add signed-out, none, never-synced and 99+ widget-form baselines in es-419 and pt-BR.

## 20. Complication (nubecita-8ynp.3)

- [ ] 20.1 Add the long-text type, the tappable no-data state, an ambient-safe image, and a distinct small image (or drop it); verify a Robolectric test per type and for no data, and attach emulator captures of each type or state plainly that the emulator could not host them.

## 21. Watch startup profile (nubecita-8ynp.4)

- [ ] 21.1 Add `:wear:benchmark` (with `consumer-rules.pro` and a module-map row) and generate the baseline and startup profiles from the bench journey; verify the committed profile contains `net.kikin.nubecita.wear` rules and the startup timing for no compilation versus profile compilation is recorded in the PR.
- [ ] 21.2 Top-of-stack gate for epic C: `:app:assembleDebug`, `:wear:app:assembleBenchDebug`, the release build, touched modules' lint, compose-expert review of the cumulative diff, and the widget and landing journeys on large and small round.

## Workflow follow-up

- After each `gh stack merge`, close that epic's children and the epic, and confirm exactly one `Release` run.
- Archive `add-wear-launcher-widget` and `add-wear-os-app` before this change, so its requirements merge into existing specs.
- After epic C merges, archive this change and reconcile "Pull on open refreshes feeds without new background work" with design D10.
- The tile-renderer check on a physical Wear OS 4/5 watch is tracked separately as `nubecita-zc6v`.
