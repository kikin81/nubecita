# SearchScreen orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mount the per-tab Screens shipped in vrba.6/.7 into the parent `SearchScreen` via a `SecondaryTabRow` + `HorizontalPager`, hoist a `SnackbarHostState` for per-tab append errors, wire the empty-state "Clear search" CTA back to the parent `TextFieldState`, and gate the tab strip vs. the recent-search chip strip on `state.isQueryBlank`. Implements decisions O1–O12 of `docs/superpowers/specs/2026-05-16-search-screen-orchestration-design.md`. Single bd child (`nubecita-vrba.8`), single PR.

**Architecture:** No new VM, no new contract types, no new module. The existing `SearchScreen.kt` (stateful) keeps its `hiltViewModel<SearchViewModel>()` hoist; the existing `SearchScreenContent.kt` (stateless body) is restructured to wrap its contents in a `Scaffold(snackbarHost = ...)` and to render either the `RecentSearchChipStrip` (when `isQueryBlank == true`) or a `SecondaryTabRow` + `HorizontalPager` hosting `SearchPostsScreen` (page 0) and `SearchActorsScreen` (page 1) (when `isQueryBlank == false`). Per-tab `onShowAppendError` callbacks resolve to a `snackbarHostState.showSnackbar(...)` call via `rememberCoroutineScope`. Per-tab `onClearQuery` callbacks call `viewModel.textFieldState.clearText()` directly (the editor-VM exception makes the field public). The `beyondViewportPageCount = 1` setting keeps both tabs alive so switching preserves results.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 Expressive (`SecondaryTabRow`, `Scaffold`, `SnackbarHost`), `androidx.compose.foundation.pager.HorizontalPager`, `androidx.compose.foundation.text.input.clearText`, JUnit 5 + Compose `@PreviewTest` screenshot tests.

---

## File map

### Modified

| File | Change |
|---|---|
| `feature/search/impl/src/main/kotlin/.../SearchScreen.kt` | Refactor `SearchScreenContent` body: Scaffold wrap, `if (isQueryBlank) RecentSearchChipStrip else TabRow + Pager` conditional. Add `viewModel` reference to the stateful entry's downstream call so `SearchScreenContent` can call `textFieldState.clearText()` (or — preferred — accept an `onClearQueryRequest: () -> Unit` lambda from the stateful entry, see Task 2 Step 4). |
| `feature/search/impl/src/main/res/values/strings.xml` | Add `search_tab_posts` + `search_tab_people`. |
| `feature/search/impl/src/screenshotTest/kotlin/.../SearchScreenScreenshotTest.kt` | Extend with new variants per Task 4. May need existing baselines regenerated due to Scaffold padding. |

### New

None. All work lands as modifications to two existing files (plus screenshot baseline PNGs).

---

## Pre-flight notes (read before coding)

- The branch is created via `bd-worktree` at `../nubecita-vrba.8` (controller handles this).
- All Kotlin code stays in package `net.kikin.nubecita.feature.search.impl` or `.ui`. Do NOT introduce new packages.
- **No gradle dependency changes.** Material 3 (`SecondaryTabRow`, `Scaffold`, `SnackbarHost`) and `androidx.compose.foundation.pager.HorizontalPager` are already transitively available via the feature convention plugin. `:designsystem` is already on the classpath.
- The per-tab `SearchPostsScreen` (vrba.6) and `SearchActorsScreen` (vrba.7) are `internal` composables in the same module — call them directly from `SearchScreenContent`.
- Per-tab Screens hoist their own `hiltViewModel<SearchPostsViewModel>()` / `hiltViewModel<SearchActorsViewModel>()`. Both consume the same `LocalViewModelStoreOwner` from the `NavBackStackEntry` — Hilt distinguishes by `@HiltViewModel` class. Should "just work"; verify in Task 5's assembleDebug.
- Commit subjects lowercase. Conventional Commits. `Refs: nubecita-vrba.8` in commit bodies; `Closes: nubecita-vrba.8` in PR body only.
- Pre-commit hooks gate everything. NEVER `--no-verify`. If spotless reformats during commit, re-stage and retry — that's normal.

