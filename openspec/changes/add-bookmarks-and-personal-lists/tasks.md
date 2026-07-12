<!--
Beads epic: nubecita-i8ny. Each task group maps 1:1 to a child bead (id noted in the heading).
Slice order follows the dependency chain: glyph → domain → exposed action → (deferred review); profile lists depend on domain.
-->

## 1. Bookmark glyph — bead nubecita-i8ny.1 (chore)

- [ ] 1.1 Verify `NubecitaIconName.Bookmark` (``) is actually present in `designsystem/src/main/res/font/material_symbols_rounded.ttf`; render it outlined and filled (`NubecitaIcon(filled = true/false)`) in a preview.
- [ ] 1.2 If the glyph is missing from the subset font, re-run `scripts/update_material_symbols.sh` (fonttools) and commit the subset-font diff alongside the enum — do NOT hand-edit or regenerate the whole font.

## 2. Bookmark domain — bead nubecita-i8ny.2 (feature)

- [ ] 2.1 Create `:core:bookmarks` module (mirror `:core:post-interactions`); add empty `consumer-rules.pro`; wire the convention plugin + Hilt.
- [ ] 2.2 Confirm the exact 9.7.5 generated types: `BookmarkService.createBookmark`/`deleteBookmark` request shapes (StrongRef uri/cid), `getBookmarks` cursor + `bookmarkView` response, and the `postView` overlay field names (`viewer.bookmarked`, `bookmarkCount`).
- [ ] 2.3 Add `BookmarkRepository` (interface + default impl over `BookmarkService`, `@IoDispatcher`): `create(uri, cid)`, `delete(uri, cid)`, `getBookmarks(cursor)` → `Result` types mapping `bookmarkView` → `PostUi`.
- [ ] 2.4 Add a bench fake `BookmarkRepository` + DI variant module.
- [ ] 2.5 Add `isBookmarked: Boolean` + `bookmarkCount: Int` to `ViewerStateUi` / `PostUi` (`@Stable`); update fixtures.
- [ ] 2.6 Map `postView.viewer.bookmarked` + `postView.bookmarkCount` in `:core:feed-mapping`; update mapper fixtures.
- [ ] 2.7 Unit tests: repository (create/delete/get) and mapper (present / absent viewer state).

## 3. Exposed bookmark action — bead nubecita-i8ny.3 (feature; depends 1, 2)

- [ ] 3.1 Add `onBookmark(post)` to `PostInteractionHandler` + `DefaultPostInteractionHandler`: optimistic flip of `isBookmarked` (± `bookmarkCount`), per-URI single-flight, rollback on failure, cache update; error surfaced via the interaction-effect channel.
- [ ] 3.2 Log `interact_post` with `action_type = bookmark` / `unbookmark`.
- [ ] 3.3 Add a bookmark cell to the action row (`PostStat` / `PostCard` `ActionRow`) between like and share, as a **toggleable** cell (`toggleable = true`, `active = isBookmarked`, static `accessibilityLabel = "Bookmark"`) — mirrors the `like` cell's `Role.Switch` semantics; filled glyph when bookmarked. Do NOT use `onClickLabel` (ignored on toggleable cells).
- [ ] 3.4 Add es-419 + pt-BR strings for the new labels.
- [ ] 3.5 Update screenshot baselines (feed / post detail / profile) for the new cell; add a handler unit test (`DefaultPostInteractionHandlerTest`) for bookmark toggle + rollback.
- [ ] 3.6 Smoke on the bench flavor: tap bookmark on a post, confirm filled state persists in a screenshot.

## 4. Data-driven placement review — bead nubecita-i8ny.4 (task; deferred ~1 month after slice 3 ships)

- [ ] 4.1 ~1 month post-ship, pull `interact_post action_type=bookmark` from GA4 (events + distinct users) vs like/repost.
- [ ] 4.2 If usage is low relative to the action-row real estate, demote: add `BookmarkPost` / `RemoveBookmark` to `PostOverflowAction`, wire through `onOverflowAction`, and remove the action-row cell. Otherwise keep it and record the decision.

## 5. Profile bookmarks list — bead nubecita-i8ny.5 (feature; depends 2)

- [ ] 5.1 Add a Bookmarks entry point to the own-profile top bar (`ProfileTopBar`, `ownProfile` branch) that navigates to a dedicated route.
- [ ] 5.2 Build the Bookmarks list screen (VM + `getBookmarks` pagination via `BookmarkRepository`), rendering `PostCard`s with working interactions; decide new `:feature:bookmarks` module vs `feature/profile` sub-route. The VM MUST merge the shared interactions `cache.state` into its list (like/repost/bookmark toggles made elsewhere stay in sync) and seed the cache from the fetched posts — mirror the Feed/Profile VM cache read-merge.
- [ ] 5.3 Empty + error + loading states; gate to the signed-in user only.
- [ ] 5.4 VM unit tests + screenshot tests.

## 6. Profile Your Likes tab — bead nubecita-i8ny.6 (feature)

- [ ] 6.1 Add an own-profile-only Likes tab alongside Posts / Replies / Media (`getActorLikes` pagination); mirror `ProfileFeedTabBody` + the tab/status architecture. The tab's data path MUST merge the shared interactions `cache.state` and seed the cache (so like/repost/bookmark state stays fresh), same as the other profile tabs.
- [ ] 6.2 Gate the tab to the signed-in user's own profile; empty + error + loading states.
- [ ] 6.3 VM unit tests + screenshot tests.
