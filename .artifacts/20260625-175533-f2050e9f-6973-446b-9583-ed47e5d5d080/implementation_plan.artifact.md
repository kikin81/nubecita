# Fix Background DM Notifications Brittleness

Analyze and fix the background DM notification system which currently fails to reliably post notifications. The root cause is a reactive scheduling bug that resets the WorkManager timer on every app launch, combined with filtering logic that may be too strict.

## User Review Required

> [!NOTE]
> I found a critical bug in `DmPollScheduler` where transient `Loading` states during app startup were "breaking" the `distinctUntilChanged` chain, causing `WorkManager` to be re-enqueued (and its 15-minute timer reset) on every single app launch. This explains why notifications "never" appear if the app is used frequently.

## Proposed Changes

### Feature: Chats (Background Poll)

Fix the scheduling churn and improve notification reliability/debuggability.

#### [DmPollScheduler.kt](file:///Users/francisco/code/nubecita-studio-notification-bug/feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/worker/DmPollScheduler.kt)

- Filter out `Decision.IGNORE` before `distinctUntilChanged()` to prevent transient `Loading` states from resetting the `WorkManager` timer.

#### [DmPollRunner.kt](file:///Users/francisco/code/nubecita-studio-notification-bug/feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/worker/DmPollRunner.kt)

- Add more detailed logging for gates (signed-out, disabled, foregrounded) and plan detection.
- Ensure the cursor is always advanced if a `nextCursor` is provided, even on the "first poll" baseline.
- Handle potential `getLog` failures better (re-baseline on persistent errors instead of retrying forever).

#### [MessagingStyleDmNotifier.kt](file:///Users/francisco/code/nubecita-studio-notification-bug/feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/worker/MessagingStyleDmNotifier.kt)

- Fix the bug where new messages in an existing notification would wipe out the previous message history in the `MessagingStyle`. It will now attempt to extract existing messages from the active notification and append the new ones.

#### [NEW] [DmPollDebugReceiver.kt](file:///Users/francisco/code/nubecita-studio-notification-bug/feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/worker/DmPollDebugReceiver.kt)

- Add a debug broadcast receiver that can be triggered via `adb` to run a manual poll and log the results. This allows verifying the logic without waiting for the `WorkManager` 15-minute interval.

#### [AndroidManifest.xml](file:///Users/francisco/code/nubecita-studio-notification-bug/feature/chats/impl/src/main/AndroidManifest.xml)

- Register `DmPollDebugReceiver`.

---

### Tests

#### [DmPollSchedulerTest.kt](file:///Users/francisco/code/nubecita-studio-notification-bug/feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/worker/DmPollSchedulerTest.kt)

- Add a reproduction test case for the `Loading` churn bug and verify it stays fixed.

## Verification Plan

### Automated Tests
- Run updated unit tests for the scheduler:
  `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.worker.DmPollSchedulerTest"`
- Run existing runner tests:
  `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.worker.DmPollRunnerTest"`

### Manual Verification
- Deploy the app to a device.
- Use the new debug receiver to trigger a poll manually:
  `adb shell am broadcast -a net.kikin.nubecita.feature.chats.impl.worker.TRIGGER_POLL -n net.kikin.nubecita/net.kikin.nubecita.feature.chats.impl.worker.DmPollDebugReceiver`
- Verify logcat output for the "DmPoll" tag to see the execution flow.
- Send a message from another account while the app is backgrounded and verify the notification appears.
- Verify that opening and closing the app does NOT reset the `WorkManager` timer (can be checked via `adb shell dumpsys jobscheduler`).