## Lessons baked in from vrba.6 / .7

- Spotless may reformat the test/screen file during the pre-commit hook; re-stage and re-commit (the hook stashes and restores cleanly).
- Compose ktlint requires present-tense callback names: `onClearQueryRequest` (verb-noun), not `onQueryClearRequested` (past-tense). The plan uses `onClearQueryRequest` consistently.
- HTTPS-insteadof fallback for `git push` when SSH signing fails: `git -c "url.https://github.com/.insteadOf=git@github.com:" push -u origin HEAD`. Controller's job — implementer doesn't push.

---

## Task 0: Worktree confirm

**Files:** none.

- [ ] **Step 1: Confirm working directory**

```bash
# cd into the worktree the controller provisioned at ../nubecita-vrba.8
# (sibling of the main checkout, per the bd-worktree skill convention).
git rev-parse --abbrev-ref HEAD   # expect feat/nubecita-vrba.8-searchscreen-orchestration
git log --oneline -3
ls feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ | head -20
```

Expected: branch checked out, recent commits include `1d4a490b docs(specs): add SearchScreen orchestration design (vrba.8)` (or whatever the spec commit SHA is on this branch's base), and the file listing shows `SearchPostsScreen.kt`, `SearchActorsScreen.kt`, `SearchScreen.kt`, etc.

No commit in this task — just orientation.

---

## Task 1: Add tab labels to strings.xml

**Files:**
- Modify: `feature/search/impl/src/main/res/values/strings.xml`

- [ ] **Step 1: Read the current strings.xml head to find a sensible insertion point**

```bash
grep -n "search_tab\|search_people_loading\|search_posts_loading\|<!-- vrba" feature/search/impl/src/main/res/values/strings.xml | head
```

Insert new strings adjacent to the existing `search_tab_*`-style strings if any, else create a new `<!-- vrba.8: tabs -->` section.

- [ ] **Step 2: Add the entries**

```xml
    <!-- vrba.8: SearchScreen tab labels -->
    <string name="search_tab_posts">Posts</string>
    <string name="search_tab_people">People</string>
```

- [ ] **Step 3: Verify lint**

```bash
./gradlew :feature:search:impl:lintDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/src/main/res/values/strings.xml
git commit -m "feat(feature/search/impl): add search tab labels

Posts / People string entries consumed by the SecondaryTabRow
landing in the next commit.

Refs: nubecita-vrba.8"
```

---

## Task 2: Refactor `SearchScreen` — Scaffold + TabRow + Pager

**Files:**
- Modify: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreen.kt`

This is the main task. Restructure both `SearchScreen` (stateful) and `SearchScreenContent` (stateless) per the spec.

- [ ] **Step 1: Read the current file**

```bash
cat feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreen.kt
```

Current shape (from the spec's "Inherited" table): stateful entry hoists `hiltViewModel<SearchViewModel>()` + reads state + calls `SearchScreenContent(textFieldState, isQueryBlank, recentSearches, onEvent, modifier)`. Stateless body is a `Column` with `SearchInputRow` + conditional `RecentSearchChipStrip`. Line 86 has the placeholder comment to delete.

- [ ] **Step 2: Verify the new Material 3 APIs you'll use are available**

```bash
# SecondaryTabRow exists in M3 1.2+; verify by grepping for any existing usage:
grep -rn "SecondaryTabRow\|HorizontalPager\|rememberPagerState" feature/ designsystem/ 2>/dev/null | head -10

# clearText() on TextFieldState exists in compose foundation 1.7+; verify:
grep -rn "\.clearText()" feature/ 2>/dev/null | head -5
```

If `SecondaryTabRow` is absent from M3, fall back to `TabRow` (Primary) — visually heavier but functionally identical. Document inline.

If `clearText()` isn't directly available, use `viewModel.textFieldState.edit { replace(0, length, "") }`.

If neither `HorizontalPager` nor `rememberPagerState` are accessible, fall back to a static `when (selectedTab) { ... }` swap with `selectedTab: Int` managed via `rememberSaveable { mutableIntStateOf(0) }`. No swipe-between-tabs gesture, but otherwise identical functionality. Report `DONE_WITH_CONCERNS` and continue.

- [ ] **Step 3: Rewrite the stateful entry**

Replace the existing `SearchScreen` function with:

```kotlin
@Composable
internal fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Stable bound-method reference for the stateless body.
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    // Direct mutation of the parent VM's TextFieldState. The editor-VM
    // exception (CLAUDE.md) makes this field public; this is the canonical
    // way for any descendant Composable to clear the input.
    val onClearQueryRequest = remember(viewModel) {
        { viewModel.textFieldState.clearText() }
    }
    SearchScreenContent(
        textFieldState = viewModel.textFieldState,
        isQueryBlank = state.isQueryBlank,
        currentQuery = state.currentQuery,
        recentSearches = state.recentSearches,
        onEvent = onEvent,
        onClearQueryRequest = onClearQueryRequest,
        modifier = modifier,
    )
}
```

Notes:
- New param: `currentQuery: String` — needed by the per-tab Screens.
- New param: `onClearQueryRequest: () -> Unit` — used by the per-tab Screens' `onClearQuery` callback.

- [ ] **Step 4: Rewrite `SearchScreenContent`**

Replace the existing stateless body. The new shape uses a `Scaffold` to host the Snackbar:

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SearchScreenContent(
    textFieldState: TextFieldState,
    isQueryBlank: Boolean,
    currentQuery: String,
    recentSearches: ImmutableList<String>,
    onEvent: (SearchEvent) -> Unit,
    onClearQueryRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSubmit = remember(onEvent) { { onEvent(SearchEvent.SubmitClicked) } }
    val onChipTap = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipTapped(query)) } }
    val onChipRemove = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipRemoved(query)) } }
    val onClearAll = remember(onEvent) { { onEvent(SearchEvent.ClearAllRecentsClicked) } }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val context = LocalContext.current

    val pagerState = rememberPagerState(pageCount = { 2 })
    val tabScope = rememberCoroutineScope()

    val onPostsAppendError = remember(snackScope, snackbarHostState, context) {
        { error: SearchPostsError ->
            snackScope.launch {
                snackbarHostState.showSnackbar(context.getString(error.appendErrorStringRes()))
            }
            Unit
        }
    }
    val onActorsAppendError = remember(snackScope, snackbarHostState, context) {
        { error: SearchActorsError ->
            snackScope.launch {
                snackbarHostState.showSnackbar(context.getString(error.appendErrorStringRes()))
            }
            Unit
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            SearchInputRow(
                textFieldState = textFieldState,
                isQueryBlank = isQueryBlank,
                onSubmit = onSubmit,
            )
            if (isQueryBlank) {
                if (recentSearches.isNotEmpty()) {
                    RecentSearchChipStrip(
                        items = recentSearches,
                        onChipTap = onChipTap,
                        onChipRemove = onChipRemove,
                        onClearAll = onClearAll,
                    )
                }
            } else {
                SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { tabScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(stringResource(R.string.search_tab_posts)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { tabScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(stringResource(R.string.search_tab_people)) },
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (page) {
                        0 ->
                            SearchPostsScreen(
                                currentQuery = currentQuery,
                                onClearQuery = onClearQueryRequest,
                                onShowAppendError = onPostsAppendError,
                            )
                        1 ->
                            SearchActorsScreen(
                                currentQuery = currentQuery,
                                onClearQuery = onClearQueryRequest,
                                onShowAppendError = onActorsAppendError,
                            )
                    }
                }
            }
        }
    }
}
```

