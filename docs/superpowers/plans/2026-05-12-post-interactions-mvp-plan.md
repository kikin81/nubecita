# Post Interactions MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the first PR of `nubecita-8f6` (Post interactions epic). Promote the existing `LikeRepostRepository` from `:feature:feed:impl` to a new `:core/post-interactions` module, introduce a `PostInteractionsCache` with cross-screen sync, refactor `FeedViewModel`, and wire `ProfileViewModel` + `PostDetailViewModel` (currently stubbed) for the first time.

**Architecture:** `@Singleton` `PostInteractionsCache` holds a `StateFlow<PersistentMap<String, PostInteractionState>>`. Each VM subscribes to the cache, projects state onto its post list via the `mergeInteractionState` extension, and dispatches `cache.toggleLike` / `cache.toggleRepost` for user actions. The cache owns optimistic flips, rollback, single-flight per-postUri, and a refresh-merger rule that preserves in-flight state against stale wire data. `LikeRepostRepository` (atproto write surface, unchanged) becomes the cache's only dep.

**Tech Stack:** Kotlin · Hilt (`@Singleton`, `@Binds`, `@Inject`) · `kotlinx.coroutines.flow.StateFlow` · `kotlinx.collections.immutable.PersistentMap` · `:data:models.PostUi` · atproto-kotlin `StrongRef` / `AtUri` / `Cid` · JUnit 5 + MockK for unit tests · all dependencies already in place from prior beads.

**Predecessor design:** `docs/superpowers/specs/2026-05-12-post-interactions-mvp-design.md`. Refer back to it when something is ambiguous.

---

## File Structure

```
:core/post-interactions/                 # NEW module
├── build.gradle.kts                     # CREATE
├── src/main/AndroidManifest.xml         # CREATE (empty namespace-only manifest)
├── src/main/kotlin/net/kikin/nubecita/core/postinteractions/
│   ├── PostInteractionState.kt          # CREATE: data class + PendingState enum
│   ├── PostInteractionsCache.kt         # CREATE: public interface
│   ├── MergeInteractionState.kt         # CREATE: public PostUi extension
│   ├── LikeRepostRepository.kt          # MOVE from :feature:feed:impl/data/
│   ├── internal/
│   │   ├── DefaultPostInteractionsCache.kt   # CREATE: @Singleton impl
│   │   ├── DefaultLikeRepostRepository.kt    # MOVE from :feature:feed:impl/data/
│   │   └── PendingSentinels.kt              # CREATE: PENDING_LIKE_SENTINEL, PENDING_REPOST_SENTINEL
│   └── di/
│       └── PostInteractionsModule.kt    # CREATE: Hilt @Binds for cache + LikeRepostRepository
├── src/test/kotlin/net/kikin/nubecita/core/postinteractions/
│   ├── DefaultPostInteractionsCacheTest.kt   # CREATE: 11 scenarios
│   └── internal/FakeLikeRepostRepository.kt  # CREATE: test fake

settings.gradle.kts                      # MODIFY: + include(":core:post-interactions")

:feature:feed:impl/                      # MODIFY
├── build.gradle.kts                     # MODIFY: + implementation(project(":core:post-interactions"))
├── src/main/kotlin/.../
│   ├── data/LikeRepostRepository.kt          # DELETE (moved)
│   ├── data/DefaultLikeRepostRepository.kt   # DELETE (moved)
│   ├── di/LikeRepostRepositoryModule.kt      # DELETE (moved)
│   └── FeedViewModel.kt                      # MODIFY: drop toggleLike/toggleRepost; delegate to cache; subscribe + seed
└── src/test/kotlin/.../FeedViewModelTest.kt  # MODIFY: replace FakeLikeRepostRepository with FakePostInteractionsCache

:feature:profile:impl/                   # MODIFY
├── build.gradle.kts                     # MODIFY: + implementation(project(":core:post-interactions"))
├── src/main/kotlin/.../
│   ├── ProfileContract.kt               # MODIFY: + OnLikeClicked / OnRepostClicked events
│   ├── ProfileViewModel.kt              # MODIFY: + cache injection + subscribe + dispatch + seed
│   └── ProfileScreen.kt                 # MODIFY: PostCallbacks onLike/onRepost no-op stubs → real dispatch
└── src/test/kotlin/.../ProfileViewModelTest.kt   # MODIFY: + 4-5 new tests

:feature:postdetail:impl/                # MODIFY
├── build.gradle.kts                     # MODIFY: + implementation(project(":core:post-interactions"))
├── src/main/kotlin/.../
│   ├── PostDetailContract.kt            # MODIFY: + OnLikeClicked / OnRepostClicked events
│   ├── PostDetailViewModel.kt           # MODIFY: + cache injection + subscribe + dispatch + seed
│   └── PostDetailScreen.kt              # MODIFY: PostCallbacks onLike/onRepost no-op stubs → real dispatch
└── src/test/kotlin/.../PostDetailViewModelTest.kt   # MODIFY: + 3-4 new tests

:core/auth/                              # MODIFY
└── src/main/kotlin/.../DefaultAuthRepository.kt   # MODIFY: + cache injection; call cache.clear() in signOut()
```

**Two new things in this PR:**
1. `:core/post-interactions` module — public cache + broadcast surface; LikeRepostRepository moves here.
2. PostDetail + Profile gain real like/repost wiring (previously stubbed `{}`).

---

## Task 1: Scaffold `:core/post-interactions` module

Creates an empty module that compiles. No callers yet.

**Files:**
- Create: `core/post-interactions/build.gradle.kts`
- Create: `core/post-interactions/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts` (add `include(":core:post-interactions")`)

- [ ] **Step 1: Create the gradle file**

Create `core/post-interactions/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.postinteractions"
}

dependencies {
    api(project(":data:models"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
}
```

- [ ] **Step 2: Create the manifest**

Create `core/post-interactions/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 3: Register module in settings.gradle.kts**

In `settings.gradle.kts`, locate the existing `include(":core:posts")` line. Insert immediately after it:

```kotlin
include(":core:post-interactions")
```

- [ ] **Step 4: Compile to verify the empty module builds**

Run: `./gradlew :core:post-interactions:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no Kotlin sources yet; this builds the gradle skeleton).

- [ ] **Step 5: Commit**

```bash
git add core/post-interactions/build.gradle.kts \
        core/post-interactions/src/main/AndroidManifest.xml \
        settings.gradle.kts
git commit -m "$(cat <<'EOF'
chore(core/post-interactions): scaffold module

Empty module skeleton. Plugins, namespace, and deps mirror :core/posts'
shape. No Kotlin sources yet; following tasks land the cache, the
LikeRepostRepository move, and the Hilt bindings.

Refs: nubecita-78p
EOF
)"
```

---

## Task 2: Add `PostInteractionState` + `PendingState` enum

Public types consumed by the cache and the merge extension. No behavior.

**Files:**
- Create: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/PostInteractionState.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.core.postinteractions

/**
 * Per-post interaction state held by [PostInteractionsCache]. Subscribers
 * project this onto their own [net.kikin.nubecita.data.models.PostUi] list at
 * consumption time via [PostUi.mergeInteractionState].
 *
 * `viewerLikeUri` is the AtUri of the user's like RECORD (needed by
 * `deleteRecord` to unlike). While a like is in flight, this carries an
 * internal sentinel (see `internal/PendingSentinels.kt`). Null = not liked.
 *
 * `viewerRepostUri` is the AtUri of the user's repost RECORD; same shape.
 *
 * `pendingLikeWrite` / `pendingRepostWrite` is set to [PendingState.Pending]
 * while the network call is in flight, signaling [PostInteractionsCache.seed]
 * to FREEZE this post's interaction state against stale wire data
 * (atproto's appview lags `createRecord` writes by seconds to minutes).
 * Cleared by the cache itself when the network call resolves
 * (success → promote pending AtUri to real; failure → restore prior state).
 *
 * **The pending fields MUST NOT be projected onto `PostUi` via
 * [PostUi.mergeInteractionState]** — see design Decision 7. Exposing them
 * to UI invites spinners that defeat the optimistic-UI illusion. The
 * cache's single-flight guard already absorbs double-taps silently.
 */
data class PostInteractionState(
    val viewerLikeUri: String? = null,
    val viewerRepostUri: String? = null,
    val likeCount: Long = 0,
    val repostCount: Long = 0,
    val pendingLikeWrite: PendingState = PendingState.None,
    val pendingRepostWrite: PendingState = PendingState.None,
)

/**
 * Sealed binary flag for in-flight network calls per interaction-type.
 *
 * Modeled as an enum (not Boolean) so future expansion to "Pending with
 * retry" or "PendingDelete" is a non-breaking variant addition rather
 * than a contract change.
 */
enum class PendingState { None, Pending }
```

- [ ] **Step 2: Compile**

Run: `./gradlew :core:post-interactions:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/PostInteractionState.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): + PostInteractionState data class + PendingState enum

Public types that the cache exposes via state: StateFlow<...>. Carries
viewerLikeUri, viewerRepostUri, counts, and a pending-write flag per
interaction-type. The pending fields are internal correctness state —
NOT to be projected onto PostUi (preserves the optimistic-UI illusion).

Refs: nubecita-78p
EOF
)"
```

---

## Task 3: Add PENDING sentinels (internal)

Constants for the in-flight `viewerLikeUri` / `viewerRepostUri` placeholder values.

**Files:**
- Create: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/PendingSentinels.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.core.postinteractions.internal

/**
 * Sentinel AtUri-shaped string written to [PostInteractionState.viewerLikeUri]
 * while a like network call is in flight. The cache writes this on the
 * optimistic flip; on success it's replaced with the real wire-returned
 * AtUri; on failure it's reverted to the pre-tap value.
 *
 * The `at://` prefix is defensive: every real atproto URI starts with
 * `at://`, so the sentinel is syntactically URI-shaped. If a downstream
 * consumer ever bypasses `mergeInteractionState` and passes the value to
 * an `AtUri.parse()` call, the parse succeeds rather than throwing.
 * Belt-and-suspenders against future regressions.
 *
 * Sentinels live in the `internal` package — they are an implementation
 * detail of `DefaultPostInteractionsCache` and `mergeInteractionState`.
 * Consumers MUST NOT compare against these constants directly; the
 * `mergeInteractionState` extension strips them on the way out.
 */
internal const val PENDING_LIKE_SENTINEL: String = "at://pending:optimistic"

/**
 * Sibling of [PENDING_LIKE_SENTINEL] for in-flight repost calls.
 * Distinct value so a diagnostic / logging path that surfaces the
 * sentinel can distinguish like vs repost in flight.
 */
internal const val PENDING_REPOST_SENTINEL: String = "at://pending:optimistic-repost"
```

- [ ] **Step 2: Compile**

Run: `./gradlew :core:post-interactions:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/PendingSentinels.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): + PENDING_LIKE_SENTINEL and PENDING_REPOST_SENTINEL

