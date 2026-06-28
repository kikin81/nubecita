# Mute / Unmute Account Implementation Plan (nubecita-oftc.5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace the "Mute — coming soon" stubs with real mute/unmute, mirroring the existing Block pattern, plus an optimistic in-memory feed-hide and a profile "You've muted this user" notice.

**Architecture:** A small `MuteRepository` in `:core:actors` (alongside `BlockRepository`) wraps `GraphService.muteActor`/`unmuteActor`. Feature VMs (Feed/PostDetail/Profile) call it on the overflow Mute/Unmute action with an optimistic `ViewerStateUi.isAuthorMutedByViewer` toggle + rollback. Muted authors' posts are hidden from the in-memory feed immediately; the profile hero shows a muted notice.

**Tech Stack:** Kotlin, Hilt, atproto-kotlin v9.6.0 (`GraphService.muteActor(MuteActorRequest(actor))`), JUnit5/Turbine/MockK, Compose screenshot tests.

## Global Constraints

- **Mirror Block, but NO confirmation dialog** — mute is reversible/quiet (oftc.5). No `MuteDialog*`; the overflow action calls the repo directly.
- **`MuteRepository` lives in `:core:actors`** (next to `BlockRepository`), NOT `ModerationRepository` (which is report-only). oftc.5's "reuse ModerationRepository" line is stale.
- **Optimistic toggle pattern** = the existing like/repost pattern: flip `ViewerStateUi.isAuthorMutedByViewer` immediately on tap, call the repo, **roll back + snackbar on failure**. PostCard never flips locally — the VM re-emits the `PostUi`.
- **PII / no surprises:** the repo takes a DID/handle string; no host strings leak. `CancellationException` always propagates (mirror `DefaultBlockRepository`).
- **Server already filters** muted authors from `getTimeline`/`getAuthorFeed` on the next fetch — the in-memory hide is only for *immediate* feedback.
- **Flavored test tasks:** `:core:actors` and the feature `:impl` modules — detect via `src/bench`; use `:<m>:testProductionDebugUnitTest` if flavored else `:testDebugUnitTest`. Screenshot tasks: `:<m>:validateDebugScreenshotTest` / `updateDebugScreenshotTest`.

---

### Task 1: `MuteRepository` in `:core:actors`

**Files:**
- Create: `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/MuteRepository.kt` (interface)
- Create: `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/internal/DefaultMuteRepository.kt`
- Modify: the `:core:actors` DI module that `@Binds` `BlockRepository` (find it — same module binds `MuteRepository` to `DefaultMuteRepository`)
- Test: `core/actors/src/test/kotlin/net/kikin/nubecita/core/actors/internal/DefaultMuteRepositoryTest.kt`

**Interfaces:**
- Produces: `interface MuteRepository { suspend fun muteActor(did: String): Result<Unit>; suspend fun unmuteActor(did: String): Result<Unit> }`

- [ ] **Step 1: Write the failing test** — mirror `DefaultBlockRepositoryTest`. Use the same MockEngine/fake XRPC harness that the block test uses (read it). Assert `muteActor("did:plc:x")` issues the `app.bsky.graph.muteActor` XRPC with `actor = did` and returns `Result.success` on 2xx; `unmuteActor` likewise; a 4xx/throw → `Result.failure`; `CancellationException` propagates.
- [ ] **Step 2: Run it, watch it fail** (`./gradlew :core:actors:testProductionDebugUnitTest --tests '*DefaultMuteRepositoryTest*'`) — fails: `MuteRepository` unresolved.
- [ ] **Step 3: Implement.** Interface as above. `DefaultMuteRepository` mirrors `DefaultBlockRepository`'s constructor (`XrpcClientProvider`, `SessionStateProvider` if needed, `@IoDispatcher`) and shape:
```kotlin
override suspend fun muteActor(did: String): Result<Unit> =
    withContext(dispatcher) {
        runCatching {
            val client = xrpcClientProvider.authenticated()
            GraphService(client).muteActor(MuteActorRequest(actor = AtIdentifier(did)))
            Unit
        }.onFailure { if (it is CancellationException) throw it; Timber.tag(TAG).w(it, "muteActor failed") }
    }
// unmuteActor: same with UnmuteActorRequest(actor = AtIdentifier(did))
```
(Confirm `MuteActorRequest`/`UnmuteActorRequest` field name `actor` and its type — `AtIdentifier` — from the SDK; `import io.github.kikin81.atproto.app.bsky.graph.*`.) Add the `@Binds` for `MuteRepository` in the same DI module as `BlockRepository`.
- [ ] **Step 4: Run tests green.**
- [ ] **Step 5: Commit** — `feat(actors): add MuteRepository (muteActor/unmuteActor XRPC)\n\nRefs: nubecita-oftc.5`

