# Post Overflow Sheet, Thread Mute and Pin — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace both post overflow dropdowns with one grouped bottom sheet, make "Mute thread" call the server, and let users pin their own post to their profile.

**Architecture:**
- A pure rule function in `:designsystem` turns a `PostUi` into grouped rows, and one sheet composable renders them for `PostCard` and the video page.
- Thread-mute and pin writes go through `PostInteractionsCache`, which gives them the right-away update and rollback that bookmark already has. A new per-field override is kept until the server agrees with it.
- Pinning is a read-modify-write of `app.bsky.actor.profile`, extracted into `:core:profile` and shared with Edit profile.

**Tech Stack:** Kotlin 2.3, Compose M3 Expressive (`ModalBottomSheet`, `NubecitaListGroup`), Hilt, `atproto-kotlin` (`RepoService`, `GraphService`, `MuteThreadRequest`), JUnit 5, MockK, Turbine, AGP screenshot tests.

**Spec:**
- `docs/superpowers/specs/2026-10-31-post-overflow-sheet-and-pin-design.md`
- `openspec/changes/post-overflow-sheet-and-pin/`

## Global Constraints

- **Stack.** One `gh stack`:
  - PR 1 = Tasks 1–6, `nubecita-iwjz.3`, base `main`.
  - PR 2 = Tasks 7–9, `nubecita-iwjz.4`.
  - PR 3 = Tasks 10–14, `nubecita-iwjz.2`.
  - Branches: `feat/nubecita-iwjz.3-…`, `feat/nubecita-iwjz.4-…`, `feat/nubecita-iwjz.2-…`.
  - Commits are Conventional Commits with a `Refs: <bd-id>` footer. PR bodies say `Closes: <bd-id>`.
  - No Claude/Co-Authored-By footer. Never `--no-verify`.
- **Flavored modules** (use `lintProductionDebug` / `testProductionDebugUnitTest`):
  - `:core:post-interactions`, `:core:profile`, `:core:actors`, `:feature:profile:impl`, `:feature:feed:impl`, `:feature:videos:impl` (`git grep -l nubecita.android.flavors -- feature/videos/impl` confirms the last one).
  - `:designsystem`, `:data:models` and `:core:feed-mapping` are **unflavored**.
- **Strings.** Every new string ships es-419 (`values-es-rUS`/`values-b+es+419`, mirror the existing folder) and pt-BR in the same commit. Run the owning module's own lint.
- **Icons.** Add glyphs by codepoint only: `Translate` = `e8e2`, `Delete` = `e872`. See memory `icon-font-add-glyph-by-codepoint`. Never regenerate the whole font.
- **Screenshot baselines.** Never commit locally regenerated baselines. Add previews, then let the `update-baselines` PR label / CI bot write them (memory `never-commit-local-baselines-use-ci-bot`).
- **No `!!`.** Use `runCatchingCancellable`-style handling: never a bare `runCatching` in a suspend fun (pre-commit hook).
- **Scaffold.** Any `Scaffold` sets `containerColor = MaterialTheme.colorScheme.surface`. The sheet is a windowed surface, so tonal elevation is allowed.
- **Copy (exact):**
  - Pin rows: "Pin to your profile", "Unpin from profile".
  - Thread notices: "Thread muted", "Thread unmuted", "Couldn't mute thread", "Couldn't unmute thread".
  - Pin notices: "Post pinned", "Post unpinned", "Couldn't pin post", "Couldn't unpin post".
- **Laziness.** A closed overflow composes no sheet and no sheet state (120 Hz is a hard requirement).

## Review Focus

1. **Two pins in quick succession on different posts.** All pins write the same single profile record, so a second pin started while the first is in flight would race on `swapRecord`. Expected: while a pin/unpin write is in flight, any further pin/unpin tap on any post is ignored. Test in Task 12: `secondPinWhileFirstInFlightIsIgnored`.
2. **Pinning on an account with no profile record yet.** Expected: the writer creates the record (stamping `$type`, omitting `swapRecord`) and the pin succeeds. Test in Task 10: `updateCreatesRecordWhenNoneExists`.
3. **Unpin when the record has no `pinnedPost` at all.** For example, it was already unpinned on another device. Expected: no write, success, and the local flag clears. Test in Task 11: `unpinWithNoPinnedPostWritesNothing`.
4. **Unmute, then a stale refresh still says `threadMuted = true`.** Expected: the post stays unmuted, because the override applies in both directions, unlike likes. Test in Task 8: `staleRefreshDoesNotReMuteAfterUnmute`.
5. **Muting a top-level post (no reply root).** Expected: `muteThread(root = post.id)`, and the flip covers cached replies whose `threadRootUri == post.id`. Test in Task 8: `muteOnRootPostFlipsItsReplies`.

