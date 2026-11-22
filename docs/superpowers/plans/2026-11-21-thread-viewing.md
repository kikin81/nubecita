# Thread Viewing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the server's thread counter (`3/5`) on feed cards, quoted posts and post
detail, and make post detail render an author's thread in order through
`app.bsky.unspecced.getPostThreadV2`.

**Architecture:** The number is a nullable `ThreadPositionUi` on `PostUi` /
`QuotedPostUi`, filled only from server fields and drawn by one design-system pill.
Feeds read it from `FeedViewPost` and persist it per feed-item member. Post detail
moves to a V2-backed repository read that keeps the server's flat order, stores the
extra fields in the thread snapshot cache, and falls back to the existing v1 path.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 Expressive), Room, Hilt, MVI on
`MviViewModel`, atproto-kotlin SDK (local mavenLocal build first), JUnit 5 + MockK +
Turbine, AGP screenshot tests.

**Spec:** `docs/superpowers/specs/2026-11-21-thread-viewing-design.md`

## Global Constraints

- Every index and count comes from the server. Validity rule, applied in exactly one
  place: `index >= 1 && count >= 1 && index <= count`. Anything else means no pill.
- The only derivation allowed: in a feed `ReplyCluster` whose leaf is numbered, root
  = `1`, parent = `index − 1`, same count.
- Post detail renders the server's order. No sorting in the app.
- V2 request values, exact: `above = true`, `below = 10`, `branchingFactor = 1`,
  `sort = "top"`.
- Card-level screenshot baselines for posts without a number stay byte-identical.
  Post detail screen baselines change only in Task 5.
- Baselines come from the `update-baselines` bot. Never stage locally rendered PNGs.
- New user-facing strings ship with es-419 and pt-BR in the same commit.
- No `!!`. Reorder on `!= null` to smart-cast.
- Commits: Conventional Commits, `Refs: <bd-id>` footer, no attribution lines, no
  `--no-verify`. Confirm each commit landed with `git log --oneline -1`.
- Flavored modules (`core/feed-cache`, `core/posts`, `feature/profile/impl`) use
  `lintProductionDebug` / `testProductionDebugUnitTest`. Unflavored (`designsystem`,
  `data/models`, `core/feed-mapping`, `core/database`, `feature/postdetail/impl`) use
  `lintDebug` / `testDebugUnitTest`.
- The SDK is consumed from mavenLocal with `-PuseMavenLocal=true` until it is
  released. `gradle/libs.versions.toml` is not changed before the release.
- The video comments sheet keeps calling `PostThreadRepository.getPostThread` (v1)
  and its behaviour does not change.

## Review Focus

Inputs the spec implies but does not spell out, each pinned by a test in the task
that owns the code:

1. **Out-of-range numbering from the server** (index 0, index > count, one field
   missing, a value above `Int.MAX_VALUE`): no pill, no crash. Task 1, step 1.
2. **A post that would appear twice in the post detail list** (a "Show more replies"
   result repeating a reply already shown): the list never holds two rows with the
   same key, because a duplicate key crashes `LazyColumn`. Task 4 step 3, Task 5
   step 3.
3. **An unknown item kind in the V2 union** (the endpoint is experimental): that item
   is skipped and the rest of the thread renders. Task 4, step 3.
4. **A snapshot written before this change** (new columns null): renders without
   pills or read-more rows, and the next refresh replaces it. Task 4, step 5.
5. **A tombstone inside the ancestor chain** (blocked or deleted parent): no thread
   line is drawn into or out of the tombstone, and nothing crashes. Task 5, step 1.

## Tracking and delivery

- bd epic `nubecita-hpl8` with five children (`.1` to `.5`), each blocking the next. One `gh stack`:

| # | Branch (`<type>/<bd-id>-<slug>`) | PR title |
|---|---|---|
| 1 | `feat/nubecita-hpl8.1-thread-position-pill` | `feat(designsystem): add thread position pill to post cards` |
| 2 | `feat/nubecita-hpl8.2-thread-numbers-in-feeds` | `feat(feed): read thread numbers from feed entries and quotes` |
| 3 | `feat/nubecita-hpl8.3-thread-numbers-feed-cache` | `feat(feed-cache): persist thread numbers in the feed cache` |
| 4 | `feat/nubecita-hpl8.4-thread-v2-repository` | `feat(posts): read threads through getPostThreadV2` |
| 5 | `feat/nubecita-hpl8.5-ordered-post-detail` | `feat(postdetail): show author threads in order` |

