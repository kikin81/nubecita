# Video Feed Chrome & Interactions (Slice 3b, PR2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the vertical video feed its overlay chrome — author, caption, and a right-rail action column (like / repost / reply / share) plus a mute toggle — with interactions routed through the sanctioned `PostInteractionHandler by handler` delegation.

**Architecture:** `VideoFeedViewModel` gains `PostInteractionHandler` by delegation and a `postInteractionsCache.state → items` read-merge so counts stay live. A stateless `VideoPageChrome` composes into the existing `VideoFeedPage` above the poster. Interaction side-effects are collected by the shared `rememberPostInteractions` helper; screen-specific navigation (author profile, post detail) flows through a new `VideoFeedEffect.NavigateTo`, collected by the screen and pushed onto `LocalMainShellNavState`.

**Tech Stack:** Kotlin 2.3, Jetpack Compose, Material 3 Expressive, Hilt, JUnit Jupiter, MockK, Turbine, AGP Compose screenshot testing.

**Spec:** `docs/superpowers/specs/2026-07-19-video-feed-chrome-design.md` — decisions **D5** (interaction delegation) and **D6** (right-rail layout), plus the accessibility contract in its `## Testing` section. PR1 (presentation) merged as #771. PR3 (gestures + progress) is out of scope.

## Global Constraints

- Module `:feature:videos:impl`; package root `net.kikin.nubecita.feature.videos.impl`.
- **MVI (CLAUDE.md):** flat state; `VideoFeedStatus` stays the sealed load-lifecycle sum. ViewModels never inject the navigation state holder — tab-internal navigation goes out as a `UiEffect` and the *screen* calls `LocalMainShellNavState.current.add(...)`.
- **D5 delegation is bounded:** the VM does **not** forward `InteractionEffect` onto its own effect channel — `rememberPostInteractions` observes `handler.interactionEffects` directly. The VM's `VideoFeedEffect` channel is reserved for screen-specific navigation only.
- **This is a NEW surface:** do **not** mirror tap markers into `UiState` — that mirror is a Feed-only migration artifact (CLAUDE.md). This PR does not consume `interactions.tapMarkers` at all, because they exist to cue `AnimatedCompactCount`'s digit-roll and that component is `internal` to `:designsystem`; the rail shows a plain count. If a later PR adds the animation, take the markers from `rememberPostInteractions`'s return value — never from `UiState`.
- **The VM keeps its own cache read-merge and seed.** Dropping it is a regression that has happened before on a handler migration.
- Tests assert interaction effects on `vm.interactionEffects` (the delegated channel), **not** on `vm.effects`.
- No Kotlin `!!`. Reorder `if`/`when` on a positive `!= null` so the compiler smart-casts.
- JUnit **Jupiter** (`org.junit.jupiter.api.Test`), not JUnit 4. `@ExtendWith(MainDispatcherExtension::class)` for VM tests.
- ktlint via Spotless: `./gradlew :feature:videos:impl:spotlessApply` before every commit.
- **Every new `<string>` needs `values-b+es+419` and `values-pt-rBR` entries** or CI `Lint / android` fails `MissingTranslation`. `:app`'s lint does **not** catch it — run `./gradlew :feature:videos:impl:lintDebug`.
- **Accessibility split (spec `## Testing`):** one-shot actions (reply, share, avatar) use `Modifier.clickable(role = Role.Button, onClickLabel = …)`. Toggles (like, repost, mute) use `Modifier.toggleable(value, role = Role.Switch, onValueChange = …)` with the label carried as the **icon's `contentDescription`** — `toggleable` takes no label parameter and an `onClickLabel` there is silently dropped. Labels on toggles are the static **noun** ("Like", "Mute"), never the inverse verb.
- New `testTag` values are pinned by `VideoFeedTestTagsTest`; `:benchmark` hardcodes the literals.
- Conventional Commits; footer `Refs: nubecita-zdv8.9`; **no `Closes:`** (PR3 remains).
- Branch `feat/nubecita-zdv8.9-video-feed-chrome` is already created off fresh `main`. Never commit to `main`.

## Verified Interface Facts

Confirmed on `main` @ `72063cf1`. Use these exactly.