---

## PR 1 — `nubecita-iwjz.3`: the sheet

Before Task 1, from an up-to-date `main`: `gh stack init feat/nubecita-iwjz.3-post-overflow-sheet`.

### Task 1: Map pinned, thread-muted and reply root

**Files:**
- Modify:
  - `data/models/src/main/kotlin/net/kikin/nubecita/data/models/ViewerStateUi.kt`
  - `data/models/src/main/kotlin/net/kikin/nubecita/data/models/PostUi.kt`
  - `core/feed-mapping/src/main/kotlin/net/kikin/nubecita/core/feedmapping/FeedMapping.kt:67-98,183-205`
- Test: `core/feed-mapping/src/test/kotlin/net/kikin/nubecita/core/feedmapping/PinAndThreadMuteMappingTest.kt` (mirror `BookmarkMappingTest.kt`'s fixture style)

**Interfaces:**
- Produces:
  - `ViewerStateUi.isPinned: Boolean = false`
  - `ViewerStateUi.isThreadMuted: Boolean = false`
  - `PostUi.threadRootUri: String? = null`, added as the last constructor param with a default so existing call sites compile. It holds `record.reply.root.uri`, or `null` for a top-level post.
  - The thread key everywhere is `post.threadRootUri ?: post.id`.

- [ ] **Step 1: Write the failing tests**
  - `pinnedTrueMapsToIsPinned`: `viewer.pinned = true` → `isPinned == true`
  - `absentFlagsDefaultFalse`: no `pinned` / `threadMuted` → both `false`
  - `threadMutedMaps`: `viewer.threadMuted = true` → `isThreadMuted == true`
  - `replyCarriesRoot`: record `reply.root.uri = "at://did:plc:a/app.bsky.feed.post/root"` → `threadRootUri == "at://did:plc:a/app.bsky.feed.post/root"`
  - `topLevelHasNullRoot`: no `reply` → `threadRootUri == null`
- [ ] **Step 2:** Run `./gradlew :core:feed-mapping:testDebugUnitTest --tests '*PinAndThreadMuteMappingTest*'`. Expected: compile FAIL, because the fields don't exist yet.
- [ ] **Step 3: Implement**
  - Add the fields.
  - In `toViewerStateUi`, set `isPinned = this?.pinned == true` and `isThreadMuted = this?.threadMuted == true`.
  - In `toPostUiCore`, set `threadRootUri = postRecord.reply.valueOrNull()?.root?.uri?.raw`. Match how the decoded `Post.reply` `AtField` is read elsewhere in the file.
- [ ] **Step 4:** Rerun the test. Expected: PASS. Then run `./gradlew :core:feed-mapping:testDebugUnitTest :data:models:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** — `feat(feed-mapping): map viewer pinned, threadMuted and the reply root`

### Task 2: Grouped overflow rule function

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostOverflowRows.kt`
- Test: `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/component/PostOverflowRowsTest.kt`

**Interfaces:**
- Consumes: `ViewerStateUi.isThreadMuted` (Task 1)
- Produces:
  - `@Immutable data class PostOverflowGroup(val actions: ImmutableList<PostOverflowAction>)`
  - `fun postOverflowGroups(post: PostUi): ImmutableList<PostOverflowGroup>` (public, pure, empty groups omitted)

- [ ] **Step 1: Write the failing tests.** Assert the exact group lists:
  - `ownPostGroups`: own post with text →
    `[[TranslatePost, CopyPostText], [MuteThread], [DeletePost]]`
  - `othersPostGroups`: someone else's post →
    `[[TranslatePost, CopyPostText], [MuteThread], [MuteAuthor, BlockAuthor, ReportPost, ReportAuthor]]`
  - `blankTextDropsTranslate`: text `"  "` → the first group is `[CopyPostText]`
  - `mutedAndBlockedAuthorTogglePairs`: `isAuthorMutedByViewer` + `isAuthorBlockedByViewer` → `UnmuteAuthor`, `UnblockAuthor`
  - `threadMutedShowsUnmute`: → `[UnmuteThread]`
  - `ownPostNeverHasSelfModeration`: own post, all flags true → none of `ReportPost`, `ReportAuthor`, `MuteAuthor`, `UnmuteAuthor`, `BlockAuthor`, `UnblockAuthor`
- [ ] **Step 2:** Run `./gradlew :designsystem:testDebugUnitTest --tests '*PostOverflowRowsTest*'`. Expected: FAIL (unresolved).
- [ ] **Step 3:** Implement `postOverflowGroups` with `buildList`, in the D2 order from the spec.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** — `feat(designsystem): grouped post overflow rule function`

### Task 3: The sheet composable (+ Translate/Delete glyphs)

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostOverflowSheet.kt`
- Modify:
  - `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt`
  - the icon subset script/inputs, per memory `icon-font-add-glyph-by-codepoint`
- Test: `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/PostOverflowSheetScreenshotTest.kt`

**Interfaces:**
- Consumes: `postOverflowGroups` (Task 2)
- Produces:
  - `@Composable fun PostOverflowSheet(post: PostUi, onDismiss: () -> Unit, onAction: (PostOverflowAction) -> Unit)`
  - `@Composable internal fun PostOverflowGroupsContent(groups: ImmutableList<PostOverflowGroup>, handle: String, onAction: (PostOverflowAction) -> Unit)`, the sheet body, split out so screenshots render without a window
  - `@Composable fun postOverflowLabel(action: PostOverflowAction, handle: String): String`, moved from `VideoOverflowMenu.overflowActionLabel` and made public
  - `fun PostOverflowAction.iconName(): NubecitaIconName`:

    | Action | Icon |
    |---|---|
    | Translate | `Translate` |
    | Copy | `ContentCopy` |
    | MuteThread | `NotificationsOff` |
    | UnmuteThread | `Notifications` |
    | Mute author | `VolumeOff` |
    | Unmute author | `VolumeUp` |
    | Block / Unblock | `Block` |
    | Report post / author | `Flag` |
    | Delete | `Delete` |

- [ ] **Step 1:** Add the `Translate` (`e8e2`) and `Delete` (`e872`) glyphs by codepoint. Verify with `./gradlew :designsystem:compileDebugKotlin`.
- [ ] **Step 2:** Write screenshot previews of `PostOverflowGroupsContent` inside `NubecitaCanvasPreviewTheme`, each in light and dark:
  - `OwnPost`
  - `OthersPost`
  - `OthersPostMutedBlocked`
  - `ThreadMuted`
  - `MediaOnlyOwnPost`
- [ ] **Step 3: Implement**
  - `ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true))` with a drag handle and no title.
  - Groups use `NubecitaListGroup` with `NubecitaListItem(headlineContent = label, trailingContent = icon)`, with vertical spacing between the cards.
  - A row tap hides the sheet (`sheetState.hide()`), then calls `onDismiss()`, then `onAction(action)`, exactly once.
- [ ] **Step 4:** Run `./gradlew :designsystem:compileDebugScreenshotTestKotlin :designsystem:lintDebug`. Expected: PASS. Don't commit baselines.
- [ ] **Step 5: Commit** — `feat(designsystem): grouped post overflow bottom sheet`

### Task 4: PostCard opens the sheet (lazily)

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt:760-920`, `PostOverflowAffordance`
- Modify (test): `designsystem/src/androidTest/kotlin/net/kikin/nubecita/designsystem/component/DropdownMenuLazinessTest.kt`. Rename it to `OverflowLazinessTest.kt`, keep the repost-menu case, and convert the overflow case.

