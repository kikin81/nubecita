## 1. Module + Gradle scaffold

- [ ] 1.1 Add `firebase-messaging` library entry to `gradle/libs.versions.toml` (BoM-managed, no new version). **No tests** — verified by `:app` consumers compiling in 1.4.
- [ ] 1.2 Create `core/push/build.gradle.kts` applying `nubecita.android.library` + `nubecita.android.hilt`. Namespace: `net.kikin.nubecita.core.push`. Dependencies: `:core:auth`, `:core:common`, atproto-kotlin `runtime` + `models`, `firebase-messaging`, `kotlinx-coroutines-play-services`, `androidx.datastore-preferences`, plus the convention-plugin defaults. **No tests** — verified by `./gradlew :core:push:assembleDebug`.
- [ ] 1.3 Register `:core:push` in `settings.gradle.kts`. **No tests** — verified by `./gradlew projects` listing it.
- [ ] 1.4 Add `firebase-messaging` to `app/build.gradle.kts` `implementation`. Add `:core:push` to `app/build.gradle.kts` `implementation`. **No tests** — verified by `./gradlew :app:assembleDebug`.
- [ ] 1.5 Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` to `app/src/main/AndroidManifest.xml`. **No tests** — verified by manifest merge succeeding.

## 2. Push payload + dispatcher (pure JVM)

- [ ] 2.1 Create `core/push/.../PushPayload.kt`: typed `data class PushPayload(reason, uri, subject, actorDid, actorHandle, actorDisplayName, recipientDid)` + `sealed interface PushPayload.Reason { Like; LikeViaRepost; Repost; RepostViaRepost; Reply; Mention; Quote; Follow; Verified; Unverified }`. Companion `parse(data: Map<String, String>): PushPayload?` — returns null on malformed input (missing required keys, unknown reason). **Tests**: `PushPayloadParseTest` — one happy-path test per reason; one defensive-drop test per missing-required-key shape; one for unknown reason.
- [ ] 2.2 Create `core/push/.../TrustedVerifiers.kt`: top-level `internal val TRUSTED_VERIFIERS: Set<String> = setOf("did:plc:z72i7hdynmk6r22z27h6tvur")`. **Tests**: `TrustedVerifiersTest` — single test asserting the set is non-empty and contains the official Bluesky DID.
- [ ] 2.3 Create `core/push/.../PushDispatcher.kt`: pure class with `dispatch(data: Map<String, String>, activeSessionDid: String?, isAppForeground: Boolean, mutedActors: Set<String>): DispatchOutcome` returning `Show(payload) | Drop(reason)`. Implements the four-stage filter chain (parse / recipient-DID match / foreground / mute / verifier). **Tests**: `PushDispatcherTest` — one test per filter branch (parse fails, recipient mismatch, foreground drop, muted actor, untrusted verifier, happy path).

## 3. Registration repository + state store

- [ ] 3.1 Create `core/push/.../PushRegistrationStateStore.kt`: DataStore wrapper for `(accountDid: String?, fcmToken: String?, status: Status)` where `Status = Pending | Succeeded | Failed`. Exposes `suspend fun read()`, `suspend fun write(...)`, `suspend fun clear()`. **Tests**: `PushRegistrationStateStoreTest` — read/write roundtrip; clear; default state.
- [ ] 3.2 Create `core/push/.../PushRegistrationRepository.kt`: `suspend fun register(did: String, fcmToken: String): Result<Unit>` and `suspend fun unregister(did: String, fcmToken: String): Result<Unit>`. Implementation calls `xrpcClient.procedure(nsid = "app.bsky.notification.{registerPush|unregisterPush}", proxy = "did:web:push.nubecita.app#bsky_notif", params = NoXrpcParams, paramsSerializer = NoXrpcParams.serializer(), input = RegisterPushRequest(serviceDid = "did:web:push.nubecita.app", token = fcmToken, platform = "android", appId = BuildConfig.APPLICATION_ID), inputSerializer = RegisterPushRequest.serializer(), responseSerializer = UnitResponseSerializer)`. Returns `Result.success`/`Result.failure` (don't throw). **Tests**: `PushRegistrationRepositoryTest` — verify request body construction with a MockXrpcFixture; verify proxy header argument; verify failure mapping.

## 4. Lifecycle coordinator

- [ ] 4.1 Verify `:core:auth`'s existing `SessionStateProvider.state: StateFlow<SessionState>` (with `Loading | SignedOut | SignedIn(handle, did)`) and `DefaultAuthRepository`'s `refresh()` calls on login/logout cover the transitions `:core:push` needs. **No code changes** — this task is a verification step + a written note in the design's "Decisions" section confirming no `:core:auth` modifications were required. Confirm by reading `DefaultAuthRepository.kt`, `DefaultSessionStateProvider.kt`, and their tests. If a transition path is missing (e.g. refresh-failure-driven session demotion isn't wired), file a follow-up bd rather than expanding this PR's scope.
- [ ] 4.2 Create `core/push/.../PushRegistrationCoordinator.kt`: injects `SessionStateProvider`, collects `state` from an application-scope coroutine. Mapping: `SignedIn(_, did)` → `onSessionEstablished(did)` (fetch FCM token, call `PushRegistrationRepository.register`, update `PushRegistrationStateStore`); `SignedOut` → `onSessionEnded(<stored did>, <stored fcmToken>)` (best-effort `PushRegistrationRepository.unregister`, then `PushRegistrationStateStore.clear()`); `Loading` → no-op. Also exposes `onTokenRotated(token: String)` for `NubecitaFcmService.onNewToken` to call directly. The StateFlow's re-emit-on-subscribe behavior covers cold-start — no separate `onColdStart` hook. Maintains exponential-backoff retry on register failure (5s/30s/2m/8m, cap at 4 attempts then wait for next state emission). Skips the register network call when `PushRegistrationStateStore` shows `status == Succeeded` AND stored `(did, fcmToken)` match the current session + current FCM token. **Tests**: `PushRegistrationCoordinatorTest` (uses a fake `SessionStateProvider` to drive transitions) — `SignedIn` triggers register; `onTokenRotated` triggers register; `SignedOut` triggers unregister + store clear (best-effort, swallows failure); `Loading` is a no-op; subscribing collector when state is already `SignedIn` and store says `Succeeded`+match is a no-op; subscribing collector when state is `SignedIn` and store says `Failed` retries; exponential backoff cadence.

## 5. Muted actor cache

- [ ] 5.1 Create `core/push/.../MutedActorRepository.kt`: holds `StateFlow<Set<String>>` (the snapshot read by `PushDispatcher`). `suspend fun refresh(force: Boolean = false)` paginates `app.bsky.graph.getMutes` and writes the new set to DataStore + emits to the flow. Debounce: skip if `force == false` AND last successful refresh was within 12 hours (read from DataStore). **Tests**: `MutedActorRepositoryTest` — refresh populates the snapshot; debounce skips within window; `force = true` bypasses debounce; failure preserves the stale snapshot + schedules retry on next foreground.
- [ ] 5.2 Wire foreground-trigger: `PushRegistrationCoordinator` (or a dedicated `AppLifecycleObserver` in `:core:push`) observes `ProcessLifecycleOwner.get().lifecycle` and calls `MutedActorRepository.refresh()` on `ON_START`. **Tests**: instrumented coverage; pure-JVM doesn't have a `ProcessLifecycleOwner` analog without robolectric — skip JVM here, cover in 9.x androidTest.

## 6. Notification channels + builder

- [ ] 6.1 Create `core/push/.../NotificationChannelInstaller.kt`: `install(context: Context)` idempotently creates 10 `NotificationChannel` instances with the three-tier importance mapping (HIGH for reply/mention/quote/verified/unverified; DEFAULT for follow; LOW for like/repost/like-via-repost/repost-via-repost). Channel name + description sourced from `:core:push` string resources. **Tests**: covered in 9.2 (instrumented — `NotificationManager.getNotificationChannels()` not mockable in pure JVM).
- [ ] 6.2 Create `core/push/src/main/res/values/strings.xml`: 10 channel-name strings (e.g. `push_channel_like_name = "Likes"`), 10 channel-description strings, 10 reason-title strings (e.g. `push_reason_like = "%1$s liked your post"`), 10 reason-body strings (e.g. `push_reason_reply_body = "%1$s: %2$s"` if we render a snippet, or just title-only if not), a `push_title_default` fallback, plus a `push_summary_like = "%1$d new likes"` etc. for the group summaries. **No tests** — strings file.
- [ ] 6.3 Create `core/push/.../PushNotificationBuilder.kt`: `build(payload: PushPayload, context: Context): Notification` constructs `NotificationCompat.Builder` with channel from `channelFor(reason)`, title/body from the strings, tap-intent built from `tapIntentFor(payload, context)` (subject ?: uri → translated to nubecita://...). Sets `setGroup("nubecita:${reason}")`. Also exposes `buildSummary(reason, count, context): Notification` with `setGroupSummary(true)` + inbox-style template. notify-IDs: individual = `payload.uri.hashCode()`; summary = `("nubecita-summary:" + reason).hashCode()`. **Tests**: `PushNotificationBuilderTest` — channel selection per reason; tap-intent URI shape; group ID; group-summary notify-ID stability; localization fallback to gateway-supplied title when string resource is absent (defensive).
- [ ] 6.4 Create `core/push/.../AtUriToDeepLink.kt` helper: `fun atUriToNubecitaUri(atUri: String): Uri?` translates `at://did/collection/rkey` → `nubecita://profile/{did}/post/{rkey}` (for posts) and `at://did` → `nubecita://profile/{did}` (for the follow case). **Tests**: `AtUriToDeepLinkTest` — happy paths for post + profile; malformed AT-URI → null.