| Thing | Fact |
|---|---|
| `PostSurface` | `core/analytics/.../AnalyticsEvent.kt:266-276`, append-ordered (NOT alphabetical). **No `Videos` variant yet** — append `Videos("videos"),` after `Bookmarks("bookmarks"),`. |
| `PostInteractionHandler` | `fun bind(surface: PostSurface, scope: CoroutineScope)`, `val tapMarkers: StateFlow<PostTapMarkers>`, `val interactionEffects: Flow<InteractionEffect>`, `onLike/onBookmark/onRepost/onReply/onQuote/onShare/onShareLongPress(post: PostUi)`, `onOverflowAction(post: PostUi, action: PostOverflowAction)`. |
| `rememberPostInteractions` | `@Composable fun rememberPostInteractions(handler, snackbarHostState: SnackbarHostState, strings: InteractionStrings, onInteractionError: () -> Unit = {}): PostInteractions` — returns `callbacks: PostCallbacks` + `tapMarkers: PostTapMarkers`. Reads `LocalMainShellNavState` internally. |
| `InteractionStrings` | **13** `String` fields, in order: `errorNetwork, errorUnauthenticated, errorUnknown, linkCopied, clipLabel, reportComingSoon, muteComingSoon, unmuteComingSoon, blockComingSoon, unblockComingSoon, muteThreadComingSoon, unmuteThreadComingSoon, textCopied`. |
| `PostInteractionsCache` | `core/post-interactions/.../PostInteractionsCache.kt:47`; injected as `private val postInteractionsCache: PostInteractionsCache`; exposes `state` and `seed(posts)`. |
| `PostUi.mergeInteractionState` | `core/post-interactions/.../MergeInteractionState.kt:36` — `fun PostUi.mergeInteractionState(state: PostInteractionState): PostUi`. |
| NavKeys | `net.kikin.nubecita.feature.profile.api.Profile(handle: String? = null)` — `handle` accepts a handle **or** a DID. `net.kikin.nubecita.feature.postdetail.api.PostDetailRoute(postUri: String)`. |
| Reusable UI | `NubecitaIcon` is **public**: `(name, contentDescription, modifier, filled, weight, grade, opticalSize, tint)`. `PostStat` and `AnimatedCompactCount` are `internal` to `:designsystem` — **not reusable**; the rail is a videos-local composable. Icons available: `Favorite`, `Repeat`, `ChatBubble`, `IosShare`, `VolumeOff`, `VolumeUp`. |
| Count formatting | Use `@Composable public fun rememberCompactCount(count: Long): String` from `net.kikin.nubecita.core.common.text`. It keys the formatter off `LocalLocale.current`, which is what keeps screenshot baselines deterministic. Do **not** call `formatCompactCount(count, locale)` with a captured `Locale.getDefault()` — that makes baselines depend on the host's locale and has already cost a CI round-trip on this repo. |
| Model fields (verified) | `PostStatsUi`: `replyCount`, `repostCount`, `likeCount`, `quoteCount`, `bookmarkCount` — all `Int`, so `.toLong()` at the rail. `ViewerStateUi`: `isLikedByViewer`, `isRepostedByViewer`, `isFollowingAuthor`, `likeUri`, `repostUri`. `PostInteractionState`: `viewerLikeUri: String?`, `viewerRepostUri: String?`, `likeCount: Long`, `repostCount: Long`, `pendingLikeWrite`, `pendingRepostWrite` — there is **no** `isLiked` field. `NavKey` is `androidx.navigation3.runtime.NavKey`. |
| tapMarkers | `interactions.tapMarkers` is deliberately **unused** in this PR: they drive `AnimatedCompactCount`'s digit-roll cue, and that component is `internal` to `:designsystem`, so the rail renders a plain count. Not mirroring them into `UiState` is correct (CLAUDE.md); not consuming them at all is a conscious consequence of not having the animation. |
| Baselines | Adding chrome to `VideoFeedPage` invalidates all **4** committed PNGs. Plan on regenerating and re-inspecting them. |

## File Structure

| File | Responsibility |
|---|---|
| `core/analytics/.../AnalyticsEvent.kt` | **Modify.** Append `PostSurface.Videos`. |
| `.../impl/VideoFeedContract.kt` | **Modify.** `VideoFeedEffect.NavigateTo`, new events. |
| `.../impl/VideoFeedViewModel.kt` | **Modify.** Delegation, `bind`, cache merge + seed, nav events. |
| `.../impl/ui/VideoPageChrome.kt` | **Create.** Stateless rail + author + caption. No VM, no player. |
| `.../impl/ui/VideoRailAction.kt` | **Create.** One rail cell (icon + optional count) with the a11y split. |
| `.../impl/ui/VideoFeedPage.kt` | **Modify.** Compose chrome above the poster. |
| `.../impl/ui/VideoFeedInteractions.kt` | **Create.** `rememberVideoFeedInteractions`, mirroring `FeedInteractions.kt`. |
| `.../impl/VideoFeedScreen.kt` | **Modify.** Snackbar host, interactions, effect collection, nav lambdas. |
| `.../impl/di/VideosNavigationModule.kt` | **Modify.** Bind `onNavigateToPost` / `onNavigateToAuthor` to `navState.add(...)`. |
| `.../impl/VideoFeedTestTags.kt` (+ its test) | **Modify.** New tags for rail cells. |
| `src/main/res/values{,-b+es+419,-pt-rBR}/strings.xml` | **Modify.** ~20 new keys each. |

---

### Task 1: `PostSurface.Videos` + ViewModel delegation, cache merge, nav effects

Non-UI and fully unit-testable. Everything visual depends on it.

**Files:**
- Modify: `core/analytics/src/main/kotlin/net/kikin/nubecita/core/analytics/AnalyticsEvent.kt:266-276`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedContract.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedViewModel.kt`
- Modify: `feature/videos/impl/build.gradle.kts`
- Test: `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedViewModelTest.kt`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `VideoFeedEffect.NavigateTo(target: NavKey)`; `VideoFeedEvent.AuthorTapped(post: PostUi)`, `VideoFeedEvent.PostTapped(post: PostUi)`; `VideoFeedViewModel` implementing `PostInteractionHandler`.

- [ ] **Step 1: Add the analytics surface**

In `AnalyticsEvent.kt`, inside `enum class PostSurface`, append after `Bookmarks("bookmarks"),`:

```kotlin
    Videos("videos"),
