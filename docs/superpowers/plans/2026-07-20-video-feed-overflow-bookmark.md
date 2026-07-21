# Video Feed Overflow Menu & Bookmark (nubecita-zdv8.12) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bookmark toggle and an overflow menu (report / mute / block) to the vertical video feed's right-rail chrome.

**Architecture:** Pure UI-layer. The ViewModel already delegates `PostInteractionHandler`, and `rememberVideoFeedInteractions` already returns `PostCallbacks` with `onBookmark` and `onOverflowAction` bound plus an effect collector that routes report/block/mute/coming-soon. So this PR adds two rail cells and a `DropdownMenu`, wired to callbacks that already exist. No ViewModel change.

**Tech Stack:** Kotlin 2.3, Jetpack Compose, Material 3, JUnit Jupiter, AGP Compose screenshot testing.

**bd:** nubecita-zdv8.12. Design context: `docs/superpowers/specs/2026-07-19-video-feed-chrome-design.md` (D5/D6 + the accessibility split in its `## Testing` section).

## Global Constraints

- Module `:feature:videos:impl`; package root `net.kikin.nubecita.feature.videos.impl`.
- **No ViewModel change.** Bookmark and overflow route entirely through the existing `PostCallbacks` from `rememberVideoFeedInteractions`. The VM's existing `postInteractionsCache.state → items` merge already carries `isBookmarked` (verified: `PostInteractionState.isBookmarked` → `mergeInteractionState` sets it), so the bookmark icon fills through the same path likes use.
- **Accessibility split (spec `## Testing`):** bookmark is a **toggle** → `Modifier.toggleable(role = Role.Switch)` with the label as the icon's `contentDescription`, static noun ("Bookmark"), never "Unbookmark". Overflow is a **one-shot** trigger → `clickable(role = Role.Button, onClickLabel = …)`. `VideoRailAction` already implements this split — the bookmark cell reuses it; the overflow cell is a plain clickable that opens the menu.
- **Mute delegates; it does NOT optimistically remove the video.** CLAUDE.md: a surface overrides `onOverflowAction` only for actions it handles locally. The Feed removes the muted author's posts from its list, but the video pager's optimistic-remove would have to re-index the pool's active slot — real complexity for v1. This PR delegates mute to `handler.onOverflowAction` (which does the real `muteRepository.muteActor` network call); the muted video stays until the next feed load. Noted as a known limitation; optimistic removal is a possible follow-up.
- Every new `<string>` needs `values-b+es+419` **and** `values-pt-rBR` entries or CI `Lint / android` fails `MissingTranslation`. Run `./gradlew :feature:videos:impl:lintDebug`.
- New `testTag` values are pinned in `VideoFeedTestTagsTest`.
- No Kotlin `!!`. JUnit **Jupiter**. ktlint via Spotless before every commit.
- Conventional Commits; footer `Refs: nubecita-zdv8.12`. `Closes:` in the PR body only.
- **Verify on the plugged-in Pixel Fold** (serial `37201FDHS002UN`). If a fixture can't exercise something, extend it.
- Branch `feat/nubecita-zdv8.12-video-feed-overflow-bookmark` exists off fresh `main`. Never commit to `main`.

## Verified Facts

Confirmed on `main`. Use exactly.

