# Tasks — add-dm-background-notifications (bead nubecita-1fy.15)

## 1. Dependencies + WorkManager/Hilt bootstrap
- [x] 1.1 Add `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work` (+ KSP `androidx.hilt:hilt-compiler`) to the version catalog and `:feature:chats:impl`; add `androidx.work:work-testing` as `androidTestImplementation`.
- [x] 1.2 Disable the default `androidx.startup` `WorkManagerInitializer` in the merged manifest; make `NubecitaApplication` implement `Configuration.Provider` returning a `HiltWorkerFactory`. Verify bench flavor still builds.

## 2. Repository: getLog cursor + persistence
- [x] 2.1 Add `ChatRepository.getLog(cursor: String?)` wrapping `chat.bsky.convo.getLog`; return events + next cursor.
- [x] 2.2 Persist the per-account cursor in `:core:preferences` (DataStore); read/write through a single accessor.

## 3. Pure detection + mapping logic (unit-testable)
- [x] 3.1 Pure function: getLog events + viewerDid + last cursor + set of still-unread convoIds → list of new inbound message-creates to notify (sender ≠ viewer, after cursor, **convo still unread** per the read-state filter, **capped to a max per run** to handle large backlogs) + the advanced cursor.
- [x] 3.2 Pure function: message → notification content (sender name, snippet, deleted/attachment handling mirroring v1's ConvoMapper).

## 4. Worker
- [x] 4.1 `@HiltWorker class DmPollWorker @AssistedInject` over a thin `doWork()` that: checks signed-in + opt-in, runs §3 logic via the repo, posts notifications (§5) unless foregrounded (§6), advances the cursor, returns success/retry. (Orchestration extracted to a JVM-unit-tested `DmPollRunner`; `DmNotifier` is a seam — real `MessagingStyle` impl in §5.)
- [x] 4.2 Auth/refresh through `:core:auth`'s session store with the existing refresh mutex (no rotation race). (All worker network goes through `ChatRepository`'s `XrpcClientProvider.authenticated()`.)

## 5. Notification + deep-link
- [x] 5.1 "Messages" notification channel (lazy idempotent install); per-convo stable notification id; group + summary. (`MessagingStyleDmNotifier` replaces the seam binding; small icon via `@DmNotificationSmallIcon` from `:app`.)
- [x] 5.2 Tap PendingIntent → deep-link to the convo (`nubecita://chat/{otherUserDid}` → `Chat` via `ChatDeepLinkModule`'s `uriDeepLinkMatcher` + `:app` manifest filter; `MainActivity.handleIntent` routes to `DeepLinkRouter`).

## 6. Foreground suppression
- [x] 6.1 At run time, read `ProcessLifecycleOwner` state; when foregrounded, skip posting **and do not advance the cursor** (hold it so the next background run re-evaluates the events through the read-state filter — design D5). (Via the `AppForegroundSignal` seam, checked before any network in `DmPollRunner`.)

## 7. Scheduling + lifecycle
- [x] 7.1 Scheduler: `enqueueUniquePeriodicWork(KEEP)` (15-min floor, `NetworkType.CONNECTED`) on (opt-in ∧ signed-in); `cancelUniqueWork` on opt-out / logout. (`WorkManagerDmWorkScheduler` behind the `DmWorkScheduler` seam.)
- [x] 7.2 Wire registration via a production-flavor `AppInitializer` + reactions to `SessionStateProvider` and the preference flow (bench inert). (`DmPollScheduler.start()` via `@IntoSet` in `ProductionBootstrapModule`; reactive decision JVM-unit-tested.)

## 8. Settings toggle (gates BOTH pollers — design D6)
- [x] 8.1 Add a `messageCheckingEnabled` (default true) preference to `:core:preferences` (DataStore) with a reactive flow. (`MessageCheckingPreference`, dedicated accessor; gates `DmPollRunner` already.)
- [x] 8.2 Add an in-app `Switch` row in `:feature:settings` (distinct from the existing OS-notification-settings deep-link row). Honest copy: off ⇒ no DM notifications AND no unread badge ("Nubecita stops checking for new messages"). (`SettingsRow.Toggle` in the Notifications section, bound to `MessageCheckingPreference`.)
- [x] 8.3 Gate v2 worker registration (§7) on (toggle ON ∧ signed-in); cancel on opt-out. (Done in §7's `DmPollScheduler`.)
- [x] 8.4 **Modify shipped v1**: gate `ChatsUnreadPollingObserver` (nubecita-1fy.14) on the same toggle — skip the foreground poll when off (badge stops updating). Update its tests for the new gate. (Poll gated on `enabled`; a collector clears the badge on disable.)
- [x] 8.5 Read-state filter (replaces a manual getLog-cursor bump, which a single global cursor can't express): the worker fetches still-unread convoIds (e.g. from `listConvos`/cache) and notifies only inbound events whose convo is still unread. Foreground reads already clear server unread via `chat.bsky.convo.updateRead` (`nubecita-1fy.18`), so a thread the user opened is filtered out on the next poll — no re-notification. (`DmPollRunner` derives the unread set from `refreshConvos`/`observeConvos` and passes it to `toDmNotifyPlan`.)

## 9. Tests
- [x] 9.1 JVM unit tests for §3.1 (cursor advance, sender≠viewer filter, read-state filter excludes already-read convos, per-run cap, dedup, empty) and §3.2 (content mapping incl. deleted/attachment), via `MainDispatcherExtension`. Add a §6 test: a foreground-suppressed run posts nothing and leaves the cursor unchanged. (`DmNotificationLogicTest`, `DmPollRunnerTest` incl. the foreground no-advance case, `ChatLogMapperTest`.)
- [x] 9.2 Instrumentation tests with `androidx.work:work-testing`: `WorkManagerTestInitHelper` + `SynchronousExecutor` to assert the periodic work is enqueued with `NetworkType.CONNECTED`; `TestListenableWorkerBuilder<DmPollWorker>(context).build().doWork()` via the Hilt factory asserting result (signed-out → success, no network). `DmPollWorkInstrumentationTest`. Needs the `run-instrumented` PR label.
- [x] 9.3 Settings toggle unit test (mirror + persist — `SettingsViewModelTest`); deep-link matcher test (DM notification URI → `Chat` — `ChatDeepLinkMatcherTest`); scheduler schedule/cancel unit test (`DmPollSchedulerTest`).

## 10. Verification
- [ ] 10.1 `:app:assembleDebug`, `:feature:chats:impl` + `:feature:settings:impl` + `:app` lint, unit tests; manual on-device check via `adb shell cmd jobscheduler run -f <pkg> <jobId>` + `dumpsys deviceidle force-idle` to confirm Doze cooperation and no battery-policy violations.
- [ ] 10.2 Update README "Versioning"-style docs if a new notification channel is user-visible; confirm no banned battery techniques crept in.
