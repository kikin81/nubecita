# Profile Videos Entry (Slice 6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a video in a profile's Media tab opens the existing immersive vertical feed over that author's videos (`getAuthorFeed` `posts_with_video`), positioned at the tapped video.

**Architecture:** A new `AuthorVideoSource` + a `VideoFeedSourceFactory` let `VideoFeedViewModel` pick trending-vs-author per route (the seam the DI KDoc flagged). The `VideoFeed` route gains a nullable `authorDid`. The VM seeks pages until the tapped `startPostUri` is found so it opens at the correct absolute index (never index 0 for a deep video). The profile redirects its Media video-cell tap to the already-wired generic `NavigateTo` effect.

**Tech Stack:** Kotlin, Hilt (`@AssistedInject`/`@AssistedFactory`, `Provider`), Media3, atproto-kotlin `FeedService.getAuthorFeed`, JUnit Jupiter + MockK + Turbine.

## Global Constraints

- Source of position/order is the **author's `posts_with_video` feed**; the tapped video is resolved by **URI identity** (`post.id == startPostUri`), never by grid position.
- The factory takes `authorDid: String?`, NOT the `VideoFeed` route — `:core:video-feed` must not depend on `:feature:videos:api` (no core→feature edge).
- `authorDid == null` preserves today's trending behavior exactly (the Discover carousel push is unchanged and backward-compatible).
- Only the **Media-grid** video cell (`OnMediaCellTapped(isVideo = true)`) is redirected. `OnVideoTapped` (inline Posts/Replies post-card video, despite the name) is unchanged.
- No Kotlin `!!`. `CancellationException` must be rethrown out of any `runCatching` around a suspend call.
- `PAGE_LIMIT = 30L`, matching the existing sources.
- Every change verified on the plugged-in Pixel Fold (serial `37201FDHS002UN`).

---

### Task 1: `AuthorVideoSource` + request helper

**Files:**
- Create: `core/video-feed/src/main/kotlin/net/kikin/nubecita/core/videofeed/AuthorVideoSource.kt`
- Test: `core/video-feed/src/test/kotlin/net/kikin/nubecita/core/videofeed/AuthorVideoSourceTest.kt`

**Interfaces:**
- Consumes: `VideoFeedSource` / `VideoFeedPage` (existing); `toVideoPosts(feed)` (existing pure mapper); `XrpcClientProvider`, `@IoDispatcher`.
- Produces: `internal fun authorVideoFeedRequest(actor: String, cursor: String?): GetAuthorFeedRequest`; `internal class AuthorVideoSource : VideoFeedSource` with `@AssistedFactory interface Factory { fun create(actor: String): AuthorVideoSource }`.

