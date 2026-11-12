# Tasks

Epic `nubecita-hzfs`, landed as one `gh stack` (one PR per group). Step detail: `docs/superpowers/plans/2026-11-12-wear-launcher-widget.md`.

## 1. Launch targets (nubecita-hzfs.1)

- [ ] 1.1 Add `WearLaunchTarget`, `launchTargetOf`, `initialBackStack`, `resolvePendingNewPost` and `launchIntent`; verify `WearLaunchTest` (JUnit 5) passes, including "restoring ignores the action" and "posting unavailable drops New post".
- [ ] 1.2 Wire `MainActivity` (`ACTION_OPEN_FEED`, `ACTION_NEW_POST`), the start back stack and the landing entry's pending New post; move the complication's tap to `launchIntent`; verify on the Wear emulator that each action lands on its destination with the landing screen underneath, and that a kill + relaunch after New post doesn't reopen the input.

## 2. Launcher widget (nubecita-hzfs.2)

- [ ] 2.1 Add Glance Wear / Remote Compose dependencies and the `:wear:app` Robolectric harness; verify `LaunchIntentTest` (Robolectric) passes and the existing JUnit 5 tests still run in the same task.
- [ ] 2.2 Add `LauncherWidget`, `LauncherWidgetService` (widget + tile protocols), provider XML, strings in three locales and the layout; verify `LauncherWidgetRenderTest` serializes the no-data and "3" states.
- [ ] 2.3 Render the 464×288 picker preview from the real composable; verify on the Wear emulators that the surface can be added and each tile opens its destination.

## 3. Live unread count (nubecita-hzfs.3)

- [ ] 3.1 Add `launcherWidgetModel` and back the widget with `WatchDmRepository`; verify `LauncherWidgetModelTest` (null, 0, 1, 99, 100) and the render test for 0 and 100.
- [ ] 3.2 Add `LauncherWidgetUpdater` (`DmSyncObserver`) and pin that clearing DMs notifies observers (`WatchSyncIngesterTest`); verify on the bench build that a sync updates the surface without opening the app.

## 4. Release readiness (nubecita-hzfs.4)

- [ ] 4.1 Verify the bench build renders the surface offline, and check `WO-V9` / `WO-V10` with screenshots attached to the PR.
- [ ] 4.2 Mention the widget in the Wear Play listing (source and localized locales) and verify the metadata lint/validation passes.
- [ ] 4.3 Top-of-stack gate: `:app:assembleDebug`, `:wear:app:assembleBenchDebug`, touched modules' lint, compose-expert review of `git diff origin/main...HEAD`, and the full emulator journey (picker → each tile → back → landing; sign-out → no count).

## Workflow follow-up

- After `gh stack merge`, close `nubecita-hzfs.1`–`.4` and the epic, then archive this change.