## 7. FCM service shell

- [ ] 7.1 Create `core/push/.../NubecitaFcmService.kt`: subclass `FirebaseMessagingService`. `onNewToken(token: String)` → forwards to injected `PushRegistrationCoordinator.onTokenRotated(token)` via a Hilt entry point. `onMessageReceived(remoteMessage: RemoteMessage)` → builds `data: Map<String, String>` from `remoteMessage.data`, asks `PushDispatcher.dispatch(...)` (with injected `activeSessionDid`, `ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(STARTED)`, `MutedActorRepository.snapshot.value`), publishes via `PushNotificationBuilder` if `Show`. Idempotent group-summary publish after every individual notify. **Tests**: covered in 9.1 (instrumented).
- [ ] 7.2 Register `<service android:name="net.kikin.nubecita.core.push.NubecitaFcmService" android:exported="false">` with the FCM intent-filter (`com.google.firebase.MESSAGING_EVENT`) in `app/src/main/AndroidManifest.xml`. **Tests**: manifest merge verified by build.

## 8. App + feature wiring

- [ ] 8.1 In `NubecitaApplication.onCreate()`: call `NotificationChannelInstaller.install(this)` after Timber init. Inject via Hilt entry point if needed. **Tests**: covered in 9.2.
- [ ] 8.2 In `:feature:login:impl`: after a successful login completes (the existing success branch in `LoginViewModel` / its caller), call `PushRegistrationCoordinator.onSessionEstablished(did)`. Order: prompt POST_NOTIFICATIONS first (Android 13+, first-time only — gate via a dedicated `notificationsPromptShown: Boolean` DataStore-backed flag exposed by `:core:push` (separate from `PushRegistrationStateStore` to keep registration state and permission state independently testable)), then trigger the registration. The registration coordinator handles FCM-token fetch + register call internally. **Tests**: `LoginPostNotificationsPromptTest` (unit, JUnit5) — first-login triggers prompt + coordinator call; second-login skips prompt; pre-API-33 skips prompt.
- [ ] 8.3 Wire the logout path to `PushRegistrationCoordinator.onSessionEnded(did, fcmToken)`. Hook depends on the session-end signal investigated in 4.2. **Tests**: extend `PushRegistrationCoordinatorTest` (covered in 4.1) to assert this is called.
- [ ] 8.4 In `:feature:settings:impl`: add a "Notifications" row that, on tap, fires `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)`. **Tests**: instrumented (`SettingsNotificationsRowInstrumentationTest`) — tap fires the right Intent shape; pure-JVM unit covers the click-handler logic.
- [ ] 8.5 Wire `:core:push`'s Hilt module: provide `PushDispatcher` (singleton), `PushRegistrationRepository` (singleton, depends on `XrpcClient`), `PushRegistrationCoordinator` (singleton, application-scoped coroutine scope), `MutedActorRepository` (singleton), `PushNotificationBuilder` (singleton, depends on `Context`), `NotificationChannelInstaller` (singleton). **No tests** — covered transitively by integration.

