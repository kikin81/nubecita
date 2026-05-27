## ADDED Requirements

### Requirement: `:core:push` module hosts the push pipeline

The application SHALL host the push notification pipeline (FCM service, registration, channel installation, payload dispatch, notification building, and gateway-gap filters) in a dedicated `:core:push` Android library module. The module SHALL apply the `nubecita.android.library` and `nubecita.android.hilt` convention plugins. The module SHALL NOT contain Compose UI, NavKeys, or `EntryProviderInstaller` contributions.

#### Scenario: Module participates in the Gradle build
- **WHEN** `./gradlew :core:push:assembleDebug` runs
- **THEN** the build succeeds without depending on any `:feature:*` module
- **AND** the module produces no `EntryProviderInstaller` contributions to either `@OuterShell` or `@MainShell`

### Requirement: Firebase Cloud Messaging service is registered

The application SHALL register a `NubecitaFcmService` subclass of `FirebaseMessagingService` in the Android manifest with the `com.google.firebase.MESSAGING_EVENT` intent-filter. The service SHALL be `android:exported="false"`.

#### Scenario: Manifest declares the FCM service
- **WHEN** the merged `AndroidManifest.xml` is inspected
- **THEN** an `<service android:name="net.kikin.nubecita.core.push.NubecitaFcmService" android:exported="false">` element exists
- **AND** the element contains an `<intent-filter>` with `<action android:name="com.google.firebase.MESSAGING_EVENT" />`

#### Scenario: FCM token rotation triggers re-registration
- **WHEN** Play Services delivers a new FCM token via `NubecitaFcmService.onNewToken(token)`
- **THEN** `PushRegistrationCoordinator.onTokenRotated(token)` is invoked
- **AND** a `register` call against the user's PDS follows once the active session is observable

### Requirement: Push registration uses the user's PDS with the gateway proxy header

The application SHALL register the device's FCM token by calling `app.bsky.notification.registerPush` against the user's PDS, passing the HTTP header `atproto-proxy: did:web:push.nubecita.app#bsky_notif`. The request body SHALL be the JSON shape `{ "serviceDid": "did:web:push.nubecita.app", "token": <fcm_token>, "platform": "android", "appId": <android.package.name> }`. The call SHALL use the user's existing ATproto session for authentication. The same call shape with NSID `app.bsky.notification.unregisterPush` SHALL be used for the unregistration path.

#### Scenario: Register request carries the proxy header
- **WHEN** `PushRegistrationRepository.register(did, fcmToken)` is invoked
- **THEN** the underlying `XrpcClient.procedure(...)` call is made with `nsid = "app.bsky.notification.registerPush"`
- **AND** the call's `proxy` argument equals `"did:web:push.nubecita.app#bsky_notif"`
- **AND** the request input deserializes to `RegisterPushRequest(serviceDid = "did:web:push.nubecita.app", token = <fcmToken>, platform = "android", appId = <packageName>)`

#### Scenario: Unregister request mirrors register shape
- **WHEN** `PushRegistrationRepository.unregister(did, fcmToken)` is invoked
- **THEN** the underlying `XrpcClient.procedure(...)` call is made with `nsid = "app.bsky.notification.unregisterPush"`
- **AND** the call's `proxy` argument equals `"did:web:push.nubecita.app#bsky_notif"`
- **AND** the request input deserializes to `UnregisterPushRequest(serviceDid = "did:web:push.nubecita.app", token = <fcmToken>, platform = "android", appId = <packageName>)`

### Requirement: Registration lifecycle fires on login, token rotation, and logout

The application SHALL invoke push registration in exactly three lifecycle moments: (a) immediately after a successful login, once the active FCM token is available; (b) when `NubecitaFcmService.onNewToken` delivers a rotated token; (c) on cold start ONLY when the persisted `PushRegistrationStateStore` indicates `status == Failed` or the persisted `(accountDid, fcmToken)` differs from the currently-active values. The application SHALL invoke push unregistration on logout.

#### Scenario: Login triggers registration
- **WHEN** the user completes a successful login and the FCM token is available
- **THEN** `PushRegistrationCoordinator.onSessionEstablished(did)` is invoked
- **AND** a register call is dispatched

#### Scenario: Logout triggers best-effort unregistration
- **WHEN** the active session ends (logout, account deletion, token revocation)
- **THEN** `PushRegistrationCoordinator.onSessionEnded(did, fcmToken)` is invoked
- **AND** an unregister call is dispatched
- **AND** the logout proceeds regardless of whether the unregister succeeds

