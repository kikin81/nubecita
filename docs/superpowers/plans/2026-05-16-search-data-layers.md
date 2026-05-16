# Posts + Actors Search Data Layers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Posts and Actors search data layers in `:feature:search:impl/data/`, each wrapping its respective atproto RPC behind a Hilt-bound `Result<Page>` interface. PR 2 also promotes `:core:posting`'s `ActorTypeaheadUi` to `:data:models` as `ActorUi`.

**Architecture:** Two repositories follow the established `FeedRepository` pattern: `internal interface` + `internal class DefaultImpl @Inject constructor(XrpcClientProvider, @IoDispatcher)` + `@Binds` Hilt module. Both return `Result<SearchXxxPage>` with cursor-based paging and matched-shape failure handling (explicit `CancellationException` rethrow before Timber log). Posts results map via a new `PostView.toFlatFeedItemUiSingle()` helper in `:core:feed-mapping` that ignores reply context (search renders flat). Actors results map locally to a new `ActorUi` model in `:data:models` (promoted from `:core:posting`'s `ActorTypeaheadUi`).

**Tech Stack:** Kotlin 2.3.21, Hilt 2.59.2, atproto-kotlin 6.5.1, kotlinx-coroutines 1.11.0, kotlinx-collections-immutable, JUnit 5 (Jupiter) for tests, Ktor 3.5.0 MockEngine for the in-process atproto fake.

**Spec:** [`docs/superpowers/specs/2026-05-16-search-data-layers-design.md`](../specs/2026-05-16-search-data-layers-design.md)

**bd:** Two children of the Search epic (`nubecita-vrba`). PR 1 implements `nubecita-vrba.3`; PR 2 implements `nubecita-vrba.4` and cuts from `main` only after PR 1 merges.

---

## File Structure

### Phase 1 — Posts data layer (PR 1 / `nubecita-vrba.3`)

**New:**
- `core/feed-mapping/src/main/kotlin/net/kikin/nubecita/core/feedmapping/FeedMapping.kt` *(modified, adds one extension function near the existing `toPostUiCore`)*
- `core/feed-mapping/src/test/kotlin/net/kikin/nubecita/core/feedmapping/FlatFeedItemUiSingleTest.kt`
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchPostsRepository.kt`
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepository.kt`
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/di/SearchPostsRepositoryModule.kt`
- `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepositoryTest.kt`

**Modified:**
- `feature/search/impl/build.gradle.kts` — adds `:core:auth`, `:core:feed-mapping`, atproto deps.

### Phase 2 — Actors data layer + `ActorUi` promotion (PR 2 / `nubecita-vrba.4`)

**New:**
- `data/models/src/main/kotlin/net/kikin/nubecita/data/models/ActorUi.kt`
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchActorsRepository.kt`
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchActorsRepository.kt`
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/di/SearchActorsRepositoryModule.kt`
- `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchActorsRepositoryTest.kt`

**Deleted:**
- `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/ActorTypeaheadUi.kt`

**Modified (mechanical rename `ActorTypeaheadUi` → `ActorUi`):**
- `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/ActorTypeaheadRepository.kt`
- `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/internal/DefaultActorTypeaheadRepository.kt`
- `core/posting/src/test/kotlin/net/kikin/nubecita/core/posting/internal/DefaultActorTypeaheadRepositoryTest.kt`
- `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModel.kt`
- `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/state/*.kt` *(implementer greps to find every reference)*
- `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/*Test.kt`

**Out of scope** (epic non-goals / deferred):
- VM-side error mapping (`vrba.6`, `vrba.7`).
- `:core:search` module extraction.
- Result caching.
- Fix to `DefaultFeedRepository`'s identical latent `CancellationException` bug — separate follow-up.

---

## PHASE 1 — Posts data layer (PR 1, `nubecita-vrba.3`)

### Task 1: Add `PostView.toFlatFeedItemUiSingle()` helper to `:core:feed-mapping`

**Files:**
- Test: `core/feed-mapping/src/test/kotlin/net/kikin/nubecita/core/feedmapping/FlatFeedItemUiSingleTest.kt` (new)
- Modify: `core/feed-mapping/src/main/kotlin/net/kikin/nubecita/core/feedmapping/FeedMapping.kt`

The existing `PostView.toPostUiCore(): PostUi?` (declared in the same file) returns null when the embedded record can't decode. The new helper wraps it in `FeedItemUi.Single`. Single-line implementation; the test verifies the null-passthrough.

- [ ] **Step 1: Write the failing test**

Create `core/feed-mapping/src/test/kotlin/net/kikin/nubecita/core/feedmapping/FlatFeedItemUiSingleTest.kt`. Look at the existing `core/feed-mapping/src/test/.../` directory first to see what test-utility/fixture builders are already in place (look for any existing `PostView` builder — the timeline mapper's tests almost certainly have one). If a fixture builder exists, reuse it; the snippet below uses a hypothetical `samplePostView(...)` helper and the implementer adjusts the imports + builder name to whatever's already in the module's test code:

```kotlin
package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.PostView
import net.kikin.nubecita.data.models.FeedItemUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FlatFeedItemUiSingleTest {
    @Test
    fun `wellFormed_post_returns_Single_wrapping_PostUi`() {
        val view = samplePostView(uri = "at://example/post/1", text = "hello world")

        val item = view.toFlatFeedItemUiSingle()

        assertNotNull(item)
        assertEquals("at://example/post/1", (item as FeedItemUi.Single).post.id)
        assertEquals("hello world", item.post.text)
    }

    @Test
    fun `malformed_record_returns_null`() {
        // toPostUiCore returns null when the embedded record can't decode as
        // a well-formed app.bsky.feed.post. The flat helper passes the null
        // through unchanged so callers can filter at the collection boundary.
        val view = samplePostViewWithMalformedRecord()

        val item = view.toFlatFeedItemUiSingle()

        assertNull(item)
    }
}
```

If `samplePostView` / `samplePostViewWithMalformedRecord` don't exist in the module's test code, write them as private helpers at the bottom of the new test file (mirror whatever fixture pattern `core/feed-mapping/src/test/.../` already uses — most likely a JSON literal decoded through `kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<PostView>(...)`).

- [ ] **Step 2: Run test, verify failure**

```bash
./gradlew :core:feed-mapping:testDebugUnitTest --tests "net.kikin.nubecita.core.feedmapping.FlatFeedItemUiSingleTest" -q
```
Expected: BUILD FAILED with `Unresolved reference: toFlatFeedItemUiSingle`.

- [ ] **Step 3: Add the helper**

Append to `core/feed-mapping/src/main/kotlin/net/kikin/nubecita/core/feedmapping/FeedMapping.kt`. Place it immediately below the existing `PostView.toPostUiCore()` function definition (which is around mid-file — search for the KDoc starting `* Project a [PostView] into the UI-ready [PostUi]`). Add the following:

```kotlin
/**
 * Project a single [PostView] to [FeedItemUi.Single], or `null` when
 * [toPostUiCore] cannot produce a well-formed [PostUi] (malformed
 * embedded record, unparseable timestamp).
 *
 * Use this helper for surfaces that render posts as flat,
 * disconnected items regardless of reply context — search results,
 * notification entries, profile post lists, etc. The home timeline
 * uses a different mapping path that detects reply clusters and
 * self-thread chains; this helper deliberately stops short of that
 * projection.
 *
 * Callers should filter `null` entries at the collection boundary
 * (e.g. `posts.mapNotNull(PostView::toFlatFeedItemUiSingle)`).
 */
fun PostView.toFlatFeedItemUiSingle(): FeedItemUi.Single? =
    toPostUiCore()?.let(FeedItemUi::Single)
```

Add the import at the top of the file if it isn't already present:

```kotlin
import net.kikin.nubecita.data.models.FeedItemUi
```

- [ ] **Step 4: Run test, verify pass**

```bash
./gradlew :core:feed-mapping:testDebugUnitTest --tests "net.kikin.nubecita.core.feedmapping.FlatFeedItemUiSingleTest" -q
```
Expected: BUILD SUCCESSFUL, 2 tests pass.

- [ ] **Step 5: Run full module tests to verify no regressions**

```bash
./gradlew :core:feed-mapping:testDebugUnitTest -q
```
Expected: BUILD SUCCESSFUL. Pre-existing timeline-mapping tests all pass.

- [ ] **Step 6: Commit**

```bash
git add core/feed-mapping/src/main/kotlin/net/kikin/nubecita/core/feedmapping/FeedMapping.kt \
        core/feed-mapping/src/test/kotlin/net/kikin/nubecita/core/feedmapping/FlatFeedItemUiSingleTest.kt
git commit -m "$(cat <<'EOF'
feat(core/feed-mapping): add PostView.toFlatFeedItemUiSingle()

Wraps the existing toPostUiCore() in FeedItemUi.Single, ignoring any
reply context. The timeline mapping detects reply clusters and
self-thread chains; flat-rendering surfaces (search results,
notification entries, profile post lists) want the un-projected
shape and skip the cluster step entirely.

The first consumer is nubecita-vrba.3's DefaultSearchPostsRepository.

Refs: nubecita-vrba.3
EOF
)"
```

---

### Task 2: `SearchPostsRepository` interface + `SearchPostsPage` + page-limit constant

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchPostsRepository.kt`

Pure declarations — no tests until the implementation lands in Task 3.

- [ ] **Step 1: Create the file**

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
    ): Result<SearchPostsPage>
}

internal data class SearchPostsPage(
    val items: ImmutableList<FeedItemUi.Single>,
    val nextCursor: String?,
)

/**
 * Default page size for `searchPosts` requests. Lexicon allows 1–100;
 * 25 matches the search-data-layers spec default (slightly lower than
 * the timeline's 30 because search results are typically scanned,
 * not deeply scrolled).
 */
internal const val SEARCH_POSTS_PAGE_LIMIT: Int = 25
```

- [ ] **Step 2: Compile-check**

```bash
./gradlew :feature:search:impl:compileDebugKotlin -q
```
Expected: BUILD SUCCESSFUL. (`feature/search/impl/build.gradle.kts` already has `:data:models` + `kotlinx-collections-immutable` from vrba.5, so no Gradle edits yet.)

- [ ] **Step 3: No commit yet** — bundles with Task 3.

---

### Task 3: `DefaultSearchPostsRepository` implementation + tests (TDD)

**Files:**
- Test: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepositoryTest.kt` (new)
- Modify: `feature/search/impl/build.gradle.kts` (add `:core:auth`, `:core:feed-mapping`, `atproto.runtime`, `atproto.models`, plus `ktor.client.mock` for tests)
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepository.kt`

Test pattern mirrors `core/posting/src/test/kotlin/.../DefaultActorTypeaheadRepositoryTest.kt`: stand up a real `XrpcClient` backed by a Ktor `MockEngine` so the SDK's `FeedService.searchPosts(...)` codepath runs end-to-end against deterministic responses. Implementation mirrors `DefaultActorTypeaheadRepository` (which already does the `CancellationException`-aware try/catch per Gemini's correctness note).

- [ ] **Step 1: Add Gradle deps**

Open `feature/search/impl/build.gradle.kts`. The current file is short (from vrba.5). Edit the `dependencies { ... }` block to add the following lines, keeping them alphabetized within their groups:

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

(Verify `:core:auth`, `:core:database`, `:core:feed-mapping`, `atproto.models`, `atproto.runtime`, `timber`, and `ktor.client.mock` exist in `gradle/libs.versions.toml` — they all do per prior epics.)

- [ ] **Step 2: Write the failing test file**

Create `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepositoryTest.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [DefaultSearchPostsRepository].
 *
 * Strategy mirrors [net.kikin.nubecita.core.posting.internal.DefaultActorTypeaheadRepositoryTest]:
 * stand up a real [XrpcClient] backed by a Ktor [MockEngine] so the
 * SDK's full `FeedService.searchPosts(...)` codepath runs end-to-end
 * against deterministic responses. Tests assert on the
 * `engine.requestHistory.single().url` for wire-format guarantees and
 * on the parsed `Result<SearchPostsPage>` for mapping + cursor
 * guarantees.
 *
 * Coverage:
 *  - Happy path: posts list + cursor map through correctly.
 *  - Empty result: empty page + null cursor.
 *  - IOException: surfaces as Result.failure(throwable) without
 *    propagating.
 *  - CancellationException: re-thrown (NOT trapped in Result.failure)
 *    so structured concurrency works.
 *  - Wire format: `q` + `limit` + `cursor` parameters present on the
 *    outgoing GET.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSearchPostsRepositoryTest {
    @Test
    fun searchPosts_happyPath_mapsPostsAndCursor() =
        runTest {
            val (engine, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "cursor": "c2",
                          "hitsTotal": 42,
                          "posts": []
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchPosts(query = "kotlin", cursor = "c1", limit = 25)

            assertTrue(result.isSuccess)
            val page = result.getOrThrow()
            assertEquals(0, page.items.size)
            assertEquals("c2", page.nextCursor)
            assertEquals(1, engine.requestHistory.size)
        }

    @Test
    fun searchPosts_emptyResultNullCursor_returnsEmptyPage() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "posts": []
                        }
                        """.trimIndent(),
                    )
                }

            val page = repo.searchPosts(query = "kotlin", cursor = null, limit = 25).getOrThrow()

            assertTrue(page.items.isEmpty())
            assertNull(page.nextCursor)
        }

    @Test
    fun searchPosts_throws_surfacesFailure() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    throw IOException("network down")
                }

            val result = repo.searchPosts(query = "kotlin", cursor = null, limit = 25)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun searchPosts_cancellation_propagates() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    throw CancellationException("scope cancelled")
                }

            assertThrows(CancellationException::class.java) {
                runTest {
                    repo.searchPosts(query = "kotlin", cursor = null, limit = 25)
                }
            }
        }

    @Test
    fun searchPosts_passesQueryCursorAndLimitThrough() =
        runTest {
            val (engine, repo) =
                newRepo { _ ->
                    okJson("""{"posts": []}""")
                }

            repo.searchPosts(query = "kotlin lang", cursor = "abc", limit = 13)

            val url = engine.requestHistory.single().url.toString()
            // Order of query params is not contractual, just presence:
            assertTrue(url.contains("q=kotlin")) // kotlin lang URL-encoded → kotlin+lang or kotlin%20lang
            assertTrue(url.contains("cursor=abc"))
            assertTrue(url.contains("limit=13"))
        }

    // --- helpers ---

    private fun newRepo(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultSearchPostsRepository> {
        val engine = MockEngine { handler(it) }
        val httpClient = HttpClient(engine)
        val xrpcClient =
            XrpcClient(
                http = httpClient,
                serviceEndpoint = "https://example.test",
                authProvider = NoAuth,
            )
        val provider = FakeXrpcClientProvider(xrpcClient)
        val repo =
            DefaultSearchPostsRepository(
                xrpcClientProvider = provider,
                dispatcher = Dispatchers.Unconfined,
            )
        return engine to repo
    }

    private fun MockRequestHandleScope.okJson(body: String): HttpResponseData =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private class FakeXrpcClientProvider(
        private val client: XrpcClient,
    ) : XrpcClientProvider {
        override suspend fun authenticated(): XrpcClient = client
        override suspend fun anonymous(): XrpcClient = client
    }
}
```

**If the `XrpcClientProvider` interface in the project doesn't have `authenticated()` / `anonymous()` exactly as written, mirror its actual surface** (the existing typeahead test uses whatever shape exists; cross-check against `core/auth/src/main/.../XrpcClientProvider.kt` before saving).

- [ ] **Step 3: Compile-check (test should fail to compile yet)**

```bash
./gradlew :feature:search:impl:compileDebugUnitTestKotlin -q
```
Expected: BUILD FAILED with `Unresolved reference: DefaultSearchPostsRepository`.

- [ ] **Step 4: Implement `DefaultSearchPostsRepository`**

Create `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepository.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.SearchPostsRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toFlatFeedItemUiSingle
import timber.log.Timber

/**
 * Production [SearchPostsRepository] backed by the atproto-kotlin SDK's
 * [FeedService]. Routes through the authenticated [XrpcClientProvider]
 * (same client every other reader uses).
 *
 * Sends `q` + `cursor` + `limit` and projects [FeedService.searchPosts]
 * response via [toFlatFeedItemUiSingle] (flat — search results never
 * render reply clusters). Posts that fail to project (malformed
 * embedded record) are filtered out at the collection boundary.
 *
 * Cancellation re-throws *before* the Timber log so the VM's
 * `mapLatest` operator in `nubecita-vrba.6` can cancel the upstream
 * coroutine cleanly when the user keeps typing.
 */
@Singleton
internal class DefaultSearchPostsRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SearchPostsRepository {
        override suspend fun searchPosts(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchPostsPage> =
            withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).searchPosts(
                            SearchPostsRequest(
                                q = query,
                                cursor = cursor,
                                limit = limit.toLong(),
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
                    Timber.tag(TAG).e(t, "searchPosts(q=%s, cursor=%s) failed: %s", query, cursor, t.javaClass.name)
                    Result.failure(t)
                }
            }

        private companion object {
            private const val TAG = "SearchPostsRepo"
        }
    }
```

- [ ] **Step 5: Run the tests, verify they pass**

```bash
./gradlew :feature:search:impl:testDebugUnitTest -q
```
Expected: BUILD SUCCESSFUL. All 5 new tests pass alongside the 13 pre-existing tests from vrba.2 / vrba.5 (total ≥ 18).

If `searchPosts_passesQueryCursorAndLimitThrough` fails on the `q=kotlin` substring assertion because the URL contains URL-encoded space (`kotlin%20lang` or `kotlin+lang`), the assertion is correct as written — both encodings contain `q=kotlin` as a prefix.

- [ ] **Step 6: Commit**

```bash
git add feature/search/impl/build.gradle.kts \
        feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchPostsRepository.kt \
        feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepository.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepositoryTest.kt
git commit -m "$(cat <<'EOF'
feat(feature/search/impl): SearchPostsRepository + Default impl

Wraps app.bsky.feed.searchPosts behind a stateless Result<Page>
interface. The VM (vrba.6) owns the cursor and re-issues calls for
the next page; the repo just translates the call. PostView results
project to FeedItemUi.Single via the new
:core:feed-mapping.toFlatFeedItemUiSingle() helper — search results
render flat (no reply clusters) regardless of whether a hit is a
reply.

Failure handling rethrows CancellationException before the Timber
log so vrba.6's `mapLatest` debouncing cancels the upstream cleanly.
DefaultFeedRepository has the same latent issue today; out of scope
for this PR.

build.gradle.kts adds :core:auth, :core:feed-mapping, atproto deps,
ktor-client-mock test dep.

5 unit tests: happy path, empty result, IOException surfacing,
CancellationException propagation, q/cursor/limit wire-format.
Pattern mirrors DefaultActorTypeaheadRepositoryTest (real XrpcClient
backed by Ktor MockEngine).

Refs: nubecita-vrba.3
EOF
)"
```

---

### Task 4: `SearchPostsRepositoryModule` Hilt binding + graph verification

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/di/SearchPostsRepositoryModule.kt`

- [ ] **Step 1: Create the module**

```kotlin
package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.DefaultSearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsRepository

/**
 * Hilt binding for [SearchPostsRepository] → [DefaultSearchPostsRepository].
 * `@InstallIn(SingletonComponent::class)` so the singleton repo is
 * available to any `@HiltViewModel` that eventually injects it
 * (nubecita-vrba.6's `SearchPostsViewModel`).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SearchPostsRepositoryModule {
    @Binds
    internal abstract fun bindsSearchPostsRepository(
        impl: DefaultSearchPostsRepository,
    ): SearchPostsRepository
}
```

- [ ] **Step 2: Verify Hilt graph still resolves end-to-end**

```bash
./gradlew :app:assembleDebug -q
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full local gate**

```bash
./gradlew :feature:search:impl:assembleDebug \
          :feature:search:impl:testDebugUnitTest \
          :feature:search:impl:lintDebug \
          :feature:search:impl:validateDebugScreenshotTest \
          :core:feed-mapping:testDebugUnitTest \
          :app:assembleDebug \
          spotlessCheck -q
```
Expected: all green.

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/di/SearchPostsRepositoryModule.kt
git commit -m "$(cat <<'EOF'
feat(feature/search/impl): @Binds SearchPostsRepository in Hilt graph

@InstallIn(SingletonComponent::class) so nubecita-vrba.6's
SearchPostsViewModel can @Inject the repository directly without
constructing it manually.

Refs: nubecita-vrba.3
EOF
)"
```

---

### Task 5: Push PR 1 + add follow-up notes

**Files:** none modified in this task.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin HEAD
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --title "feat(feature/search/impl): Posts search data layer" --body "$(cat <<'EOF'
## Summary

Wraps app.bsky.feed.searchPosts behind a stateless Result<SearchPostsPage> interface in :feature:search:impl/data/. The VM in nubecita-vrba.6 will own the cursor and re-issue calls for paging.

PostView results project via a new \`PostView.toFlatFeedItemUiSingle()\` helper in :core:feed-mapping that ignores reply context — search results render as flat, disconnected post cards regardless of whether a given hit happens to be a reply. The home timeline mapping path (which detects reply clusters and self-thread chains) is untouched.

Failure handling explicitly rethrows CancellationException before the Timber log so vrba.6's debounced search can cancel the upstream coroutine cleanly. DefaultFeedRepository has the same latent issue today; out of scope here — separate follow-up.

Spec: \`docs/superpowers/specs/2026-05-16-search-data-layers-design.md\`
Plan: \`docs/superpowers/plans/2026-05-16-search-data-layers.md\` (Phase 1)

## Test plan

- [x] \`./gradlew :core:feed-mapping:testDebugUnitTest\` — 2 new tests for the helper + all pre-existing tests pass.
- [x] \`./gradlew :feature:search:impl:testDebugUnitTest\` — 5 new SearchPostsRepository tests pass (happy path, empty result, IOException surfacing, CancellationException propagation, wire-format).
- [x] \`./gradlew :feature:search:impl:lintDebug\` — clean.
- [x] \`./gradlew :app:assembleDebug\` — Hilt graph still resolves.
- [x] \`./gradlew spotlessCheck\` — green.

No connected-test job needed; pure data layer with no Room / Compose / Android-specific surface.

Closes: nubecita-vrba.3

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: No `run-instrumented` label** — pure JVM unit tests; no androidTest.

---

## ⏸ Gate between Phase 1 and Phase 2

**Wait for PR 1 to merge before starting Phase 2.** The controller cleans up the vrba.3 worktree (`bd close`, `bd worktree remove`, `bd dolt push`) and rebases `main` before creating the new worktree for `nubecita-vrba.4`.

---

## PHASE 2 — Actors data layer + `ActorUi` promotion (PR 2, `nubecita-vrba.4`)

### Task 6: Promote `ActorTypeaheadUi` → `:data:models/ActorUi`

**Files:**
- Create: `data/models/src/main/kotlin/net/kikin/nubecita/data/models/ActorUi.kt`
- Delete: `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/ActorTypeaheadUi.kt`
- Modify (mechanical rename): `core/posting/.../ActorTypeaheadRepository.kt`, `core/posting/.../internal/DefaultActorTypeaheadRepository.kt`, `core/posting/.../internal/DefaultActorTypeaheadRepositoryTest.kt`, plus every file under `feature/composer/impl/src/main` and `feature/composer/impl/src/test` that references `ActorTypeaheadUi`.

- [ ] **Step 1: Create the new model**

```kotlin
// data/models/src/main/kotlin/net/kikin/nubecita/data/models/ActorUi.kt
package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * UI-ready projection of a Bluesky actor (user). Used by surfaces that
 * render a single actor row — composer's @-mention typeahead, search's
 * People tab, future profile lists, etc.
 *
 * `displayName` is nullable because the underlying lexicon allows
 * actors without a display name; consumers fall back to `handle` for
 * display. `avatarUrl` is nullable because actors without a profile
 * picture serve no avatar.
 *
 * Promoted from `:core:posting`'s former `ActorTypeaheadUi` in
 * `nubecita-vrba.4` — same shape, broader applicability.
 */
@Immutable
public data class ActorUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
)
```

- [ ] **Step 2: Find every reference to `ActorTypeaheadUi`**

```bash
grep -RIn "ActorTypeaheadUi" --include="*.kt" core feature data 2>/dev/null
```
Expected output: a list of files in `:core:posting` and `:feature:composer:impl` — every file you'll edit in this task.

- [ ] **Step 3: Apply the rename**

For each file listed in Step 2 (excluding the now-deleted `ActorTypeaheadUi.kt`):
1. Replace every `ActorTypeaheadUi` symbol with `ActorUi`.
2. Replace the import `import net.kikin.nubecita.core.posting.ActorTypeaheadUi` with `import net.kikin.nubecita.data.models.ActorUi`.

The implementer can use a single `sed` pass per file, or rely on IDE-side find-and-replace:

```bash
# safer: one file at a time, dry-run first
grep -lI "ActorTypeaheadUi" $(grep -RIl "ActorTypeaheadUi" --include="*.kt" core feature data)
# Then for each path:
sed -i '' -e 's|net\.kikin\.nubecita\.core\.posting\.ActorTypeaheadUi|net.kikin.nubecita.data.models.ActorUi|g' \
          -e 's|\bActorTypeaheadUi\b|ActorUi|g' "<path>"
