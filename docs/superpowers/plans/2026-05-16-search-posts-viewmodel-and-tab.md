# SearchPostsViewModel + Posts tab UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the per-tab `SearchPostsViewModel` + Posts tab UI (`PostsTabContent`, sort row, empty/error/loading bodies, `HighlightedText` body match) that vrba.8 will host inside the search results screen. Implements decisions D1–D10 of `docs/superpowers/specs/2026-05-16-search-posts-viewmodel-and-tab-design.md`. Single bd child (`nubecita-vrba.6`), single PR.

**Architecture:** New `SearchPostsViewModel` (Hilt) consumes the merged `:feature:search:impl/SearchPostsRepository` (extended in this PR with a `sort` param), reacts to a `MutableStateFlow<FetchKey>` via `distinctUntilChanged().mapLatest`, projects results into a sealed `SearchPostsLoadStatus` sum (Idle / InitialLoading / Loaded / Empty / InitialError), and exposes events for `LoadMore`, `Retry`, `SortClicked`, `ClearQueryClicked`, `PostTapped`. The stateful `SearchPostsScreen` Composable hoists the VM and routes `NavigateToPost` to `LocalMainShellNavState.current.add(PostDetailRoute(uri))`. The stateless `PostsTabContent` body branches on the load status; `Loaded` uses the existing `:designsystem/PostCard` extended with an optional `bodyMatch: String?` parameter to highlight query substrings via a new `:designsystem/component/HighlightedText` utility. No new screen-entry point — vrba.8 wires this composable into its TabRow next.

**Tech Stack:** Kotlin, Jetpack Compose, MVI on `ViewModel` + `StateFlow`, Hilt, Material 3, `kotlinx.coroutines.flow.mapLatest`, `kotlinx.collections.immutable`, atproto-kotlin `SearchPostsRequest.sort` field, JUnit 5 + Turbine + plain hand-written fakes for unit tests, Compose `@PreviewTest` screenshot tests.

---

## File map

### New (under `:feature:search:impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/`)

| File | Responsibility |
|---|---|
| `SearchPostsContract.kt` | `SearchPostsState`, `SearchPostsLoadStatus`, `SearchPostsEvent`, `SearchPostsEffect`. Public-package-internal types per project convention. |
| `SearchPostsError.kt` | Sealed `SearchPostsError` + `Throwable.toSearchPostsError()` extension. |
| `SearchPostsViewModel.kt` | `@HiltViewModel` presenter. Owns `MutableStateFlow<FetchKey>`, runs `distinctUntilChanged().mapLatest { runFirstPage(it) }`. |
| `SearchPostsScreen.kt` | Stateful entry. Hoists `hiltViewModel<SearchPostsViewModel>()`, wires `setQuery`/`setSort` via `LaunchedEffect`, collects effects, delegates to `PostsTabContent`. |
| `ui/PostsTabContent.kt` | Stateless body. Branches on `SearchPostsLoadStatus`. For `Loaded`, renders a `LazyColumn` of `PostCard` with `bodyMatch`. Hosts the sort row at the top of `Loaded`/`Empty`. |
| `ui/PostsLoadingBody.kt` | Circular progress + skeleton rows (3× avatar + 2-line shimmer). |
| `ui/PostsEmptyBody.kt` | D10 rich empty state: cloud icon, heading with current query, body copy, two buttons. |
| `ui/PostsInitialErrorBody.kt` | Full-screen retry layout. Maps `SearchPostsError` → `stringResource`. |
| `ui/PostsSortRow.kt` | Top/Latest chip strip. Visible inside `Loaded` and (per D10) above the empty state. |

### Modified

| File | Change |
|---|---|
| `feature/search/impl/src/main/kotlin/.../data/SearchPostsRepository.kt` | Add `enum class SearchPostsSort { TOP, LATEST }` + `sort: SearchPostsSort = SearchPostsSort.TOP` parameter to `searchPosts(...)`. |
| `feature/search/impl/src/main/kotlin/.../data/DefaultSearchPostsRepository.kt` | Pass `sort.name.lowercase()` through to `SearchPostsRequest.sort`. |
| `feature/search/impl/src/test/kotlin/.../data/DefaultSearchPostsRepositoryTest.kt` | Add `searchPosts_passesSortThrough_whenLatest` + `searchPosts_omitsOrDefaultsSortParam_whenTop` (or whatever the wire format reveals — see Task 1 step 1 acceptance). |
| `feature/search/impl/build.gradle.kts` | Add `implementation(project(":feature:postdetail:api"))` for `PostDetailRoute`. |
| `feature/search/impl/src/main/res/values/strings.xml` | Empty-state heading/body/buttons, error-state strings, sort labels, loading content description. |
| `designsystem/src/main/kotlin/.../component/PostCard.kt` | Add optional `bodyMatch: String? = null` to `PostCard`. Thread to `BodyText`. `BodyText` merges match highlight spans over the existing facets annotated-string. Default null = byte-identical to today. |

### New under `:designsystem/`

| File | Responsibility |
|---|---|
| `designsystem/src/main/kotlin/.../component/HighlightedText.kt` | `@Composable fun HighlightedText(...)` for plain-text rendering with query highlighting; co-located helper `AnnotatedString.withMatchHighlight(match: String?, style: SpanStyle): AnnotatedString` reused by `PostCard.BodyText`. |
| `designsystem/src/screenshotTest/kotlin/.../component/HighlightedTextScreenshotTest.kt` | Light + dark baselines for noMatch / singleMatch / multiMatch. |

### New screenshot tests under `:feature:search:impl/src/screenshotTest/kotlin/.../ui/`

| File | What it captures |
|---|---|
| `PostsTabContentScreenshotTest.kt` | InitialLoading, Empty, Loaded (with highlights), LoadedAppending, InitialError(Network), InitialError(RateLimited) × light/dark. |
| `PostsSortRowScreenshotTest.kt` | Top selected and Latest selected × light/dark. |
| `PostsEmptyBodyScreenshotTest.kt` | Rich empty body × light/dark. |
| `PostsInitialErrorBodyScreenshotTest.kt` | Network + RateLimited variants × light/dark. |

### New unit tests under `:feature:search:impl/src/test/kotlin/.../`

| File | Responsibility |
|---|---|
| `SearchPostsErrorTest.kt` | `Throwable.toSearchPostsError()` mapping cases. |
| `SearchPostsViewModelTest.kt` | All 13 cases enumerated in spec "Unit". Uses hand-written `FakeSearchPostsRepository`. |
| `data/FakeSearchPostsRepository.kt` (test-source) | CompletableDeferred-gated fake for cancel/in-flight tests; happy-path mode returns prebuilt pages. |

---

## Pre-flight notes (read before coding)

- The branch should be created via the `bd-worktree` skill at `../nubecita-vrba.6` (separate from this plan-writing session). The plan executor runs `cd ../nubecita-vrba.6` before any task.
- All Kotlin code lives in package `net.kikin.nubecita.feature.search.impl[.ui|.data]` or `net.kikin.nubecita.designsystem.component`. Existing files in `:feature:search:impl` import the long-form package — match them.
- `:designsystem` is already transitively wired into `:feature:search:impl` via the `nubecita.android.feature` convention plugin. Do NOT add `implementation(project(":designsystem"))` to the gradle file — only the new `:feature:postdetail:api` dep is needed.
- Commit subjects all-lowercase (commitlint `subject-case`). Use Conventional Commits. Reference `nubecita-vrba.6` in commit footers via `Refs: nubecita-vrba.6` on intermediate commits; the final PR body carries `Closes: nubecita-vrba.6`.
- Pre-commit hooks (spotless + ktlint + commitlint + detect-secrets + openspec) gate every commit. NEVER use `--no-verify`. If a hook fails, fix the root cause.
- `MockK` is NOT in this module's `testImplementation`. Use hand-written fakes per the project convention (mirrors `DefaultSearchPostsRepositoryTest` + `SearchViewModelTest`). Adding mockk in this PR is out of scope.

---

## Task 0: Setup — branch + worktree + gradle dependency

**Files:**
- Modify: `feature/search/impl/build.gradle.kts:1-25`

- [ ] **Step 1: Create the worktree (if not already in one)**

If this plan is being executed in a fresh session, use the `bd-worktree` skill (`Skill bd-worktree nubecita-vrba.6`) to create `../nubecita-vrba.6` from `main`. The skill handles `bd update nubecita-vrba.6 --claim`, branch naming (`feat/nubecita-vrba.6-searchpostsviewmodel-posts-tab-ui`), and `git worktree add`. If the worktree already exists, skip.

Verify with:

```bash
git worktree list
git rev-parse --abbrev-ref HEAD   # expect feat/nubecita-vrba.6-searchpostsviewmodel-posts-tab-ui
```

- [ ] **Step 2: Add `:feature:postdetail:api` to the search impl gradle file**

