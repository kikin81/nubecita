# Tasks — add-widget-feed-refresh (bead nubecita-lgoo.2)

> Sub-project B of the Glance feed widgets epic (`nubecita-lgoo`). The Glance-free background refresher for the widget's feed cache. Reuse the DM-notification worker patterns (`feature/chats/impl/.../worker`: `DmPollWorker`/`DmPollRunner`, `DmWorkScheduler` seam, `DmPollScheduler` observer, `AppForegroundSignal`). TDD: failing test first for each unit. Tests: JUnit Jupiter + Turbine + MockK; the real WorkManager wiring is a `work-testing` instrumentation pass (run-instrumented). Battery is the top priority — never fight Doze.

## 1. Module scaffold
- [ ] 1.1 Create `:core:widget-sync` (`nubecita.android.library` + `nubecita.android.hilt`); `settings.gradle.kts` include (alphabetical); namespace `net.kikin.nubecita.core.widgetsync`. Deps: `:core:feed-cache`, `:core:auth`, `:core:common`, WorkManager (`androidx.work.runtime-ktx` + `androidx.hilt:hilt-work`), Hilt. **No `androidx.glance`.** (Add catalog entries for work-runtime/hilt-work if absent.)
- [ ] 1.2 `./gradlew :core:widget-sync:assembleDebug :app:checkSortDependencies` green.

## 2. WidgetUpdater seam (Glance-free)
- [ ] 2.1 `interface WidgetUpdater { suspend fun updateFeedWidgets() }` + `NoOpWidgetUpdater` (does nothing) + a `@Provides`/`@Binds` default binding. Sub-project C overrides with the Glance-backed impl.

## 3. Cache refresh entry point (extend `:core:feed-cache`)
- [ ] 3.1 Add `suspend fun refresh(feedKey: FeedKey): Result<Boolean>` to `FeedRepository` — extract the mediator's REFRESH (fetch page 1 → transactional clear-partition + insert + cursor) into a shared repository method so the worker can refresh a partition WITHOUT a `Pager`/UI. The `Boolean` is `endOfPaginationReached` (empty page or null next-cursor) so `FeedRemoteMediator`'s REFRESH delegates cleanly and still returns `MediatorResult.Success(endOfPaginationReached)` (no behavior change); the worker ignores the boolean (success/failure only). Unit-test: `refresh` success writes page 1 + cursor and returns the end-of-pagination flag; fetch failure returns `Result.failure` and leaves the cache intact.

## 4. Refresh runner (the logic + foreground guard)
- [ ] 4.1 `WidgetRefreshRunner` (plain injectable, mirrors `DmPollRunner`): gates in order — signed-out → `Outcome.SUCCESS` (no-op); `AppForegroundSignal.isForegrounded()` → `Outcome.SUCCESS` (no write, D-B4). Then refresh each MVP `FeedKey` (Following via `FeedKey.following(did)` + fixed Discover) **independently**, calling `repository.trimToCap(feedKey)` after each one that succeeds. Return **`Outcome.RETRY` only if *every* feed failed**; a partial or full success → `Outcome.SUCCESS` (a succeeded feed is never re-fetched by a retry — D-B7). Call `WidgetUpdater.updateFeedWidgets()` when at least one feed succeeded.
- [ ] 4.2 Runner unit tests (MockK fakes): signed-out → no refresh/write; foregrounded → no refresh/write; backgrounded+signed-in → refreshes both feeds, trims each, calls the updater once; a `refresh` failure → `RETRY` and updater NOT called; reuses the `:core:auth` DID for the FeedKey.

## 5. Worker
- [ ] 5.1 `@HiltWorker WidgetRefreshWorker(@Assisted ctx, @Assisted params, runner: WidgetRefreshRunner) : CoroutineWorker` — `doWork()` delegates to `runner.run()` mapped `SUCCESS → Result.success()`, `RETRY → Result.retry()` (mirrors `DmPollWorker`). Picked up by the existing `HiltWorkerFactory`/`Configuration.Provider`.

## 6. Scheduling
- [ ] 6.1 `WidgetWorkScheduler` interface + `WorkManagerWidgetWorkScheduler` (seam over `WorkManager`, on `@IoDispatcher`): `ensureScheduled()` → `enqueueUniquePeriodicWork("widget-feed-refresh-periodic", UPDATE, PeriodicWorkRequest(15, MINUTES, NetworkType.CONNECTED))`; `refreshNow()` → `enqueueUniqueWork("widget-feed-refresh-now", KEEP, OneTimeWorkRequest(NetworkType.CONNECTED))`; `cancel()`. Battery-cooperative: no expedited, no FGS, no wakelocks.
- [ ] 6.2 `WidgetRefreshScheduler` observer (mirrors `DmPollScheduler`): idempotent `start()` that `combine(sessionState)…distinctUntilChanged().collect { signedIn → ensureScheduled() else cancel() }` on an injected scope. JVM-unit-test the schedule/cancel decision against a fake `WidgetWorkScheduler`.

## 7. DI + app wiring
- [ ] 7.1 Hilt module(s) in `:core:widget-sync`: bind `WidgetWorkScheduler` + `WidgetUpdater` (no-op default); provide `WidgetRefreshScheduler` (plain-constructed with the injected scope), mirroring `DmWorkerProvidesModule`.
- [ ] 7.2 Contribute `WidgetRefreshScheduler.start()` to the **production**-flavor `AppInitializer` multibinding (`ProductionBootstrapModule`), mirroring `provideDmPollSchedulerInitializer`. Bench flavor does not contribute it.
- [ ] 7.3 `./gradlew :app:assembleDebug` — Hilt graph + `HiltWorkerFactory` link with the new worker.

## 8. Verification
- [ ] 8.1 `:core:widget-sync` + `:core:feed-cache` unit tests green; root `testDebugUnitTest` green (the `FeedRepository.refresh` addition didn't break A's fakes/tests).
- [ ] 8.2 `./gradlew :core:widget-sync:lintDebug :core:feed-cache:lintDebug spotlessCheck :app:checkSortDependencies` green.
- [ ] 8.3 Work-testing instrumentation (`WorkManagerTestInitHelper` + `TestListenableWorkerBuilder`): the worker runs end-to-end (backgrounded) and refreshes the cache; periodic work is enqueued as unique work with the network constraint. (run-instrumented.)
- [ ] 8.4 Battery audit: confirm no expedited work / foreground service / wakelock / exact alarm; periodic at the 15-min floor with `NetworkType.CONNECTED`; the foreground guard prevents writes while foregrounded. No new always-on work.
