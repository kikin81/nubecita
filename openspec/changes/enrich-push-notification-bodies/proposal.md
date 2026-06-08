## Why

Social push notifications for **reply / mention / quote** show only a generic line — "sean quoted your post" — while the official Bluesky client shows the **actual text of the notifying post** as the body. That text is the difference between a notification a user can triage from the shade and one they must open the app to understand.

The data is already free: the push gateway (our DracoBlue fork) decodes `PostRecord.Text` from the Jetstream firehose event in `handlePost` to detect reply/quote/mention targets, then **discards it**. The text is in memory, from a stream the gateway already consumes. So enriching the notification body needs **no new network call, no firehose change, no database** — which is exactly what keeps it scalable on the cheapest 256 MB shared-CPU Fly.io box for 100–5000 users.

The wrong way to do this would be a per-notification `getPosts` fetch (on the gateway or the device); that adds an HTTP call per event and, on-device, would violate the battery rule. We explicitly avoid it.

## What Changes

- **Gateway** (`~/code/atproto-push-gateway`, Go): for reply/quote/mention only, thread the already-decoded post text through to the notification. Render Bluesky-style — **title = the actor+reason line** ("sean quoted your post"), **body = the post text** — and also expose it as a `bodyText` field in the FCM `data` map. Truncate server-side to ~200 chars. Like/repost/follow are unchanged (no post body exists for them).
- **Android** (`nubecita`): add `bodyText` to `PushPayload`, and render it via `NotificationCompat.BigTextStyle` in `PushNotificationBuilder` so the foreground / custom-render path matches the gateway and long posts are expandable.
- **Rollout is gateway-first**: a `fly deploy` ships the richer body immediately for backgrounded Android (FCM auto-displays the `notification` block), no app release required. The Android `BigTextStyle` render is follow-up polish for the foreground/custom path.

## Capabilities

### Modified Capabilities
- `push-notifications`: reply / mention / quote notifications carry the notifying post's text as the body (server-rendered from the firehose event, truncated), and the client renders it as expandable big-text. Like / repost / follow notification content is unchanged.

## Impact

- **Gateway**: `internal/jetstream/consumer.go` (thread `post.Text` through `handlePost` → `sendNotification`; `formatNotification` body for the 3 reasons; a truncation helper) + Go tests. No new dependencies, no new network calls, no schema/DB change.
- **`:core:push`**: `PushPayload` gains `bodyText`; `PushNotificationBuilder` renders `BigTextStyle`. Unit tests.
- **Scalability**: per-event cost unchanged (firehose consumption is the fixed cost regardless of user count); the only per-notification network call (actor profile resolution) is already cached (1 h TTL, 10k entries); payload grows ~200 bytes (far under FCM's 4 KB). Cheapest Fly.io box remains sufficient.
- **Battery**: unaffected — this is the server-side social push path, not the on-device DM worker; no device-side network is added.
- **No privacy change**: the post text is public (it is already on the firehose); nothing private is added to the payload.
