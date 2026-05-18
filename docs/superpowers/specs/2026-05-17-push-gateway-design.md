# Push Notification Gateway — Design

> **Epic:** [`nubecita-1fy`](../../../README.md) — Push notifications + in-app notifications surface.
>
> **Status:** Approved 2026-05-17. Informs Slice 2 of `nubecita-1fy` (FCM push delivery). No bd children spawned yet; this spec is the architectural anchor those children will reference.
>
> **Scope:** Server-side push gateway infrastructure that lives **outside** the `nubecita` Android repo — a static DID document hosted on GitHub Pages and a Firebase Cloud Function. The on-device pieces (FCM registration, foreground bridge, notification rendering) are scoped separately in the parent epic.

## Design pivot from `nubecita-1fy`'s 2026-05-05 research

The epic's original research summary (still in `bd show nubecita-1fy`) reads:

> "Bluesky operates a centralized push relay that fans out to APNS / FCM. … no Nubecita-side backend required. … Bluesky's relay sends to whatever `appId` we register."

That model assumed Bluesky's notification gateway (`did:web:api.bsky.app`) was multi-tenant — i.e., the AppView would push to any FCM project whose `appId` we register. Subsequent investigation shows that is **not** how third-party clients integrate: Bluesky's official push gateway pushes only into Bluesky's own FCM project and only signs for the official Bluesky Android/iOS apps' package signatures. A third-party client (us) cannot have its FCM payloads relayed by Bluesky's gateway. This is by design — the AppView treats each registered `serviceDid` as the owner-operator of its own push fanout.

This spec lays out the corrected architecture: **we run our own minimal push gateway**, advertised as `did:web:nubecita.app`, and the Android app's `registerPush` call points at our gateway's service DID rather than Bluesky's. The bd description for `nubecita-1fy` should be updated to swap `serviceDid = "did:web:api.bsky.app"` for `serviceDid = "did:web:nubecita.app"` and remove the "no Nubecita-side backend required" line; that update is queued as a follow-up to this spec.

## Architecture at a glance

```
┌─────────────────────┐                 ┌──────────────────────┐
│ Android app          │                │  Bluesky AppView      │
│ (FirebaseMessaging-  │  registerPush  │  (notification         │
│  ServiceImpl)        │ ────────────► │   delivery worker)    │
│                      │  serviceDid=   │                       │
│  FCM token: <T>      │  did:web:      │                       │
└─────────────────────┘  nubecita.app  └──────────┬───────────┘
                                                    │
                              resolves DID via      │ POST
                              https://nubecita.app/ │
                              .well-known/did.json  │
                                                    ▼
                       ┌──────────────────────────────────────────┐
                       │ GitHub Pages: kikin81/nubecita-web         │
                       │   /.well-known/did.json                    │
                       │     id: did:web:nubecita.app               │
                       │     service[#bsky_notif].serviceEndpoint = │
                       │       https://us-central1-<project>...     │
                       └──────────────────────────────────────────┘
                                                    │
                                                    ▼
                       ┌──────────────────────────────────────────┐
                       │ Firebase Cloud Function `notify`           │
                       │   - parses Bluesky push-proxy payload      │
                       │   - fans out to FCM via firebase-admin     │
                       │   - returns {succeeded, failed} counts     │
                       └──────────────────┬───────────────────────┘
                                            │ data-only message
                                            ▼
                       ┌──────────────────────────────────────────┐
                       │ FCM → device → onMessageReceived           │
                       │ (Android client builds the system          │
                       │  notification per channel, or suppresses   │
                       │  per the Option-C foreground bridge)       │
                       └──────────────────────────────────────────┘
```

Two artifacts live outside the `nubecita` Android repo:

1. **`kikin81/nubecita-web`** — static GitHub Pages site serving `https://nubecita.app/`. Adds `.well-known/did.json` and `.nojekyll`.
2. **A new repo (or sub-folder) hosting the Firebase Functions project** — `firebase init functions` scaffold, TypeScript, deployed to a Firebase project that shares an FCM API key with the Android client's `google-services.json`.