Internal const strings for the in-flight viewerLikeUri / viewerRepostUri
placeholder. AtUri-shaped (at:// prefix) so a stray AtUri.parse() doesn't
crash on the sentinel — defensive only; mergeInteractionState strips
these before exposing PostUi.

Refs: nubecita-78p
EOF
)"
```

---

## Task 4: Add `PostInteractionsCache` interface

Public surface that VMs subscribe to and dispatch through. No impl yet.

**Files:**
- Create: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/PostInteractionsCache.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package net.kikin.nubecita.core.postinteractions

import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.data.models.PostUi

/**
 * Singleton cache + broadcast layer for post interactions (like / repost).
 *
 * Owns the canonical "is post X liked/reposted right now" state across the
 * app session. Every screen that renders PostCards subscribes to [state]
 * and projects the cached interaction onto its own `PostUi` list. A like
 * on PostDetail mutates the cache; Feed's subscriber receives the
 * emission and re-renders without re-fetching.
 *
 * # Subscriber pattern
 *
 * ```kotlin
 * viewModelScope.launch {
 *     cache.state
 *         .map { interactionMap -> currentItems().applyInteractions(interactionMap) }
 *         .distinctUntilChanged()
 *         .collect { setState { copy(items = it) } }
 * }
 * ```
 *
 * # Seed contract
 *
 * Every screen that LOADS posts (initial fetch, refresh, pagination)
 * MUST call [seed] with the loaded wire posts. The cache merges wire
 * data with any in-flight optimistic state, preserving pending writes
 * against the appview's eventual consistency lag.
 *
 * # Toggle contract
 *
 * [toggleLike] and [toggleRepost] are suspending and single-flight per
 * postUri. Double-taps during in-flight return [Result.success] without
 * re-firing the network call. On failure, the cache rolls back state
 * internally and returns [Result.failure] so the calling VM can route
 * the error to its own effect channel.
 *
 * # Sign-out
 *
 * [clear] resets the cache. Wired into `:core/auth/DefaultAuthRepository`
 * to fire before session revocation.
 */
interface PostInteractionsCache {
    /**
     * Canonical interaction state per postUri. Emits on every mutation
     * ([toggleLike] / [toggleRepost] / [seed] / [clear]).
     *
     * Keyed by `PostUi.id` (the post's AtUri).
     */
    val state: StateFlow<PersistentMap<String, PostInteractionState>>

    /**
     * Seed / refresh the cache from freshly-loaded wire posts. Idempotent;
     * safe to call on every wire fetch (initial load, refresh, tab switch,
     * pagination page).
     *
     * Merger rules:
     * - If `cache[postUri].pendingLikeWrite == Pending` OR
     *   `pendingRepostWrite == Pending`: preserve existing state entirely.
     *   Wire data is presumed stale during in-flight writes.
     * - Else if `cache[postUri].viewerLikeUri != null` AND wire's
     *   `viewer.likeUri == null`: preserve cache state for this post
     *   (atproto appview lags; user's recent like not yet indexed).
     * - Else: seed from wire (counts, viewerLikeUri/viewerRepostUri).
     *
     * Same shape for repost.
     */
    fun seed(posts: List<PostUi>)

    /**
     * Toggle like for [postUri]. Suspending: returns when the network call
     * resolves (success or failure).
     *
     * Behavior:
     * 1. Single-flight: if a like call is already in flight for this
     *    postUri, returns `Result.success(Unit)` synthetically without
     *    dispatching anything.
     * 2. Optimistic flip: writes a new state with `pendingLikeWrite =
     *    Pending`, flipped viewerLikeUri (null → PENDING_LIKE_SENTINEL or
     *    vice-versa), and ±1 likeCount delta. Emits.
     * 3. Fires the underlying repository call.
     * 4. Success: promotes pending to real (wire-returned AtUri on like;
     *    null on unlike); clears pendingLikeWrite. Emits.
     * 5. Failure: rolls back to the pre-tap snapshot; clears
     *    pendingLikeWrite. Emits. Returns `Result.failure(throwable)`.
     *
     * @param postUri The post's AtUri (`PostUi.id`).
     * @param postCid The post's CID (`PostUi.cid`), required for the
     *   `StrongRef` that `app.bsky.feed.like` records.
     */
    suspend fun toggleLike(postUri: String, postCid: String): Result<Unit>

    /**
     * Symmetric for repost. Same single-flight semantics, same rollback
     * shape, same return contract.
     */
    suspend fun toggleRepost(postUri: String, postCid: String): Result<Unit>

    /**
     * Reset the cache. Called by `:core/auth/DefaultAuthRepository.signOut`
     * before session revocation so a re-login starts with a fresh
     * canonical state.
     */
    fun clear()
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :core:post-interactions:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/PostInteractionsCache.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): + PostInteractionsCache public interface

Public contract: state: StateFlow<...> for subscribers, seed() for
wire-loaded posts, toggleLike/toggleRepost with single-flight + Result
return, clear() for sign-out. Behavior + merger rules documented in
KDoc. Impl lands in the next task.

Refs: nubecita-78p
EOF
)"
```

---

## Task 5: Add `MergeInteractionState` extension

Public extension on `PostUi` that VMs use to project cache state onto their post lists. Strips the PENDING sentinels.

**Files:**
- Create: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/MergeInteractionState.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.core.postinteractions

import net.kikin.nubecita.core.postinteractions.internal.PENDING_LIKE_SENTINEL
import net.kikin.nubecita.core.postinteractions.internal.PENDING_REPOST_SENTINEL
import net.kikin.nubecita.data.models.PostUi

/**
 * Project a [PostInteractionState] from [PostInteractionsCache.state] onto
 * a [PostUi] instance loaded from wire data. Returns a new [PostUi] with
 * the viewer/stats fields updated to reflect the cache's truth.
 *
 * The cache's PENDING sentinels (see `internal/PendingSentinels.kt`) are
 * stripped on the way out — consumers see `null` in `viewer.likeUri` while
 * a like is in flight, but the boolean `viewer.isLikedByViewer` reads
 * `viewerLikeUri != null` so the heart still renders as liked.
 *
 * This extension lives in `:core/post-interactions` (NOT in `:data:models`)
 * to keep `:data:models` a pure leaf — the data-models module should be
 * ignorant of cache logic, sentinels, and projection rules. The
 * dependency direction is `:core/post-interactions` → `:data:models`,
 * matching the natural architectural arrow.
 *
 * # Usage
 *
 * ```kotlin
 * // Inside a VM subscriber:
 * cache.state.collect { interactionMap ->
 *     val merged = currentItems.map { item ->
 *         val state = interactionMap[item.post.id] ?: return@map item
 *         item.copy(post = item.post.mergeInteractionState(state))
 *     }
 *     setState { copy(items = merged.toImmutableList()) }
 * }
 * ```
 */
fun PostUi.mergeInteractionState(state: PostInteractionState): PostUi =
    copy(
        viewer = viewer.copy(
            isLikedByViewer = state.viewerLikeUri != null,
            likeUri = state.viewerLikeUri?.takeIf { it != PENDING_LIKE_SENTINEL },
            isRepostedByViewer = state.viewerRepostUri != null,
            repostUri = state.viewerRepostUri?.takeIf { it != PENDING_REPOST_SENTINEL },
        ),
        stats = stats.copy(
            likeCount = state.likeCount.toInt(),
            repostCount = state.repostCount.toInt(),
        ),
    )
```

- [ ] **Step 2: Compile**

Run: `./gradlew :core:post-interactions:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/MergeInteractionState.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): + PostUi.mergeInteractionState extension

Public projection of PostInteractionState onto a PostUi. Strips the
PENDING sentinels via takeIf so consumers see null likeUri/repostUri
while pending, while the boolean isLikedByViewer/isRepostedByViewer
still renders as true (heart shows filled). Extension lives in
:core/post-interactions to keep :data:models a pure leaf.

Refs: nubecita-78p
EOF
)"
```

---

## Task 6: Move `LikeRepostRepository` from `:feature:feed:impl`

Promotes the interface + impl from `internal` to `public` and relocates the package. Updates Feed's build.gradle to depend on `:core/post-interactions`.

**Files:**
- Move: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/data/LikeRepostRepository.kt` → `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/LikeRepostRepository.kt`
- Move: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/data/DefaultLikeRepostRepository.kt` → `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultLikeRepostRepository.kt`
- Delete: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/di/LikeRepostRepositoryModule.kt`
- Modify: `feature/feed/impl/build.gradle.kts` (add `:core/post-interactions` dep)
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt` (import path change only — same usage shape; the actual logic-refactor happens in Task 13)

- [ ] **Step 1: Move the interface**

Using `git mv` to preserve history:

```bash
git mv feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/data/LikeRepostRepository.kt \
       core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/LikeRepostRepository.kt
