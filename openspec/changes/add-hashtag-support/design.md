## Context

Hashtags in the AT Protocol are rich-text facets (`app.bsky.richtext.facet#tag`) that the client computes and attaches to the post record at publish time. The server doesn't parse `#` out of post text. Nubecita's current state:

- **Composer:** `:core:posting` `DefaultFacetExtractor` builds mention and link facets only. Tags are never emitted.
- **Rendering:** `:designsystem` `PostCard` styles `FacetTag` spans with `linkStyle` but attaches no click handler.
- **Tap routing:** `FacetTarget` in `:data:models` is a sealed interface with `Mention` and `Link`. Seven hosts `when` over it: feed, post detail, profile, bookmarks, chats, search posts, and notifications through the shared post card.
- **Search:** `:feature:search:impl` has `SearchPostsViewModel` and `DefaultSearchPostsRepository` (v1 `searchPosts`, `q` + `sort`, moderation-filtered). The module is flavored and ships a bench fake.
- **SDK:** atproto-kotlin `models` 9.13.0 already has `SearchPostsV2Request` / `Response`, which include `hashtags`, `authors`, `sort`, `allTime`, `cursor` and `hitsTotal`.

### Research summary (2026-10-24)

- **Official social-app:**
  - Composer detection uses `TAG_REGEX` / `CASHTAG_REGEX` from the SDK's `rich-text/util`.
  - On native, a tap goes straight to `/hashtag/:tag` and a long-press opens a menu with "See posts", "See posts by user" and "Mute/Unmute".
  - The Hashtag screen has Top and Latest tabs over `searchPostsV2`. Its `#tag` query is lifted into `hashtags[]`, and `authors` is set when filtered.
  - It has no tag autocomplete, never writes the outline `tags`, and has no "follow tag".
- **Skywalker** (native peer):
  - Tap goes to the search; long-press opens a menu that also has Favorite (pin as a local tab) and Focus (highlight tags in the timeline).
  - Composer autocomplete comes from local least-recently-used (LRU) indexes of the user's own tags and tags seen in feeds.
- **Graysky:**
  - Its tag screen is driven by SkyFeed's virtual `skyfeed:tags` feeds, which is a third-party dependency.
  - It strips a stray leading `#` from facet values.
- **Tokimeki, deck.blue, Ouranos, Klearsky:** variations of the above.
  - Ouranos is the only client with an outline `tags` editor.
  - Tokimeki keeps mutes in a local list, a pattern to avoid.
- **Mastodon contrast:** "follow tag" pours posts into the home timeline, and users call that noise. Pinned tabs are preferred.

## Goals / Non-Goals

**Goals:**
- Nubecita-published tags are byte-for-byte what the official client would produce, so they are discoverable in every client.
- One tap on any `#tag` reaches a useful Top/Latest view, with an in-screen way to narrow it to the post's author.
- Reuse the search module's paging and moderation plumbing rather than build a second feed stack.

**Non-Goals:** see the proposal's Non-goals. The headline exclusions:
- mute and the long-press menu, which are blocked on muted words;
- autocomplete;
- pinning;
- composer highlighting;
- the outline `tags` editor;
- tags in profile bios (bios have no stored facets, and the hero only auto-detects URLs today).

## Decisions

### D1. Detection is a pure `TagDetector` in `:core:posting`, ported from the official regexes

Port `TAG_REGEX`, `TRAILING_PUNCTUATION_REGEX` and `CASHTAG_REGEX` verbatim, adapted to `kotlin.text.Regex` (JDK regex):

- `\p{P}` works with the default flags.
- The zero-width exclusion class must be written with `\u` escapes.
- The JDK has no `/u` flag, so the "not a digit, whitespace or punctuation" class needs `[^\d\s\p{P}…]`, with `UNICODE_CHARACTER_CLASS` so `\d` and `\s` match the JS semantics.

Grapheme counting reuses the composer's existing `GraphemeCounter`. Byte offsets reuse `DefaultFacetExtractor`'s existing UTF-16 → UTF-8 prefix table.

- **Why a separate pure helper:** it is unit-testable on the JVM with a table of the official edge cases. The extractor stays a coordinator.
- **Alternative considered:** a hand-written tokenizer. Rejected, because regex parity with the reference client is the actual requirement, and divergence would make our tags differ from theirs.

**Link overlap:** a tag can never start inside a URL token, because it requires preceding whitespace. A defensive rule still drops any tag whose range intersects a detected link facet, so a future URL-regex change can't produce overlapping facets.

### D2. Hashtag screen queries `searchPostsV2` with the structured `hashtags` filter

The request is `hashtags = [tag]`, `sort = top|recent`, `authors = [author]` when filtered, plus `cursor` and `limit = 25`.