```

The enum is append-ordered, not alphabetical — appending is correct.

- [ ] **Step 2: Add the dependencies**

In `feature/videos/impl/build.gradle.kts`, the `dependencies` block becomes (projects before `libs.*`, alphabetical within each group — `checkSortDependencies` enforces this):

```kotlin
dependencies {
    api(project(":feature:videos:api"))

    implementation(platform(libs.coil.bom))
    implementation(project(":core:analytics"))
    implementation(project(":core:common"))
    implementation(project(":core:post-interactions"))
    implementation(project(":core:post-interactions-ui"))
    implementation(project(":core:video"))
    implementation(project(":core:video-feed"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    implementation(project(":feature:postdetail:api"))
    implementation(project(":feature:profile:api"))
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.media3.ui.compose)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
```

Verify with `./gradlew :feature:videos:impl:checkSortDependencies` — expected BUILD SUCCESSFUL.

- [ ] **Step 3: Write the failing tests**

Append these to the existing `VideoFeedViewModelTest` class. Follow the file's existing fixture/setup style for constructing the VM; the new constructor parameters are `handler: PostInteractionHandler` and `postInteractionsCache: PostInteractionsCache`, both MockK mocks. Drive the cache with a `MutableStateFlow`.

```kotlin
    @Test
    fun `binds the handler to the videos analytics surface`() = runTest {
        createViewModel()
        verify { handler.bind(PostSurface.Videos, any()) }
    }

    @Test
    fun `tapping an author emits NavigateTo the author profile`() = runTest {
        val vm = createViewModel()
        vm.effects.test {
            vm.handleEvent(VideoFeedEvent.AuthorTapped(postWithVideo))
            assertEquals(
                VideoFeedEffect.NavigateTo(Profile(handle = postWithVideo.author.did)),
                awaitItem(),
            )
        }
    }

    @Test
    fun `tapping a post emits NavigateTo post detail`() = runTest {
        val vm = createViewModel()
        vm.effects.test {
            vm.handleEvent(VideoFeedEvent.PostTapped(postWithVideo))
            assertEquals(
                VideoFeedEffect.NavigateTo(PostDetailRoute(postUri = postWithVideo.id)),
                awaitItem(),
            )
        }
    }

    @Test
    fun `cache emissions merge into the loaded items so counts stay live`() = runTest {
        // The regression this test exists to prevent: a handler migration previously
        // dropped this merge, so likes appeared to work and then reverted.
        val vm = createViewModel()
        advanceUntilIdle()
        val before = (vm.uiState.value.status as VideoFeedStatus.Content).items.first().post
        cacheState.value = persistentMapOf(
            before.id to
                PostInteractionState(
                    viewerLikeUri = "at://did:plc:test/app.bsky.feed.like/1",
                    likeCount = before.stats.likeCount.toLong() + 1,
                ),
        )
        advanceUntilIdle()
        val after = (vm.uiState.value.status as VideoFeedStatus.Content).items.first().post
        assertTrue(after.viewer.isLikedByViewer)
        assertEquals(before.stats.likeCount + 1, after.stats.likeCount)
    }

    @Test
    fun `seeds the interactions cache with the loaded posts`() = runTest {
        createViewModel()
        advanceUntilIdle()
        verify { postInteractionsCache.seed(any()) }
    }
```

`PostInteractionState`'s fields are verified: `viewerLikeUri: String?`, `viewerRepostUri: String?`, `likeCount: Long`, `repostCount: Long`, `pendingLikeWrite`, `pendingRepostWrite`. A non-null `viewerLikeUri` is what `mergeInteractionState` turns into `viewer.isLikedByViewer = true`.

- [ ] **Step 4: Run the tests to verify they fail**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedViewModelTest*'
```

Expected: FAIL — unresolved references (`PostSurface`, `AuthorTapped`, `NavigateTo`, …).

- [ ] **Step 5: Extend the contract**

In `VideoFeedContract.kt`, add the import `androidx.navigation3.runtime.NavKey` (`PostUi` is already imported), then:

```kotlin
sealed interface VideoFeedEvent : UiEvent {
    /** The visible page settled on [index]. */
    data class ActiveIndexChanged(
        val index: Int,
    ) : VideoFeedEvent

    data object ToggleMute : VideoFeedEvent

    data object Retry : VideoFeedEvent

    /** The author's avatar or handle was tapped — open their profile. */
    data class AuthorTapped(
        val post: PostUi,
    ) : VideoFeedEvent

    /** The caption or reply affordance was tapped — open the thread. */
    data class PostTapped(
        val post: PostUi,
    ) : VideoFeedEvent
}

/**
 * Screen-specific effects only. Interaction side-effects (share sheet, clipboard,
 * error snackbars, composer/report/block navigation) are NOT routed here — the
 * shared `rememberPostInteractions` helper observes `handler.interactionEffects`
 * directly, per the sanctioned delegation contract in CLAUDE.md.
 */
sealed interface VideoFeedEffect : UiEffect {
    /** Push a sub-route onto the MainShell back stack. The screen performs the push. */
    data class NavigateTo(
        val target: NavKey,
    ) : VideoFeedEffect
}
```

- [ ] **Step 6: Wire the ViewModel**

In `VideoFeedViewModel.kt`: add constructor params `private val handler: PostInteractionHandler,` and `private val postInteractionsCache: PostInteractionsCache,`; change the supertype list to delegate; and add the bind, merge and seed. The class header becomes:

```kotlin
    ) : MviViewModel<VideoFeedState, VideoFeedEvent, VideoFeedEffect>(
            VideoFeedState(activeIndex = route.startIndex.coerceAtLeast(0)),
        ),
        PostInteractionHandler by handler {
```

In `init`, **before** `loadFirstPage()`:

```kotlin
            handler.bind(PostSurface.Videos, viewModelScope)
            // Read-merge only: the handler owns writes and tap markers. Without this the
            // optimistic like/repost state never reaches the rail and counts revert.
            viewModelScope.launch {
                postInteractionsCache.state
                    .map { snapshot -> uiState.value.itemsOrEmpty().applyInteractions(snapshot) }
                    .distinctUntilChanged()
                    .collect { merged ->
                        setState {
                            val current = status
                            if (current is VideoFeedStatus.Content) {
                                copy(status = current.copy(items = merged))
                            } else {
                                this
                            }
                        }
                    }
            }
```

Add these private helpers to the class:

```kotlin
        private fun VideoFeedState.itemsOrEmpty(): ImmutableList<VideoFeedItem> =
            (status as? VideoFeedStatus.Content)?.items ?: persistentListOf()

        private fun ImmutableList<VideoFeedItem>.applyInteractions(
            interactionMap: PersistentMap<String, PostInteractionState>,
        ): ImmutableList<VideoFeedItem> =
            map { item ->
                interactionMap[item.post.id]
                    ?.let { state -> item.copy(post = item.post.mergeInteractionState(state)) }
                    ?: item
            }.toImmutableList()
```

Seed the cache in `loadFirstPage`'s success branch, immediately after `pool.bind(...)`:

```kotlin
                                postInteractionsCache.seed(loaded.map { it.post })
```

and in `maybeLoadMore`'s success branch, immediately after the `pool.bind(...)` call there:

```kotlin
                                    postInteractionsCache.seed(fresh.map { it.post })
```

Extend `handleEvent`:

```kotlin
                is VideoFeedEvent.AuthorTapped ->
                    sendEffect(VideoFeedEffect.NavigateTo(Profile(handle = event.post.author.did)))

                is VideoFeedEvent.PostTapped ->
                    sendEffect(VideoFeedEffect.NavigateTo(PostDetailRoute(postUri = event.post.id)))
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedViewModelTest*'
```

Expected: PASS. If `advanceUntilIdle()` is unavailable in the existing test style, match whatever the file already uses to drain coroutines rather than introducing a new dispatcher pattern.

- [ ] **Step 8: Verify the whole module and the analytics module still build**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest :core:analytics:compileDebugKotlin :feature:videos:impl:checkSortDependencies
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply
git add core/analytics feature/videos/impl
git commit -m "feat(videos): delegate post interactions on the vertical feed

Adds PostSurface.Videos and wires VideoFeedViewModel to
PostInteractionHandler by delegation, keeping its own cache read-merge
and seed so like/repost counts stay live. Navigation to author and post
detail flows out as VideoFeedEffect.NavigateTo.

Refs: nubecita-zdv8.9"
```

---

### Task 2: Rail action cell + chrome, with the accessibility split

**Files:**
- Create: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoRailAction.kt`
- Create: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoPageChrome.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedTestTags.kt`
- Modify: `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedTestTagsTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1 (stateless; takes `PostUi` and lambdas).
- Produces:
  - `internal fun VideoRailAction(icon: NubecitaIconName, accessibilityLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier, count: Long? = null, active: Boolean = false, toggleable: Boolean = false, activeColor: Color = Color.White, testTag: String? = null)`
  - `internal fun VideoPageChrome(post: PostUi, isMuted: Boolean, captionExpanded: Boolean, onCaptionToggle: () -> Unit, onAuthorTap: () -> Unit, onLike: () -> Unit, onRepost: () -> Unit, onReply: () -> Unit, onShare: () -> Unit, onMuteToggle: () -> Unit, modifier: Modifier = Modifier)`
  - Test tags: `RAIL_LIKE = "video_feed_like"`, `RAIL_REPOST = "video_feed_repost"`, `RAIL_REPLY = "video_feed_reply"`, `RAIL_SHARE = "video_feed_share"`, `MUTE = "video_feed_mute"`, `CAPTION = "video_feed_caption"`

- [ ] **Step 1: Write the failing test-tag pins**

Add to the existing `VideoFeedTestTagsTest` class:

```kotlin
    @Test
    fun `chrome tag values are pinned`() {
        assertEquals("video_feed_like", VideoFeedTestTags.RAIL_LIKE)
        assertEquals("video_feed_repost", VideoFeedTestTags.RAIL_REPOST)
        assertEquals("video_feed_reply", VideoFeedTestTags.RAIL_REPLY)
        assertEquals("video_feed_share", VideoFeedTestTags.RAIL_SHARE)
        assertEquals("video_feed_mute", VideoFeedTestTags.MUTE)
        assertEquals("video_feed_caption", VideoFeedTestTags.CAPTION)
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedTestTagsTest*'
```

Expected: FAIL — `Unresolved reference: RAIL_LIKE`.

- [ ] **Step 3: Add the tags**

In `VideoFeedTestTags.kt`, inside `object VideoFeedTestTags`:

```kotlin
    /** Right-rail like cell. */
    const val RAIL_LIKE: String = "video_feed_like"

    /** Right-rail repost cell. */
    const val RAIL_REPOST: String = "video_feed_repost"

    /** Right-rail reply cell. */
    const val RAIL_REPLY: String = "video_feed_reply"

    /** Right-rail share cell. */
    const val RAIL_SHARE: String = "video_feed_share"

    /** Mute toggle on the bottom edge. */
    const val MUTE: String = "video_feed_mute"

    /** Caption block (tap to expand). */
    const val CAPTION: String = "video_feed_caption"
```

- [ ] **Step 4: Run it to verify it passes**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedTestTagsTest*'
```

Expected: PASS.

- [ ] **Step 5: Write the rail cell**

Create `VideoRailAction.kt`. The a11y split is the whole point of this component — get it exactly right.

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * One cell of the vertical video feed's right-hand action rail: an icon with an
 * optional count beneath it, drawn white over the video.
 *
 * `:designsystem`'s `PostStat` is `internal` and horizontal, so it cannot be
 * reused here — but the accessibility contract is deliberately identical:
 *
 * - `toggleable = true` (like, repost) → `Modifier.toggleable(role = Role.Switch)`
 *   with the label on the icon's `contentDescription`. `toggleable` accepts no
 *   label parameter, so an `onClickLabel` here would be silently dropped.
 *   TalkBack announces "<label>, switch, on/off".
 * - `toggleable = false` (reply, share) → `Modifier.clickable(role = Role.Button,
 *   onClickLabel = …)`, and the icon stays decorative to avoid a double
 *   announcement. TalkBack announces "Double-tap to <label>".
 *
 * [accessibilityLabel] is always the plain **noun** ("Like"), never the inverse
 * verb ("Unlike") — the on/off state comes from the switch semantics.
 */
@Composable
internal fun VideoRailAction(
    icon: NubecitaIconName,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Long? = null,
    active: Boolean = false,
    toggleable: Boolean = false,
    activeColor: Color = Color.White,
    testTag: String? = null,
) {
    val tint = if (active) activeColor else Color.White
    val interaction =
        if (toggleable) {
            Modifier.toggleable(
                value = active,
                role = Role.Switch,
                onValueChange = { onClick() },
            )
        } else {
            Modifier.clickable(
                role = Role.Button,
                onClickLabel = accessibilityLabel,
                onClick = onClick,
            )
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .clip(CircleShape)
                .then(interaction)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        NubecitaIcon(
            name = icon,
            contentDescription = if (toggleable) accessibilityLabel else null,
            filled = active,
            tint = tint,
            opticalSize = RAIL_ICON_SIZE,
        )
        if (count != null) {
            Text(
                text = rememberCompactCount(count),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

private val RAIL_ICON_SIZE = 28.dp
```

All parameters above are verified against the current sources: `NubecitaIcon(name, contentDescription, modifier, filled, weight, grade, opticalSize, tint)` and `rememberCompactCount(count: Long): String`. `rememberCompactCount` is a `@Composable` — call it in the composable body, not inside a lambda.

- [ ] **Step 6: Write the chrome**

Create `VideoPageChrome.kt`. White-on-video content needs a scrim for legibility over bright frames — use a bottom vertical gradient, not a flat overlay.

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.videos.impl.R
import net.kikin.nubecita.feature.videos.impl.VideoFeedTestTags

private const val CAPTION_COLLAPSED_LINES = 2
private val AVATAR_SIZE = 40.dp
private val SCRIM_ALPHA = 0.55f

/**
 * Overlay chrome for one page of the vertical video feed (design D6): author and
 * caption bottom-left, action rail on the right edge, mute toggle bottom-right.
 *
 * Stateless — takes a [PostUi] and lambdas, holds no ViewModel and no player, so
 * it renders under layoutlib for screenshot tests.
 */
@Composable
internal fun VideoPageChrome(
    post: PostUi,
    isMuted: Boolean,
    captionExpanded: Boolean,
    onCaptionToggle: () -> Unit,
    onAuthorTap: () -> Unit,
    onLike: () -> Unit,
    onRepost: () -> Unit,
    onReply: () -> Unit,
    onShare: () -> Unit,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // Scrim only at the bottom, where the text sits — a full-screen scrim would
        // dim the video itself.
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA)),
                    ),
                ).padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.videos_open_profile),
                            onClick = onAuthorTap,
                        ),
                ) {
                    NubecitaAsyncImage(
                        model = post.author.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape),
                    )
                    Text(
                        text = post.author.displayName.ifBlank { post.author.handle },
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (post.text.isNotBlank()) {
                    Text(
                        text = post.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = if (captionExpanded) Int.MAX_VALUE else CAPTION_COLLAPSED_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .testTag(VideoFeedTestTags.CAPTION)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(R.string.videos_expand_caption),
                                    onClick = onCaptionToggle,
                                ),
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
        ) {
            VideoRailAction(
                icon = NubecitaIconName.Favorite,
                accessibilityLabel = stringResource(R.string.videos_action_like),
                onClick = onLike,
                count = post.stats.likeCount.toLong(),
                active = post.viewer.isLikedByViewer,
                toggleable = true,
                activeColor = MaterialTheme.colorScheme.secondary,
                testTag = VideoFeedTestTags.RAIL_LIKE,
            )
            VideoRailAction(
                icon = NubecitaIconName.Repeat,
                accessibilityLabel = stringResource(R.string.videos_action_repost),
                onClick = onRepost,
                count = post.stats.repostCount.toLong(),
                active = post.viewer.isRepostedByViewer,
                toggleable = true,
                activeColor = MaterialTheme.colorScheme.tertiary,
                testTag = VideoFeedTestTags.RAIL_REPOST,
            )
            VideoRailAction(
                icon = NubecitaIconName.ChatBubble,
                accessibilityLabel = stringResource(R.string.videos_action_reply),
                onClick = onReply,
                count = post.stats.replyCount.toLong(),
                testTag = VideoFeedTestTags.RAIL_REPLY,
            )
            VideoRailAction(
                icon = NubecitaIconName.IosShare,
                accessibilityLabel = stringResource(R.string.videos_action_share),
                onClick = onShare,
                testTag = VideoFeedTestTags.RAIL_SHARE,
            )
            VideoRailAction(
                icon = if (isMuted) NubecitaIconName.VolumeOff else NubecitaIconName.VolumeUp,
                accessibilityLabel = stringResource(R.string.videos_action_mute),
                onClick = onMuteToggle,
                active = isMuted,
                toggleable = true,
                testTag = VideoFeedTestTags.MUTE,
            )
        }
    }
}
```

`post.viewer.isLikedByViewer` / `isRepostedByViewer` and `post.stats.likeCount` / `repostCount` / `replyCount` are verified field names; the stats are `Int`, hence `.toLong()`.

- [ ] **Step 7: Add the strings (all three locales)**

`feature/videos/impl/src/main/res/values/strings.xml` — add alongside the existing `videos_retry`:

```xml
    <string name="videos_action_like">Like</string>
    <string name="videos_action_repost">Repost</string>
    <string name="videos_action_reply">Reply</string>
    <string name="videos_action_share">Share</string>
    <string name="videos_action_mute">Mute</string>
    <string name="videos_open_profile">Open profile</string>
    <string name="videos_expand_caption">Expand caption</string>
    <string name="videos_error_network">Couldn\'t reach Bluesky. Check your connection.</string>
    <string name="videos_error_unauthenticated">Please sign in again.</string>
    <string name="videos_error_unknown">Something went wrong.</string>
    <string name="videos_link_copied">Link copied</string>
    <string name="videos_clip_label">Post link</string>
    <string name="videos_text_copied">Text copied</string>
    <string name="videos_report_coming_soon">Reporting is coming soon.</string>
    <string name="videos_mute_coming_soon">Muting is coming soon.</string>
    <string name="videos_unmute_coming_soon">Unmuting is coming soon.</string>
    <string name="videos_block_coming_soon">Blocking is coming soon.</string>
    <string name="videos_unblock_coming_soon">Unblocking is coming soon.</string>
    <string name="videos_mute_thread_coming_soon">Muting threads is coming soon.</string>
    <string name="videos_unmute_thread_coming_soon">Unmuting threads is coming soon.</string>