## 9. Instrumented tests

- [ ] 9.1 `core/push/src/androidTest/.../NubecitaFcmServiceInstrumentationTest.kt`: synthesize a `RemoteMessage` for each reason; deliver via the service's lifecycle; assert `NotificationManagerCompat.getActiveNotifications()` shape (per-reason channel, expected title, expected tap-intent target). Cover the foreground-drop path with a fake `ProcessLifecycleOwner` state. **Tests**: this IS the test file.
- [ ] 9.2 `core/push/src/androidTest/.../NotificationChannelInstallerInstrumentationTest.kt`: call `install(context)` then assert `NotificationManager.notificationChannels` includes all 10 IDs with the right importance per the table. Call `install` twice and assert idempotence (no duplicate channels). **Tests**: this IS the test file.
- [ ] 9.3 `core/push/src/androidTest/.../PushRegistrationCoordinatorInstrumentationTest.kt`: end-to-end with a fake `XrpcClient` and a fake FCM-token source; cover login → register call shape (proxy header asserted), logout → unregister, retry on failure. **Tests**: this IS the test file.

## 10. Validation pass

- [ ] 10.1 `./gradlew :app:assembleDebug` — green.
- [ ] 10.2 `./gradlew :core:push:testDebugUnitTest :core:push:lintDebug` — green.
- [ ] 10.3 `./gradlew :feature:login:impl:testDebugUnitTest :feature:settings:impl:testDebugUnitTest` — green (covers the new wiring).
- [ ] 10.4 `pre-commit run --all-files` — green (spotless, ktlint, commitlint, openspec validate).
- [ ] 10.5 `./gradlew :core:push:connectedDebugAndroidTest` against a real device or emulator — green.
- [ ] 10.6 Manual smoke (recorded here after running, before claiming done):
  - [ ] Baseline: `curl -s https://push.nubecita.app/health | jq` shows `registeredDIDs: 0, totalTokens: 0`.
  - [ ] Install debug APK, sign in. POST_NOTIFICATIONS prompt appears; accept.
  - [ ] `/health` now shows `registeredDIDs: 1, totalTokens: 1`.
  - [ ] From a second account on web bsky.app, like one of my posts. Within 10 seconds, native notification appears with "X liked your post."
  - [ ] Tap the notification → MainActivity opens the post detail.
  - [ ] Open the app to foreground, have second account like another post. NO notification appears (foreground suppression verified).
  - [ ] Background the app, verify next like produces a notification.
  - [ ] Reply from second account → HIGH-importance heads-up notification (peeps).
  - [ ] Like again with the app backgrounded but still installed → no duplicate notification (notify-ID uniqueness verified).
  - [ ] 5 quick likes from the second account → shade collapses into a single "N new likes" summary.
  - [ ] Log out. `/health` shows `registeredDIDs: 0, totalTokens: 0` within a few seconds.
  - [ ] Mute the second account via the official Bluesky client (or via the eventual nubecita-oftc.5 wiring once it ships). Background the app for > 12h (or trigger a forced refresh). Like from the muted account → no notification.
  - [ ] Verification spoof test: in dev console, simulate a `verified` push whose `actorDid` is NOT in `TRUSTED_VERIFIERS`. Confirm the notification is dropped.

## 11. Memory + bd hygiene

- [ ] 11.1 Update / delete `reference_atproto_kotlin_notification_lexicon_gap.md` — `registerPush`/`unregisterPush` are present in atproto-kotlin `6.5.1`. Either delete (if no remaining gaps) or trim to whatever IS still missing (e.g. proxy parameter on `NotificationService` — file upstream issue).
- [ ] 11.2 File a `kikin81/atproto-kotlin` issue: add a `proxy: String?` parameter to `NotificationService.{registerPush, unregisterPush, …}` so generated services can pass through to `XrpcClient.procedure(..., proxy = ...)`. Reference this change as the consumer that demonstrates the need.
- [ ] 11.3 File follow-up bds:
  - `:feature:notifications:{api,impl}` epic (in-app notifications list using `listNotifications` / `getUnreadCount`, includes the foreground-push in-app surface).
  - In-app per-reason settings toggles (currently system-settings-only).
  - Multi-account push routing (when multi-account ships).
  - Trusted-verifier list refresh path (if/when the verifier ecosystem grows beyond ~3 entries).
  - "Re-register" button in settings as a recovery for gateway-side silent state loss.