Add the missing imports at the top:

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
```

- [ ] **Step 5: Add the `appendErrorStringRes()` mapper helpers**

In the same file (top-level, after the composables), add two small extension functions that map each typed error to its corresponding string resource id:

```kotlin
@StringRes
private fun SearchPostsError.appendErrorStringRes(): Int =
    when (this) {
        SearchPostsError.Network -> R.string.search_posts_append_error_network
        SearchPostsError.RateLimited -> R.string.search_posts_append_error_rate_limited
        is SearchPostsError.Unknown -> R.string.search_posts_append_error_unknown
    }

@StringRes
private fun SearchActorsError.appendErrorStringRes(): Int =
    when (this) {
        SearchActorsError.Network -> R.string.search_people_append_error_network
        SearchActorsError.RateLimited -> R.string.search_people_append_error_rate_limited
        is SearchActorsError.Unknown -> R.string.search_people_append_error_unknown
    }
```

Add `import androidx.annotation.StringRes`.

- [ ] **Step 6: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

Likely first-attempt issues:
- `rememberPagerState`'s overloads vary across compose-foundation versions. If `rememberPagerState(pageCount = { 2 })` doesn't compile, try `rememberPagerState(initialPage = 0) { 2 }` or `rememberPagerState(initialPage = 0, initialPageOffsetFraction = 0f, pageCount = { 2 })`.
- `clearText()` extension may need a different import.
- `WindowInsets.statusBars` may already be handled by `Scaffold`'s insets — in which case drop the explicit `windowInsetsPadding`. The existing pre-Scaffold code applied it manually because there was no Scaffold; now with Scaffold, `innerPadding` covers it. Choose one — Scaffold's insets, not the explicit modifier — and drop the redundant call.

- [ ] **Step 7: Run the existing unit tests to verify nothing broke**

```bash
./gradlew :feature:search:impl:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. No tests should break — `SearchViewModelTest` doesn't touch the screen Composable, and `SearchPostsViewModelTest` / `SearchActorsViewModelTest` test the VMs in isolation.