**Interfaces:**
- Consumes: `PostOverflowSheet` (Task 3)

- [ ] **Step 1: Update the laziness test**
  - `closedOverflowComposesNoSheet`: no node tagged `postOverflowSheet` exists, and none of the row labels exist.
  - `openedOverflowShowsGroups`: click "More", and the sheet tag plus "Copy post text" are displayed.
  - Tag the sheet `Modifier.testTag("postOverflowSheet")`.
- [ ] **Step 2:** Run `./gradlew :designsystem:compileDebugAndroidTestKotlin`. Expected: compile OK; it runs in CI under `run-instrumented`.
- [ ] **Step 3: Implement.** Replace the `DropdownMenu` block with `if (expanded) PostOverflowSheet(post, onDismiss = { expanded = false }, onAction = onAction)`. Delete the per-row code and update the KDoc. The closed affordance (the `PostStat` "…") stays identical, so existing `PostCard` baselines don't change.
- [ ] **Step 4:** Run `./gradlew :designsystem:testDebugUnitTest :designsystem:lintDebug`. Expected: PASS.
- [ ] **Step 5: Commit** — `feat(designsystem): open the post overflow as a bottom sheet`

### Task 5: Video page uses the shared sheet

**Files:**
- Modify:
  - `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoPageChrome.kt:~130`
