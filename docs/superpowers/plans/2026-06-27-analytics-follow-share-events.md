# Follow + Share Analytics Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two PII-free analytics events — `interact_actor` (follow/unfollow) and `share` (share-sheet/copy-link) — and wire them at their tap sites.

**Architecture:** Two new sealed `AnalyticsEvent` data classes + two local enums in `:core:analytics`. Fire fire-and-forget on tap from the feature ViewModels that already inject `AnalyticsClient` (Profile, PostDetail, Feed), mirroring the existing `InteractPost` call sites.

**Tech Stack:** Kotlin, JUnit Jupiter, the project's typed `AnalyticsClient` (Firebase-backed in production, NoOp in bench).

**Spec:** `docs/superpowers/specs/2026-06-27-analytics-follow-share-events-design.md`

## Global Constraints

- **PII NEVER sent.** Params are enum-derived `Str` or `BoolVal` only — no DIDs, AT-URIs, handles, or free text. No `item_id`/`target_user_id`.
- **`:core:analytics` is dependency-free** — no `:data:models`. New enums are defined locally in `AnalyticsEvent.kt`; the call site maps domain types onto them.
- **Fire-and-forget on tap.** Log before/independent of the network call, mirroring `InteractPost` (action direction read from pre-tap state). No batching/queue in `AnalyticsClient` — Firebase persists/batches offline.
- **Reuse `PostSurface`** (`feed | post_detail | profile | search`) for `source_surface`. Reuse the param name `action_type` (matches `InteractPost`).
- **Each flavored module test task runs `:<module>:testProductionDebugUnitTest`** (the modules are flavored; bare `testDebugUnitTest` is ambiguous).

---

### Task 1: Add `interact_actor` + `share` events to `:core:analytics`

**Files:**
- Modify: `core/analytics/src/main/kotlin/net/kikin/nubecita/core/analytics/AnalyticsEvent.kt`
- Test: `core/analytics/src/test/kotlin/net/kikin/nubecita/core/analytics/AnalyticsModelTest.kt`
- Test: `core/analytics/src/test/kotlin/net/kikin/nubecita/core/analytics/AnalyticsValidatorTest.kt`

**Interfaces:**
- Produces: `enum class ActorAction { Follow, Unfollow }` (wires `"follow"`/`"unfollow"`); `data class InteractActor(action: ActorAction, surface: PostSurface)` → event name `"interact_actor"`, params `action_type` + `source_surface`. `enum class ShareMethod { ShareSheet, CopyLink }` (wires `"share_sheet"`/`"copy_link"`); `data class Share(method: ShareMethod, surface: PostSurface)` → event name `"share"`, params `method` + `content_type`(`"post"`) + `source_surface`.

- [ ] **Step 1: Write the failing model tests**

Add to `AnalyticsModelTest.kt`:

```kotlin
@Test
fun `interact_actor carries action and source surface`() {
    val event = InteractActor(action = ActorAction.Follow, surface = PostSurface.Profile)
    assertEquals("interact_actor", event.name)
    assertEquals(
        mapOf(
            "action_type" to Str("follow"),
            "source_surface" to Str("profile"),
        ),
        event.params,
    )
    assertEquals("unfollow", ActorAction.Unfollow.wire)
}

@Test
fun `share carries method content_type and source surface`() {
    val event = Share(method = ShareMethod.ShareSheet, surface = PostSurface.Feed)
    assertEquals("share", event.name)
    assertEquals(
        mapOf(
            "method" to Str("share_sheet"),
            "content_type" to Str("post"),
            "source_surface" to Str("feed"),
        ),
        event.params,
    )
    assertEquals("copy_link", ShareMethod.CopyLink.wire)
}
```

Add the two events to the validation list in `AnalyticsValidatorTest.kt` (inside the `listOf(...)` in `all v1 events pass validation`):