#### Scenario: Cold start does not re-register on a fresh state
- **GIVEN** `PushRegistrationStateStore` contains `(accountDid = X, fcmToken = T, status = Succeeded)`
- **AND** the currently-active session DID equals X and the currently-active FCM token equals T
- **WHEN** the application cold-starts
- **THEN** no register network call is dispatched

#### Scenario: Cold start retries when the prior attempt failed
- **GIVEN** `PushRegistrationStateStore` contains `status = Failed`
- **WHEN** the application cold-starts
- **THEN** a register call is dispatched

### Requirement: `POST_NOTIFICATIONS` runtime permission is requested at first login

The application SHALL declare the `android.permission.POST_NOTIFICATIONS` permission in the manifest. On Android 13+ (API 33+), the application SHALL request the runtime permission once, immediately after the first successful login, with a brief in-app rationale. The application SHALL NOT re-prompt the user automatically after the initial response.

#### Scenario: First login on Android 13+ prompts the permission
- **GIVEN** the device runs API 33 or higher
- **AND** the user has never been prompted for `POST_NOTIFICATIONS` from this app installation
- **WHEN** the user completes a successful login
- **THEN** the runtime permission prompt is shown after a one-shot rationale

#### Scenario: Subsequent logins do not re-prompt
- **GIVEN** the user has previously responded to the `POST_NOTIFICATIONS` prompt (accepted or denied)
- **WHEN** the user signs out and back in
- **THEN** the prompt is NOT shown again

#### Scenario: Permission denial does not block registration
- **GIVEN** the user denies the `POST_NOTIFICATIONS` permission
- **WHEN** the login flow completes
- **THEN** the FCM token is still registered against the gateway
- **AND** Android suppresses display of the resulting notifications until the user grants the permission via system settings

### Requirement: Ten notification channels installed at app start

The application SHALL create ten Android notification channels at app start, one per gateway `reason`, with the importance tiers:

- HIGH: `reply`, `mention`, `quote`, `verified`, `unverified`
- DEFAULT: `follow`
- LOW: `like`, `like-via-repost`, `repost`, `repost-via-repost`

Channel IDs SHALL exactly match the `reason` string. Channel names and descriptions SHALL be localized via string resources in `:core:push`.

#### Scenario: All ten channels exist after install
- **WHEN** `NotificationChannelInstaller.install(context)` runs
- **THEN** `NotificationManager.notificationChannels` includes channel IDs `reply`, `mention`, `quote`, `verified`, `unverified`, `follow`, `like`, `like-via-repost`, `repost`, `repost-via-repost`

#### Scenario: Importance tiers match the table
- **WHEN** `NotificationChannelInstaller.install(context)` runs
- **THEN** the `reply`, `mention`, `quote`, `verified`, and `unverified` channels each have `importance = NotificationManager.IMPORTANCE_HIGH`
- **AND** the `follow` channel has `importance = NotificationManager.IMPORTANCE_DEFAULT`
- **AND** the `like`, `like-via-repost`, `repost`, and `repost-via-repost` channels each have `importance = NotificationManager.IMPORTANCE_LOW`

#### Scenario: Install is idempotent
- **WHEN** `NotificationChannelInstaller.install(context)` is invoked twice in succession
- **THEN** `NotificationManager.notificationChannels.size` after the second call equals the size after the first
- **AND** each channel's importance is unchanged

### Requirement: `onMessageReceived` parses gateway payloads

The application SHALL parse `RemoteMessage.data` into a typed `PushPayload` with fields `reason`, `uri`, `subject`, `recipientDid`, `actorDid`, `actorDisplayName`, and `actorHandle`. The `reason` SHALL be one of: `like`, `like-via-repost`, `repost`, `repost-via-repost`, `reply`, `mention`, `quote`, `follow`, `verified`, `unverified`. Malformed payloads (missing required keys, unknown reason) SHALL be dropped silently with a logged warning.

#### Scenario: Each known reason parses to the typed payload
- **WHEN** `PushPayload.parse(data)` is called with a `data` map whose `reason` is any of the ten known values and required keys are present
- **THEN** a non-null `PushPayload` with the matching `Reason` variant is returned

