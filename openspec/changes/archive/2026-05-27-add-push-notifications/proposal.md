## Why

Nubecita has no push notification surface. Users get no out-of-band signal when someone replies, mentions, follows, likes, reposts, quotes, or verifies them — they must open the app and check the (also-unbuilt) in-app notifications list. The official Bluesky app's push pipeline is opaque; competing third-party clients increasingly have native pushes. A self-hosted gateway is already deployed (`https://push.nubecita.app`, DID `did:web:push.nubecita.app`, Fly.io, region iad, built from DracoBlue/atproto-push-gateway v1.2.0) — it listens to Jetstream and forwards FCM/APNs/Expo pushes for ten reasons (seven base events — like, repost, reply, mention, quote, follow, verifications — plus the `like-via-repost`, `repost-via-repost`, and `verified`/`unverified` split that the gateway emits as distinct `reason` values). The Android client now needs to register against it, receive the messages, filter the two cases the gateway cannot (mutes, verification spoofing), and route taps into the existing deep-link surface.

The work is scoped to the Android client only — the gateway side is fully deployed and out of scope here.

## What Changes

- **New `:core:push` module** (`nubecita.android.library` + `nubecita.android.hilt`). Hosts the entire push pipeline. No Compose UI, no NavKeys — push is invisible plumbing. The future in-app notifications list lives in a separate `:feature:notifications:{api,impl}` epic.
- **Add `firebase-messaging` runtime dep** to `:app` (BoM-managed, version inherited from the existing `firebase-bom` pin set by `add-firebase-integration`). Register `NubecitaFcmService` in `app/src/main/AndroidManifest.xml`.
- **Subclass `FirebaseMessagingService` as `NubecitaFcmService`**. Overrides `onNewToken` (delegates to `PushRegistrationCoordinator`) and `onMessageReceived` (delegates to `PushDispatcher`). All decision logic lives in pure Kotlin so the service is a thin Android shell.
- **`PushDispatcher`** — pure JVM class. Parses `RemoteMessage.data` into a typed `PushPayload(reason, uri, subject, actorDid, actorHandle, actorDisplayName, recipientDid)`. Applies three filters in order: recipient-DID match against the active session, mute filter (against `MutedActorRepository.snapshot`), trusted-verifier filter (for `verified`/`unverified` reasons). Surviving payloads route to `PushNotificationBuilder`. Foreground-app suppression: drops the system notification entirely when `ProcessLifecycleOwner` reports `STARTED` (v1; the in-app notifications epic restores this with an in-app surface).
- **`PushRegistrationRepository`** — wraps `XrpcClient.procedure(nsid = "app.bsky.notification.{register,unregister}Push", proxy = "did:web:push.nubecita.app#bsky_notif", …)`. The atproto-proxy header is REQUIRED by the gateway contract; the generated `NotificationService` in atproto-kotlin 8.1.0 (the version currently pinned in `gradle/libs.versions.toml`) does NOT expose a `proxy` parameter on its `registerPush` / `unregisterPush` methods — verified by reading the generated source in the local Gradle cache. So registerPush/unregisterPush are called via `XrpcClient` directly with the typed `RegisterPushRequest`/`UnregisterPushRequest` DTOs the library already ships.
- **`PushRegistrationCoordinator`** — glues registration to lifecycle triggers: login success, `onNewToken`, logout. Persists `(accountDid, fcmToken, status: Pending | Succeeded | Failed)` in DataStore so cold-start re-registration is no-op when nothing changed and the gateway-registration state is still fresh. Cold-start re-register only fires when `status == Failed` OR `(accountDid, fcmToken)` differ from the persisted record — avoids per-launch radio wake. Failed register attempts retry with exponential backoff.
- **`MutedActorRepository`** — cached `Set<String>` of muted DIDs in DataStore. `refresh()` paginates `app.bsky.graph.getMutes`. Debounced on app foreground: skip if last successful refresh was within 12 hours. The future mute-write feature (nubecita-oftc.5) invalidates the cache on writes, bypassing the debounce — same-device mute changes propagate immediately; cross-device mutes are picked up within the 12-hour window.
- **`TrustedVerifiers`** — top-level `val TRUSTED_VERIFIERS: Set<String>` constant. V1 ships exactly one entry: `did:plc:z72i7hdynmk6r22z27h6tvur` (Bluesky's official verifier). PushDispatcher silently drops `verified`/`unverified` payloads whose `actorDid` isn't in the set — prevents the spoof where any account can forge `app.bsky.graph.verification` records and ride them into a "Your account is verified" push.
- **`NotificationChannelInstaller`** — creates 10 notification channels at app start (one per `reason`) under the three-tier importance scheme: HIGH for `reply`/`mention`/`quote`/`verified`/`unverified`, DEFAULT for `follow`, LOW for `like`/`repost`/`like-via-repost`/`repost-via-repost`. Idempotent — Android merges idempotent calls per channel ID.
- **`PushNotificationBuilder`** — maps `PushPayload` → `NotificationCompat.Builder`. Picks channel via `channelFor(reason)`. Title/body from localized `:core:push` string resources keyed on `reason`, with `actorDisplayName` interpolated. Gateway-supplied English title/body are fallback only. Tap intent deep-links via the AT-URI in `subject ?: uri` — reuses the existing manifest deep-link handlers. notify-ID = `uri.hashCode()` so distinct events stack (the gateway's `uri` is the *action* AT-URI, not the subject, so likes on the same post produce distinct IDs). `setGroup("nubecita:${reason}")` + per-reason group-summary notification (`setGroupSummary(true)`, inbox-style template) collapse N likes into "N new likes" in the shade.
- **POST_NOTIFICATIONS runtime permission** (Android 13+, API 33+): prompt the first time the user completes a successful login. One-shot rationale + system prompt. Registration proceeds regardless of the answer; if denied, the FCM token is still registered with the gateway and Android silently suppresses display. Re-prompting requires the user to flip the system-settings toggle.
- **Logout hook**: explicit `Coordinator.onSessionEnded(did, fcmToken)` → best-effort `unregister`. Logout proceeds even if the gateway call fails; the gateway's `UNREGISTERED` FCM pruning catches the eventual case.
- **No in-app settings UI for v1.** The existing Settings screen surfaces a "Notifications" row that fires `Settings.ACTION_APP_NOTIFICATION_SETTINGS` — users tune per-channel preferences in Android system settings. An in-app per-reason toggle UI is a future epic.

## Capabilities

### New Capabilities
- `push-notifications`: FCM-backed push pipeline for AT Protocol social events — `:core:push` module, channel installation, FCM lifecycle, gateway registration via `app.bsky.notification.{register,unregister}Push` through the user's PDS, payload dispatch with mute + trusted-verifier filters, foreground-app suppression, deep-link routing to existing handlers, and the POST_NOTIFICATIONS permission gate.

### Modified Capabilities
- `firebase-integration`: adds `firebase-messaging` as a fourth runtime dep alongside `firebase-analytics`, `firebase-appcheck-playintegrity`, and `firebase-appcheck-debug`. No init plumbing in `Application.onCreate()` (FCM auto-initializes via its ContentProvider); the manifest gains a `<service>` element for `NubecitaFcmService`.

## Impact

**Code:**
- `gradle/libs.versions.toml` — one new library entry (`firebase-messaging`); no new version since the BoM owns it.
- `app/build.gradle.kts` — add `firebase-messaging` to `implementation`.
- `app/src/main/AndroidManifest.xml` — add `<service>` for `NubecitaFcmService` with the FCM intent-filter; add `POST_NOTIFICATIONS` to `<uses-permission>` declarations.
- `core/push/build.gradle.kts` (new) — applies `nubecita.android.library` + `nubecita.android.hilt`; deps on `:core:auth`, `:core:common`, `firebase-messaging`, `kotlinx-coroutines-play-services`, `androidx.datastore-preferences`, atproto-kotlin `runtime` + `models`.
- `core/push/src/main/kotlin/net/kikin/nubecita/core/push/` (new):
  - `NubecitaFcmService.kt` — thin Android shell.
  - `PushDispatcher.kt` — pure dispatch + filters.
  - `PushPayload.kt` — typed payload + sealed `Reason` enum.
  - `PushRegistrationRepository.kt` — XRPC wrapper.
  - `PushRegistrationCoordinator.kt` — lifecycle glue.
  - `PushRegistrationStateStore.kt` — DataStore wrapper for `(did, fcmToken, status)`.
  - `MutedActorRepository.kt` — cached mute set + 12-hour foreground refresh.
  - `TrustedVerifiers.kt` — hardcoded constant.
  - `NotificationChannelInstaller.kt` — channel idempotent install.
  - `PushNotificationBuilder.kt` — payload → NotificationCompat.Builder.
  - `di/PushModule.kt` — Hilt bindings.
- `core/push/src/main/res/values/strings.xml` (new) — 10 reason title strings + 10 body strings + channel name/description strings.
- `core/push/src/test/kotlin/` (new) — unit tests per component.
- `core/push/src/androidTest/kotlin/` (new) — `NubecitaFcmServiceInstrumentationTest`, `NotificationChannelInstallerInstrumentationTest`.
- `app/src/main/kotlin/net/kikin/nubecita/NubecitaApplication.kt` — call `NotificationChannelInstaller.install()` from `onCreate()` after Timber init.
- `feature/login:impl` — hook successful login into `PushRegistrationCoordinator.onSessionEstablished(did)`; prompt POST_NOTIFICATIONS on Android 13+.
- `feature/settings:impl` — add a "Notifications" row that fires `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
- `core/auth` — **no changes**. The reactive session signal `:core:push` needs already exists: `SessionStateProvider.state: StateFlow<SessionState>` with `sealed interface SessionState { object Loading; object SignedOut; data class SignedIn(val handle, val did) }`. `DefaultAuthRepository` already calls `sessionStateProvider.refresh()` on login and logout. `PushRegistrationCoordinator` collects this flow as its single source of truth — establishes registration on `SignedIn`, unregisters and clears local state on `SignedOut`, ignores `Loading`. Eliminates the need for a separate cold-start trigger (StateFlow re-emits current state on subscribe).
- `settings.gradle.kts` — register `:core:push`.

**Already deployed (no diff):**
- Push gateway at `https://push.nubecita.app`; DID `did:web:push.nubecita.app`.
- GCP service account `push-gateway@nubecita-2a4c1.iam.gserviceaccount.com` scoped to `roles/firebasecloudmessaging.admin`.
- `app/google-services.json` (committed by `add-firebase-integration`).
- Existing deep-link manifest handlers for `bsky.app/profile`, `bsky.app/post`, and `nubecita://` URIs.

**External (manual, no PR diff):**
- Verify Firebase Cloud Messaging API is enabled on the `nubecita-2a4c1` GCP project (likely already on; no-op if so).
- Confirm gateway `/health` shows `registeredDIDs: 0, totalTokens: 0` baseline before the smoke test.

**Dependencies / system:**
- Runtime adds Firebase Messaging classes to the APK (one of the larger Firebase modules but still well under the methods budget).
- FCM heartbeat + token refresh add a background cost owned by Play Services — no nubecita-side polling.
- The gateway's per-event Jetstream throughput drives the inbound push rate; the device's FCM connection is the only persistent network cost.

**Stale memory to fix on land:**
- `reference_atproto_kotlin_notification_lexicon_gap.md` says `registerPush` is missing from atproto-kotlin. As of the pinned `8.1.0`, `NotificationService.registerPush` + `unregisterPush` are present (generated method bodies confirmed by reading the local Gradle cache). The remaining gap — no `proxy` parameter on the generated methods — is filed upstream separately, not as a missing-NSID concern. The memory is stale and gets corrected when this lands.