```kotlin
InteractActor(ActorAction.Follow, PostSurface.Profile),
InteractActor(ActorAction.Unfollow, PostSurface.Search),
Share(ShareMethod.ShareSheet, PostSurface.Feed),
Share(ShareMethod.CopyLink, PostSurface.PostDetail),
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:analytics:testProductionDebugUnitTest --tests '*AnalyticsModelTest*' --tests '*AnalyticsValidatorTest*'`
Expected: FAIL — compile error, `InteractActor`/`Share`/`ActorAction`/`ShareMethod` unresolved.

- [ ] **Step 3: Implement the events**

Append to `AnalyticsEvent.kt` (after `InteractPost`; `Str` is already imported):

```kotlin
/** A user-to-user interaction (room for mute/block later). */
enum class ActorAction(
    val wire: String,
) {
    Follow("follow"),
    Unfollow("unfollow"),
}

/** Fired at follow/unfollow call sites (Profile today). Generic — mirrors [InteractPost]. */
data class InteractActor(
    val action: ActorAction,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name: String = "interact_actor"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "action_type" to Str(action.wire),
            "source_surface" to Str(surface.wire),
        )
}

/** How a post was shared. */
enum class ShareMethod(
    val wire: String,
) {
    ShareSheet("share_sheet"),
    CopyLink("copy_link"),
}

/**
 * GA4-recommended `share` event (PII-free: carries no item_id). Named `Share`
 * (not `SharePost`) to match the wire name and avoid shadowing the existing
 * `*Effect.SharePost` UI effects.
 */
data class Share(
    val method: ShareMethod,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name: String = "share"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "method" to Str(method.wire),
            "content_type" to Str("post"),
            "source_surface" to Str(surface.wire),
        )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:analytics:testProductionDebugUnitTest`
Expected: PASS (all analytics tests green).

- [ ] **Step 5: Commit**

```bash
git add core/analytics/src/main/kotlin/net/kikin/nubecita/core/analytics/AnalyticsEvent.kt \
        core/analytics/src/test/kotlin/net/kikin/nubecita/core/analytics/AnalyticsModelTest.kt \
        core/analytics/src/test/kotlin/net/kikin/nubecita/core/analytics/AnalyticsValidatorTest.kt
git commit -m "feat(analytics): add interact_actor + share events

Refs: nubecita-049f.8"
```

---

### Task 2: Wire `interact_actor` in `ProfileViewModel`

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt:496-513` (`onFollowTapped`)
- Test: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt`

**Interfaces:**
- Consumes: `InteractActor`, `ActorAction`, `PostSurface` from Task 1; the existing injected `analytics: AnalyticsClient`; `ProfileEvent.FollowTapped`.

- [ ] **Step 1: Write the failing test**

Mirror the existing `like and repost log InteractPost with the Profile surface` test (same file, ~line 1137) for the VM/analytics harness (`RecordingAnalyticsClient()` + `newVm(analytics = analytics)`), and the existing `FollowTapped` tests for seeding `viewerRelationship`. Add:

```kotlin
@Test
fun `FollowTapped from NotFollowing logs InteractActor follow`() =
    runTest {
        val analytics = RecordingAnalyticsClient()
        // Seed a profile whose viewer relationship is NotFollowing, mirroring the
        // existing follow tests' setup, then:
        val vm = newVm(/* repo yielding NotFollowing */, analytics = analytics)
        vm.handleEvent(ProfileEvent.FollowTapped)
        assertEquals(
            listOf(InteractActor(ActorAction.Follow, PostSurface.Profile)),
            analytics.events,
        )
    }

@Test
fun `FollowTapped from Following logs InteractActor unfollow`() =
    runTest {
        val analytics = RecordingAnalyticsClient()
        val vm = newVm(/* repo yielding Following(followUri set) */, analytics = analytics)
        vm.handleEvent(ProfileEvent.FollowTapped)
        assertEquals(
            listOf(InteractActor(ActorAction.Unfollow, PostSurface.Profile)),
            analytics.events,
        )
    }
```

