## Context

Nubecita is a phone-only Bluesky client. The full brainstormed design, with the review log, is `docs/superpowers/specs/2026-10-25-wear-os-app-design.md`. This document records the decisions the specs rest on. Relevant current state:

- **`:core:feed-cache`:** a Room cache of feed posts, keyed by account, feed type and URI. Only the home-screen widgets read it.
- **`:core:widget-sync`:** a 15-minute `WidgetRefreshWorker`, scheduled whenever the user is signed in (never in the bench flavor).
- **`:feature:chats:impl`:** the 15-minute `DmPollWorker`. It calls only `listConvos` and `getLog`, and skips while the app is in the foreground or message checking is off.
- **Token refresh:** OAuth refresh is single-use and the server deletes the session on replay. Every new background refresh caller is a logout risk, and the `RefreshCaller` telemetry tags today's callers.

Constraints:
- Battery beats freshness: no Doze workarounds, no new periodic work.
- Compose only: the latest Wear Compose M3 and Navigation 3, pre-release allowed.
- The repo's MVI, Hilt and Room conventions apply.

## Goals / Non-Goals

**Goals:**
- Offline feed browsing (up to 3 feeds).
- Offline likes, reposts and bookmarks, plus "Open on phone" from a post.
- Reading DMs, with quick replies, reactions, and voice or typed replies (W6).
- Short text posts from the watch, relayed through the phone (W6).
- An unread-DMs complication.
- No second Bluesky session, and no new background schedule.

**Non-Goals:**
- Standalone or LTE operation, and any network access from the watch.
- Video playback.
- Posts with media, quotes, replies to posts, and reply settings from the watch.
- Feed tiles.
- Notification inline-reply parity.
- Offline browsing in the phone's Feed screen (`nubecita-j16x`).
- A Pro gate.

## Decisions

1. **Phone-relayed companion over a standalone watch client.** The watch keeps only what the phone sends over the Wear Data Layer.
   - *Alternatives:* a standalone watch with its own OAuth session (a second token path that can be lost to refresh replay, plus LTE battery cost); a hybrid of the two (two data paths).
   - *Why:* one session, battery-friendly, and the content is the same.
2. **`DataItem`s for both snapshots and the action queue.** They persist across disconnection and are delivered when the devices reconnect, so the offline queue is built into the transport rather than being a retry loop we write.
   - Results come back as separate result items, and each side deletes what it consumed.
   - *Alternative:* `MessageClient` plus a custom queue (it loses messages while disconnected).
3. **Urgency by who is waiting.** Actions and the pull-on-open republish use `setUrgent()`, because a non-urgent item can wait up to 30 minutes. Periodic snapshots stay non-urgent so the system can batch them.
4. **Ride on the existing workers.** Publishing hooks into `WidgetRefreshWorker` and `DmPollRunner`, gated on the `nubecita_wear` capability.
   - Pull-on-open reuses the existing `refreshNow()` with `KEEP`, which keeps the `RefreshCaller.Widget` tag, and does no DM refresh.
   - *Alternative:* a dedicated watch sync job (another wakeup and another refresh caller).
5. **Fetch only the DM threads that changed.** The poll fetches messages only for the conversations `getLog` marks as changed, merged into a small phone-side Room cache. The first poll after pairing seeds about 10 conversations.
   - *Alternatives:* last message only (too little context for a reply); refetching everything every 15 minutes (battery).