```

Then open the moved file and:
1. Change the `package` line to `package net.kikin.nubecita.core.postinteractions`.
2. Change `internal interface LikeRepostRepository {` to `interface LikeRepostRepository {` (drop `internal`).
3. Update the KDoc — replace the "promote to a new `:core:feed` module" sentence with:

```
 * Lives in `:core/post-interactions`; consumed by [PostInteractionsCache]'s
 * implementation. Direct VM dependence on this interface is deprecated —
 * VMs should subscribe to [PostInteractionsCache.state] and dispatch
 * through [PostInteractionsCache.toggleLike] / [PostInteractionsCache.toggleRepost].
```

- [ ] **Step 2: Move the implementation**

```bash
git mv feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/data/DefaultLikeRepostRepository.kt \
       core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultLikeRepostRepository.kt
```

Then open the moved file and:
1. Change the `package` line to `package net.kikin.nubecita.core.postinteractions.internal`.
2. Add the import for the moved interface: `import net.kikin.nubecita.core.postinteractions.LikeRepostRepository`.
3. The `internal class DefaultLikeRepostRepository` stays internal — it's `:core/post-interactions`-internal. Same `@AssistedInject` / `@Inject constructor` body.

- [ ] **Step 3: Move the Hilt module**

`feature/feed/impl/src/main/kotlin/.../di/LikeRepostRepositoryModule.kt` is replaced by a new module in `:core/post-interactions` (next task). Delete the old file:

```bash
git rm feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/di/LikeRepostRepositoryModule.kt
```

- [ ] **Step 4: Update Feed's build.gradle.kts**

In `feature/feed/impl/build.gradle.kts`, find the existing `implementation(project(":feature:postdetail:api"))` line and add a `:core/post-interactions` entry next to it:

```kotlin
    implementation(project(":core:post-interactions"))
```

Keep all other deps. The atproto deps in `:feature:feed:impl` are still needed by the rest of FeedViewModel (wire mapper, etc.) — do NOT remove them.

- [ ] **Step 5: Update FeedViewModel's import**

In `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt`, find the existing import:

```kotlin
import net.kikin.nubecita.feature.feed.impl.data.LikeRepostRepository
```

Replace with:

```kotlin
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
```

FeedViewModel's body still injects `likeRepostRepository` and calls `like`/`unlike`/`repost`/`unrepost` directly — that's preserved for now. Task 13 refactors this away in favor of the cache.

- [ ] **Step 6: Compile to verify the move worked**

Run: `./gradlew :core:post-interactions:compileDebugKotlin :feature:feed:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run the existing feed unit tests to confirm no behavior change:
`./gradlew :feature:feed:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL with all pre-existing tests passing.

- [ ] **Step 7: Verify nothing else broke**

```bash
grep -rn "net\.kikin\.nubecita\.feature\.feed\.impl\.data\.LikeRepostRepository" --include="*.kt" feature/ core/ app/
```
Expected: zero hits.

- [ ] **Step 8: Commit**

```bash
git add core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/LikeRepostRepository.kt \
        core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultLikeRepostRepository.kt \
        feature/feed/impl/build.gradle.kts \
        feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt
git add -u feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/data/ \
           feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/di/
git commit -m "$(cat <<'EOF'
refactor(core/post-interactions): promote LikeRepostRepository from :feature:feed:impl

The interface + impl move from package-internal in :feature:feed:impl to
public in :core/post-interactions. FeedViewModel keeps its current direct
usage (no behavior change yet); Task 13 swaps it for cache delegation.
The old Hilt module is deleted; the new module lands in Task 8.

Refs: nubecita-78p
EOF
)"
```

---

## Task 7: Add `DefaultPostInteractionsCache` stub

Empty implementation skeleton that throws `NotImplementedError` in every method. Sets up the class structure + `@Inject` constructor + dependencies. The TDD tasks (8–15) fill in real behavior method-by-method.

**Files:**
- Create: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt`

- [ ] **Step 1: Create the stub**

```kotlin
package net.kikin.nubecita.core.postinteractions.internal

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.data.models.PostUi

/**
 * Default implementation of [PostInteractionsCache]. See the interface
 * KDoc for the full contract.
 *
 * Holds:
 * - `_cache`: the canonical state map. Exposed read-only via [state].
 * - `likeJobs` / `repostJobs`: single-flight guards keyed on postUri.
 *   Cleared when a network call resolves (in `finally`).
 *
 * Coroutine scope: writes are launched on the application-scoped
 * dispatcher (`@ApplicationScope CoroutineScope`) so they survive the
 * screen that initiated them leaving the back stack. Awaits the launched
 * job before returning so the calling VM gets the Result it can route.
 */
@Singleton
internal class DefaultPostInteractionsCache
    @Inject
    constructor(
        private val likeRepostRepository: LikeRepostRepository,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : PostInteractionsCache {
        private val _cache = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())

        override val state: StateFlow<PersistentMap<String, PostInteractionState>> = _cache.asStateFlow()

        override fun seed(posts: List<PostUi>) {
            TODO("Task 11: seed merger rules")
        }

        override suspend fun toggleLike(postUri: String, postCid: String): Result<Unit> {
            TODO("Tasks 8, 9, 10: toggleLike happy + failure + single-flight paths")
        }

        override suspend fun toggleRepost(postUri: String, postCid: String): Result<Unit> {
            TODO("Task 12: toggleRepost mirror")
        }

        override fun clear() {
            TODO("Task 13: clear()")
        }
    }
```

- [ ] **Step 2: Verify `ApplicationScope` exists in `:core/common`**

Run:
```bash
grep -rn "annotation class ApplicationScope\|object ApplicationScope" core/common/src/main/kotlin/
```

If this returns zero matches, the project doesn't have an `@ApplicationScope` qualifier — we need a different scope binding. Check how `:core/auth/DefaultAuthRepository` injects its `CoroutineScope`:

```bash
grep -nE "CoroutineScope|@ApplicationScope|@Scope" core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/DefaultAuthRepository.kt
```

If `:core/auth` uses a different qualifier (e.g., `@AuthScope`, `@AppScope`, or a `coroutineScope: CoroutineScope` injected directly with no qualifier), match its pattern. Update the constructor and import in the file accordingly.

If no application-scoped CoroutineScope is available in the codebase yet, **STOP and report** — that's a missing infra piece outside this task's scope. The plan assumes one exists.

- [ ] **Step 3: Compile**

Run: `./gradlew :core:post-interactions:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): + DefaultPostInteractionsCache skeleton

@Singleton @Inject constructor takes LikeRepostRepository +
ApplicationScope. _cache is the canonical MutableStateFlow; state
exposes it read-only. All methods stub TODO() — the next 6 TDD tasks
fill in toggleLike happy / failure / single-flight, seed merger,
toggleRepost, and clear.

Refs: nubecita-78p
EOF
)"
```

---

## Task 8: TDD — `toggleLike` happy paths (like and unlike)

Two scenarios from the spec: §Tests #1, #2, #3 collapsed into one task (they share the same implementation surface).

**Files:**
- Create: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/internal/FakeLikeRepostRepository.kt`
- Create: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt`
- Modify: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt`

- [ ] **Step 1: Create the test fake**

```kotlin
package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [LikeRepostRepository] for [DefaultPostInteractionsCache]
 * tests. Tracks call counts and per-method captured args; lets the
 * test class set the next return value before each call.
 */
internal class FakeLikeRepostRepository : LikeRepostRepository {
    val likeCalls = AtomicInteger(0)
    val unlikeCalls = AtomicInteger(0)
    val repostCalls = AtomicInteger(0)
    val unrepostCalls = AtomicInteger(0)

    var lastLikedSubject: StrongRef? = null
    var lastUnlikedUri: AtUri? = null
    var lastRepostedSubject: StrongRef? = null
    var lastUnrepostedUri: AtUri? = null

    var nextLikeResult: Result<AtUri> = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/auto"))
    var nextUnlikeResult: Result<Unit> = Result.success(Unit)
    var nextRepostResult: Result<AtUri> = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.repost/auto"))
    var nextUnrepostResult: Result<Unit> = Result.success(Unit)

    /** Optional latch; set non-zero to make calls suspend for N ms before returning. */
    var nextDelayMs: Long = 0

    override suspend fun like(post: StrongRef): Result<AtUri> {
        likeCalls.incrementAndGet()
        lastLikedSubject = post
        if (nextDelayMs > 0) kotlinx.coroutines.delay(nextDelayMs)
        return nextLikeResult
    }

    override suspend fun unlike(likeUri: AtUri): Result<Unit> {
        unlikeCalls.incrementAndGet()
        lastUnlikedUri = likeUri
        if (nextDelayMs > 0) kotlinx.coroutines.delay(nextDelayMs)
        return nextUnlikeResult
    }

    override suspend fun repost(post: StrongRef): Result<AtUri> {
        repostCalls.incrementAndGet()
        lastRepostedSubject = post
        if (nextDelayMs > 0) kotlinx.coroutines.delay(nextDelayMs)
        return nextRepostResult
    }

    override suspend fun unrepost(repostUri: AtUri): Result<Unit> {
        unrepostCalls.incrementAndGet()
        lastUnrepostedUri = repostUri
        if (nextDelayMs > 0) kotlinx.coroutines.delay(nextDelayMs)
        return nextUnrepostResult
    }
}
```

- [ ] **Step 2: Create the test class with the first two failing tests**

```kotlin
package net.kikin.nubecita.core.postinteractions

import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionsCache
import net.kikin.nubecita.core.postinteractions.internal.FakeLikeRepostRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultPostInteractionsCacheTest {

    @Test
    fun `toggleLike from empty cache emits optimistic then success and calls like`() =
        runTest {
            val fake = FakeLikeRepostRepository().apply {
                nextLikeResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/abc"))
            }
            val cache = newCache(fake)

            val result = cache.toggleLike(postUri = "at://did:plc:author/app.bsky.feed.post/post-1", postCid = "bafy123")
            advanceUntilIdle()

            assertTrue(result.isSuccess, "toggleLike from empty cache MUST succeed")
            val state = cache.state.value["at://did:plc:author/app.bsky.feed.post/post-1"]
            assertEquals("at://did:plc:viewer/app.bsky.feed.like/abc", state?.viewerLikeUri,
                "viewerLikeUri MUST hold the wire-returned AtUri after success")
            assertEquals(1L, state?.likeCount, "likeCount MUST be incremented from 0 to 1")
            assertEquals(PendingState.None, state?.pendingLikeWrite,
                "pendingLikeWrite MUST clear on success")
            assertEquals(1, fake.likeCalls.get(), "like() MUST be called exactly once")
            assertEquals(0, fake.unlikeCalls.get(), "unlike() MUST NOT be called")
        }

    @Test
    fun `toggleLike from seeded not-liked state increments count and calls like`() =
        runTest {
            val fake = FakeLikeRepostRepository().apply {
                nextLikeResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/abc"))
            }
            val cache = newCache(fake)
            cache.seedDirectly("at://post-2", PostInteractionState(viewerLikeUri = null, likeCount = 10))

            val result = cache.toggleLike("at://post-2", "bafy222")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val state = cache.state.value["at://post-2"]
            assertEquals("at://did:plc:viewer/app.bsky.feed.like/abc", state?.viewerLikeUri)
            assertEquals(11L, state?.likeCount, "count 10 → 11")
        }

    @Test
    fun `toggleLike from seeded liked state decrements count and calls unlike`() =
        runTest {
            val fake = FakeLikeRepostRepository().apply {
                nextUnlikeResult = Result.success(Unit)
            }
            val cache = newCache(fake)
            cache.seedDirectly(
                "at://post-3",
                PostInteractionState(viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/old", likeCount = 11),
            )

            val result = cache.toggleLike("at://post-3", "bafy333")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val state = cache.state.value["at://post-3"]
            assertNull(state?.viewerLikeUri, "unlike clears viewerLikeUri")
            assertEquals(10L, state?.likeCount, "count 11 → 10")
            assertEquals(1, fake.unlikeCalls.get())
            assertEquals(AtUri("at://did:plc:viewer/app.bsky.feed.like/old"), fake.lastUnlikedUri)
            assertEquals(0, fake.likeCalls.get(), "like() MUST NOT be called on unlike path")
        }

    // -- Test helpers ---------------------------------------------------------

    private fun TestScope.newCache(fake: FakeLikeRepostRepository): DefaultPostInteractionsCache =
        DefaultPostInteractionsCache(
            likeRepostRepository = fake,
            applicationScope = this,
        )

    /**
     * Direct injection helper for tests that need pre-existing state without
     * going through `seed(posts: List<PostUi>)` (which is the subject of
     * separate tests). Reaches into the private `_cache` via reflection so
     * the production seed contract isn't entangled.
     */
    private fun DefaultPostInteractionsCache.seedDirectly(postUri: String, state: PostInteractionState) {
        val field = DefaultPostInteractionsCache::class.java.getDeclaredField("_cache")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as kotlinx.coroutines.flow.MutableStateFlow<kotlinx.collections.immutable.PersistentMap<String, PostInteractionState>>
        flow.value = flow.value.put(postUri, state)
    }
}
```

- [ ] **Step 3: Run the tests; verify they fail**

Run: `./gradlew :core:post-interactions:testDebugUnitTest`
Expected: 3 tests FAIL with `NotImplementedError` thrown by `toggleLike`'s TODO.

- [ ] **Step 4: Implement `toggleLike` for the happy paths**

Replace `DefaultPostInteractionsCache.toggleLike`'s body:

```kotlin
override suspend fun toggleLike(postUri: String, postCid: String): Result<Unit> {
    val before = _cache.value[postUri] ?: PostInteractionState()
    val optimistic = before.copy(
        viewerLikeUri = if (before.viewerLikeUri == null) PENDING_LIKE_SENTINEL else null,
        likeCount = (before.likeCount + if (before.viewerLikeUri == null) 1 else -1).coerceAtLeast(0),
        pendingLikeWrite = PendingState.Pending,
    )
    _cache.update { it.put(postUri, optimistic) }

    val callResult = runCatching {
        if (before.viewerLikeUri == null) {
            likeRepostRepository.like(StrongRef(uri = AtUri(postUri), cid = Cid(postCid))).getOrThrow()
        } else {
            likeRepostRepository.unlike(AtUri(before.viewerLikeUri)).getOrThrow()
            null
        }
    }

    return callResult.fold(
        onSuccess = { newLikeUri: AtUri? ->
            _cache.update {
                it.put(
                    postUri,
                    optimistic.copy(
                        viewerLikeUri = newLikeUri?.raw,
                        pendingLikeWrite = PendingState.None,
                    ),
                )
            }
            Result.success(Unit)
        },
        onFailure = { throwable ->
            _cache.update { it.put(postUri, before) }
            Result.failure(throwable)
        },
    )
}
```

Add the imports the implementation needs (at the top of `DefaultPostInteractionsCache.kt`):

```kotlin
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import kotlinx.coroutines.flow.update
import net.kikin.nubecita.core.postinteractions.PendingState
```

- [ ] **Step 5: Re-run the tests; verify they pass**

Run: `./gradlew :core:post-interactions:testDebugUnitTest`
Expected: All 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/post-interactions/src/test/kotlin/ \
        core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): toggleLike happy paths

Optimistic flip writes PENDING_LIKE_SENTINEL + count delta + pending=Pending
to _cache; calls like() or unlike() via the injected repository; on
success promotes pending to the wire-returned AtUri and clears pending.
3 tests cover: empty-cache like, seeded not-liked → like, seeded liked
→ unlike. Failure path + single-flight + seed land in next tasks.

Refs: nubecita-78p
EOF
)"
```

---

## Task 9: TDD — `toggleLike` failure rollback

Adds the failure branch.

**Files:**
- Modify: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt`

The implementation in Task 8 already includes the rollback branch — this task adds the test that exercises it. If the test passes immediately, that's the right outcome (regression coverage).

- [ ] **Step 1: Append the failure test**

After the third `@Test` in the test file, add:

```kotlin
    @Test
    fun `toggleLike rolls back state and returns failure on network error`() =
        runTest {
            val networkFailure = IllegalStateException("net down")
            val fake = FakeLikeRepostRepository().apply {
                nextLikeResult = Result.failure(networkFailure)
            }
            val cache = newCache(fake)
            val initial = PostInteractionState(viewerLikeUri = null, likeCount = 7)
            cache.seedDirectly("at://post-fail", initial)

            val result = cache.toggleLike("at://post-fail", "bafyFAIL")
            advanceUntilIdle()

            assertTrue(result.isFailure, "toggleLike MUST surface the underlying failure")
            assertEquals(networkFailure, result.exceptionOrNull())

            val state = cache.state.value["at://post-fail"]
            assertEquals(initial.viewerLikeUri, state?.viewerLikeUri,
                "rollback MUST restore pre-tap viewerLikeUri (null)")
            assertEquals(initial.likeCount, state?.likeCount,
                "rollback MUST restore pre-tap likeCount (7)")
            assertEquals(PendingState.None, state?.pendingLikeWrite,
                "rollback MUST clear pendingLikeWrite")
        }
```

- [ ] **Step 2: Run the tests; verify the new one passes (no impl change)**

Run: `./gradlew :core:post-interactions:testDebugUnitTest`
Expected: All 4 tests PASS — the rollback branch was already implemented in Task 8, this test is regression coverage.

- [ ] **Step 3: Commit**

```bash
git add core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt
git commit -m "$(cat <<'EOF'
test(core/post-interactions): toggleLike failure rolls back to pre-tap state

Regression coverage for the rollback branch added in the previous
commit. Asserts viewerLikeUri, likeCount, and pendingLikeWrite all
restore to their pre-tap snapshot on failure, and that the underlying
throwable surfaces in Result.failure for caller routing.

Refs: nubecita-78p
EOF
)"
```

---

## Task 10: TDD — `toggleLike` single-flight on double-tap

Adds the single-flight guard. The Task 8 implementation does NOT yet guard against concurrent calls; this task adds it.

**Files:**
- Modify: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt`
- Modify: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt`

- [ ] **Step 1: Append the failing test**

In `DefaultPostInteractionsCacheTest.kt`, after the failure test, append:

```kotlin
    @Test
    fun `toggleLike is single-flight per postUri — double-tap is absorbed`() =
        runTest {
            val fake = FakeLikeRepostRepository().apply {
                nextDelayMs = 1_000 // hold the in-flight call so a second arrives mid-flight
                nextLikeResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/x"))
            }
            val cache = newCache(fake)

            // Fire two like calls back-to-back without awaiting the first.
            val first = kotlinx.coroutines.async { cache.toggleLike("at://post-sf", "bafySF") }
            val second = kotlinx.coroutines.async { cache.toggleLike("at://post-sf", "bafySF") }
            advanceUntilIdle()

            val firstResult = first.await()
            val secondResult = second.await()

            assertTrue(firstResult.isSuccess)
            assertTrue(secondResult.isSuccess, "second toggle MUST return synthetic success (no error)")
            assertEquals(1, fake.likeCalls.get(),
                "single-flight: like() MUST be called exactly once for the same postUri")
        }
```

- [ ] **Step 2: Run the test; verify it fails**

Run: `./gradlew :core:post-interactions:testDebugUnitTest --tests "*single-flight*"`
Expected: FAIL — assertion expects `likeCalls == 1` but the impl currently calls twice (no guard).

- [ ] **Step 3: Add the single-flight guard**

In `DefaultPostInteractionsCache.kt`, add a `likeJobs` map at the class level (near `_cache`):

```kotlin
private val likeJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
```

Then refactor `toggleLike`'s body to launch in a single-flight Job:

```kotlin
override suspend fun toggleLike(postUri: String, postCid: String): Result<Unit> {
    // Single-flight: if a like call is already in flight for this postUri,
    // return synthetic success without firing anything.
    if (likeJobs[postUri]?.isActive == true) {
        return Result.success(Unit)
    }

    val before = _cache.value[postUri] ?: PostInteractionState()
    val optimistic = before.copy(
        viewerLikeUri = if (before.viewerLikeUri == null) PENDING_LIKE_SENTINEL else null,
        likeCount = (before.likeCount + if (before.viewerLikeUri == null) 1 else -1).coerceAtLeast(0),
        pendingLikeWrite = PendingState.Pending,
    )
    _cache.update { it.put(postUri, optimistic) }

    val job = applicationScope.async {
        val callResult = runCatching {
            if (before.viewerLikeUri == null) {
                likeRepostRepository.like(StrongRef(uri = AtUri(postUri), cid = Cid(postCid))).getOrThrow()
            } else {
                likeRepostRepository.unlike(AtUri(before.viewerLikeUri)).getOrThrow()
                null
            }
        }

        callResult.fold(
            onSuccess = { newLikeUri: AtUri? ->
                _cache.update {
                    it.put(
                        postUri,
                        optimistic.copy(
                            viewerLikeUri = newLikeUri?.raw,
                            pendingLikeWrite = PendingState.None,
                        ),
                    )
                }
                Result.success(Unit)
            },
            onFailure = { throwable ->
                _cache.update { it.put(postUri, before) }
                Result.failure(throwable)
            },
        )
    }
    likeJobs[postUri] = job
    return try {
        job.await()
    } finally {
        likeJobs.remove(postUri)
    }
}
```

Add the missing imports:

```kotlin
import kotlinx.coroutines.async
```

(`Job` is already implicit; `ConcurrentHashMap` is fully qualified inline.)

- [ ] **Step 4: Re-run all cache tests; verify all pass**

Run: `./gradlew :core:post-interactions:testDebugUnitTest`
Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt \
        core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): toggleLike single-flight per postUri

Concurrent toggleLike calls for the same postUri now route through one
in-flight Job per postUri (tracked in a ConcurrentHashMap). Second tap
returns synthetic Result.success(Unit) without firing the network call.
Double-tap test asserts exactly one like() invocation reaches the repo.
Job is removed from the map on completion (success or failure) so the
postUri can be toggled again later.

Refs: nubecita-78p
EOF
)"
```

---

## Task 11: TDD — `seed()` merger rules

Implements the seed function with the 3 merger rules from spec Decision 2.

**Files:**
- Modify: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt`
- Modify: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt`

- [ ] **Step 1: Append three failing tests**

```kotlin
    @Test
    fun `seed on empty cache writes wire data for every post`() =
        runTest {
            val cache = newCache(FakeLikeRepostRepository())
            val post = samplePost(
                id = "at://post-seed-1",
                viewerLikeUri = null,
                viewerRepostUri = null,
                likeCount = 5,
                repostCount = 2,
            )

            cache.seed(listOf(post))

            val state = cache.state.value["at://post-seed-1"]
            assertEquals(null, state?.viewerLikeUri)
            assertEquals(null, state?.viewerRepostUri)
            assertEquals(5L, state?.likeCount)
            assertEquals(2L, state?.repostCount)
            assertEquals(PendingState.None, state?.pendingLikeWrite)
        }

    @Test
    fun `seed preserves in-flight optimistic state against stale wire data`() =
        runTest {
            val cache = newCache(FakeLikeRepostRepository())
            val pendingState = PostInteractionState(
                viewerLikeUri = "at://pending:optimistic",
                likeCount = 6,
                pendingLikeWrite = PendingState.Pending,
            )
            cache.seedDirectly("at://post-pending", pendingState)

            // Wire data shows the like as not-yet-indexed (stale because the
            // appview hasn't caught up to the user's recent createRecord).
            val stalePost = samplePost(
                id = "at://post-pending",
                viewerLikeUri = null,
                likeCount = 5,
            )
            cache.seed(listOf(stalePost))

            val state = cache.state.value["at://post-pending"]
            assertEquals(pendingState, state,
                "seed MUST preserve in-flight optimistic state entirely while pending")
        }

    @Test
    fun `seed reseeds from wire when no write is pending and wire is fresh`() =
        runTest {
            val cache = newCache(FakeLikeRepostRepository())
            val existing = PostInteractionState(viewerLikeUri = null, likeCount = 5)
            cache.seedDirectly("at://post-reseed", existing)

            // Wire returns updated counts and a fresh-from-server like AtUri
            // (someone else may have liked between fetches).
            val freshPost = samplePost(
                id = "at://post-reseed",
                viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/fresh",
                likeCount = 8,
            )
            cache.seed(listOf(freshPost))

            val state = cache.state.value["at://post-reseed"]
            assertEquals("at://did:plc:viewer/app.bsky.feed.like/fresh", state?.viewerLikeUri)
            assertEquals(8L, state?.likeCount)
        }
```

Add the `samplePost` helper at the bottom of the test class (before the existing helpers):

```kotlin
    private fun samplePost(
        id: String = "at://did:plc:author/app.bsky.feed.post/p1",
        viewerLikeUri: String? = null,
        viewerRepostUri: String? = null,
        likeCount: Int = 0,
        repostCount: Int = 0,
    ): net.kikin.nubecita.data.models.PostUi =
        net.kikin.nubecita.data.models.PostUi(
            id = id,
            cid = "bafy-test",
            author = net.kikin.nubecita.data.models.AuthorUi(
                did = "did:plc:author",
                handle = "alice.bsky.social",
                displayName = "Alice",
                avatarUrl = null,
            ),
            text = "test post",
            embed = net.kikin.nubecita.data.models.EmbedUi.Empty,
            createdAt = kotlin.time.Instant.fromEpochMilliseconds(1_700_000_000_000L),
            stats = net.kikin.nubecita.data.models.PostStatsUi(
                replyCount = 0,
                repostCount = repostCount,
                likeCount = likeCount,
            ),
            viewer = net.kikin.nubecita.data.models.ViewerStateUi(
                isLikedByViewer = viewerLikeUri != null,
                likeUri = viewerLikeUri,
                isRepostedByViewer = viewerRepostUri != null,
                repostUri = viewerRepostUri,
            ),
        )
```

**Note:** the exact `PostUi` constructor shape may differ — open `data/models/src/main/kotlin/net/kikin/nubecita/data/models/PostUi.kt` first to confirm the field names + types, then adjust the helper. Required fields like `repostedBy: String? = null` etc. may need to be added.

- [ ] **Step 2: Run the tests; verify they fail**

Run: `./gradlew :core:post-interactions:testDebugUnitTest --tests "*seed*"`
Expected: 3 tests FAIL — `seed()` throws `NotImplementedError`.

- [ ] **Step 3: Implement `seed`**

Replace `DefaultPostInteractionsCache.seed`'s body:

```kotlin
override fun seed(posts: List<PostUi>) {
    _cache.update { current ->
        posts.fold(current) { acc, post ->
            val existing = acc[post.id]
            val merged = when {
                // In-flight optimistic state takes precedence over wire data.
                existing?.pendingLikeWrite == PendingState.Pending ||
                    existing?.pendingRepostWrite == PendingState.Pending -> existing
                // Cached likeUri non-null but wire says null: assume appview lag,
                // preserve cache. Same for repost.
                existing != null &&
                    existing.viewerLikeUri != null &&
                    post.viewer.likeUri == null -> existing
                existing != null &&
                    existing.viewerRepostUri != null &&
                    post.viewer.repostUri == null -> existing
                else -> PostInteractionState(
                    viewerLikeUri = post.viewer.likeUri,
                    viewerRepostUri = post.viewer.repostUri,
                    likeCount = post.stats.likeCount.toLong(),
                    repostCount = post.stats.repostCount.toLong(),
                )
            }
            acc.put(post.id, merged)
        }
    }
}
```

- [ ] **Step 4: Re-run all cache tests; verify all pass**

Run: `./gradlew :core:post-interactions:testDebugUnitTest`
Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt \
        core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): seed merger rules

