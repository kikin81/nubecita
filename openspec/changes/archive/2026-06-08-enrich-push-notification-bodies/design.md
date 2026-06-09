## Context

The push gateway (DracoBlue fork) consumes the full Jetstream firehose, filters create events whose target DID is registered, resolves the **actor's** profile (cached: 1 h TTL, 10k-entry cap), server-renders a `Title`/`Body`, and sends an FCM message carrying **both** a `notification` block (Title/Body) and a `data` map. Backgrounded Android auto-displays the `notification` block — today that body is the generic "sean quoted your post" from `formatNotification`.

In `handlePost`, the gateway already unmarshals `PostRecord` (including `.Text`) to detect reply/quote/mention targets, then drops the text. The notifying post's text is therefore in hand for free, from the firehose event the gateway already processes.

## Goals / Non-Goals

**Goals:**
- reply/mention/quote notifications show the notifying post's text as the body, like the first-party client.
- Zero new network calls, no firehose/DB change — keep the cheapest Fly.io box viable for 100–5000 users.
- Ship the visible win without an app release (gateway-first).

**Non-Goals:**
- Bodies for like/repost/follow (no post text exists; first-party shows none either).
- The **quoted/parent** post's text (we use the *notifying* post's own text, which is what's free in the event; fetching the referenced post would be a per-event network call — rejected).
- Any on-device fetch/hydration (battery rule; also defeats the scalability point).
- DM notifications (separate `chat.bsky.convo` worker path; unaffected).

## Decisions

### D1. Text comes from the firehose event, never a fetch
For reply/quote/mention the body is `PostRecord.Text` from the create event already decoded in `handlePost`. No `getPosts`/`getPostThread`, on gateway or device. This is the load-bearing scalability decision: per-event cost is unchanged, so user count (100→5000) doesn't change the gateway's work — the firehose volume is the fixed cost and it's independent of how many users are registered.

### D2. Bluesky-style title/body split, 3 reasons only
For reply/mention/quote: `title` = the actor+reason line ("sean quoted your post"), `body` = the post text. Like/repost/follow keep today's templated body (`"%s liked your post"`) — there is no post text for them. Implemented by threading the text from `handlePost` → `sendNotification` → `formatNotification`; the other handlers pass empty text and are untouched.

### D3. Truncate server-side (~200 chars, on a rune boundary)
Bodies are truncated to ~200 characters with an ellipsis, cutting on a UTF-8 rune boundary (never mid-codepoint), before they enter the payload. Keeps the FCM payload well under 4 KB and the shade notification tidy; the full post is one tap away.

### D4. Carry it both in the `notification` block and as `data.bodyText`
The gateway sets the `notification.body` (so backgrounded auto-display shows it with no app change) **and** adds `bodyText` to the `data` map (so the client's custom/foreground render path can use it). Belt-and-suspenders across FCM's two delivery modes; the data field is the source of truth for the client render.

### D5. Android renders `data.bodyText` via `BigTextStyle`
`PushPayload` gains `bodyText: String?` (defaulted/nullable — older gateway payloads without it still parse). `PushNotificationBuilder` sets it as the content text wrapped in `NotificationCompat.BigTextStyle` so long posts expand in the shade. When `bodyText` is null/blank (like/repost/follow, or a pre-deploy gateway), behavior is exactly as today (title only).

**Scope of this path — foreground only.** Because the gateway payload carries a `notification` block (D4), when the app is **backgrounded** Google Play Services auto-displays the notification and `NubecitaFcmService.onMessageReceived` (hence `PushNotificationBuilder`) is **not** invoked — so `BigTextStyle` does not apply there. That case is already covered by D4: the OS renders `notification.body`, which the gateway has set to the post text, as a plain (system-collapsed/expandable) notification. `BigTextStyle` is therefore a foreground/custom-render enhancement, not the mechanism that delivers the post text in the background. Full custom styling + grouping for backgrounded notifications would require the gateway to switch to **data-only** payloads (drop the `notification` block) so `onMessageReceived` always fires; that is intentionally out of scope here (it trades off the no-app-release background win of D6) and is noted as a possible future change.

### D6. Gateway-first rollout
`fly deploy` ships the gateway change; backgrounded Android picks up the richer `notification.body` immediately — no app release. The Android `BigTextStyle` work is a separate follow-up PR that improves the foreground/custom-render path and long-text expansion. The two are independent and order-agnostic; gateway-first maximizes time-to-visible-value.

## Risks / Trade-offs

- **Notifying-post vs referenced-post text.** We show the *notifying* post's text (the reply/quote/mention itself), which is what's free in the event. For a quote, that's the quoting post's commentary — which is exactly what Bluesky shows — so this matches. For a reply, it's the reply text (correct). No risk, but documented so a future reader doesn't "fix" it into a fetch.
- **Payload size.** +~200 bytes; negligible vs FCM's 4 KB limit even with the existing fields.
- **Firehose backpressure** (pre-existing): the gateway drops events when its 1024-deep channel is full. This change adds no per-event latency (no I/O), so it doesn't worsen drop rate; just noting the existing counter to watch as the user base grows.
- **Two render paths** (FCM auto-display vs our custom builder): D4 covers both so the body is consistent regardless of which path fires.
- **Truncation across graphemes:** cutting on a rune boundary avoids broken codepoints; we accept that a multi-codepoint emoji/ZWJ sequence could be split at ~200 chars (cosmetic, rare, and the post is one tap away).