6. **Module ownership through a multibinding.** `:core:wear-sync` defines a `WearActionHandler` interface, and feature modules contribute `@IntoSet` handlers. The DM pieces stay in `:feature:chats:impl`.
   - *Alternative:* moving `ChatRepository` into `:core` (a large refactor this change doesn't need).
7. **`:core:wear-contract` is shared and free of atproto runtime deps.** It holds kotlinx.serialization DTOs, `/v1/` paths and a `schemaVersion`. A watch that sees a newer major version keeps its data and asks the user to update.
8. **Watch UI stack.** It uses:
   - Wear Compose M3 and Navigation 3 `1.8.0-alpha02`;
   - one `AppScaffold`, with `ScreenScaffold` and `TransformingLazyColumn` everywhere;
   - `rememberSwipeDismissableSceneStrategy()`;
   - its own black theme, not `:designsystem`;
   - Wear preview annotations and screenshot tests.

   Pre-release APIs are accepted on purpose; breakage on version bumps is absorbed per epic.
9. **Optimistic overlay with silent revert for likes and bookmarks.** Pending actions are overlaid on the synced viewer state, mirroring the phone's post-interactions overlay. DM replies instead show a visible "Sending…" state, then "Not sent — tap to retry" if they fail.
10. **Action expiry.** Every action carries `createdAt` and `accountDid`. Replies and reactions expire after 1 hour, while likes and bookmarks never expire. Actions for any account other than the signed-in one are dropped.
11. **Release.** The Wear bundle goes to dedicated Wear form-factor tracks, with a version-code scheme independent of the phone's (the packaging docs' target-SDK-prefixed example). A closed-testing release comes before the Wear OS opt-in, and production follows with W2.

12. **System `RemoteInput` for voice and text (W6).** The watch launches `RemoteInputIntentHelper` (voice, keyboard and emoji in one system flow), then shows its own review screen with a grapheme counter before sending. Limits match the phone: 1000 graphemes for a DM, 300 for a post.
    - *Alternative:* a custom `SpeechRecognizer` UI (needs `RECORD_AUDIO`, costs battery, and duplicates what the system already does well).
13. **Watch posts are a new `create-post` action, gated by a capability (W6).** The phone runs it through `PostingRepository.createPost`, so facets, `langs` and the `create_post` event match the composer. It expires after 1 hour like a DM reply. The watch offers it only while the phone advertises `nubecita_phone_post_actions`. Free-text DM replies reuse `dm-reply`, which older phones already send verbatim, so they need no gate.
    - *Alternative:* a `supportedActions` list in the session snapshot. It would be more precise, but every earlier gate (W3, W4) is a capability, and one mechanism is easier to reason about.

## Risks / Trade-offs

- **[Alpha Wear Compose and Nav3 APIs change]** → Pin exact versions in the catalog, follow the `wear-compose-m3` skill, and bump them in a dedicated PR with a screenshot triage.
- **[Snapshot freshness is bounded by the 15-minute workers and Doze]** → Accepted. The UI always shows "Updated N min ago", and pull-on-open covers the case where the user is actively looking.
- **[A 100 KB `DataItem` limit with many images]** → Images go as `Asset`s at about 320 px WebP. Avatars are deduplicated per snapshot, and snapshot sizes are capped.
- **[A delayed reply arrives out of context]** → The 1-hour expiry, plus retry.
- **[Play form-factor track names and the review process]** → Verify them against the Play Developer API in W1. The Play Console Wear enrollment is an owner step that blocks only the release part.
- **[The extra DM fetches cost battery]** → Only for changed threads, only while a watch is paired, and with the poll's existing gates.
- **[Dictation mistakes go out under the user's name]** → Every voice or typed input goes through a review screen before Send; posts can't be edited after posting.
- **[Account switch while actions are queued]** → `accountDid` is checked on the phone, and the watch wipes its data when the account changes.

## Migration Plan

Additive: there is nothing to migrate on the phone.
- **Phone changes:** the snapshot publishing and the extra DM fetches stay inert until a node advertises `nubecita_wear`.
- **Sequencing:** W1 (internal plus closed testing) → W2 (production launch) → W3 → W4 → W5.
- **Rollback:** halt the Wear form-factor rollout in Play. Phone behavior without a watch is unchanged.

## Open Questions

- The exact Play Developer API track names for the Wear form factor (resolved in W1).
- The quick-reply phrase list and its localizations (W4).
- Whether the watch gets Crashlytics or Analytics in W1 or later.