---

### Task 2: Wire mute/unmute + optimistic feed-hide in `FeedViewModel`

**Files:**
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt` (moderation handler ~`:146-165`; inject `MuteRepository`)
- Test: `feature/feed/impl/src/test/.../FeedViewModelTest.kt`

**Interfaces:**
- Consumes: `MuteRepository` (Task 1); `PostOverflowAction.MuteAuthor`/`UnmuteAuthor`; `ViewerStateUi.isAuthorMutedByViewer`.

- [ ] **Step 1: Write failing tests.** Mirror the existing like/repost optimistic tests in this file. Cover: (a) MuteAuthor → `muteRepository.muteActor(authorDid)` called, the author's posts are removed from the in-memory feed state immediately, and on success they stay hidden; (b) failure → rollback (posts reappear / mute flag reverts) + error effect; (c) UnmuteAuthor → `unmuteActor` called, mute flag flips to false.
- [ ] **Step 2: Run, watch fail.**
- [ ] **Step 3: Implement.** Replace the `FeedEffect.ShowComingSoon(action)` arm for `MuteAuthor`/`UnmuteAuthor` with: resolve the author DID from the post; optimistically remove that DID's posts from the feed list (mute) or flip `isAuthorMutedByViewer` (unmute) — mirror the like/repost optimistic-flip + the existing block effect routing; `viewModelScope.launch { muteRepository.muteActor(did).onFailure { restore + sendEffect(error) } }`. Keep ReportPost/Block routing unchanged.
- [ ] **Step 4: Run feed suite green.**
- [ ] **Step 5: Commit** — `feat(feed): wire mute/unmute with optimistic feed-hide\n\nRefs: nubecita-oftc.5`

---

### Task 3: Wire mute/unmute in `PostDetailViewModel`

**Files:**
- Modify: `feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailViewModel.kt` (the `ShowComingSoon` arm for `MuteAuthor`/`UnmuteAuthor`; inject `MuteRepository`)
- Test: `feature/postdetail/impl/src/test/.../PostDetailViewModelTest.kt`

**Interfaces:** Consumes `MuteRepository`.

- [ ] **Step 1: Write failing tests** — MuteAuthor → `muteActor(did)` + optimistic `isAuthorMutedByViewer=true` on the thread's posts by that author; failure → rollback + error; UnmuteAuthor → `unmuteActor` + flag false. Mirror this VM's existing like/repost optimistic tests.
- [ ] **Step 2: Run, watch fail.**
- [ ] **Step 3: Implement** — replace the Mute/Unmute `ShowComingSoon` arms with the repo call + optimistic toggle (no feed-hide here — thread context just reflects the muted flag). Mirror Task 2's pattern.
- [ ] **Step 4: Green.**
- [ ] **Step 5: Commit** — `feat(postdetail): wire mute/unmute\n\nRefs: nubecita-oftc.5`

---

### Task 4: Wire mute/unmute in `ProfileViewModel` (per-post + profile-hero overflow)

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt` — `StubActionTapped` (~`:176`, the hero-level Mute) and `OnPostOverflowAction` Mute/Unmute (~`:226`); inject `MuteRepository`
- Modify: `.../ProfileContract.kt` — the `StubbedAction { Edit, Block, Mute }` enum: drop `Mute` (now real); keep `Edit`/`Block` stubs
- Test: `feature/profile/impl/src/test/.../ProfileViewModelTest.kt`