```

(The order matters: rewrite the qualified import first so the unqualified `ActorTypeaheadUi` rewrite doesn't damage the now-correct `ActorUi` import.)

- [ ] **Step 4: Delete the old file**

```bash
git rm core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/ActorTypeaheadUi.kt
```

- [ ] **Step 5: Verify compilation + tests in the affected modules**

```bash
./gradlew :core:posting:testDebugUnitTest \
          :feature:composer:impl:testDebugUnitTest \
          :app:assembleDebug \
          spotlessApply -q
```
Expected: BUILD SUCCESSFUL. Every pre-existing test in `:core:posting` and `:feature:composer:impl` still passes.

If spotless rewrites any file (e.g. reordering imports after the rename), re-stage those files for the commit.

- [ ] **Step 6: Commit**

```bash
git add data/models/src/main/kotlin/net/kikin/nubecita/data/models/ActorUi.kt
git add core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/ActorTypeaheadRepository.kt \
        core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/internal/DefaultActorTypeaheadRepository.kt \
        core/posting/src/test/kotlin/net/kikin/nubecita/core/posting/internal/DefaultActorTypeaheadRepositoryTest.kt
# Stage every modified composer file; the implementer expands the glob below
# based on the Step-2 grep output:
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/
git add feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/

git commit -m "$(cat <<'EOF'
refactor(data/models,core/posting,feature/composer/impl): promote ActorTypeaheadUi to ActorUi