The `SearchPostsScreen` will call `LocalMainShellNavState.current.add(PostDetailRoute(uri))` when collecting the `NavigateToPost` effect (per spec D9). `PostDetailRoute` is in `:feature:postdetail:api`. Do NOT add `:feature:postdetail:impl` (the project's cross-feature rule).

Edit `feature/search/impl/build.gradle.kts`. The dependencies block currently reads:

```kotlin
dependencies {
    api(project(":feature:search:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:feed-mapping"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
}
```

Add `implementation(project(":feature:postdetail:api"))` after the existing `:core:feed-mapping` line. Mirror the comment from `feature/chats/impl/build.gradle.kts` so the dep's purpose is documented:

```kotlin
    implementation(project(":core:feed-mapping"))
    // Tap-to-open the post hit pushes a PostDetailRoute onto the MainShell
    // back stack. The api module ships just the NavKey — :feature:search:impl
    // never depends on :impl, matching the Chats / Feed / Profile pattern.
    implementation(project(":feature:postdetail:api"))
```

- [ ] **Step 3: Verify the build still configures**

```bash
./gradlew :feature:search:impl:help -q
```

Expected: completes without error. (We're not compiling yet — just sanity-checking the gradle file edit.)

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/build.gradle.kts
git commit -m "chore(feature/search/impl): add postdetail:api dep for nav effect

Refs: nubecita-vrba.6"
```

---

## Task 1: Extend `SearchPostsRepository` with a `sort` parameter

**Files:**
- Modify: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchPostsRepository.kt`
- Modify: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepository.kt`
- Modify: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepositoryTest.kt`

Atproto's `SearchPostsRequest.sort` is `String? = null`; the Bluesky lexicon accepts `"top"` and `"latest"`. We pass `sort.name.lowercase()` through (`"top"` / `"latest"`).

- [ ] **Step 1: Write the failing test for `sort` passthrough**

Add this `@Test` to `DefaultSearchPostsRepositoryTest.kt` (above the closing brace of the class, before the `// --- helpers ---` divider). Note the import of the new enum.

```kotlin
    @Test
    fun searchPosts_passesSortThrough_whenLatest() =
        runTest {
            val (engine, repo) =
                newRepo { _ -> okJson("""{"posts": []}""") }

            repo.searchPosts(
                query = "kotlin",
                cursor = null,
                limit = 25,
                sort = SearchPostsSort.LATEST,
            )

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            assertTrue(url.contains("sort=latest"))
        }

    @Test
    fun searchPosts_passesSortThrough_whenTop() =
        runTest {
            val (engine, repo) =
                newRepo { _ -> okJson("""{"posts": []}""") }

            repo.searchPosts(
                query = "kotlin",
                cursor = null,
                limit = 25,
                sort = SearchPostsSort.TOP,
            )

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            assertTrue(url.contains("sort=top"))
        }
```

- [ ] **Step 2: Run the failing tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.DefaultSearchPostsRepositoryTest" -i
```

Expected: BUILD FAILED — compilation errors on `SearchPostsSort` (doesn't exist yet) and the unknown `sort` named argument.

- [ ] **Step 3: Add the `SearchPostsSort` enum + extend the interface**

Edit `feature/search/impl/src/main/kotlin/.../data/SearchPostsRepository.kt`. Add the enum above the interface declaration and a new `sort` parameter (default `TOP`) to `searchPosts(...)`:

```kotlin
package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.FeedItemUi

/**
 * `app.bsky.feed.searchPosts` fetch surface scoped to
 * `:feature:search:impl`. Stateless — the caller (Search Posts tab VM
 * in `nubecita-vrba.6`) owns the cursor and re-issues calls for the
 * next page. Mirrors [net.kikin.nubecita.feature.feed.impl.data.FeedRepository]'s
 * shape.
 *
 * Results are projected via
 * [net.kikin.nubecita.core.feedmapping.toFlatFeedItemUiSingle], which
 * ignores reply context — `searchPosts` results render as flat,
 * disconnected post cards regardless of whether a given hit is a reply.
 */
internal interface SearchPostsRepository {
    suspend fun searchPosts(
        query: String,
        cursor: String?,
        limit: Int = SEARCH_POSTS_PAGE_LIMIT,
        sort: SearchPostsSort = SearchPostsSort.TOP,
    ): Result<SearchPostsPage>
}

internal data class SearchPostsPage(
    val items: ImmutableList<FeedItemUi.Single>,
    val nextCursor: String?,
)

/**
 * Bluesky `searchPosts` sort modes. The lexicon accepts `"top"` and
 * `"latest"`; we serialise via `name.lowercase()` at the
 * [DefaultSearchPostsRepository] boundary. Default is [TOP] to match
 * the official Bluesky client.
 */
internal enum class SearchPostsSort { TOP, LATEST }

/**
 * Default page size for `searchPosts` requests. Lexicon allows 1–100;
 * 25 matches the search-data-layers spec default (slightly lower than
 * the timeline's 30 because search results are typically scanned,
 * not deeply scrolled).
 */
internal const val SEARCH_POSTS_PAGE_LIMIT: Int = 25
```

- [ ] **Step 4: Pass `sort` through in `DefaultSearchPostsRepository`**

Edit `feature/search/impl/src/main/kotlin/.../data/DefaultSearchPostsRepository.kt`. Add `sort: SearchPostsSort` to the override signature and pass `sort.name.lowercase()` to `SearchPostsRequest.sort`:

```kotlin
override suspend fun searchPosts(
    query: String,
    cursor: String?,
    limit: Int,
    sort: SearchPostsSort,
): Result<SearchPostsPage> {
    require(limit in 1..100) {
        "limit must be in 1..100 (atproto lexicon range), got $limit"
    }
    return withContext(dispatcher) {
        try {
            val client = xrpcClientProvider.authenticated()
            val response =
                FeedService(client).searchPosts(
                    SearchPostsRequest(
                        q = query,
                        cursor = cursor,
                        limit = limit.toLong(),
                        sort = sort.name.lowercase(),
                    ),
                )
            Result.success(
                SearchPostsPage(
                    items =
                        response.posts
                            .mapNotNull { it.toFlatFeedItemUiSingle() }
                            .toImmutableList(),
                    nextCursor = response.cursor,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Timber.tag(TAG).e(
                t,
                "searchPosts(q=%s, cursor=%s, sort=%s) failed: %s",
                query,
                cursor,
                sort,
                t.javaClass.name,
            )
            Result.failure(t)
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.DefaultSearchPostsRepositoryTest" -i
```

Expected: all 7 tests pass (5 pre-existing + 2 new).

- [ ] **Step 6: Verify spotless + lint**

```bash
./gradlew :feature:search:impl:spotlessCheck :feature:search:impl:lintDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchPostsRepository.kt \
        feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepository.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepositoryTest.kt
git commit -m "feat(feature/search/impl): add sort param to searchposts repository

Adds SearchPostsSort enum (TOP / LATEST) and threads it through
DefaultSearchPostsRepository to SearchPostsRequest.sort as
name.lowercase(). Default TOP preserves existing call sites.

Refs: nubecita-vrba.6"
```

---

## Task 2: Add `HighlightedText` + `AnnotatedString.withMatchHighlight` to `:designsystem`

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/HighlightedText.kt`
- Create: `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/HighlightedTextScreenshotTest.kt`

Per spec D6, this composable splits plain text into `AnnotatedString` segments and applies a `SpanStyle` to case-insensitive substring matches. The lower-level helper `AnnotatedString.withMatchHighlight(match, style)` is co-located so `PostCard.BodyText` can reuse the same logic on top of its already-built facets annotated string (Task 3).

- [ ] **Step 1: Write the file**

Create `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/HighlightedText.kt`:

```kotlin
package net.kikin.nubecita.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Renders [text] with case-insensitive substring matches of [match]
 * styled by [highlightStyle]. When [match] is null or blank, renders
 * [text] unstyled (byte-identical to a plain [Text]).
 *
 * Used by the Search Posts tab to highlight the debounced query
 * inside post bodies + actor display names. Plain-text input only —
 * facets-rendered post bodies use the underlying
 * [withMatchHighlight] helper directly to merge highlights into the
 * facets annotated string. See `:designsystem/component/PostCard.kt`'s
 * `BodyText` for the integration.
 */
@Composable
fun HighlightedText(
    text: String,
    match: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    highlightStyle: SpanStyle =
        SpanStyle(
            background = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
) {
    val annotated = AnnotatedString(text).withMatchHighlight(match, highlightStyle)
    Text(text = annotated, modifier = modifier, style = style)
}

/**
 * Returns a new [AnnotatedString] with [highlightStyle] applied to every
 * case-insensitive occurrence of [match] in this string. When [match] is
 * null or blank, returns `this` unchanged. Preserves any existing styles
 * already attached to the receiver — used by `PostCard.BodyText` to
 * overlay match highlights on top of the facet annotations produced by
 * `rememberBlueskyAnnotatedString`.
 *
 * Implementation: walks plain `text` for matches and re-builds via
 * [buildAnnotatedString], appending the existing receiver as-is between
 * match spans (so mention/link spans survive). The match span overlays
 * on top via [withStyle].
 */
fun AnnotatedString.withMatchHighlight(
    match: String?,
    highlightStyle: SpanStyle,
): AnnotatedString {
    if (match.isNullOrBlank()) return this
    val haystack = this.text
    val needle = match
    val needleLower = needle.lowercase()
    val haystackLower = haystack.lowercase()
    if (needleLower.isEmpty() || needleLower !in haystackLower) return this

    return buildAnnotatedString {
        var cursor = 0
        while (cursor < haystack.length) {
            val matchStart = haystackLower.indexOf(needleLower, startIndex = cursor)
            if (matchStart < 0) {
                append(this@withMatchHighlight.subSequence(cursor, haystack.length))
                break
            }
            if (matchStart > cursor) {
                append(this@withMatchHighlight.subSequence(cursor, matchStart))
            }
            val matchEnd = matchStart + needleLower.length
            withStyle(highlightStyle) {
                append(this@withMatchHighlight.subSequence(matchStart, matchEnd))
            }
            cursor = matchEnd
        }
    }
}

@Preview(name = "HighlightedText — no match", showBackground = true)
@Composable
private fun HighlightedTextNoMatchPreview() {
    NubecitaTheme {
        HighlightedText(
            text = "The quick brown fox jumps over the lazy dog",
            match = null,
        )
    }
}

@Preview(name = "HighlightedText — single match", showBackground = true)
@Composable
private fun HighlightedTextSingleMatchPreview() {
    NubecitaTheme {
        HighlightedText(
            text = "The quick brown fox jumps over the lazy dog",
            match = "fox",
        )
    }
}

@Preview(name = "HighlightedText — multi match (case-insensitive)", showBackground = true)
@Composable
private fun HighlightedTextMultiMatchPreview() {
    NubecitaTheme {
        HighlightedText(
            text = "Kotlin and KOTLIN and kotlin — same word, three cases",
            match = "kotlin",
        )
    }
}

@Preview(name = "HighlightedText — needle longer than haystack", showBackground = true)
@Composable
private fun HighlightedTextNoOccurrencePreview() {
    NubecitaTheme {
        HighlightedText(
            text = "Short string",
            match = "completely-unrelated-very-long-needle",
        )
    }
}
```

- [ ] **Step 2: Compile to verify the file is syntactically valid**

```bash
./gradlew :designsystem:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Add the screenshot test file**

Create `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/HighlightedTextScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "highlighted-no-match-light", showBackground = true)
@Preview(name = "highlighted-no-match-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HighlightedTextNoMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            HighlightedText(
                text = "The quick brown fox jumps over the lazy dog",
                match = null,
            )
        }
    }
}

@PreviewTest
@Preview(name = "highlighted-single-match-light", showBackground = true)
@Preview(name = "highlighted-single-match-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HighlightedTextSingleMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            HighlightedText(
                text = "The quick brown fox jumps over the lazy dog",
                match = "fox",
            )
        }
    }
}

@PreviewTest
@Preview(name = "highlighted-multi-match-light", showBackground = true)
@Preview(name = "highlighted-multi-match-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HighlightedTextMultiMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            HighlightedText(
                text = "Kotlin and KOTLIN and kotlin — same word, three cases",
                match = "kotlin",
            )
        }
    }
}
```

- [ ] **Step 4: Generate the screenshot baselines**

```bash
./gradlew :designsystem:updateDebugScreenshotTest
./gradlew :designsystem:validateDebugScreenshotTest
```

Expected: 3 functions × 2 previews each = 6 new baselines under `designsystem/src/screenshotTest/reference/`. Validation passes.

- [ ] **Step 5: Commit**

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/HighlightedText.kt \
        designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/HighlightedTextScreenshotTest.kt \
        designsystem/src/screenshotTest/reference/
git commit -m "feat(designsystem): add highlightedtext + withmatchhighlight helper

Plain-text composable for query-substring highlighting + a reusable
AnnotatedString extension that preserves existing styles (e.g. facets
on the postcard body). Three previews + light/dark screenshot
baselines.

Refs: nubecita-vrba.6"
```

---

## Task 3: Thread an optional `bodyMatch` parameter through `PostCard`

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt`

The change is additive. `BodyText` currently builds an `AnnotatedString` via `rememberBlueskyAnnotatedString(text, facets)` then renders it via `Text`. We overlay match highlight spans on top of that annotated string using the new `withMatchHighlight` helper from Task 2.

- [ ] **Step 1: Add `bodyMatch` to `PostCard`'s signature + thread to `BodyText`**

In `designsystem/src/main/kotlin/.../component/PostCard.kt` (line 87 — the `PostCard` function), add a new parameter `bodyMatch: String? = null` AFTER `animateRepostTap`. Default null preserves byte-identical behavior for the feed.

```kotlin
@Composable
fun PostCard(
    post: PostUi,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks.None,
    connectAbove: Boolean = false,
    connectBelow: Boolean = false,
    videoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)? = null,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
    animateLikeTap: Boolean = false,
    animateRepostTap: Boolean = false,
    bodyMatch: String? = null,
) {
```

Locate the `BodyText(text = post.text, facets = post.facets)` call (around line 144) and pass the new parameter:

```kotlin
                    BodyText(text = post.text, facets = post.facets, bodyMatch = bodyMatch)
```

- [ ] **Step 2: Extend `BodyText` to apply the highlight overlay**

Locate the private `BodyText` function (around line 236). Add a `bodyMatch: String?` parameter and merge match spans on top of the facets-annotated string. Use the `MaterialTheme.colorScheme.primaryContainer` / `onPrimaryContainer` pair to match `HighlightedText`'s default.

Replace the existing `BodyText` body with:

```kotlin
@Composable
private fun BodyText(
    text: String,
    facets: kotlinx.collections.immutable.ImmutableList<Facet>,
    bodyMatch: String? = null,
) {
    val annotated = rememberBlueskyAnnotatedString(text = text, facets = facets)
    val withHighlight =
        annotated.withMatchHighlight(
            match = bodyMatch,
            highlightStyle =
                androidx.compose.ui.text.SpanStyle(
                    background = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        )
    Text(text = withHighlight, style = MaterialTheme.typography.bodyLarge)
}
```

(The fully-qualified `androidx.compose.ui.text.SpanStyle` reference keeps the diff localized — there's already a `SpanStyle` import indirectly available via the new `withMatchHighlight` extension, but spelling it out here makes the diff self-explanatory. The implementer may also add a top-level `import androidx.compose.ui.text.SpanStyle` and drop the FQN; either form is fine.)

- [ ] **Step 3: Add one preview that exercises the highlight path**

Add a `@Preview` next to the existing PostCard previews (anywhere in the previews block from line 420 onwards):

```kotlin
@Preview(name = "PostCard — with body match highlight", showBackground = true)
@Composable
private fun PostCardWithBodyMatchPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    text = "Kotlin is great. We use Kotlin every day at this Bluesky client.",
                ),
            bodyMatch = "kotlin",
        )
    }
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew :designsystem:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run any existing designsystem unit tests + screenshot validation**