- [ ] **Step 8: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreen.kt
git commit -m "feat(feature/search/impl): mount posts/people tabs in searchscreen

Replace the placeholder Idle below-input area with a SecondaryTabRow +
HorizontalPager hosting SearchPostsScreen (page 0) and
SearchActorsScreen (page 1), gated on !state.isQueryBlank.
beyondViewportPageCount = 1 keeps both VMs alive across tab switches.
Per-tab onShowAppendError callbacks route to a Scaffold-hosted
SnackbarHostState; onClearQuery callbacks call
viewModel.textFieldState.clearText() directly (editor-VM exception).
Two small SearchPostsError / SearchActorsError → R.string mappers
co-located in the screen file.

Refs: nubecita-vrba.8"
```

---

## Task 3: Screenshot tests for the new orientations

**Files:**
- Modify: `feature/search/impl/src/screenshotTest/kotlin/.../SearchScreenScreenshotTest.kt`

The existing screenshot test (from vrba.5) captures the `blank query + recents` shape — that's still valid post-Scaffold (might re-baseline due to padding shift). Add four new baselines for the variants the spec lists.

- [ ] **Step 1: Read the existing screenshot test**

```bash
cat feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreenScreenshotTest.kt
```

- [ ] **Step 2: Add the new variants**

Edit the file. Add the following alongside whatever's there. For the tab-content variants, the previewed `SearchScreenContent` mounts real `SearchPostsScreen` / `SearchActorsScreen` — those hoist `hiltViewModel()` which won't work in a screenshot test without a Hilt graph.

**Two options for the tab-content screenshots:**

**A) Skip the integrated tab-content screenshot tests; rely on vrba.6/.7's per-component baselines.** Test only the structural variants `blank + recents`, `blank + no recents`, and `non-blank + (empty Pager body, e.g., Pager visible but tabs uninitialized)`. This is the safer path.

**B) Refactor `SearchScreenContent` to accept the tab bodies as `@Composable` slots.** Lets previews/screenshots pass stub bodies. More work, more flexible.

**Pick A for V1.** The integrated visual coverage comes from the vrba.6/.7 screenshot tests on `PostsTabContent` / `PeopleTabContent` already in the repo. The new SearchScreen-level screenshots focus on the **shell** behavior (Scaffold inset, chip-strip-vs-tab-row toggle).

Append:

```kotlin
@PreviewTest
@Preview(name = "search-screen-blank-no-recents-light", showBackground = true)
@Preview(
    name = "search-screen-blank-no-recents-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchScreenBlankNoRecentsScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SearchScreenContent(
            textFieldState = rememberTextFieldState(),
            isQueryBlank = true,
            currentQuery = "",
            recentSearches = persistentListOf(),
            onEvent = {},
            onClearQueryRequest = {},
        )
    }
}