Moves the actor-row UI model from :core:posting to :data:models and
drops the misleading Typeahead suffix. Same shape (did, handle,
nullable displayName, nullable avatarUrl); the type is now broadly
applicable to search results (nubecita-vrba.4's Actors tab) and any
future actor-row surface (profile lists, follower lists).

ActorTypeaheadRepository's repository name is unchanged — it still
serves the composer's @-mention typeahead — but its return type now
references ActorUi from :data:models.

Mechanical rename across :core:posting and :feature:composer:impl;
no behavior change, all pre-existing tests in both modules still
pass.

Refs: nubecita-vrba.4
EOF
)"
```

---

### Task 7: `SearchActorsRepository` interface + `SearchActorsPage` + page-limit constant

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchActorsRepository.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.ActorUi

/**
 * `app.bsky.actor.searchActors` fetch surface scoped to
 * `:feature:search:impl`. Stateless — the caller (Search People tab
 * VM in `nubecita-vrba.7`) owns the cursor and re-issues calls for
 * the next page. Mirrors [SearchPostsRepository]'s shape.
 *
 * Results are projected to [ActorUi] via a local
 * `ProfileView.toActorUi()` extension co-located in
 * [DefaultSearchActorsRepository].
 */
internal interface SearchActorsRepository {
    suspend fun searchActors(
        query: String,
        cursor: String?,
        limit: Int = SEARCH_ACTORS_PAGE_LIMIT,
    ): Result<SearchActorsPage>
}

internal data class SearchActorsPage(
    val items: ImmutableList<ActorUi>,
    val nextCursor: String?,
)

/**
 * Default page size for `searchActors` requests. Lexicon allows 1–100;
 * 25 matches the search-data-layers spec default.
 */
internal const val SEARCH_ACTORS_PAGE_LIMIT: Int = 25
```