- The spec, this plan and the OpenSpec change go to `main` on their own docs PR.
- Before `gh stack submit` on each child: lint and unit tests of the touched modules.
- Children 4 and 5 are red on CI until the SDK release; the `atproto` version bump is
  a final commit on child 4.

---

### Task 0: SDK — thread V2 in atproto-kotlin, published locally

**Files (in `~/code/atproto-kotlin`, on a new worktree so the checked-out
`chore/lexicon-bump` branch is not disturbed):**
- Modify: `generator/lexicons.json` (three NSIDs plus their `resolutions`)
- Modify: `generator/src/main/kotlin/io/github/kikin81/atproto/generator/tools/LexiconDriftDetect.kt:236-243` (triage note)
- Regenerated: `models/` output, golden files, API dumps

**Interfaces:**
- Produces: Kotlin types for `app.bsky.unspecced.getPostThreadV2`,
  `getPostThreadOtherV2` and `app.bsky.unspecced.defs#threadItem*` in package
  `io.github.kikin81.atproto.app.bsky.unspecced`, plus a service class with
  `getPostThreadV2(...)` and `getPostThreadOtherV2(...)`. **Record the generated class
  and member names at the bottom of this file under "Generated SDK names"** — Task 4
  reads them from there.

- [ ] **Step 1:** `git fetch`; create worktree and branch
  `feat/unspecced-thread-v2` from `origin/main`. If the published
  `app.bsky.unspecced.defs` lexicon only resolves with the newer `@atproto/lex` from
  `chore/lexicon-bump`, base the branch on that instead and note it.
- [ ] **Step 2:** In the worktree, `rm -rf generator/lexicons`, add the three NSIDs
  with the lex CLI (`cd generator && npx lex install --help` for the add form; do not
  hand-write CIDs), then `npx lex install --ci`.
- [ ] **Step 3:** Confirm the installed `generator/lexicons/app/bsky/unspecced/defs.json`
  contains `opThreadPostIndex` and `opThreadPostCount` on `threadItemPost`. If the
  published lexicon lacks them, stop and report: the SDK cannot carry the numbers yet.
- [ ] **Step 4:** `./gradlew :generator:generateModels --no-configuration-cache`, then
  `GOLDEN_UPDATE=1 ./gradlew :generator:test --tests '*GoldenFileTest*'`.
- [ ] **Step 5:** Reword the triage note: `app.bsky.unspecced.*` stays out of scope
  except the thread V2 endpoints the official client ships on.