- Delete:
  - `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoOverflowMenu.kt`
  - `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoOverflowMenuTest.kt`

**Interfaces:**
- Consumes: `PostOverflowSheet`, `postOverflowGroups` (Tasks 2–3)

- [ ] **Step 1:** Replace the `VideoOverflowMenu(...)` call with `if (expanded) PostOverflowSheet(post, onDismiss = …, onAction = …)`. Delete both files. Behaviour parity is now guaranteed by construction and covered by `PostOverflowRowsTest`, so add no duplicate test.
- [ ] **Step 2:** Run `./gradlew :feature:videos:impl:testProductionDebugUnitTest :feature:videos:impl:lintProductionDebug`. Expected: PASS.
- [ ] **Step 3: Commit** — `feat(videos): use the shared post overflow sheet`

### Task 6: Bench fixtures + PR 1 gate

**Files:**
- Modify:
  - `feature/feed/impl/src/bench/kotlin/net/kikin/nubecita/feature/feed/impl/data/BenchTimelineDto.kt:150-159` (`BenchViewerDto` gains `isOwnPost`, `isPinned`, `isThreadMuted`; the post DTO gains `threadRootUri`)
  - `.../BenchTimelineMapper.kt:161-171`
  - `feature/feed/impl/src/bench/assets/timeline.json`

- [ ] **Step 1: Add fixtures.** Add two posts authored by `did:plc:benchnubecita0000000000000` near the top of `timeline.json`:
  - one with `isOwnPost: true, isPinned: true`
  - one own reply with `isOwnPost: true` and a `threadRootUri`

  Also set `isThreadMuted: true` on one existing post.
- [ ] **Step 2:** Run `./gradlew :feature:feed:impl:compileBenchDebugKotlin :app:assembleBenchDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Device pass.** Install the bench build and open the sheet from Feed, Post detail, Profile and Videos.
  - Screenshot the own-post sheet (no Report/Mute/Block, Delete last) and an others'-post sheet.
  - Tapping "Mute thread" still shows "coming soon", which is expected in PR 1.
- [ ] **Step 4:** Lint the touched modules: `:designsystem:lintDebug :feature:videos:impl:lintProductionDebug :feature:feed:impl:lintProductionDebug :core:feed-mapping:lintDebug`. Expected: PASS.
- [ ] **Step 5: Commit, then submit.**
  - Commit: `test(feed): bench fixtures for own, pinned and thread-muted posts`
  - `gh stack submit`.
  - Add the `update-baselines` label to PR 1 for the new sheet baselines, then `gh stack sync`.

---

## PR 2 — `nubecita-iwjz.4`: real thread mute (`gh stack add feat/nubecita-iwjz.4-real-thread-mute`)

### Task 7: ThreadMuteRepository

**Files:**
- Create:
  - `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/ThreadMuteRepository.kt`
  - `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultThreadMuteRepository.kt`
  - `core/post-interactions/src/bench/kotlin/net/kikin/nubecita/core/postinteractions/internal/BenchFakeThreadMuteRepository.kt`
- Modify: the production and bench `di/PostInteractionsWriteModule.kt` (bind both)
- Test: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultThreadMuteRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `interface ThreadMuteRepository { suspend fun mute(rootUri: String): Result<Unit>; suspend fun unmute(rootUri: String): Result<Unit> }`
  - The bench fake returns `success` after `delay(150)`.

- [ ] **Step 1: Failing tests**
  - `muteSendsRootUri`: the captured `MuteThreadRequest.root.raw == rootUri`
  - `unmuteSendsRootUri`: the same check for `unmute`
  - `failureIsResultFailure`: the client throws `IOException` → `isFailure`
  - `cancellationPropagates`

  Mirror `DefaultBookmarkRepository`'s test setup (mocked `XrpcClientProvider`).
- [ ] **Step 2:** Run `./gradlew :core:post-interactions:testProductionDebugUnitTest --tests '*DefaultThreadMuteRepositoryTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** with `GraphService(xrpcClientProvider.authenticated()).muteThread(MuteThreadRequest(root = AtUri(rootUri)))`, wrapped with cancellation-safe catching on `dispatcher`, and redaction-safe logging (error class only).
- [ ] **Step 4:** Rerun. Expected: PASS. Run `./gradlew :core:post-interactions:compileBenchDebugKotlin`. Expected: OK.
- [ ] **Step 5: Commit** — `feat(post-interactions): add ThreadMuteRepository over app.bsky.graph.muteThread`