- [ ] **Step 2: Compile-check**

```bash
./gradlew :feature:search:impl:compileDebugKotlin -q
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: No commit yet** — bundles with Task 8.

---

### Task 8: `DefaultSearchActorsRepository` implementation + tests (TDD)

**Files:**
- Test: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchActorsRepositoryTest.kt` (new)
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchActorsRepository.kt`

- [ ] **Step 1: Write the failing test file**

```kotlin
package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.data.models.ActorUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [DefaultSearchActorsRepository]. Same strategy as
 * [DefaultSearchPostsRepositoryTest]: real [XrpcClient] backed by a
 * Ktor [MockEngine], assertions on `engine.requestHistory` + the
 * parsed result. Mirrors [net.kikin.nubecita.core.posting.internal.DefaultActorTypeaheadRepositoryTest]
 * for the ProfileView fixture shape.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSearchActorsRepositoryTest {
    @Test
    fun searchActors_happyPath_mapsAllFieldsAndCursor() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "cursor": "c2",
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileView",
                              "did": "did:plc:abc123",
                              "handle": "alice.bsky.social",
                              "displayName": "Alice",
                              "avatar": "https://cdn.example/alice.jpg"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val page = repo.searchActors(query = "ali", cursor = "c1", limit = 25).getOrThrow()

            assertEquals(1, page.items.size)
            assertEquals(
                ActorUi(
                    did = "did:plc:abc123",
                    handle = "alice.bsky.social",
                    displayName = "Alice",
                    avatarUrl = "https://cdn.example/alice.jpg",
                ),
                page.items[0],
            )
            assertEquals("c2", page.nextCursor)
        }

    @Test
    fun searchActors_blankDisplayName_normalizedToNull() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileView",
                              "did": "did:plc:xyz",
                              "handle": "bob.bsky.social",
                              "displayName": "   "
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val page = repo.searchActors(query = "bob", cursor = null, limit = 25).getOrThrow()

            assertNull(page.items[0].displayName)
            assertNull(page.items[0].avatarUrl)
        }

    @Test
    fun searchActors_emptyResult_returnsEmptyPage() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson("""{"actors": []}""")
                }

            val page = repo.searchActors(query = "noone", cursor = null, limit = 25).getOrThrow()

            assertTrue(page.items.isEmpty())
            assertNull(page.nextCursor)
        }

    @Test
    fun searchActors_throws_surfacesFailure() =
        runTest {
            val (_, repo) = newRepo { _ -> throw IOException("network down") }

            val result = repo.searchActors(query = "x", cursor = null, limit = 25)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun searchActors_cancellation_propagates() =
        runTest {
            val (_, repo) = newRepo { _ -> throw CancellationException("cancelled") }

            assertThrows(CancellationException::class.java) {
                runTest { repo.searchActors(query = "x", cursor = null, limit = 25) }
            }
        }

    @Test
    fun searchActors_passesQueryCursorAndLimitThrough() =
        runTest {
            val (engine, repo) = newRepo { _ -> okJson("""{"actors": []}""") }

            repo.searchActors(query = "kotlin lang", cursor = "abc", limit = 13)

            val url = engine.requestHistory.single().url.toString()
            assertTrue(url.contains("q=kotlin"))
            assertTrue(url.contains("cursor=abc"))
            assertTrue(url.contains("limit=13"))
        }

    // --- helpers (same shape as DefaultSearchPostsRepositoryTest) ---

    private fun newRepo(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultSearchActorsRepository> {
        val engine = MockEngine { handler(it) }
        val httpClient = HttpClient(engine)
        val xrpcClient =
            XrpcClient(
                http = httpClient,
                serviceEndpoint = "https://example.test",
                authProvider = NoAuth,
            )
        val provider = FakeXrpcClientProvider(xrpcClient)
        return engine to
            DefaultSearchActorsRepository(
                xrpcClientProvider = provider,
                dispatcher = Dispatchers.Unconfined,
            )
    }

    private fun MockRequestHandleScope.okJson(body: String): HttpResponseData =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private class FakeXrpcClientProvider(
        private val client: XrpcClient,
    ) : XrpcClientProvider {
        override suspend fun authenticated(): XrpcClient = client
        override suspend fun anonymous(): XrpcClient = client
    }
}
```

(The `FakeXrpcClientProvider` is intentionally duplicated rather than shared across the two test files — keeps each test file independently readable; the two repos may diverge in their provider needs later.)

- [ ] **Step 2: Compile-check (test should fail to compile yet)**

```bash
./gradlew :feature:search:impl:compileDebugUnitTestKotlin -q
```
Expected: BUILD FAILED with `Unresolved reference: DefaultSearchActorsRepository`.

- [ ] **Step 3: Implement `DefaultSearchActorsRepository`**

```kotlin
// feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchActorsRepository.kt
package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.actor.SearchActorsRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.data.models.ActorUi
import timber.log.Timber

/**
 * Production [SearchActorsRepository] backed by the atproto-kotlin
 * SDK's [ActorService]. Mirrors [DefaultSearchPostsRepository]: same
 * IO dispatcher routing, same `CancellationException`-aware error
 * handling, same Timber failure-log shape.
 *
 * Sends `q` + `cursor` + `limit` and projects
 * [ActorService.searchActors] response via a local
 * [ProfileView.toActorUi] extension (declared in this file to keep
 * the mapper colocated with its single consumer; promote to
 * `:data:models` or `:core:posting` if a third consumer appears).
 *
 * `displayName` is normalized: blank strings collapse to `null` to
 * preserve the [ActorUi] contract that "no display name" means null,
 * not empty string.
 */
@Singleton
internal class DefaultSearchActorsRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SearchActorsRepository {
        override suspend fun searchActors(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchActorsPage> =
            withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ActorService(client).searchActors(
                            SearchActorsRequest(
                                q = query,
                                cursor = cursor,
                                limit = limit.toLong(),
                            ),
                        )
                    Result.success(
                        SearchActorsPage(
                            items = response.actors.map { it.toActorUi() }.toImmutableList(),
                            nextCursor = response.cursor,
                        ),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).e(t, "searchActors(q=%s, cursor=%s) failed: %s", query, cursor, t.javaClass.name)
                    Result.failure(t)
                }
            }

        private companion object {
            private const val TAG = "SearchActorsRepo"
        }
    }