- [ ] **Step 1: Write the failing test** (pins the critical filter string — the SDK network call itself isn't unit-testable here, mirroring `TrendingVideoSource`, so the pure request builder is the tested seam)

Create `AuthorVideoSourceTest.kt`:

```kotlin
package net.kikin.nubecita.core.videofeed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthorVideoSourceTest {
    @Test
    fun request_usesPostsWithVideoFilterAndActor() {
        val request = authorVideoFeedRequest(actor = "did:plc:abc", cursor = null)
        assertEquals("posts_with_video", request.filter)
        assertEquals("did:plc:abc", request.actor.value)
        assertEquals(30L, request.limit)
        assertNull(request.cursor)
    }

    @Test
    fun request_threadsCursor() {
        val request = authorVideoFeedRequest(actor = "did:plc:abc", cursor = "page2")
        assertEquals("page2", request.cursor)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:video-feed:testDebugUnitTest --tests "*AuthorVideoSourceTest"`
Expected: FAIL — `authorVideoFeedRequest` unresolved.

> Note: `AtIdentifier`'s value accessor — confirm it is `.value` when writing Step 3; if the generated type exposes the raw string under a different name, adjust the assertion in Step 1 to match (it wraps the `actor` string). `AtIdentifier` is `io.github.kikin81.atproto.runtime.AtIdentifier`.

- [ ] **Step 3: Implement `AuthorVideoSource`**

Create `AuthorVideoSource.kt`:

```kotlin
package net.kikin.nubecita.core.videofeed

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetAuthorFeedRequest
import io.github.kikin81.atproto.runtime.AtIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber

/** The `posts_with_video` author-feed request. Pure, so the critical filter is unit-tested. */
internal fun authorVideoFeedRequest(
    actor: String,
    cursor: String?,
): GetAuthorFeedRequest =
    GetAuthorFeedRequest(
        actor = AtIdentifier(actor),
        filter = "posts_with_video",
        cursor = cursor,
        limit = AUTHOR_VIDEO_PAGE_LIMIT,
    )

/**
 * [VideoFeedSource] over a single actor's videos, via `app.bsky.feed.getAuthorFeed`
 * with `filter = "posts_with_video"` (a first-class lexicon filter). Backs the
 * profile-videos entry (epic nubecita-zdv8 Slice 6). Mirrors [DefaultTrendingVideoSource]
 * but scoped to one author; reuses the shared [toVideoPosts] mapper.
 */
internal class AuthorVideoSource
    @AssistedInject
    constructor(
        @Assisted private val actor: String,
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : VideoFeedSource {
        override suspend fun loadPage(cursor: String?): Result<VideoFeedPage> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response = FeedService(client).getAuthorFeed(authorVideoFeedRequest(actor, cursor))
                    VideoFeedPage(items = toVideoPosts(response.feed), cursor = response.cursor)
                }.onFailure { throwable ->
                    // runCatching also catches CancellationException; rethrow so structured
                    // coroutine cancellation propagates instead of being swallowed into a Result.
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "author getAuthorFeed failed: %s", throwable.javaClass.name)
                }
            }

        @AssistedFactory
        interface Factory {
            fun create(actor: String): AuthorVideoSource
        }

        private companion object {
            const val TAG = "AuthorVideoSource"
        }
    }

private const val AUTHOR_VIDEO_PAGE_LIMIT = 30L
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:video-feed:testDebugUnitTest --tests "*AuthorVideoSourceTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Compile the module**

Run: `./gradlew :core:video-feed:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Spotless + commit**

```bash
./gradlew :core:video-feed:spotlessApply -q
git add core/video-feed/src/main/kotlin/net/kikin/nubecita/core/videofeed/AuthorVideoSource.kt \
        core/video-feed/src/test/kotlin/net/kikin/nubecita/core/videofeed/AuthorVideoSourceTest.kt
git commit -m "feat(videos): AuthorVideoSource over getAuthorFeed posts_with_video

Refs: nubecita-zdv8.7"
```

---

### Task 2: Route `authorDid` + `VideoFeedSourceFactory` + DI + VM seek

This is one atomic graph change: the factory binding replaces the `VideoFeedSource` binding, and the VM must switch to injecting the factory in the same task or the Hilt graph won't resolve.

**Files:**
- Modify: `feature/videos/api/src/main/kotlin/net/kikin/nubecita/feature/videos/api/VideoFeed.kt`
- Create: `core/video-feed/src/main/kotlin/net/kikin/nubecita/core/videofeed/VideoFeedSourceFactory.kt`
- Modify: `core/video-feed/src/production/kotlin/net/kikin/nubecita/core/videofeed/di/VideoFeedModule.kt`
- Modify: `core/video-feed/src/bench/kotlin/net/kikin/nubecita/core/videofeed/di/VideoFeedModule.kt`
- Create: `core/video-feed/src/bench/kotlin/net/kikin/nubecita/core/videofeed/BenchVideoFeedSourceFactory.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedViewModel.kt`
- Test: `core/video-feed/src/test/kotlin/net/kikin/nubecita/core/videofeed/VideoFeedSourceFactoryTest.kt` (new)
- Test: `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedViewModelTest.kt` (update + add)

**Interfaces:**
- Consumes: `AuthorVideoSource.Factory`, `DefaultTrendingVideoSource` (Task 1 + existing); `FakeVideoFeedSource` (existing bench).
- Produces:
  - `data class VideoFeed(val startPostUri: String? = null, val authorDid: String? = null)`.
  - `interface VideoFeedSourceFactory { fun create(authorDid: String?): VideoFeedSource }` + `class DefaultVideoFeedSourceFactory`.
  - `VideoFeedViewModel` now `@AssistedInject`s `sourceFactory: VideoFeedSourceFactory` (replacing `source: VideoFeedSource`); `private val source = sourceFactory.create(route.authorDid)`.

- [ ] **Step 1: Add `authorDid` to the route**

In `VideoFeed.kt`, change the data class:

```kotlin
@Serializable
data class VideoFeed(
    val startPostUri: String? = null,
    val authorDid: String? = null,
) : NavKey
```

- [ ] **Step 2: Write the failing factory test**

Create `VideoFeedSourceFactoryTest.kt`:

```kotlin
package net.kikin.nubecita.core.videofeed

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.inject.Provider

class VideoFeedSourceFactoryTest {
    private val trendingInstance = mockk<DefaultTrendingVideoSource>()
    private val authorInstance = mockk<AuthorVideoSource>()
    private val authorFactory = mockk<AuthorVideoSource.Factory> { every { create(any()) } returns authorInstance }
    private val factory = DefaultVideoFeedSourceFactory(Provider { trendingInstance }, authorFactory)

    @Test
    fun nullAuthor_returnsTrending() {
        assertSame(trendingInstance, factory.create(null))
    }

    @Test
    fun nonNullAuthor_returnsAuthorSourceForThatActor() {
        val source = factory.create("did:plc:abc")
        assertSame(authorInstance, source)
        io.mockk.verify { authorFactory.create("did:plc:abc") }
    }
}
```

- [ ] **Step 3: Run factory test to verify it fails**

Run: `./gradlew :core:video-feed:testDebugUnitTest --tests "*VideoFeedSourceFactoryTest"`
Expected: FAIL — `VideoFeedSourceFactory` / `DefaultVideoFeedSourceFactory` unresolved.

- [ ] **Step 4: Create the factory**

Create `VideoFeedSourceFactory.kt`:

```kotlin
package net.kikin.nubecita.core.videofeed

import javax.inject.Inject
import javax.inject.Provider

/**
 * Selects the [VideoFeedSource] for a vertical-feed entry point. Takes the raw
 * `authorDid` (NOT the `VideoFeed` route) so `:core:video-feed` stays free of
 * feature dependencies; the ViewModel passes `route.authorDid` through.
 */
interface VideoFeedSourceFactory {
    /** @param authorDid null → trending feed; non-null → that author's videos. */
    fun create(authorDid: String?): VideoFeedSource
}

internal class DefaultVideoFeedSourceFactory
    @Inject
    constructor(
        private val trending: Provider<DefaultTrendingVideoSource>,
        private val authorSourceFactory: AuthorVideoSource.Factory,
    ) : VideoFeedSourceFactory {
        override fun create(authorDid: String?): VideoFeedSource =
            authorDid
                ?.let { authorSourceFactory.create(it) }
                ?: trending.get()
    }
```

- [ ] **Step 5: Run factory test to verify it passes**

Run: `./gradlew :core:video-feed:testDebugUnitTest --tests "*VideoFeedSourceFactoryTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Swap the production DI binding**

Replace the body of `core/video-feed/src/production/.../di/VideoFeedModule.kt`:

```kotlin
package net.kikin.nubecita.core.videofeed.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.videofeed.DefaultVideoFeedSourceFactory
import net.kikin.nubecita.core.videofeed.VideoFeedSourceFactory

/**
 * Binds the [VideoFeedSourceFactory] that selects per entry point: trending
 * (`thevids`) when no author, else an author's `posts_with_video` feed.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface VideoFeedModule {
    @Binds
    fun bindVideoFeedSourceFactory(impl: DefaultVideoFeedSourceFactory): VideoFeedSourceFactory
}
```

- [ ] **Step 7: Add the bench factory + swap the bench DI binding**

Create `core/video-feed/src/bench/.../BenchVideoFeedSourceFactory.kt`:

```kotlin
package net.kikin.nubecita.core.videofeed

import javax.inject.Inject
import javax.inject.Provider

/**
 * Bench-flavor factory: every entry point (trending or author) resolves to the
 * bundled [FakeVideoFeedSource], so the vertical feed and the profile-videos entry
 * both play fully offline.
 */
internal class BenchVideoFeedSourceFactory
    @Inject
    constructor(
        private val fake: Provider<FakeVideoFeedSource>,
    ) : VideoFeedSourceFactory {
        override fun create(authorDid: String?): VideoFeedSource = fake.get()
    }
```

Replace the body of `core/video-feed/src/bench/.../di/VideoFeedModule.kt`:

```kotlin
package net.kikin.nubecita.core.videofeed.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.videofeed.BenchVideoFeedSourceFactory
import net.kikin.nubecita.core.videofeed.VideoFeedSourceFactory

/**
 * Bench-flavor binding: [VideoFeedSourceFactory] → [BenchVideoFeedSourceFactory]
 * (bundled clips, fully offline for every entry point).
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface VideoFeedModule {
    @Binds
    fun bindVideoFeedSourceFactory(impl: BenchVideoFeedSourceFactory): VideoFeedSourceFactory
}
```

- [ ] **Step 8: Write the failing VM seek tests**

In `VideoFeedViewModelTest.kt`, replace the `source`/`vm()` wiring and add seek tests. First update the fixture near the top of the class:

```kotlin
    private val source = mockk<VideoFeedSource>()
    private val sourceFactory = mockk<VideoFeedSourceFactory> { every { create(any()) } returns source }
```

Change the `vm` helper to inject the factory and accept an `authorDid`:

```kotlin
    private fun vm(startPostUri: String? = null, authorDid: String? = null) =
        VideoFeedViewModel(VideoFeed(startPostUri, authorDid), sourceFactory, pool, shared, dataSaver, handler, interactionsCache)
```

Add these tests to the class (needs `import net.kikin.nubecita.core.videofeed.VideoFeedSourceFactory` and `io.mockk.coVerify`):

```kotlin
    @Test
    fun seek_opensAtAbsoluteIndex_whenTargetIsOnLaterPage() =
        runTest(mainDispatcher.dispatcher) {
            // page 1 = a,b,c (cursor c1); page 2 = d,e (cursor null). Target "e" is index 4 overall.
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a"), videoPost("b"), videoPost("c")), cursor = "c1"))
            coEvery { source.loadPage("c1") } returns
                Result.success(VideoFeedPage(listOf(videoPost("d"), videoPost("e")), cursor = null))

            val viewModel = vm(startPostUri = "e")
            advanceUntilIdle()

            val status = viewModel.uiState.value.status
            assertInstanceOf(VideoFeedStatus.Content::class.java, status)
            assertEquals(5, (status as VideoFeedStatus.Content).items.size) // both pages accumulated
            assertEquals(4, viewModel.uiState.value.activeIndex)            // absolute index of "e", not 0
            coVerify { source.loadPage("c1") }                              // it actually paged forward
        }

    @Test
    fun seek_stopsAtFirstPage_whenTargetIsThere() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a"), videoPost("b")), cursor = "c1"))

            val viewModel = vm(startPostUri = "b")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.activeIndex)
            coVerify(exactly = 0) { source.loadPage("c1") } // no needless second page
        }

    @Test
    fun seek_fallsBackToTop_whenTargetNeverFound() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a"), videoPost("b")), cursor = null))

            val viewModel = vm(startPostUri = "does-not-exist")
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.activeIndex)
        }

    @Test
    fun noStartUri_loadsExactlyOnePage() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = "c1"))

            val viewModel = vm(startPostUri = null)
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.activeIndex)
            coVerify(exactly = 0) { source.loadPage("c1") } // seek short-circuits with no target
        }
```

> `videoPost(id)` is the existing test helper (returns a `PostUi` with a video embed); reuse it. Existing tests that used `coEvery { source.loadPage(null) } ...` keep working unchanged — the mock `source` is still what the factory returns.

- [ ] **Step 9: Run the VM tests to verify the new ones fail**

Run: `./gradlew :feature:videos:impl:testDebugUnitTest --tests "*VideoFeedViewModelTest"`
Expected: FAIL — compilation error (VM constructor still takes `source`, not `sourceFactory`).

- [ ] **Step 10: Switch the VM to the factory + implement the seek**

In `VideoFeedViewModel.kt` constructor, replace the `source` parameter:

```kotlin
        @Assisted private val route: VideoFeed,
        private val sourceFactory: VideoFeedSourceFactory,
        private val pool: VerticalVideoPlaylistPlayer,
```

Add the resolved source as a property (place it beside the other `private val` state fields, e.g. next to `private val loaded = mutableListOf<VideoFeedItem>()`):

```kotlin
        private val source: VideoFeedSource = sourceFactory.create(route.authorDid)
```

Add the import `import net.kikin.nubecita.core.videofeed.VideoFeedSourceFactory` (and keep the existing `VideoFeedSource` import — the property is typed with it).

Replace the `loadFirstPage` body's single-page load with the seek. The current body is:

```kotlin
            loadJob =
                viewModelScope.launch {
                    source
                        .loadPage(null)
                        .onSuccess { page ->
                            cursor = page.cursor
                            endReached = page.cursor == null
                            loaded.clear()
                            loaded += page.items.mapNotNull { it.toVideoFeedItemOrNull() }.distinctBy { it.post.id }
                            if (loaded.isEmpty()) {
                                setState { copy(status = VideoFeedStatus.Error) }
                            } else {
                                val initialIndex = loaded.indexOfFirst { it.post.id == route.startPostUri }.coerceAtLeast(0)
                                val merged = loaded.toImmutableList().applyInteractions(postInteractionsCache.state.value)
                                setState { copy(status = VideoFeedStatus.Content(merged), activeIndex = initialIndex) }
                                pool.bind(loaded.map { it.source }, startIndex = initialIndex)
                                postInteractionsCache.seed(loaded.map { it.post })
                            }
                        }.onFailure {
                            Timber.w(it, "video feed first page failed")
                            setState { copy(status = VideoFeedStatus.Error) }
                        }
                }
```

Replace it with a seek loop that accumulates pages until `startPostUri` is found:

```kotlin
            loadJob =
                viewModelScope.launch {
                    loaded.clear()
                    cursor = null
                    endReached = false
                    var pages = 0
                    // Seek: page from the top until the tapped post appears, so it opens at its
                    // ABSOLUTE index (not 0). Both the Media grid and this feed are the same
                    // reverse-chron author posts under different filters, so the target is
                    // guaranteed present for a real tap; the loop terminates on it. With no
                    // startPostUri the do-body runs once and the while short-circuits (today's
                    // trending behaviour: one page, open at 0). MAX_SEEK_PAGES bounds pathological
                    // depth / bug cases → fall back to top.
                    do {
                        val result = source.loadPage(cursor)
                        val page =
                            result.getOrElse {
                                Timber.w(it, "video feed page load failed")
                                setState { copy(status = VideoFeedStatus.Error) }
                                return@launch
                            }
                        loaded += page.items.mapNotNull { it.toVideoFeedItemOrNull() }
                        // distinctBy across ALL accumulated pages: the appview can return a post
                        // twice, which would duplicate pager keys (it keys on post.id).
                        val deduped = loaded.distinctBy { it.post.id }
                        loaded.clear()
                        loaded += deduped
                        cursor = page.cursor
                        endReached = page.cursor == null
                        pages++
                    } while (
                        route.startPostUri != null &&
                        loaded.none { it.post.id == route.startPostUri } &&
                        cursor != null &&
                        pages < MAX_SEEK_PAGES
                    )

                    if (loaded.isEmpty()) {
                        setState { copy(status = VideoFeedStatus.Error) }
                    } else {
                        // -1 only if genuinely absent (aged out / past MAX_SEEK_PAGES) → open at top.
                        val initialIndex = loaded.indexOfFirst { it.post.id == route.startPostUri }.coerceAtLeast(0)
                        val merged = loaded.toImmutableList().applyInteractions(postInteractionsCache.state.value)
                        setState { copy(status = VideoFeedStatus.Content(merged), activeIndex = initialIndex) }
                        pool.bind(loaded.map { it.source }, startIndex = initialIndex)
                        postInteractionsCache.seed(loaded.map { it.post })
                    }
                }
```

Add `MAX_SEEK_PAGES` to the ViewModel's companion object (create one if absent):

```kotlin
        private companion object {
            /** Safety bound for the open-at-tapped seek; a real tap resolves well within this. */
            const val MAX_SEEK_PAGES = 6
        }