### Task 8: Cache — thread-mute overrides and `toggleThreadMute`

**Files:**
- Modify:
  - `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/PostInteractionState.kt`
  - `.../PostInteractionsCache.kt`
  - `.../internal/DefaultPostInteractionsCache.kt:41-98`
  - `.../MergeInteractionState.kt`
- Test: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/internal/ThreadMuteCacheTest.kt`

**Interfaces:**
- Consumes: `ThreadMuteRepository` (Task 7), `PostUi.threadRootUri`, `ViewerStateUi.isThreadMuted` (Task 1)
- Produces:
  - New `PostInteractionState` fields: `threadRootUri: String? = null`, `isThreadMuted: Boolean = false`, `threadMuteOverride: Boolean? = null`
  - `suspend fun PostInteractionsCache.toggleThreadMute(postUri: String): Result<Boolean>`, which returns the new muted value
  - `PostUi.mergeInteractionState` also sets `viewer.isThreadMuted = state.isThreadMuted`

**Override rule:**
- `private fun PostInteractionState.withWireOverlays(post: PostUi): PostInteractionState` applies in **every** `seed` branch, including the `existing` branches, next to `withWireReplyCount`. It always refreshes `threadRootUri`.
- If `threadMuteOverride == null`: `isThreadMuted = post.viewer.isThreadMuted`.
- Else, if the wire equals the override: set `isThreadMuted` to that value and clear the override to `null`.
- Otherwise, keep the override.
- This is deliberately two-way, unlike likes. See Review Focus 4.

`toggleThreadMute`:
- `key = threadRootUri ?: postUri`.
- Ignore it (return the current value) if a job for `key` is active.
- Flip `isThreadMuted` and set the override on every entry whose `(threadRootUri ?: id) == key`.
- Call the repo. On failure, restore those entries' previous states.

- [ ] **Step 1: Failing tests** (use `putForTest` / `seed`)
  - `muteFlipsEveryPostInThread`: three posts share root R → all `isThreadMuted` right away
  - `muteOnRootPostFlipsItsReplies`: tap on R itself → its replies flip, and the repo is called with R
  - `muteFromReplySendsRoot`: the repo is called with R, not the reply's URI
  - `failureRestoresAll`
  - `staleRefreshKeepsMute`: after success, `seed` with the wire `false` → still `true`
  - `staleRefreshDoesNotReMuteAfterUnmute`: unmute succeeds, `seed` with the wire `true` → still `false`
  - `wireAgreementClearsOverride`
  - `repeatTapWhileInFlightIgnored`: the repo is called once
- [ ] **Step 2:** Run `./gradlew :core:post-interactions:testProductionDebugUnitTest --tests '*ThreadMuteCacheTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement as above. Inject `ThreadMuteRepository` into `DefaultPostInteractionsCache`. Keep a `threadMuteJobs` map keyed by root.
- [ ] **Step 4:** Rerun, then the full `:core:post-interactions:testProductionDebugUnitTest`. Expected: PASS. Existing like/repost/bookmark seed tests must stay green.
- [ ] **Step 5: Commit** — `feat(post-interactions): optimistic thread mute in the interactions cache`

### Task 9: Handler routing, notices, strings, device pass

**Files:**
- Modify:
  - `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/PostInteractionHandler.kt` (adds `InteractionEffect.ShowNotice`)
  - `.../internal/DefaultPostInteractionHandler.kt:240-295`
  - `core/post-interactions-ui/src/main/kotlin/net/kikin/nubecita/core/postinteractions/ui/PostInteractions.kt` (collector)
  - `designsystem/src/main/res/values{,-es-*,-pt-rBR}/strings.xml`, mirroring the folders that hold `moderation_action_unmute_thread`