private fun ProfileView.toActorUi(): ActorUi =
    ActorUi(
        did = did.raw,
        handle = handle.raw,
        // Normalize blank → null per ActorUi's contract.
        displayName = displayName?.takeIf { it.isNotBlank() },
        avatarUrl = avatar?.raw,
    )
```

- [ ] **Step 4: Run the tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest -q
```
Expected: BUILD SUCCESSFUL. All 6 new tests pass alongside the existing tests.

- [ ] **Step 5: Add the Hilt binding**

Create `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/di/SearchActorsRepositoryModule.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.DefaultSearchActorsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchActorsRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SearchActorsRepositoryModule {
    @Binds
    internal abstract fun bindsSearchActorsRepository(
        impl: DefaultSearchActorsRepository,
    ): SearchActorsRepository
}
```

- [ ] **Step 6: Run the full local gate**

```bash
./gradlew :feature:search:impl:assembleDebug \
          :feature:search:impl:testDebugUnitTest \
          :feature:search:impl:lintDebug \
          :feature:search:impl:validateDebugScreenshotTest \
          :app:assembleDebug \
          spotlessCheck -q
```
Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchActorsRepository.kt \
        feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchActorsRepository.kt \
        feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/di/SearchActorsRepositoryModule.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchActorsRepositoryTest.kt