```

- [ ] **Step 11: Run the VM tests to verify all pass**

Run: `./gradlew :feature:videos:impl:testDebugUnitTest --tests "*VideoFeedViewModelTest"`
Expected: PASS — the four new seek tests plus all pre-existing VM tests.

- [ ] **Step 12: Compile both flavors of the app graph**

Run: `./gradlew :app:compileProductionDebugKotlin :app:compileBenchDebugKotlin`
Expected: BUILD SUCCESSFUL — Hilt resolves `VideoFeedSourceFactory` in both flavors and the VM injection type-checks. (This is the guard that the binding swap + VM change are consistent; a leftover `VideoFeedSource` injection would fail here.)

- [ ] **Step 13: Spotless + commit**

```bash
./gradlew :core:video-feed:spotlessApply :feature:videos:impl:spotlessApply :feature:videos:api:spotlessApply -q
git add feature/videos/api core/video-feed feature/videos/impl
git commit -m "feat(videos): per-entry-point source factory + open-at-tapped seek

Adds authorDid to the VideoFeed route and a VideoFeedSourceFactory the VM
uses to pick trending vs an author's posts_with_video feed. The VM now
seeks pages until startPostUri is found so a deep tap opens at its
absolute index, not the newest video.

Refs: nubecita-zdv8.7"
```

---

### Task 3: Profile Media-video redirect

**Files:**
- Modify: `feature/profile/impl/build.gradle.kts`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt`
- Test: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt` (add)

**Interfaces:**
- Consumes: `VideoFeed` (`:feature:videos:api`); `ProfileEffect.NavigateTo(key)`, `resolveActor()` (existing).
- Produces: the Media-video-cell tap now emits `NavigateTo(VideoFeed(startPostUri, authorDid = resolveActor()))`.

- [ ] **Step 1: Add the module dependency**

In `feature/profile/impl/build.gradle.kts`, add beside the other `:feature:*:api` implementation deps (e.g. after `:feature:moderation:api`):

```kotlin
    implementation(project(":feature:videos:api"))
