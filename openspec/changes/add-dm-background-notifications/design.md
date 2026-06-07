## Context

v1 (`nubecita-1fy.14`, shipped) added a foreground-only Chats unread badge + in-row unread via a `ProcessLifecycle` poller — no closed-app awareness. Real FCM push is deferred (`nubecita-1fy.13`) because Bluesky DMs are private (`chat.bsky.convo`, not on Jetstream), so a server gateway would have to custody full-account credentials per user. The credential-free path is to let the **device** poll its own inbox (the session is already on-device) and post **local** notifications when backgrounded.

The non-negotiable constraint is battery: stay within Google's WorkManager guidelines and cooperate with Doze / App Standby. The accepted UX bar is "best-effort, may be delayed."

## Goals / Non-Goals

**Goals:**
- Notify the user of new inbound DMs when the app is backgrounded, credential-free and gateway-free.
- Cooperate with the OS battery model; zero anti-Doze workarounds.
- Reuse v1's unread/cursor plumbing; never double-notify over v1's in-app surface.
- Both JVM unit tests and `androidx.work:work-testing` instrumentation tests.

**Non-Goals:**
- Real-time / push delivery (that's the deferred server-FCM path, `nubecita-1fy.13`).
- Reliable delivery on battery-hostile OEMs — explicitly best-effort.
- In-thread live message streaming (separate concern).
- Reactions / typing indicators / read receipts.
- **Inline Direct Reply** (`RemoteInput` from the notification) — a clean follow-up on the `MessagingStyle` base (`nubecita-1fy.17`), not this change.

## Decisions

### D1. Periodic WorkManager, Doze-cooperative, never fought
`PeriodicWorkRequest` at the platform floor (15 min) with `setConstraints(NetworkType.CONNECTED)`. Doze/App Standby batching is **accepted** (notifications may be hours late on an idle device). **Banned** (hard rule, see `battery-top-priority-no-doze-workarounds`): foreground service for cadence, exact alarms (`setExactAndAllowWhileIdle`), wakelocks, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` / battery-exemption nags, expedited work for the periodic poll. No `setExpedited`. The worker returns `Result.success()`/`retry()` and lets WorkManager's default backoff handle transient failures.

### D2. @HiltWorker + HiltWorkerFactory (the worker needs DI)
The worker needs `ChatRepository`, the cursor store, and `:core:auth`. Use `@HiltWorker` + `@AssistedInject(@Assisted Context, @Assisted WorkerParameters, …)`, a `HiltWorkerFactory`, and a `Configuration.Provider` on `NubecitaApplication` — and **disable the default `WorkManagerInitializer`** in the manifest so the Hilt factory is used. Deps: `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work` (+ KSP `androidx.hilt:hilt-compiler`).

### D3. Change detection via `chat.bsky.convo.getLog` cursor (not listConvos diff)
`getLog` returns an ordered, cursored event log; it's the right primitive for "what's new since last time." Persist one cursor per account in `:core:preferences` (DataStore). On each run: fetch from the stored cursor, keep only **inbound** message-create events (sender ≠ viewer), post notifications, then persist the new cursor **after** a successful post attempt (at-least-once; the client/notification id dedups a re-post). `listConvos` (v1's source) gives counts but not per-message identity, so it's wrong for per-message notifications.

### D4. Notification granularity: **per-convo**, grouped, MessagingStyle
One notification per conversation, keyed by a stable id derived from `convoId` (a later message in the same convo updates the existing notification rather than stacking). Group all under the "Messages" channel with a `MessagingStyle` + group summary. Rationale: matches Messages/WhatsApp, avoids per-message spam. Using `MessagingStyle` also keeps the door open for **inline Direct Reply** (`RemoteInput`) as an isolated follow-up (`nubecita-1fy.17`) — out of scope here.

### D5. Foreground suppression
While the app is foregrounded, v1's in-app surface already reflects unread, so the worker MUST NOT post. The worker checks an app-foreground signal (`ProcessLifecycleOwner` current state, read at run time) and skips posting (still advances the cursor) when foregrounded. WorkManager rarely runs in the foreground anyway; this is the correctness guard for the overlap window.

### D6. One "message checking" toggle gating BOTH pollers, default ON
A single user-facing in-app `Switch` in `:feature:settings` (distinct from the existing row that deep-links to the OS notification-channel settings) persisted in `:core:preferences`, default **ON**. When **OFF it disables ALL message-checking** — both v1's foreground `ChatsUnreadPollingObserver` *and* v2's background worker — so a user who doesn't DM pays zero polling/battery cost. This means:
- v2: the periodic work is registered only when (toggle ON ∧ signed-in); cancelled on opt-out.
- v1 (modifies the shipped `nubecita-1fy.14` `ChatsUnreadPollingObserver`): the foreground poll is additionally gated on the toggle; OFF ⇒ no foreground refresh, so the unread **badge stops updating** too.
- Honest copy: turning it off means **no DM notifications AND no unread badge** (Nubecita stops checking for new messages entirely) — not just "no background notifications." Default ON because a chat app is expected to surface new messages; the switch is the escape hatch for non-chatters / battery-sensitive users.

### D7. Notification content + tap target
Show sender display name + message text snippet (deleted/attachment handled like v1's row mapping), respecting OS lock-screen privacy controls (no app-level redaction in v1 of this feature). Tapping deep-links to the convo: a new `uriDeepLinkMatcher` → `Chat(otherUserDid)` (or a convo route), via the existing `DeepLinkRouter` + `MainActivity` handoff, mirroring the social-notification deep-link.

### D8. Registration lifecycle
`enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)` on (sign-in ∧ opt-in); `cancelUniqueWork` on logout or opt-out. Driven from a production-flavor `AppInitializer` + reactions to `SessionStateProvider` / the preference flow (bench stays inert).

### D9. Background auth = same on-device session, refreshed safely
The worker authenticates with the user's existing session via `:core:auth`'s `XrpcClientProvider`. Token refresh MUST go through `:core:auth`'s session store as the single source of truth with a refresh mutex — atproto refresh tokens are single-use/rotating, and a background refresh racing the foreground app would otherwise log the user out.

### D10. Testing strategy (both layers)
- **JVM unit**: extract pure logic — (a) `getLog` events → "new inbound messages to notify" (cursor advance, sender≠viewer filter, dedup), (b) message → notification content mapping — and test on the JVM with `MainDispatcherExtension`. Keep `doWork()` a thin orchestrator over these.
- **Instrumentation (`androidx.work:work-testing`)**: `WorkManagerTestInitHelper` + `SynchronousExecutor` to enqueue the periodic worker and assert it's scheduled with `NetworkType.CONNECTED`; `TestDriver.setPeriodDelayMet`/`setAllConstraintsMet` to drive a run; `TestListenableWorkerBuilder<DmPollWorker>(context).build().doWork()` against a fake `ChatRepository` to assert results + that a notification is posted (and suppressed when foregrounded). Add the `run-instrumented` PR label.

### D11. CLI affordances (debug/QA)
`adb shell cmd jobscheduler run -f <pkg> <jobId>` to force-run without waiting; `adb shell dumpsys jobscheduler | grep <pkg>` to inspect; `adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS -p <pkg>` + logcat for WM state; `adb shell dumpsys deviceidle force-idle` to verify Doze cooperation.

## Risks / Trade-offs

- **OEM background killers (MIUI/Samsung/Huawei/OnePlus/Oppo)** may kill the worker entirely → some users get nothing. Mitigation: honest "best-effort" UX; do NOT add anti-killer nags (battery rule).
- **Doze delay** → notifications can be hours late on idle devices. Accepted (late beats never).
- **Refresh-token rotation race** (background vs foreground) → must funnel through `:core:auth` single-flight or risk logout. Highest-correctness-risk item.
- **getLog pagination / large backlog** → cap events processed per run; coalesce to per-convo notifications so a backlog doesn't spam.
- **Cursor coordination with v1** → if v1 advanced reads (user opened the thread), the worker must not re-notify; cursor + "inbound & unseen" filter handles it.
- **Double-notify window** at the foreground/background boundary → D5's run-time foreground check is the guard.
- **Two background/foreground pollers** (this + notifications) → future coalescing noted in `nubecita-1fy.14`; out of scope here.