## Assumptions to verify before deploying to public beta

1. **Bluesky's push-proxy → gateway payload shape is not in a public lexicon.** The wire format the AppView posts to a registered `BskyNotificationService` is defined in `bluesky-social/atproto`'s server module, not in `lexicons/app/bsky/notification/`. Before public beta, pin the exact shape against that source (search the open-source codebase for `BskyNotificationService` consumers). The schema sketched below is the *minimum-viable shape* that the gateway needs: token, platform, appId, title, body, optional collapse_key and data.
2. **Inbound authentication.** Bluesky's push service signs requests with a JWT (`Authorization: Bearer ...`). v1.0 of this gateway accepts unauthenticated POSTs and relies on URL obscurity + Cloud Functions per-IP rate limiting; **v1.1 must verify the JWT signature** against Bluesky's published signing keys before processing. Target: ship v1.1 before the Android app's public-beta milestone.
3. **Custom domain on the function** (e.g. `push.nubecita.app` via Firebase Hosting rewrite) is optional for v1 — the raw Cloud Run URL is fine. The URL only changes when we redeploy the function under a different name; deploys under the same name keep a stable URL.

## Phase 1 — GitHub Pages Static Identity (`kikin81/nubecita-web`)

### Files to add

**`.well-known/did.json`** — W3C DID Document, served at `https://nubecita.app/.well-known/did.json`:

```json
{
  "@context": [
    "https://www.w3.org/ns/did/v1"
  ],
  "id": "did:web:nubecita.app",
  "service": [
    {
      "id": "#bsky_notif",
      "type": "BskyNotificationService",
      "serviceEndpoint": "https://<REGION>-<PROJECT-ID>.cloudfunctions.net/notify"
    }
  ]
}
```

The `serviceEndpoint` is left as a `<...>` placeholder until Phase 2 deploys the function. Phase 2's close-out step rewrites it and pushes the update.

**`.nojekyll`** — empty file at repo root. Without this, Jekyll (the default GitHub Pages processor) silently drops any directory whose name starts with `.`, including `.well-known`, and the DID doc 404s. The `.nojekyll` marker disables Jekyll entirely for the repo, which is what we want — the site is pre-built static HTML.

### Steps

```bash
cd ~/code/nubecita-web   # path may vary
mkdir -p .well-known
cat > .well-known/did.json <<'EOF'
{
  "@context": ["https://www.w3.org/ns/did/v1"],
  "id": "did:web:nubecita.app",
  "service": [
    {
      "id": "#bsky_notif",
      "type": "BskyNotificationService",
      "serviceEndpoint": "https://example.invalid/placeholder"
    }
  ]
}
EOF
touch .nojekyll
git add .well-known/did.json .nojekyll
git commit -m "feat: add did:web:nubecita.app DID document for BskyNotificationService"
git push
```

### Verification

```bash
# Wait ~30 s for GitHub Pages to publish.
curl -i https://nubecita.app/.well-known/did.json
# Expect: HTTP/2 200, content-type application/json, body matches the doc.

# DID resolution sanity check: did:web resolvers fetch the well-known
# URL directly (no PLC directory call, unlike did:plc:*).
```

The Android app's `registerPush` call passes `serviceDid: "did:web:nubecita.app"`. Bluesky's AppView resolves the DID, reads the `BskyNotificationService` entry, and posts to the `serviceEndpoint` URL for every push event targeted at a Nubecita-registered account.

## Phase 2 — Firebase Push Gateway

### 2.1 Project setup (one-time)

CLI install + login:

```bash
npm install -g firebase-tools
firebase login
```

In the Firebase console:

1. **Create the project** (or share with an existing nubecita project). The Android `google-services.json` and this gateway MUST be backed by the same FCM project — device tokens registered client-side are scoped to that project, so a gateway pushing from a different project gets `INVALID_ARGUMENT` rejections.
2. **Upgrade billing to Blaze** (pay-as-you-go). Cloud Functions cannot make outbound network calls on the Spark/free tier; calling the FCM HTTP v1 endpoint from `firebase-admin` counts as outbound. Cost ceiling is bounded — FCM admin SDK calls hit Google's own backbone, and even the busiest individual user is at most a few thousand pushes/month.
3. **Enable Cloud Messaging API (V1)** under Project settings → Cloud Messaging. The legacy server-key API is being deprecated; v1 uses the default service-account credentials, no manual key handling.
4. **Note the project ID** for the `firebase init` step below.

### 2.2 Functions scaffold

In a new directory **outside** `nubecita` (e.g. `~/code/nubecita-push-gateway`):

```bash
mkdir nubecita-push-gateway && cd nubecita-push-gateway
firebase init functions
```

Choose:

- **Use an existing project** → the nubecita Firebase project.
- **Language:** TypeScript.
- **ESLint:** Yes — catches common mistakes early.
- **Install dependencies:** Yes.

Scaffold:

```
nubecita-push-gateway/
├── .firebaserc
├── firebase.json
└── functions/
    ├── package.json
    ├── tsconfig.json
    └── src/
        └── index.ts
```

### 2.3 Function implementation (`functions/src/index.ts`)

```typescript
import {onRequest} from "firebase-functions/v2/https";
import {logger} from "firebase-functions/v2";
import {initializeApp} from "firebase-admin/app";
import {getMessaging, Message} from "firebase-admin/messaging";

initializeApp();

/**
 * BskyNotificationService webhook.
 *
 * Bluesky's AppView posts here for every notification event targeted at
 * an account whose `serviceDid` is `did:web:nubecita.app`. Each entry
 * fans out to FCM using the firebase-admin SDK — the function runs as
 * the project's default service account, which is authorized to issue
 * pushes to any device token registered under this Firebase project.
 *
 * Wire format (UNVERIFIED — see assumption #1 in the spec):
 *
 *   {
 *     "notifications": [
 *       {
 *         "token":        "<fcm-device-token>",   // from registerPush
 *         "platform":     1 | 2,                  // 1=ios, 2=android
 *         "appId":        "net.kikin.nubecita",
 *         "title":        "Alice liked your post",
 *         "message":      "...",
 *         "topic":        "...",                  // optional
 *         "collapse_key": "...",                  // optional
 *         "data":         { ... arbitrary ... }
 *       }
 *     ]
 *   }
 *
 * Pin this against the bluesky-social/atproto source before public
 * beta; the shape above is a best-effort scaffold.
 */
export const notify = onRequest(
  {
    region: "us-central1",
    cors: false,
    // Bound abuse window while v1.0 has no JWT verification.
    maxInstances: 10,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({error: "Method not allowed"});
      return;
    }

    // TODO v1.1 — verify the JWT in `Authorization: Bearer <jwt>` against
    // Bluesky's known signing keys before processing. Until then, log
    // every caller IP for after-the-fact monitoring.
    logger.info("notify webhook hit", {
      ip: req.ip,
      ua: req.headers["user-agent"],
    });

    interface IncomingNotif {
      token: string;
      platform: number;
      appId?: string;
      title?: string;
      message?: string;
      topic?: string;
      collapse_key?: string;
      data?: Record<string, string>;
    }

    const body = req.body as {notifications?: IncomingNotif[]};
    if (!body?.notifications || !Array.isArray(body.notifications)) {
      res.status(400).json({error: "Missing notifications[]"});
      return;
    }

    const messaging = getMessaging();
    const results = await Promise.allSettled(
      body.notifications.map(async (n) => {
        const dataPayload: Record<string, string> = Object.fromEntries(
          Object.entries(n.data ?? {}).map(([k, v]) => [k, String(v)]),
        );

        const message: Message = {
          token: n.token,
          // Data-only payloads always wake `FirebaseMessagingService.onMessageReceived`
          // on Android — required by the foreground-bridge (Option C) so
          // the client can suppress + bus-event when the Notifications tab
          // is already open. The client constructs the system notification
          // and attaches the right channel (mentions=HIGH, likes=LOW, …).
          data: {
            ...dataPayload,
            ...(n.title ? {title: n.title} : {}),
            ...(n.message ? {body: n.message} : {}),
            ...(n.topic ? {topic: n.topic} : {}),
          },
          android: {
            priority: "high",
            ...(n.collapse_key ? {collapseKey: n.collapse_key} : {}),
          },
        };

        const messageId = await messaging.send(message);
        return {token: n.token, messageId};
      }),
    );

    const succeeded = results.filter((r) => r.status === "fulfilled").length;
    const failed = results.length - succeeded;
    logger.info("notify fan-out complete", {succeeded, failed});

    results.forEach((r, i) => {
      if (r.status === "rejected") {
        logger.warn("FCM send failed", {
          index: i,
          token: body.notifications![i].token.slice(0, 12) + "…",
          error: r.reason instanceof Error ? r.reason.message : String(r.reason),
        });
      }
    });

    res.status(200).json({succeeded, failed});
  },
);
```