```

Copy the **existing** wording for the equivalent keys from `feature/feed/impl/src/main/res/values*/strings.xml` rather than writing new copy, so the same message reads identically across surfaces — and take the es-419 and pt-BR translations from there too. Only invent copy for keys Feed has no equivalent of (`videos_open_profile`, `videos_expand_caption`). Add every key to `values-b+es+419/strings.xml` and `values-pt-rBR/strings.xml`.

- [ ] **Step 8: Verify translations and build**

```bash
./gradlew :feature:videos:impl:lintDebug
```

Expected: BUILD SUCCESSFUL, no `MissingTranslation`. `:app`'s lint does not catch this — the module's own lint must be run.

- [ ] **Step 9: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply
git add feature/videos/impl
git commit -m "feat(videos): right-rail chrome for the vertical feed

Author, caption and a like/repost/reply/share rail drawn white over the
video with a bottom scrim. Toggles use Role.Switch with a static noun
label on the icon; one-shot cells use clickable + onClickLabel.

Refs: nubecita-zdv8.9"
```

---

### Task 3: Wire chrome into the page and screen

**Files:**
- Create: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedInteractions.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPage.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedScreen.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/di/VideosNavigationModule.kt`
- Modify: `feature/videos/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPageScreenshotTest.kt`

**Interfaces:**
- Consumes: `VideoFeedEffect.NavigateTo`, `VideoFeedEvent.AuthorTapped/PostTapped` (Task 1); `VideoPageChrome` (Task 2).
- Produces: `rememberVideoFeedInteractions(viewModel, snackbarHostState): PostCallbacks`.

- [ ] **Step 1: Build the interactions helper**

Create `VideoFeedInteractions.kt`, mirroring `feature/feed/impl/.../ui/FeedInteractions.kt`. Pre-resolve all 13 strings at composition time — lint (`LocalContextGetResourceValueCall`) forbids `context.getString` inside the effect.

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import net.kikin.nubecita.core.postinteractions.ui.InteractionStrings
import net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.feature.videos.impl.R
import net.kikin.nubecita.feature.videos.impl.VideoFeedViewModel

/**
 * Wires the vertical feed's chrome to the delegated [PostInteractionHandler].
 *
 * The returned callbacks come from the shared helper, which also OWNS the
 * interaction-effect collector (share sheet, clipboard, error snackbars,
 * composer/report/block navigation). The ViewModel must not forward those onto
 * its own effect channel — its channel carries screen navigation only.
 */
@Composable
internal fun rememberVideoFeedInteractions(
    viewModel: VideoFeedViewModel,
    snackbarHostState: SnackbarHostState,
): PostCallbacks {
    val strings =
        InteractionStrings(
            errorNetwork = stringResource(R.string.videos_error_network),
            errorUnauthenticated = stringResource(R.string.videos_error_unauthenticated),
            errorUnknown = stringResource(R.string.videos_error_unknown),
            linkCopied = stringResource(R.string.videos_link_copied),
            clipLabel = stringResource(R.string.videos_clip_label),
            reportComingSoon = stringResource(R.string.videos_report_coming_soon),
            muteComingSoon = stringResource(R.string.videos_mute_coming_soon),
            unmuteComingSoon = stringResource(R.string.videos_unmute_coming_soon),
            blockComingSoon = stringResource(R.string.videos_block_coming_soon),
            unblockComingSoon = stringResource(R.string.videos_unblock_coming_soon),
            muteThreadComingSoon = stringResource(R.string.videos_mute_thread_coming_soon),
            unmuteThreadComingSoon = stringResource(R.string.videos_unmute_thread_coming_soon),
            textCopied = stringResource(R.string.videos_text_copied),
        )
    val interactions =
        rememberPostInteractions(
            handler = viewModel,
            snackbarHostState = snackbarHostState,
            strings = strings,
        )
    return interactions.callbacks
}
```