```bash
./gradlew :designsystem:testDebugUnitTest :designsystem:validateDebugScreenshotTest
```

Expected: SUCCESSFUL. The screenshot validation should still pass — `bodyMatch` defaults to null, so existing PostCard screenshot baselines are unchanged.

- [ ] **Step 6: Add and regenerate a screenshot baseline for the new preview**

The new preview is plain `@Preview`, not `@PreviewTest` — so no new baseline is auto-generated. To capture it as a screenshot test, the implementer may add an explicit `@PreviewTest` companion in `designsystem/src/screenshotTest/kotlin/.../component/PostCardScreenshotTest.kt` if one exists, or skip this step (the spec lists `HighlightedText` baselines but not a separate PostCard body-match baseline — the visual coverage comes from the `PostsTabContent` screenshot test in Task 12 which exercises the full Loaded variant with matches).

Skip baseline regen unless `designsystem/src/screenshotTest/kotlin/.../component/PostCardScreenshotTest.kt` exists and you're extending it. Confirm by:

```bash
find designsystem/src/screenshotTest -name "PostCardScreenshotTest.kt" 2>/dev/null
```

If empty, move on.

- [ ] **Step 7: Commit**

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt
git commit -m "feat(designsystem/postcard): add optional bodymatch highlight param

Default null preserves existing rendering byte-for-byte. When set,
overlays primary-container highlight spans on top of the facets
annotated string via withMatchHighlight.

Refs: nubecita-vrba.6"
```

---

## Task 4: Create `SearchPostsError` + `Throwable.toSearchPostsError()`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsError.kt`
- Create: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsErrorTest.kt`

Per spec D3. Sealed sum + extension lives next to the VM contract. RateLimited is best-effort; the atproto-kotlin SDK doesn't expose a typed `RateLimitException`, so the implementer maps based on what the SDK actually surfaces. Treat unknown shapes as `Unknown(cause = message)` — the VM doesn't need to differentiate beyond that today.

- [ ] **Step 1: Write the failing test**

Create `feature/search/impl/src/test/kotlin/.../SearchPostsErrorTest.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class SearchPostsErrorTest {
    @Test
    fun ioException_mapsToNetwork() {
        val mapped = IOException("connection reset").toSearchPostsError()
        assertEquals(SearchPostsError.Network, mapped)
    }

    @Test
    fun unknownThrowable_mapsToUnknown_withMessage() {
        val mapped = IllegalStateException("totally unexpected").toSearchPostsError()
        assertTrue(mapped is SearchPostsError.Unknown)
        assertEquals("totally unexpected", (mapped as SearchPostsError.Unknown).cause)
    }

    @Test
    fun throwableWithNullMessage_mapsToUnknown_withNullCause() {
        val mapped = RuntimeException().toSearchPostsError()
        assertTrue(mapped is SearchPostsError.Unknown)
        assertEquals(null, (mapped as SearchPostsError.Unknown).cause)
    }
}
```

- [ ] **Step 2: Run the failing test**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsErrorTest" -i
```