seed() now folds wire posts into the cache with three merger rules:
(1) any post with pendingLikeWrite/pendingRepostWrite == Pending is
preserved entirely (in-flight optimistic state wins over stale wire);
(2) if cache has a non-null viewerLikeUri/viewerRepostUri but wire says
null, preserve cache (appview lag); (3) else reseed from wire. Same
shape for repost. 3 tests cover all three branches.

Refs: nubecita-78p
EOF
)"
```

---

## Task 12: TDD — refresh-during-in-flight integration

End-to-end test covering the existing FeedVM semantic: a `seed()` mid-`toggleLike` preserves the optimistic state, and the post-success state correctly promotes.

**Files:**
- Modify: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt`

- [ ] **Step 1: Append the integration test**

```kotlin
    @Test
    fun `seed during in-flight toggleLike preserves optimistic state then promotes on success`() =
        runTest {
            val fake = FakeLikeRepostRepository().apply {
                nextDelayMs = 500 // hold the like in flight
                nextLikeResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/promoted"))
            }
            val cache = newCache(fake)
            cache.seedDirectly("at://post-refresh", PostInteractionState(viewerLikeUri = null, likeCount = 3))

            // Fire toggleLike; it suspends inside the fake.
            val toggle = kotlinx.coroutines.async { cache.toggleLike("at://post-refresh", "bafyRF") }
            // Run until the optimistic emission has landed but the fake is still suspended.
            kotlinx.coroutines.test.runCurrent()

            // Now simulate a refresh: wire returns stale data (no like, count 3).
            val stale = samplePost(
                id = "at://post-refresh",
                viewerLikeUri = null,
                likeCount = 3,
            )
            cache.seed(listOf(stale))

            val midFlight = cache.state.value["at://post-refresh"]
            assertEquals(PENDING_LIKE_SENTINEL_FOR_TEST, midFlight?.viewerLikeUri,
                "seed during in-flight MUST preserve the optimistic sentinel")
            assertEquals(4L, midFlight?.likeCount,
                "seed during in-flight MUST preserve the optimistic count delta")
            assertEquals(PendingState.Pending, midFlight?.pendingLikeWrite)

            // Now let the fake complete.
            advanceUntilIdle()
            val finalResult = toggle.await()
            assertTrue(finalResult.isSuccess)
            val final = cache.state.value["at://post-refresh"]
            assertEquals("at://did:plc:viewer/app.bsky.feed.like/promoted", final?.viewerLikeUri)
            assertEquals(4L, final?.likeCount)
            assertEquals(PendingState.None, final?.pendingLikeWrite)
        }
```

The test references `PENDING_LIKE_SENTINEL_FOR_TEST` — define it as a constant at the top of the test class so the value isn't repeated as a magic string. The PENDING constant itself is package-internal to `:core/post-interactions/internal/`, but the test is in the public `core.postinteractions` package, so we can't import it directly. Match the spec literal:

```kotlin
private const val PENDING_LIKE_SENTINEL_FOR_TEST = "at://pending:optimistic"
```

Place it just above the first `@Test`.

- [ ] **Step 2: Run the test; verify it passes**

Run: `./gradlew :core:post-interactions:testDebugUnitTest --tests "*refresh*"`
Expected: PASS — the seed merger rules from Task 11 already handle this case; this test is integration coverage.

- [ ] **Step 3: Run the full cache test suite**

Run: `./gradlew :core:post-interactions:testDebugUnitTest`
Expected: All 9 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt
git commit -m "$(cat <<'EOF'
test(core/post-interactions): refresh-during-in-flight integration coverage

Covers the existing FeedViewModel.toggleLike semantic: seed() called
mid-toggleLike preserves the optimistic state (sentinel + count delta
+ pending=Pending) against stale wire data; on toggleLike completion,
the pending sentinel promotes to the real AtUri. Integration of
single-flight + seed merger + promote pipeline.

Refs: nubecita-78p
EOF
)"
```

---

## Task 13: TDD — `clear()`

**Files:**
- Modify: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt`
- Modify: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt`

- [ ] **Step 1: Append the failing test**

```kotlin
    @Test
    fun `clear empties the cache state`() =
        runTest {
            val cache = newCache(FakeLikeRepostRepository())
            cache.seedDirectly("at://post-a", PostInteractionState(viewerLikeUri = null, likeCount = 1))
            cache.seedDirectly("at://post-b", PostInteractionState(viewerLikeUri = "at://like/x", likeCount = 9))
            assertEquals(2, cache.state.value.size)

            cache.clear()

            assertTrue(cache.state.value.isEmpty(), "clear MUST empty the cache state")
        }
```

- [ ] **Step 2: Run; verify failure**

Run: `./gradlew :core:post-interactions:testDebugUnitTest --tests "*clear*"`
Expected: FAIL — `clear()` throws `NotImplementedError`.

- [ ] **Step 3: Implement `clear()`**

Replace `DefaultPostInteractionsCache.clear`'s body:

```kotlin
override fun clear() {
    _cache.value = persistentMapOf()
}
```

- [ ] **Step 4: Re-run; verify pass**

Run: `./gradlew :core:post-interactions:testDebugUnitTest`
Expected: All 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt \
        core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): clear() resets cache state

Sets _cache.value back to persistentMapOf(). Used by the sign-out flow
to drop session-touched state before the user re-authenticates.

Refs: nubecita-78p
EOF
)"
```

---

## Task 14: TDD — `toggleRepost` mirror

Mirrors the like behavior for repost. Same 3 cases as Task 8 but for the repost path.

**Files:**
- Modify: `core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt`
- Modify: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt`

- [ ] **Step 1: Append the failing tests**

```kotlin
    @Test
    fun `toggleRepost from seeded not-reposted state increments count and calls repost`() =
        runTest {
            val fake = FakeLikeRepostRepository().apply {
                nextRepostResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.repost/r1"))
            }
            val cache = newCache(fake)
            cache.seedDirectly("at://post-rp", PostInteractionState(viewerRepostUri = null, repostCount = 2))

            val result = cache.toggleRepost("at://post-rp", "bafyRP")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val state = cache.state.value["at://post-rp"]
            assertEquals("at://did:plc:viewer/app.bsky.feed.repost/r1", state?.viewerRepostUri)
            assertEquals(3L, state?.repostCount)
            assertEquals(1, fake.repostCalls.get())
        }

    @Test
    fun `toggleRepost from seeded reposted state decrements count and calls unrepost`() =
        runTest {
            val fake = FakeLikeRepostRepository()
            val cache = newCache(fake)
            cache.seedDirectly(
                "at://post-unrp",
                PostInteractionState(viewerRepostUri = "at://did:plc:viewer/app.bsky.feed.repost/old", repostCount = 3),
            )

            val result = cache.toggleRepost("at://post-unrp", "bafyUNRP")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val state = cache.state.value["at://post-unrp"]
            assertNull(state?.viewerRepostUri)
            assertEquals(2L, state?.repostCount)
            assertEquals(1, fake.unrepostCalls.get())
        }

    @Test
    fun `toggleRepost rolls back state and returns failure on network error`() =
        runTest {
            val networkFailure = IllegalStateException("net down")
            val fake = FakeLikeRepostRepository().apply {
                nextRepostResult = Result.failure(networkFailure)
            }
            val cache = newCache(fake)
            val initial = PostInteractionState(viewerRepostUri = null, repostCount = 4)
            cache.seedDirectly("at://post-rp-fail", initial)

            val result = cache.toggleRepost("at://post-rp-fail", "bafyRPFAIL")
            advanceUntilIdle()

            assertTrue(result.isFailure)
            assertEquals(networkFailure, result.exceptionOrNull())
            val state = cache.state.value["at://post-rp-fail"]
            assertEquals(initial.viewerRepostUri, state?.viewerRepostUri)
            assertEquals(initial.repostCount, state?.repostCount)
            assertEquals(PendingState.None, state?.pendingRepostWrite)
        }
```

- [ ] **Step 2: Run; verify failure**

Run: `./gradlew :core:post-interactions:testDebugUnitTest --tests "*toggleRepost*"`
Expected: 3 tests FAIL — `toggleRepost` throws `NotImplementedError`.

- [ ] **Step 3: Implement `toggleRepost`**

Add a `repostJobs` map at the class level (next to `likeJobs`):

```kotlin
private val repostJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
```

Replace `toggleRepost`'s body:

```kotlin
override suspend fun toggleRepost(postUri: String, postCid: String): Result<Unit> {
    if (repostJobs[postUri]?.isActive == true) {
        return Result.success(Unit)
    }

    val before = _cache.value[postUri] ?: PostInteractionState()
    val optimistic = before.copy(
        viewerRepostUri = if (before.viewerRepostUri == null) PENDING_REPOST_SENTINEL else null,
        repostCount = (before.repostCount + if (before.viewerRepostUri == null) 1 else -1).coerceAtLeast(0),
        pendingRepostWrite = PendingState.Pending,
    )
    _cache.update { it.put(postUri, optimistic) }

    val job = applicationScope.async {
        val callResult = runCatching {
            if (before.viewerRepostUri == null) {
                likeRepostRepository.repost(StrongRef(uri = AtUri(postUri), cid = Cid(postCid))).getOrThrow()
            } else {
                likeRepostRepository.unrepost(AtUri(before.viewerRepostUri)).getOrThrow()
                null
            }
        }

        callResult.fold(
            onSuccess = { newRepostUri: AtUri? ->
                _cache.update {
                    it.put(
                        postUri,
                        optimistic.copy(
                            viewerRepostUri = newRepostUri?.raw,
                            pendingRepostWrite = PendingState.None,
                        ),
                    )
                }
                Result.success(Unit)
            },
            onFailure = { throwable ->
                _cache.update { it.put(postUri, before) }
                Result.failure(throwable)
            },
        )
    }
    repostJobs[postUri] = job
    return try {
        job.await()
    } finally {
        repostJobs.remove(postUri)
    }
}
```

Add the `PENDING_REPOST_SENTINEL` import:

```kotlin
import net.kikin.nubecita.core.postinteractions.internal.PENDING_REPOST_SENTINEL
```

Wait, the import is already in the same package — drop the import; just use `PENDING_REPOST_SENTINEL` directly. Same for `PENDING_LIKE_SENTINEL` (it's in `internal/PendingSentinels.kt`, same `internal` subpackage as `DefaultPostInteractionsCache.kt`).

- [ ] **Step 4: Re-run; verify all pass**

Run: `./gradlew :core:post-interactions:testDebugUnitTest`
Expected: All 13 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/post-interactions/src/test/kotlin/net/kikin/nubecita/core/postinteractions/DefaultPostInteractionsCacheTest.kt \
        core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionsCache.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): toggleRepost with single-flight + rollback

Mirror of toggleLike. Maintains its own repostJobs single-flight map.
3 new tests cover seeded not-reposted → repost (count +1), seeded
reposted → unrepost (count -1), and network failure → rollback to
pre-tap state.

Refs: nubecita-78p
EOF
)"
```

---

## Task 15: Add `PostInteractionsModule` (Hilt bindings)

Wires `@Binds` for `PostInteractionsCache` → `DefaultPostInteractionsCache` and `LikeRepostRepository` → `DefaultLikeRepostRepository`.

**Files:**
- Create: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/di/PostInteractionsModule.kt`

- [ ] **Step 1: Create the module**

```kotlin
package net.kikin.nubecita.core.postinteractions.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.internal.DefaultLikeRepostRepository
import net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionsCache

/**
 * Hilt bindings for [PostInteractionsCache] and [LikeRepostRepository].
 *
 * The cache is `@Singleton`-scoped (matches the class-level annotation on
 * [DefaultPostInteractionsCache]) so all VMs across the app share one
 * canonical state map. The repository is also `@Singleton` to avoid
 * re-wrapping the atproto service per injection point.
 *
 * The class is publicly addressable (not `internal`) so future test
 * modules in any feature module can replace it via
 * `@TestInstallIn(replaces = [PostInteractionsModule::class])`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PostInteractionsModule {
    @Binds
    @Singleton
    internal abstract fun bindPostInteractionsCache(
        impl: DefaultPostInteractionsCache,
    ): PostInteractionsCache

    @Binds
    @Singleton
    internal abstract fun bindLikeRepostRepository(
        impl: DefaultLikeRepostRepository,
    ): LikeRepostRepository
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :core:post-interactions:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify the full module builds + tests still pass**

Run: `./gradlew :core:post-interactions:assembleDebug :core:post-interactions:testDebugUnitTest`
Expected: BUILD SUCCESSFUL with all 13 cache tests passing.

- [ ] **Step 4: Verify Feed can resolve the LikeRepostRepository binding now from :core**

Run: `./gradlew :feature:feed:impl:compileDebugKotlin :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. The old `LikeRepostRepositoryModule.kt` in `:feature:feed:impl` was deleted in Task 6; the new module here is now the only binding source.

If you get a `[Dagger/MissingBinding]` error referencing `LikeRepostRepository`, that's expected ONLY if a stale `bindLikeRepostRepository` in the Hilt graph predates the move — clean the build directories: `./gradlew clean :app:assembleDebug` and re-try.

- [ ] **Step 5: Commit**

```bash
git add core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/di/PostInteractionsModule.kt
git commit -m "$(cat <<'EOF'
feat(core/post-interactions): + PostInteractionsModule Hilt bindings

Binds PostInteractionsCache → DefaultPostInteractionsCache and
LikeRepostRepository → DefaultLikeRepostRepository, both Singleton.
Public abstract class so downstream feature module tests can
@TestInstallIn-replace it. After this commit, :app and :feature:feed:impl
both link cleanly — the LikeRepostRepository binding sits in :core now.

Refs: nubecita-78p
EOF
)"
```

---

## Task 16: Migrate `FeedViewModel` to the cache

Deletes the ~150-line `toggleLike` / `toggleRepost` optimistic-with-rollback logic. Replaces with cache delegation + subscription + seed.

**Files:**
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt`

- [ ] **Step 1: Read the current FeedViewModel**

Open the file and locate:
- The `@AssistedInject` / `@Inject constructor` (FeedViewModel's exact injection shape — adjust the new dep accordingly).
- The `init {}` block (where the cache subscription will go).
- The `OnLikeClicked` / `OnRepostClicked` event handlers in `handleEvent`.
- The `toggleLike(post: PostUi)` and `toggleRepost(post: PostUi)` private methods (lines ~222-375 from the spec context).
- The existing `likeRepostRepository: LikeRepostRepository` constructor param.

You'll modify all of these.

- [ ] **Step 2: Add the cache dependency, remove the direct repository**

In FeedViewModel's constructor, replace:

```kotlin
private val likeRepostRepository: LikeRepostRepository,
```

with:

```kotlin
private val postInteractionsCache: PostInteractionsCache,
```

Replace the import:

```kotlin
// Remove:
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
// Add:
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
```

- [ ] **Step 3: Add the cache subscription in `init`**

Locate the existing `init {}` block (it likely contains `loadFeed()` or similar). Add a subscription before the existing logic:

```kotlin
init {
    viewModelScope.launch {
        postInteractionsCache.state
            .map { interactionMap -> uiState.value.feedItems.applyInteractions(interactionMap) }
            .distinctUntilChanged()
            .collect { merged -> setState { copy(feedItems = merged) } }
    }
    // ... existing init logic (loadFeed, etc.) unchanged ...
}
```

Add the helper extension at file-scope (just below the FeedViewModel class declaration, or in a small file in the same package):

```kotlin
private fun ImmutableList<FeedItemUi>.applyInteractions(
    map: PersistentMap<String, PostInteractionState>,
): ImmutableList<FeedItemUi> =
    map { item ->
        when (item) {
            is FeedItemUi.Post -> {
                val state = map[item.post.id] ?: return@map item
                item.copy(post = item.post.mergeInteractionState(state))
            }
            // Pass through any non-Post item types unchanged.
            else -> item
        }
    }.toImmutableList()
```

Adjust the `when` branches to match the actual `FeedItemUi` sealed sum's variants (likely `FeedItemUi.Post`, `FeedItemUi.ChainStart`, `FeedItemUi.ChainEnd`, etc. — pass through whatever isn't a Post).

Add imports:

```kotlin
import kotlinx.collections.immutable.PersistentMap
import net.kikin.nubecita.core.postinteractions.PostInteractionState
```

- [ ] **Step 4: Replace `OnLikeClicked` and `OnRepostClicked` handlers in `handleEvent`**

Locate the event handlers. Replace:

```kotlin
is FeedEvent.OnLikeClicked -> toggleLike(event.post)
is FeedEvent.OnRepostClicked -> toggleRepost(event.post)
```

with:

```kotlin
is FeedEvent.OnLikeClicked -> viewModelScope.launch {
    postInteractionsCache.toggleLike(event.post.id, event.post.cid)
        .onFailure { sendEffect(FeedEffect.ShowError(it.toFeedError())) }
}
is FeedEvent.OnRepostClicked -> viewModelScope.launch {
    postInteractionsCache.toggleRepost(event.post.id, event.post.cid)
        .onFailure { sendEffect(FeedEffect.ShowError(it.toFeedError())) }
}
```

- [ ] **Step 5: Delete `toggleLike` and `toggleRepost` methods**

Delete the two `private fun toggleLike(post: PostUi)` and `private fun toggleRepost(post: PostUi)` methods entirely (lines roughly 222-375 from your earlier reading). Approximately 150 lines come out.

If the `private fun Throwable.toFeedError()` extension at the bottom is only used by `toggleLike` / `toggleRepost`, move/preserve it — the new event handlers still call it. Verify it's still referenced.

If `findPost` / `replacePost` extensions on `ImmutableList<FeedItemUi>` were only used by the deleted methods, delete them too. If they have other callers, leave them.

- [ ] **Step 6: Add `cache.seed(...)` after each wire fetch**

Locate the `loadFeed()` / `refresh()` / `loadMore()` (or similarly named) methods. After each gets a wire response and BEFORE the `setState { copy(feedItems = …) }` call, insert:

```kotlin
postInteractionsCache.seed(page.posts.filterIsInstance<FeedItemUi.Post>().map { it.post })
```

The exact pattern depends on the wire-response shape. If the loader receives a `List<PostUi>` directly (not `List<FeedItemUi>`), use that:

```kotlin
postInteractionsCache.seed(page.posts)  // if posts is List<PostUi>
```

Confirm the wire-response type by reading the existing setState call's argument expression.

- [ ] **Step 7: Compile**

Run: `./gradlew :feature:feed:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt
git commit -m "$(cat <<'EOF'
refactor(feature/feed/impl): delegate toggleLike/toggleRepost to PostInteractionsCache

FeedViewModel no longer owns the 150-line optimistic-flip-with-rollback
logic for like/repost. Event handlers dispatch through
postInteractionsCache.toggleLike/toggleRepost and route failures via
FeedEffect.ShowError. init subscribes to cache.state and projects via
mergeInteractionState onto feedItems. Each wire fetch calls cache.seed
to merge wire data with any in-flight optimistic state.

Refs: nubecita-78p
EOF
)"
```

---

## Task 17: Update `FeedViewModelTest`

Replace the old `FakeLikeRepostRepository` with a `FakePostInteractionsCache`. Delete tests that the cache now owns; rewrite the dispatch + error-routing tests.

**Files:**
- Modify: `feature/feed/impl/src/test/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModelTest.kt`

- [ ] **Step 1: Read the existing test file**

Identify which tests cover:
- The optimistic flip + rollback logic now owned by the cache → DELETE these tests.
- Dispatch (event → cache method called) → REWRITE.
- Error routing (failure → ShowError effect) → REWRITE.
- Other Feed-specific behavior (chain handling, pagination, etc.) → KEEP unchanged.

- [ ] **Step 2: Create the test fake**

In the same test directory, create `FakePostInteractionsCache.kt`:

```kotlin
package net.kikin.nubecita.feature.feed.impl

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.data.models.PostUi

internal class FakePostInteractionsCache : PostInteractionsCache {
    val toggleLikeCalls = AtomicInteger(0)
    val toggleRepostCalls = AtomicInteger(0)
    val seedCalls = AtomicInteger(0)
    val clearCalls = AtomicInteger(0)

    val lastToggleLikeArgs: MutableList<Pair<String, String>> = mutableListOf()
    val lastToggleRepostArgs: MutableList<Pair<String, String>> = mutableListOf()
    val lastSeedPosts: MutableList<List<PostUi>> = mutableListOf()

    var nextToggleLikeResult: Result<Unit> = Result.success(Unit)
    var nextToggleRepostResult: Result<Unit> = Result.success(Unit)

    private val _state = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())
    override val state: StateFlow<PersistentMap<String, PostInteractionState>> = _state.asStateFlow()

    /** Test hook to emit a state update without going through seed/toggle. */
    fun emit(map: PersistentMap<String, PostInteractionState>) {
        _state.value = map
    }

    override fun seed(posts: List<PostUi>) {
        seedCalls.incrementAndGet()
        lastSeedPosts += posts
    }

    override suspend fun toggleLike(postUri: String, postCid: String): Result<Unit> {
        toggleLikeCalls.incrementAndGet()
        lastToggleLikeArgs += postUri to postCid
        return nextToggleLikeResult
    }

    override suspend fun toggleRepost(postUri: String, postCid: String): Result<Unit> {
        toggleRepostCalls.incrementAndGet()
        lastToggleRepostArgs += postUri to postCid
        return nextToggleRepostResult
    }

    override fun clear() {
        clearCalls.incrementAndGet()
        _state.value = persistentMapOf()
    }
}
```

- [ ] **Step 3: Update the test class's VM construction**

Find the existing `newVm(…)` test helper. Replace its `likeRepostRepository: LikeRepostRepository = …` parameter with:

```kotlin
postInteractionsCache: PostInteractionsCache = FakePostInteractionsCache(),
```

…and pass it to the FeedViewModel constructor instead of `likeRepostRepository`. Remove the `FakeLikeRepostRepository` class from the file (deleted along with its parameter).

- [ ] **Step 4: Delete tests now owned by the cache**

Delete the test methods that exercise:
- "like flips viewer + count optimistically and persists likeUri on success"
- "like rolls back state and emits ShowError on failure"
- "repost flips viewer + count optimistically …"
- "repost rolls back and emits ShowError on failure"

(The cache's own test suite covers these. The VM-level concern is just dispatch + error-routing.)

- [ ] **Step 5: Add the new dispatch + error tests**

```kotlin
@Test
fun `OnLikeClicked dispatches cache toggleLike with post id and cid`() =
    runTest(mainDispatcher.dispatcher) {
        val cache = FakePostInteractionsCache()
        val vm = newVm(postInteractionsCache = cache)
        val post = samplePostUi(id = "at://post-a", cid = "bafyA")

        vm.handleEvent(FeedEvent.OnLikeClicked(post))
        advanceUntilIdle()

        assertEquals(1, cache.toggleLikeCalls.get())
        assertEquals("at://post-a" to "bafyA", cache.lastToggleLikeArgs.last())
    }

@Test
fun `OnLikeClicked routes cache failure to FeedEffect_ShowError`() =
    runTest(mainDispatcher.dispatcher) {
        val cache = FakePostInteractionsCache().apply {
            nextToggleLikeResult = Result.failure(java.io.IOException("net down"))
        }
        val vm = newVm(postInteractionsCache = cache)
        vm.effects.test {
            vm.handleEvent(FeedEvent.OnLikeClicked(samplePostUi(id = "at://post-x", cid = "bafyX")))
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FeedEffect.ShowError, "MUST emit ShowError on cache failure")
            cancelAndIgnoreRemainingEvents()
        }
    }

@Test
fun `cache state emission projects onto feedItems`() =
    runTest(mainDispatcher.dispatcher) {
        val cache = FakePostInteractionsCache()
        val vm = newVm(postInteractionsCache = cache)
        // Wait for init + initial load to settle.
        advanceUntilIdle()
        // Find a post id present in the loaded feed (depends on the fake feed
        // repo's contents; adapt to whatever sample post is loaded).
        val postId = vm.uiState.value.feedItems.filterIsInstance<FeedItemUi.Post>().first().post.id

        cache.emit(
            kotlinx.collections.immutable.persistentMapOf(
                postId to PostInteractionState(
                    viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/test",
                    likeCount = 99,
                ),
            ),
        )
        advanceUntilIdle()

        val merged = vm.uiState.value.feedItems
            .filterIsInstance<FeedItemUi.Post>()
            .first { it.post.id == postId }
        assertTrue(merged.post.viewer.isLikedByViewer)
        assertEquals(99, merged.post.stats.likeCount)
    }
```

Adapt the test class's fixture builder (`samplePostUi(...)`) — there's likely already one in the file; if not, build one minimally that takes `id` + `cid` and fills the rest with defaults.

Mirror the `OnLikeClicked` dispatch + failure tests for `OnRepostClicked` (3 more tests).

- [ ] **Step 6: Run the tests**

Run: `./gradlew :feature:feed:impl:testDebugUnitTest`
Expected: All tests PASS (existing non-interaction tests + new dispatch/projection tests).

If a previously-passing non-interaction test now fails because of the cache subscription, debug: it's probably from `init`'s cache subscription firing a setState before the test's own first setState landed. Add `advanceUntilIdle()` after VM construction in the failing test.

- [ ] **Step 7: Commit**

```bash
git add feature/feed/impl/src/test/kotlin/net/kikin/nubecita/feature/feed/impl/FakePostInteractionsCache.kt \
        feature/feed/impl/src/test/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModelTest.kt
git commit -m "$(cat <<'EOF'
test(feature/feed/impl): replace FakeLikeRepostRepository with FakePostInteractionsCache

FeedViewModel's optimistic logic moved to the cache, so the VM-level
tests now cover dispatch + error-routing + state-projection only. The
cache's own test suite owns the optimistic-flip + rollback assertions.
4 existing tests deleted; 6 new tests cover OnLikeClicked dispatch,
OnLikeClicked failure routing, cache-state projection, and the
symmetric OnRepostClicked trio.

Refs: nubecita-78p
EOF
)"
```

---

## Task 18: Wire `ProfileViewModel` (Contract + VM + tests)

Adds the like/repost event handlers, cache subscription + projection across all 3 tabs, and seed calls.

**Files:**
- Modify: `feature/profile/impl/build.gradle.kts`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileContract.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt`
- Modify: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt`

- [ ] **Step 1: Add the gradle dep**

In `feature/profile/impl/build.gradle.kts`, add to the `dependencies` block alongside the existing `:core/*` deps:

```kotlin
    implementation(project(":core:post-interactions"))
```

- [ ] **Step 2: Add events to `ProfileContract.kt`**

In the `sealed interface ProfileEvent : UiEvent { ... }` block, append two new events (after `MessageTapped` or wherever the action-tap events are grouped):

```kotlin
    /** User tapped the like icon on a PostCard rendered inside one of the tab bodies. */
    data class OnLikeClicked(val post: PostUi) : ProfileEvent

    /** User tapped the repost icon on a PostCard rendered inside one of the tab bodies. */
    data class OnRepostClicked(val post: PostUi) : ProfileEvent
```

Add the import:

```kotlin
import net.kikin.nubecita.data.models.PostUi
```

- [ ] **Step 3: Inject the cache into `ProfileViewModel`**

In the `ProfileViewModel` constructor (`@AssistedInject` form), add:

```kotlin
private val postInteractionsCache: PostInteractionsCache,
```

The factory interface (`ProfileViewModel.Factory`) is unchanged — `@AssistedInject` resolves `postInteractionsCache` from the Hilt graph.

Add the import:

```kotlin
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import kotlinx.collections.immutable.PersistentMap
```

- [ ] **Step 4: Subscribe to the cache in `init`**

In the VM's `init { ... }` block, after the existing `launchInitialLoads(actor)` call, append:

```kotlin
viewModelScope.launch {
    postInteractionsCache.state
        .map { interactionMap -> uiState.value.applyInteractions(interactionMap) }
        .distinctUntilChanged()
        .collect { merged -> setState { merged } }
}
```

Add the helper extension at file scope (below the VM class):

```kotlin
private fun ProfileScreenViewState.applyInteractions(
    map: PersistentMap<String, PostInteractionState>,
): ProfileScreenViewState =
    copy(
        postsStatus = postsStatus.applyInteractions(map),
        repliesStatus = repliesStatus.applyInteractions(map),
        mediaStatus = mediaStatus.applyInteractions(map),
    )

