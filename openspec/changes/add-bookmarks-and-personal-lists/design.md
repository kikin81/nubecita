## Context

Nubecita already models the like/repost interactions as an optimistic toggle owned by `PostInteractionHandler` (a `by`-delegated handler injected into each screen's ViewModel) with a shared interactions cache that fans state out to every surface. The AT Proto bookmark lexicon ships in the pinned SDK (atproto 9.7.5): `app.bsky.bookmark.BookmarkService` exposes `createBookmark(StrongRef)`, `deleteBookmark`, and `getBookmarks(cursor)`; the feed-defs overlay adds `bookmarkCount: int` and `viewer.bookmarked: boolean` to `postView`. Bookmark is therefore structurally identical to like/repost and slots into the existing patterns.

GA4 (28 days) frames the priorities: likes are the top interaction (321 events / 16 users vs 10/5 reposts), while bookmarks are net-new with no usage signal. Hence the deliberate choice to ship the bookmark action **exposed** and measure before deciding its permanent home.

## Goals / Non-Goals

**Goals:**
- Bookmark a post from any surface (feed, post detail, profile) via one optimistic toggle, mirroring like/repost.
- Reflect `isBookmarked` / `bookmarkCount` on the post model from the `postView` overlay.
- Show a bookmark cell in the action row (between like and share) to gather real usage data.
- Provide own-profile personal lists: a Bookmarks route and a Your Likes tab.
- Instrument usage so the action-row-vs-overflow placement is a data-driven follow-up.

**Non-Goals:**
- No action-row → overflow demotion in v1 (that is the deferred, data-driven decision `nubecita-i8ny.4`).
- No viewing other users' bookmarks or likes — both are private/own-user only.
- No changes to existing like/repost/share behavior.
- No SDK/lexicon work — 9.7.5 already has it.

## Decisions

**D1 — Bookmark toggle rides `PostInteractionHandler`, not a bespoke path.**
Add `onBookmark(post)` to `PostInteractionHandler` + `DefaultPostInteractionHandler`: optimistic flip of `isBookmarked` (± `bookmarkCount`), per-URI single-flight guard, rollback on failure, and a cache update so every visible copy of the post reflects the new state — the exact shape `onLike` already uses. *Alternative considered:* a separate bookmark callback threaded per screen (like the image-tap callbacks) — rejected because it would repeat the "missable surface" problem and bookmark is a whole-`PostUi` interaction that fits the handler contract cleanly.

**D2 — New `:core:bookmarks` module for the boundary; toggle uses it, list uses it.**
`BookmarkRepository` wraps `BookmarkService` (`createBookmark`/`deleteBookmark` take a `StrongRef(uri, cid)`; `getBookmarks(cursor)` returns `bookmarkView` → `PostUi`). The handler calls the repo for the write; the profile Bookmarks screen calls it for the paginated read. *Alternative:* fold into `:core:post-interactions` — rejected because the list-fetch (`getBookmarks`) is a feed-shaped read that doesn't belong in the interaction handler's module; keep the interaction (write) and the repository (read) separable but in one domain module.

**D3 — Model state on the viewer.** Add `isBookmarked: Boolean` + `bookmarkCount: Int` to `ViewerStateUi`/`PostUi` (`@Stable`), mapped in `:core:feed-mapping` from `postView.viewer.bookmarked` + `postView.bookmarkCount`. Consistent with how `isLikedByViewer`/`likeCount` are carried.

**D4 — Action-row placement between like and share (6 cells).** Matches the official Bluesky app's mental model and keeps share visible. The row is a weighted layout so six cells fit; narrow-screen density is acceptable for the measurement window. Rendered as a `PostStat` **toggleable** cell (`toggleable = true`, `active = isBookmarked`): filled glyph when bookmarked (FILL axis), and a **static** `accessibilityLabel = "Bookmark"` — the on/off state is announced by the cell's `Role.Switch`, exactly like the `like` cell. (`onClickLabel`/dynamic verbs are for the non-toggle cells like reply/share; `PostStat` ignores `onClickLabel` when `toggleable = true`.)

**D5 — Expose first, demote by data.** Ship the cell visible; emit `interact_post action_type = bookmark/unbookmark`. After ~1 month, review the interaction rate; if low relative to the row real estate, move bookmark into `PostOverflowAction`. This makes placement an evidence-based decision instead of a guess.

**D6 — Profile surfaces asymmetric by intent.** Likes reads as *profile content* → a tab alongside Posts/Replies/Media (`getActorLikes`, own-profile only). Bookmarks reads as a *private utility* → an own-profile top-bar affordance opening a dedicated full-screen route (`getBookmarks`), avoiding a 5th pill tab. *Alternative:* both as tabs (rejected: pill-row crowding) or both as routes (rejected: likes is genuinely profile content and benefits from tab discoverability).

## Risks / Trade-offs

- **Action-row crowding at 6 cells on narrow phones** → mitigated by the weighted layout and the explicit D5 demotion escape hatch if data disfavors it.
- **Optimistic toggle drift if `createBookmark`/`deleteBookmark` fails silently** → per-URI single-flight + rollback + surface an error effect, identical to the like path already proven in `DefaultPostInteractionHandler`.
- **Bookmark glyph not actually in the subset font** (only the enum entry) → the `.1` chore verifies `` renders outlined + filled and re-runs `update_material_symbols.sh` only if missing (never regenerate the whole font).
- **`getBookmarks`/overlay field names differ from assumption** → confirm exact generated types (`bookmarkView`, `viewer.bookmarked`) against the 9.7.5 models jar during `.2`; this is a naming risk, not a feasibility risk.
- **Measuring a low-signal feature** → bookmarks start at zero usage by definition; D5's review window is the guard against over-investing in the surface.

## Migration Plan

Additive only — no data migration, no breaking changes. Ships in slices behind the natural dependency order (glyph → domain → exposed action → profile surfaces); the deferred review (`.4`) fires ~1 month after the action ships. Rollback for the action-row cell is trivial (the demotion path is already the planned D5 follow-up).

## Open Questions

- Exact generated request/response types for `getBookmarks` pagination and the `StrongRef` shape for create/delete — resolve by reading the 9.7.5 models during `.2`.
- Whether the Bookmarks route lives in a new `:feature:bookmarks` module or as a `feature/profile` sub-route — decide in `.5` based on how much it reuses profile list plumbing.