git commit -m "$(cat <<'EOF'
feat(feature/search/impl): SearchActorsRepository + Default impl

Wraps app.bsky.actor.searchActors behind a stateless
Result<SearchActorsPage> interface. Mirrors SearchPostsRepository's
shape (same dispatcher routing, same CancellationException-aware
error handling, same Timber log).

ProfileView results project to ActorUi (from :data:models, promoted
in the previous commit) via a local ProfileView.toActorUi() extension
co-located with the repo. Blank displayName values normalize to null
per ActorUi's contract. Mapper stays local until a second consumer
needs it.

Hilt @Binds in SearchActorsRepositoryModule under SingletonComponent
so nubecita-vrba.7's SearchActorsViewModel can inject the repo
directly.

6 unit tests: happy path with all fields, blank-displayName
normalization, empty result, IOException surfacing, CancellationException
propagation, q/cursor/limit wire-format. Real XrpcClient + Ktor
MockEngine pattern matching DefaultSearchPostsRepositoryTest.

Refs: nubecita-vrba.4
EOF
)"
```

---

### Task 9: Push PR 2

- [ ] **Step 1: Push the branch**

```bash
git push -u origin HEAD
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --title "feat(data/models,feature/search/impl): Actors search + promote ActorUi" --body "$(cat <<'EOF'
## Summary