**Interfaces:** Consumes `MuteRepository`; sets a profile-level muted flag (see Task 5's `ProfileHeaderUi.isMutedByViewer`).

- [ ] **Step 1: Write failing tests** — hero Mute → `muteActor(profileDid)` + profile header re-renders muted (Unmute label + notice flag true); per-post Mute → `muteActor(authorDid)` + that author's posts in the profile tabs reflect muted; failure → rollback + `ProfileEffect.ShowError`; Unmute symmetric.
- [ ] **Step 2: Run, watch fail.**
- [ ] **Step 3: Implement** — replace the Mute/Unmute `ShowComingSoon`/`ShowPostOverflowComingSoon` arms with the repo call + optimistic toggle (header `isMutedByViewer` + post viewer flags). Remove `StubbedAction.Mute` and its `ShowComingSoon` routing.
- [ ] **Step 4: Green.**
- [ ] **Step 5: Commit** — `feat(profile): wire mute/unmute on hero + per-post overflow\n\nRefs: nubecita-oftc.5`

---

### Task 5: Profile header "You've muted this user" notice

**Files:**
- Modify: `data:models` `ProfileHeaderUi` (add `isMutedByViewer: Boolean = false`) — find the type
- Modify: `feature/profile/impl/.../data/AuthorProfileMapper.kt` — map `isMutedByViewer` from the profile fetch's `viewer.muted`
- Modify: `feature/profile/impl/.../ui/ProfileHero.kt` (`ProfileHeroLoaded`, ~`:79-203`) — render an inline notice when `isMutedByViewer`
- Add string: `feature/profile/impl/.../res/values/strings.xml` — `profile_muted_notice` = "You've muted this user"
- Test: `ProfileViewModelTest`/mapper test asserts the flag maps; screenshot test (below)

**Interfaces:** Consumes `ProfileHeaderUi.isMutedByViewer` (set by Task 4 on toggle + Task 5 on fetch).

- [ ] **Step 1: Write failing test** — `AuthorProfileMapper` maps `viewer.muted == true` → `isMutedByViewer = true`.
- [ ] **Step 2: Run, watch fail.**
- [ ] **Step 3: Implement** the field + mapping + the `ProfileHero` notice (mirror the `showSupporterBadge` conditional-chip pattern already in `ProfileHeroLoaded`).
- [ ] **Step 4: Green** + add/refresh the screenshot test for the muted hero (mirror an existing ProfileHero screenshot fixture; `updateDebugScreenshotTest` to baseline, commit the PNG).
- [ ] **Step 5: Commit** — `feat(profile): muted-state notice in the profile hero\n\nRefs: nubecita-oftc.5`

---

### Task 6: Strings cleanup + screenshot baselines + final sweep

**Files:**
- Delete the `*_mute_coming_soon` string resources (profile + postdetail `overflowMuteComingSoon`/`overflowUnmuteComingSoon`) now unused — grep to confirm zero references first.
- Update overflow-menu screenshot baselines if the mute/unmute variants changed (`designsystem` `PostCardOverflowScreenshotTest` already has `isAuthorMutedByViewer` data).

- [ ] **Step 1:** `grep -rn` each `*coming_soon`/`overflow*Mute*ComingSoon` resource → confirm no remaining references, then delete the unused ones (keep Edit/Block stubs' strings).
- [ ] **Step 2:** Run `./gradlew lint :app:lintProductionDebug` + the touched modules' `validateDebugScreenshotTest`; regenerate baselines where the diff is intended; commit PNGs.
- [ ] **Step 3:** `./gradlew :app:assembleDebug` links.
- [ ] **Step 4: Commit** — `chore(moderation): remove mute coming-soon strings + refresh baselines\n\nRefs: nubecita-oftc.5`

---

## Final verification (before PR)
- [ ] All touched modules' unit suites green; `:app:assembleDebug` links; screenshot baselines committed.
- [ ] Compose gate: this adds `@Composable` (ProfileHero notice) → run the `compose-expert` review on the diff before pushing.
- [ ] Acceptance (oftc.5): mute/unmute writes succeed; menu toggles; feed hides muted author immediately; profile shows the muted notice; `profile_snackbar_mute_coming_soon` deleted; tests green.