### 2.4 Deploy

```bash
cd functions
npm run build
cd ..
firebase deploy --only functions:notify
```

The deploy step prints the function URL — capture it.

### 2.5 Close the loop

1. Edit `kikin81/nubecita-web`'s `.well-known/did.json`, replace the `example.invalid` placeholder with the deployed function URL.
2. Commit and push; GitHub Pages republishes within ~30 s.
3. Smoke-test end-to-end with a real device token (from the Android app's Logcat after first launch):

   ```bash
   curl -X POST https://us-central1-<project>.cloudfunctions.net/notify \
     -H 'Content-Type: application/json' \
     -d '{"notifications":[{"token":"<test-fcm-token>","platform":2,"title":"Test","message":"Hello"}]}'
   # Expect: {"succeeded":1,"failed":0}
   # Device receives the push and onMessageReceived fires.
   ```

## Outstanding work tracked for v1.1 (post-Android-beta)

| Concern | Mitigation |
|---|---|
| Unauthenticated webhook | Verify the JWT in `Authorization: Bearer <jwt>` against Bluesky's published signing keys. Reject with 401 if invalid or signed by an unknown key. Track the key set so it can rotate without redeploy. |
| Stale device tokens | When FCM returns `messaging/registration-token-not-registered`, write the token to a Firestore tombstone set. The Android client's foreground re-register reads the tombstone and skips re-registering tokens already known dead (one round-trip to clear). |
| Per-account quotas | Add per-account rate-limit (e.g. 100 pushes/min/account) once we see abuse patterns. Cloud Functions has account-level concurrency limits as a floor. |
| Custom domain | Wire `push.nubecita.app` via Firebase Hosting rewrite → `notify` function. Stable URL across redeploys + lets us migrate functions later without re-publishing the DID document. |
| Aggregate-payload support | If Bluesky's server starts batching ("Justin Bieber" Tier 3 defense), this function passes `data` through unchanged; the Android client handles the rendering. No server-side change here. |

## Follow-ups for the `nubecita-1fy` epic

- Update `bd show nubecita-1fy`'s description: swap `serviceDid = "did:web:api.bsky.app"` for `serviceDid = "did:web:nubecita.app"`, and remove the "no Nubecita-side backend required" line. The updated text should link to this spec.
- File child bd issues for the two phases of this spec under `nubecita-1fy`:
  - `nubecita-1fy.X`: deploy `did:web:nubecita.app` DID document to `kikin81/nubecita-web`.
  - `nubecita-1fy.Y`: stand up the Firebase push gateway (project setup + function + deploy + DID document rewrite).
- The on-device pieces (FCM registration via `registerPush`, foreground bridge via `CurrentScreenMonitor`, notification rendering, settings) remain scoped under `nubecita-1fy`'s existing slices; this spec doesn't change their shape.