Expected: compilation fails on `SearchPostsError`, `.toSearchPostsError()` (don't exist yet).

- [ ] **Step 3: Create `SearchPostsError.kt`**

Create `feature/search/impl/src/main/kotlin/.../SearchPostsError.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl

import java.io.IOException

/**
 * UI-resolvable error categories surfaced by the Search Posts tab.
 * The screen maps each variant to a stringResource call when rendering
 * — the VM stays Android-resource-free. Mirrors the [FeedError] shape
 * in `:feature:feed:impl`.
 *
 * `RateLimited` is reserved for an eventual atproto-kotlin 429 typed
 * exception; until the SDK exposes one, the mapping in
 * [toSearchPostsError] falls through to [Unknown]. When the SDK surface
 * lands, extend the `when` branch — no contract change at the VM/UI
 * boundary.
 */
internal sealed interface SearchPostsError {
    /** Underlying network or transport failure. */
    data object Network : SearchPostsError

    /** Bluesky / atproto rate-limit response (HTTP 429). */
    data object RateLimited : SearchPostsError

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    data class Unknown(val cause: String?) : SearchPostsError
}

internal fun Throwable.toSearchPostsError(): SearchPostsError =
    when (this) {
        is IOException -> SearchPostsError.Network
        // Future: add a branch for the SDK's typed 429 exception when it
        // exists (file an upstream issue against kikin81/atproto-kotlin
        // if it doesn't; we have a reference memory for that pattern).
        else -> SearchPostsError.Unknown(cause = message)
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsErrorTest" -i
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsError.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsErrorTest.kt
git commit -m "feat(feature/search/impl): add searchpostserror typed mapping

Sealed Network / RateLimited / Unknown sum + Throwable extension.
RateLimited branch reserved for an eventual SDK-typed 429 surface;
maps to Unknown today until atproto-kotlin exposes a typed
exception.

Refs: nubecita-vrba.6"
```

---

## Task 5: Define `SearchPostsContract.kt`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsContract.kt`

Per spec "MVI contract" section. The contract is read by the VM tests in Task 6+, so we add it now without its own test file.

Two deviations from the spec to flag inline:

1. **`NavigateToChangeSort` is dropped.** The spec lists it but never emits it — the `SortClicked` event is handled internally (`fetchKey.update { copy(sort = …) }`) and triggers a re-fetch via `mapLatest`; no effect is needed. The empty-state "Change sort" button dispatches `SortClicked` directly, same as the chip row. If a future surface needs the effect, add it then.
2. **`Loaded.hitsTotal: Long?` is dropped.** The spec mentions it as "surfaced for tab badge in vrba.8" but `SearchPostsResponse.hitsTotal` is not consumed by anything in vrba.6 and the Posts tab badge logic is vrba.8's responsibility. Adding the field here without a consumer would be YAGNI; vrba.8 can extend `SearchPostsPage` + `Loaded` to carry it when the badge UI lands.

- [ ] **Step 1: Create the contract file**

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

/**
 * MVI state for the Search Posts tab.
 *
 * [currentQuery] mirrors the latest non-debounced query set via
 * [SearchPostsViewModel.setQuery]. Held here (rather than re-derived
 * from the parent) so the empty-state copy + body match-highlighting
 * have a stable, recompose-friendly source.
 *
 * [sort] is the active sort mode. Defaults to [SearchPostsSort.TOP];
 * changing it via [SearchPostsEvent.SortClicked] resets pagination
 * and triggers a fresh first-page fetch.
 *
 * [loadStatus] is the mutually-exclusive lifecycle sum — per CLAUDE.md's
 * MVI carve-out, this stays sealed so the type system forbids
 * combinations like "InitialLoading AND Loaded.isAppending".
 */
@Immutable
internal data class SearchPostsState(
    val currentQuery: String = "",
    val sort: SearchPostsSort = SearchPostsSort.TOP,
    val loadStatus: SearchPostsLoadStatus = SearchPostsLoadStatus.Idle,
) : UiState

/**
 * Mutually-exclusive load lifecycle. Five variants, each rendered by a
 * distinct body composable in [net.kikin.nubecita.feature.search.impl.ui.PostsTabContent].
 *
 * - [Idle]: query is blank; render nothing (parent's recent-search chips show).
 * - [InitialLoading]: first-page fetch for the current query.
 * - [Loaded]: results in hand; [items], [nextCursor], [endReached] track
 *   pagination; [isAppending] is the transient pagination-in-flight flag.
 * - [Empty]: query returned zero results.
 * - [InitialError]: full-screen retry layout against the typed error.
 */
internal sealed interface SearchPostsLoadStatus {
    @Immutable
    data object Idle : SearchPostsLoadStatus

    @Immutable
    data object InitialLoading : SearchPostsLoadStatus

    @Immutable
    data class Loaded(
        val items: ImmutableList<FeedItemUi.Single>,
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,
    ) : SearchPostsLoadStatus

    @Immutable
    data object Empty : SearchPostsLoadStatus

    @Immutable
    data class InitialError(val error: SearchPostsError) : SearchPostsLoadStatus
}

internal sealed interface SearchPostsEvent : UiEvent {
    /** Append-on-scroll. Single-flight + idempotent past `endReached`. */
    data object LoadMore : SearchPostsEvent

    /** Re-run the initial fetch after [SearchPostsLoadStatus.InitialError]. */
    data object Retry : SearchPostsEvent

    /** Empty-state "Clear search" button. Parent VM clears the field via effect. */
    data object ClearQueryClicked : SearchPostsEvent

    /** Sort chip / empty-state "Change sort" button. Resets pagination + refetches. */
    data class SortClicked(val sort: SearchPostsSort) : SearchPostsEvent

    /** Tap on a post card. Emits [SearchPostsEffect.NavigateToPost]. */
    data class PostTapped(val uri: String) : SearchPostsEvent
}

internal sealed interface SearchPostsEffect : UiEffect {
    /** Push [net.kikin.nubecita.feature.postdetail.api.PostDetailRoute] onto the MainShell nav stack. */
    data class NavigateToPost(val uri: String) : SearchPostsEffect

    /** Snackbar-surface for append-time failures; existing results stay visible. */
    data class ShowAppendError(val error: SearchPostsError) : SearchPostsEffect

    /**
     * Empty-state CTA dispatched up to vrba.8's screen; vrba.8 forwards
     * to the parent [SearchViewModel] which owns the canonical
     * `TextFieldState`. SearchPostsViewModel can't reach the parent VM
     * directly (MVI rule: no Hilt ViewModel-into-ViewModel injection).
     */
    data object NavigateToClearQuery : SearchPostsEffect
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsContract.kt
git commit -m "feat(feature/search/impl): add searchpostscontract types

SearchPostsState (flat currentQuery/sort + sealed loadStatus),
sealed SearchPostsLoadStatus with five variants, SearchPostsEvent
(LoadMore/Retry/ClearQueryClicked/SortClicked/PostTapped),
SearchPostsEffect (NavigateToPost/ShowAppendError/NavigateToClearQuery).

Refs: nubecita-vrba.6"
```

---

## Task 6: Build the `SearchPostsViewModel` query pipeline (Idle → Loaded / Empty / InitialError)

**Files:**
- Create: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/FakeSearchPostsRepository.kt`
- Create: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsViewModelTest.kt` (first batch of tests)
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsViewModel.kt`

This is the largest task in the plan. Decompose into focused TDD micro-steps so the VM lands incrementally.

The VM holds a `MutableStateFlow<FetchKey>(...)` where `FetchKey(query, sort, incarnation: Int)`. The init block runs:

```
fetchKey
    .onEach { setState { copy(currentQuery = it.query, sort = it.sort) } }
    .filter { it.query.isNotBlank() }
    .mapLatest { runFirstPage(it) }
    .launchIn(viewModelScope)
```

NOTE: `MutableStateFlow` already filters identical consecutive values via its `value`-equality semantics, so `distinctUntilChanged()` is redundant but harmless — including it makes the intent explicit and mirrors the composer typeahead pipeline. Pick: include it for parity with `ComposerViewModel`'s pipeline. The `incarnation` field is what makes `Retry` produce a new `FetchKey` value even when query+sort haven't changed.

The setState `onEach` runs BEFORE the `filter`, so the blank-query path still updates `state.currentQuery` to `""` (which the Idle body uses indirectly — actually it ignores currentQuery while Idle, but updating is harmless and keeps state consistent for the moment query goes blank → renders Idle).

- [ ] **Step 1: Build the hand-written `FakeSearchPostsRepository`**

Create `feature/search/impl/src/test/kotlin/.../data/FakeSearchPostsRepository.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.data.models.FeedItemUi
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hand-written fake for [SearchPostsRepository]. Two configuration
 * paths:
 *
 *  - [respond] / [fail] register a result for a `(query, cursor, sort)`
 *    triple that the VM will hit. The first matching call wins; on a
 *    second call to the same triple, the registered response is
 *    consumed and replaced with the fallback ([fallback]).
 *  - [gate] returns a [CompletableDeferred] for the triple, suspending
 *    the call until the test completes it. Used by single-flight +
 *    mapLatest-cancellation tests.
 *
 * [callLog] is the chronological list of every `(query, cursor, sort)`
 * the VM passed; tests use it to assert that `mapLatest` cancelled
 * stale calls and that pagination passes through the right cursor.
 */
internal class FakeSearchPostsRepository : SearchPostsRepository {
    private data class Key(val query: String, val cursor: String?, val sort: SearchPostsSort)

    data class Call(val query: String, val cursor: String?, val sort: SearchPostsSort, val limit: Int)

    private val gates = mutableMapOf<Key, CompletableDeferred<Result<SearchPostsPage>>>()
    private var fallback: Result<SearchPostsPage> =
        Result.success(SearchPostsPage(items = persistentListOf(), nextCursor = null))
    val callLog: MutableList<Call> = CopyOnWriteArrayList()

    /** Register an immediate response for the given key. */
    fun respond(
        query: String,
        cursor: String?,
        sort: SearchPostsSort,
        items: List<FeedItemUi.Single>,
        nextCursor: String? = null,
    ) {
        gate(query, cursor, sort).complete(
            Result.success(
                SearchPostsPage(
                    items = items.toImmutableList(),
                    nextCursor = nextCursor,
                ),
            ),
        )
    }

    /** Register a failure for the given key. */
    fun fail(
        query: String,
        cursor: String?,
        sort: SearchPostsSort,
        throwable: Throwable,
    ) {
        gate(query, cursor, sort).complete(Result.failure(throwable))
    }

    /**
     * Returns a deferred for the given key; subsequent gate calls for
     * the same key return the same instance. Suspends [searchPosts]
     * for that key until the test completes the deferred.
     */
    fun gate(
        query: String,
        cursor: String?,
        sort: SearchPostsSort,
    ): CompletableDeferred<Result<SearchPostsPage>> = gates.getOrPut(Key(query, cursor, sort)) { CompletableDeferred() }

    /**
     * Configure the response returned when no gate is registered for
     * the requested key. Default is an empty page with no cursor.
     */
    fun setFallback(result: Result<SearchPostsPage>) {
        fallback = result
    }

    override suspend fun searchPosts(
        query: String,
        cursor: String?,
        limit: Int,
        sort: SearchPostsSort,
    ): Result<SearchPostsPage> {
        callLog += Call(query = query, cursor = cursor, sort = sort, limit = limit)
        val key = Key(query, cursor, sort)
        val deferred = gates[key]
        return deferred?.await() ?: fallback
    }
}

internal fun searchPostFixture(uri: String, text: String): FeedItemUi.Single =
    // The repo's outputs go straight into the VM; the VM doesn't introspect
    // every PostUi field. Build the minimum-viable PostUi shape that compiles
    // — fields not exercised by the tests can carry placeholder values.
    FeedItemUi.Single(
        post =
            net.kikin.nubecita.data.models.PostUi(
                id = uri,
                cid = "bafyfakecid",
                author =
                    net.kikin.nubecita.data.models.AuthorUi(
                        did = "did:plc:fake",
                        handle = "fake.bsky.social",
                        displayName = "Fake",
                        avatarUrl = null,
                    ),
                createdAt = kotlin.time.Clock.System.now(),
                text = text,
                facets = kotlinx.collections.immutable.persistentListOf(),
                embed = net.kikin.nubecita.data.models.EmbedUi.Empty,
                stats = net.kikin.nubecita.data.models.PostStatsUi(),
                viewer = net.kikin.nubecita.data.models.ViewerStateUi(),
            ),
    )
```

**Implementer notes on the fixture:** if `PostUi`'s constructor signature differs from what's shown above (the implementer should verify by opening `data/models/src/main/kotlin/.../PostUi.kt`), adjust accordingly. The aim is a minimal `FeedItemUi.Single` that round-trips through the VM's mapping logic without depending on real fixture JSON.

- [ ] **Step 2: Write the first three failing tests (Idle, InitialLoading→Loaded, Empty)**

Create `feature/search/impl/src/test/kotlin/.../SearchPostsViewModelTest.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.feature.search.impl.data.FakeSearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.data.searchPostFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SearchPostsViewModel].
 *
 * The VM owns a `MutableStateFlow<FetchKey>` whose `mapLatest` pipeline
 * runs on the test scheduler via `Dispatchers.setMain(UnconfinedTestDispatcher())`.
 * Unlike `SearchViewModelTest` / `ComposerViewModelTypeaheadTest`, no
 * Compose `TextFieldState` / `snapshotFlow` is involved, so we don't
 * need `Snapshot.sendApplyNotifications()` — `runCurrent()` after each
 * `setQuery` / `handleEvent` call is sufficient to drive the pipeline.
 *
 * Repository fake is the hand-written [FakeSearchPostsRepository]; mockk
 * is not in this module's testImplementation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchPostsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = FakeSearchPostsRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setQuery_blank_stateStaysIdle() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            runCurrent()

            assertEquals(SearchPostsLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
            assertEquals(0, repo.callLog.size, "blank query must not hit the repo")
        }

    @Test
    fun setQuery_nonBlank_fetchesFirstPage_emitsLoaded() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            val hit = searchPostFixture(uri = "at://did:plc:fake/p1", text = "kotlin")
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(hit),
                nextCursor = "c2",
            )

            vm.setQuery("kotlin")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded, "expected Loaded, was $status")
            status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf(hit), status.items.toList())
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
            assertEquals("kotlin", vm.uiState.value.currentQuery)
        }

    @Test
    fun setQuery_emptyResponse_emitsEmpty() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "no-matches",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = emptyList(),
                nextCursor = null,
            )

            vm.setQuery("no-matches")
            runCurrent()

            assertEquals(SearchPostsLoadStatus.Empty, vm.uiState.value.loadStatus)
        }
}
```

- [ ] **Step 3: Run the failing tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsViewModelTest" -i
```

Expected: compilation fails — `SearchPostsViewModel` doesn't exist yet.

- [ ] **Step 4: Implement the VM (minimal — query pipeline only)**

Create `feature/search/impl/src/main/kotlin/.../SearchPostsViewModel.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.search.impl.data.SearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import javax.inject.Inject

/**
 * Presenter for the Search Posts tab. Reactive on a
 * [MutableStateFlow] of [FetchKey] — the screen Composable's
 * `LaunchedEffect(parentState.currentQuery)` calls [setQuery] whenever
 * the parent [SearchViewModel] emits a new debounced query, and
 * [SortClicked] / [Retry] update the key from inside [handleEvent].
 *
 * The init pipeline runs:
 *
 *   fetchKey
 *     .distinctUntilChanged()
 *     .onEach { setState(currentQuery, sort) }
 *     .filter { it.query.isNotBlank() }
 *     .mapLatest { runFirstPage(it) }
 *     .launchIn(viewModelScope)
 *
 * `mapLatest` cancels the prior in-flight fetch on a new key — the
 * canonical pattern from `:feature:composer:impl`'s `ComposerViewModel`
 * typeahead path. Retry bumps an internal incarnation token so the
 * pipeline fires even when query + sort haven't changed.
 *
 * Does NOT inject the parent [SearchViewModel] — Hilt-injecting
 * ViewModels into each other is a smell. The screen Composable is the
 * orchestration seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchPostsViewModel
    @Inject
    constructor(
        private val repository: SearchPostsRepository,
    ) : MviViewModel<SearchPostsState, SearchPostsEvent, SearchPostsEffect>(SearchPostsState()) {
        private data class FetchKey(
            val query: String,
            val sort: SearchPostsSort,
            /** Bumps on [SearchPostsEvent.Retry] to force a re-emit when query+sort didn't change. */
            val incarnation: Int,
        )

        private val fetchKey =
            MutableStateFlow(FetchKey(query = "", sort = SearchPostsSort.TOP, incarnation = 0))

        init {
            fetchKey
                .distinctUntilChanged()
                .onEach { key ->
                    setState { copy(currentQuery = key.query, sort = key.sort) }
                }.filter { it.query.isNotBlank() }
                .mapLatest { key -> runFirstPage(key) }
                .launchIn(viewModelScope)
        }

        /** Called by the screen Composable from a `LaunchedEffect(parent.currentQuery)`. */
        fun setQuery(query: String) {
            fetchKey.update { it.copy(query = query) }
        }

        override fun handleEvent(event: SearchPostsEvent) {
            // LoadMore / Retry / SortClicked / ClearQueryClicked / PostTapped
            // land in Task 7. For now, the pipeline only reacts to setQuery.
            when (event) {
                SearchPostsEvent.LoadMore -> Unit
                SearchPostsEvent.Retry -> Unit
                SearchPostsEvent.ClearQueryClicked -> Unit
                is SearchPostsEvent.SortClicked -> Unit
                is SearchPostsEvent.PostTapped -> Unit
            }
        }

        private suspend fun runFirstPage(key: FetchKey) {
            setState { copy(loadStatus = SearchPostsLoadStatus.InitialLoading) }
            repository
                .searchPosts(query = key.query, cursor = null, sort = key.sort)
                .onSuccess { page ->
                    val nextStatus =
                        if (page.items.isEmpty()) {
                            SearchPostsLoadStatus.Empty
                        } else {
                            SearchPostsLoadStatus.Loaded(
                                items = page.items,
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                            )
                        }
                    setState { copy(loadStatus = nextStatus) }
                }.onFailure { throwable ->
                    setState {
                        copy(loadStatus = SearchPostsLoadStatus.InitialError(throwable.toSearchPostsError()))
                    }
                }
        }

        private companion object {
            const val TAG = "SearchPostsVM"
        }
    }
```

- [ ] **Step 5: Run the three tests to verify they pass**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsViewModelTest" -i
```

Expected: 3 tests pass.

- [ ] **Step 6: Add the InitialError test**

Append to `SearchPostsViewModelTest.kt`:

```kotlin
    @Test
    fun setQuery_failure_emitsInitialError_withMappedError() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.fail(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                throwable = java.io.IOException("disconnected"),
            )

            vm.setQuery("kotlin")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.InitialError, "expected InitialError, was $status")
            assertEquals(
                SearchPostsError.Network,
                (status as SearchPostsLoadStatus.InitialError).error,
            )
        }
```

