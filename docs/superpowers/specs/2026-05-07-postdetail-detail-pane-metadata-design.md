# PostDetail entry — `ListDetailSceneStrategy.detailPane()` metadata

**bd issue:** nubecita-t30
**Date:** 2026-05-07
**Status:** Approved

## Problem

On Medium/Expanded widths (≥600dp — tablet, foldable open, large
landscape phone), `MainShell` already runs an inner `NavDisplay` with
a `ListDetailSceneStrategy`. `Feed` is registered with
`ListDetailSceneStrategy.listPane(detailPlaceholder = …)` metadata, so
the strategy renders Feed in the left pane and the
`FeedDetailPlaceholder` in the right pane until something is pushed
onto the back stack.

Tapping a feed item fires `onNavigateToPost`, which calls
`LocalMainShellNavState.current.add(PostDetailRoute(...))`. The
`PostDetailRoute` entry, however, is registered in
`PostDetailNavigationModule.kt` *without* scene-strategy metadata.
The strategy treats a metadata-less route as opaque and renders it
full-screen, replacing the entire two-pane layout: the placeholder
disappears, the Feed list disappears, and the user sees only the
post detail occupying the full inner content area.

The same wrong-pane-rendering happens for any other navigation path
that pushes `PostDetailRoute` (Search results, profile post lists,
chat post-embed taps).

Compact width (<600dp) is unaffected — the strategy collapses to
single-pane and a metadata-less entry rendering full-screen *is* the
correct behavior there.

## Fix

Tag the `PostDetailRoute` entry registration with
`ListDetailSceneStrategy.detailPane()` metadata. The strategy reads
that tag at composition time and slots the entry into the right pane
on Medium/Expanded; on Compact it continues to render full-screen,
which `detailPane()` semantics correctly preserve.

The change is one metadata literal plus one import in one production
file. No `MainShell.kt` changes, no `:core:common` API additions, no
new modules, no Hilt graph changes, no MVI changes.

## Production change

**File:** `feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/di/PostDetailNavigationModule.kt`

Add the import:

```kotlin
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
```

Add the `metadata` parameter to the existing `entry<PostDetailRoute>`
call:

```kotlin
entry<PostDetailRoute>(
    metadata = ListDetailSceneStrategy.detailPane(),
) { route ->
    // entry block body unchanged — VM lookup, screen invocation,
    // LocalMainShellNavState wiring all stay as they are.
}
```

That is the entire production-side delta.

## Cross-feature behavior (intended)

Once `PostDetailRoute` carries `detailPane()` metadata, every
navigation path that pushes `PostDetailRoute` renders in the right
pane on Medium/Expanded:

- Tapping a feed item.
- Tapping a reply inside an already-open PostDetail
  (`PostDetailScreen.onNavigateToPost` → `navState.add(PostDetailRoute(...))`).
  The detail stack grows on the detail pane; Feed remains in the
  list pane throughout. Matches the X / Bluesky web pattern.
- Future Search-result taps, profile post-list taps, chat post-embed
  taps. All inherit `detailPane()` placement automatically — no
  per-call-site work required.

This is the desired behavior, not a regression. The spec records it
explicitly so a reviewer reading "one route, one metadata literal"
isn't surprised by the cross-feature impact.

## Back behavior

`ListDetailSceneStrategy` + Nav3 handle system-back for
`detailPane()` entries without explicit handlers:

- On Medium/Expanded: system-back pops the detail stack until the
  placeholder is reached again, then pops the tab.
- On Compact: system-back pops the back stack as it does today
  (single-pane behavior unchanged).

The new persistence test's rotation round-trip is the closest
automated guard for detail-stack survival. Full back-press behavior
stays under manual smoke-test criteria in the bd issue.

## Test plan

The change has no business logic, no MVI state machine, no reducer.
Test surface is therefore:

- **Two new screenshot baselines** — lock in the fix's pixel output
  on Medium/Expanded.
- **One new instrumented persistence test** — locks in detail-pane
  content survives a configuration-change round-trip in both
  directions.
- **No unit tests** — there is no logic-bearing surface to exercise
  in isolation. CLAUDE.md's "UI tasks need unit tests + previews +
  screenshot tests" convention is satisfied by the previews +
  screenshot tests; the unit-tests bucket is N/A here, deliberately,
  not by oversight.

### Screenshot test extension

**File:** `app/src/screenshotTest/java/net/kikin/nubecita/shell/MainShellListDetailScreenshotTest.kt`

Approach: keep all three existing previews (`list-detail-compact`,
`list-detail-medium`, `list-detail-expanded`) as the placeholder-path
regression guard. Add new previews for the detail-on-stack contract.

**New `@PreviewTest` composables:**