- Test: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionHandlerTest.kt` (extend)

**Interfaces:**
- Produces:
  - `enum class InteractionNotice { ThreadMuted, ThreadUnmuted, MuteThreadFailed, UnmuteThreadFailed, PostPinned, PostUnpinned, PinFailed, UnpinFailed }`
  - `data class ShowNotice(val notice: InteractionNotice) : InteractionEffect`
  - `rememberPostInteractions` resolves each notice with `stringResource` from new `:designsystem` strings:
    - `post_notice_thread_muted`, `post_notice_thread_unmuted`
    - `post_notice_mute_thread_failed`, `post_notice_unmute_thread_failed`
    - and the pin ones in Task 13

  This is my call: the 7 `InteractionStrings(...)` construction sites stay untouched.

- [ ] **Step 1: Failing tests**
  - `muteThreadCallsCacheAndEmitsThreadMuted`
  - `unmuteThreadEmitsThreadUnmuted`
  - `muteThreadFailureEmitsMuteThreadFailed`
  - `unblockAuthorStillComingSoon`
  - `muteThreadNoLongerComingSoon`

  Assert on `vm.interactionEffects` / the handler's `interactionEffects` with Turbine.
- [ ] **Step 2:** Run `./gradlew :core:post-interactions:testProductionDebugUnitTest --tests '*DefaultPostInteractionHandlerTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Route `MuteThread` / `UnmuteThread` with `requireScope().launch { cache.toggleThreadMute(post.id).fold(...) }`. Remove them from the coming-soon branch. Add the strings with es-419 and pt-BR. Add the `ShowNotice` collector branch (replace-not-stack snackbar, like `ShowComingSoon`).
- [ ] **Step 4:** Rerun, then lint: `./gradlew :core:post-interactions:lintProductionDebug :core:post-interactions-ui:lintDebug :designsystem:lintDebug` (check whether `:core:post-interactions-ui` is flavored). Expected: PASS, with no `MissingTranslation`.
- [ ] **Step 5: Device pass on the bench build.**
  - Mute the thread from the own reply fixture, then open the root post → "Unmute thread".
  - Unmute it.
  - Screenshot both states.
- [ ] **Step 6: Commit, then submit.**
  - Commit: `feat(post-interactions): wire Mute thread / Unmute thread to the server`
  - Then `gh stack submit`.

---

## PR 3 — `nubecita-iwjz.2`: pin (`gh stack add feat/nubecita-iwjz.2-pin-own-post`)

### Task 10: ProfileRecordWriter in `:core:profile`

**Files:**
- Create:
  - `core/profile/src/main/kotlin/net/kikin/nubecita/core/profile/ProfileRecordWriter.kt`
  - `core/profile/src/main/kotlin/net/kikin/nubecita/core/profile/internal/DefaultProfileRecordWriter.kt`
  - the bench binding (fake, in-memory `JsonObject`), in `core/profile/src/bench/.../BenchProfileRecordWriter.kt`
- Modify:
  - `core/profile/src/{production,bench}/kotlin/.../di/` (bind it)
  - `core/profile/build.gradle.kts` (adds `kotlinx-serialization-json` if not transitive)
  - `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/DefaultProfileRepository.kt:190-275,489-511` (`updateProfile` uses the writer, and `fetchProfileRecord` moves)
- Test:
  - `core/profile/src/test/kotlin/net/kikin/nubecita/core/profile/internal/DefaultProfileRecordWriterTest.kt`
  - the existing `DefaultProfileRepositoryTest` stays green

**Interfaces:**
- Produces:

```kotlin
interface ProfileRecordWriter {
    /**
     * Reads the signed-in viewer's app.bsky.actor.profile/self, applies [transform] and writes the result
     * back with swapRecord = the read CID (omitted when no record existed; `$type` is stamped on create).
     * [transform] receives null when there is no record yet; returning null skips the write.
     * Returns success(true) when written, success(false) when skipped. Raw XrpcErrors propagate as failures
     * (callers map them — updateProfile keeps mapToProfileUpdateError).
     */
    suspend fun update(transform: suspend (existing: JsonObject?) -> JsonObject?): Result<Boolean>
}
```

- [ ] **Step 1: Failing tests**
  - `updatePreservesUntouchedFields`: the read record has displayName, description, avatar, banner, labels, links and pinnedPost, and the transform changes only `description` → the written record equals the read one except for `description`
  - `updateSendsSwapRecordCid`
  - `updateCreatesRecordWhenNoneExists`: `getRecord` throws `RecordNotFound` → the transform gets `null`, the write omits `swapRecord`, and `$type == "app.bsky.actor.profile"`
  - `nullTransformSkipsWrite`: `putRecord` is never called, and the result is `success(false)`
  - `signedOutFails`