- [ ] **Step 7: Run the test to verify**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsViewModelTest.setQuery_failure_emitsInitialError_withMappedError" -i
```

Expected: PASS.

- [ ] **Step 8: Add the mapLatest cancellation test**

Append to `SearchPostsViewModelTest.kt`. This exercises the spec's D1 requirement that `mapLatest` cancels the prior in-flight fetch on a new query.

```kotlin
    @Test
    fun setQuery_rapidChange_cancelsPrior_viaMapLatest() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            // Gate the "ali" fetch so it never completes — mapLatest must
            // cancel it when "alic" arrives.
            val aliGate =
                repo.gate(query = "ali", cursor = null, sort = SearchPostsSort.TOP)
            repo.respond(
                query = "alic",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://p1", "alic")),
                nextCursor = null,
            )

            vm.setQuery("ali")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchPostsLoadStatus.InitialLoading)

            vm.setQuery("alic")
            runCurrent()

            // Late completion of the cancelled deferred must NOT clobber
            // the Loaded("alic", ...) that mapLatest wrote.
            aliGate.complete(
                Result.success(
                    net.kikin.nubecita.feature.search.impl.data.SearchPostsPage(
                        items = kotlinx.collections.immutable.persistentListOf(
                            searchPostFixture("at://stale", "ali"),
                        ),
                        nextCursor = null,
                    ),
                ),
            )
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded, "expected Loaded for alic, was $status")
            assertEquals("at://p1", (status as SearchPostsLoadStatus.Loaded).items.single().post.id)
        }
```

- [ ] **Step 9: Run the test to verify**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsViewModelTest.setQuery_rapidChange_cancelsPrior_viaMapLatest" -i
```

Expected: PASS. If FAIL: the `mapLatest` operator may not be cancelling promptly because the cancellation happens at the `await()` suspension point — verify the pipeline shape matches the implementation exactly.

- [ ] **Step 10: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsViewModel.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsViewModelTest.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/FakeSearchPostsRepository.kt
git commit -m "feat(feature/search/impl): add searchpostsviewmodel query pipeline

MutableStateFlow<FetchKey>.distinctUntilChanged().mapLatest pipeline
projecting to Idle / InitialLoading / Loaded / Empty / InitialError.
Hand-written FakeSearchPostsRepository (no mockk per module convention).
Five unit tests cover the happy path + mapLatest cancellation.

Refs: nubecita-vrba.6"
```

---

## Task 7: Implement `LoadMore` (single-flight + append) + `ShowAppendError`

**Files:**
- Modify: `feature/search/impl/src/main/kotlin/.../SearchPostsViewModel.kt`
- Modify: `feature/search/impl/src/test/kotlin/.../SearchPostsViewModelTest.kt`

Per spec D4. Guard order:
1. If `status !is Loaded` → no-op.
2. If `status.endReached` → no-op (idempotent).
3. If `status.isAppending` → no-op (single-flight).
4. Else: flip `isAppending = true`, fetch with current cursor, append on success / emit `ShowAppendError` effect on failure (no state flip — keep existing items visible).

- [ ] **Step 1: Write the failing tests for LoadMore**

Append to `SearchPostsViewModelTest.kt`:

```kotlin
    @Test
    fun loadMore_loaded_appendsNextPage_andClearsIsAppending() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            val page1 = listOf(searchPostFixture("at://p1", "p1"))
            val page2 = listOf(searchPostFixture("at://p2", "p2"), searchPostFixture("at://p3", "p3"))
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = page1,
                nextCursor = "c2",
            )
            repo.respond(
                query = "kotlin",
                cursor = "c2",
                sort = SearchPostsSort.TOP,
                items = page2,
                nextCursor = "c3",
            )

            vm.setQuery("kotlin")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchPostsLoadStatus.Loaded)

            vm.handleEvent(SearchPostsEvent.LoadMore)
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded)
            status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf("at://p1", "at://p2", "at://p3"), status.items.map { it.post.id })
            assertEquals("c3", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
        }

    @Test
    fun loadMore_endReached_isNoOp() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://p1", "p1")),
                nextCursor = null, // endReached
            )

            vm.setQuery("kotlin")
            runCurrent()
            val beforeCallCount = repo.callLog.size

            vm.handleEvent(SearchPostsEvent.LoadMore)
            runCurrent()

            assertEquals(beforeCallCount, repo.callLog.size, "endReached must short-circuit before hitting repo")
        }

    @Test
    fun loadMore_alreadyAppending_isNoOp_singleFlight() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://p1", "p1")),
                nextCursor = "c2",
            )
            // Gate the second-page fetch so isAppending stays true.
            repo.gate(query = "kotlin", cursor = "c2", sort = SearchPostsSort.TOP)

            vm.setQuery("kotlin")
            runCurrent()

            vm.handleEvent(SearchPostsEvent.LoadMore)
            runCurrent()
            val callsAfterFirstLoadMore = repo.callLog.size
            val statusMid = vm.uiState.value.loadStatus
            assertTrue(statusMid is SearchPostsLoadStatus.Loaded)
            assertEquals(true, (statusMid as SearchPostsLoadStatus.Loaded).isAppending)

            // Second LoadMore while the first is in flight — should be dropped.
            vm.handleEvent(SearchPostsEvent.LoadMore)
            runCurrent()
            assertEquals(
                callsAfterFirstLoadMore,
                repo.callLog.size,
                "concurrent LoadMore must not double-fire the repo",
            )
        }

    @Test
    fun loadMore_failure_emitsShowAppendError_keepsExistingItems() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://p1", "p1")),
                nextCursor = "c2",
            )
            repo.fail(
                query = "kotlin",
                cursor = "c2",
                sort = SearchPostsSort.TOP,
                throwable = java.io.IOException("flap"),
            )

            vm.setQuery("kotlin")
            runCurrent()

            app.cash.turbine.test(vm.effects) {
                vm.handleEvent(SearchPostsEvent.LoadMore)
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchPostsEffect.ShowAppendError)
                assertEquals(SearchPostsError.Network, (effect as SearchPostsEffect.ShowAppendError).error)
                cancelAndIgnoreRemainingEvents()
            }

            // Existing items still visible, isAppending cleared.
            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded)
            status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf("at://p1"), status.items.map { it.post.id })
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.isAppending)
        }
```

Note on the Turbine call shape: `app.cash.turbine.test(vm.effects) { ... }` is the modular form (the receiver-style `vm.effects.test { ... }` works too — pick whichever matches the existing style in `:feature:search:impl` tests). If the existing tests use `vm.effects.test { ... }` (the `ReceiveTurbine.test` extension), prefer that to match local convention. Confirm by `grep "turbine" feature/search/impl/src/test`.

- [ ] **Step 2: Run the failing tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsViewModelTest.loadMore*" -i
```

Expected: compile error or all four fail — `LoadMore` handler is a no-op stub.

- [ ] **Step 3: Implement `loadMore()` in the VM**

Edit `SearchPostsViewModel.kt`. Replace the `LoadMore` no-op in `handleEvent`:

```kotlin
            override fun handleEvent(event: SearchPostsEvent) {
                when (event) {
                    SearchPostsEvent.LoadMore -> loadMore()
                    SearchPostsEvent.Retry -> Unit
                    SearchPostsEvent.ClearQueryClicked -> Unit
                    is SearchPostsEvent.SortClicked -> Unit
                    is SearchPostsEvent.PostTapped -> Unit
                }
            }
```

And add the `loadMore()` private method (place it next to `runFirstPage`):

```kotlin
            private fun loadMore() {
                val status = uiState.value.loadStatus
                if (status !is SearchPostsLoadStatus.Loaded) return
                if (status.endReached) return
                if (status.isAppending) return

                setState { copy(loadStatus = status.copy(isAppending = true)) }
                val cursor = status.nextCursor
                val sort = uiState.value.sort
                val query = uiState.value.currentQuery
                viewModelScope.launch {
                    repository
                        .searchPosts(query = query, cursor = cursor, sort = sort)
                        .onSuccess { page ->
                            val current = uiState.value.loadStatus as? SearchPostsLoadStatus.Loaded ?: return@onSuccess
                            val appended = (current.items + page.items).toImmutableList()
                            setState {
                                copy(
                                    loadStatus =
                                        current.copy(
                                            items = appended,
                                            nextCursor = page.nextCursor,
                                            endReached = page.nextCursor == null,
                                            isAppending = false,
                                        ),
                                )
                            }
                        }.onFailure { throwable ->
                            val current = uiState.value.loadStatus as? SearchPostsLoadStatus.Loaded ?: return@onFailure
                            setState { copy(loadStatus = current.copy(isAppending = false)) }
                            sendEffect(SearchPostsEffect.ShowAppendError(throwable.toSearchPostsError()))
                        }
                }
            }
```

Add the missing imports at the top:

```kotlin
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsViewModelTest" -i
```

Expected: all 8 tests pass (5 prior + 4 new).

- [ ] **Step 5: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsViewModel.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsViewModelTest.kt
git commit -m "feat(feature/search/impl): add loadmore single-flight + showappenderror

Guard order: Loaded → !endReached → !isAppending. On failure, keeps
existing items visible and emits ShowAppendError effect for snackbar
surfacing.

Refs: nubecita-vrba.6"
```

---

## Task 8: Implement `Retry`, `SortClicked`, `ClearQueryClicked`, `PostTapped`

**Files:**
- Modify: `feature/search/impl/src/main/kotlin/.../SearchPostsViewModel.kt`
- Modify: `feature/search/impl/src/test/kotlin/.../SearchPostsViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `SearchPostsViewModelTest.kt`:

```kotlin
    @Test
    fun sortClicked_resetsPaginationAndFetches_freshFirstPage() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://top1", "top1")),
                nextCursor = "c2",
            )
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.LATEST,
                items = listOf(searchPostFixture("at://latest1", "latest1")),
                nextCursor = null,
            )

            vm.setQuery("kotlin")
            runCurrent()
            val firstStatus = vm.uiState.value.loadStatus as SearchPostsLoadStatus.Loaded
            assertEquals("at://top1", firstStatus.items.single().post.id)

            vm.handleEvent(SearchPostsEvent.SortClicked(SearchPostsSort.LATEST))
            runCurrent()

            val secondStatus = vm.uiState.value.loadStatus
            assertTrue(secondStatus is SearchPostsLoadStatus.Loaded)
            assertEquals("at://latest1", (secondStatus as SearchPostsLoadStatus.Loaded).items.single().post.id)
            assertEquals(SearchPostsSort.LATEST, vm.uiState.value.sort)
        }

    @Test
    fun retry_initialError_retriggersFirstPage_viaIncarnationBump() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            // First call fails.
            repo.fail(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                throwable = java.io.IOException("network"),
            )

            vm.setQuery("kotlin")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchPostsLoadStatus.InitialError)

            // Pre-register success for the same key — Retry will bump
            // incarnation, distinctUntilChanged sees a new value, and
            // mapLatest fires again.
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://retry-success", "yay")),
                nextCursor = null,
            )

            vm.handleEvent(SearchPostsEvent.Retry)
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded, "expected Loaded after retry, was $status")
        }

    @Test
    fun postTapped_emitsNavigateToPostEffect() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            app.cash.turbine.test(vm.effects) {
                vm.handleEvent(SearchPostsEvent.PostTapped("at://did:plc:fake/p1"))
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchPostsEffect.NavigateToPost)
                assertEquals("at://did:plc:fake/p1", (effect as SearchPostsEffect.NavigateToPost).uri)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearQueryClicked_emitsNavigateToClearQueryEffect() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            app.cash.turbine.test(vm.effects) {
                vm.handleEvent(SearchPostsEvent.ClearQueryClicked)
                runCurrent()

                assertEquals(SearchPostsEffect.NavigateToClearQuery, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
```

- [ ] **Step 2: Run the failing tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsViewModelTest" -i
```

Expected: the four new tests fail (handlers are still no-ops).

- [ ] **Step 3: Implement the handlers**

Edit `SearchPostsViewModel.kt`. Replace `handleEvent`'s body:

```kotlin
            override fun handleEvent(event: SearchPostsEvent) {
                when (event) {
                    SearchPostsEvent.LoadMore -> loadMore()
                    SearchPostsEvent.Retry ->
                        fetchKey.update { it.copy(incarnation = it.incarnation + 1) }
                    SearchPostsEvent.ClearQueryClicked ->
                        sendEffect(SearchPostsEffect.NavigateToClearQuery)
                    is SearchPostsEvent.SortClicked ->
                        fetchKey.update { it.copy(sort = event.sort, incarnation = it.incarnation + 1) }
                    is SearchPostsEvent.PostTapped ->
                        sendEffect(SearchPostsEffect.NavigateToPost(event.uri))
                }
            }
