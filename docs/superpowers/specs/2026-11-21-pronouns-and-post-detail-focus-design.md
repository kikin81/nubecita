# Pronouns, cache-first profiles & threads, and the post-detail focus redesign

Date: 2026-11-21 · Status: design approved pending review · Absorbs: `nubecita-dwmf`

## Intent

Bluesky shipped profile pronouns. Nubecita should match the official client:
show them on profiles, let people edit them, and show them where the official
app does: next to a reply-parent's author in the composer and on the focused
post in post detail. While touching post detail, give the focused post room to
breathe (official Bluesky / Threads layout instead of our padded card), and make
both profiles and threads open **instantly from Room**, refreshing behind.

The user's priorities, in their words: parity with the official app, "use Room
caching as much as possible to make it as fast as possible", "handle migrations
as needed", and land the whole thing as **one `gh stack`** (one epic, one release).

## Goals

1. **Pronouns, read:** profile hero (`@handle  he/him`), post-detail focused post
   (`@handle · he/him`), composer reply parent (`Cameron Banga  He / Him`).
2. **Pronouns, write:** an Edit profile "Pronouns" row opening a bottom sheet:
   preset chips (she/her · he/him · they/them), a free-text field, **Done**,
   **Remove pronouns**. Presets are localized (see Decisions).
3. **Focused-post redesign (option A, chosen):** no container; two-line header
   with a Follow toggle; full date-time line; counts row; then the action row.
4. **Cache-first profiles** (`nubecita-dwmf`, widened): any profile you've opened
   renders from Room immediately, then refreshes from `getProfile`.
5. **Cache-first threads:** opening a post renders the last-seen thread (or at
   least the focused post) from Room immediately, then refreshes from
   `getPostThread`.

## Non-goals

- Pronouns on feed / profile / search / notification post cards, people lists, chats.
- Caching profile **tabs** (posts/replies/media lists) — header only.
- Tappable counts (likers / reposters / quoters lists).
- Shared-element transitions from feed card → focused post.
- Avatar image caching — Coil's disk cache already covers it (user confirmed).
- Offline mode as a product feature; caches are a speed layer that always revalidates.

## Anti-goals

- Feed / reply `PostCard` rendering changing at all (no screenshot churn outside
  post detail, profile hero, edit profile, composer).
- Showing a stale cached view **without** a refresh behind it, or a cache that
  silently grows without bound.
- Any derived/client-side pronoun state; the record field is the source of truth.
- Breaking the existing read-modify-write in `DefaultProfileRepository.updateProfile`
  (it must still preserve every other record key).

## Constraints & facts (verified)

- **No SDK / lexicon bump needed.** `atproto 9.17.1` (already in
  `libs.versions.toml`) exposes `pronouns: String?` on `app.bsky.actor.profile`
  (record) and on `ProfileView`, `ProfileViewBasic`, `ProfileViewDetailed`.
  atproto-kotlin's pinned lexicon CIDs equal the live upstream CIDs.
- Record constraint: `maxGraphemes = 20`, `maxLength = 200` bytes. Free-form.
- Room is at **v11** (`core/database/schemas/.../11.json`). This epic does **one**
  bump to **v12** via `@AutoMigration(11 → 12)` (additive tables only).
- `cached_post` is per-account (`account_did`) and GC'd by
  `CachedPostDao.deleteUnreferenced`, which today only counts `feed_post` and
  `feed_item_member` references.
- Post detail today: `PostThreadRepository.getPostThread` is network-only; the
  focus is `PostCard` inside a `Surface(surfaceContainerHigh, 24dp corner)` with
  12dp/8dp outer padding (double padding + wrong surface role).
- Profile today: `feature/profile/impl` `ProfileRepository.fetchHeader` is
  network-only; follow/unfollow already exist there and in `:core:post-interactions`
  `FollowRepository`.
- Composer reply parent uses its own `ParentPostUi` (via `DefaultParentFetchSource`),
  not `AuthorUi`.

## Approaches considered