@PreviewTest
@Preview(name = "search-screen-tabrow-posts-active-light", showBackground = true)
@Preview(
    name = "search-screen-tabrow-posts-active-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchScreenTabRowPostsActiveScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        // Non-blank query triggers the TabRow + Pager branch. The Pager
        // tries to mount SearchPostsScreen which calls hiltViewModel() —
        // screenshot tests don't have a Hilt graph, so this composition
        // would throw at runtime in a normal Compose host. In the
        // screenshot test harness (layoutlib-based), hiltViewModel() may
        // return a degraded instance OR throw silently — verify with
        // `:feature:search:impl:updateDebugScreenshotTest` and react
        // accordingly. If hiltViewModel throws, fall back to either
        // omitting this screenshot OR refactoring SearchScreenContent
        // to a slot-based variant (spec Task 3 Option B).
        SearchScreenContent(
            textFieldState = rememberTextFieldState(initialText = "kotlin"),
            isQueryBlank = false,
            currentQuery = "kotlin",
            recentSearches = persistentListOf(),
            onEvent = {},
            onClearQueryRequest = {},
        )
    }
}
```

If `hiltViewModel()` in the screenshot-test harness fails (likely), DROP the `SearchScreenTabRowPostsActiveScreenshot` test and replace it with a screenshot of just the `SecondaryTabRow` itself (extract the tab-strip composable into a small `SearchTabBar` helper, screenshot-test that in isolation). The visual integration of the tab strip + pager + tab content is genuinely hard to capture without a Hilt graph; defer to manual on-device verification in Task 4.

- [ ] **Step 3: Generate baselines + verify**

```bash
./gradlew :feature:search:impl:updateDebugScreenshotTest
./gradlew :feature:search:impl:validateDebugScreenshotTest
```

Expected: New baselines under `feature/search/impl/src/screenshotTestDebug/reference/`. If validation fails because the existing baseline shifted due to the Scaffold padding, regenerate (`updateDebugScreenshotTest` overwrites).

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/src/screenshotTest/ feature/search/impl/src/screenshotTestDebug/
git commit -m "test(feature/search/impl): add searchscreen orchestration screenshots

New baselines for blank-no-recents shell + (best-effort) the TabRow
active path. Existing chip-strip baseline regenerated if Scaffold
padding shifted pixels.

Refs: nubecita-vrba.8"
```

---

## Task 4: Final verification

- [ ] **Step 1: Full module + designsystem + feed test suites**