| Thing | Fact |
|---|---|
| Callbacks already bound | `rememberVideoFeedInteractions(viewModel, snackbarHostState)` returns `PostCallbacks` whose `onBookmark(post)` and `onOverflowAction(post, action)` are already wired by the shared helper. The effect collector already maps `ShowComingSoon`/`NavigateToReport`/`NavigateToBlock`/`CopyPostText`. **Nothing to add on the VM/effect side.** |
| Bookmark loop | `PostInteractionState.isBookmarked/bookmarkCount/pendingBookmarkWrite` exist; `mergeInteractionState` sets `isBookmarked` (`MergeInteractionState.kt:44`); `cache.toggleBookmark(id, cid)` is real. The VM's PR2 cache merge propagates it. Rail cell reads `post.viewer.isBookmarked`. |
| Overflow action type | `PostOverflowAction` (`designsystem/.../PostOverflowAction.kt:24`): `ReportPost, MuteAuthor, UnmuteAuthor, BlockAuthor, UnblockAuthor, MuteThread, UnmuteThread, CopyPostText`. |
| Viewer flags | `ViewerStateUi`: `isBookmarked`, `isAuthorMutedByViewer`, `isAuthorBlockedByViewer` (all `Boolean`). |
| Menu-item set (mirror PostCard.kt:736-812) | Report (always); one of Unmute/Mute keyed on `isAuthorMutedByViewer`; one of Unblock/Block keyed on `isAuthorBlockedByViewer`; MuteThread (always); CopyPostText (always). `UnmuteThread` never appears in the closed menu. |
| Menu strings (reuse, don't clone) | In `:designsystem` → `net.kikin.nubecita.designsystem.R.string.moderation_action_{report_post,mute_author,unmute_author,block_author,unblock_author,mute_thread,copy_post_text}`. Mute/block labels take `post.author.handle` as `%1$s`. es-419 + pt-BR already exist. |
| Icons | `NubecitaIconName.Bookmark`, `NubecitaIconName.MoreVert` both exist. (PostCard's action row uses `MoreHoriz`; the vertical rail uses `MoreVert` (⋮) — the vertical-feed convention.) |
| Overflow menu is NOT reusable | `PostOverflowAffordance` is `private` in PostCard and coupled to `internal PostStat`. Build a videos-local `DropdownMenu`. Item logic to mirror is PostCard.kt:736-812. |
| Screenshot limits | Layoutlib can't render an open `DropdownMenu` deterministically (overlay window). Cover the **closed** rail (bookmark filled/empty, overflow present) with screenshots; cover the item-set logic with a **unit test** on a pure function; cover menu-open on the **device**. |

## Rail order (D6)

Top→bottom: **like, repost, bookmark, reply, share, overflow**, then **mute** at the foot (mute is a playback control, visually separated from the post-interaction cells). This is easy to tweak after the device screenshot; the order is not load-bearing.

## File Structure

| File | Responsibility |
|---|---|
| `.../impl/ui/VideoOverflowMenu.kt` | **Create.** Pure `videoOverflowActions(viewer): ImmutableList<PostOverflowAction>` + the `VideoOverflowMenu` `DropdownMenu` composable. |
| `.../impl/ui/VideoPageChrome.kt` | **Modify.** Add bookmark + overflow cells; new `onBookmark`, `onOverflowAction` params; own the menu's `expanded` state. |
| `.../impl/VideoFeedScreen.kt` | **Modify.** Wire `onBookmark`/`onOverflowAction` to the existing `callbacks`. |
| `.../impl/VideoFeedTestTags.kt` (+ test) | **Modify.** `RAIL_BOOKMARK`, `RAIL_OVERFLOW`. |
| `res/values{,-b+es+419,-pt-rBR}/strings.xml` | **Modify.** `videos_action_bookmark`, `videos_action_more`. |
| `.../screenshotTest/.../VideoFeedPageScreenshotTest.kt` | **Modify.** Closed-state matrix. |

---

### Task 1: Pure overflow-item logic + test tags

**Files:**
- Create: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoOverflowMenu.kt`
- Test: `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoOverflowMenuTest.kt`
- Modify: `.../impl/VideoFeedTestTags.kt` + `.../src/test/.../VideoFeedTestTagsTest.kt`

**Interfaces:**
- Produces: `internal fun videoOverflowActions(viewer: ViewerStateUi): ImmutableList<PostOverflowAction>`; tags `RAIL_BOOKMARK = "video_feed_bookmark"`, `RAIL_OVERFLOW = "video_feed_overflow"`.

- [ ] **Step 1: Write the failing tests**

Create `VideoOverflowMenuTest.kt`:

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VideoOverflowMenuTest {
    @Test
    fun `neutral viewer shows report, mute, block, mute-thread, copy`() {
        val actions = videoOverflowActions(ViewerStateUi())
        assertEquals(
            listOf(
                PostOverflowAction.ReportPost,
                PostOverflowAction.MuteAuthor,
                PostOverflowAction.BlockAuthor,
                PostOverflowAction.MuteThread,
                PostOverflowAction.CopyPostText,
            ),
            actions,
        )
    }

    @Test
    fun `a muted author shows Unmute, not Mute`() {
        val actions = videoOverflowActions(ViewerStateUi(isAuthorMutedByViewer = true))
        assertTrue(PostOverflowAction.UnmuteAuthor in actions)
        assertFalse(PostOverflowAction.MuteAuthor in actions)
    }

    @Test
    fun `a blocked author shows Unblock, not Block`() {
        val actions = videoOverflowActions(ViewerStateUi(isAuthorBlockedByViewer = true))
        assertTrue(PostOverflowAction.UnblockAuthor in actions)
        assertFalse(PostOverflowAction.BlockAuthor in actions)
    }

    @Test
    fun `the closed menu never offers UnmuteThread`() {
        assertFalse(PostOverflowAction.UnmuteThread in videoOverflowActions(ViewerStateUi()))
    }
}
```

Add to `VideoFeedTestTagsTest`:

```kotlin
    @Test
    fun `overflow and bookmark tag values are pinned`() {
        assertEquals("video_feed_bookmark", VideoFeedTestTags.RAIL_BOOKMARK)
        assertEquals("video_feed_overflow", VideoFeedTestTags.RAIL_OVERFLOW)
    }
```

- [ ] **Step 2: Run to verify they fail**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoOverflowMenuTest*' --tests '*VideoFeedTestTagsTest*'
```

Expected: FAIL — `Unresolved reference: videoOverflowActions` / `RAIL_BOOKMARK`.

- [ ] **Step 3: Add the tags**

In `VideoFeedTestTags.kt`, inside the object:

```kotlin
    /** Right-rail bookmark toggle cell. */
    const val RAIL_BOOKMARK: String = "video_feed_bookmark"

    /** Right-rail overflow (⋮) trigger cell. */
    const val RAIL_OVERFLOW: String = "video_feed_overflow"
```

- [ ] **Step 4: Write the pure function**

Create `VideoOverflowMenu.kt` with just the function for now (the composable lands in Task 2):

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction

/**
 * The overflow items to show for [viewer], in display order. Mirrors PostCard's
 * closed-menu rules: report always; exactly one of mute/unmute and block/unblock
 * keyed on the viewer flags; mute-thread and copy always. `UnmuteThread` is a
 * type-surface-only variant and never appears here.
 *
 * Pure so the mute-vs-unmute / block-vs-unblock branching is unit-tested without
 * rendering a DropdownMenu (which layoutlib can't compose deterministically).
 */
internal fun videoOverflowActions(viewer: ViewerStateUi): ImmutableList<PostOverflowAction> =
    persistentListOf(
        PostOverflowAction.ReportPost,
        if (viewer.isAuthorMutedByViewer) PostOverflowAction.UnmuteAuthor else PostOverflowAction.MuteAuthor,
        if (viewer.isAuthorBlockedByViewer) PostOverflowAction.UnblockAuthor else PostOverflowAction.BlockAuthor,
        PostOverflowAction.MuteThread,
        PostOverflowAction.CopyPostText,
    )
```

- [ ] **Step 5: Run to verify pass**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoOverflowMenuTest*' --tests '*VideoFeedTestTagsTest*'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply -q
git add feature/videos/impl
git commit -m "feat(videos): overflow item-selection logic for the vertical feed

Pure videoOverflowActions(viewer) mirroring PostCard's closed-menu rules,
unit-tested so the mute/unmute and block/unblock branching is covered
without rendering a DropdownMenu.

Refs: nubecita-zdv8.12"
```

---

### Task 2: Bookmark cell, overflow cell + menu, screen wiring

**Files:**
- Modify: `.../impl/ui/VideoOverflowMenu.kt` (add the composable)
- Modify: `.../impl/ui/VideoPageChrome.kt`
- Modify: `.../impl/VideoFeedScreen.kt`
- Modify: `res/values{,-b+es+419,-pt-rBR}/strings.xml`
- Modify: `.../screenshotTest/.../VideoFeedPageScreenshotTest.kt`

**Interfaces:**
- Consumes: `videoOverflowActions` (Task 1); `VideoRailAction` (existing); the existing `callbacks: PostCallbacks`.
- Produces: `internal fun VideoOverflowMenu(post, expanded, onDismiss, onAction, modifier)`; `VideoPageChrome` gains `onBookmark: () -> Unit`, `onOverflowAction: (PostOverflowAction) -> Unit`.

- [ ] **Step 1: Add the strings (all three locales)**

`values`:
```xml
    <string name="videos_action_bookmark">Bookmark</string>
    <string name="videos_action_more">More options</string>
```
`values-b+es+419`: `Guardar` / `Más opciones`
`values-pt-rBR`: `Salvar` / `Mais opções`

- [ ] **Step 2: Add the menu composable**

Append to `VideoOverflowMenu.kt` (imports: `androidx.compose.material3.DropdownMenu`, `DropdownMenuItem`, `Text`, `androidx.compose.runtime.Composable`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.res.stringResource`, `net.kikin.nubecita.data.models.PostUi`, `net.kikin.nubecita.designsystem.R` as `designsystemR`):

```kotlin
@Composable
internal fun VideoOverflowMenu(
    post: PostUi,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (PostOverflowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        videoOverflowActions(post.viewer).forEach { action ->
            DropdownMenuItem(
                text = { Text(overflowActionLabel(action, post.author.handle)) },
                onClick = {
                    onDismiss()
                    onAction(action)
                },
            )
        }
    }
}

@Composable
private fun overflowActionLabel(action: PostOverflowAction, handle: String): String =
    when (action) {
        PostOverflowAction.ReportPost -> stringResource(designsystemR.string.moderation_action_report_post)
        PostOverflowAction.MuteAuthor -> stringResource(designsystemR.string.moderation_action_mute_author, handle)
        PostOverflowAction.UnmuteAuthor -> stringResource(designsystemR.string.moderation_action_unmute_author, handle)
        PostOverflowAction.BlockAuthor -> stringResource(designsystemR.string.moderation_action_block_author, handle)
        PostOverflowAction.UnblockAuthor -> stringResource(designsystemR.string.moderation_action_unblock_author, handle)
        PostOverflowAction.MuteThread -> stringResource(designsystemR.string.moderation_action_mute_thread)
        PostOverflowAction.UnmuteThread -> stringResource(designsystemR.string.moderation_action_mute_thread) // never rendered
        PostOverflowAction.CopyPostText -> stringResource(designsystemR.string.moderation_action_copy_post_text)
    }
```

Import alias line: `import net.kikin.nubecita.designsystem.R as designsystemR`. If ktlint rejects the alias, use the fully-qualified `net.kikin.nubecita.designsystem.R.string.…` at each call site instead.

- [ ] **Step 3: Add the cells to `VideoPageChrome`**

Add params after `onShare`:

```kotlin
    onBookmark: () -> Unit,
    onOverflowAction: (net.kikin.nubecita.designsystem.component.PostOverflowAction) -> Unit,
```

Add `import net.kikin.nubecita.designsystem.component.PostOverflowAction`, `androidx.compose.runtime.getValue/setValue/mutableStateOf/remember`, and `androidx.compose.foundation.layout.Box`.

In the rail `Column`, insert the **bookmark** cell after the repost cell:

```kotlin
            VideoRailAction(
                icon = NubecitaIconName.Bookmark,
                accessibilityLabel = stringResource(R.string.videos_action_bookmark),
                onClick = onBookmark,
                active = post.viewer.isBookmarked,
                toggleable = true,
                activeColor = MaterialTheme.colorScheme.primary,
                testTag = VideoFeedTestTags.RAIL_BOOKMARK,
            )
```

and the **overflow** cell after the share cell. It owns the menu's expanded state and anchors the menu:

```kotlin
            Box {
                var overflowExpanded by remember { mutableStateOf(false) }
                VideoRailAction(
                    icon = NubecitaIconName.MoreVert,
                    accessibilityLabel = stringResource(R.string.videos_action_more),
                    onClick = { overflowExpanded = true },
                    testTag = VideoFeedTestTags.RAIL_OVERFLOW,
                )
                VideoOverflowMenu(
                    post = post,
                    expanded = overflowExpanded,
                    onDismiss = { overflowExpanded = false },
                    onAction = onOverflowAction,
                )
            }
```

(Bookmark uses `activeColor = primary`, matching PostCard's bookmark cell; like uses `secondary`, repost `tertiary`.)

- [ ] **Step 4: Wire the screen**

In `VideoFeedScreen.kt`, in the `VideoPageChrome(...)` call inside the pager lambda, add:

```kotlin
                                onBookmark = { callbacks.onBookmark(item.post) },
                                onOverflowAction = { action -> callbacks.onOverflowAction(item.post, action) },
```

Both `callbacks.onBookmark` and `callbacks.onOverflowAction` already exist on the `PostCallbacks` returned by `rememberVideoFeedInteractions`.

- [ ] **Step 5: Update the strings comment**

In `feature/videos/impl/src/main/res/values/strings.xml`, the overflow coming-soon strings carry a comment saying "unreachable: this surface ships no overflow menu (tracked as nubecita-zdv8.12)". They are now reachable — update the comment to drop the "unreachable" claim.

- [ ] **Step 6: Update the chrome screenshot previews**

The existing `PreviewChrome` helper in `VideoFeedPageScreenshotTest.kt` calls `VideoPageChrome(...)`; add the two new required lambdas (`onBookmark = {}`, `onOverflowAction = {}`) so it compiles. Add one preview with `chromePost(...)` where the fixture's `viewer` has `isBookmarked = true`, so the filled bookmark state is pinned. (The open menu can't be screenshotted — device covers it.)

Regenerate and inspect:

```bash
./gradlew :feature:videos:impl:updateScreenshots
```

**Look at the regenerated PNGs.** The `chrome-default` baseline now has 7 rail cells (like, repost, bookmark, reply, share, overflow, mute). Confirm the rail isn't clipped at the top or bottom of the 600dp canvas; if it is, that is a real finding (the rail is too tall) — report it rather than accepting a clipped baseline. Then restrict the commit to baselines your change actually altered, and restore any that only drifted:

```bash
git status --porcelain | grep '\.png'
# for any baseline that changed but whose composable you did NOT touch, restore it:
# git checkout origin/main -- <path>
```

- [ ] **Step 7: Verify**

```bash
./gradlew :feature:videos:impl:assembleDebug :feature:videos:impl:testDebugUnitTest :feature:videos:impl:lintDebug > /tmp/gate.log 2>&1; echo "EXIT=$?"; tail -3 /tmp/gate.log
```

Expected `EXIT=0`, no `MissingTranslation`. Check the exit code explicitly.

- [ ] **Step 8: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply -q
git add feature/videos/impl
git commit -m "feat(videos): bookmark cell and overflow menu on the vertical feed rail

Bookmark is a Role.Switch toggle reading viewer.isBookmarked, reflected
through the VM's existing cache merge. Overflow opens a videos-local
DropdownMenu (report / mute / block / mute-thread / copy) routed through
the already-bound callbacks; mute delegates to the handler's real
muteActor rather than optimistically removing the video.

Refs: nubecita-zdv8.12"
```

---

### Task 3: Device verification on the Pixel Fold

Unit tests cover item selection; screenshots cover the closed rail. The open menu and the bookmark round-trip are device-only.

**Files:** none.

- [ ] **Step 1: Check devices, install, DND**

```bash
adb devices -l
./gradlew :app:installBenchDebug
adb -s 37201FDHS002UN shell cmd notification set_dnd priority
```

`installBenchDebug` targets every attached device; use `-s 37201FDHS002UN` for every adb call. The Fold is a foldable — pass `-d <displayId>` (the ON display; folded ⇒ outer `4619827677550801153`, 1080x2092) to `screencap`, and confirm it's `state ON`.

- [ ] **Step 2: Open the feed and verify the rail**

Discover → Trending Videos → open a clip. Resolve tap targets from `uiautomator dump`, not eyeballed coordinates. Confirm the app is foreground before each capture. Capture the feed; confirm 7 rail cells render without clipping and the bookmark icon is present.

- [ ] **Step 3: Verify bookmark round-trips (the load-bearing check)**

Resolve the bookmark cell bounds via `uiautomator dump | grep video_feed_bookmark`. Tap it. Capture, crop the bookmark cell.

Pass = the bookmark icon **fills**. Then swipe away and back and confirm it is **still filled** — that proves the cache merge propagated it, exactly as the like check did in PR2. A tap that fills but reverts on return means the merge dropped bookmark.

- [ ] **Step 4: Verify the overflow menu opens and routes**

Tap the overflow (⋮) cell. Capture. Pass = a menu appears with Report / Mute @handle / Block @handle / Mute thread / Copy post text. Tap **Report** → confirm it navigates to the report screen (or shows the report flow), no crash. Reopen, tap **Copy post text** → confirm the copy snackbar / no crash. Reopen, tap **Mute @handle** → confirm no crash (the video may remain — that is the documented delegate-don't-remove behavior; confirm it doesn't crash or double-apply).

```bash
adb -s 37201FDHS002UN shell cmd notification set_dnd off
```

- [ ] **Step 5: Report each check with its evidence.** A failure is a finding to surface, not smooth over. Item 3 reverting = broken cache merge; the menu not opening = the `DropdownMenu` anchor/state is wrong.

---

## PR completion

Open the PR with `Closes: nubecita-zdv8.12` in the body. State the **known limitation**: muting an author applies the real mute but does not remove the video from the current session (optimistic removal deferred). This diff adds `@Composable` lines → run the **compose-expert skill** (invoke it). Reply to and resolve every review thread. `bd close nubecita-zdv8.12` after merge.