```

- [ ] **Step 2: Write the failing test**

In `ProfileViewModelTest.kt`, add a test that a Media video-cell tap emits the `VideoFeed` nav (mirror the existing effect-assertion style in that file — collect `vm.effects` via Turbine; reuse the file's existing `vm`/actor setup helpers). Use the actual actor the test's profile loads with:

```kotlin
    @Test
    fun onMediaCellTapped_video_navigatesToVideoFeedForThisAuthor() =
        runTest(mainDispatcher.dispatcher) {
            val viewModel = /* existing helper that builds a loaded profile for actor TEST_ACTOR */
            viewModel.effects.test {
                viewModel.handleEvent(ProfileEvent.OnMediaCellTapped(postUri = "at://post/vid", isVideo = true))
                val effect = awaitItem()
                assertInstanceOf(ProfileEffect.NavigateTo::class.java, effect)
                val key = (effect as ProfileEffect.NavigateTo).key
                assertInstanceOf(VideoFeed::class.java, key)
                assertEquals("at://post/vid", (key as VideoFeed).startPostUri)
                assertEquals(TEST_ACTOR, key.authorDid)
            }
        }

    @Test
    fun onMediaCellTapped_image_stillOpensMediaViewer() =
        runTest(mainDispatcher.dispatcher) {
            val viewModel = /* same loaded-profile helper */
            viewModel.effects.test {
                viewModel.handleEvent(ProfileEvent.OnMediaCellTapped(postUri = "at://post/img", isVideo = false))
                assertInstanceOf(ProfileEffect.NavigateToMediaViewer::class.java, awaitItem())
            }
        }