### Focused-post container
- **A. No container; emphasis via type, shape, motion — CHOSEN.** One 16dp gutter,
  full-width body/media, larger body type, expressive Follow `ToggleButton` shape
  morph. Fixes padding completely; focus is marked by layout/type, not colour.
- B. Full-bleed `surfaceContainer` tonal band. Keeps a colour highlight, no width
  lost, but adds a colour seam into replies and reads heavy on OLED.
- C. Tighten the existing card. Cheapest, but keeps the double padding the user
  disliked.

### Where focused-post pronouns go
- One-line author row with a `showAuthorPronouns` flag on `PostCard` (rejected:
  crowds the name-priority row). Superseded by the two-line focus header, where
  pronouns sit on the handle line.

### Profile cache shape
- **Wire-blob table (CHOSEN, builder may revisit with reason):** `cached_profile`
  stores the `profileViewDetailed` JSON plus a few denormalized columns, mirroring
  `cached_post.postBlob`. New lexicon fields (like pronouns) need no future
  migration; the mapper stays the single wire→UI path.
- Column-per-field `ProfileEntity` or widening `ActorEntity`: every new field is a
  schema bump; rejected.

## Design

### Visual: the focused post (A)

```
[avatar] Christian Alder ✓              ( Follow )
         @hejchristian.com · he/him
I'm a sucker for a nice font…            ← one type step above reply body
╭──────── embed ────────╮                ← embeds keep their own large shapes
8:35 AM · Nov 21, 2026                   ← locale-formatted absolute time
44 reposts  5 quotes  193 likes  102 saves   ← zero counts omitted
──────────────────────────
💬 5   🔁 49   ♡ 193   🔖   ↗   ⋯          ← existing action row
══════════════════════════  replies
```

- New design-system composable (e.g. `FocusPostCard`) reusing PostCard's internal
  body / embed / action-row pieces; `PostCard` itself and its baselines unchanged.
  Lives on the `surface` canvas, no container, 16dp horizontal gutter.
- Header: avatar; name (+ verified badge) on line 1; `@handle · pronouns` on line
  2 (pronouns omitted when null/blank). No relative timestamp in the header.
- **Follow toggle:** M3 Expressive `ToggleButton`, small size. Shown when the
  author is not the signed-in account and not blocked. Unchecked = "Follow"
  (tonal, pill shape); checked = "Following ✓" (shape morphs to rounded-square).
  Hidden entirely when already following at load (matches official app); once the
  user taps Follow in-session it stays visible as "Following" so the row doesn't
  jump. Tapping "Following" unfollows. Optimistic with rollback + snackbar on
  failure, via `FollowRepository`.
- Counts row: reposts, quotes, likes, saves from `PostStatsUi`; omit zero entries;
  hide the row if all are zero. Not tappable. Localized plurals.
- Divider between action row and replies. Replies keep current `PostCard`.

### Visual: profile hero & composer

- Hero: `@handle` then pronouns on the same line, `onSurfaceVariant`, as in the
  official screenshot. Hidden when null/blank.
- Composer reply parent: `displayName  pronouns` (pronouns `onSurfaceVariant`).

### Visual: edit pronouns

- Edit profile gains a "Pronouns" field row (after Bio) showing the current value
  or a placeholder; tap opens a `ModalBottomSheet`: title, helper text, preset
  `FilterChip`s, an `OutlinedTextField` (20-grapheme counter), **Done** (primary),
  **Remove pronouns** (tonal; disabled when empty). Selecting a chip fills the
  field; editing the field deselects non-matching chips. Done updates the form
  state (dirty), Save writes. Remove sets null → key omitted from the record.

### Data

- `AuthorUi` gains `pronouns: String? = null` and `viewerFollowUri: String? = null`
  (follow-record URI; non-null ⇒ following). Defaults keep every fixture and
  baseline unchanged. Filled in `FeedMapping.toAuthorUi` from `ProfileViewBasic` /
  `ProfileView`.
- `ProfileHeaderUi` gains `pronouns: String? = null` (mapper from
  `ProfileViewDetailed`).
- `ParentPostUi` gains `pronouns: String? = null`.
- `updateProfile` takes a pronouns parameter; read-modify-write sets or removes
  the `pronouns` key, preserving everything else. Its existing test is extended.

