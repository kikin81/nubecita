## Why

The Chats tab home is a flat, single-list inbox: it cannot separate message requests from accepted chats, offers no per-conversation actions (leave / mute / report / block / go-to-profile), and has no safe way to remove a conversation — `chat.bsky.convo.leaveConvo` is irreversible server-side, so a naive immediate "Leave" loses a thread on a mis-tap. These are table-stakes for a native messaging surface.

## What Changes

- Add a **Chats / Requests** segmented toggle (pill tabs) above the conversation list, backed by `listConvos(status = accepted | request)`, with a count badge on Requests and graceful degradation when the requests fetch fails.
- Add a **multi-select contextual app bar** (Google-Messages style): long-press enters selection mode; the action set is derived from selection count × active segment (1 selected → Leave / Mute / Go-to-profile / Report / Block, or Accept / Leave in Requests; 2+ → bulk Leave / Mute, or bulk Accept / Leave). Report reuses the existing `feature-moderation` `Report.forAccount` NavKey; profile and block reuse existing routes.
- Add **leave-with-undo**: leaving (single or bulk) removes rows immediately and shows a Snackbar with Undo; the `leaveConvo` call is **deferred** until the snackbar dismisses, so Undo is a zero-network client-side restore. One pending batch at a time; a leave still pending at process death is dropped.
- Add four glyphs to the `NubecitaIconName` Material Symbols subset (`Logout`, `NotificationsOff`, `Flag`, `Block`) in a dedicated `:designsystem` change so the font regen's baseline blast radius does not ride a feature PR.

Non-goals (deferred): in-thread per-message delete (`deleteMessageForSelf`), any Pro/paywall gating, and thread-screen changes beyond opening a convo from selection.

## Capabilities

### New Capabilities
- `feature-chats`: the Chats tab home — conversation list, Chats/Requests segmentation, multi-select conversation actions (leave / mute / accept / report / block / open-profile), and the leave-with-undo deferred-deletion behavior.

### Modified Capabilities
<!-- No spec-level requirement changes to existing capabilities. feature-moderation's Report NavKey and design-system's icon set are reused as-is (the new glyphs are additive vendoring, not a requirement change). -->

## Impact

- **`:feature:chats:impl`** — `ChatRepository` / `DefaultChatRepository` (two-list cache + `leaveConvo` / `acceptConvo` / `setMuted`), `ChatsContract` (segment, selection, undo events/effects), `ChatsViewModel`, `ChatsScreen` / `ChatsScreenContent` (contextual app bar, pill tabs, snackbar undo).
- **`:designsystem`** — four additive `NubecitaIconName` entries + regenerated Material Symbols subset (own PR; `NubecitaIconShowcase` baseline regen via the `update-baselines` CI label).
- **Reuse (no change):** `:feature:moderation:api` `Report.forAccount`, the Profile route, the existing block path, the `ProfilePillTabs` component.
- **AT Protocol:** new `chat.bsky.convo` operations exercised — `leaveConvo`, `acceptConvo`, `muteConvo` / `unmuteConvo`, and `listConvos`'s `status` parameter (all present in the vendored SDK).
- **Battery:** no new background work — the extra `listConvos(status=request)` call rides the existing user-initiated refresh.
