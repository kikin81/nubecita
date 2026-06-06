## 1. Investigation (gates the mechanism)

- [ ] 1.1 Resolve the Material3 FIRST-vs-LAST `detailPane()` behavior: read `androidx.compose.material3.adaptive.navigation3` `ListDetailSceneStrategy` source and confirm on the live Pixel Tablet emulator whether a second `detailPane()` entry stacks (renders last) or is ignored (renders first). Record the finding in `design.md` (D3). Decides D2a vs D2b.
- [ ] 1.2 Confirm how the active-tab segment is recoverable at scene-strategy time: verify `MainShellNavState` can expose the active tab boundary (`topLevelKey` + per-tab stack) without breaking the concatenated `backStack` that predictive/system-back relies on. No code change yet — just confirm the seam.

## 2. Core fix — active-tab-scoped positional pane strategy

- [ ] 2.1 Add a positional list-detail scene strategy under `app/.../shell/adaptive/` (alongside `AdaptiveDialogSceneStrategy`) that buckets the **active tab's segment** into `(listAnchor = first listPane entry, detailRegion = entries above it)` and renders the detail-region top in the detail pane, ignoring later `listPane()` metadata. Unit test: pure bucketing function over representative stacks (`[Feed,PostDetail,Profile]`, `[Search,PostDetail]`, `[Chats]`, cross-tab `[Feed,PostDetail,Chats]`) asserting the chosen anchor + detail entry.
- [ ] 2.2 Wire the strategy into `MainShell`'s inner `NavDisplay.sceneStrategies` (overlay/dialog strategy first, then the list-detail strategy); ensure the active-tab segment is passed in. Update the existing `app-navigation-shell` unit/instrumentation that asserts the `sceneStrategies` shape (the "Inner NavDisplay applies a ListDetailSceneStrategy" scenarios — now a list-detail strategy possibly behind the overlay).
- [ ] 2.3 Verify Feed, Search, and Chats surfaces all obey the rule through the single inner `NavDisplay` (no per-surface code expected — confirm and add a unit assertion per surface).

## 3. Quirk-specific behavior + regression guards

- [ ] 3.1 Quirk 1: author-tap-from-detail keeps the list. Unit test over the bucketing function for `[Feed, PostDetail, Profile]` → anchor Feed, detail Profile; and update/add a screenshot test for the Feed | Profile pane state on Expanded.
- [ ] 3.2 Quirk 2: tab switch resets the detail pane. Unit test for active-tab scoping (`[Feed, PostDetail(A), Chats]` with active tab Chats → anchor Chats, detail = Chats placeholder, NOT PostDetail(A)); screenshot test for Chats | placeholder.
- [ ] 3.3 Back behavior + tab-restore regressions: assert Back from `[Feed, PostDetail, Profile]` returns to Feed | PostDetail, and returning to a tab restores its preserved detail entry. Keep all existing `app-navigation-shell` scenarios green (tab switch, process-death restore, "exit through home", placeholders, `Report` overlay).
- [ ] 3.4 Compact-width unchanged: assert the same pushes stack full-screen single-pane on Compact (screenshot or unit on the strategy's compact path).

## 4. Bench test fidelity + end-to-end repro

- [ ] 4.1 (Optional, bundled) Make `feature/profile/impl/src/bench/.../data/BenchFakeProfileRepository.kt` resolve per-handle fixtures (at least the bench thread authors `jess.trails`, `gabe.climbs`, `carmen.alpine`) instead of always returning the static `bench.nubecita.app` profile. Unit test: `fetchHeader("jess.trails")` returns the Jess fixture; unknown handle falls back gracefully.
- [ ] 4.2 Instrumented bench test (Pixel Tablet / Expanded) reproducing the exact session repro: launch → tap a feed post → tap the author avatar → assert the Feed list pane is still present (Quirk 1); then re-establish and switch to Chats → assert the detail pane shows the Chats placeholder, not the prior post (Quirk 2). Gate with the `run-instrumented` PR label.

## 5. Docs

- [ ] 5.1 Update `docs/adaptive-layouts.md`: document the active-tab-scoped positional detail-stacking rule (both quirks), and record the Nav3 three-pane "push" as the eventual north star / explicit v1 non-goal.