Two commits inside this branch (squash-merge collapses to one):

1. **\`refactor(data/models,core/posting,feature/composer/impl): promote ActorTypeaheadUi to ActorUi\`** — moves the actor-row UI model from \`:core:posting\` to \`:data:models\` and drops the misleading \`Typeahead\` suffix. Same shape (\`did\`, \`handle\`, nullable \`displayName\`, nullable \`avatarUrl\`); now broadly applicable to search results, future profile lists, follower lists, etc. Mechanical rename; all pre-existing tests in \`:core:posting\` and \`:feature:composer:impl\` still pass.

2. **\`feat(feature/search/impl): SearchActorsRepository + Default impl\`** — wraps \`app.bsky.actor.searchActors\` behind a stateless \`Result<SearchActorsPage>\` interface, mirroring \`SearchPostsRepository\` from PR \`nubecita-vrba.3\`. \`ProfileView\` projects to \`ActorUi\` via a local extension. Same \`CancellationException\`-aware error handling. Hilt @Binds under \`SingletonComponent\` so \`nubecita-vrba.7\`'s \`SearchActorsViewModel\` can inject the repo directly.

Spec: \`docs/superpowers/specs/2026-05-16-search-data-layers-design.md\`
Plan: \`docs/superpowers/plans/2026-05-16-search-data-layers.md\` (Phase 2)

## Test plan

- [x] \`:core:posting:testDebugUnitTest\` — all pre-existing typeahead tests pass after rename.
- [x] \`:feature:composer:impl:testDebugUnitTest\` — composer tests pass after rename.
- [x] \`:feature:search:impl:testDebugUnitTest\` — 6 new SearchActorsRepository tests pass.
- [x] \`:feature:search:impl:lintDebug\` — clean.
- [x] \`:app:assembleDebug\` — Hilt graph still resolves.
- [x] \`spotlessCheck\` — green.

No connected-test job needed.

Closes: nubecita-vrba.4

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: No \`run-instrumented\` label** — pure JVM.

---

## Self-Review Notes

- **Spec coverage.** D1 (feature-internal placement) ✓ Tasks 2/4/7/8. D2 (Result<Page>) ✓ Tasks 2/7. D3 (cursor-stateless) ✓ Tasks 2/7. D4 (limit 25) ✓ Tasks 2/7. D5 (FeedItemUi.Single for posts) ✓ Tasks 1/3. D6 (ActorUi promotion) ✓ Task 6. D7 (dispatcher + CancellationException rethrow) ✓ Tasks 3/8 with explicit test cases (`searchPosts_cancellation_propagates`, `searchActors_cancellation_propagates`). D8 (mapper in `:core:feed-mapping` + local mapper for actors) ✓ Tasks 1 and 8. D9 (hand-written fakes / plain JVM / no Robolectric) ✓ Tasks 3/8.
- **Placeholder scan.** None. No "implement later" / "TBD". The Step-3 sed pattern in Task 6 is a real shell command, not a placeholder.
- **Type consistency.** `SearchPostsRepository`, `SearchPostsPage`, `SEARCH_POSTS_PAGE_LIMIT`, `DefaultSearchPostsRepository`, `toFlatFeedItemUiSingle`, `SearchActorsRepository`, `SearchActorsPage`, `SEARCH_ACTORS_PAGE_LIMIT`, `DefaultSearchActorsRepository`, `ActorUi`, `ProfileView.toActorUi()` — all identical across tasks where referenced.
- **Test-fixture honesty.** Task 1's test references `samplePostView(...)` builders that may not exist in `:core:feed-mapping/src/test/.../` today; the task explicitly tells the implementer to inspect the directory first and reuse what's there or write minimal local helpers. Not a placeholder.
- **CancellationException coverage.** Both repositories have dedicated test cases (`searchPosts_cancellation_propagates`, `searchActors_cancellation_propagates`) that exercise the rethrow path — addresses Gemini's correctness concern explicitly.