The 13 parameter names above are verified against `core/post-interactions-ui/.../PostInteractions.kt:53` and are in declaration order.

- [ ] **Step 2: Give `VideoFeedPage` a chrome slot**

In `VideoFeedPage.kt`, add a trailing `chrome: @Composable () -> Unit = {}` parameter and invoke it inside the root `Box` **after** the poster, so chrome draws above it:

```kotlin
        chrome()
```

Keeping it a slot (rather than passing a `PostUi`) leaves the page composable independent of the interaction wiring and keeps it renderable under layoutlib.

- [ ] **Step 3: Wire the screen**

In `VideoFeedScreen.kt`:

1. Add a `SnackbarHostState`, set it on the `Scaffold`'s `snackbarHost`, and keep `containerColor = Color.Black`.
2. Add screen parameters `onNavigateToPost: (String) -> Unit` and `onNavigateToAuthor: (String) -> Unit`, wrapped in `rememberUpdatedState` before use in the collector so it doesn't restart.
3. `val callbacks = rememberVideoFeedInteractions(viewModel, snackbarHostState)`.
4. Collect the VM's own effects once, in the outermost composable:

```kotlin
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is VideoFeedEffect.NavigateTo -> currentNavState.add(effect.target)
            }
        }
    }
```

where `currentNavState` is `rememberUpdatedState(LocalMainShellNavState.current).value`. Note `rememberPostInteractions` already reads `LocalMainShellNavState` itself for composer/report/block — this collector only handles `NavigateTo`.