- [ ] **Step 2:** Run `./gradlew :core:profile:testProductionDebugUnitTest --tests '*DefaultProfileRecordWriterTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Move `fetchProfileRecord` and the `putRecord` call. Re-express `updateProfile` as `writer.update { existing -> buildMergedRecord(...) + link writes }`, followed by `deleteLinkRecords` and `_ownProfileUpdates.tryEmit`.
- [ ] **Step 4:** Rerun, then run `./gradlew :core:profile:testProductionDebugUnitTest :feature:profile:impl:testProductionDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** — `refactor(profile): extract the profile-record read-modify-write into :core:profile`

### Task 11: Pin writer with compare-and-clear

**Files:**
- Create: `core/profile/src/main/kotlin/net/kikin/nubecita/core/profile/PinnedPostRepository.kt` (interface plus a `@Inject` default implementation over `ProfileRecordWriter`, which needs no bench variant because it rides the bench writer)
- Test: `core/profile/src/test/kotlin/net/kikin/nubecita/core/profile/PinnedPostRepositoryTest.kt`

**Interfaces:**
- Consumes: `ProfileRecordWriter.update` (Task 10)
- Produces:
  - `interface PinnedPostRepository { suspend fun pin(uri: String, cid: String): Result<Unit>; suspend fun unpin(uri: String): Result<Unit> }`
  - `pin` sets `pinnedPost = {"uri", "cid"}`.
  - `unpin` removes the `pinnedPost` key only when `existing?.get("pinnedPost")?.uri == uri`; otherwise the transform returns `null`.

- [ ] **Step 1: Failing tests**
  - `pinSetsStrongRef`
  - `unpinClearsWhenPointingAtPost`
  - `unpinElsewhereWritesNothing`: the record points at B, unpin A → no write, success
  - `unpinWithNoPinnedPostWritesNothing`
- [ ] **Step 2:** Run `./gradlew :core:profile:testProductionDebugUnitTest --tests '*PinnedPostRepositoryTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** — `feat(profile): pin and compare-and-clear unpin of the profile's pinnedPost`

### Task 12: Cache pin overrides, handler routing, analytics