private fun TabLoadStatus.applyInteractions(
    map: PersistentMap<String, PostInteractionState>,
): TabLoadStatus =
    if (this is TabLoadStatus.Loaded) {
        copy(items = items.map { it.applyInteraction(map) }.toImmutableList())
    } else {
        this
    }

private fun TabItemUi.applyInteraction(
    map: PersistentMap<String, PostInteractionState>,
): TabItemUi =
    when (this) {
        is TabItemUi.Post -> {
            val state = map[post.id] ?: return this
            copy(post = post.mergeInteractionState(state))
        }
        is TabItemUi.MediaCell -> this // media cells don't show interaction UI
    }
```

- [ ] **Step 5: Wire `OnLikeClicked` and `OnRepostClicked` in `handleEvent`**

In `handleEvent`, add:

```kotlin
is ProfileEvent.OnLikeClicked -> viewModelScope.launch {
    postInteractionsCache.toggleLike(event.post.id, event.post.cid)
        .onFailure { sendEffect(ProfileEffect.ShowError(it.toProfileError())) }
}
is ProfileEvent.OnRepostClicked -> viewModelScope.launch {
    postInteractionsCache.toggleRepost(event.post.id, event.post.cid)
        .onFailure { sendEffect(ProfileEffect.ShowError(it.toProfileError())) }
}
```

Place them with the other event handlers.

- [ ] **Step 6: Call `cache.seed(...)` after each tab page fetch**

Locate `launchInitialTabLoad`, `launchTabRefresh`, and `onLoadMore` in `ProfileViewModel.kt`. In each `.onSuccess { page -> ... }` block, just before the `setTabStatus(tab) { ... }` call, add:

```kotlin
postInteractionsCache.seed(page.items.filterIsInstance<TabItemUi.Post>().map { it.post })
```

`TabItemUi.Post` is the variant carrying a `PostUi`; `TabItemUi.MediaCell` doesn't have one.

- [ ] **Step 7: Wire ProfileScreen.kt PostCallbacks**

In `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreen.kt`, find the `PostCallbacks(...)` construction (around line 40 from the bead F work). Replace the no-op stubs:

```kotlin
onLike = {},
onRepost = {},
```

with:

```kotlin
onLike = { post -> viewModel.handleEvent(ProfileEvent.OnLikeClicked(post)) },
onRepost = { post -> viewModel.handleEvent(ProfileEvent.OnRepostClicked(post)) },
```

- [ ] **Step 8: Compile**

Run: `./gradlew :feature:profile:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Add unit tests**

In `ProfileViewModelTest.kt`, update the `newVm` helper to take `postInteractionsCache: PostInteractionsCache = FakePostInteractionsCache()` (create the fake in `:feature:profile:impl/src/test/.../FakePostInteractionsCache.kt` mirroring the one from Task 17). Pass it through to the VM constructor.

Append three new tests:

```kotlin
@Test
fun `OnLikeClicked dispatches cache toggleLike with post id and cid`() =
    runTest(mainDispatcher.dispatcher) {
        val cache = FakePostInteractionsCache()
        val repo = FakeProfileRepository(
            headerWithViewerResult = Result.success(
                ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None),
            ),
            tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
        )
        val vm = newVm(repo = repo, postInteractionsCache = cache)
        advanceUntilIdle()
        val post = samplePostUi(id = "at://post-p", cid = "bafyP")

        vm.handleEvent(ProfileEvent.OnLikeClicked(post))
        advanceUntilIdle()

        assertEquals(1, cache.toggleLikeCalls.get())
        assertEquals("at://post-p" to "bafyP", cache.lastToggleLikeArgs.last())
    }

@Test
fun `OnLikeClicked failure surfaces ProfileEffect_ShowError`() =
    runTest(mainDispatcher.dispatcher) {
        val cache = FakePostInteractionsCache().apply {
            nextToggleLikeResult = Result.failure(java.io.IOException("net down"))
        }
        val repo = FakeProfileRepository(
            headerWithViewerResult = Result.success(
                ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None),
            ),
            tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
        )
        val vm = newVm(repo = repo, postInteractionsCache = cache)
        advanceUntilIdle()

        vm.effects.test {
            vm.handleEvent(ProfileEvent.OnLikeClicked(samplePostUi(id = "at://x", cid = "bafyX")))
            advanceUntilIdle()
            assertTrue(awaitItem() is ProfileEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

@Test
fun `cache emission projects onto the active tab's items`() =
    runTest(mainDispatcher.dispatcher) {
        val postsPage = ProfileTabPage(
            items = persistentListOf(
                TabItemUi.Post(samplePostUi(id = "at://post-A", cid = "bafyA")),
            ),
            nextCursor = null,
        )
        val cache = FakePostInteractionsCache()
        val repo = FakeProfileRepository(
            headerWithViewerResult = Result.success(
                ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None),
            ),
            tabResults = mapOf(
                ProfileTab.Posts to Result.success(postsPage),
                ProfileTab.Replies to Result.success(EMPTY_PAGE),
                ProfileTab.Media to Result.success(EMPTY_PAGE),
            ),
        )
        val vm = newVm(repo = repo, postInteractionsCache = cache)
        advanceUntilIdle()

        cache.emit(
            kotlinx.collections.immutable.persistentMapOf(
                "at://post-A" to PostInteractionState(
                    viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/test",
                    likeCount = 42,
                ),
            ),
        )
        advanceUntilIdle()

        val merged = (vm.uiState.value.postsStatus as TabLoadStatus.Loaded)
            .items
            .filterIsInstance<TabItemUi.Post>()
            .first { it.post.id == "at://post-A" }
        assertTrue(merged.post.viewer.isLikedByViewer)
        assertEquals(42, merged.post.stats.likeCount)
    }
```

Add a `samplePostUi` helper if the file doesn't have one — mirror the shape from the bead E/F tests.

- [ ] **Step 10: Run all profile tests**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest`
Expected: All tests PASS.

- [ ] **Step 11: Commit**

```bash
git add feature/profile/impl/build.gradle.kts \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileContract.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreen.kt \
        feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/FakePostInteractionsCache.kt \
        feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): wire PostInteractionsCache for like/repost

ProfileContract gains OnLikeClicked / OnRepostClicked events.
ProfileViewModel injects the cache, subscribes in init, projects state
across all 3 tabs (Posts / Replies / Media via mergeInteractionState),
dispatches toggleLike/toggleRepost on events, and seeds the cache after
each tab page fetch. ProfileScreen's PostCallbacks no-op stubs become
real event dispatches. 3 new unit tests cover dispatch, failure routing,
and cross-tab projection.

Refs: nubecita-78p
EOF
)"
```

---

## Task 19: Wire `PostDetailViewModel` (Contract + VM + tests)

Same shape as Task 18 but for PostDetail — focused post + thread tree.

**Files:**
- Modify: `feature/postdetail/impl/build.gradle.kts`
- Modify: `feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailContract.kt`
- Modify: `feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailViewModel.kt`
- Modify: `feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailScreen.kt`
- Modify: `feature/postdetail/impl/src/test/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailViewModelTest.kt`
- Create: `feature/postdetail/impl/src/test/kotlin/net/kikin/nubecita/feature/postdetail/impl/FakePostInteractionsCache.kt`

- [ ] **Step 1: Add the gradle dep**

In `feature/postdetail/impl/build.gradle.kts`:

```kotlin
    implementation(project(":core:post-interactions"))
```

- [ ] **Step 2: Add events to `PostDetailContract.kt`**

In the `sealed interface PostDetailEvent : UiEvent { ... }` block, append:

```kotlin
    /** User tapped like on the focused post or a thread reply. */
    data class OnLikeClicked(val post: PostUi) : PostDetailEvent

    /** User tapped repost on the focused post or a thread reply. */
    data class OnRepostClicked(val post: PostUi) : PostDetailEvent
```

Import `PostUi` if not already imported.

- [ ] **Step 3: Inject cache into `PostDetailViewModel`**

Add to the `@AssistedInject` constructor:

```kotlin
private val postInteractionsCache: PostInteractionsCache,
```

Add imports for `PostInteractionsCache`, `PostInteractionState`, `mergeInteractionState`, `PersistentMap`.

- [ ] **Step 4: Subscribe + project**

In `PostDetailViewModel.init`, after the existing thread-load logic:

```kotlin
viewModelScope.launch {
    postInteractionsCache.state
        .map { interactionMap -> uiState.value.applyInteractions(interactionMap) }
        .distinctUntilChanged()
        .collect { merged -> setState { merged } }
}
```

The `applyInteractions` helper depends on `PostDetailViewState`'s shape — open the contract and adapt. The focused post (likely `state.focusedPost: PostUi?` or similar) needs the merge; each thread item (likely `state.threadItems: ImmutableList<ThreadItemUi>`) needs the merge applied to its `.post: PostUi`.

Pseudo:

```kotlin
private fun PostDetailViewState.applyInteractions(
    map: PersistentMap<String, PostInteractionState>,
): PostDetailViewState =
    copy(
        focusedPost = focusedPost?.let { post ->
            map[post.id]?.let { post.mergeInteractionState(it) } ?: post
        },
        threadItems = threadItems.map { item ->
            // Assuming ThreadItemUi has a `post: PostUi` field; if it's a sealed type
            // with multiple variants, handle each variant.
            val state = map[item.post.id] ?: return@map item
            item.copy(post = item.post.mergeInteractionState(state))
        }.toImmutableList(),
    )
```

Adapt to the actual `PostDetailViewState` + `ThreadItemUi` shapes — open `PostDetailContract.kt` first to confirm.

- [ ] **Step 5: Wire events**

In `PostDetailViewModel.handleEvent`:

```kotlin
is PostDetailEvent.OnLikeClicked -> viewModelScope.launch {
    postInteractionsCache.toggleLike(event.post.id, event.post.cid)
        .onFailure { sendEffect(PostDetailEffect.ShowError(it.toPostDetailError())) }
}
is PostDetailEvent.OnRepostClicked -> viewModelScope.launch {
    postInteractionsCache.toggleRepost(event.post.id, event.post.cid)
        .onFailure { sendEffect(PostDetailEffect.ShowError(it.toPostDetailError())) }
}
```

If `PostDetailEffect.ShowError` doesn't exist with the right shape, check the existing error effects and use whichever matches. The `toPostDetailError()` helper may need to be added if absent — model it after Feed's `toFeedError()`.

- [ ] **Step 6: Seed after thread fetch**

After the thread load resolves successfully, before `setState`:

```kotlin
val allPosts = listOfNotNull(focusedPost) + threadItems.map { it.post }
postInteractionsCache.seed(allPosts)
```

Place this inside the success branch of whatever loads the thread.

- [ ] **Step 7: Wire PostDetailScreen.kt PostCallbacks**

In `PostDetailScreen.kt` (around line 105 from earlier exploration), the existing `PostCallbacks(...)` only wires `onTap` and `onAuthorTap`. Add:

```kotlin
onLike = { viewModel.handleEvent(PostDetailEvent.OnLikeClicked(it)) },
onRepost = { viewModel.handleEvent(PostDetailEvent.OnRepostClicked(it)) },
```

- [ ] **Step 8: Compile**

Run: `./gradlew :feature:postdetail:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Add unit tests**

Create `feature/postdetail/impl/src/test/kotlin/net/kikin/nubecita/feature/postdetail/impl/FakePostInteractionsCache.kt` mirroring Task 17's fake (different package).

In `PostDetailViewModelTest.kt`, update `newVm(...)` to accept and pass `postInteractionsCache: PostInteractionsCache = FakePostInteractionsCache()`.

Add three tests mirroring Task 18's Profile tests:

```kotlin
@Test
fun `OnLikeClicked dispatches cache toggleLike with focused post id and cid`() {
    /* same shape as Profile's test; assert cache.toggleLikeCalls + lastToggleLikeArgs */
}

@Test
fun `OnLikeClicked failure surfaces PostDetailEffect_ShowError`() {
    /* same shape */
}