Import `InteractActor`, `ActorAction` (PostSurface already imported).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest --tests '*ProfileViewModelTest*FollowTapped*InteractActor*'`
Expected: FAIL — `analytics.events` is empty (no event logged yet).

- [ ] **Step 3: Implement — log on the two real branches**

In `onFollowTapped()`, log before each launch (fires only on a real toggle; the `isPending`/`Self`/`None` early-outs stay silent):

```kotlin
when (current) {
    is ViewerRelationship.NotFollowing -> {
        analytics.log(InteractActor(ActorAction.Follow, PostSurface.Profile))
        launchFollow(previous = current)
    }
    is ViewerRelationship.Following -> {
        val followUri =
            requireNotNull(current.followUri) {
                "committed Following MUST have a non-null followUri"
            }
        analytics.log(InteractActor(ActorAction.Unfollow, PostSurface.Profile))
        launchUnfollow(previous = current, followUri = followUri)
    }
    ViewerRelationship.Self, ViewerRelationship.None -> Unit
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest --tests '*ProfileViewModelTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt \
        feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt
git commit -m "feat(analytics): log interact_actor on Profile follow/unfollow

Refs: nubecita-049f.8"
```

---

### Task 3: Wire `share` in `ProfileViewModel`

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt:210-213`
- Test: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt`

**Interfaces:**
- Consumes: `Share`, `ShareMethod`, `PostSurface`; `ProfileEvent.OnShareClicked`/`OnShareLongPressed` (each carries `post`).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `share click and long-press log share events with Profile surface`() =
    runTest {
        val analytics = RecordingAnalyticsClient()
        val vm = newVm(analytics = analytics)
        val post = /* a PostUi fixture, as used by other Profile share/like tests */
        vm.handleEvent(ProfileEvent.OnShareClicked(post))
        vm.handleEvent(ProfileEvent.OnShareLongPressed(post))
        assertEquals(
            listOf(
                Share(ShareMethod.ShareSheet, PostSurface.Profile),
                Share(ShareMethod.CopyLink, PostSurface.Profile),
            ),
            analytics.events,
        )
    }
```

Import `Share`, `ShareMethod`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest --tests '*ProfileViewModelTest*share click and long-press*'`
Expected: FAIL — `analytics.events` empty.

- [ ] **Step 3: Implement**

```kotlin
is ProfileEvent.OnShareClicked -> {
    analytics.log(Share(ShareMethod.ShareSheet, PostSurface.Profile))
    sendEffect(ProfileEffect.SharePost(event.post.toShareIntent()))
}
is ProfileEvent.OnShareLongPressed -> {
    analytics.log(Share(ShareMethod.CopyLink, PostSurface.Profile))
    sendEffect(ProfileEffect.CopyPermalink(event.post.toShareIntent().permalink))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest --tests '*ProfileViewModelTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/...
git commit -m "feat(analytics): log share on Profile share/copy-link

Refs: nubecita-049f.8"
```

---

### Task 4: Wire `share` in `PostDetailViewModel`

**Files:**
- Modify: `feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailViewModel.kt:154-157`
- Test: `feature/postdetail/impl/src/test/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `Share`, `ShareMethod`, `PostSurface`; the existing injected `analytics` (already logs `InteractPost`); `PostDetailEvent.OnShareClicked`/`OnShareLongPressed`.

- [ ] **Step 1: Write the failing test**

Mirror this module's existing `InteractPost` analytics test for the harness. Add:

```kotlin
@Test
fun `share click and long-press log share events with PostDetail surface`() =
    runTest {
        val analytics = RecordingAnalyticsClient()
        val vm = /* newVm/helper with analytics = analytics, as the InteractPost test does */
        val post = /* focused-post PostUi fixture used by existing share tests */
        vm.handleEvent(PostDetailEvent.OnShareClicked(post))
        vm.handleEvent(PostDetailEvent.OnShareLongPressed(post))
        assertEquals(
            listOf(
                Share(ShareMethod.ShareSheet, PostSurface.PostDetail),
                Share(ShareMethod.CopyLink, PostSurface.PostDetail),
            ),
            analytics.events,
        )
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:postdetail:impl:testProductionDebugUnitTest --tests '*PostDetailViewModelTest*share click and long-press*'`
Expected: FAIL — empty `analytics.events`.

- [ ] **Step 3: Implement**

```kotlin
is PostDetailEvent.OnShareClicked -> {
    analytics.log(Share(ShareMethod.ShareSheet, PostSurface.PostDetail))
    sendEffect(PostDetailEffect.SharePost(event.post.toShareIntent()))
}
is PostDetailEvent.OnShareLongPressed -> {
    analytics.log(Share(ShareMethod.CopyLink, PostSurface.PostDetail))
    sendEffect(PostDetailEffect.CopyPermalink(event.post.toShareIntent().permalink))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:postdetail:impl:testProductionDebugUnitTest --tests '*PostDetailViewModelTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/postdetail/impl/...
git commit -m "feat(analytics): log share on PostDetail share/copy-link

Refs: nubecita-049f.8"
```

---

### Task 5: Wire `share` in `FeedViewModel`

**Files:**
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt:135-137`
- Test: `feature/feed/impl/src/test/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModelTest.kt`

**Interfaces:**
- Consumes: `Share`, `ShareMethod`, `PostSurface`; the existing injected `analytics` (already logs `InteractPost`); `FeedEvent.OnShareClicked`/`OnShareLongPressed`.

- [ ] **Step 1: Write the failing test**

Mirror this module's existing `InteractPost` analytics test for the harness. Add:

```kotlin
@Test
fun `share click and long-press log share events with Feed surface`() =
    runTest {
        val analytics = RecordingAnalyticsClient()
        val vm = /* newVm/helper with analytics = analytics, as the InteractPost test does */
        val post = /* a feed PostUi fixture used by existing share/like tests */
        vm.handleEvent(FeedEvent.OnShareClicked(post))
        vm.handleEvent(FeedEvent.OnShareLongPressed(post))
        assertEquals(
            listOf(
                Share(ShareMethod.ShareSheet, PostSurface.Feed),
                Share(ShareMethod.CopyLink, PostSurface.Feed),
            ),
            analytics.events,
        )
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:feed:impl:testProductionDebugUnitTest --tests '*FeedViewModelTest*share click and long-press*'`
Expected: FAIL — empty `analytics.events`.

- [ ] **Step 3: Implement**

```kotlin
is FeedEvent.OnShareClicked -> {
    analytics.log(Share(ShareMethod.ShareSheet, PostSurface.Feed))
    sendEffect(FeedEffect.SharePost(event.post.toShareIntent()))
}
is FeedEvent.OnShareLongPressed -> {
    analytics.log(Share(ShareMethod.CopyLink, PostSurface.Feed))
    sendEffect(FeedEffect.CopyPermalink(event.post.toShareIntent().permalink))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:feed:impl:testProductionDebugUnitTest --tests '*FeedViewModelTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/feed/impl/...
git commit -m "feat(analytics): log share on Feed share/copy-link

Refs: nubecita-049f.8"
```

---

### Task 6: Register `content_type` GA4 custom dimension (post-merge, non-code)

Not a code change — GA4 config, forward-only, do when this ships. `action_type`/`source_surface`/`method` are already registered (049f.10).

- [ ] **Step 1:** Add a row to the local source-of-truth script `~/.config/nubecita/analytics/register_ga4_dimensions.py` `DIMENSIONS` list: `("content_type", "Content Type")  # share`.
- [ ] **Step 2:** Grant the SA Editor on the property, then run `./.venv/bin/python register_ga4_dimensions.py --apply` (creates the one missing dimension), and demote the SA back to Viewer.
- [ ] **Step 3:** Verify via the analytics MCP `get_custom_dimensions_and_metrics` that `content_type` is live.

---

## Final verification (before marking PR ready)

- [ ] `./gradlew :core:analytics:testProductionDebugUnitTest :feature:profile:impl:testProductionDebugUnitTest :feature:postdetail:impl:testProductionDebugUnitTest :feature:feed:impl:testProductionDebugUnitTest` all green.
- [ ] `./gradlew :app:assembleDebug` links.
- [ ] `git diff origin/main...HEAD -- '*.kt' | grep '^+' | grep -q '@Composable'` → headless (no Compose touched) → skip compose-expert gate.
- [ ] Push, mark PR #617 ready for review, run `/gemini review`.