```

Note the `incarnation` bump on `SortClicked` as well — without it, if the user clicks the *currently-selected* sort, the pipeline wouldn't refire. Defensive against a UI glitch that double-fires the same sort.

- [ ] **Step 4: Run all VM tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchPostsViewModelTest" -i
```

Expected: all 12 tests pass.

- [ ] **Step 5: Run all module tests + spotless + lint**

```bash
./gradlew :feature:search:impl:testDebugUnitTest :feature:search:impl:spotlessCheck :feature:search:impl:lintDebug
```

Expected: SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsViewModel.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsViewModelTest.kt
git commit -m "feat(feature/search/impl): wire retry / sortclicked / cleartap / posttapped handlers

Retry bumps an incarnation token on FetchKey so distinctUntilChanged
sees a new value and mapLatest refires the first-page fetch. SortClicked
also bumps incarnation defensively in case the UI double-fires the
currently-selected sort. ClearQueryClicked + PostTapped route through
UiEffect for screen-level handling.

Refs: nubecita-vrba.6"
```

---

## Task 9: Add `strings.xml` entries

**Files:**
- Modify: `feature/search/impl/src/main/res/values/strings.xml`

The UI bodies in Tasks 10–14 reference these strings. Add them up-front so the composables compile.

- [ ] **Step 1: Verify the file exists and capture its current state**

```bash
cat feature/search/impl/src/main/res/values/strings.xml
```

If it doesn't exist, create the directory and an empty resources skeleton.

- [ ] **Step 2: Add the new entries**

Edit (or create) `feature/search/impl/src/main/res/values/strings.xml`. Add the entries below (preserve any existing entries; merge into the existing `<resources>` block):

```xml
    <!-- vrba.6: Search Posts tab strings -->

    <!-- Loading state -->
    <string name="search_posts_loading_content_description">Searching posts</string>

    <!-- Empty state -->
    <string name="search_posts_empty_title">Nothing matches \"%1$s\"</string>
    <string name="search_posts_empty_body">Try a different spelling, or change the sort to broaden the search.</string>
    <string name="search_posts_empty_clear">Clear search</string>
    <string name="search_posts_empty_change_sort_to_top">Show top posts</string>
    <string name="search_posts_empty_change_sort_to_latest">Show latest posts</string>
    <string name="search_posts_empty_icon_content_description">No matching posts</string>

    <!-- Initial-error state -->
    <string name="search_posts_error_network_title">No connection</string>
    <string name="search_posts_error_network_body">Check your connection and try again.</string>
    <string name="search_posts_error_rate_limited_title">Slow down</string>
    <string name="search_posts_error_rate_limited_body">You\'ve been searching a lot. Wait a moment and try again.</string>
    <string name="search_posts_error_unknown_title">Something went wrong</string>
    <string name="search_posts_error_unknown_body">We couldn\'t load your search. Try again.</string>
    <string name="search_posts_error_retry">Try again</string>

    <!-- Sort row -->
    <string name="search_posts_sort_top">Top</string>
    <string name="search_posts_sort_latest">Latest</string>
    <string name="search_posts_sort_label">Sort by</string>

    <!-- Append failure snackbar -->
    <string name="search_posts_append_error_network">Couldn\'t load more posts. Check your connection.</string>
    <string name="search_posts_append_error_rate_limited">Slow down — you\'ve been searching a lot.</string>
    <string name="search_posts_append_error_unknown">Couldn\'t load more posts. Try again.</string>
```

- [ ] **Step 3: Verify lint passes**

```bash
./gradlew :feature:search:impl:lintDebug
```

Expected: BUILD SUCCESSFUL. If lint complains about missing translations, suppress per the existing module's convention (check whether other strings.xml files in this module suppress; mirror).

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/src/main/res/values/strings.xml
git commit -m "feat(feature/search/impl): add posts-tab strings

Loading / empty / error / sort / append-snackbar strings consumed
by the UI bodies landing in subsequent commits.

Refs: nubecita-vrba.6"
```

---

## Task 10: Build `PostsLoadingBody`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../ui/PostsLoadingBody.kt`

Mirrors `:feature:feed:impl/ui` shimmer/loading patterns. Simple stateless composable: a centered `CircularProgressIndicator` plus three shimmer-style placeholder rows. Use `:designsystem`'s `PostCardShimmer` if it exists; otherwise fall back to a plain `CircularProgressIndicator`.

- [ ] **Step 1: Check whether `:designsystem` exposes a reusable shimmer**

```bash
find designsystem/src/main -name "PostCardShimmer.kt" 2>/dev/null
find designsystem/src/main -name "Shimmer.kt" 2>/dev/null
```

Both should exist (listed in earlier file survey). Open `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardShimmer.kt` to confirm its signature.

- [ ] **Step 2: Create `PostsLoadingBody.kt`**

If `PostCardShimmer()` exists and takes no args (or trivial args), use it three times. Otherwise fall back to a `Column { CircularProgressIndicator(); Spacer; (three PostCardShimmer or three placeholder Row()s) }`. Implementer picks based on what's actually exposed.

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCardShimmer
import net.kikin.nubecita.feature.search.impl.R

/**
 * Loading body for the Posts tab. Three shimmer post-cards. Mirrors
 * the feed's loading-state pattern — same shimmer component, same
 * count so the visual continuity carries across surfaces.
 *
 * Accessibility: the parent Column carries a contentDescription so
 * TalkBack announces "Searching posts" instead of three individual
 * empty cards.
 */
@Composable
internal fun PostsLoadingBody(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.search_posts_loading_content_description)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        repeat(3) {
            PostCardShimmer()
        }
    }
}

@Preview(name = "PostsLoadingBody — light", showBackground = true)
@Preview(
    name = "PostsLoadingBody — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostsLoadingBodyPreview() {
    NubecitaTheme {
        PostsLoadingBody()
    }
}
```

Verify `dp` is imported (`androidx.compose.ui.unit.dp`). If `PostCardShimmer()` has a different parameter list (e.g., requires a `Modifier`), adjust at the call site.

- [ ] **Step 3: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/PostsLoadingBody.kt
git commit -m "feat(feature/search/impl): add postsloadingbody shimmer

Three PostCardShimmer rows with a semantics contentDescription so
TalkBack announces a single loading state.

Refs: nubecita-vrba.6"
```

---

## Task 11: Build `PostsEmptyBody`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../ui/PostsEmptyBody.kt`

Per spec D10. Icon + display heading with current query + body + two buttons. Per D10, swap the second button label based on the current sort (`TOP` → "Show latest posts"; `LATEST` → "Show top posts"). Use Material Symbols `NubecitaIconName.SearchOff` (per the spec's first preference).

- [ ] **Step 1: Verify the icon name exists**

```bash
grep -n "SearchOff\|CloudOff\|Inbox\b" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt
```

If `SearchOff` isn't in the icon-font subset, fall back to `CloudOff` or `Inbox` (the feed empty-state uses `Inbox`). Document the substitution in the file's KDoc.

- [ ] **Step 2: Create `PostsEmptyBody.kt`**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

/**
 * Empty-state body for the Posts tab. Per design D10: large icon,
 * display heading with the user's query, descriptive body, and two
 * actions — "Clear search" (tonal) and "Show {opposite-sort} posts"
 * (outlined). The outlined CTA label flips based on the current sort.
 *
 * Custom cloud illustration from the design handoff is out of scope;
 * Material Symbol [NubecitaIconName.SearchOff] (or fallback) is the V1
 * visual.
 */
@Composable
internal fun PostsEmptyBody(
    currentQuery: String,
    currentSort: SearchPostsSort,
    onClearQuery: () -> Unit,
    onToggleSort: (SearchPostsSort) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val oppositeSort =
        when (currentSort) {
            SearchPostsSort.TOP -> SearchPostsSort.LATEST
            SearchPostsSort.LATEST -> SearchPostsSort.TOP
        }
    val oppositeLabelRes =
        when (oppositeSort) {
            SearchPostsSort.LATEST -> R.string.search_posts_empty_change_sort_to_latest
            SearchPostsSort.TOP -> R.string.search_posts_empty_change_sort_to_top
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.SearchOff,
            contentDescription = stringResource(R.string.search_posts_empty_icon_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(R.string.search_posts_empty_title, currentQuery),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.search_posts_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
        ) {
            FilledTonalButton(onClick = onClearQuery) {
                Text(stringResource(R.string.search_posts_empty_clear))
            }
            OutlinedButton(onClick = { onToggleSort(oppositeSort) }) {
                Text(stringResource(oppositeLabelRes))
            }
        }
    }
}

private val ICON_SIZE = 96.dp

@Preview(name = "PostsEmptyBody — light, TOP active", showBackground = true)
@Composable
private fun PostsEmptyBodyTopPreview() {
    NubecitaTheme {
        PostsEmptyBody(
            currentQuery = "kotlin",
            currentSort = SearchPostsSort.TOP,
            onClearQuery = {},
            onToggleSort = {},
        )
    }
}

@Preview(
    name = "PostsEmptyBody — dark, LATEST active",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostsEmptyBodyLatestDarkPreview() {
    NubecitaTheme {
        PostsEmptyBody(
            currentQuery = "compose",
            currentSort = SearchPostsSort.LATEST,
            onClearQuery = {},
            onToggleSort = {},
        )
    }
}
```

If `NubecitaIconName.SearchOff` doesn't exist, fall back to `NubecitaIconName.Inbox` or `NubecitaIconName.CloudOff` per Step 1 — adjust the import and the call.

- [ ] **Step 3: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/PostsEmptyBody.kt
git commit -m "feat(feature/search/impl): add postsemptybody rich layout

Display heading with query, two-button row (Clear search tonal +
Change-sort outlined). Outlined CTA label flips based on the active
sort. Skipping the design's custom cloud illustration per spec scope.

Refs: nubecita-vrba.6"
```

---

## Task 12: Build `PostsInitialErrorBody`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../ui/PostsInitialErrorBody.kt`

Full-screen retry layout mirroring `:feature:chats:impl`'s ErrorBody pattern. Map `SearchPostsError` → stringResource via a `when`. Single "Try again" button dispatches `Retry`.

- [ ] **Step 1: Create `PostsInitialErrorBody.kt`**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.SearchPostsError

@Composable
internal fun PostsInitialErrorBody(
    error: SearchPostsError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val (titleRes, bodyRes) =
        when (error) {
            SearchPostsError.Network ->
                R.string.search_posts_error_network_title to R.string.search_posts_error_network_body
            SearchPostsError.RateLimited ->
                R.string.search_posts_error_rate_limited_title to R.string.search_posts_error_rate_limited_body
            is SearchPostsError.Unknown ->
                R.string.search_posts_error_unknown_title to R.string.search_posts_error_unknown_body
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.search_posts_error_retry))
        }
    }
}

private val ICON_SIZE = 64.dp

@Preview(name = "PostsInitialErrorBody — Network (light)", showBackground = true)
@Composable
private fun PostsInitialErrorBodyNetworkPreview() {
    NubecitaTheme {
        PostsInitialErrorBody(error = SearchPostsError.Network, onRetry = {})
    }
}

@Preview(
    name = "PostsInitialErrorBody — RateLimited (dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostsInitialErrorBodyRateLimitedDarkPreview() {
    NubecitaTheme {
        PostsInitialErrorBody(error = SearchPostsError.RateLimited, onRetry = {})
    }
}

