# Gate New-Chat picker + FAB on DM availability

- **bd issue:** nubecita-j1gm (`fix(chats)`) — fast-follow after PR #349 (epic nubecita-b6uv)
- **Date:** 2026-05-30
- **Status:** design approved, ready for plan

## Problem

The New-Chat recipient picker (b6uv.6) lists actors the signed-in user cannot DM — both live
search/typeahead results and the cache-backed "Recent" list. Tapping one navigates into the thread
and dead-ends on "Can't send messages / This person has chosen not to receive direct messages." We
should not list someone we can't message.

Separately (deferred from PR #349 Copilot review): the New-Chat FAB on the Chats screen shows even
when the account is not DM-enrolled, so "new chat" dead-ends for a non-enrolled account.

Both are the same theme: gate chat affordances on availability.

## Protocol signal (verified)

AT Protocol exposes DM eligibility via `associated.chat.allowIncoming`, a string enum:

- `"none"` → nobody can DM → not messageable
- `"following"` → only accounts the actor follows can DM → messageable iff `viewer.followedBy != null`
  (the actor's follow record points at the viewer)
- `"all"` / absent `associated.chat` → messageable (fail-open, matches official Bluesky clients)

The authoritative server check is `chat.bsky.convo.getConvoAvailability.canChat`, but that is a
per-actor round-trip — too heavy for typeahead. It stays **out of scope**.

Crucially, **both search responses already carry the hint**: `searchActorsTypeahead → ProfileViewBasic`
and `searchActors → ProfileView` both expose `associated?.chat?.allowIncoming` and `viewer?.followedBy`
(verified via `javap` on `io.github.kikin81.atproto` models-jvm 9.0.x). So the picker can filter with
**no extra network call**. (Implementation note: one exploration pass doubted this; trust the `javap`
verification and confirm at the mapper when wiring `toActorUi()`.)

## Existing precedent

`feature/profile/impl/.../data/AuthorProfileMapper.kt` already implements this gate as a private
`ProfileViewDetailed.canViewerMessage()` and uses it to hide the profile Message button via
`ProfileHeaderUi.canMessage`. The picker lacks the signal only because `:core:actors`'
`ProfileView/ProfileViewBasic.toActorUi()` mappers drop `associated` + `viewer`.

`avatarHueFor` in `:core:profile` (`core/profile/.../AvatarHue.kt`) is the precedent for promoting a
duplicated actor-surface helper into `:core:profile`.

## Design

Six-part change, all additive and fail-open.

### 1. Shared helper — `:core:profile`

New top-level function (e.g. `core/profile/.../DmAvailability.kt`), co-located with `avatarHueFor`:

```kotlin
fun canViewerMessage(associated: ProfileAssociated?, viewer: ViewerState?): Boolean =
    when (associated?.chat?.allowIncoming) {
        "none" -> false
        "following" -> viewer?.followedBy != null
        else -> true            // "all" / absent → fail-open
    }
```

Both `ProfileAssociated` and `ViewerState` are shared `io.github.kikin81.atproto.app.bsky.actor`
types carried by `ProfileView`, `ProfileViewBasic`, and `ProfileViewDetailed`, so all three call
sites pass `.associated, .viewer`.

Refactor `AuthorProfileMapper.kt` to call the shared helper and delete the private extension. Profile
Message-button behavior is **unchanged** (refactor only).

### 2. `ActorUi` — `:data:models`

Add `val canMessage: Boolean = true`. The `true` default is fail-open and keeps existing
constructions compiling.

### 3. `:core:actors` mappers

In `DefaultActorRepository`, both `ProfileView.toActorUi()` and `ProfileViewBasic.toActorUi()` set
`canMessage = canViewerMessage(associated, viewer)`. Adds a `:core:actors → :core:profile`
dependency.

### 4. Room v3 → v4

Add a `can_message` column to `ActorEntity` (`NOT NULL DEFAULT 1` → fail-open for rows cached before
the migration). Additive `@AutoMigration(from = 3, to = 4)`; commit the new
`core/database/schemas/.../4.json`. Round-trip `canMessage` through `asExternalModel()` and
`toCacheEntity()`; `writeThrough` persists the value computed at search time.

### 5. Picker filter — `NewChatViewModel`

Apply `.filter { it.canMessage }` to:

- the recent path (inside the `recentActors(selfDid)` map, alongside the existing self-filter), and
- the search path (right after the existing `it.did != selfDid` filter, before the `isEmpty()` check).

### 6. FAB gate — `ChatsScreenContent`

Hide the FAB when `state.status is ChatsLoadStatus.InitialError` and its `error == ChatsError.NotEnrolled`.

### Backstop (unchanged)

`ChatsErrorMapping` already maps `MessagesDisabled` / `NotFollowedBySender` → the "can't send
messages" screen. The client filter is a **hint**; this stays the authoritative post-tap catch and
covers cache staleness (the volatile "following" case) and appview lag.

## Data flow

```
searchActors / searchActorsTypeahead
  → ProfileView(Basic)  (carries associated + viewer)
  → toActorUi()         (computes canMessage = canViewerMessage(...))
  → writeThrough()      (persists can_message)
  → recentActors()      (reads can_message back)
  → NewChatViewModel    (filters !canMessage)
  → picker lists only messageable actors
```

## Edge cases / staleness

- Fail-open everywhere: absent signal ⇒ messageable.
- The `"following"` value is viewer-relative; the cached boolean can go stale on follow/unfollow
  after caching. Accepted, because the unchanged post-tap backstop catches it gracefully.
- Migration default `1` keeps pre-existing cached rows visible until the next search refreshes them.

## Out of scope

- `getConvoAvailability` pre-flight per recipient (only needed for zero false-positives; expensive).
- Changing the profile Message-button UX (e.g. disabled+explanatory button) — separate bd issue.

## Testing

- **Helper unit tests** (`:core:profile`): `none` / `following`+followedBy / `following` without /
  `all` / absent → expected boolean.
- **Mapper tests** (`:core:actors`): `ProfileView`/`ProfileViewBasic.toActorUi()` set `canMessage`
  correctly across the enum cases.
- **VM tests** (`:feature:chats:impl`): `NewChatViewModel` drops `!canMessage` from both the recent
  and search lists.
- **DAO / migration test** (`:core:database`): v3→v4 migration applies; `can_message` round-trips
  through upsert → `recentActors` → `asExternalModel`.
- **Screenshot** (`:feature:chats:impl`): FAB hidden in the `NotEnrolled` state — adjust
  `ChatsScreenContentScreenshotTest` (`ChatsScreenNotEnrolledScreenshot`, light + dark) and
  **regenerate baselines on Linux** (Mac baselines drift from CI on FAB shadows).
- `AuthorProfileMapperTest` should pass unchanged (profile behavior preserved by the refactor).

## Acceptance

- Picker (search + recent) excludes actors with `allowIncoming="none"` and `"following"` actors who
  don't follow the viewer; `"all"`/absent shown.
- A stale actor that passes the filter still hits the existing "can't send messages" backstop (no
  regression).
- New-Chat FAB hidden when Chats status is `InitialError(NotEnrolled)`.
- Profile Message-button gate unchanged.