5. Inside the pager page lambda, pass the chrome, holding caption expansion as local Compose state per page:

```kotlin
                        var captionExpanded by rememberSaveable(item.post.id) { mutableStateOf(false) }
                        VideoFeedPage(
                            posterUrl = item.posterUrl,
                            aspectRatio = if (isSettled) settledAspectRatio else item.aspectRatio,
                            posterAlpha = { posterAlphaState.value },
                        ) {
                            VideoPageChrome(
                                post = item.post,
                                isMuted = state.isMuted,
                                captionExpanded = captionExpanded,
                                onCaptionToggle = { captionExpanded = !captionExpanded },
                                onAuthorTap = { viewModel.handleEvent(VideoFeedEvent.AuthorTapped(item.post)) },
                                onLike = { viewModel.onLike(item.post) },
                                onRepost = { viewModel.onRepost(item.post) },
                                onReply = { callbacks.onReply(item.post) },
                                onShare = { callbacks.onShare(item.post) },
                                onMuteToggle = { viewModel.handleEvent(VideoFeedEvent.ToggleMute) },
                            )
                        }
```

`onLike`/`onRepost` call the VM directly — those are delegation-forwarded per D5. `onReply`/`onShare` go through `callbacks` so the helper's share-sheet and composer-navigation effects fire.

