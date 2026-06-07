## Why

Nubecita has no way to tell a user they have a new direct message when the app is closed. v1 (`nubecita-1fy.14`) added a foreground unread badge + in-row unread, but it only updates while the app is open. A messaging surface with zero background notifications churns users — "late beats never" for DMs among friends.

Real *push* (FCM via the gateway) is deferred (`nubecita-1fy.13`): Bluesky DMs are private (`chat.bsky.convo`, not on Jetstream), so a server would have to custody full-account credentials for every user — a privacy/blast-radius escalation we've declined for an indie deployment. The credential-free alternative is to let the **device poll its own inbox** with the session it already holds and post **local** notifications.

The hard constraint is battery: the device-side background work must stay within Google's WorkManager guidelines and **cooperate with** Doze / App Standby rather than fight them. The acceptable bar is "best-effort, may be delayed," never "drains battery / prevents sleep."

## What Changes

- A periodic on-device `WorkManager` worker polls `chat.bsky.convo.getLog` (cursor) using the user's own on-device session (via `:core:auth`) while the app is backgrounded, detects new inbound messages, and posts **local** notifications. No server, no FCM gateway, no stored credentials beyond the device.
- New "Messages" notification channel; tapping a notification deep-links to the convo.
- Per-convo dedup via a persisted `getLog` cursor (carried over from / shared with v1's unread plumbing).
- Foreground suppression: while the app is foregrounded, the worker does not post (v1's in-app surface already shows unread) — no double-notify.
- An opt-in settings toggle ("DM notifications — best-effort, may be delayed") gates registration of the worker.
- Strictly Google-sanctioned background only: periodic `WorkManager` (15-min floor) + `NetworkType.CONNECTED`, single-flight, signed-in-gated. **Banned**: foreground service for polling, exact alarms, wakelocks, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` nags.
- Testing: extracted pure logic covered by JVM unit tests, plus `androidx.work:work-testing` instrumentation tests (`WorkManagerTestInitHelper` + `TestDriver` + `TestListenableWorkerBuilder`).

## Capabilities

### New Capabilities
- `dm-background-notifications`: device-side, credential-free background polling of the Bluesky chat service that posts local DM notifications, cooperating with Doze/App Standby, opt-in, foreground-suppressed.

### Modified Capabilities
<!-- None at the spec/requirement level. The chats unread/cursor plumbing from v1 is reused as implementation, not a requirement change. -->

## Impact

- **New dependencies**: `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work` (+ KSP `androidx.hilt:hilt-compiler`), `androidx.work:work-testing` (androidTest). WorkManager is net-new to the project — initialize via a Hilt `HiltWorkerFactory` + `Configuration.Provider` (and disable the default `WorkManagerInitializer` so the Hilt factory is used).
- **`:feature:chats:impl`**: new `@HiltWorker` (e.g. `DmPollWorker`), a worker scheduler/registrar, a "Messages" notification channel + builder, a deep-link to the convo, and a `ChatRepository.getLog`(cursor) + persisted-cursor addition.
- **`:feature:settings` + `:core:preferences`**: one in-app "message checking" `Switch` (default on) that gates **both** the new background worker **and** v1's foreground unread poller — so non-chatters can disable all message polling.
- **Modifies shipped v1 (`nubecita-1fy.14`)**: `ChatsUnreadPollingObserver` gains the same toggle gate (off ⇒ no foreground poll, badge stops updating).
- **`:app`**: register the worker on sign-in / opt-in (production-flavor `AppInitializer`); a deep-link matcher + `MainActivity` route for the convo target.
- **Follow-up (out of scope, `nubecita-1fy.17`)**: inline Direct Reply (`RemoteInput`) on the `MessagingStyle` notification.
- **Bench flavor**: stays inert (no scheduling), consistent with the other initializers.
- **No server / gateway / credential-storage impact** — this is the whole point versus the deferred `nubecita-1fy.13`.
