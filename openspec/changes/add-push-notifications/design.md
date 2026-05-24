## Context

A self-hosted push gateway built from DracoBlue/atproto-push-gateway v1.2.0 is deployed at `https://push.nubecita.app` (Fly.io, iad region). Its public DID is `did:web:push.nubecita.app`. The gateway:

- Listens to Jetstream for the seven social-event types nubecita cares about: `like`, `repost`, `reply`, `mention`, `quote`, `follow`, `verified`/`unverified` (the latter from `app.bsky.graph.verification` records), plus the `*-via-repost` variants for like/repost.
- Forwards via FCM (Android), APNs (iOS), and Expo.
- Filters server-side: blocks ARE applied; mutes are NOT (mutes aren't published to Jetstream).
- Auto-prunes stale FCM tokens on `UNREGISTERED` errors.

The Android client side is greenfield: `firebase-messaging` isn't on the classpath, no `:core:push` module exists, no FCM service class exists, no `registerPush` call site exists. The pieces that ARE present:

- `add-firebase-integration` shipped Firebase Analytics + App Check + App Distribution, established the `firebase-bom` pin, and committed `app/google-services.json` for project `nubecita-2a4c1`.
- atproto-kotlin `6.5.1` (current pin) generates `NotificationService.registerPush(request)` and `unregisterPush(request)` — the stale memory `reference_atproto_kotlin_notification_lexicon_gap.md` says otherwise but the jar contradicts it.
- The manifest has `intent-filter`s for `bsky.app/profile`, `bsky.app/post`, and the `nubecita://` scheme. Deep-link handling is centralized at `MainActivity`.
- No mute infrastructure exists on-device beyond per-post `ViewerStateUi.isAuthorMutedByViewer` propagation. The mute-write feature (`nubecita-oftc.5`) is open but not shipped.
- Single-account today; multi-account planned. Session-end is observable from `:core:auth` (signal shape to be verified during implementation).

The gateway contract for register: POST `app.bsky.notification.registerPush` against the **user's PDS** (not the gateway directly — the PDS forwards via the `atproto-proxy: did:web:push.nubecita.app#bsky_notif` header attached by the PDS-side inter-service JWT mechanism). Body: `{ serviceDid, token, platform, appId }`.

## Goals / Non-Goals

**Goals:**
- Receive native Android push notifications for the seven social events within seconds of the underlying record being committed to a PDS.
- Register the device's FCM token against the user's PDS on login; re-register on token rotation; unregister on logout.
- Filter pushes the gateway cannot (mutes; spoofed verifications) before display.
- Tap-to-deep-link via existing `MainActivity` handlers (no new deep-link surface).
- Three-tier notification importance per reason; Android system settings is the user's per-reason tuning surface.
- Foreground-suppress system notifications (v1; the in-app notifications epic restores this with an in-app surface).
- POST_NOTIFICATIONS permission asked at the natural first-benefit moment (after login).

**Non-Goals:**
- The in-app notifications list screen (`app.bsky.notification.listNotifications` / `getUnreadCount` UI). Separate epic — `:feature:notifications:{api,impl}`.
- Per-reason in-app settings toggles. V1 = Android system settings only.
- Snackbar / badge fallback for foreground pushes. V1 drops; future in-app surface handles it.
- Server-side mute filtering (upstream gateway change).
- Multi-account real support — design accommodates it via `(accountDid, fcmToken)` keyed storage but v1 maintains one record.
- WorkManager for retries or for periodic mute refresh. V1 uses in-process exponential backoff for register retries and a foreground-triggered debounce for mute refresh.
- Tonal / vibration / LED customization per channel. V1 uses Android defaults per importance tier.
- Notification dot / unread badge on the launcher icon. Out of scope; depends on the in-app notifications list epic for unread count.

## Decisions

### Decision: `:core:push`, not `:feature:notifications:impl`

`:core:push` owns the FCM service, registration plumbing, channel installation, payload dispatch, filters, and the NotificationCompat builder. No screens, no NavKeys.

The push pipeline is invisible plumbing: it doesn't render to a Composable, doesn't navigate, doesn't observe any UiState. Treating it as a feature module would require an empty `:feature:notifications:api` (no NavKey) and an `:impl` module that contributes no `EntryProviderInstaller`. A `:core` module is the honest shape.

The future in-app notifications list (the one that calls `listNotifications` / `getUnreadCount`) is a separate epic and gets its own `:feature:notifications:{api,impl}`. The two modules share nothing UI-side; they only share the lexicon types from atproto-kotlin.

**Alternative considered:** Bundle everything (FCM service + future in-app screen) inside `:feature:notifications:impl`. Rejected — couples an Android `Service` subclass to a Compose-using module, drags Compose deps into the FCM hot path, and confuses the seam between "invisible system surface" and "in-app screen."

### Decision: `XrpcClient.procedure(...)` directly, not through `NotificationService.registerPush`

The generated `NotificationService.registerPush(request)` in atproto-kotlin `6.5.1` does NOT expose a `proxy: String?` parameter — it hardcodes `proxy = null` when it calls `client.procedure(...)`. The gateway contract REQUIRES `atproto-proxy: did:web:push.nubecita.app#bsky_notif`. So we bypass the service wrapper and call `XrpcClient.procedure(nsid = "app.bsky.notification.registerPush", proxy = "did:web:push.nubecita.app#bsky_notif", params = NoXrpcParams, paramsSerializer = NoXrpcParams.serializer(), input = RegisterPushRequest(...), inputSerializer = RegisterPushRequest.serializer(), responseSerializer = UnitResponseSerializer)` directly. The typed DTOs are the same ones the library already ships.

This is a localized divergence — one call site for `register`, one for `unregister`. The upstream improvement (add a `proxy` parameter to `NotificationService.registerPush`) is filed as a follow-on `kikin81/atproto-kotlin` issue; when it lands we swap to the generated path.

**Alternative considered:** Subclass `NotificationService` and override `registerPush`. Rejected — Kotlin generated classes aren't designed for subclassing; cleaner to call the underlying `XrpcClient` API the generated service is a thin wrapper over.

**Alternative considered:** Wrap `XrpcClient` itself with a delegate that injects the proxy header for specific NSIDs. Rejected — overreach for two call sites; couples the runtime client to nubecita-specific routing.

### Decision: Cold-start re-register is gated by a dirty flag, not unconditional

Original recommendation was "register on login + onNewToken + cold-start-if-logged-in." Reviewer correctly flagged that cold-start re-register on every launch is aggressive: FCM tokens are extremely stable, the gateway dedupes registrations, and the radio wake / rate-limit cost is real.

Adopted: `PushRegistrationStateStore` persists `(accountDid, fcmToken, status: Pending | Succeeded | Failed)` in DataStore. Cold-start logic:

- If `status == Succeeded` AND `currentSessionDid == storedDid` AND `currentFcmToken == storedFcmToken` → no-op. No network.
- Otherwise → register, update store.

The retry path (exponential backoff on `Failed`) covers transient errors so the cold-start path stays no-op on the happy path. Lost-server-state recovery rides the next `onNewToken` or the next explicit re-login.

**Alternative considered:** Drop cold-start re-register entirely; trust that login + onNewToken are sufficient. Rejected — silent gateway-side state loss (gateway redeploy that clears state, manual unregister via tooling) would never recover without a re-login. The dirty-flag path adds one DataStore read per cold start, negligible.

### Decision: Mute filter via cached `Set<DID>` + 12-hour foreground refresh, not per-push fetch

`onMessageReceived` runs under a 10-second ANR limit and may execute in a Doze-throttled context. A per-push `getMutes` paginated fetch would add a network roundtrip (and possibly an OAuth refresh) inside the hot path — fragile under low connectivity, exposes the user to "phantom pushes for muted accounts because the network was slow" failure modes.

Adopted: `MutedActorRepository` holds a `Set<String>` snapshot in DataStore, refreshed on app foreground if the last successful refresh was > 12 hours ago. Same-device mute changes from the upcoming `nubecita-oftc.5` mute-write feature invalidate the cache immediately, bypassing the debounce; the refresh interval exists only for the cross-device case (user muted via the official Bluesky web app on another device).

12 hours is conservative — most users mute infrequently, and cross-device propagation latency in the same-day range is acceptable for a defense-in-depth filter. A 5-minute or hourly cadence would burn paginated network calls every time the user swaps apps with no proportional benefit.

**Alternative considered:** Fetch on every push. Rejected — latency, ANR risk, offline fragility.

**Alternative considered:** WorkManager periodic job, hourly. Rejected — extra dependency just for this, plus battery cost the foreground-debounce model avoids.

### Decision: Foreground app drops the system notification entirely (v1)

When `ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(STARTED)`, `PushDispatcher` drops the payload without calling `NotificationManagerCompat.notify`. No Snackbar, no badge, no in-app surface.

Heads-up notifications dropping over an app the user is actively using ARE disruptive. Suppressing them is the standard pattern (Twitter / Bluesky's official client / etc.). The trade-off is the user misses real-time signal for events they didn't trigger themselves; this is acceptable for v1 because the in-app notifications list epic restores it with a proper in-app surface (a notifications tab badge + the list itself).

For v1, the architectural seam — `ProcessLifecycleOwner` observation injected into `PushDispatcher` — means the future epic can drop in a `ForegroundPushSink` interface (publish to a SharedFlow that the notifications tab observes) without touching the FCM service or the dispatcher's filter chain.

**Alternative considered:** Drop heads-up but post silent shade entry. Rejected — half-measure; users open the shade and see notifications they explicitly opened the app to read. Either fully show or fully suppress.

**Alternative considered:** Always show in foreground, let the user tune. Rejected — disruptive default; the user has to discover and configure per-channel suppression.

### Decision: Three-tier channel importance (HIGH / DEFAULT / LOW), 10 channels

| Channel ID (= reason)        | Importance |
| ---------------------------- | ---------- |
| `reply`                      | HIGH       |
| `mention`                    | HIGH       |
| `quote`                      | HIGH       |
| `verified`                   | HIGH       |
| `unverified`                 | HIGH       |
| `follow`                     | DEFAULT    |
| `like`                       | LOW        |
| `like-via-repost`            | LOW        |
| `repost`                     | LOW        |
| `repost-via-repost`          | LOW        |

The gateway sets `notification.android.channel_id = reason`, so users tune per-reason in Android system settings. The defaults mirror the official Bluesky client's UX (direct interactions peep; passive engagement stays in the shade silently). Verifications go HIGH because they're rare and security-relevant; if a future spoof slips past the trusted-verifier filter, the HIGH importance ensures the user notices and reports.

Channel installation is idempotent — `NotificationManager.createNotificationChannel` is a no-op when a channel with that ID and the same configuration exists. Changes to importance after install only take effect for new installs (Android caches per-channel user prefs); explicit follow-up needed if we ever want to migrate importance for existing users.

### Decision: Hardcoded trusted-verifier list in `:core:push`, not a remote config

V1 ships `setOf("did:plc:z72i7hdynmk6r22z27h6tvur")` — Bluesky's official verifier — as a top-level `val` constant in `:core:push`. PushDispatcher silently drops `verified`/`unverified` payloads whose `actorDid` isn't in the set.

Hardcoded is the right call for v1: the verifier ecosystem is essentially one DID right now; the spoof risk is high (any account can issue `app.bsky.graph.verification` records); a stale list means we drop legitimate verifications from a newly-trusted verifier, which fails-safe.

When the verifier ecosystem grows beyond a handful (e.g. trusted news organizations, project foundations), this graduates to a remote-config-fed list or a built-in registry sourced from `app.bsky.actor.getProfile` + a trust convention. Out of scope here.

### Decision: POST_NOTIFICATIONS prompt fires after first successful login, not pre-login

Android 13+ (API 33+) requires `android.permission.POST_NOTIFICATIONS` runtime-granted. The natural "why would I want this?" moment is right after the user signs in: their first real interaction with the app, and the moment they have an account that could receive pushes.

Pre-login prompting (during cold-start) has no context and high denial rates. Lazy / settings-only prompting has lowest opt-in. Login-time prompting balances both — first-real-use, clear context, immediate first-push benefit.

Implementation note: registration to the gateway happens regardless of the permission answer. If denied, the FCM token still works (taps still resolve deep-links if Android ever surfaces the notification via shade after a system-settings flip). Re-prompting requires the user to enable the permission in system settings; we don't pester.

**Alternative considered:** Use Compose `rememberPermissionState` from accompanist-permissions. Rejected — accompanist-permissions is in maintenance mode; the Activity-level launcher API works fine.

### Decision: `setGroup` + per-reason group-summary notification for shade collapsing

Each push notification carries `setGroup("nubecita:${reason}")`. After publishing the individual notification, `PushNotificationBuilder` ALSO publishes (idempotently) a per-reason summary notification with `setGroupSummary(true)`, an inbox-style template ("N new likes" with up to 5 actor lines), and a fixed summary notify-ID computed as `("nubecita-summary:" + reason).hashCode()`.

Without the summary, Android stacks individual notifications in the shade without collapsing — 20 likes = 20 separate rows. With it, the shade shows one "20 new likes" row that expands.

`notify-ID = uri.hashCode()` per individual push: the gateway's `uri` is the action AT-URI (the like / repost / reply record), not the subject (the post being acted on). Distinct events have distinct URIs, so they stack rather than overwrite — confirmed by reading the DracoBlue/atproto-push-gateway v1.2.0 payload schema.

**Alternative considered:** Use `setGroup` only without a summary. Rejected — Android's auto-summary text is generic ("X notifications") and not customizable for inbox content.

### Decision: Tap intent reuses `MainActivity` deep-link handlers via AT-URI

`PushNotificationBuilder.buildTapIntent(payload)` constructs an `Intent` targeting `MainActivity` with `data = Uri.parse(payload.subject ?: payload.uri)`. The existing manifest's `<intent-filter>` for `bsky.app/profile/{handle}/post/{rkey}` and the `nubecita://` scheme handlers resolve the AT-URI (which is `at://did:plc:.../app.bsky.feed.post/<rkey>`) → the post detail screen.

AT-URIs are NOT what the manifest filters match against natively. We translate: `at://did/collection/rkey` → `nubecita://profile/{did}/post/{rkey}` (or `bsky.app/profile/{did}/post/{rkey}`) via a small helper. The helper lives in `:core:push` for the push-tap path only; the canonical AT-URI ↔ HTTPS-URI translation belongs in `:core:common` (file a follow-up to extract if a second caller needs it).

Subject-first: when the push is `like` or `reply`, the user wants to land on the post they were liked/replied-to, not on the like record itself. `subject` is the post URI in those reasons; `uri` is the action URI. Falling back to `uri` covers `follow` (no subject; tap should land on the actor's profile — handled by the same translation).

**Alternative considered:** Synthesize a custom `nubecita://push/<encoded payload>` scheme that re-parses inside `MainActivity`. Rejected — duplicates routing logic that already exists in the deep-link handlers.

### Decision: `PushRegistrationCoordinator` collects the existing `SessionStateProvider.state` flow

Hardcoding `PushRegistrationCoordinator.onSessionEnded(...)` calls into specific logout call sites (a Settings "Sign out" button, a 401-driven force-logout, a future account-deletion flow) couples push teardown to UI surfaces that have nothing to do with notifications. The right shape is a single source of truth that emits whenever the session changes.

That source already exists in `:core:auth`: `SessionStateProvider.state: StateFlow<SessionState>` with `sealed interface SessionState { object Loading : SessionState; object SignedOut : SessionState; data class SignedIn(val handle: String, val did: String) : SessionState }`. `DefaultAuthRepository` calls `sessionStateProvider.refresh()` on both successful login (post-token-exchange) and logout (post-session-clear), so the flow already emits on every session transition.

Adopted: `PushRegistrationCoordinator` injects `SessionStateProvider` and collects `state` from an application-scope coroutine. Mapping:

- `SessionState.SignedIn(_, did)` → `onSessionEstablished(did)`: fetch FCM token, call `PushRegistrationRepository.register`, update `PushRegistrationStateStore`.
- `SessionState.SignedOut` → `onSessionEnded(<previously-stored did>, <stored fcmToken>)`: best-effort `PushRegistrationRepository.unregister`, then `PushRegistrationStateStore.clear()`.
- `SessionState.Loading` → no-op.

This guarantees push is torn down regardless of how the session ended — manual logout, token expiry, account deletion, remote revocation — because every path goes through `DefaultAuthRepository`'s refresh call. It also collapses the "cold-start re-register" path: StateFlow re-emits current state on collect-subscribe, so the coordinator's process-init subscription naturally handles "I just woke up with a `SignedIn` already in place."

`:core:auth` requires NO modifications for this — the surface is unchanged. The push module is purely a new consumer.

**Alternative considered:** A `SharedFlow<SessionEvent>` of one-shot events (Established / Ended). Rejected — the existing StateFlow gives "current state on subscribe" semantics the coordinator wants for cold-start, and forking a parallel SharedFlow would split the source of truth.

**Alternative considered:** Push the unregister call into the Settings "Sign out" button's ViewModel directly. Rejected — couples push to a UI surface; misses non-button logout paths (token revocation, multi-account switch).

### Decision: Manual payload parsing in `PushPayload.parse`, no reflection-based serializer

`RemoteMessage.data` is a `Map<String, String>` by construction; the gateway sends 7 known string fields per the schema. Manual extraction is ~20 lines of `data["reason"]?.let { Reason.fromWireString(it) } ?: return null` patterns.

Adopted: hand-written `PushPayload.parse(Map<String, String>): PushPayload?`. No Moshi, Gson, or kotlinx.serialization on this path. Three benefits:

1. **Zero ProGuard / R8 risk.** Reflection-based parsers require `@Keep` (or library-specific annotations) on the target data class so field names survive R8 minification. Manual mapping is safe under any minification config.
2. **Single source of validation.** Each defensive drop (missing reason, unknown reason value, missing required key for the reason) is visible in one place, easy to test exhaustively.
3. **No extra runtime dep.** kotlinx.serialization is already on the classpath via atproto-kotlin, but bringing it into the FCM hot path for a 7-field map is overkill.

**Alternative considered:** kotlinx.serialization with a `@Serializable` `PushPayload`. Rejected — the input is `Map<String, String>` not JSON; we'd have to round-trip via `Json.encodeToJsonElement(data)` first. Just parse the map.

### Decision: One unit test per pure class; one instrumented test per Android-bound class

- **Unit (JUnit5)**: `PushDispatcher` (each filter branch + the foreground-state suppression), `PushPayload.parse` (each reason + the malformed-payload defensive drops), `PushNotificationBuilder` (channel selection, intent payload, group ID); `PushRegistrationRepository` (XRPC call shape + auth header + proxy header); `PushRegistrationCoordinator` (login/logout/onNewToken/cold-start state-store transitions); `MutedActorRepository` (cache, debounce, mute-write invalidation hook); `TrustedVerifiers` (immutable set sanity check).
- **Instrumented (androidTest)**: `NubecitaFcmServiceInstrumentationTest` — boots the service via `Intent`, delivers a synthetic `RemoteMessage`, asserts `NotificationManager` posts the expected notification; `NotificationChannelInstallerInstrumentationTest` — calls `install()` and verifies all 10 channels exist with the right importance tier; `PushRegistrationCoordinatorInstrumentationTest` — end-to-end login → register call with a fake gateway.
- **Manual (recorded in this proposal's validation section after smoke)**: `/health` baseline, login + registration confirmation, like-on-post → notification, tap → correct deep-link, foreground suppression, logout → unregister.

The pure / Android split is the same pattern `:core:auth` uses. JVM tests run in milliseconds and CI parallelizes them; instrumented tests cost a connected device but only the three covering Android-bound surfaces.

## Risks

- **Gateway redeploy clears tokens silently.** Cold-start dirty-flag check is the recovery mechanism; if the gateway loses our token but `status == Succeeded` in our local store, pushes silently stop until `onNewToken` fires (FCM tokens are stable; this could be days). Mitigation: gateway-side persistent storage (out of scope here) OR a manual "Re-register" button in settings (follow-up).
- **OAuth token refresh during onMessageReceived.** Push service runs on a low-priority background thread under FCM constraints. Long auth refresh (network round-trip) inside the ANR window is a risk. Mitigation: filters run BEFORE any network call; the only network in the dispatcher path is the cached `MutedActorRepository.snapshot` (synchronous, no I/O).
- **Trusted-verifier list staleness.** If Bluesky adds a second official verifier, our hardcoded constant drops legitimate `verified` pushes. Mitigation: monitor verifier-ecosystem signals (Bluesky blog, official client behavior); if it grows beyond ~3 entries, graduate the list to a remote-config fetch.
- **Multi-account preparation friction.** Storage keyed by `(accountDid, fcmToken)` is the right shape but only v1 has one entry. A future multi-account refactor will discover constraints we didn't see (e.g. PushDispatcher must route `recipientDid` to the right session's mute set). Mitigation: file the multi-account follow-up early so the constraint surface is visible.
- **POST_NOTIFICATIONS denied permanently.** User denies the prompt and never flips it. Pushes register but are silently suppressed by Android. No bell icon / no in-app notification surface (yet) means the user has no visible signal that they're missing pushes. Mitigation: in-app notifications list epic adds the in-app surface; document the system-settings-flip path in Help.
- **Foreground suppression hides events from the user.** Acceptable for v1 per the design decision but worth flagging as a known limitation in the proposal's validation section.
- **Channel importance migration.** If we ever want to change a channel's importance after launch, Android caches per-channel user prefs — existing installs keep their current setting. Migration requires either a new channel ID (loses user customization) or an explicit re-prompt flow. Out of scope but worth noting.

## Open Questions

None — the two open questions identified during proposal review (session-end signal shape; FCM ProGuard rules) were resolved by the decisions above: `:core:auth` exposes a new `StateFlow<SessionState>`, and `PushPayload.parse` is manual so no ProGuard / R8 rules beyond Firebase's own consumer rules are required.