```bash
./gradlew :feature:search:impl:testDebugUnitTest \
          :feature:search:impl:validateDebugScreenshotTest \
          :feature:search:impl:spotlessCheck \
          :feature:search:impl:lintDebug \
          :designsystem:testDebugUnitTest \
          :designsystem:validateDebugScreenshotTest \
          :feature:feed:impl:testDebugUnitTest \
          :feature:feed:impl:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: App assemble (Hilt graph check)**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. The Hilt graph should still resolve all three Search VMs — they coexist in the same `NavBackStackEntry`'s `ViewModelStoreOwner` but are keyed by class.

- [ ] **Step 3: Pre-commit hooks against the full diff**

```bash
pre-commit run --all-files
```

Expected: PASS.

- [ ] **Step 4: Reporting**

Report `DONE` to the controller with:
- Tasks completed: 0–4
- Test results: any new/changed counts; all green
- `:app:assembleDebug` result
- `pre-commit` result
- Commits made: `git log --oneline main..HEAD`
- Files touched: `git diff --name-only main...HEAD | grep -v '\.png$'`
- Self-review findings + concerns + deviations (especially: did `hiltViewModel()` work in screenshot tests? Did `SecondaryTabRow` and `HorizontalPager` resolve cleanly? Did the Scaffold inset replace the explicit `windowInsetsPadding` cleanly?)

Do NOT push, do NOT open a PR. Two-stage review happens next.

---

## Self-review checklist (run before reporting DONE)

- **O1** (SecondaryTabRow with two tabs) — code shows `SecondaryTabRow` + `Tab(...)` × 2 with correct stringResource labels. ✓
- **O2** (HorizontalPager with `beyondViewportPageCount = 1`) — confirmed in the `HorizontalPager(...)` call. ✓
- **O3** (Tab state in `rememberPagerState`, not UiState) — `rememberPagerState` is inside `SearchScreenContent`, no new `selectedTab` field on `SearchScreenViewState`. ✓
- **O4** (TabRow + Pager exclusive with chip strip) — the `if (isQueryBlank) { ... } else { TabRow + Pager }` shape captures this. ✓
- **O5** (Scaffold wrapping) — `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })` exists. ✓
- **O6** (Snackbar mapping via `appendErrorStringRes()`) — both helpers present. ✓
- **O7** (`onClearQuery` calls `textFieldState.clearText()`) — `onClearQueryRequest` in the stateful entry, wired through to per-tab Screens. ✓
- **O8** (input-row clear button untouched) — `SearchInputRow` not modified. ✓
- **O9** (no `SearchViewModel` changes) — only `SearchScreen.kt` and `strings.xml` touched. ✓
- **O10** (tab labels via stringResource) — `R.string.search_tab_posts` + `R.string.search_tab_people` referenced. ✓
- **O11** (no NestedScrollConnection) — none added. ✓
- **O12** (Vrba.11 extension path) — leaving the `when (page) { 0 -> ...; 1 -> ... }` shape as documented; adding a 3rd branch + bumping `pageCount = { 3 }` is the mechanical extension.

## Risk + rollback (mirror of spec)

- Risk: `SecondaryTabRow` / `HorizontalPager` / `clearText` / `beyondViewportPageCount` may not be available with the exact names in the project's Compose Material 3 / Compose Foundation version. Implementer verifies each and falls back per Task 2 Step 2.
- Risk: `hiltViewModel()` in screenshot tests may not work. Implementer falls back to a stripped-down screenshot per Task 3 Step 2.
- Risk: Hilt scoping of three VMs in the same `ViewModelStoreOwner`. Should work cleanly; verified by `:app:assembleDebug` + a manual on-device check (not gating but recommended).
- Rollback: Single-file change (`SearchScreen.kt`) + a strings entry + a few screenshot baselines. Reverting restores the pre-vrba.8 Idle placeholder shell. No data-layer or VM rollback needed.

## Out of scope (per spec)

- vrba.9 (tap-through nav) — folded into vrba.6/.7. Close as "implemented as part of vrba.6/.7" after vrba.8 merges + on-device verification.
- vrba.11 (Feeds tab) — separate child.
- vrba.10 (typeahead screen) — separate child.
- Collapsing input row / NestedScrollConnection — follow-up.
- Tab badges / hit counts — would need `hitsTotal` on per-tab Loaded.
- Instrumented tests for swipe + tab state preservation — follow-up.