```

> Fill the `/* … */` from the existing test file: it already constructs a `ProfileViewModel` with a fake `ProfileRepository` and a known actor (via `Profile(handle = …)` or the signed-in DID). Use that same actor value for `TEST_ACTOR` so the `authorDid` assertion matches `resolveActor()`. If the file has no media-tap test yet, model the setup on its existing header/tab tests.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "*ProfileViewModelTest"`
Expected: FAIL — the video tap currently emits `NavigateToVideoPlayer`, not `NavigateTo(VideoFeed(...))`.

- [ ] **Step 4: Redirect the Media video-cell branch**

In `ProfileViewModel.kt`, add the import `import net.kikin.nubecita.feature.videos.api.VideoFeed`, then change the `OnMediaCellTapped` branch:

```kotlin
                is ProfileEvent.OnMediaCellTapped ->
                    if (event.isVideo) {
                        sendEffect(
                            ProfileEffect.NavigateTo(
                                VideoFeed(startPostUri = event.postUri, authorDid = resolveActor()),
                            ),
                        )
                    } else {
                        sendEffect(ProfileEffect.NavigateToMediaViewer(event.postUri, imageIndex = 0))
                    }
```

Leave `OnVideoTapped` unchanged (inline post-card video, out of scope).

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "*ProfileViewModelTest"`
Expected: PASS (both new tests + existing).

- [ ] **Step 6: Verify sort + lint for the new dependency**

Run: `./gradlew :feature:profile:impl:checkSortDependencies :feature:profile:impl:compileProductionDebugKotlin`
Expected: BUILD SUCCESSFUL (the dep list stays sorted; the module compiles with the new `:feature:videos:api` dep).

- [ ] **Step 7: Spotless + commit**

```bash
./gradlew :feature:profile:impl:spotlessApply -q
git add feature/profile/impl
git commit -m "feat(profile): open the vertical video feed from a Media video cell