- `MainShellListDetailMediumWithDetail` — `widthDp = 600`,
  `NavigationSuiteType.NavigationRail` layout, name
  `"list-detail-medium-with-detail"`.
- `MainShellListDetailExpandedWithDetail` — `widthDp = 840`,
  `NavigationSuiteType.NavigationRail` layout, name
  `"list-detail-expanded-with-detail"`.

Both invoke a new private `FakeListDetailNavDisplayWithDetail()`
helper.

**`FakeListDetailNavDisplayWithDetail()` composable:**

- Imports the real `PostDetailRoute` from
  `net.kikin.nubecita.feature.postdetail.api`. `:app` already
  declares `implementation(project(":feature:postdetail:api"))` in
  `app/build.gradle.kts`, so the route type is reachable from
  `:app`'s `screenshotTest` source set without any new dependency.
  Mirrors the existing test's pattern of using the real `Feed`
  NavKey + fake `FeedScreen` body — production NavKey types,
  test-local fake content.
- A fake URI string is fine for the route argument (the test
  doesn't compose the real screen):
  `PostDetailRoute(postUri = "at://did:plc:fake/app.bsky.feed.post/abc123")`.
- Back stack: `mutableStateListOf<NavKey>(Feed, PostDetailRoute(postUri = ...))`.
- Scene strategy: `rememberListDetailSceneStrategy<NavKey>(directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfoV2()))`
  — same directive as the existing helper.
- `entryProvider`:
  - `Feed` entry with the same `listPane{ FakeDetailPlaceholder() }`
    metadata + `FakeFeedListContent` body the existing helper uses.
    The placeholder lambda will not be reached at composition (the
    detail entry is on top), but the `listPane{}` metadata still has
    to be present for the strategy to identify Feed as the list-pane
    host.
  - `PostDetailRoute` entry with
    `metadata = ListDetailSceneStrategy.detailPane()` and a new
    `FakePostDetailContent()` body.

**New `FakePostDetailContent` composable:** visually distinct from
`FakeDetailPlaceholder` so the new baselines obviously show *post
detail content* rather than *placeholder*. Shape:

```kotlin
@Composable
private fun FakePostDetailContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Post detail (fake)",
            style = MaterialTheme.typography.titleMedium,
        )
        repeat(3) { index ->
            Text(
                text = "Reply #${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

Plays the same role as `FakeFeedListContent` for the left pane: a
recognizable, content-agnostic stand-in whose visual correctness
isn't what the test is asserting.

**Baseline PNGs:** generated by the screenshot runner. The PR
includes two new files under
`app/src/screenshotTestDebug/reference/net/kikin/nubecita/shell/MainShellListDetailScreenshotTestKt/`:

- `MainShellListDetailMediumWithDetail_list-detail-medium-with-detail_*.png`
- `MainShellListDetailExpandedWithDetail_list-detail-expanded-with-detail_*.png`

The three existing baselines are not regenerated and stay byte-for-byte
identical.

### Persistence test extension

**File:** `app/src/androidTest/java/net/kikin/nubecita/shell/MainShellPersistenceTest.kt`

**Harness refactor.** The existing `ListDetailHarness(windowAdaptiveInfo)`
hard-codes a `[Feed]` back stack and a single entry installer.
Generalize lightly so both tests share machinery:

```kotlin
@Composable
private fun ListDetailHarness(
    windowAdaptiveInfo: WindowAdaptiveInfo,
    backStack: List<NavKey> = listOf(Feed),
    extraInstallers: List<EntryProviderInstaller> = emptyList(),
) {
    val backStackState = remember { mutableStateListOf<NavKey>().apply { addAll(backStack) } }
    // ... existing strategy wiring ...
    NavDisplay(
        backStack = backStackState,
        // ...
        entryProvider = entryProvider {
            fakeFeedInstaller()
            extraInstallers.forEach { it() }
        },
    )
}
```

The existing call site
(`tester.setContent { ListDetailHarness(adaptiveInfo) }`) keeps
defaults and behavior unchanged.

**New `@Test` method:** `listDetailDetailPane_survivesMediumToCompactToMediumRotation`.

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Test
fun listDetailDetailPane_survivesMediumToCompactToMediumRotation() {
    val tester = StateRestorationTester(composeTestRule)
    var adaptiveInfo by mutableStateOf(adaptiveInfoForWidth(MEDIUM_WIDTH_DP))

    val detailRoute = PostDetailRoute(postUri = "at://did:plc:fake/app.bsky.feed.post/abc123")
    val fakeDetailInstaller: EntryProviderInstaller = {
        entry<PostDetailRoute>(
            metadata = ListDetailSceneStrategy.detailPane(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().testTag(DETAIL_TAG),
            ) {
                Text(text = "fake-detail-content")
            }
        }
    }

    tester.setContent {
        ListDetailHarness(
            windowAdaptiveInfo = adaptiveInfo,
            backStack = listOf(Feed, detailRoute),
            extraInstallers = listOf(fakeDetailInstaller),
        )
    }

    // Medium: detail content visible in right pane.
    composeTestRule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()

    // Rotate to Compact + restore: strategy collapses to single-pane,
    // top-of-stack (the detail entry) renders full-screen.
    adaptiveInfo = adaptiveInfoForWidth(COMPACT_WIDTH_DP)
    tester.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()

    // Rotate back to Medium + restore: strategy expands back to
    // two-pane, detail content slots into the right pane again.
    adaptiveInfo = adaptiveInfoForWidth(MEDIUM_WIDTH_DP)
    tester.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
}
```

The Compact assertion is the load-bearing one: it verifies the
back stack `[Feed, FakePostDetail]` survives the saved-state
round-trip and the strategy correctly renders top-of-stack rather
than dropping the detail entry. Matches the bd issue's "Compact
width unaffected" non-regression requirement.

**New file-local declarations:**

- `private const val DETAIL_TAG = "list-detail-content"`

Goes alongside `PLACEHOLDER_TAG` and the other private constants at
the bottom of the file. The detail route uses the real
`PostDetailRoute` from `:feature:postdetail:api` (already a
transitive dep of `:app`'s androidTest source set via the main
`implementation` line).

**PR label:** `run-instrumented` must be added (the PR touches
`app/src/androidTest/**`). Without it CI's instrumented job is
skipped and the new test's coverage isn't actually exercised.

## Files changed

| File | Change |
|---|---|
| `feature/postdetail/impl/build.gradle.kts` | + 1 dependency line (`androidx.compose.material3.adaptive.navigation3`) — required for `ListDetailSceneStrategy.detailPane()`, which lives in the adaptive-navigation3 artifact (the base `material3.adaptive` artifact already wired through `:designsystem` doesn't transitively expose it). |
| `feature/postdetail/impl/.../di/PostDetailNavigationModule.kt` | + 2 imports, + 1 `@OptIn`, + 1 metadata parameter |
| `app/src/screenshotTest/java/.../MainShellListDetailScreenshotTest.kt` | + 2 `@PreviewTest` composables, + 1 helper (`FakeListDetailNavDisplayWithDetail`), + 1 stand-in (`FakePostDetailContent`) |
| `app/src/screenshotTestDebug/reference/.../MainShellListDetailScreenshotTestKt/` | + 2 baseline PNGs (generated) |
| `app/src/androidTest/java/.../MainShellPersistenceTest.kt` | refactor `ListDetailHarness` (default-compatible parameters, `rememberNavBackStack`-backed stack so rotation tests round-trip the saver), + 1 `@Test`, + 1 testTag constant |

No new modules.

## Acceptance criteria

Mirror the bd issue's bullets. The PR is mergeable when:

- [ ] Pixel Tablet landscape (Expanded ≥840dp): tapping a feed item
      renders the post in the right pane; Feed list stays visible
      in the left pane.
- [ ] Tapping a reply inside the post-detail right pane pushes
      another PostDetail onto the detail-pane stack — Feed list
      unchanged.
- [ ] System-back on Medium/Expanded with detail open: detail pane
      returns to `FeedDetailPlaceholder`.
- [ ] Pixel 10 Pro XL portrait (Compact <600dp): tapping a feed
      item still renders full-screen post detail (no regression).
- [ ] `MainShellListDetailScreenshotTest` includes the two new
      `*WithDetail` baselines and they pass after running
      `./gradlew :app:validateScreenshotTest`.
- [ ] `MainShellPersistenceTest.listDetailDetailPane_survivesMediumToCompactToMediumRotation`
      passes when CI's instrumented job runs (gated on
      `run-instrumented` label).
- [ ] Existing `MainShellPersistenceTest.listDetailPlaceholder_survivesMediumToCompactToMediumRotation`
      still passes — the harness refactor is default-compatible.

## Non-goals

- No changes to other navigation paths that reach PostDetail
  (Search, profile post-list, chat post-embed) — they pick up
  `detailPane()` placement automatically and need no per-site work.
- No `:core:common` API surface for "scene strategy metadata
  conventions" — one consumer (PostDetail) doesn't justify an
  abstraction. If a third feature later needs `detailPane()`
  metadata, revisit.
- No back-press handler additions — Nav3 + the strategy handle
  detail-stack pop semantics correctly out of the box.
- No new module dependencies. Both test source sets reach
  `PostDetailRoute` transitively through `:app`'s existing
  `implementation(project(":feature:postdetail:api"))`.