@Test
fun `cache emission projects onto focused post and thread items`() {
    /* emit a state; assert both focusedPost.viewer.isLikedByViewer flips AND a thread item's post does */
}
```

Write them out with the same level of detail as Task 18's tests — full code, full setup, full asserts.

- [ ] **Step 10: Run tests**

Run: `./gradlew :feature:postdetail:impl:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add feature/postdetail/impl/build.gradle.kts \
        feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailContract.kt \
        feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailViewModel.kt \
        feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailScreen.kt \
        feature/postdetail/impl/src/test/kotlin/net/kikin/nubecita/feature/postdetail/impl/FakePostInteractionsCache.kt \
        feature/postdetail/impl/src/test/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(feature/postdetail/impl): wire PostInteractionsCache for like/repost

PostDetailContract gains OnLikeClicked / OnRepostClicked events.
PostDetailViewModel injects the cache, subscribes in init, projects
state onto both the focused post and the thread tree via
mergeInteractionState, dispatches toggle calls on events, and seeds
the cache after the thread load resolves. PostDetailScreen's
PostCallbacks no-op stubs become real event dispatches. 3 new unit
tests cover dispatch, failure routing, and projection.

Refs: nubecita-78p
EOF
)"
```

---

## Task 20: Sign-out clears the cache

`DefaultAuthRepository.signOut()` gains an injected `PostInteractionsCache` and calls `cache.clear()` before revocation.

**Files:**
- Modify: `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/DefaultAuthRepository.kt`
- Modify: `core/auth/build.gradle.kts`

- [ ] **Step 1: Add the gradle dep**

In `core/auth/build.gradle.kts`, append to `dependencies`:

```kotlin
    implementation(project(":core:post-interactions"))
```

- [ ] **Step 2: Inject the cache and call clear()**

In `DefaultAuthRepository.kt`, add `postInteractionsCache: PostInteractionsCache` to the `@Inject constructor`:

```kotlin
internal class DefaultAuthRepository
    @Inject
    constructor(
        private val atOAuth: AtOAuth,
        private val sessionStateProvider: SessionStateProvider,
        private val postInteractionsCache: PostInteractionsCache,
    ) : AuthRepository {
```

Add the import:

```kotlin
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
```

In the `signOut()` method body, BEFORE the existing `atOAuth.logout()` call, add:

```kotlin
postInteractionsCache.clear()
```

- [ ] **Step 3: Compile**

Run: `./gradlew :core:auth:compileDebugKotlin :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify existing auth tests still pass**

Run: `./gradlew :core:auth:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

If `DefaultAuthRepositoryTest` (if it exists) breaks because the new dep isn't injected in the test setup, add a `FakePostInteractionsCache` (mirror Task 17's shape — minimal stub with `clear()` no-op) to the test's VM construction.

- [ ] **Step 5: Commit**

```bash
git add core/auth/build.gradle.kts \
        core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/DefaultAuthRepository.kt
git commit -m "$(cat <<'EOF'
feat(core/auth): sign-out clears PostInteractionsCache

DefaultAuthRepository.signOut() now clears the session-scoped post
interactions cache before revocation. Prevents session-touched
optimistic state from leaking into a different user's session after
sign-out + sign-in-as-someone-else.

Refs: nubecita-78p
EOF
)"
```

---

## Task 21: Full local verification

Run the entire suite locally before opening the PR.

- [ ] **Step 1: Run the full Gradle verification matrix**

```bash
./gradlew :core:post-interactions:testDebugUnitTest \
          :core:auth:testDebugUnitTest \
          :feature:feed:impl:testDebugUnitTest \
          :feature:feed:impl:validateDebugScreenshotTest \
          :feature:profile:impl:testDebugUnitTest \
          :feature:profile:impl:validateDebugScreenshotTest \
          :feature:postdetail:impl:testDebugUnitTest \
          :feature:postdetail:impl:validateDebugScreenshotTest \
          :app:assembleDebug \
          spotlessCheck lint :app:checkSortDependencies
```

Expected: BUILD SUCCESSFUL across all tasks.

- [ ] **Step 2: Handle any auto-fixable findings**

If `spotlessCheck` fails, run `./gradlew spotlessApply` and commit:

```bash
git add -u
git commit -m "$(cat <<'EOF'
style(core/post-interactions): spotless apply after task suite

Refs: nubecita-78p
EOF
)"
```

If `lint` flags new issues (orphan strings, missing contentDescription on new icons, etc.), address them inline. Pre-existing baseline-filtered findings are NOT a regression — only fix newly-introduced ones.

If `:app:checkSortDependencies` complains, the gradle dep additions in Tasks 6, 18, 19, 20 may need to be alphabetized — re-sort and commit.

- [ ] **Step 3: On-device smoke**

Per the `feedback_run_instrumentation_tests_after_compose_work` memory: try `adb devices` first. If a device is connected:

```bash
./gradlew :app:installDebug
adb shell am start -n net.kikin.nubecita/.MainActivity
```

Walk through the cross-screen smoke:
- Like a post in Feed → tap into PostDetail → still shows liked.
- Unlike on PostDetail → back to Feed → shows unliked.
- Tap an author handle → other-user profile loads with PostCards → like a post on Posts tab → switch to Replies → switch back to Posts → still liked.
- Sign out via own-profile overflow → Settings → Sign Out → confirm → returns to login.

If no device is connected, skip the smoke; the unit-level coverage on cache + per-VM projection is the primary correctness signal. The PR's `run-instrumented` label will trigger CI's instrumented job.

- [ ] **Step 4: No commit (this task is verification only)**

If everything passes, proceed to Task 22. If anything fails, fix and commit before continuing.

---

## Task 22: File follow-up bd issues + open PR

Files the six (or seven) follow-up bd issues per the spec's "Out of scope" table, pushes the branch, opens the PR.

- [ ] **Step 1: File the follow-up bd issues**

For each, use `bd create` with the pattern below. Record each new id for the PR body.

```bash
bd create --title "feature/feed/impl: repost-vs-Quote bottomsheet" \
  --type feature --priority 2 \
  --description "Splits the current auto-repost behavior in FeedScreen into a M3 bottomsheet asking 'Repost' vs 'Quote'. Real Repost wires to PostInteractionsCache.toggleRepost (already in place after nubecita-78p). Quote routes to the composer in Quote mode (depends on follow-up bd to ship that). Surfaced as a UX bug during nubecita-78p manual testing."
```

```bash
bd create --title "feature/composer: Quote-post mode for the unified composer" \
  --type feature --priority 3 \
  --description "Adds a Quote authoring mode to the composer that produces a record with a quoted-post embed. The repost-vs-Quote bottomsheet (sibling follow-up) routes 'Quote' here. Gated on the existing composer epic's progress."
```

```bash
bd create --title "feature/{feed,profile,postdetail}/impl: wire PostCallbacks.onReply to launch composer" \
  --type feature --priority 3 \
  --description "Today PostCallbacks.onReply is no-op on most surfaces; reply-from-PostCard-icon doesn't launch the composer. Wire each screen's onReply to LocalComposerLauncher with the parent post as the reply target. PostDetail's focused-post FAB-style reply (already works) is unaffected."
```

```bash
bd create --title "feature/{feed,profile,postdetail}/impl: PostCallbacks.onShare → Android intent share" \
  --type feature --priority 3 \
  --description "Implement the share path: convert PostUi.id (AT URI) to a bsky.app/profile/<handle>/post/<rkey> permalink, fire an Intent.ACTION_SEND. Wire onShare across all three screens."
```

```bash
bd create --title "core/post-interactions: in-flight pending-write spinner (PostCard)" \
  --type feature --priority 4 \
  --description "Optional polish — if UX feedback shows users tap-tap during in-flight calls and find the delay confusing, surface PostInteractionState.pendingLikeWrite to PostCard. Today the cache's single-flight absorbs double-taps so this is genuinely optional. File ONLY if a real product requirement surfaces."
```

For 6, the Room graduation epic (`nubecita-zcw`) was already filed during the brainstorming flow. Don't re-file.

After each `bd create`, parent the new issue under `nubecita-8f6`:

```bash
bd update <new-id> --parent nubecita-8f6 2>&1 | tail -2
```

(The `--parent` flag stores the relationship as a `parent-child` dep entry, per the bead-F-era discovery.)

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feat/nubecita-78p-core-post-interactions-cache-broadcast-mvp-wiring
```

- [ ] **Step 3: Open the PR**

Replace `<BD-ID-...>` with the actual ids collected from step 1.

```bash
gh pr create --base main \
  --title "feat(core/post-interactions): cache + broadcast for cross-screen like/repost" \
  --body "$(cat <<'EOF'
## Summary

First PR of `nubecita-8f6` (Post interactions epic). Promotes `LikeRepostRepository` from `:feature:feed:impl` to a new `:core/post-interactions` module, introduces a `PostInteractionsCache` with cross-screen sync, refactors `FeedViewModel` (drops ~150 lines of optimistic-with-rollback into the cache), and wires `ProfileViewModel` + `PostDetailViewModel` (previously stubbed `onLike = {}`) for the first time.

After this PR: a like on PostDetail reflects on Feed and Profile without re-fetching. Repost works the same way. The user can like/unlike from any of the three surfaces.

## Design

- Spec: `docs/superpowers/specs/2026-05-12-post-interactions-mvp-design.md`
- Plan: `docs/superpowers/plans/2026-05-12-post-interactions-mvp-plan.md`

## Architecture highlights

- `PostInteractionsCache` is a `@Singleton` holding `StateFlow<PersistentMap<String, PostInteractionState>>`.
- VMs subscribe and project via `PostUi.mergeInteractionState` extension.
- Cache owns optimistic flips, rollback, single-flight per-postUri, and the seed-merger rule that preserves in-flight state against stale wire data (atproto eventual consistency).
- `Result<Unit>` return from toggle calls so each VM routes errors via its own `FooEffect.ShowError`.
- `at://`-prefixed PENDING sentinels for in-flight URIs (defensive against `AtUri.parse()` if a leak ever happens).
- Sign-out clears the cache before session revocation.

## Out of scope (filed as follow-ups)

- <BD-ID-1>: repost-vs-Quote bottomsheet (the auto-repost UX bug)
- <BD-ID-2>: Quote-post composer mode
- <BD-ID-3>: PostCallbacks.onReply launches composer
- <BD-ID-4>: PostCallbacks.onShare → Android intent
- <BD-ID-5>: optional in-flight pending-write spinner
- nubecita-zcw (sibling epic): graduate to Room backing for cross-session persistence

## Test plan

- [x] `:core:post-interactions:testDebugUnitTest` — 13 cache scenarios (toggle happy/failure/single-flight, seed merger, refresh-during-in-flight, clear, repost mirror)
- [x] `:feature:feed:impl:testDebugUnitTest` — VM dispatch + projection + error routing (replaced old optimistic-flip tests; cache owns those)
- [x] `:feature:profile:impl:testDebugUnitTest` — same shape (dispatch + projection across 3 tabs + error routing)
- [x] `:feature:postdetail:impl:testDebugUnitTest` — same shape (dispatch + focused-post + thread projection + error routing)
- [x] `:core:auth:testDebugUnitTest` — existing signOut tests still pass with the new dep injection
- [x] `:app:assembleDebug spotlessCheck lint :app:checkSortDependencies` — green locally
- [ ] On-device smoke: like on Feed → tap to PostDetail → still liked; unlike there → back to Feed → unliked; like on Profile → switch tab → switch back → still liked; sign out clears cache

Closes: nubecita-78p
EOF
)" 2>&1 | tail -3
```

- [ ] **Step 4: Confirm the PR is open**

```bash
gh pr view --json url,title,state --jq '.'
```

Expected: an OPEN PR URL is printed. Save the URL for follow-up.

- [ ] **Step 5: Print final summary**

No commit. The PR is open. After merge, `bd close nubecita-78p` per the project workflow.

---

## Acceptance summary

When all 22 tasks complete, the bd issue is done if:

- All commits land on the branch with Conventional-Commit messages referencing `nubecita-78p`
- `:core:post-interactions:testDebugUnitTest` (13 tests) all green
- All four feature modules' unit tests green
- `:app:assembleDebug spotlessCheck lint :app:checkSortDependencies` all green
- PR opened
- 5 follow-up bd issues filed (+ the pre-filed `nubecita-zcw` Room epic)
- On-device cross-screen smoke verified (or deferred to CI's `run-instrumented` if no device)

Once the PR merges:

```bash
bd close nubecita-78p
```

— and `nubecita-8f6` stays open as the umbrella for the 5 follow-ups + Room sibling epic.