A Media-tab video tap now opens the immersive vertical feed over this
author's posts_with_video, at the tapped video, instead of the single
fullscreen player. Inline post-card videos are unchanged.

Refs: nubecita-zdv8.7"
```

---

### Task 4: Bench fixture + device verification

**Files:**
- Modify (if needed): the bench profile fixture that backs the Media grid (`BenchFakeActorRepository` / the bench `ProfileRepository` fake) so at least one Media cell is a video.

- [ ] **Step 1: Confirm the bench profile Media grid has a video cell**

Run: `grep -rn "isVideo\|MediaCell\|posts_with_media\|video" feature/profile/impl/src/bench core/*/src/bench app/src/bench 2>/dev/null | grep -i "video\|media" | head`
- If a bench profile already yields a `MediaCell(isVideo = true)`, no fixture change is needed — proceed to Step 2.
- If not, extend the bench profile fake so one Media item carries a video embed (reuse the bundled `asset:///video/clip-*.mp4` + `video-poster-*.jpg` the `FakeVideoFeedSource` already uses). Keep it a small, additive change; do not alter existing committed screenshot journeys.

- [ ] **Step 2: Build + install the bench build**

Run: `./gradlew :app:installBenchDebug`
Expected: BUILD SUCCESSFUL, installed on the Fold.

- [ ] **Step 3: Device-verify on the Pixel Fold (outer display, id 4619827677550801153 when folded)**

```bash
D=37201FDHS002UN
adb -s $D shell am start-activity -n net.kikin.nubecita/.MainActivity
# Navigate to a profile (e.g. tap an author avatar/handle, or the Profile tab for own profile),
# open the Media tab, and tap a video cell.
DISP=$(adb -s $D shell dumpsys SurfaceFlinger --display-id | grep 'display 1' | grep -oE '[0-9]{16,}' | head -1)
adb -s $D exec-out screencap -p -d "$DISP" > /tmp/slice6-feed.png
```

Read `/tmp/slice6-feed.png` and verify:
- Tapping a Media **video** cell opens the immersive vertical feed (full-screen video + right rail), NOT the single fullscreen lightbox.
- The feed shows the author's videos and can swipe up/down between them.
- Tapping a Media **image** cell still opens the image viewer (unchanged).

Record what was observed as the pass evidence.

- [ ] **Step 4: Commit any fixture change**

```bash
./gradlew :app:spotlessApply -q   # or the touched bench module's spotlessApply
git add -A
git commit -m "test(profile): bench Media grid exposes a video cell for the videos entry

Refs: nubecita-zdv8.7"
```

(If Step 1 needed no fixture change, skip this commit — the device verification stands on its own.)

---

## Self-Review

**Spec coverage:**
- `authorDid` route field → Task 2 Step 1. ✓
- `AuthorVideoSource` + `getAuthorFeed(posts_with_video)` → Task 1. ✓
- `VideoFeedSourceFactory` (`create(authorDid)`, no core→feature dep) + DI (prod + bench) → Task 2. ✓
- Page-until-found seek (absolute index, dedup across pages, `MAX_SEEK_PAGES`, no-startUri single page) → Task 2 Steps 8/10 + tests. ✓
- Newer-videos-reachable / videos-only (a consequence of seeking from the top) → covered by the seek's accumulate-from-top; asserted via the absolute-index test. ✓
- Profile Media-video redirect via `resolveActor()`, image unchanged, `OnVideoTapped` unchanged → Task 3. ✓
- Module dep `:feature:profile:impl → :feature:videos:api` → Task 3 Step 1. ✓
- Bench offline factory + device verification → Task 2 Step 7, Task 4. ✓
- Non-goals (no Videos tab, no inline-video change, no trending change) → nothing in the plan touches them. ✓

**Placeholder scan:** Two `/* … */` markers in Task 3 Step 2 are deliberate — the existing `ProfileViewModelTest` construction helper varies and must be reused verbatim; the note names exactly what to fill and from where. Every code step that creates production code has complete code.

**Type consistency:** `VideoFeedSourceFactory.create(authorDid: String?)`, `AuthorVideoSource.Factory.create(actor: String)`, `authorVideoFeedRequest(actor, cursor)`, `VideoFeed(startPostUri, authorDid)`, `MAX_SEEK_PAGES` — names/signatures match across tasks and the spec.