- [ ] **Step 6:** `./gradlew build` (spotless, tests, API dumps). Expected: green.
- [ ] **Step 7:** Publish `:models` to mavenLocal at the version Nubecita pins
  (`atproto` in Nubecita's `gradle/libs.versions.toml`), using the `nosign` init
  script recipe: `./gradlew --no-configuration-cache -I nosign.init.gradle.kts
  :models:publishJvmPublicationToMavenLocal
  :models:publishKotlinMultiplatformPublicationToMavenLocal`.
- [ ] **Step 8:** Verify:
  `unzip -l ~/.m2/repository/io/github/kikin81/atproto/models-jvm/<ver>/models-jvm-<ver>.jar | grep -i PostThreadV2`
  lists the request, response and service classes. Record the names (see Interfaces).
- [ ] **Step 9: Prove the API output.** Fetch two real responses from the public
  appview into the scratchpad (not the repo):
  `https://public.api.bsky.app/xrpc/app.bsky.unspecced.getPostThreadV2?anchor=<at-uri>&below=10&branchingFactor=1&sort=top`
  for (a) a post in the middle of a short author thread and (b) the first post of an
  author thread longer than 11 posts. Check and write down: numbering on every OP
  post; the author's next post first under each level; `moreReplies > 0` on the last
  returned OP post in (b). If (b) shows `moreReplies == 0`, set `below = 20` in the
  Global Constraints and the spec and note the limit.
- [ ] **Step 10:** Commit on the SDK branch
  (`feat(lexicons): adopt unspecced thread V2 endpoints`). Do not push or open the PR
  yet; that happens after the feature is proven (Task 6).

---

### Task 1: Thread position model and pill (child 1)

**Files:**
- Create: `data/models/src/main/kotlin/net/kikin/nubecita/data/models/ThreadPositionUi.kt`
- Test: `data/models/src/test/kotlin/net/kikin/nubecita/data/models/ThreadPositionUiTest.kt`
- Modify: `data/models/.../PostUi.kt`, `data/models/.../QuotedPostUi.kt` (new defaulted field)
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/ThreadPositionBadge.kt`
- Modify: `designsystem/.../component/PostCard.kt` (`BodyText` at :348 and its call at :200),
  `FocusPostCard.kt:95`, `PostCardQuotedPost.kt:150` (`QuotedBodyText`)
- Modify: `designsystem/src/main/res/values/strings.xml`, `values-b+es+419/strings.xml`,
  `values-pt-rBR/strings.xml`
- Test: `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/component/ThreadPositionTextTest.kt`
- Screenshot: `designsystem/src/screenshotTest/kotlin/.../component/ThreadPositionBadgeScreenshotTest.kt`,
  plus new cases in `PostCardScreenshotTest.kt`, `FocusPostCardScreenshotTest.kt`,
  `PostCardQuotedPostScreenshotTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Immutable
  public data class ThreadPositionUi(val index: Int, val count: Int) {
      /** Root of the same thread: 1/count. */
      public fun root(): ThreadPositionUi?
      /** The post this one replies to: (index-1)/count, or null at index 1. */
      public fun parent(): ThreadPositionUi?
      public companion object {
          /** Null unless index >= 1, count >= 1, index <= count and both fit an Int. */
          public fun of(index: Long?, count: Long?): ThreadPositionUi?
      }
  }
  ```
  `PostUi.threadPosition: ThreadPositionUi? = null`,
  `QuotedPostUi.threadPosition: ThreadPositionUi? = null`.
  `@Composable fun ThreadPositionBadge(position: ThreadPositionUi, modifier: Modifier = Modifier)`.
  `internal fun AnnotatedString.withThreadPositionSlot(): AnnotatedString` and
  `internal const val THREAD_POSITION_INLINE_ID = "thread-position"`.
  `BodyText(..., threadPosition: ThreadPositionUi? = null)`.

- [ ] **Step 1: Failing test** `ThreadPositionUiTest`:
  ```kotlin
  assertEquals(ThreadPositionUi(3, 5), ThreadPositionUi.of(3, 5))
  assertEquals(ThreadPositionUi(1, 1), ThreadPositionUi.of(1, 1))
  listOf(0L to 5L, 6L to 5L, 3L to 0L, -1L to 5L).forEach { (i, c) -> assertNull(ThreadPositionUi.of(i, c)) }
  assertNull(ThreadPositionUi.of(null, 5)); assertNull(ThreadPositionUi.of(3, null))
  assertNull(ThreadPositionUi.of(1, Int.MAX_VALUE.toLong() + 1))
  assertEquals(ThreadPositionUi(1, 5), ThreadPositionUi(3, 5).root())
  assertEquals(ThreadPositionUi(2, 5), ThreadPositionUi(3, 5).parent())
  assertNull(ThreadPositionUi(1, 5).parent())
  ```
  Run `./gradlew :data:models:testDebugUnitTest --tests '*ThreadPositionUiTest*'`. Expected: fails to compile.
- [ ] **Step 2:** Implement `ThreadPositionUi`; add the two model fields. Run the test. Expected: PASS.
- [ ] **Step 3: Failing test** `ThreadPositionTextTest` for `withThreadPositionSlot()`:
  - `"Hello\n\n"` → text before the slot is `"Hello"` (trailing whitespace trimmed),
    followed by one no-break space and one inline-content placeholder with id
    `THREAD_POSITION_INLINE_ID`.
  - A string whose last 10 characters carry a link annotation: after the call the
    annotation's `start` and `end` are unchanged and do not cover the slot.
  - An empty string → only the placeholder, no leading space.
- [ ] **Step 4:** Implement `withThreadPositionSlot()` and `ThreadPositionBadge`
  (rounded pill, `labelSmall` with tabular figures, `onSurfaceVariant` on
  `surfaceContainerHigh`, not clickable, content description from
  `thread_position_a11y`). Strings: `thread_position_label` = `%1$d/%2$d`;
  `thread_position_a11y` = "Post %1$d of %2$d" (es-419 and pt-BR using the word each
  locale file already uses for "post").
- [ ] **Step 5:** Wire it. `BodyText` renders the pill through `InlineTextContent`
  when `threadPosition != null`; with blank text it renders the pill alone. Every call
  site that skips the body for empty text must now render it when a position is
  present, and it stays above the embed. Same for `QuotedBodyText`. With
  `threadPosition == null` the produced `AnnotatedString` and layout are exactly as
  before. The pill is not part of a translated body's text: it follows whichever text
  is shown.
- [ ] **Step 6:** Screenshot fixtures (source only): badge alone at `1/5` and
  `12/240`; `PostCard` with text + pill, pill with a last line that is already full
  width, text ending in a link facet, textless post with an image, and one at font
  scale 2.0; `FocusPostCard` with pill; quoted card with pill.
- [ ] **Step 7:** `./gradlew :data:models:testDebugUnitTest :designsystem:testDebugUnitTest :designsystem:lintDebug :designsystem:compileDebugScreenshotTestKotlin`.
  Expected: green. Render locally only to look; `git checkout -- '*/reference'` before committing.
- [ ] **Step 8:** Commit `feat(designsystem): add thread position pill to post cards`.

---

### Task 2: Numbers in feeds and quotes (child 2)

**Files:**
- Modify: `core/feed-mapping/src/main/kotlin/net/kikin/nubecita/core/feedmapping/FeedViewPostMapper.kt`
  (`toPostUiOrNull` :43, `toFeedItemUiOrNull` :73, `toFeedItemsUi` :158)
- Modify: `core/feed-mapping/.../FeedMapping.kt:328` (`RecordViewRecord.toEmbedUiRecord`)
- Modify: `feature/profile/impl/.../data/AuthorFeedMapper.kt` (wherever it builds a
  `PostUi` from a `FeedViewPost` without going through `toPostUiOrNull`)
- Test: `core/feed-mapping/src/test/.../FeedViewPostMapperTest.kt`, the profile
  module's existing author-feed mapper test

**Interfaces:**
- Consumes: `ThreadPositionUi.of`, `.root()`, `.parent()` (Task 1);
  `FeedViewPost.opThreadPostIndex` / `opThreadPostCount` and the same on
  `RecordViewRecord` (already in the SDK).
- Produces: `FeedViewPost.toFeedItemUiOrNull(prefs, viewerDid, numberedByUri:
  Map<String, ThreadPositionUi> = emptyMap())`. Every `PostUi` built from a feed entry
  carries `threadPosition`; `QuotedPostUi` built from a record view carries it.

- [ ] **Step 1: Failing tests** in `FeedViewPostMapperTest`:
  - `entry numbered 3 of 5 sets threadPosition` → `ThreadPositionUi(3, 5)`.
  - `entry with index 0, index above count, or one field missing has no threadPosition`.
  - `reply cluster with leaf 3 of 5 numbers root 1 of 5 and parent 2 of 5`.
  - `reply cluster with an unnumbered leaf numbers nothing`.
  - `a context post that is also its own numbered entry in the page uses the server number`
    (page: entry A numbered 2/9; entry B is a reply whose parent is A and whose own
    number is 3/5 → B's parent shows 2/9).
  - `self thread chain members keep their own numbers` (three linked entries
    1/3, 2/3, 3/3).
  - `quoted record numbered 2 of 4 sets threadPosition on QuotedPostUi`.
  Run `./gradlew :core:feed-mapping:testDebugUnitTest --tests '*FeedViewPostMapperTest*'`. Expected: FAIL.
- [ ] **Step 2:** Implement: `toPostUiOrNull` copies `threadPosition`;
  `toFeedItemsUi` builds `numberedByUri` from the page once and passes it down;
  `toFeedItemUiOrNull` sets root and parent from `numberedByUri[uri] ?:
  leaf.threadPosition?.root()` / `.parent()`; `toEmbedUiRecord` sets the quote's
  position. Run. Expected: PASS.
- [ ] **Step 3:** Author feed: add a test that a numbered author-feed entry yields a
  numbered `PostUi`, then route the mapper through the shared functions or copy the
  field. `./gradlew :feature:profile:impl:testProductionDebugUnitTest`.
- [ ] **Step 4:** `./gradlew :core:feed-mapping:lintDebug :feature:profile:impl:lintProductionDebug`. Commit
  `feat(feed): read thread numbers from feed entries and quotes`.

---

### Task 3: Numbers in the feed cache and bench timeline (child 3)

**Files:**
- Modify: `core/database/.../model/FeedItemMemberEntity.kt` (two nullable columns)
- Modify: `core/database/.../NubecitaDatabase.kt` (`version = 13`, `AutoMigration(12, 13)`, KDoc line)
- Create: `core/database/schemas/net.kikin.nubecita.core.database.NubecitaDatabase/13.json` (generated)
- Test: `core/database/src/androidTest/.../MigrationTest.kt`
- Modify: `core/feed-cache/.../FeedPostMappers.kt:160` (`entryContextBlob`),
  `FeedProjectionWriter.kt:427-486` (`write`, `memberUris`),
  `FeedProjectionMappers.kt:41` (`toFeedItemUi`)
- Modify: `core/feed-cache/src/bench/kotlin/.../BenchTimelineDto.kt:121` (`BenchPostDto`),
  `BenchTimelineMapper.kt`, `core/feed-cache/src/bench/assets/timeline.json`
- Test: the existing feed-cache mapper / projection tests under `core/feed-cache/src/test`

**Interfaces:**
- Consumes: `PostUi.threadPosition` (Task 1), numbered feed items (Task 2).
- Produces: `FeedItemMemberEntity.opThreadIndex: Int? = null` (`op_thread_index`),
  `opThreadCount: Int? = null` (`op_thread_count`). `BenchPostDto.threadIndex: Int? =
  null`, `threadCount: Int? = null`. Bench thread fixture: five posts by one bench
  author with rkeys `benchthread1` … `benchthread5` (Task 4 serves the same thread).

- [ ] **Step 1: Failing tests:**
  - Entry blob: a `FeedViewPost` numbered 3/5 → `toFeedPostEntity` →
    `toFeedViewPostOrNull` still has index 3, count 5. An un-numbered root post with
    no reason and no reply still stores a null blob.
  - Projection round trip: write a `ReplyCluster` whose root/parent/leaf are 1/5,
    2/5, 3/5 and a `Single` without a number → read back → same positions, and null
    for the un-numbered post.
  - `toFeedItemUi` with members whose new columns are null yields posts without a
    position (rows written before the migration).
  Run `./gradlew :core:feed-cache:testProductionDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2:** Add the columns, bump the database to 13 with the auto-migration,
  build `:core:database` to generate `13.json`, add a `MigrationTest` case 12 → 13
  asserting an existing `feed_item_member` row survives with nulls.
- [ ] **Step 3:** `entryContextBlob` also keeps the `opThreadPostIndex` and
  `opThreadPostCount` keys. `write` records each member's position; `toFeedItemUi`
  puts `ThreadPositionUi.of(...)` back on each member post. Run the tests. Expected: PASS.
- [ ] **Step 4:** Bench: add the DTO fields and mapping; in `timeline.json` add, near
  the top, a single entry for `benchthread1` numbered 1/5 and, further down, a reply
  cluster whose leaf is `benchthread3` numbered 3/5.
  `./gradlew :core:feed-cache:compileBenchDebugKotlin :core:feed-cache:testBenchDebugUnitTest`.
- [ ] **Step 5:** `./gradlew :core:database:lintDebug :core:feed-cache:lintProductionDebug`. Commit
  `feat(feed-cache): persist thread numbers in the feed cache`. The PR gets the
  `run-instrumented` label for the migration test.

---

### Task 4: Thread V2 repository and snapshot cache (child 4)

**Files:**
- Modify: `data/models/.../ThreadItem.kt` (remove `Fold`; add three variants)
- Modify: `core/posts/src/main/kotlin/net/kikin/nubecita/core/posts/PostThreadRepository.kt`
- Modify: `core/posts/.../internal/PostThreadMapper.kt` (`RawThreadEntry` :72, `toThreadItems` :102)
- Create: `core/posts/.../internal/PostThreadV2Mapper.kt`
- Modify: `core/posts/.../internal/DefaultPostThreadRepository.kt`,
  `core/posts/.../internal/ThreadSnapshotStore.kt`
- Modify: `core/database/.../model/ThreadSnapshotEntity.kt` (item columns, new kind),
  `NubecitaDatabase.kt` (`version = 14`, `AutoMigration(13, 14)`), `schemas/.../14.json`
- Modify: `core/posts/src/bench/kotlin/.../BenchFakePostThreadRepository.kt` (+ a
  fixture file beside `BenchForeignThreads.kt`)
- Modify (compile only, render nothing new yet): every exhaustive `when` over
  `ThreadItem` — `feature/postdetail/impl/.../PostDetailScreen.kt:590-675`,
  `PostDetailViewModel.kt:547-560`, `ThreadVisiblePosts.kt`; grep the repo for others
- Test: `core/posts/src/test/.../PostThreadV2MapperTest.kt` (new),
  `DefaultPostThreadRepositoryTest.kt`, `ThreadSnapshotStoreTest.kt`,
  `PostThreadMapperTest.kt`, `core/posts/src/testBench/.../BenchFakePostThreadRepositoryTest.kt`,
  `core/database/src/androidTest/.../MigrationTest.kt`
- Test resource: `core/posts/src/test/resources/fixtures/thread_v2_long_op_thread.json`
  (hand-shaped from the Task 0 capture: synthetic DIDs, handles and text)

**Interfaces:**
- Consumes: the generated SDK names recorded under "Generated SDK names";
  `ThreadPositionUi.of` (Task 1).
- Produces:
  ```kotlin
  // ThreadItem additions (Fold removed)
  data class MoreParents(val postUri: String) : ThreadItem      // key "moreparents:$postUri"
  data class MoreReplies(val postUri: String, val count: Int, val depth: Int) : ThreadItem // key "morereplies:$postUri"
  data object OtherReplies : ThreadItem                          // key "otherreplies"

  // PostThreadRepository additions
  /** Post detail's read: V2 order and numbers, falling back to [getPostThread]. */
  public suspend fun getOrderedThread(uri: String): Result<ImmutableList<ThreadItem>> = getPostThread(uri)
  /** Replies the main read leaves out (muted, hidden). Not cached. */
  public suspend fun getOtherReplies(uri: String): Result<ImmutableList<ThreadItem>> = Result.success(persistentListOf())

  // RawThreadEntry additions, all defaulted
  val opThreadIndex: Int? = null, val opThreadCount: Int? = null,
  val moreReplies: Int = 0, val moreParents: Boolean = false

  // ThreadSnapshotKind addition
  const val OTHER_REPLIES = "other_replies"   // a body-less trailing row
  ```
  `ThreadSnapshotItemEntity` gains nullable `op_thread_index`, `op_thread_count`,
  `more_replies`, `more_parents`.

- [ ] **Step 1:** Change `ThreadItem`; add compile-only branches in the `when`s
  listed above (new rows render nothing and have no post id). Add the interface
  defaults. `./gradlew :app:assembleDebug -PuseMavenLocal=true`. Expected: compiles.
- [ ] **Step 2: Failing tests** in `PostThreadV2MapperTest`, driven by the fixture
  and by small hand-built responses:
  - `order of the response is preserved` (uris in, uris out, same sequence).
  - `depth below zero is an ancestor, zero the focus, above zero a reply with that depth`.
  - `numbered items carry threadPosition; un-numbered items carry none`.
  - `a post with moreReplies whose next item is not its reply is followed by MoreReplies`
    (asserts `postUri`, `count`, `depth`).
  - `a post with moreReplies whose next item is its reply gets no row`.
  - `the focus never gets a MoreReplies row`.
  - `the first ancestor with moreParents is preceded by MoreParents`.
  - `hasOtherReplies adds one trailing OtherReplies row; false adds none`.
  - `blocked and notFound items map to the existing tombstones`.
  - `an unknown union member is skipped and the remaining items map` (Review Focus 3).
  - `a uri that occurs twice keeps only its first occurrence` (Review Focus 2).
- [ ] **Step 3:** Implement `PostThreadV2Mapper.kt`: the V2 response →
  `List<RawThreadEntry>` (kinds from depth; a trailing `OTHER_REPLIES` entry when the
  flag is set), and extend `List<RawThreadEntry>.toThreadItems` to copy
  `threadPosition` and emit the read-more rows by the rules the tests state. v1
  entries (all defaults) must map exactly as before: `PostThreadMapperTest` stays
  green untouched. Run. Expected: PASS.
- [ ] **Step 4: Failing tests** in `DefaultPostThreadRepositoryTest`:
  - `getOrderedThread sends anchor, above=true, below=10, branchingFactor=1, sort=top`.
  - `getOrderedThread writes the V2 entries through to the snapshot`.
  - `a response that cannot be decoded falls back to v1 and logs` (plant a Timber
    tree; assert one log at warn or above naming V2).
  - `an unknown-method XRPC error falls back to v1`.
  - `a connectivity failure does not fall back and returns the failure`.
  - `a thread whose focus is not found removes the snapshot`.
  - `getOtherReplies maps posts to Reply items with moderation applied`.
- [ ] **Step 5:** Implement in `DefaultPostThreadRepository`. Fallback applies to:
  a serialization failure; an `XrpcError` with status 501; an `XrpcError` whose
  `errorName` is `MethodNotImplemented`, `XRPCNotSupported` or `InvalidRequest`; a
  404 whose `errorName` is not `NotFound`. Nothing else. Add the snapshot columns,
  database 14 + auto-migration + `14.json`, and store/restore the new fields in
  `ThreadSnapshotStore` (`OTHER_REPLIES` has no body). Tests in
  `ThreadSnapshotStoreTest`: `round trip keeps numbering, moreReplies, moreParents
  and the other-replies row`; `rows with null new columns read as a plain thread`
  (Review Focus 4). `MigrationTest` 13 → 14. Run
  `./gradlew :core:posts:testProductionDebugUnitTest -PuseMavenLocal=true`. Expected: PASS.
- [ ] **Step 6:** Bench: `BenchFakePostThreadRepository.getOrderedThread` serves the
  `benchthread1…5` thread for any of its five URIs, ordered and numbered around the
  requested post, with other bench replies after, one `MoreReplies` row and a
  trailing `OtherReplies` row; `getOtherReplies` returns two fixture replies. Other
  URIs behave as today. Extend `BenchFakePostThreadRepositoryTest`.
  `./gradlew :core:posts:testBenchDebugUnitTest -PuseMavenLocal=true`.
- [ ] **Step 7:** `./gradlew :core:posts:lintProductionDebug :core:database:lintDebug :data:models:lintDebug :feature:postdetail:impl:lintDebug -PuseMavenLocal=true`.
  Commit `feat(posts): read threads through getPostThreadV2`. Label `run-instrumented`.

---

### Task 5: Ordered post detail (child 5)

**Files:**
- Create: `feature/postdetail/impl/src/main/kotlin/.../impl/ThreadConnectors.kt`
- Test: `feature/postdetail/impl/src/test/kotlin/.../impl/ThreadConnectorsTest.kt`
- Modify: `PostDetailContract.kt`, `PostDetailViewModel.kt` (`load` :235, `refresh` :327),
  `PostDetailScreen.kt` (:383-452 state and top bar, :581-680 list)
- Modify: `designsystem/.../component/FocusPostCard.kt` (`connectAbove`)
- Create: `designsystem/.../component/ThreadReadMore.kt` (+ screenshot test)
- Modify: `feature/postdetail/impl/src/main/res/values*/strings.xml` (three locales)
- Test: `PostDetailViewModelTest.kt`, `ui/` tests,
  `src/screenshotTest/.../PostDetailScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: `getOrderedThread`, `getOtherReplies`, the three new `ThreadItem` rows
  (Task 4); `PostCard(connectAbove, connectBelow)` (existing).
- Produces:
  ```kotlin
  internal data class ConnectorFlags(val above: Boolean, val below: Boolean)
  internal fun List<ThreadItem>.connectorFlags(): List<ConnectorFlags>   // same size and order

  FocusPostCard(..., connectAbove: Boolean = false)
  @Composable fun ThreadReadMore(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
      connectAbove: Boolean = false, isLoading: Boolean = false)

  // PostDetailEvent additions
  data class OnMoreRepliesTapped(val postUri: String); data class OnMoreParentsTapped(val postUri: String)
  data object OnOtherRepliesTapped
  // PostDetailState addition
  val isLoadingOtherReplies: Boolean = false
  ```
  Strings: `postdetail_read_more_replies` "Read more replies",
  `postdetail_read_earlier_posts` "Read earlier posts",
  `postdetail_show_more_replies` "Show more replies".

- [ ] **Step 1: Failing test** `ThreadConnectorsTest`:
  - Opened at 3 of 5 — `[A1, A2, Focus, R4(d1), R5(d2), Rx(d1)]` → A1 `(false,true)`,
    A2 `(true,true)`, Focus `(true,false)`, R4 `(false,true)`, R5 `(true,false)`,
    Rx `(false,false)`.
  - Focus with no ancestors → `(false,false)`.
  - `[MoreParents, A1, Focus]` → A1 `above = true`.
  - `[Focus, R(d1), MoreReplies(d1)]` → R `below = true`.
  - `[A1, Blocked, A2, Focus]` → A1 `below = false`, Blocked `(false,false)`,
    A2 `above = false` (Review Focus 5).
- [ ] **Step 2:** Implement `connectorFlags()`. Rules: an ancestor connects below when
  the next row is an ancestor or the focus; a row connects above when the previous row
  connects below into it; the focus never connects below; a reply at depth *d*
  connects below when the next row is a reply at *d + 1* or its own `MoreReplies`;
  tombstones and `OtherReplies` connect nothing. Run. Expected: PASS.
- [ ] **Step 3: Failing tests** in `PostDetailViewModelTest`:
  - `load and refresh read through getOrderedThread`.
  - `OnMoreRepliesTapped and OnMoreParentsTapped emit NavigateToPost with that uri`.
  - `OnOtherRepliesTapped replaces the OtherReplies row with the loaded replies`.
  - `other replies already in the list are not added twice` (Review Focus 2).
  - `a failed other-replies load keeps the row, clears isLoadingOtherReplies and emits ShowError`.
  - `refresh restores the OtherReplies row`.
- [ ] **Step 4:** Implement the ViewModel changes. Run
  `./gradlew :feature:postdetail:impl:testDebugUnitTest -PuseMavenLocal=true`. Expected: PASS.
- [ ] **Step 5:** Screen: pass `connectorFlags()` to `PostCard` and `FocusPostCard`
  (`connectAbove` draws from the card's top edge into the avatar; default `false`
  leaves its baselines unchanged); render `MoreReplies` and `MoreParents` with
  `ThreadReadMore` and `OtherReplies` with `ThreadReadMore(isLoading =
  state.isLoadingOtherReplies)`.
- [ ] **Step 6: Pin the opened post.** Approach: hold the ancestor rows back until
  the focus row has been placed once, then add them; `LazyColumn` keeps the first
  visible item by key, so the focus stays at the top on every path (snapshot hit,
  focus-only hit, cold load, refresh) with no scroll call after layout. When
  ancestors exist, a trailing spacer of `viewport height − height of the rows from
  the focus to the end` (floor 0) lets a short tail still reach the top; no spacer
  without ancestors. `PostDetailTopBar`'s `focusIndex` must be the index in the list
  actually displayed. Test at the level `src/test/.../ui` already uses: with items
  `[A1, A2, Focus, R1]` the first visible row is the focus, and scrolling up reveals
  A2 then A1; with `[Focus, R1]` there is no trailing spacer.
- [ ] **Step 7:** Screenshot fixtures: a thread opened at 3 of 5 with lines and pills;
  a `MoreReplies` row at a chain end; a `MoreParents` row; the `OtherReplies` row idle
  and loading. Baselines for this PR come from the `update-baselines` bot; compare
  the bot's PNGs against the previous ones by max channel delta before accepting.
- [ ] **Step 8:** `./gradlew :feature:postdetail:impl:lintDebug :designsystem:lintDebug -PuseMavenLocal=true`.
  Commit `feat(postdetail): show author threads in order`.

---

### Task 6: Prove it, release the SDK, land the stack

- [ ] **Step 1: Full gate on the top branch:**
  `./gradlew :app:assembleDebug spotlessCheck lint :app:checkSortDependencies -PuseMavenLocal=true`,
  every touched module's unit tests (flavored names where they apply), the bench
  compile (`:app:assembleBenchDebug` or the project's bench variant; dry-run the task
  path first), and the compose-expert skill on `git diff origin/main...HEAD`.
- [ ] **Step 2: Device pass, bench build, on the plugged-in foldable** (install by
  serial; compare `lastUpdateTime` with the APK mtime): the feed shows `1/5` on the
  fixture thread and the cluster shows `1/5`, `2/5`, `3/5`; opening `benchthread3`
  lands on post 3 with 1 and 2 above and 4, 5 below; "Read more replies" opens the
  next post pinned; "Show more replies" appends two replies; kill and relaunch shows
  the pills from cache before any load. Screenshot each.
- [ ] **Step 3: Device pass, production build** against the real long thread from
  Task 0: numbers match the official app; the continuation row appears after the
  eleventh post and leads to the rest.
- [ ] **Step 4:** With the user: push the SDK branch, open its PR, release. Then, on
  child 4, bump `atproto` in `gradle/libs.versions.toml` to the released version
  (`chore(deps)` commit), `gh stack sync`, `gh stack submit`, and confirm CI is green
  on every PR without `-PuseMavenLocal`.
- [ ] **Step 5:** Review threads resolved on every PR; newest bot review is newer than
  the last commit on each; then `gh stack merge --squash --yes`, count the `Release`
  runs, `bd close` the children and the epic, archive the OpenSpec change.

## Generated SDK names

_(filled in by Task 0, step 8)_