### Room v12

- **`cached_profile`** — PK `(account_did, did)`; columns: `handle`, `blob`
  (wire `profileViewDetailed` JSON), `fetched_at`, `last_opened_at`. Upsert on
  every successful `getProfile` and after own-profile save. Cap: keep the 200
  most-recently-opened rows per account, own profile always kept. Cleared with the
  account (same hook as `cached_post.clearAccount`).
- **`thread_snapshot`** + **`thread_snapshot_item`** — snapshot PK
  `(account_did, focus_uri)` with `fetched_at`, `last_opened_at`; items PK
  `(account_did, focus_uri, position)` with `kind` (ancestor/focus/reply/blocked/
  not_found) and `post_uri`, posts themselves stored in `cached_post` (its
  documented role as the single post store). Cap: 50 snapshots per account by
  `last_opened_at`.
- `CachedPostDao.deleteUnreferenced` gains a `NOT EXISTS` clause for
  `thread_snapshot_item` so thread posts aren't collected out from under a
  snapshot, and snapshot eviction runs before post GC.

### Cache-first read paths

- **Profile header:** repository exposes `observeHeader(actor): Flow` = Room row
  (if any) → network refresh → write-through → Room re-emits. ProfileViewModel
  renders cached header immediately (no shimmer), keeps its existing error effect
  if the refresh fails while a cached header is shown (snackbar, not error screen).
  Tabs unchanged.
- **Post detail:** on open, emit the snapshot if one exists; else if
  `cached_post` has the focus URI, emit `[Focus]` alone with replies loading; else
  today's loading state. Then `getPostThread` → write snapshot + posts in one
  `@Transaction` → re-emit. `PostInteractionsCache.seed` runs on both cached and
  fresh emissions (fresh wins). Mute/delete projections apply to both.

## Decisions (user)

- Pronouns surfaces: profile hero, edit, composer reply parent, post-detail focus.
- Focused post: option A (no container), plus Follow toggle, two-line header,
  absolute time, counts row.
- Cache aggressively with Room; migrations as needed; fold in `nubecita-dwmf`.
- Avatars: Coil is sufficient; no work.
- Ship as one epic / one `gh stack`.

## Calls made by the designer (correct any)

- Localized presets: en `she/her · he/him · they/them`; es-419 `ella · él · elle`;
  pt-BR `ela · ele · elu`. Saved value = exactly what's selected/typed.
- Follow toggle hidden if already following at load; stays as "Following" after an
  in-session follow; tapping Following unfollows.
- Counts row omits zero counts; not tappable.
- Wire-blob profile cache; one v11→v12 migration for both new tables; caps 200
  profiles / 50 threads per account.
- `nubecita-dwmf` closed as absorbed into the new epic.

## Delivery: stack order (bottom → top)

1. `core/database`: v12 tables, DAOs, AutoMigration, `deleteUnreferenced` update, schema JSON.
2. Models + mappers: `AuthorUi`, `ProfileHeaderUi`, `ParentPostUi` pronouns/follow fields.
3. Cache-first profile header + pronouns in the hero.
4. Edit pronouns sheet + save.
5. Cache-first post detail (snapshots).
6. Focused-post redesign + Follow toggle.
7. Composer reply-parent pronouns.

At least one child PR is titled `feat:` so the epic reaches Play. Each child needs
es-419/pt-BR strings in the same commit, unit tests for logic, and screenshot
tests for new UI (focus card, sheet, hero, composer parent). Device pass on the
bench build; bench fixtures get pronouns on a few authors. Full gate
(compose-expert review on the cumulative diff) on the top branch before
`gh stack merge`.

## Left to the builder

- Exact type token for the focused body (one step above `bodyLarge`) and spacing values.
- How `FocusPostCard` shares PostCard internals (extract `internal` pieces vs. slots).
- Whether profile `observeHeader` lives in `:core:profile` or stays in the
  feature's `ProfileRepository`.
- Date-time formatter choice (must be locale-aware and screenshot-deterministic).