#### Scenario: Malformed payload is dropped
- **WHEN** `PushPayload.parse(data)` is called with a `data` map missing the `reason` key OR a `data` map whose `reason` is an unrecognized string
- **THEN** the call returns `null`
- **AND** no notification is published

### Requirement: Recipient DID must match active session

The application SHALL drop pushes whose `recipientDid` does not match the currently-active session DID. There SHALL be no exception to this rule.

#### Scenario: Cross-account push is dropped
- **GIVEN** the active session DID is `did:plc:alice`
- **WHEN** a push arrives with `recipientDid = did:plc:bob`
- **THEN** no notification is published
- **AND** the drop is logged for diagnostics

### Requirement: Foreground app suppresses system notifications

The application SHALL drop incoming pushes (skip `NotificationManagerCompat.notify`) when `ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(STARTED)` is true. This SHALL apply to all reasons in v1.

#### Scenario: Foreground push is dropped
- **GIVEN** the application is in the foreground (process lifecycle is at least `STARTED`)
- **WHEN** a push payload survives parse, recipient-DID, mute, and verifier filters
- **THEN** no system notification is posted

#### Scenario: Background push is shown
- **GIVEN** the application is in the background (process lifecycle is below `STARTED`)
- **WHEN** a push payload survives parse, recipient-DID, mute, and verifier filters
- **THEN** the corresponding system notification is posted via `NotificationManagerCompat.notify`

### Requirement: Cached mute list filters incoming pushes

The application SHALL maintain a cached `Set<String>` of muted DIDs in DataStore via `MutedActorRepository`. The cache SHALL be refreshed on app foreground when the last successful refresh is older than 12 hours. Refreshes SHALL paginate `app.bsky.graph.getMutes`. The mute-write path (future) SHALL invalidate the cache and force-refresh, bypassing the debounce. `PushDispatcher` SHALL drop incoming pushes whose `actorDid` is in the cached set.

#### Scenario: Push from muted actor is dropped
- **GIVEN** `MutedActorRepository.snapshot.value` contains `did:plc:bob`
- **WHEN** a push arrives with `actorDid = did:plc:bob`
- **THEN** no notification is published

#### Scenario: Foreground refresh skips within debounce window
- **GIVEN** the last successful mute refresh completed less than 12 hours ago
- **WHEN** the app enters the foreground
- **THEN** `app.bsky.graph.getMutes` is NOT called

#### Scenario: Foreground refresh proceeds when stale
- **GIVEN** the last successful mute refresh completed more than 12 hours ago
- **WHEN** the app enters the foreground
- **THEN** `app.bsky.graph.getMutes` is called and the snapshot is updated on success

### Requirement: Verification reasons filtered by trusted-verifier allow-list

The application SHALL maintain a hardcoded `TRUSTED_VERIFIERS` constant of DIDs. V1 SHALL contain exactly the official Bluesky verifier DID (`did:plc:z72i7hdynmk6r22z27h6tvur`). The application SHALL drop incoming `verified` and `unverified` pushes whose `actorDid` is not in `TRUSTED_VERIFIERS`.

#### Scenario: Verification from trusted verifier is shown
- **GIVEN** a push with `reason = verified` and `actorDid = did:plc:z72i7hdynmk6r22z27h6tvur`
- **WHEN** the dispatcher processes the push
- **THEN** the notification is published (subject to other filters)

#### Scenario: Verification from untrusted verifier is dropped
- **GIVEN** a push with `reason = verified` and `actorDid = did:plc:rando`
- **WHEN** the dispatcher processes the push
- **THEN** no notification is published

#### Scenario: Non-verification reasons unaffected by the verifier filter
- **GIVEN** a push with any reason other than `verified` or `unverified`
- **WHEN** the dispatcher processes the push
- **THEN** the trusted-verifier filter is not consulted

### Requirement: Notifications are grouped per reason with a summary

The application SHALL set `setGroup("nubecita:${reason}")` on every individual push notification. The application SHALL publish (idempotently) a per-reason group-summary notification with `setGroupSummary(true)` and an inbox-style template (e.g. "N new likes" with up to N actor lines). Individual notify-IDs SHALL be derived from `payload.uri.hashCode()`. Summary notify-IDs SHALL be derived from `("nubecita-summary:" + reason).hashCode()`.

#### Scenario: Notifications carry the per-reason group
- **WHEN** a push of reason `like` produces a notification
- **THEN** the notification's `group` is `"nubecita:like"`
- **AND** the notification's notify-ID equals `payload.uri.hashCode()`

