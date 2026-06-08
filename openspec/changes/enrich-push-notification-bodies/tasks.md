# Tasks — enrich-push-notification-bodies (bead nubecita-1fy.19)

> Two repos. Gateway-first (D6): §1 ships the visible win via `fly deploy` with no app release; §2 is the Android follow-up.

## 1. Gateway — post text in the notification body (`~/code/atproto-push-gateway`, Go)
- [ ] 1.1 In `internal/jetstream/consumer.go`, thread the already-decoded `post.Text` from `handlePost` into the reply/quote/mention `sendNotification` calls (a new `bodyText` parameter; like/repost/follow/verification handlers pass `""`).
- [ ] 1.2 Add a rune-safe truncation helper (~200 chars + ellipsis) and apply it to the post text before it enters the notification.
- [ ] 1.3 In `formatNotification` (or `sendNotification`): for reply/mention/quote, set title = the actor+reason line and body = the truncated post text; leave like/repost/follow on the existing templates. Set `data["bodyText"]` to the truncated text when present.
- [ ] 1.4 Go tests: quote/reply/mention bodies = post text + `bodyText` data field set; like/repost/follow bodies unchanged + no `bodyText`; truncation cuts on a rune boundary and bounds length; empty/whitespace post text falls back to the actor+reason line.
- [ ] 1.5 `go test ./...` + `go build`; deploy via `fly deploy` (config in `~/code/nubecita-push-config`). Manual: trigger a quote to a registered test account and confirm the shade body shows the post text.

## 2. Android — render `bodyText` as expandable big-text (`nubecita`)
- [ ] 2.1 Add `bodyText: String? = null` to `PushPayload` (`core/push/.../PushPayload.kt`) and parse it from the `data` map in `parse` (nullable — pre-deploy/like payloads omit it).
- [ ] 2.2 In `PushNotificationBuilder.build`, when `bodyText` is non-blank set it as `setContentText(bodyText)` + `setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))`; when null/blank, render exactly as today (title only).
- [ ] 2.3 Unit tests: `PushPayload.parse` reads `bodyText` and tolerates its absence; `PushNotificationBuilder` applies BigTextStyle when present and omits it when absent (assert via the builder's pre-Intent shape, consistent with the existing `tapIntentSpecFor` test pattern).

## 3. Verification
- [ ] 3.1 `:app:assembleDebug`, `:core:push` unit tests, `:core:push` lint, spotless green.
- [ ] 3.2 Confirm backward compatibility: a payload with no `bodyText` (and a `like`) renders unchanged. Confirm no new device-side network call (battery rule) — the client only reads `bodyText` from the payload.
