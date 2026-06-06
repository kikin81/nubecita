## 1. Investigation (gates the mechanism)

- [x] 1.1 Resolved the Material3 pane logic from `ListDetailSceneStrategy` source: `calculateScene` walks the contiguous metadata run ending at the top entry and renders **last-of-each-role** (so nested `detailPane()` stacks correctly — last wins), but does NOT scope to the active tab. This made mechanism (B) viable — tag the sub-route `detailPane()` rather than rewriting roles. Recorded in `design.md`.
- [x] 1.2 Confirmed the seam: `NavEntry.key`/`content` are private (can't rewrite metadata post-hoc), but the `entry(metadata = { key -> })` DSL allows instance-dependent metadata, and `MainShellNavState` can expose `activeSegmentStartIndex` without touching the concatenated `backStack` that back relies on.

## 2. Core fix

- [x] 2.1 `ActiveTabScopedSceneStrategy` (`app/.../shell/adaptive/`) wraps the list-detail strategy and slices entries to the active tab's segment before delegating. Unit test: `ActiveTabScopedSceneStrategyTest` (4 cases — index-0 passthrough, non-start drop, scene passthrough, out-of-range clamp).
- [x] 2.2 Wired into `MainShell`'s inner `NavDisplay.sceneStrategies` (dialog overlay first, then the wrapped list-detail strategy). Existing `:app` nav unit tests stay green.
- [x] 2.3 Feed/Search/Chats share the one inner `NavDisplay`, so the wrapper covers all three — verified on-device (Feed, Chats) and by the active-tab-scoping unit test.
- [x] 2.4 Quirk 1 mechanism: `profilePaneMetadata` makes `Profile`'s role instance-dependent (`handle == null` → `listPane`; else `detailPane`) via the per-key `entry(metadata = { … })` DSL. Unit test: `ProfilePaneMetadataTest`.

## 3. Regression guards

- [x] 3.1 Quirk 1 (author-tap keeps the list) — battle-tested on the Pixel Tablet; `ProfilePaneMetadataTest` pins the role decision.
- [x] 3.2 Quirk 2 (tab switch resets the detail) — battle-tested on the Pixel Tablet (Chats shows its own placeholder); `MainShellNavStateTest.activeSegmentStartIndex` + `ActiveTabScopedSceneStrategyTest` pin the slice math.
- [x] 3.3 Back + tab-restore — verified on-device (Back from Feed|Profile → Feed|PostDetail; Chats↔Feed restore); all 16 `MainShellNavStateTest` cases green.
- [x] 3.4 Compact-width unchanged — verified on the phone emulator (PostDetail then Profile both full-screen single-pane; Back intact).
- [ ] 3.5 (Follow-up) Add `:app` screenshot tests for the Feed|Profile and Chats|placeholder pane states (needs committed baselines via the `update-baselines` CI path; `:app` screenshots are not CI-validated today — see the `:app screenshot CI gap`).

## 4. Bench test fidelity + end-to-end repro (follow-up)

- [ ] 4.1 (Optional) Make `feature/profile/impl/src/bench/.../data/BenchFakeProfileRepository.kt` resolve per-handle fixtures (bench thread authors `jess.trails`, `gabe.climbs`, `carmen.alpine`) instead of always returning the static `bench.nubecita.app` profile, so a bench author-tap shows the right profile. Unit test on the resolution.
- [ ] 4.2 (Follow-up) Instrumented bench test (Pixel Tablet / Expanded) reproducing launch → tap post → tap author (assert list pane survives) and tab-switch (assert active tab's placeholder). Gate with the `run-instrumented` PR label.

## 5. Docs

- [x] 5.1 `docs/adaptive-layouts.md` — added the "List-detail pane scoping" section (active-tab scoping + instance-dependent sub-route role) and the three-pane "push" north star as the explicit v1 non-goal.
