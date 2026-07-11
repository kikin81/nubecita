## Why

Bookmarks are a first-class Bluesky feature Nubecita is missing, and there is no way to revisit your own likes — yet GA4 shows **likes are the #1 post interaction** (321 events / 16 users in 28d vs 10/5 for reposts), so a personal "your content" surface is clearly wanted. The AT Proto bookmark lexicon already ships in our pinned SDK (atproto 9.7.5: `BookmarkService` + `postView` overlay adding `bookmarkCount`/`viewer.bookmarked`), so the capability is unblocked today. Bookmarks themselves are net-new with no usage signal, so we ship the action **exposed** to measure real demand, then demote it if usage is low.

## What Changes

- Add **bookmark** as a first-class post interaction: an optimistic toggle (`createBookmark`/`deleteBookmark`) that rides the existing `PostInteractionHandler` so it works on every surface (feed, post detail, profile).
- Surface `bookmarkCount` + `isBookmarked` on the post model, mapped from the `postView` overlay's viewer state.
- Add a **bookmark cell to the post action row**, positioned **between like and share** (`reply · repost · like · bookmark · share · ⋯`), filled when bookmarked.
- Add a **Bookmarks list** reachable from an own-profile top-bar route, and a **Your Likes tab** on the own profile (Posts / Replies / Media / Likes). Both are private (own-user only).
- Emit `interact_post` analytics with `action_type = bookmark / unbookmark`, and schedule a **data-driven review (~1 month)** to demote the action-row cell into the overflow menu if usage is low.
- **Non-goals (v1):** no action-row demotion (that is the deferred, data-driven decision); no viewing other users' bookmarks or likes (private); no changes to like/repost behavior.

## Capabilities

### New Capabilities
- `post-bookmarks`: bookmark a post and reflect bookmark state (`isBookmarked`, `bookmarkCount`) — the domain module over `BookmarkService`, the model/mapping fields, the optimistic toggle through the interaction handler, and the exposed action-row cell.
- `profile-personal-lists`: personal-content surfaces on the signed-in user's own profile — a Bookmarks list (via `getBookmarks`) reached from a top-bar route, and a Your Likes tab (via `getActorLikes`).

### Modified Capabilities
- `data-models`: `PostUi` / `ViewerStateUi` gain `isBookmarked` + `bookmarkCount`.
- `core-feed-mapping`: map `postView.viewer.bookmarked` + `postView.bookmarkCount` into the post model. *(Implementation-level mapping; listed for traceability — no external requirement change if reviewers prefer to scope it under `post-bookmarks`.)*

## Impact

- **New module:** `:core:bookmarks` (wraps `BookmarkService`; needs `consumer-rules.pro`).
- **SDK:** none — atproto 9.7.5 (pinned) already ships the bookmark lexicon; no version bump.
- **Design system:** `NubecitaIconName` (bookmark glyph, likely already present), `PostStat` / `PostCard` action row, `PostOverflowAction` (only for the deferred demotion).
- **Interactions:** `PostInteractionHandler` + `DefaultPostInteractionHandler` (+ interactions cache) gain `onBookmark`.
- **Models / mapping:** `:data:models`, `:core:feed-mapping`.
- **Profile:** `feature/profile/impl` (`ProfileFeedTabBody` for the Likes tab, `ProfileTopBar` + a new route for Bookmarks).
- **Analytics:** reuse the `action_type` dimension (`bookmark` / `unbookmark`); new i18n strings (es-419, pt-BR).
- **Tracking:** beads epic `nubecita-i8ny` (children `.1`–`.6`).