- [ ] **Step 4: Bind navigation in the DI module**

In `VideosNavigationModule.kt`, inside the `entry<VideoFeed>` block that already reads `LocalMainShellNavState.current` and passes `onBack`, add:

```kotlin
                onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                onNavigateToAuthor = { handle -> navState.add(Profile(handle = handle)) },
```

- [ ] **Step 5: Update the screenshot previews and regenerate baselines**

The previews now need chrome. Add a module-local `previewPost()` fixture with a **pinned** `Instant` (copy the `PREVIEW_NOW` pattern from `feature/feed/impl/.../FeedScreen.kt` — host TZ/locale differences otherwise fail CI). Update the four existing previews to pass a `chrome = { VideoPageChrome(...) }` lambda, and add one preview with a long caption collapsed and one expanded.

Then:

```bash
./gradlew :feature:videos:impl:updateScreenshots
```

**Look at every regenerated PNG** with the Read tool before committing (if the deep `reference/` path fails to read, `cp` to /tmp first). Confirm concretely: the rail is visible on the right edge with counts, the author row and caption are legible over the bottom scrim, the collapsed caption shows 2 lines with an ellipsis and the expanded one shows more. Then verify uniqueness:

```bash
shasum -a 256 feature/videos/impl/src/screenshotTestDebug/reference/**/*.png | awk '{print $1}' | sort -u | wc -l
```