@Preview(name = "PostsInitialErrorBody — Unknown (light)", showBackground = true)
@Composable
private fun PostsInitialErrorBodyUnknownPreview() {
    NubecitaTheme {
        PostsInitialErrorBody(
            error = SearchPostsError.Unknown(cause = "Decode failure"),
            onRetry = {},
        )
    }
}
```

If `NubecitaIconName.WifiOff` doesn't exist, fall back to `NubecitaIconName.SearchOff` or whatever fits — implementer picks based on what's in the icon-font subset.

- [ ] **Step 2: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/PostsInitialErrorBody.kt
git commit -m "feat(feature/search/impl): add postsinitialerrorbody

Full-screen retry layout mapping each SearchPostsError variant to a
title/body string pair. Mirrors :feature:chats:impl's ErrorBody.

Refs: nubecita-vrba.6"
```

---

## Task 13: Build `PostsSortRow`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../ui/PostsSortRow.kt`

A Material 3 `FilterChip` row with "Top" / "Latest". Visible inside `Loaded` and (per D10) `Empty`. Implements the spec's D5 chip UI.

- [ ] **Step 1: Create `PostsSortRow.kt`**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

@Composable
internal fun PostsSortRow(
    activeSort: SearchPostsSort,
    onSortSelected: (SearchPostsSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.s4,
                    vertical = MaterialTheme.spacing.s2,
                ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
    ) {
        FilterChip(
            selected = activeSort == SearchPostsSort.TOP,
            onClick = { onSortSelected(SearchPostsSort.TOP) },
            label = { Text(stringResource(R.string.search_posts_sort_top)) },
        )
        FilterChip(
            selected = activeSort == SearchPostsSort.LATEST,
            onClick = { onSortSelected(SearchPostsSort.LATEST) },
            label = { Text(stringResource(R.string.search_posts_sort_latest)) },
        )
    }
}

@Preview(name = "PostsSortRow — Top active (light)", showBackground = true)
@Composable
private fun PostsSortRowTopPreview() {
    NubecitaTheme {
        PostsSortRow(activeSort = SearchPostsSort.TOP, onSortSelected = {})
    }
}

