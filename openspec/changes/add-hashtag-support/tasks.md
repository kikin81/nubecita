Epic: `nubecita-cxcz`. Section 1 is child `.1`, section 2 is child `.2`, section 3 is child `.3`.

**Landing model.** The epic ships as one `gh stack`, driven by the `gh-stack` and `bd-workflow` skills. Each child is one branch and one PR, stacked `.1 → .2 → .3` on `main`. Nothing reaches `main` until every child is done, reviewed and green. The whole feature then lands in a single atomic `gh stack merge`, which cuts one release. **Never merge a child PR on its own**, whether from the GitHub UI or with `gh pr merge`: a lone child would ship publishing or the screen without tappable tags, and cut its own release.

## 0. Stack setup (per child, via the `gh-stack` / `bd-workflow` skills)

- [ ] 0.1 Child `.1`: start from an up-to-date `main` with `gh stack init feat/nubecita-cxcz.1-publish-hashtag-and-cashtag-facets-from-the-compos`, then `bd update nubecita-cxcz.1 --claim`.
- [ ] 0.2 Child `.2`: from the stack top, `gh stack add feat/nubecita-cxcz.2-hashtag-posts-screen-top-latest-optional-author-fi`, then claim it.
- [ ] 0.3 Child `.3`: from the stack top, `gh stack add feat/nubecita-cxcz.3-make-tags-in-posts-tappable`, then claim it.
- [ ] 0.4 After each child's commits:
  - lint the touched modules, using flavored task names where required;
  - run `gh stack submit --auto --open`;
  - set that PR's body to `Closes: nubecita-cxcz.N`;
  - check that its title is a Conventional Commit.
  - At least one child title MUST be `feat:` so the epic cuts a Play release.
- [ ] 0.5 Review fixes on a lower branch: `gh stack sync`, then `gh stack submit`. Batch fixes, because every restack re-runs CI on the branches above. Re-check review currency per PR, since a restack invalidates the bot reviews above it.

## 1. Publish hashtag and cashtag facets (`nubecita-cxcz.1`, `:core:posting`)

- [ ] 1.1 Add the pure `TagDetector` (internal), ported from the official `TAG_REGEX`, `TRAILING_PUNCTUATION_REGEX` and `CASHTAG_REGEX` with JDK-regex adaptations. It returns `(tag, utf16Range)` matches, applying trailing-punctuation stripping and the 64-grapheme cap.
  - Test: add a parameterized `TagDetectorTest` table that covers:
    - simple, trailing `!`, `#123`, `#1st`, `##foo`, full-width `＃`;
    - `#️⃣`, `(#tag`, `foo#bar`, emoji, `#2024年`;
    - 64 vs 65 graphemes;
    - `$btc` → `$BTC`, `$5`, `$TOOLONG`, `($AAPL)`.
- [ ] 1.2 Wire `TagDetector` into `DefaultFacetExtractor`: emit `FacetTag` facets with UTF-8 byte ranges from the existing offset table, drop any tag overlapping a link facet, and keep the final `byteStart` sort.
  - Test: extend `DefaultFacetExtractorTest` with the mixed mention+tag+link ordering case, multibyte `café #día` offsets, and a URL-adjacent case.
- [ ] 1.3 Device pass (bench build): publish a post containing `#nubecita $BTC`, then confirm the record's facets. Use the bench posting fake's captured record or a logged `createRecord` payload.

## 2. Hashtag screen (`nubecita-cxcz.2`, `:feature:search`)

- [ ] 2.1 Verify `FeedService.searchPostsV2` exists in atproto-kotlin 9.13.0. Add `searchHashtag(tag, author, sort, cursor, limit)` to `SearchPostsRepository` / `DefaultSearchPostsRepository`, using `SearchPostsV2Request(hashtags = [tag], authors = author?.let(::listOf), sort, cursor, limit)` with the same moderation filtering as `searchPosts`. Add the bench fake implementation.
  - Test: repository unit test for request mapping (sort `top`/`recent`, authors only when set) and moderation filtering.
- [ ] 2.2 Add the `Hashtag(tag, author)` NavKey to `:feature:search:api`. Add `HashtagContract` (state, events, effects, `HashtagLoadStatus`) and `HashtagViewModel`:
  - independent Top/Latest tabs with lazy first load;
  - cursor paging with de-duplication;
  - refresh;
  - author filter toggle that resets both tabs;
  - `PostInteractionHandler` delegation.
  - Test: `HashtagViewModelTest` covering initial Top load, Latest lazy load, paging append and de-duplication, empty, error and retry, and the filter toggle reloading both tabs with `authors`.
- [ ] 2.3 Build `HashtagScreen`:
  - top bar with the `#tag` title (lowercase, cashtag uppercase) and a share action (`bsky.app/hashtag/<tag>[?author=]`);
  - Top/Latest tabs;
  - the "From @handle" `FilterChip`, shown only when `author != null`;
  - post list via the shared post card;
  - empty and error states;
  - strings in en, es-419 and pt-BR.

  Register it `@MainShell` as a list-pane entry, with a post tap filling the detail pane.
  - Test: screenshot tests for loaded, empty, error and filter-on states, in light and dark.
- [ ] 2.4 Gate: `:feature:search:impl:lintProductionDebug`, `testProductionDebugUnitTest`, and `:app:assembleBenchDebug`.

## 3. Tappable tags (`nubecita-cxcz.3`, `:data:models`, `:designsystem`, hosts)

- [ ] 3.1 Add `FacetTarget.Tag(tag: String, authorHandle: String?)` to `:data:models`. In `PostCard`'s rich-text builder, turn `FacetTag` into `LinkAnnotation.Clickable` with the stray leading `#` stripped, and pass the rendering post's (or quoted post's) author handle.
  - Test: `:designsystem` unit test asserting the tap emits `FacetTarget.Tag("Kotlin", "alice.bsky.social")` for `#Kotlin` and for a `#Kotlin`-valued facet. Screenshot baselines stay unchanged; triage if not.
- [ ] 3.2 Add the `is FacetTarget.Tag` branch to every host: feed, post detail, profile, bookmarks, search posts, notifications and chats. Each routes to `Hashtag(tag, author)`, through an effect where the screen already uses one. Run `git grep -n "FacetTarget"` to confirm no host is missed and none uses an `else` fallback.
  - Test: per-host unit or instrumentation coverage where an existing facet-tap test lives (e.g. `FeedScreenInstrumentationTest`, `PostDetail` tests).
- [ ] 3.3 Full gate on the stack top:
  - `:app:assembleDebug`;
  - touched-module lint, with flavored names where required;
  - compose-expert review over `git diff origin/main...HEAD`;
  - device pass: tap a tag in the feed, a quoted post and post detail; toggle the author filter; switch tabs; share.

## 4. Land the epic (only when the whole feature is ready)

- [ ] 4.1 Preconditions, all required on **every** PR in `gh stack view`:
  - open, non-draft, and CI-green;
  - zero unresolved review threads;
  - newest bot review newer than the last commit;
  - full gate (3.3) done on the stack top;
  - the user's explicit go-ahead to land.
- [ ] 4.2 Land atomically with `gh stack merge --squash --yes`, then confirm exactly one `Release` run with `gh run list --workflow=release.yaml --limit 3`.
- [ ] 4.3 Clean up:
  - `gh stack sync --prune`;
  - `bd close nubecita-cxcz.1 nubecita-cxcz.2 nubecita-cxcz.3 nubecita-cxcz`;
  - archive this change with `openspec archive add-hashtag-support`. Reconcile the merged spec's Purpose and fix the end-of-file newline before committing.