This must equal the PNG count. Identical baselines assert nothing — that failure has already happened once on this feature.

- [ ] **Step 6: Verify everything**

```bash
./gradlew :feature:videos:impl:assembleDebug :feature:videos:impl:testDebugUnitTest :feature:videos:impl:validateScreenshots :feature:videos:impl:lintDebug > /tmp/gate.log 2>&1; echo "EXIT=$?"; tail -5 /tmp/gate.log
```

Expected: `EXIT=0`. Check the exit code explicitly — a piped gradle command's failure is otherwise invisible.

- [ ] **Step 7: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply
git add feature/videos/impl
git commit -m "feat(videos): wire chrome and interactions into the vertical feed

Chrome composes into VideoFeedPage via a slot; interactions route through
the shared rememberPostInteractions helper, which owns the interaction
effect collector. Screen navigation flows through VideoFeedEffect.

Refs: nubecita-zdv8.9"
```

---

### Task 4: Bench smoke + device verification

DI/VM wiring changed (two new injected dependencies), so a compile-and-unit-test pass is not sufficient — a missing Hilt binding only fails at runtime.

**Files:** none.

- [ ] **Step 1: Check which devices are attached BEFORE installing**

```bash
adb devices -l
```

`installBenchDebug` installs to **every** attached device. Note the target's serial and use `-s <serial>` for every subsequent adb command. If a device carries a Play-installed build (`installerPackageName=com.android.vending`), do not uninstall it without asking.

- [ ] **Step 2: Install and launch**

```bash
./gradlew :app:installBenchDebug
adb -s <serial> shell cmd notification set_dnd priority
adb -s <serial> shell am start -n net.kikin.nubecita/.MainActivity
```

DND prevents a heads-up notification from intercepting a tap or landing in a capture.

- [ ] **Step 3: Navigate to the feed and verify no crash**

Discover → Trending Videos → open a clip. Resolve every tap target from `adb -s <serial> shell uiautomator dump`, not eyeballed coordinates. Confirm the app is still foreground before each capture. On a foldable, pass `-d <displayId>` to `screencap` and confirm that display is `state ON`.

```bash
adb -s <serial> logcat -d | grep -iE "FATAL|AndroidRuntime" | head
```

Expected: no output. A missing `PostInteractionHandler` or `PostInteractionsCache` binding surfaces here as a Hilt crash on screen entry.

- [ ] **Step 4: Verify each claim against what you see**

1. The rail renders on the right edge with like/repost/reply/share and their counts.
2. Author avatar, name and caption are legible over the video.
3. Tapping like fills the heart **and increments the count**, and the change persists across a swipe away and back — that is the cache read-merge working. A missed tap gives a false pass, so confirm the count actually changed in a capture.
4. Tapping the author opens the profile; tapping the caption expands it.
5. The mute toggle changes the icon.

- [ ] **Step 5: Restore and report honestly**

```bash
adb -s <serial> shell cmd notification set_dnd off
```

Report the result of each of the five checks. If any fails, that is a finding to surface, not to smooth over. Item 3 failing means the cache read-merge is broken — the exact regression the Global Constraints call out.

---

## PR completion

Open the PR with `Refs: nubecita-zdv8.9` — **not** `Closes:`, since PR3 remains. This diff adds `@Composable` declarations, so run the **compose-expert skill** (invoke it; do not substitute a hand-review). Gemini reviews automatically at PR open. Reply to and resolve every review thread — the repo blocks merge on unresolved threads.