**Files:**
- Modify:
  - `core/post-interactions/build.gradle.kts` (`implementation(project(":core:profile"))`)
  - `PostInteractionState.kt`
  - `PostInteractionsCache.kt`
  - `internal/DefaultPostInteractionsCache.kt`
  - `MergeInteractionState.kt`
  - `internal/DefaultPostInteractionHandler.kt`
  - `designsystem/.../PostOverflowAction.kt` (adds `PinPost`, `UnpinPost`)
  - `core/analytics/.../AnalyticsEvent.kt:888` (adds `PostMenuAction.PinPost("pin_post")` and `UnpinPost("unpin_post")`, plus their mapping)
  - every exhaustive `when` over `PostOverflowAction` (`git grep -n 'PostOverflowAction\.' -- '*.kt'`, including `PostInteractions.kt` `ShowComingSoon` and the feed/profile/postdetail VMs' `onOverflowAction` overrides)
- Test: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/internal/PinCacheTest.kt`, plus extend `DefaultPostInteractionHandlerTest`

**Interfaces:**
- Consumes: `PinnedPostRepository` (Task 11), `ViewerStateUi.isPinned` (Task 1)
- Produces:
  - New `PostInteractionState` fields: `isPinned: Boolean = false`, `pinOverride: Boolean? = null`. These follow the same two-way override rule as Task 8, inside `withWireOverlays`.
  - `suspend fun PostInteractionsCache.togglePin(postUri: String, postCid: String): Result<Boolean>`:
    - It holds one global in-flight job, because there is one profile record. While that job runs, any call returns the current value without writing.
    - Pinning B sets B true and every other entry with an effective `isPinned` false, all as overrides.
    - On failure, it restores every changed entry.
  - `mergeInteractionState` sets `viewer.isPinned = state.isPinned`.
  - The handler routes `PinPost` / `UnpinPost` → `togglePin` → `ShowNotice(PostPinned | PostUnpinned | PinFailed | UnpinFailed)`.

- [ ] **Step 1: Failing tests**
  - `pinMovesFromAToB`: A pinned, pin B → B true and A false right away
  - `pinFailureRestoresBoth`
  - `staleRefreshKeepsMovedPin`: after success, `seed` with A wire `true` and B wire `false` → A false, B true
  - `secondPinWhileFirstInFlightIsIgnored`: the repo is called once
  - `unpinClearsOnlyThisPost`
  - Handler: `pinPostEmitsPostPinned`, `pinFailureEmitsPinFailed`
- [ ] **Step 2:** Run `./gradlew :core:post-interactions:testProductionDebugUnitTest --tests '*PinCacheTest*' --tests '*DefaultPostInteractionHandlerTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Then check every implementor of the interface you changed: run `git grep -n ': PostInteractionsCache\|PostInteractionsCache {' -- '*.kt'`, and update any fakes, such as `FakePostInteractionHandler` in the profile androidTest.
- [ ] **Step 4:** Rerun, then run `./gradlew jacocoTestReportAggregated`. This covers the flavored modules that the root `testDebugUnitTest` skips. Expected: PASS.
- [ ] **Step 5: Commit** — `feat(post-interactions): optimistic pin and unpin with a single in-flight write`

### Task 13: Pin row in the sheet + strings

**Files:**
- Modify:
  - `designsystem/.../PostOverflowRows.kt`
  - `PostOverflowSheet.kt` (label + icon `Keep` for both)
  - `designsystem/src/main/res/values*/strings.xml`: `post_overflow_pin` "Pin to your profile", `post_overflow_unpin` "Unpin from profile", `post_notice_post_pinned`, `post_notice_post_unpinned`, `post_notice_pin_failed`, `post_notice_unpin_failed`
  - `PostInteractions.kt` (resolve the pin notices)
- Test: extend `PostOverflowRowsTest`; add the screenshot previews `OwnPostPinned` and `OwnPostUnpinned`

**Interfaces:**
- Consumes: `PostOverflowAction.PinPost` / `UnpinPost` (Task 12), `ViewerStateUi.isPinned`

- [ ] **Step 1: Failing tests**
  - `ownPostFirstGroupIsPin`: → `[PinPost]`
  - `pinnedOwnPostShowsUnpin`
  - `ownReplyIsPinnable`: an own post with non-null `threadRootUri` still gets `[PinPost]`
  - `othersPostHasNoPinRow`
- [ ] **Step 2:** Run `./gradlew :designsystem:testDebugUnitTest --tests '*PostOverflowRowsTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Add all six strings with es-419 and pt-BR.
- [ ] **Step 4:** Rerun, then `./gradlew :designsystem:lintDebug :designsystem:compileDebugScreenshotTestKotlin`. Expected: PASS.
- [ ] **Step 5: Commit** — `feat(designsystem): Pin to your profile / Unpin from profile in the overflow sheet`

### Task 14: Profile pinned slot follows the cache + full gate

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt:420-450,840-856`
- Test: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt` (extend)

**Interfaces:**
- Consumes: `PostInteractionState.pinOverride` (Task 12)
- Rule (applied in `applyInteractions` plus a check in the `cache.state` collector):
  - If any cache entry has `pinOverride == true` and its URI authority equals the profile's DID, and it isn't the current `pinnedPost`:
    - use the matching `PostUi` from the loaded tabs if one is present
    - otherwise call the existing `resolvePinnedPost(PinnedPostRef(uri = …, cid = …))`
  - If the current `pinnedPost` has `pinOverride == false`, clear it.

- [ ] **Step 1: Failing tests**
  - `pinFromAnotherSurfaceFillsSlot`: the cache emits an override for an own post that's in the Posts tab → `pinnedPost` is that post
  - `pinOfUnloadedPostResolvesIt`: the resolve is invoked with that URI
  - `unpinClearsSlot`
  - `otherAccountsPinIgnored`: the authority ≠ the profile DID → no change
- [ ] **Step 2:** Run `./gradlew :feature:profile:impl:testProductionDebugUnitTest --tests '*ProfileViewModelTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Don't touch `ownProfileUpdates`.
- [ ] **Step 4: Full top-of-stack gate**
  - `./gradlew :app:assembleDebug`
  - every touched module's lint (flavored names as in Global Constraints)
  - `./gradlew jacocoTestReportAggregated`
  - the `compose-expert` skill on `git diff origin/main...HEAD` (run the skill, not a self-review)
- [ ] **Step 5: Device pass on the bench build.** Do these steps in order and screenshot each persisted state:
  1. Pin the own fixture post from Feed.
  2. Open Profile: it shows in the pinned slot, and its sheet reads "Unpin from profile".
  3. Pin another own post: the slot moves.
  4. Unpin: the slot clears.
- [ ] **Step 6: Commit, then submit.**
  - Commit: `feat(profile): pinned slot follows pin changes from any surface`
  - Then `gh stack submit`, and add the `update-baselines` label to PR 3.
  - Land with `gh stack merge --squash --yes` only when the user asks.