@Preview(
    name = "PostsSortRow — Latest active (dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostsSortRowLatestDarkPreview() {
    NubecitaTheme {
        PostsSortRow(activeSort = SearchPostsSort.LATEST, onSortSelected = {})
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/PostsSortRow.kt
git commit -m "feat(feature/search/impl): add postssortrow filterchip strip

Top / Latest filter chips. Filters chip from the design handoff is
out of scope.

Refs: nubecita-vrba.6"
```

---

## Task 14: Build `PostsTabContent` (stateless body)

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../ui/PostsTabContent.kt`

Ties together the load-status branches. Renders the `PostsSortRow` at the top whenever the status carries meaningful state (`Loaded` or `Empty`). For `Loaded`, uses a `LazyColumn` of `PostCard`s with `bodyMatch = state.currentQuery`. Pagination via a `LaunchedEffect` keyed on the last visible item index (mirror the feed's pagination trigger).

- [ ] **Step 1: Find the feed's pagination trigger pattern**

```bash
grep -n "LaunchedEffect\|LoadMore\|lastVisibleItem" feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt | head -30
```

Mirror that pattern for the LazyColumn. If feed uses `snapshotFlow { listState.layoutInfo.visibleItemsInfo }`, use the same shape.

- [ ] **Step 2: Create `PostsTabContent.kt`**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.feature.search.impl.SearchPostsEvent
import net.kikin.nubecita.feature.search.impl.SearchPostsLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchPostsState
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

/**
 * Stateless body for the Posts tab. Branches on
 * [SearchPostsState.loadStatus]; renders the sort row above non-Idle
 * states; for Loaded uses a LazyColumn of PostCards with bodyMatch
 * highlighting; for Loaded + isAppending shows a bottom progress row.
 *
 * `onEvent` dispatches every user intent upward to the VM. Doesn't
 * hoist `hiltViewModel` itself — that's [SearchPostsScreen]'s job —
 * so previews + screenshot tests can drive this Composable without a
 * Hilt graph.
 *
 * Pagination trigger: a `snapshotFlow` over the `lastVisibleItemIndex`
 * fires `LoadMore` when the user scrolls within [PAGINATION_LOAD_AHEAD]
 * items of the tail. Mirrors `:feature:feed:impl/FeedScreen`'s shape.
 */
@Composable
internal fun PostsTabContent(
    state: SearchPostsState,
    onEvent: (SearchPostsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    when (val status = state.loadStatus) {
        SearchPostsLoadStatus.Idle ->
            Box(modifier = modifier.fillMaxSize())
        SearchPostsLoadStatus.InitialLoading ->
            PostsLoadingBody(modifier = modifier)
        is SearchPostsLoadStatus.Loaded -> {
            LoadedBody(
                items = status.items,
                isAppending = status.isAppending,
                endReached = status.endReached,
                currentQuery = state.currentQuery,
                currentSort = state.sort,
                listState = listState,
                onEvent = onEvent,
                modifier = modifier,
            )
            // Pagination trigger
            LaunchedEffect(listState, status.items.size, status.endReached, status.isAppending) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    last to info.totalItemsCount
                }.distinctUntilChanged()
                    .filter { (last, total) ->
                        total > 0 && last >= total - PAGINATION_LOAD_AHEAD
                    }.collect {
                        if (!status.endReached && !status.isAppending) {
                            onEvent(SearchPostsEvent.LoadMore)
                        }
                    }
            }
        }
        SearchPostsLoadStatus.Empty ->
            Column(modifier = modifier.fillMaxSize()) {
                PostsSortRow(
                    activeSort = state.sort,
                    onSortSelected = { onEvent(SearchPostsEvent.SortClicked(it)) },
                )
                PostsEmptyBody(
                    currentQuery = state.currentQuery,
                    currentSort = state.sort,
                    onClearQuery = { onEvent(SearchPostsEvent.ClearQueryClicked) },
                    onToggleSort = { onEvent(SearchPostsEvent.SortClicked(it)) },
                )
            }
        is SearchPostsLoadStatus.InitialError ->
            PostsInitialErrorBody(
                error = status.error,
                onRetry = { onEvent(SearchPostsEvent.Retry) },
                modifier = modifier,
            )
    }
}

@Composable
private fun LoadedBody(
    items: kotlinx.collections.immutable.ImmutableList<FeedItemUi.Single>,
    isAppending: Boolean,
    endReached: Boolean,
    currentQuery: String,
    currentSort: SearchPostsSort,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEvent: (SearchPostsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
    ) {
        item("sort-row") {
            PostsSortRow(
                activeSort = currentSort,
                onSortSelected = { onEvent(SearchPostsEvent.SortClicked(it)) },
            )
        }
        items(items = items, key = { it.post.id }) { item ->
            PostCard(
                post = item.post,
                bodyMatch = currentQuery.takeIf { it.isNotBlank() },
                callbacks =
                    net.kikin.nubecita.designsystem.component.PostCallbacks(
                        onTap = { post -> onEvent(SearchPostsEvent.PostTapped(post.id)) },
                    ),
            )
        }
        if (isAppending) {
            item("appending-indicator") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        }
    }
}

private const val PAGINATION_LOAD_AHEAD = 5
```

**Notes for the implementer:**

- `PostCallbacks` may have a different constructor shape than `PostCallbacks(onTap = ...)` — open `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCallbacks.kt` and adjust. The feed wires every callback via the `PostCallbacks` constructor or factory.
- The pagination trigger keys on `listState`, `items.size`, `status.endReached`, `status.isAppending` to ensure the LaunchedEffect restarts when any of those change. Adjust if the feed's pattern differs.
- The sort row is `item("sort-row")` inside the LazyColumn so it scrolls with the list (matches the design handoff). If a sticky-header behavior is wanted, the implementer can extract the LazyColumn into a `Column { PostsSortRow(...); LazyColumn { items {...} } }` instead — pick whichever the spec implies. Spec D10 says "Visible only when loadStatus is Loaded or query is non-blank" → in-list works.
- The `Idle` branch renders an empty Box because parent SearchScreen's recent-search chips are shown instead. The PostsTab simply yields visually.

- [ ] **Step 3: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. Likely first-attempt failures around `PostCallbacks` constructor mismatch — fix by reading the actual file and matching the call.

- [ ] **Step 4: Add previews for the five status variants**

Add inside the same file (after the existing composable):

```kotlin
@androidx.compose.ui.tooling.preview.Preview(name = "PostsTabContent — InitialLoading", showBackground = true)
@Composable
private fun PostsTabContentInitialLoadingPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PostsTabContent(
            state = SearchPostsState(loadStatus = SearchPostsLoadStatus.InitialLoading, currentQuery = "kotlin"),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PostsTabContent — Empty", showBackground = true)
@Composable
private fun PostsTabContentEmptyPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PostsTabContent(
            state = SearchPostsState(loadStatus = SearchPostsLoadStatus.Empty, currentQuery = "xyzqq"),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PostsTabContent — InitialError(Network)", showBackground = true)
@Composable
private fun PostsTabContentInitialErrorPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PostsTabContent(
            state =
                SearchPostsState(
                    loadStatus =
                        SearchPostsLoadStatus.InitialError(
                            error = net.kikin.nubecita.feature.search.impl.SearchPostsError.Network,
                        ),
                    currentQuery = "kotlin",
                ),
            onEvent = {},
        )
    }
}

// Loaded previews require FeedItemUi.Single fixtures. The implementer
// reuses the same shape as searchPostFixture() in the test source, OR
// imports the existing :data:models preview helpers if they exist.
// Two Loaded variants worth previewing: ordinary Loaded, Loaded with
// isAppending=true (CircularProgressIndicator visible at tail).
```

For the `Loaded` previews, the implementer copies a couple `FeedItemUi.Single` literals into the preview function — verbose but acceptable; previews aren't reused across compilation units. If preview helpers like `:data:models/previewFixtures.kt` exist, prefer those.

- [ ] **Step 5: Re-compile after adding previews**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

- [ ] **Step 6: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/PostsTabContent.kt
git commit -m "feat(feature/search/impl): add poststabcontent stateless body

LazyColumn-based Loaded body with PostCard.bodyMatch highlighting,
sort row at the top, pagination trigger via snapshotFlow on the last
visible index, and the four other status branches. Stateless — no
hiltViewModel hoist.

Refs: nubecita-vrba.6"
```

---

## Task 15: Build `SearchPostsScreen` (stateful entry)

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../SearchPostsScreen.kt`

Stateful entry. Hoists `hiltViewModel<SearchPostsViewModel>()`. Takes `currentQuery: String` and `sort: SearchPostsSort` from vrba.8's glue (vrba.8 will pass parent's debounced query in). Wires `setQuery` via `LaunchedEffect`. Collects effects in a single `LaunchedEffect(Unit) { viewModel.effects.collect { ... } }`. Routes `NavigateToPost` to `LocalMainShellNavState.current.add(PostDetailRoute(uri))`. Hoists `NavigateToClearQuery` + `ShowAppendError` as callbacks for vrba.8 to wire.

- [ ] **Step 1: Create `SearchPostsScreen.kt`**

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.ui.PostsTabContent

/**
 * Stateful entry for the Posts tab. Hoists [SearchPostsViewModel],
 * wires the parent's debounced query via [LaunchedEffect], and routes
 * [SearchPostsEffect.NavigateToPost] to [LocalMainShellNavState] —
 * mirroring `:feature:chats:impl/ChatScreen`'s effect-collection
 * pattern.
 *
 * Two effects propagate via callback up to the (future) [vrba.8]
 * search-results screen:
 *  - [onClearQuery]: parent SearchViewModel owns the canonical
 *    TextFieldState and is the only thing that can reset it.
 *  - [onShowAppendError]: append-time failures surface as snackbars
 *    in the host's SnackbarHostState, which lives at the search-
 *    screen level (not inside the per-tab content).
 */
@Composable
internal fun SearchPostsScreen(
    currentQuery: String,
    initialSort: SearchPostsSort = SearchPostsSort.TOP,
    onClearQuery: () -> Unit,
    onShowAppendError: (SearchPostsError) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchPostsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentOnClearQuery by rememberUpdatedState(onClearQuery)
    val currentOnShowAppendError by rememberUpdatedState(onShowAppendError)

    // Push the latest debounced query down to the VM. The VM
    // dedupes via distinctUntilChanged on the FetchKey.
    LaunchedEffect(currentQuery) {
        viewModel.setQuery(currentQuery)
    }

    // initialSort is fired once on screen entry — the user's
    // subsequent SortClicked events drive sort changes from inside the
    // VM, so we don't re-LaunchedEffect on this past first composition.
    LaunchedEffect(Unit) {
        if (initialSort != SearchPostsSort.TOP) {
            viewModel.handleEvent(SearchPostsEvent.SortClicked(initialSort))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchPostsEffect.NavigateToPost ->
                    navState.add(PostDetailRoute(postUri = effect.uri))
                is SearchPostsEffect.ShowAppendError ->
                    currentOnShowAppendError(effect.error)
                SearchPostsEffect.NavigateToClearQuery ->
                    currentOnClearQuery()
            }
        }
    }

    PostsTabContent(
        state = state,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchPostsScreen.kt
git commit -m "feat(feature/search/impl): add stateful searchpostsscreen

Hoists SearchPostsViewModel, pushes parent's debounced query via
LaunchedEffect, collects effects in a single LaunchedEffect,
routes NavigateToPost to LocalMainShellNavState and hoists
NavigateToClearQuery + ShowAppendError as callbacks for vrba.8.

Refs: nubecita-vrba.6"
```

---

## Task 16: Add screenshot tests for the new composables

**Files:**
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/PostsLoadingBodyScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/PostsEmptyBodyScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/PostsInitialErrorBodyScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/PostsSortRowScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/PostsTabContentScreenshotTest.kt`

Mirror the pattern from `feature/search/impl/src/screenshotTest/kotlin/.../ui/SearchInputRowScreenshotTest.kt` (already read above). One `@PreviewTest` per status variant, stacked light + dark `@Preview`s on each.

- [ ] **Step 1: Write each screenshot test file**

Follow the existing pattern exactly. Example for the sort row:

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

@PreviewTest
@Preview(name = "posts-sort-row-top-light", showBackground = true)
@Preview(name = "posts-sort-row-top-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsSortRowTopScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsSortRow(activeSort = SearchPostsSort.TOP, onSortSelected = {})
        }
    }
}

@PreviewTest
@Preview(name = "posts-sort-row-latest-light", showBackground = true)
@Preview(name = "posts-sort-row-latest-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsSortRowLatestScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsSortRow(activeSort = SearchPostsSort.LATEST, onSortSelected = {})
        }
    }
}
```

Repeat the same shape for:

- `PostsLoadingBodyScreenshotTest.kt` — one `@PreviewTest` × light/dark.
- `PostsEmptyBodyScreenshotTest.kt` — two `@PreviewTest`s: TOP active light/dark, LATEST active light/dark.
- `PostsInitialErrorBodyScreenshotTest.kt` — three `@PreviewTest`s: Network, RateLimited, Unknown × light/dark.
- `PostsTabContentScreenshotTest.kt` — five `@PreviewTest`s: InitialLoading, Empty, Loaded (1-3 cards w/ highlight), Loaded + isAppending, InitialError × light/dark.

For `PostsTabContentScreenshotTest`'s Loaded variants, build a couple of `FeedItemUi.Single` literals inline (same as the preview function in PostsTabContent). Use a query that matches the body text so the highlight overlay is exercised.

- [ ] **Step 2: Generate baselines**

```bash
./gradlew :feature:search:impl:updateDebugScreenshotTest
./gradlew :feature:search:impl:validateDebugScreenshotTest
```

Expected: new baselines under `feature/search/impl/src/screenshotTest/reference/`. Roughly 22 PNG files (12 from the variant counts × 2 light/dark each + a couple extras).

- [ ] **Step 3: Commit baselines + screenshot test files**

```bash
git add feature/search/impl/src/screenshotTest/
git commit -m "test(feature/search/impl): add screenshot baselines for posts tab ui

Loading / Empty / InitialError variants × light/dark; sort row; full
PostsTabContent across status variants including Loaded with body
highlights.

Refs: nubecita-vrba.6"
```

---

## Task 17: Final verification (full build + tests + verify nothing else regressed)

- [ ] **Step 1: Run the full module + designsystem test suites**

```bash
./gradlew :feature:search:impl:testDebugUnitTest \
          :feature:search:impl:validateDebugScreenshotTest \
          :designsystem:testDebugUnitTest \
          :designsystem:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run repository-wide spotless + lint**

```bash
./gradlew :feature:search:impl:spotlessCheck \
          :feature:search:impl:lintDebug \
          :designsystem:spotlessCheck \
          :designsystem:lintDebug
```

Expected: BUILD SUCCESSFUL. Fix any auto-format issues with `./gradlew spotlessApply` and re-commit if needed.

- [ ] **Step 3: Run feed unit tests + screenshot tests to confirm no regression from the PostCard.bodyMatch change**

```bash
./gradlew :feature:feed:impl:testDebugUnitTest :feature:feed:impl:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL. `bodyMatch` defaulting to null means feed callers see no change.

- [ ] **Step 4: Run the app's debug assembly to confirm the whole graph builds**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. This catches any DI graph issues — `SearchPostsViewModel` is a `@HiltViewModel` and Hilt will fail-fast at app assembly if the graph can't resolve `SearchPostsRepository`.

- [ ] **Step 5: Run pre-commit hooks against the full diff**

```bash
pre-commit run --all-files
```

Expected: PASS. If commitlint complains about the commit subjects, fix the offending commits via `git commit --amend` or `git rebase -i HEAD~N` (NEVER `--no-verify`).

- [ ] **Step 6: Push the branch and open the PR**

Try SSH first; if it fails with a signing error, fall back to the HTTPS-insteadof override per the project's git memory:

```bash
git push -u origin HEAD || \
  git -c "url.https://github.com/.insteadOf=git@github.com:" push -u origin HEAD
```

Then open the PR (use the gh CLI with a heredoc body per the project convention). The PR body should include `Closes: nubecita-vrba.6` (NOT in commit messages — squash-merge would double-close). Do NOT add the `run-instrumented` label — vrba.6 ships only unit + screenshot tests, no `src/androidTest/` files. Verify with `git diff --name-only main...HEAD | grep androidTest` returning empty.

PR title (Conventional Commits, all lowercase per commitlint):

```
feat(feature/search/impl): add searchpostsviewmodel + posts tab ui
```

PR body skeleton:

```
## Summary
- Adds SearchPostsViewModel + Posts tab UI for the search-results screen
  (vrba.8 will host this composable next).
- Adds a sort param (SearchPostsSort: TOP / LATEST) to SearchPostsRepository
  and a HighlightedText utility in :designsystem.
- Extends :designsystem/PostCard with an optional bodyMatch param to
  highlight query substrings in post bodies (default null preserves
  feed rendering).

## Spec compliance
Implements all 10 decisions in
docs/superpowers/specs/2026-05-16-search-posts-viewmodel-and-tab-design.md.

## Test plan
- [ ] :feature:search:impl:testDebugUnitTest — 12 new SearchPostsViewModelTest
      cases + 3 SearchPostsErrorTest + 2 new sort-passthrough cases in
      DefaultSearchPostsRepositoryTest.
- [ ] :designsystem:testDebugUnitTest — green (no behavior change).
- [ ] :feature:search:impl:validateDebugScreenshotTest + designsystem screenshot
      baselines added for HighlightedText × 3 + posts UI bodies.
- [ ] :feature:feed:impl:validateDebugScreenshotTest — green (PostCard
      bodyMatch defaults to null; no visual change in the feed).
- [ ] :app:assembleDebug — graph compiles (Hilt resolves SearchPostsViewModel).

Closes: nubecita-vrba.6
```

---

## Self-review checklist (run before opening the PR)

After finishing Task 17, run through the spec one more time:

- **D1** (mapLatest pipeline) — covered by Task 6 (VM pipeline + cancellation test). ✓
- **D2** (sealed SearchPostsLoadStatus) — covered by Task 5 (contract). ✓
- **D3** (SearchPostsError + extension) — covered by Task 4 + 3 unit tests. ✓
- **D4** (LoadMore single-flight + idempotent endReached) — covered by Task 7 with 4 unit tests covering the guard order. ✓
- **D5** (Sort row + repo extension) — covered by Task 1 (data layer) + Task 13 (UI) + Task 8 (SortClicked handler). ✓
- **D6** (HighlightedText) — covered by Task 2 (composable + helper) + Task 3 (PostCard integration). ✓
- **D7** (reuse PostCard, additive bodyMatch) — Task 3. Risk noted: spec called out body rendering might be split across multiple composables; the implementer should report `DONE_WITH_CONCERNS` if a deeper integration is needed beyond `BodyText`.
- **D8** (PostsTabContent signature + SearchPostsScreen hoist) — Tasks 14 + 15. ✓
- **D9** (NavigateToPost effect → LocalMainShellNavState) — Task 15. ✓
- **D10** (rich empty-state with Clear / Change-sort buttons) — Task 11. ✓
- **Unit test coverage** — 12 SearchPostsViewModelTest cases per the spec's enumeration. Two spec cases deferred and reworded:
  - `loadMore_loaded_appendsNextPage_emitsIsAppendingTransient` — replaced by `loadMore_loaded_appendsNextPage_andClearsIsAppending` (the spec's "transient" wording was unclear; we verify both that isAppending was true during the load and false after).
  - `setQuery_rapidChange_cancelsPrior_mapLatestSemantic` — same idea, named `setQuery_rapidChange_cancelsPrior_viaMapLatest`.
- **Screenshot test coverage** — all listed previews × light/dark in Task 16. Roughly 12 baselines per the spec estimate (we'll hit ~16-22 with PostsTabContent variants + sort row + error variants).

---

## Risk + rollback (mirror of spec's "Risk + rollback")

- **`PostCard.BodyText`'s body rendering might be split across multiple composables.** The actual file shows `BodyText` is a single composable that builds an `AnnotatedString` via `rememberBlueskyAnnotatedString` then renders via one `Text`. Merging match-highlight spans on top is straightforward (Task 3). If a future refactor splits `BodyText` (e.g., per-hashtag or per-mention wrapping), the `bodyMatch` parameter needs to thread through the new sub-composables. The default-null contract means rolling back is just removing the parameter without affecting feed renders.
- **atproto-kotlin's 429 / RateLimited exception shape unknown.** `Throwable.toSearchPostsError()`'s `RateLimited` branch is best-effort; defaults to `Unknown` until the SDK surface is verified. Task 4 documents this in the file's KDoc — when an upstream SDK release lands a typed exception, file a follow-up to extend the `when`.
- **Rollback strategy:** The PR is independently revertible. The `PostCard.bodyMatch` extension is additive (default null); reverting search doesn't break feed. The data-layer extension (`sort` param) defaults to `TOP`, byte-identical to pre-PR call sites.

---

## Out of scope (per spec, restated for the implementer)

- Typeahead screen (vrba.10).
- Feeds tab (vrba.11).
- Adaptive tablet/foldable layouts.
- `Filters` chip beyond Top/Latest.
- Custom cloud illustration.
- `Refreshing` lifecycle state — search has no pull-to-refresh; query change is the implicit refresh.
- Tap-through to PostDetail's actual rendering — vrba.9 wires the full nav; vrba.6 only emits the effect (and Task 15 routes it to `LocalMainShellNavState`).
- `hitsTotal` on `Loaded` — defer until vrba.8's tab badge needs it.
- `NavigateToChangeSort` effect — spec listed it but no handler emits it; dropped per Task 5's deviation note.