- **Why:** the structured filter matches facet tags **and** the outline `tags` field, and avoids query-parser pitfalls. The official client hit a Lucene `from:did:` bug before moving to structured params.
- **Alternative considered:** v1 `searchPosts(q = "#tag")`, reusing the existing repository unchanged. Rejected as the primary path for the reasons above.
- **Fallback:** keep it in mind if the v2 endpoint misbehaves for authenticated AppView calls. Verify `FeedService.searchPostsV2` exists in the runtime artifact during task 2.1.
- **`allTime`:** leave it unset at first (recent window), matching the official screen's default behaviour for Latest. Revisit if rare tags come back empty (see Open Questions).

### D3. Screen lives in `:feature:search`, as a new route next to `SearchPosts`

- `:feature:search:api` adds `@Serializable data class Hashtag(val tag: String, val author: String? = null) : NavKey`.
- `:feature:search:impl` adds `HashtagViewModel` (MVI) and `HashtagScreen`, registered `@MainShell` as a list-pane entry, so a post tap fills the detail pane the same way search results do.
- **Why:** the paging, moderation filtering, post-card callbacks and bench-fake patterns already live here. A new `:feature:hashtag` module would duplicate them.
- **Alternative considered:** a separate module, like `:feature:bookmarks`. Rejected, because the screen is a search view with a fixed filter.

### D4. State shape

`HashtagState` holds:
- `selectedTab: HashtagTab`;
- `authorFilterOn: Boolean`;
- per-tab `HashtagTabState(items: ImmutableList<PostUi>, cursor: String?, status: HashtagLoadStatus)`, where `HashtagLoadStatus` is a sealed `Idle / InitialLoading / Refreshing / Appending / InitialError(error) / Empty`.

This follows the CLAUDE.md flat-state plus sealed-status rule. Tabs load independently, and toggling the author filter resets both tabs. Post interactions use the sanctioned `PostInteractionHandler by handler` delegation, as the other post-hosting screens do.

### D5. Tap routing: `FacetTarget.Tag(tag, authorHandle)`; tap navigates, no menu

- `PostCard`'s rich-text builder emits `LinkAnnotation.Clickable` for `FacetTag`, the same mechanism as mentions, so styling and the a11y link role are unchanged.
- `authorHandle` comes from the `PostUi` / `QuotedPostUi` being rendered. The quoted post's author is used for tags inside a quote.
- Each host's `when` gains `is FacetTarget.Tag -> navState.add(Hashtag(target.tag, target.authorHandle))`. Chats route through their own event.
- **Why no long-press menu now:** a text-range long-press inside a card that already has click and long-press semantics needs custom pointer handling over the text layout. Its only unique entry, Mute, is blocked on muted words. "Posts by this user" is covered in-screen by the author filter (D6). The menu ships with `nubecita-gt3m`.

### D6. "Posts by this user" is an in-screen filter chip, off by default

Opened from a post, the screen shows a `FilterChip` "From @handle", initially off. This gives the official menu's second action without the long-press, and keeps the default (everyone's posts) identical to the official tap behaviour.

### D7. Title and share

- **Title:** `#` + the tag lowercased (cashtags uppercased), as in the official app. Long tags ellipsize via `maxLines = 1`.
- **Share:** a plain `ACTION_SEND` of `https://bsky.app/hashtag/<encoded>[?author=…]`, reusing the existing share-intent helper used for posts.

## Risks / Trade-offs

- **[JDK regex ≠ JS regex semantics]** Unicode classes differ subtly. → Port the official test cases, including `#️⃣`, `#123`, `##foo`, full-width `＃`, `#2024年` and emoji tags, into a parameterized `TagDetectorTest`.
- **[searchPostsV2 availability or behaviour]** The endpoint is newer, and the recent window may hide old tags. → Verify in task 2.1. The repository interface hides the endpoint, so falling back to v1 `searchPosts(tag = [..])` is a one-file change.
- **[Seven hosts must all add a `Tag` branch]** A missed host fails to compile, which is desirable. A host with an `else` branch would silently ignore taps → `git grep "FacetTarget"` across the repo during task 3.2, and add a host-coverage scenario to tests where feasible.
- **[Bench flavor]** `:feature:search:impl` is flavored. → Extend `BenchFakeSearchPostsRepository`, or add a bench hashtag fake, so `assembleBenchDebug` and the macrobench still compile.
- **[Screenshot churn]** Making tags clickable shouldn't change pixels. → If baselines change, triage with `scripts/triage-screenshot-failures.py` and commit only genuine diffs.

## Migration Plan

This is additive. There are no schema or preference changes. It lands as one `gh stack` (children `.1 → .2 → .3`) in a single atomic merge, so tags become tappable only once the screen exists. Rollback is reverting the squash commit.

## Open Questions

- Should Latest set `allTime = true` so rare tags aren't empty? Decide after observing real results in task 2.1.
- Should the screen expose a compose-with-tag action (pre-fill `#tag` in the composer)? Skywalker and Tokimeki do. It's cheap, but out of scope unless requested.