#### Scenario: Group summary collapses the shade
- **WHEN** two or more notifications of the same reason exist
- **THEN** a summary notification with `setGroupSummary(true)` and notify-ID `("nubecita-summary:" + reason).hashCode()` exists
- **AND** the inbox-style template lists up to N actor display names

### Requirement: Tap deep-links via translated `nubecita://` URIs through existing handlers

The application SHALL set the notification's content intent (`PendingIntent`) to launch `MainActivity` with `data` derived from translating the AT-URI in `payload.subject ?: payload.uri`. The translation SHALL produce a `nubecita://profile/{didOrHandle}[/post/{rkey}]` URI that matches the existing manifest `<data android:scheme="nubecita" android:host="profile" />` `<intent-filter>`. The application SHALL NOT set an `at://` URI on the Intent — Android does not register `at` as a URI scheme and no manifest `<intent-filter>` matches it. No new manifest `<intent-filter>` SHALL be introduced for this purpose.

#### Scenario: Tap on a post-shaped notification opens post detail
- **GIVEN** a push of reason `like` with `subject = "at://did:plc:alice/app.bsky.feed.post/abc"`
- **WHEN** the user taps the notification
- **THEN** the AT-URI is translated to `nubecita://profile/did:plc:alice/post/abc` and set as the Intent's `data`
- **AND** `MainActivity` receives the intent and `DeepLinkRouter` resolves it to the `PostDeepLinkKey` matcher
- **AND** the post detail screen is opened

#### Scenario: Tap on a follow-shaped notification opens the actor's profile
- **GIVEN** a push of reason `follow` with `uri = "at://did:plc:bob/app.bsky.graph.follow/xyz"` and no `subject`
- **WHEN** the user taps the notification
- **THEN** the AT-URI is translated to `nubecita://profile/did:plc:bob` (the DID is extracted from the AT-URI's authority component; the `app.bsky.graph.follow/xyz` collection + rkey are discarded for the tap target)
- **AND** `MainActivity` receives the intent and `DeepLinkRouter` resolves it to the `Profile` matcher
- **AND** Bob's profile screen is opened

#### Scenario: Tap on a verification-shaped notification opens the recipient's profile
- **GIVEN** a push of reason `verified` with `uri = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.graph.verification/xyz"` and `recipientDid = "did:plc:alice"`
- **WHEN** the user taps the notification
- **THEN** the translation uses `recipientDid`, NOT the verifier DID from the AT-URI authority, and produces `nubecita://profile/did:plc:alice`
- **AND** the application opens Alice's profile (the user whose verification status changed), not the verifier's profile

### Requirement: Settings exposes a system-notifications entry point

The Settings screen SHALL include a "Notifications" row that opens the Android system notification settings for the application package. Tapping the row SHALL fire `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)` with `Settings.EXTRA_APP_PACKAGE = packageName`. The application SHALL NOT provide per-reason in-app toggles in v1.

#### Scenario: Notifications row fires the system intent
- **WHEN** the user taps the "Notifications" row in Settings
- **THEN** an `Intent` with action `android.settings.APP_NOTIFICATION_SETTINGS` and the application's package name extra is started

### Requirement: Firebase Messaging is available on the runtime classpath

The application SHALL bundle `firebase-messaging` as a runtime dependency in `:app`, version-managed by the existing `firebase-bom` pin already used by the Analytics / App Check / App Distribution modules. No explicit initialization in `Application.onCreate()` is required for Messaging itself — FCM auto-initializes via its `ContentProvider`, gated by the manifest's `firebase_messaging_auto_init_enabled` meta (set to `false` for instrumented-test safety; re-enabled at runtime via `PushRegistrationCoordinator.start()` once the real `NubecitaApplication` has booted).

#### Scenario: Firebase Messaging class is on the runtime classpath
- **WHEN** the debug APK is built
- **THEN** the resulting `classes.dex` contains the `com.google.firebase.messaging.FirebaseMessaging` class
- **AND** `FirebaseMessaging.getInstance()` returns a non-null instance at runtime

#### Scenario: FCM token can be retrieved post-onCreate
- **WHEN** the application's `onCreate()` has returned and the device has network connectivity
- **AND** any caller invokes `FirebaseMessaging.getInstance().token.await()` from kotlinx-coroutines-play-services
- **THEN** a non-empty FCM token string is returned within FCM's normal retrieval timeout
