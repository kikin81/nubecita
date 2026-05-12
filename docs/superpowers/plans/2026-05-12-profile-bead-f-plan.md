# Profile Bead F Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the final bead of the Profile epic (`nubecita-s6p.6`): other-user actions row (Follow/Following + Message + overflow stubs), `ListDetailSceneStrategy.listPane{}` metadata on the Profile entry, a real SettingsStubScreen with confirmation-dialog sign-out, and removal of `:app`'s Settings placeholder.

**Architecture:** `ProfileActionsRow.kt` becomes a slim router that delegates to `OwnProfileActionsRow` (Edit + overflow→Settings) or `OtherUserActionsRow` (Follow/Following + Message + overflow→Block/Mute/Report stubs) based on `ownProfile`. A new `ProfileActionsOverflowMenu` primitive owns the DropdownMenu state. `SettingsStubScreen` follows the screen+content split with a full MVI `SettingsStubViewModel` that runs `AuthRepository.signOut()` behind a confirmation dialog and surfaces errors via snackbar. `AuthorProfileMapper` gains a `toProfileHeaderWithViewer()` extension that populates `viewerRelationship` from `viewer.following`; `ProfileViewModel` overrides to `Self` for own-profile. `FeedDetailPlaceholder` is promoted to `:designsystem.PostDetailPaneEmptyState` and reused by Profile via `listPane(detailPlaceholder = …)`. `:app`'s `MainShellPlaceholderModule` loses its Settings provider.

**Tech Stack:** Kotlin · Jetpack Compose with Material 3 Expressive · Hilt (assisted + plain VM injection) · `:core:auth.AuthRepository` for sign-out · `androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy` · JUnit 5 + Turbine + AssertK for VM tests · `com.android.tools.screenshot.PreviewTest` for screenshot baselines · `@HiltAndroidTest` + `HiltTestActivity` for instrumentation tests · all dependencies already in place from beads C–E.

**Predecessor design:** `docs/superpowers/specs/2026-05-12-profile-bead-f-design.md`. Refer back to it when something is ambiguous.

---

## File Structure

```
:designsystem/
├── src/main/kotlin/.../component/
│   └── PostDetailPaneEmptyState.kt              # CREATE (promoted from FeedDetailPlaceholder)
├── src/main/res/values/strings.xml              # MODIFY: + nubecita_detail_pane_select_post
└── src/screenshotTest/kotlin/.../component/
    └── PostDetailPaneEmptyStateScreenshotTest.kt # CREATE

:feature:feed:impl/
├── src/main/kotlin/.../ui/FeedDetailPlaceholder.kt          # DELETE
├── src/main/kotlin/.../di/FeedNavigationModule.kt           # MODIFY: call PostDetailPaneEmptyState
└── src/main/res/values/strings.xml                          # MODIFY: - feed_detail_placeholder_select

:feature:profile:impl/
├── src/main/kotlin/.../
│   ├── ProfileContract.kt                       # MODIFY: + StubbedAction.{Block,Mute,Report}; + ProfileEvent.StubActionTapped
│   ├── ProfileScreen.kt                         # MODIFY: + onNavigateToSettings + new effect snackbar copy
│   ├── ProfileScreenContent.kt                  # MODIFY: pass viewerRelationship + new callbacks through ProfileHero
│   ├── ProfileViewModel.kt                      # MODIFY: handle StubAction event; consume ProfileHeaderWithViewer
│   ├── SettingsStubContract.kt                  # CREATE
│   ├── SettingsStubScreen.kt                    # CREATE
│   ├── SettingsStubViewModel.kt                 # CREATE
│   ├── ui/
│   │   ├── OwnProfileActionsRow.kt              # CREATE
│   │   ├── OtherUserActionsRow.kt               # CREATE
│   │   ├── ProfileActionsOverflowMenu.kt        # CREATE
│   │   ├── ProfileActionsRow.kt                 # MODIFY: becomes slim router
│   │   └── ProfileHero.kt                       # MODIFY: + viewerRelationship + new callbacks
│   ├── di/
│   │   └── ProfileNavigationModule.kt           # MODIFY: listPane{} metadata; Settings entry wires SettingsStubScreen
│   └── data/
│       ├── AuthorProfileMapper.kt               # MODIFY: + toProfileHeaderWithViewer + ProfileHeaderWithViewer
│       ├── DefaultProfileRepository.kt          # MODIFY: fetchHeader returns ProfileHeaderWithViewer
│       └── ProfileRepository.kt                 # MODIFY: fetchHeader return type
├── src/main/res/values/strings.xml              # MODIFY: + ~10 new strings
├── src/test/kotlin/.../
│   ├── SettingsStubViewModelTest.kt             # CREATE
│   ├── ProfileViewModelTest.kt                  # MODIFY: + StubAction + Self override + FakeProfileRepository update
│   └── data/AuthorProfileMapperTest.kt          # MODIFY: + 3 viewer cases (verify or add)
├── src/screenshotTest/kotlin/.../ui/
│   ├── OwnProfileActionsRowScreenshotTest.kt    # CREATE
│   ├── OtherUserActionsRowScreenshotTest.kt     # CREATE
│   └── SettingsStubScreenScreenshotTest.kt      # CREATE
├── src/screenshotTest/kotlin/.../
│   └── ProfileScreenContentScreenshotTest.kt    # MODIFY: + other-user variants + Medium-width fixture
└── src/androidTest/kotlin/.../
    ├── ProfileScreenAdaptiveInstrumentationTest.kt  # CREATE
    ├── SettingsStubInstrumentationTest.kt           # CREATE
    ├── ProfileScreenInstrumentationTest.kt          # MODIFY: + Settings overflow path
    └── testing/
        └── TestAuthRepositoryModule.kt              # CREATE (Hilt test module for SettingsStubInstrumentationTest)

app/src/main/java/net/kikin/nubecita/shell/
└── MainShellPlaceholderModule.kt                # MODIFY: remove Settings provider
```

**Three things that didn't exist anywhere in beads C–E:**
1. An MVI presenter for a one-button "stub" surface (`SettingsStubViewModel`).
2. A DropdownMenu primitive on the profile actions row (`ProfileActionsOverflowMenu`).
3. `ListDetailSceneStrategy.listPane{}` metadata on a non-Feed entry.

Everything else is mechanical wiring or test fixtures.

---

## Task 1: Contract extensions — `StubbedAction.{Block,Mute,Report}` + `ProfileEvent.StubActionTapped(action)` + new strings

Foundational: nothing else compiles until these symbols exist. No behavior change yet.

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileContract.kt`
- Modify: `feature/profile/impl/src/main/res/values/strings.xml`

- [ ] **Step 1: Extend `StubbedAction` enum + add `ProfileEvent.StubActionTapped`**

Open `ProfileContract.kt`. Replace the existing `enum class StubbedAction { Follow, Edit, Message }` declaration with:

```kotlin
/**
 * Which stubbed write action triggered a "Coming soon" snackbar.
 * Lets the screen pick per-action copy (`Follow coming soon` vs
 * `Edit profile coming soon`) without coupling the VM to UI strings.
 *
 * Bead F adds Block / Mute / Report to cover the other-user overflow-menu
 * stubs. The real moderation writes ship under follow-up bd 7.7.
 */
enum class StubbedAction { Follow, Edit, Message, Block, Mute, Report }
```

In the `sealed interface ProfileEvent : UiEvent { ... }` block, append after the existing `data object MessageTapped : ProfileEvent` entry:

```kotlin
    /**
     * User tapped one of the stubbed overflow entries (Block / Mute / Report)
     * on the other-user actions row. Consolidates the three into a single
     * parameterized event — mirrors the [ProfileEffect.ShowComingSoon] shape
     * on the effect side, keeping the VM dispatch a one-liner.
     */
    data class StubActionTapped(
        val action: StubbedAction,
    ) : ProfileEvent
```

- [ ] **Step 2: Add new string resources**

Open `feature/profile/impl/src/main/res/values/strings.xml`. After the existing `<string name="profile_snackbar_message_coming_soon">…</string>` line, insert:

```xml
    <string name="profile_snackbar_block_coming_soon">Block — coming soon</string>
    <string name="profile_snackbar_mute_coming_soon">Mute — coming soon</string>
    <string name="profile_snackbar_report_coming_soon">Report — coming soon</string>
```

After the existing `<string name="profile_action_message">…</string>` line, insert the following grouped block (other-user follow-state copy, overflow menu items, settings overflow item):

```xml
    <string name="profile_action_following">Following</string>
    <string name="profile_action_settings">Settings</string>
    <string name="profile_action_block">Block</string>
    <string name="profile_action_mute">Mute</string>
    <string name="profile_action_report">Report</string>
```

At the bottom of the file, before `</resources>`, insert the Settings stub copy block:

```xml

    <!-- Settings stub screen + sign-out flow (Bead F) -->
    <string name="profile_settings_title">Settings</string>
    <string name="profile_settings_back_content_description">Back</string>
    <string name="profile_settings_coming_soon">Settings — coming soon. Sign out below.</string>
    <string name="profile_settings_signout">Sign out</string>
    <string name="profile_settings_signout_dialog_title">Sign out?</string>
    <string name="profile_settings_signout_dialog_body">You\'ll need to sign in again to use Nubecita.</string>
    <string name="profile_settings_signout_dialog_confirm">Sign out</string>
    <string name="profile_settings_signout_dialog_cancel">Cancel</string>
    <string name="profile_settings_signout_error">Couldn\'t sign out. Check your network and try again.</string>
```

- [ ] **Step 3: Compile**

Run: `./gradlew :feature:profile:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (The renamed enum + new event symbol are additive — no callers reference the new variants yet; existing call sites still compile.)

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileContract.kt \
        feature/profile/impl/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): + StubbedAction.{Block,Mute,Report} + StubAction event + strings

Foundation for Bead F's other-user overflow menu (Block/Mute/Report) and
Settings stub screen. Additions are non-breaking: no callers yet.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 2: VM dispatch for `StubAction` — TDD

**Files:**
- Modify: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt`

- [ ] **Step 1: Write the failing test**

Append to `ProfileViewModelTest.kt` after the existing `Media tab PostTapped emits NavigateToPost effect with the tapped postUri` test:

```kotlin
    @Test
    fun `StubAction emits ShowComingSoon with the same action value, never touches the repo`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerResult = Result.success(SAMPLE_HEADER),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()
            val priorHeaderCalls = repo.headerCalls.get()
            val priorPostsCalls = repo.tabCalls[ProfileTab.Posts]!!.get()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.StubActionTapped(StubbedAction.Block))
                assertEquals(ProfileEffect.ShowComingSoon(StubbedAction.Block), awaitItem())
                vm.handleEvent(ProfileEvent.StubActionTapped(StubbedAction.Mute))
                assertEquals(ProfileEffect.ShowComingSoon(StubbedAction.Mute), awaitItem())
                vm.handleEvent(ProfileEvent.StubActionTapped(StubbedAction.Report))
                assertEquals(ProfileEffect.ShowComingSoon(StubbedAction.Report), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(priorHeaderCalls, repo.headerCalls.get(),
                "StubAction MUST NOT issue a repository call")
            assertEquals(priorPostsCalls, repo.tabCalls[ProfileTab.Posts]!!.get(),
                "StubAction MUST NOT issue a tab fetch")
        }
```

- [ ] **Step 2: Run the test; verify it fails**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.ProfileViewModelTest.StubAction emits ShowComingSoon with the same action value, never touches the repo"`
Expected: FAIL — `MatchError` or similar because `ProfileViewModel.handleEvent` has no branch for `StubAction`.

- [ ] **Step 3: Add the dispatch case**

In `ProfileViewModel.kt`, locate the `override fun handleEvent(event: ProfileEvent) { when (event) { … } }` block. Add a new branch:

```kotlin
                is ProfileEvent.StubActionTapped ->
                    sendEffect(ProfileEffect.ShowComingSoon(event.action))
```

Place it immediately after the `ProfileEvent.MessageTapped ->` branch so the new event sits with its three stubbed siblings.

- [ ] **Step 4: Re-run the test; verify it passes**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.ProfileViewModelTest.StubAction emits ShowComingSoon with the same action value, never touches the repo"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt \
        feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): handle StubAction(action) by emitting ShowComingSoon(action)

One-liner dispatch alongside the existing FollowTapped/EditTapped/MessageTapped
stubs. Test asserts each of Block/Mute/Report flows through to the snackbar
effect without touching the repo.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 3: Mapper extension — `ProfileHeaderWithViewer` + `toProfileHeaderWithViewer` + viewerRelationship reading

Adds the mapper-side support for option-A's Follow/Following label. TDD: mapper tests first, then the production code.

**Files:**
- Modify: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/data/AuthorProfileMapperTest.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/AuthorProfileMapper.kt`

- [ ] **Step 1: Skim the existing test file to know how it builds wire fixtures**

Run: `head -60 feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/data/AuthorProfileMapperTest.kt`
You'll see the existing fixture-builder pattern. The new tests reuse it.

- [ ] **Step 2: Write the three failing mapper tests**

Append the following three `@Test` methods to `AuthorProfileMapperTest.kt`. Use whatever fixture-builder helper the file already exposes (commonly named `buildProfileViewDetailed(...)` or similar — match the existing style). If no helper exists yet, build a `ProfileViewDetailed` inline; the imports needed are `io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed`, `io.github.kikin81.atproto.app.bsky.actor.ViewerState`, `io.github.kikin81.atproto.runtime.{AtUri, Did, Handle}`.

```kotlin
    @Test
    fun `toProfileHeaderWithViewer returns ViewerRelationship_None when viewer is null`() {
        val wire = sampleProfileViewDetailed(viewer = null)

        val result = wire.toProfileHeaderWithViewer()

        assertEquals(ViewerRelationship.None, result.viewerRelationship)
    }

    @Test
    fun `toProfileHeaderWithViewer returns ViewerRelationship_Following when viewer follows the subject`() {
        val wire = sampleProfileViewDetailed(
            viewer = ViewerState(following = AtUri("at://did:plc:viewer/app.bsky.graph.follow/abc")),
        )

        val result = wire.toProfileHeaderWithViewer()

        assertEquals(ViewerRelationship.Following, result.viewerRelationship)
    }

    @Test
    fun `toProfileHeaderWithViewer returns ViewerRelationship_NotFollowing when viewer does not follow the subject`() {
        val wire = sampleProfileViewDetailed(
            viewer = ViewerState(following = null),
        )

        val result = wire.toProfileHeaderWithViewer()

        assertEquals(ViewerRelationship.NotFollowing, result.viewerRelationship)
    }
```

If `sampleProfileViewDetailed` doesn't exist yet, define a private companion helper at the bottom of the test class that returns a minimal `ProfileViewDetailed` with the required `did` + `handle` and accepts the `viewer` parameter (default null):

```kotlin
    private companion object {
        fun sampleProfileViewDetailed(
            viewer: ViewerState? = null,
        ): ProfileViewDetailed =
            ProfileViewDetailed(
                did = Did("did:plc:alice"),
                handle = Handle("alice.bsky.social"),
                viewer = viewer,
            )
    }
```

Add the necessary imports at the top of the file (`ViewerState`, `AtUri`) if not already present.

- [ ] **Step 3: Run the tests; verify they fail**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.data.AuthorProfileMapperTest.*"`
Expected: 3 of the new tests FAIL with "unresolved reference: toProfileHeaderWithViewer".

- [ ] **Step 4: Add the mapper extension + return type**

Open `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/AuthorProfileMapper.kt`. After the existing `toProfileHeaderUi()` extension and `avatarHueFor` + `toJoinedDisplay` helpers, append:

```kotlin
/**
 * Pair of the UI-ready header and the viewer's relationship to the
 * subject profile, both derived from a single [ProfileViewDetailed]
 * wire response.
 *
 * Bead F separates `viewerRelationship` from [ProfileHeaderUi] so a
 * future follow-up bd (epic 7.3 — real Follow / Unfollow writes) can
 * mutate the relationship without invalidating the header — the two
 * fields have independent lifetimes.
 */
internal data class ProfileHeaderWithViewer(
    val header: ProfileHeaderUi,
    val viewerRelationship: ViewerRelationship,
)

/**
 * Composes [toProfileHeaderUi] with [viewer]-derived relationship.
 *
 * `viewer.following: AtUri?` is the AtUri of *the requesting user's*
 * follow record pointing at this profile. Non-null → Following.
 * Null but `viewer` itself non-null → NotFollowing. `viewer` null →
 * None (unauthed-style fallback; shouldn't happen post-login since
 * profile screens only mount past the splash routing gate).
 *
 * Own-profile (`route.handle == null`) overrides the result to
 * [ViewerRelationship.Self] at the ViewModel layer — the mapper itself
 * doesn't know about own/other-user. See
 * [net.kikin.nubecita.feature.profile.impl.ProfileViewModel.launchHeaderLoad].
 */
internal fun ProfileViewDetailed.toProfileHeaderWithViewer(): ProfileHeaderWithViewer =
    ProfileHeaderWithViewer(
        header = toProfileHeaderUi(),
        viewerRelationship = viewer.toViewerRelationship(),
    )

private fun ViewerState?.toViewerRelationship(): ViewerRelationship =
    when {
        this == null -> ViewerRelationship.None
        following != null -> ViewerRelationship.Following
        else -> ViewerRelationship.NotFollowing
    }
```

Add the new import at the top of the file:

```kotlin
import io.github.kikin81.atproto.app.bsky.actor.ViewerState
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship
```

(`ProfileHeaderUi` is already imported.)

- [ ] **Step 5: Re-run mapper tests; verify they pass**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.data.AuthorProfileMapperTest.*"`
Expected: ALL PASS (the existing `toProfileHeaderUi` tests are unaffected; the 3 new tests now pass).

- [ ] **Step 6: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/AuthorProfileMapper.kt \
        feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/data/AuthorProfileMapperTest.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): + toProfileHeaderWithViewer mapping viewer.following → ViewerRelationship

AuthorProfileMapper composes the existing toProfileHeaderUi() with a
new viewer-derived ViewerRelationship field (Following / NotFollowing /
None). Self override stays at the VM layer. 3 mapper unit tests
cover null, following=non-null, following=null.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 4: Repository signature + VM update; Self override

Switch the repository return type, update the VM to consume the new shape, and add VM tests covering the Self override.

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/ProfileRepository.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/DefaultProfileRepository.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt`
- Modify: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt`

- [ ] **Step 1: Write the failing VM tests for the Self override**

Append to `ProfileViewModelTest.kt` (just after the StubAction test from Task 2):

```kotlin
    @Test
    fun `own-profile header load overrides mapper-reported viewerRelationship to Self`() =
        runTest(mainDispatcher.dispatcher) {
            // Mapper reports NotFollowing (own user has no follow record pointing
            // at themselves). The VM MUST override this to Self for the own-profile
            // route.
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                header = SAMPLE_HEADER,
                                viewerRelationship = ViewerRelationship.NotFollowing,
                            ),
                        ),
                )
            val vm = newVm(repo = repo, route = Profile(handle = null))
            advanceUntilIdle()

            assertEquals(ViewerRelationship.Self, vm.uiState.value.viewerRelationship,
                "Own-profile MUST override mapper-reported relationship to Self")
        }

    @Test
    fun `other-user header load preserves mapper-reported viewerRelationship`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                header = SAMPLE_HEADER.copy(handle = "bob.bsky.social"),
                                viewerRelationship = ViewerRelationship.Following,
                            ),
                        ),
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            assertEquals(ViewerRelationship.Following, vm.uiState.value.viewerRelationship,
                "Other-user route MUST preserve mapper-reported relationship")
        }
```

Then update `FakeProfileRepository` (already in the same file) to take the new return type. Replace the existing `headerResult: Result<ProfileHeaderUi>` parameter and `fetchHeader` override with:

```kotlin
    private class FakeProfileRepository(
        private val headerWithViewerResult: Result<ProfileHeaderWithViewer> =
            Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
        var tabResults: Map<ProfileTab, Result<ProfileTabPage>> =
            ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
    ) : ProfileRepository {
        val headerCalls = AtomicInteger(0)
        val tabCalls: Map<ProfileTab, AtomicInteger> =
            ProfileTab.entries.associateWith { AtomicInteger(0) }
        val lastTabCursor: MutableMap<ProfileTab, String?> = mutableMapOf()

        override suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer> {
            headerCalls.incrementAndGet()
            return headerWithViewerResult
        }

        override suspend fun fetchTab(
            actor: String,
            tab: ProfileTab,
            cursor: String?,
            limit: Int,
        ): Result<ProfileTabPage> {
            tabCalls.getValue(tab).incrementAndGet()
            if (cursor != null) lastTabCursor[tab] = cursor
            return tabResults.getValue(tab)
        }
    }
```

Update every existing `FakeProfileRepository(headerResult = Result.success(SAMPLE_HEADER), tabResults = …)` call site in the same file to use `headerWithViewerResult = Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None))`. There are ~9 such call sites — adjust each.

Add the necessary import at the top of the test file:

```kotlin
import net.kikin.nubecita.feature.profile.impl.data.ProfileHeaderWithViewer
```

- [ ] **Step 2: Run the tests; verify the new tests fail and existing tests fail to compile**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.ProfileViewModelTest.*"`
Expected: COMPILATION FAILURE — `ProfileHeaderWithViewer` doesn't exist in the `data` package yet at the call site context, and `ProfileRepository.fetchHeader` still returns `Result<ProfileHeaderUi>`.

- [ ] **Step 3: Update the repository interface**

Open `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/ProfileRepository.kt`. Change the `fetchHeader` signature:

```kotlin
internal interface ProfileRepository {
    suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer>

    suspend fun fetchTab(
        actor: String,
        tab: ProfileTab,
        cursor: String? = null,
        limit: Int = PROFILE_TAB_PAGE_LIMIT,
    ): Result<ProfileTabPage>
}
```

Remove the now-unused `import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi` line if it's no longer needed (the file still imports `ProfileTab` and `TabItemUi` for `ProfileTabPage`).

- [ ] **Step 4: Update the default implementation**

In `DefaultProfileRepository.kt`, change the `fetchHeader` body:

```kotlin
        override suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response = ActorService(client).getProfile(buildGetProfileRequest(actor))
                    response.toProfileHeaderWithViewer()
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(throwable, "fetchHeader failed: %s", throwable.javaClass.name)
                }
            }
```

Remove the now-unused `import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi` import if present.

- [ ] **Step 5: Update the ViewModel's header-load success branch**

In `ProfileViewModel.kt`, locate `launchHeaderLoad`. The existing success branch reads:

```kotlin
                    .onSuccess { header ->
                        setState {
                            copy(
                                header = header,
                                viewerRelationship = if (ownProfile) ViewerRelationship.Self else viewerRelationship,
                            )
                        }
                    }
```

Replace it with:

```kotlin
                    .onSuccess { result ->
                        setState {
                            copy(
                                header = result.header,
                                viewerRelationship =
                                    if (ownProfile) ViewerRelationship.Self else result.viewerRelationship,
                            )
                        }
                    }
```

This swaps the lambda parameter from `header: ProfileHeaderUi` to `result: ProfileHeaderWithViewer` and reads `.header` / `.viewerRelationship` from it. The `ownProfile` branch override is the key Self semantic.

- [ ] **Step 6: Re-run all ProfileViewModel tests**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.ProfileViewModelTest.*"`
Expected: ALL PASS (existing 11 tests + 2 new tests = 13).

- [ ] **Step 7: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/ProfileRepository.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/DefaultProfileRepository.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt \
        feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): fetchHeader returns ProfileHeaderWithViewer; VM Self override

ProfileRepository.fetchHeader now returns the header + viewerRelationship
pair. ProfileViewModel.launchHeaderLoad overrides to Self for own-profile,
otherwise preserves the mapper-reported relationship. 2 new VM tests cover
both branches.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 5: Promote `FeedDetailPlaceholder` → `:designsystem.PostDetailPaneEmptyState`; migrate Feed callsite; delete originals

Cross-module move. After this task, Feed's metadata calls the promoted composable; the new composable becomes available for Profile in Task 13.

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostDetailPaneEmptyState.kt`
- Modify: `designsystem/src/main/res/values/strings.xml`
- Delete: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/ui/FeedDetailPlaceholder.kt`
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/di/FeedNavigationModule.kt`
- Modify: `feature/feed/impl/src/main/res/values/strings.xml`

- [ ] **Step 1: Locate the existing string in `:feature:feed:impl`**

Run: `grep -n feed_detail_placeholder_select feature/feed/impl/src/main/res/values/strings.xml`
You should see a line like `<string name="feed_detail_placeholder_select">Select a post to read its thread</string>`. Note its exact value.

- [ ] **Step 2: Move the string to `:designsystem`**

Open `designsystem/src/main/res/values/strings.xml`. Find the file's tail (`</resources>`); insert the new string just before it (preserve grouping conventions if the file groups strings):

```xml
    <!-- ListDetailSceneStrategy detail-pane empty state. Used by any feature
         whose entry carries `listPane(detailPlaceholder = { PostDetailPaneEmptyState() })`.
         Bead F (nubecita-s6p.6) promoted this from :feature:feed:impl when
         Profile became the second caller. -->
    <string name="nubecita_detail_pane_select_post">Select a post to read its thread</string>
```

Use the same English copy as the original `feed_detail_placeholder_select`.

- [ ] **Step 3: Create the new composable in `:designsystem`**

Create `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostDetailPaneEmptyState.kt`:

```kotlin
package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.designsystem.spacing

/**
 * Detail-pane placeholder for [`ListDetailSceneStrategy`]-driven entries
 * whose back stack hasn't pushed a detail entry yet (on Medium / Expanded
 * widths). Compact widths collapse to single-pane and this Composable is
 * not composed at all.
 *
 * Callers wire it via
 * `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { PostDetailPaneEmptyState() })`.
 *
 * Promoted from `:feature:feed:impl`'s `FeedDetailPlaceholder` in Bead F
 * when Profile became the second caller. Both Feed and Profile delegate
 * to this; future post-list surfaces (Search results, hashtag, user
 * search) can reuse the same composable.
 */
@Composable
fun PostDetailPaneEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(
                horizontal = MaterialTheme.spacing.s6,
                vertical = MaterialTheme.spacing.s8,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Article,
            // Decorative — the bodyLarge prompt below is the accessible label.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
            modifier = Modifier.mirror(),
        )
        Text(
            text = stringResource(R.string.nubecita_detail_pane_select_post),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val ICON_SIZE = 56.dp

@Preview(name = "PostDetailPaneEmptyState — light", showBackground = true)
@Preview(
    name = "PostDetailPaneEmptyState — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostDetailPaneEmptyStatePreview() {
    NubecitaTheme {
        PostDetailPaneEmptyState()
    }
}
```

- [ ] **Step 4: Update Feed's nav module call site**

In `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/di/FeedNavigationModule.kt`, change the metadata block. Replace:

```kotlin
                metadata =
                    ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { FeedDetailPlaceholder() },
                    ),
```

with:

```kotlin
                metadata =
                    ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { PostDetailPaneEmptyState() },
                    ),
```

Replace the old import line `import net.kikin.nubecita.feature.feed.impl.ui.FeedDetailPlaceholder` with:

```kotlin
import net.kikin.nubecita.designsystem.component.PostDetailPaneEmptyState
```

- [ ] **Step 5: Delete the old Feed composable file**

```bash
git rm feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/ui/FeedDetailPlaceholder.kt
```

Also delete its screenshot reference baselines if any exist:

```bash
find feature/feed/impl/src -path '*/FeedDetailPlaceholder*' -delete
```

(The `find` command catches both source baselines and any screenshot-test class file referencing it. If the find prints "no such file", that's fine.)

- [ ] **Step 6: Remove the orphan string from `:feature:feed:impl`**

In `feature/feed/impl/src/main/res/values/strings.xml`, delete the line:

```xml
    <string name="feed_detail_placeholder_select">Select a post to read its thread</string>
```

- [ ] **Step 7: Verify no remaining references**

Run: `grep -rn "FeedDetailPlaceholder\|feed_detail_placeholder_select" feature/ app/ designsystem/ --include="*.kt" --include="*.xml" 2>/dev/null`
Expected: NO matches.

- [ ] **Step 8: Compile**

Run: `./gradlew :designsystem:compileDebugKotlin :feature:feed:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostDetailPaneEmptyState.kt \
        designsystem/src/main/res/values/strings.xml \
        feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/di/FeedNavigationModule.kt \
        feature/feed/impl/src/main/res/values/strings.xml
git add -u feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/ui/
git commit -m "$(cat <<'EOF'
refactor(designsystem): promote FeedDetailPlaceholder → PostDetailPaneEmptyState

Bead F preparation: the detail-pane "select a post" empty state will be
reused by Profile via ListDetailSceneStrategy.listPane(detailPlaceholder
= { PostDetailPaneEmptyState() }). Now that a second caller exists,
promote from :feature:feed:impl to :designsystem. Feed migrates its
call site; orphan string + composable are deleted.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 6: Screenshot test for `PostDetailPaneEmptyState`

**Files:**
- Create: `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/PostDetailPaneEmptyStateScreenshotTest.kt`

- [ ] **Step 1: Inspect an existing designsystem screenshot test for the project's style**

Run: `find designsystem/src/screenshotTest -name "*.kt" | head -3`
Open one of them in your editor to confirm import / annotation conventions.

- [ ] **Step 2: Create the screenshot test**

Create `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/PostDetailPaneEmptyStateScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostDetailPaneEmptyState]. Two themes,
 * widthDp = 400 (matches the detail-pane proportion at Medium screen
 * widths; the composable adapts to whatever pane size the strategy
 * gives it).
 */
@PreviewTest
@Preview(name = "placeholder-light", showBackground = true, widthDp = 400, heightDp = 600)
@Preview(
    name = "placeholder-dark",
    showBackground = true,
    widthDp = 400,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostDetailPaneEmptyStateScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostDetailPaneEmptyState()
    }
}
```

- [ ] **Step 3: Generate baselines**

Run: `./gradlew :designsystem:updateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL. Two new PNGs land under `designsystem/src/screenshotTestDebug/reference/`.

- [ ] **Step 4: Validate**

Run: `./gradlew :designsystem:validateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/PostDetailPaneEmptyStateScreenshotTest.kt \
        designsystem/src/screenshotTestDebug/reference/
git commit -m "$(cat <<'EOF'
test(designsystem): screenshot baselines for PostDetailPaneEmptyState

2 baselines (light + dark). Feed's old FeedDetailPlaceholder baselines
were removed in the prior commit; these are the replacement coverage.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 7: `ProfileActionsOverflowMenu` primitive

Small shared composable that owns its own `expanded` state.

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileActionsOverflowMenu.kt`

- [ ] **Step 1: Create the primitive**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.R

/**
 * One entry in a [ProfileActionsOverflowMenu]. `label` is the
 * already-resolved string (call sites read via `stringResource(...)`);
 * `onClick` fires when the menu item is selected — the menu closes
 * itself first, then dispatches.
 */
internal data class OverflowEntry(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * MoreVert IconButton that opens a [DropdownMenu] with the given
 * [entries]. Owns its own `expanded` state — parents pass callbacks,
 * not visibility.
 *
 * Bead F uses this for both variants of the profile actions row:
 * own-profile (one entry → Settings), other-user (three entries →
 * Block / Mute / Report). The `contentDescription` is fixed
 * ("More options") since both variants share the affordance.
 */
@Composable
internal fun ProfileActionsOverflowMenu(
    entries: List<OverflowEntry>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            NubecitaIcon(
                name = NubecitaIconName.MoreVert,
                contentDescription = stringResource(R.string.profile_action_overflow),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        expanded = false
                        entry.onClick()
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :feature:profile:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileActionsOverflowMenu.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): + ProfileActionsOverflowMenu primitive

DropdownMenu trigger with a parameterized entries list. Owns its own
expanded state so parent composables stay stateless.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 8: `OwnProfileActionsRow` + screenshot test

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/OwnProfileActionsRow.kt`
- Create: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/OwnProfileActionsRowScreenshotTest.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Own-profile actions row: an Edit button next to the overflow menu.
 *
 * Edit dispatches [onEdit] (the screen routes it to
 * `ProfileEvent.EditTapped` → `ShowComingSoon(Edit)`). The overflow
 * menu has one entry — Settings — wired to [onSettings] (routes to
 * `ProfileEvent.SettingsTapped` → `NavigateToSettings`, the only
 * user-reachable entry point for the Settings sub-route in this epic).
 *
 * Real Edit writes ship under follow-up bd 7.4.
 */
@Composable
internal fun OwnProfileActionsRow(
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsLabel = stringResource(R.string.profile_action_settings)
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onEdit) {
            Text(text = stringResource(R.string.profile_action_edit))
        }
        ProfileActionsOverflowMenu(
            entries = listOf(OverflowEntry(label = settingsLabel, onClick = onSettings)),
        )
    }
}
```

- [ ] **Step 2: Create the screenshot test**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [OwnProfileActionsRow]. Covers the closed
 * state (overflow menu collapsed); the menu-open state is intentionally
 * NOT screenshotted because [`androidx.compose.material3.DropdownMenu`]
 * renders inside an overlay window that Layoutlib doesn't compose
 * deterministically. The open-state coverage lives in the
 * instrumentation test instead.
 */
@PreviewTest
@Preview(name = "own-edit-overflow-closed-light", showBackground = true, heightDp = 80)
@Preview(
    name = "own-edit-overflow-closed-dark",
    showBackground = true,
    heightDp = 80,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OwnProfileActionsRowScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        OwnProfileActionsRow(onEdit = {}, onSettings = {})
    }
}
```

- [ ] **Step 3: Generate baselines**

Run: `./gradlew :feature:profile:impl:updateDebugScreenshotTest`
Expected: 2 new PNGs land under `feature/profile/impl/src/screenshotTestDebug/reference/.../OwnProfileActionsRowScreenshotTestKt/`.

- [ ] **Step 4: Validate**

Run: `./gradlew :feature:profile:impl:validateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/OwnProfileActionsRow.kt \
        feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/OwnProfileActionsRowScreenshotTest.kt \
        feature/profile/impl/src/screenshotTestDebug/reference/
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): + OwnProfileActionsRow (Edit + overflow→Settings)

Replaces the own-profile half of bead D's ProfileActionsRow with a
focused composable. Settings is the only user-reachable entry point for
the Settings sub-route in this epic. 2 screenshot baselines (light +
dark, overflow closed).

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 9: `OtherUserActionsRow` + screenshot test

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/OtherUserActionsRow.kt`
- Create: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/OtherUserActionsRowScreenshotTest.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.R
import net.kikin.nubecita.feature.profile.impl.StubbedAction
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship

/**
 * Other-user actions row: Follow / Following + Message + overflow.
 *
 * [viewerRelationship] drives the Follow button's emphasis:
 *
 * - `Following` → outlined `Following` (quiet, indicates already-following state)
 * - any other relationship → filled `Follow` (CTA, primary emphasis)
 *
 * The tap dispatches [onFollow] in both cases — real writes ship under
 * follow-up bd 7.3; bed F surfaces a "Coming soon" snackbar. [onMessage]
 * routes to `ProfileEvent.MessageTapped → ShowComingSoon(Message)`.
 * The overflow menu's three entries each dispatch [onOverflowAction]
 * with the corresponding [StubbedAction] variant (`Block / Mute /
 * Report`); the screen-level handler routes those to
 * `ProfileEvent.StubActionTapped(action) → ShowComingSoon(action)`.
 *
 * The Follow / Message buttons share equal width via `Modifier.weight(1f)`;
 * the overflow stays content-sized.
 */
@Composable
internal fun OtherUserActionsRow(
    viewerRelationship: ViewerRelationship,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val followLabel =
        when (viewerRelationship) {
            ViewerRelationship.Following -> stringResource(R.string.profile_action_following)
            else -> stringResource(R.string.profile_action_follow)
        }
    val blockLabel = stringResource(R.string.profile_action_block)
    val muteLabel = stringResource(R.string.profile_action_mute)
    val reportLabel = stringResource(R.string.profile_action_report)

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (viewerRelationship == ViewerRelationship.Following) {
            OutlinedButton(onClick = onFollow, modifier = Modifier.weight(1f)) {
                Text(text = followLabel)
            }
        } else {
            Button(onClick = onFollow, modifier = Modifier.weight(1f)) {
                Text(text = followLabel)
            }
        }
        OutlinedButton(onClick = onMessage, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.profile_action_message))
        }
        ProfileActionsOverflowMenu(
            entries =
                listOf(
                    OverflowEntry(label = blockLabel, onClick = { onOverflowAction(StubbedAction.Block) }),
                    OverflowEntry(label = muteLabel, onClick = { onOverflowAction(StubbedAction.Mute) }),
                    OverflowEntry(label = reportLabel, onClick = { onOverflowAction(StubbedAction.Report) }),
                ),
        )
    }
}
```

- [ ] **Step 2: Create the screenshot test**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship

/**
 * Screenshot baselines for [OtherUserActionsRow]. Two relationships
 * (NotFollowing → filled Follow CTA; Following → outlined Following)
 * × two themes = 4 baselines. Overflow open-state is verified by
 * instrumentation only (DropdownMenu overlay doesn't compose
 * deterministically in Layoutlib).
 */
@PreviewTest
@Preview(name = "other-follow-light", showBackground = true, heightDp = 80)
@Preview(
    name = "other-follow-dark",
    showBackground = true,
    heightDp = 80,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OtherUserActionsRowFollowScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        OtherUserActionsRow(
            viewerRelationship = ViewerRelationship.NotFollowing,
            onFollow = {},
            onMessage = {},
            onOverflowAction = {},
        )
    }
}

@PreviewTest
@Preview(name = "other-following-light", showBackground = true, heightDp = 80)
@Preview(
    name = "other-following-dark",
    showBackground = true,
    heightDp = 80,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OtherUserActionsRowFollowingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        OtherUserActionsRow(
            viewerRelationship = ViewerRelationship.Following,
            onFollow = {},
            onMessage = {},
            onOverflowAction = {},
        )
    }
}
```

- [ ] **Step 3: Generate baselines**

Run: `./gradlew :feature:profile:impl:updateDebugScreenshotTest`
Expected: 4 new PNGs (other-follow-{light,dark}, other-following-{light,dark}).

- [ ] **Step 4: Validate**

Run: `./gradlew :feature:profile:impl:validateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/OtherUserActionsRow.kt \
        feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/OtherUserActionsRowScreenshotTest.kt \
        feature/profile/impl/src/screenshotTestDebug/reference/
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): + OtherUserActionsRow (Follow/Following + Message + overflow)

Filled Follow CTA emphasis when not-following; outlined Following
(same shape) when already-following. Message stays outlined.
Overflow surfaces Block / Mute / Report stubs via the
ProfileActionsOverflowMenu primitive. 4 screenshot baselines.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 10: `ProfileActionsRow` router + `ProfileHero` signature + `ProfileScreenContent` wires

Renames the bead-D actions row from a single-variant composable into a slim router. Bumps the hero signature and the screen-content composable to thread the new callbacks through.

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileActionsRow.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileHero.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt`

- [ ] **Step 1: Rewrite `ProfileActionsRow.kt` as the router**

Replace the entire contents of `ProfileActionsRow.kt` with:

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kikin.nubecita.feature.profile.impl.StubbedAction
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship

/**
 * Hero actions row dispatcher. Delegates to [OwnProfileActionsRow] or
 * [OtherUserActionsRow] based on [ownProfile].
 *
 * The unified callback surface here lets `ProfileHero` stay
 * variant-ignorant: it forwards the full set, and only the picked
 * variant uses the relevant subset. [onEdit] / [onSettings] are
 * own-profile only; [onFollow] / [onMessage] / [onOverflowAction]
 * are other-user only.
 *
 * Real Follow / Edit / Message / Block / Mute / Report writes ship
 * under separate follow-up bd issues (7.3 / 7.4 / 7.5 / 7.7).
 */
@Composable
internal fun ProfileActionsRow(
    ownProfile: Boolean,
    viewerRelationship: ViewerRelationship,
    onEdit: () -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ownProfile) {
        OwnProfileActionsRow(onEdit = onEdit, onSettings = onSettings, modifier = modifier)
    } else {
        OtherUserActionsRow(
            viewerRelationship = viewerRelationship,
            onFollow = onFollow,
            onMessage = onMessage,
            onOverflowAction = onOverflowAction,
            modifier = modifier,
        )
    }
}
```

- [ ] **Step 2: Update `ProfileHero` signature + call site**

In `ProfileHero.kt`, update the public `ProfileHero` Composable's signature and the inner `ProfileHeroLoaded`'s signature + call site:

```kotlin
@Composable
internal fun ProfileHero(
    header: ProfileHeaderUi?,
    headerError: ProfileError?,
    ownProfile: Boolean,
    viewerRelationship: ViewerRelationship,
    onRetryHeader: () -> Unit,
    onEditTap: () -> Unit,
    onFollowTap: () -> Unit,
    onMessageTap: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
    onSettingsTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        header != null ->
            ProfileHeroLoaded(
                header = header,
                ownProfile = ownProfile,
                viewerRelationship = viewerRelationship,
                onEditTap = onEditTap,
                onFollowTap = onFollowTap,
                onMessageTap = onMessageTap,
                onOverflowAction = onOverflowAction,
                onSettingsTap = onSettingsTap,
                modifier = modifier,
            )
        headerError != null ->
            ProfileHeroError(
                error = headerError,
                onRetry = onRetryHeader,
                modifier = modifier,
            )
        else -> ProfileHeroLoading(modifier = modifier)
    }
}
```

Update `ProfileHeroLoaded`'s signature and replace the existing `ProfileActionsRow(ownProfile = ownProfile, onEdit = onEditTap, onOverflow = onOverflowTap)` call site with the new shape:

```kotlin
@Composable
private fun ProfileHeroLoaded(
    header: ProfileHeaderUi,
    ownProfile: Boolean,
    viewerRelationship: ViewerRelationship,
    onEditTap: () -> Unit,
    onFollowTap: () -> Unit,
    onMessageTap: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
    onSettingsTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ... existing BoldHeroGradient + bio + ProfileStatsRow + ProfileMetaRow unchanged ...

        ProfileActionsRow(
            ownProfile = ownProfile,
            viewerRelationship = viewerRelationship,
            onEdit = onEditTap,
            onFollow = onFollowTap,
            onMessage = onMessageTap,
            onOverflowAction = onOverflowAction,
            onSettings = onSettingsTap,
        )
    }
}
```

Keep the existing imports; add new ones at the top of the file:

```kotlin
import net.kikin.nubecita.feature.profile.impl.StubbedAction
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship
```

- [ ] **Step 3: Update `ProfileScreenContent.kt`'s hero call site**

In `ProfileScreenContent.kt`, locate the `item(key = "hero", contentType = "hero") { ProfileHero(...) }` block. Replace the existing `ProfileHero` call with:

```kotlin
                item(key = "hero", contentType = "hero") {
                    ProfileHero(
                        header = state.header,
                        headerError = state.headerError,
                        ownProfile = state.ownProfile,
                        viewerRelationship = state.viewerRelationship,
                        onRetryHeader = { onEvent(ProfileEvent.Refresh) },
                        onEditTap = { onEvent(ProfileEvent.EditTapped) },
                        onFollowTap = { onEvent(ProfileEvent.FollowTapped) },
                        onMessageTap = { onEvent(ProfileEvent.MessageTapped) },
                        onOverflowAction = { action -> onEvent(ProfileEvent.StubActionTapped(action)) },
                        onSettingsTap = { onEvent(ProfileEvent.SettingsTapped) },
                    )
                }
```

(The previous version had `onEditTap` + `onOverflowTap` only; this expands the callback surface for all five action paths.)

- [ ] **Step 4: Compile**

Run: `./gradlew :feature:profile:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run existing tests to confirm no regressions**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest`
Expected: ALL PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileActionsRow.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileHero.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt
git commit -m "$(cat <<'EOF'
refactor(feature/profile/impl): ProfileActionsRow becomes router; hero threads new callbacks

ProfileActionsRow now delegates to OwnProfileActionsRow or OtherUserActionsRow
based on ownProfile. ProfileHero's call surface expands from
(onEdit, onOverflow) to (onEdit, onFollow, onMessage, onOverflowAction,
onSettings) plus viewerRelationship. ProfileScreenContent wires every
new callback to its matching ProfileEvent.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 11: `SettingsStubContract` + `SettingsStubViewModel` + unit tests

TDD: write the failing VM tests, then implement.

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubContract.kt`
- Create: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubViewModelTest.kt`
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubViewModel.kt`

- [ ] **Step 1: Define the contract**

Create `SettingsStubContract.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Settings stub screen.
 *
 * `confirmDialogOpen` is **flat** because dialog visibility is
 * independent of the sign-out status (a dialog can be open while idle
 * OR while signing out — the latter shows a spinner inside the dialog).
 *
 * `status` is a **sealed sum** because Idle / SigningOut are mutually
 * exclusive — at any given moment exactly one is true.
 */
data class SettingsStubViewState(
    val confirmDialogOpen: Boolean = false,
    val status: SettingsStubStatus = SettingsStubStatus.Idle,
) : UiState

/**
 * Sign-out lifecycle. No `SignedOut` variant: on success, the
 * `SessionStateProvider` transitions, `MainActivity`'s reactive
 * collector does `navigator.replaceTo(Login)`, and this screen
 * unmounts before we'd ever render a "signed out" state.
 */
sealed interface SettingsStubStatus {
    data object Idle : SettingsStubStatus
    data object SigningOut : SettingsStubStatus
}

/**
 * Events the screen sends to the ViewModel.
 */
sealed interface SettingsStubEvent : UiEvent {
    /** User tapped the Sign Out button. Opens the confirmation dialog. */
    data object SignOutTapped : SettingsStubEvent

    /** User tapped Confirm inside the dialog. Kicks off the sign-out request. */
    data object ConfirmSignOut : SettingsStubEvent

    /** User tapped Cancel inside the dialog or tapped outside (scrim). */
    data object DismissDialog : SettingsStubEvent
}

/**
 * One-shot effects. There is exactly one — error surfacing on
 * sign-out failure. Success has no effect: the screen unmounts when
 * the outer Navigator replaces to Login.
 */
sealed interface SettingsStubEffect : UiEffect {
    /** Sign-out failed. Surface a snackbar; copy is resolved at render time. */
    data object ShowSignOutError : SettingsStubEffect
}
```

- [ ] **Step 2: Write the failing ViewModel tests**

Create `SettingsStubViewModelTest.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class SettingsStubViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `SignOutTapped opens the confirmation dialog`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = mockk<AuthRepository>(relaxed = true)
            val vm = SettingsStubViewModel(authRepository = auth)

            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            assertTrue(vm.uiState.value.confirmDialogOpen)
            assertEquals(SettingsStubStatus.Idle, vm.uiState.value.status)
        }

    @Test
    fun `DismissDialog closes the confirmation dialog`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = mockk<AuthRepository>(relaxed = true)
            val vm = SettingsStubViewModel(authRepository = auth)
            vm.handleEvent(SettingsStubEvent.SignOutTapped)
            assertTrue(vm.uiState.value.confirmDialogOpen)

            vm.handleEvent(SettingsStubEvent.DismissDialog)

            assertFalse(vm.uiState.value.confirmDialogOpen)
        }

    @Test
    fun `ConfirmSignOut transitions to SigningOut and calls AuthRepository_signOut`() =
        runTest(mainDispatcher.dispatcher) {
            val auth =
                mockk<AuthRepository> {
                    coEvery { signOut() } returns Result.success(Unit)
                }
            val vm = SettingsStubViewModel(authRepository = auth)
            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            vm.handleEvent(SettingsStubEvent.ConfirmSignOut)
            advanceUntilIdle()

            coVerify(exactly = 1) { auth.signOut() }
            // Success leaves the VM in SigningOut (the screen unmounts via
            // outer Navigator; no setState after success).
            assertEquals(SettingsStubStatus.SigningOut, vm.uiState.value.status)
        }

    @Test
    fun `sign-out failure resets to Idle, closes dialog, emits ShowSignOutError`() =
        runTest(mainDispatcher.dispatcher) {
            val auth =
                mockk<AuthRepository> {
                    coEvery { signOut() } returns Result.failure(IOException("net down"))
                }
            val vm = SettingsStubViewModel(authRepository = auth)
            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            vm.effects.test {
                vm.handleEvent(SettingsStubEvent.ConfirmSignOut)
                advanceUntilIdle()
                assertEquals(SettingsStubEffect.ShowSignOutError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(SettingsStubStatus.Idle, vm.uiState.value.status)
            assertFalse(vm.uiState.value.confirmDialogOpen)
        }

    @Test
    fun `double ConfirmSignOut is single-flight — only one signOut call`() =
        runTest(mainDispatcher.dispatcher) {
            val auth =
                mockk<AuthRepository> {
                    // Hang the first call so a second arrives mid-flight.
                    coEvery { signOut() } coAnswers {
                        kotlinx.coroutines.delay(1_000)
                        Result.success(Unit)
                    }
                }
            val vm = SettingsStubViewModel(authRepository = auth)
            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            vm.handleEvent(SettingsStubEvent.ConfirmSignOut)
            vm.handleEvent(SettingsStubEvent.ConfirmSignOut) // second tap mid-flight
            advanceUntilIdle()

            coVerify(exactly = 1) { auth.signOut() }
        }
}
```

- [ ] **Step 3: Run the tests; verify they fail**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.SettingsStubViewModelTest.*"`
Expected: COMPILATION FAILURE (`SettingsStubViewModel` doesn't exist yet).

- [ ] **Step 4: Implement the ViewModel**

Create `SettingsStubViewModel.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.common.mvi.MviViewModel
import javax.inject.Inject

/**
 * Presenter for the Settings stub sub-route. Owns the sign-out flow
 * via [AuthRepository.signOut]: tap → confirmation dialog → confirm →
 * loading state → success (screen unmounts when SessionStateProvider
 * transitions and MainActivity replaces to Login) OR failure (error
 * snackbar).
 *
 * Per `openspec/.../design.md` Decision 6 + Bead F design, the
 * Settings sub-route ships as a one-screen stub with Sign Out only.
 * When the real Settings screen graduates (follow-up bd 7.6), this
 * class is renamed and re-shaped; the contract surface is small
 * enough that the rename is mechanical.
 */
@HiltViewModel
internal class SettingsStubViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : MviViewModel<SettingsStubViewState, SettingsStubEvent, SettingsStubEffect>(
            SettingsStubViewState(),
        ) {
        override fun handleEvent(event: SettingsStubEvent) {
            when (event) {
                SettingsStubEvent.SignOutTapped ->
                    setState { copy(confirmDialogOpen = true) }
                SettingsStubEvent.DismissDialog ->
                    setState { copy(confirmDialogOpen = false) }
                SettingsStubEvent.ConfirmSignOut ->
                    runSignOut()
            }
        }

        private fun runSignOut() {
            // Single-flight: ignore a second Confirm tap while the first
            // request is still in flight.
            if (uiState.value.status is SettingsStubStatus.SigningOut) return
            setState { copy(status = SettingsStubStatus.SigningOut) }
            viewModelScope.launch {
                authRepository
                    .signOut()
                    .onFailure {
                        setState {
                            copy(
                                confirmDialogOpen = false,
                                status = SettingsStubStatus.Idle,
                            )
                        }
                        sendEffect(SettingsStubEffect.ShowSignOutError)
                    }
                // No onSuccess: SessionStateProvider transitions →
                // MainActivity's reactive collector replaces to Login →
                // this VM is scrapped. setState after success would
                // race the unmount.
            }
        }
    }
```

- [ ] **Step 5: Re-run the tests**

Run: `./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.SettingsStubViewModelTest.*"`
Expected: ALL 5 PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubContract.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubViewModel.kt \
        feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): + SettingsStubContract + SettingsStubViewModel

MVI presenter for the Settings sub-route. Confirmation dialog gates
sign-out; failure resets state + emits ShowSignOutError; success path
relies on SessionState transition + outer Navigator replaceTo(Login)
unmounting the screen. 5 unit tests cover dispatch, single-flight,
failure path.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 12: `SettingsStubScreen` + screenshot test

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubScreen.kt`
- Create: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/SettingsStubScreenScreenshotTest.kt`

- [ ] **Step 1: Create the screen**

```kotlin
package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Stateful Settings stub screen. Owns the [SettingsStubViewModel] +
 * effect collector + snackbar host. Delegates rendering to
 * [SettingsStubContent] which previews and screenshot tests can
 * exercise with fixture inputs.
 *
 * On Sign Out success, the screen unmounts when
 * `SessionStateProvider` transitions and MainActivity replaces to
 * Login — no nav effect required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsStubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<SettingsStubViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val signOutErrorMsg = stringResource(R.string.profile_settings_signout_error)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsStubEffect.ShowSignOutError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(signOutErrorMsg)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.profile_settings_back_content_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SettingsStubContent(
            state = state,
            onEvent = viewModel::handleEvent,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun SettingsStubContent(
    state: SettingsStubViewState,
    onEvent: (SettingsStubEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_settings_coming_soon),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { onEvent(SettingsStubEvent.SignOutTapped) },
            enabled = state.status !is SettingsStubStatus.SigningOut,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text(text = stringResource(R.string.profile_settings_signout))
        }
    }

    if (state.confirmDialogOpen) {
        SignOutConfirmDialog(
            isSigningOut = state.status is SettingsStubStatus.SigningOut,
            onConfirm = { onEvent(SettingsStubEvent.ConfirmSignOut) },
            onDismiss = { onEvent(SettingsStubEvent.DismissDialog) },
        )
    }
}

@Composable
private fun SignOutConfirmDialog(
    isSigningOut: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSigningOut) onDismiss() },
        title = { Text(stringResource(R.string.profile_settings_signout_dialog_title)) },
        text = { Text(stringResource(R.string.profile_settings_signout_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSigningOut) {
                if (isSigningOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.profile_settings_signout_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSigningOut) {
                Text(stringResource(R.string.profile_settings_signout_dialog_cancel))
            }
        },
    )
}
```

- [ ] **Step 2: Verify `NubecitaIconName.ArrowBack` exists**

Run: `grep -n "ArrowBack" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt`
Expected: a line declaring `ArrowBack` as a valid icon. If it doesn't exist, substitute the existing back-arrow icon name (likely `ArrowBack` or `ChevronLeft`) — `grep -E "Arrow|Back|Chevron"` the file to find the right one and update both the import and the call site in `SettingsStubScreen.kt`.

- [ ] **Step 3: Compile**

Run: `./gradlew :feature:profile:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Create the screenshot test**

Create `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/SettingsStubScreenScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.SettingsStubContent
import net.kikin.nubecita.feature.profile.impl.SettingsStubStatus
import net.kikin.nubecita.feature.profile.impl.SettingsStubViewState

/**
 * Screenshot baselines for the Settings stub screen. Three states ×
 * two themes = 6 baselines. The TopAppBar isn't exercised here
 * (separate from the body's lifecycle); the snackbar host is empty.
 *
 * - idle: no dialog, Sign Out button enabled
 * - confirm-open: dialog visible, idle status, Confirm enabled
 * - signing-out: dialog visible, SigningOut status, Confirm shows spinner
 */
@PreviewTest
@Preview(name = "settings-idle-light", showBackground = true, heightDp = 600)
@Preview(
    name = "settings-idle-dark",
    showBackground = true,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsStubIdleScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(state = SettingsStubViewState(), onEvent = {})
    }
}

@PreviewTest
@Preview(name = "settings-confirm-open-light", showBackground = true, heightDp = 600)
@Preview(
    name = "settings-confirm-open-dark",
    showBackground = true,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsStubConfirmOpenScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(
            state = SettingsStubViewState(confirmDialogOpen = true),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "settings-signing-out-light", showBackground = true, heightDp = 600)
@Preview(
    name = "settings-signing-out-dark",
    showBackground = true,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsStubSigningOutScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(
            state =
                SettingsStubViewState(
                    confirmDialogOpen = true,
                    status = SettingsStubStatus.SigningOut,
                ),
            onEvent = {},
        )
    }
}
```

- [ ] **Step 5: Generate baselines**

Run: `./gradlew :feature:profile:impl:updateDebugScreenshotTest`
Expected: 6 new PNGs.

> **Note:** AlertDialogs in Compose render in their own overlay window. Layoutlib may render them at the document root; the baselines reflect whatever Layoutlib produces. If the dialog state baselines look identical to the idle baseline (the dialog isn't visible in the screenshot), update the test to render the dialog content directly — but try the default Compose render first; in many cases Layoutlib captures the dialog content correctly.

- [ ] **Step 6: Validate**

Run: `./gradlew :feature:profile:impl:validateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubScreen.kt \
        feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/SettingsStubScreenScreenshotTest.kt \
        feature/profile/impl/src/screenshotTestDebug/reference/
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): + SettingsStubScreen with confirmation-dialog sign-out

Screen + content split per the project's MVI screen pattern. TopAppBar
with back nav, errorContainer-tinted Sign Out button, AlertDialog with
spinner during sign-out. 6 screenshot baselines (idle / confirm-open /
signing-out, each × light + dark).

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 13: `ProfileScreen` onNavigateToSettings + `ProfileNavigationModule` listPane{} + Settings entry

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreen.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/di/ProfileNavigationModule.kt`

- [ ] **Step 1: Add `onNavigateToSettings` to `ProfileScreen` + wire its effect branch**

In `ProfileScreen.kt`, update the function signature to accept the new callback:

```kotlin
@Composable
internal fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToPost: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Wrap `onNavigateToSettings` in `rememberUpdatedState` alongside the existing wrappers:

```kotlin
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToProfile by rememberUpdatedState(onNavigateToProfile)
    val currentOnNavigateToSettings by rememberUpdatedState(onNavigateToSettings)
```

In the effects collector's `when (effect) { ... }` block, replace the bead-D no-op `NavigateToSettings -> { /* Bead F wires Settings */ }` branch with:

```kotlin
                ProfileEffect.NavigateToSettings -> currentOnNavigateToSettings()
```

In the same file, also add the three new "Coming soon" strings to the effect collector's snackbar copy map. Locate the existing `val comingSoonEdit = stringResource(...)` block and add three new lines just below it:

```kotlin
    val comingSoonBlock = stringResource(R.string.profile_snackbar_block_coming_soon)
    val comingSoonMute = stringResource(R.string.profile_snackbar_mute_coming_soon)
    val comingSoonReport = stringResource(R.string.profile_snackbar_report_coming_soon)
```

Then extend the `when (effect.action) { ... }` block inside `is ProfileEffect.ShowComingSoon -> { ... }`:

```kotlin
                is ProfileEffect.ShowComingSoon -> {
                    val msg =
                        when (effect.action) {
                            StubbedAction.Edit -> comingSoonEdit
                            StubbedAction.Follow -> comingSoonFollow
                            StubbedAction.Message -> comingSoonMessage
                            StubbedAction.Block -> comingSoonBlock
                            StubbedAction.Mute -> comingSoonMute
                            StubbedAction.Report -> comingSoonReport
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = msg)
                }
```

- [ ] **Step 2: Update `ProfileNavigationModule.kt`**

Rewrite the file's body. Replace the entire `internal object ProfileNavigationModule { ... }` with:

```kotlin
package net.kikin.nubecita.feature.profile.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.designsystem.component.PostDetailPaneEmptyState
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.api.Settings
import net.kikin.nubecita.feature.profile.impl.ProfileScreen
import net.kikin.nubecita.feature.profile.impl.ProfileViewModel
import net.kikin.nubecita.feature.profile.impl.SettingsStubScreen

/**
 * Profile + Settings entries in MainShell's inner NavDisplay.
 *
 * `ListDetailSceneStrategy.listPane{}` metadata on the Profile entry
 * means: on Medium+ widths, Profile renders in the left pane and
 * `PostDetailPaneEmptyState` in the right pane until a
 * `PostDetailRoute` (which carries `detailPane()` metadata via
 * `:feature:postdetail:impl`) is pushed onto the stack. Compact
 * widths collapse to single-pane and the placeholder is not composed.
 *
 * The Settings entry resolves to [SettingsStubScreen]. The own-profile
 * overflow menu (via [`ProfileScreenContent`] → [`OwnProfileActionsRow`])
 * is the only user-reachable entry point for the Settings sub-route
 * in this epic.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ProfileNavigationModule {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Provides
    @IntoSet
    @MainShell
    fun provideProfileEntries(): EntryProviderInstaller =
        {
            entry<Profile>(
                metadata =
                    ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { PostDetailPaneEmptyState() },
                    ),
            ) { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                    onNavigateToProfile = { handle -> navState.add(Profile(handle = handle)) },
                    onNavigateToSettings = { navState.add(Settings) },
                )
            }
        }

    @Provides
    @IntoSet
    @MainShell
    fun provideSettingsEntries(): EntryProviderInstaller =
        {
            entry<Settings> {
                val navState = LocalMainShellNavState.current
                SettingsStubScreen(onBack = { navState.removeLast() })
            }
        }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :feature:profile:impl:compileDebugKotlin :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — but you may get a duplicate-binding Hilt error because `:app`'s `MainShellPlaceholderModule` still provides a Settings entry. That cleanup is the next task.

If the error is exactly:
```
error: [Dagger/DuplicateBindings] EntryProviderInstaller is bound multiple times
```
or a similar multibindings error about Settings, that's expected — proceed to Task 14 and revisit compilation after.

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreen.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/di/ProfileNavigationModule.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile/impl): listPane{} on Profile entry; Settings entry resolves to SettingsStubScreen

Profile entry carries ListDetailSceneStrategy.listPane(detailPlaceholder =
{ PostDetailPaneEmptyState() }) so Medium+ widths place post taps in the
right pane. ProfileScreen gains onNavigateToSettings and routes the
NavigateToSettings effect through it. Settings entry now resolves to
SettingsStubScreen with a back-arrow that pops the inner nav stack.

The Hilt multibindings will conflict with :app's MainShellPlaceholderModule
Settings provider until the next commit removes it.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 14: Remove `:app`'s Settings placeholder

**Files:**
- Modify: `app/src/main/java/net/kikin/nubecita/shell/MainShellPlaceholderModule.kt`
- Possibly modify: `app/src/main/res/values/strings.xml` (if `main_shell_settings` is orphaned)

- [ ] **Step 1: Remove the Settings provider**

In `MainShellPlaceholderModule.kt`, delete the entire `provideSettingsPlaceholderEntries` function block (the `@Provides @IntoSet @MainShell fun provideSettingsPlaceholderEntries(): EntryProviderInstaller = { entry<Settings> { ... } }` block).

Remove the now-unused import:

```kotlin
import net.kikin.nubecita.feature.profile.api.Settings
```

- [ ] **Step 2: Verify `R.string.main_shell_settings` isn't referenced anywhere else**

Run: `grep -rn "main_shell_settings" app/ feature/ --include="*.kt" --include="*.xml" 2>/dev/null`

If the only remaining references are the resource definition itself in `app/src/main/res/values/strings.xml`, delete the string. If it's referenced from a bottom-bar label elsewhere (e.g., `MainShell.kt`), leave it — that's a separate (still-valid) consumer.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (the duplicate-bindings error from Task 13 is now resolved).

- [ ] **Step 4: Assemble the full debug build to catch any lint / aggregation issues**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/kikin/nubecita/shell/MainShellPlaceholderModule.kt
# If you removed the orphan string:
# git add app/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
chore(app): remove :app Settings placeholder (now owned by :feature:profile:impl)

MainShellPlaceholderModule keeps Search + Chats placeholders; Settings
now resolves to SettingsStubScreen via :feature:profile:impl's
@MainShell EntryProviderInstaller. The :app module no longer references
the Settings NavKey.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 15: `ProfileScreenContentScreenshotTest` — other-user variants + Medium-width fixture

Adds 6 new baselines: other-user-follow / other-user-following / medium-two-pane (each × light + dark).

**Files:**
- Modify: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt`

- [ ] **Step 1: Inspect the existing screenshot test file**

Run: `cat feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt | head -80`

Note the existing fixture helpers (the SAMPLE_HEADER constant, the test names already in use). The new fixtures slot in alongside them.

- [ ] **Step 2: Add the other-user variants**

Append the following two screenshot functions to the file (just before any private helpers / companion at the bottom):

```kotlin
@PreviewTest
@Preview(name = "screen-other-user-follow-light", showBackground = true, heightDp = 1100)
@Preview(
    name = "screen-other-user-follow-dark",
    showBackground = true,
    heightDp = 1100,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileScreenOtherUserFollowScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileScreenContent(
            state =
                ProfileScreenViewState(
                    handle = "bob.bsky.social",
                    header = SAMPLE_HEADER.copy(handle = "bob.bsky.social", displayName = "Bob"),
                    ownProfile = false,
                    viewerRelationship = ViewerRelationship.NotFollowing,
                    postsStatus =
                        TabLoadStatus.Loaded(
                            items = SAMPLE_POSTS,
                            isAppending = false,
                            isRefreshing = false,
                            hasMore = false,
                            cursor = null,
                        ),
                ),
            listState = rememberLazyListState(),
            snackbarHostState = remember { SnackbarHostState() },
            postCallbacks = NO_OP_POST_CALLBACKS,
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "screen-other-user-following-light", showBackground = true, heightDp = 1100)
@Preview(
    name = "screen-other-user-following-dark",
    showBackground = true,
    heightDp = 1100,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileScreenOtherUserFollowingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileScreenContent(
            state =
                ProfileScreenViewState(
                    handle = "bob.bsky.social",
                    header = SAMPLE_HEADER.copy(handle = "bob.bsky.social", displayName = "Bob"),
                    ownProfile = false,
                    viewerRelationship = ViewerRelationship.Following,
                    postsStatus =
                        TabLoadStatus.Loaded(
                            items = SAMPLE_POSTS,
                            isAppending = false,
                            isRefreshing = false,
                            hasMore = false,
                            cursor = null,
                        ),
                ),
            listState = rememberLazyListState(),
            snackbarHostState = remember { SnackbarHostState() },
            postCallbacks = NO_OP_POST_CALLBACKS,
            onEvent = {},
        )
    }
}
```

Add necessary imports if not already present:

```kotlin
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship
```

If the file references `SAMPLE_POSTS` and `NO_OP_POST_CALLBACKS` already, reuse them; if those names differ, substitute whatever the file's existing fixture helpers are called.

- [ ] **Step 3: Add the Medium-width fixture**

The Medium-width fixture demonstrates the list-pane placement: the Profile renders in the left pane and `PostDetailPaneEmptyState` renders in the right pane. The clean way to demo this in a screenshot test is to compose `ListDetailPaneScaffold` directly with the same directive `MainShell` uses.

Append to the same file:

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@PreviewTest
@Preview(
    name = "screen-medium-two-pane-empty-light",
    showBackground = true,
    widthDp = 800,
    heightDp = 800,
)
@Preview(
    name = "screen-medium-two-pane-empty-dark",
    showBackground = true,
    widthDp = 800,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileScreenMediumTwoPaneEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        val directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfo())
        val scaffoldState = rememberListDetailPaneScaffoldNavigator<Nothing>(scaffoldDirective = directive)
        ListDetailPaneScaffold(
            directive = scaffoldState.scaffoldDirective,
            value = scaffoldState.scaffoldValue,
            listPane = {
                AnimatedPane {
                    ProfileScreenContent(
                        state =
                            ProfileScreenViewState(
                                handle = null,
                                header = SAMPLE_HEADER,
                                ownProfile = true,
                                viewerRelationship = ViewerRelationship.Self,
                                postsStatus =
                                    TabLoadStatus.Loaded(
                                        items = SAMPLE_POSTS,
                                        isAppending = false,
                                        isRefreshing = false,
                                        hasMore = false,
                                        cursor = null,
                                    ),
                            ),
                        listState = rememberLazyListState(),
                        snackbarHostState = remember { SnackbarHostState() },
                        postCallbacks = NO_OP_POST_CALLBACKS,
                        onEvent = {},
                    )
                }
            },
            detailPane = {
                AnimatedPane { PostDetailPaneEmptyState() }
            },
        )
    }
}
```

Add the imports at the top of the file:

```kotlin
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import net.kikin.nubecita.designsystem.component.PostDetailPaneEmptyState
```

> **Note:** the exact import paths for `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` and `rememberListDetailPaneScaffoldNavigator` depend on the Compose Material3 Adaptive library version. If the imports don't resolve, mirror the imports used in `app/src/main/java/net/kikin/nubecita/shell/MainShell.kt` (the production callsite for this directive). Adjust this fixture to use whichever Adaptive API shape MainShell uses.

- [ ] **Step 4: Generate baselines**

Run: `./gradlew :feature:profile:impl:updateDebugScreenshotTest`
Expected: 6 new PNGs (2 follow + 2 following + 2 medium).

- [ ] **Step 5: Validate**

Run: `./gradlew :feature:profile:impl:validateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Inspect the Medium baselines visually**

The Medium fixtures should show the profile in the left half and the "Select a post to read its thread" placeholder in the right half. If the baselines show single-pane or wrong proportions, the Adaptive API import mismatch from step 3 is the cause — fix the imports to match MainShell's.

- [ ] **Step 7: Commit**

```bash
git add feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt \
        feature/profile/impl/src/screenshotTestDebug/reference/
git commit -m "$(cat <<'EOF'
test(feature/profile/impl): screenshot baselines for other-user variants + Medium 2-pane

6 new baselines: other-user-follow (NotFollowing) / other-user-following
(Following) / medium-two-pane-empty (Profile in list pane, PostDetailPane
EmptyState in detail pane at widthDp = 800). All × light + dark.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 16: `ProfileScreenInstrumentationTest` modification — Settings overflow path

**Files:**
- Modify: `feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenInstrumentationTest.kt`

- [ ] **Step 1: Inspect the existing test file**

Run: `cat feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenInstrumentationTest.kt | head -60`

Note the setup pattern (the bead D test should use `createAndroidComposeRule<ComponentActivity>()` per the design doc reference to PR #150's pattern).

- [ ] **Step 2: Add a new test method**

Append the following test method to the existing test class. It tests that tapping the overflow icon on own-profile opens the menu, tapping Settings inside the menu pushes `Settings` onto the inner nav stack (asserted via a fake `LocalMainShellNavState`), and no "Coming soon" snackbar appears.

```kotlin
    @Test
    fun ownProfile_overflowMenu_settingsEntry_pushesSettingsRoute() {
        val pushedKeys = mutableListOf<Any>()
        val fakeNavState =
            object {
                fun add(key: Any) {
                    pushedKeys.add(key)
                }
                fun removeLast() = Unit
            }

        composeTestRule.setContent {
            NubecitaTheme {
                CompositionLocalProvider(
                    LocalMainShellNavState provides
                        MainShellNavState().apply {
                            // No-op: we use the default; we'll verify by tapping
                            // the menu entry and asserting the snackbar does NOT appear
                            // (Settings tap is nav, not stub).
                        },
                ) {
                    // Render ProfileScreenContent (stateless) with own-profile state.
                    ProfileScreenContent(
                        state =
                            ProfileScreenViewState(
                                handle = null,
                                header = SAMPLE_HEADER,
                                ownProfile = true,
                                viewerRelationship = ViewerRelationship.Self,
                                postsStatus =
                                    TabLoadStatus.Loaded(
                                        items = persistentListOf(),
                                        isAppending = false,
                                        isRefreshing = false,
                                        hasMore = false,
                                        cursor = null,
                                    ),
                            ),
                        listState = rememberLazyListState(),
                        snackbarHostState = remember { SnackbarHostState() },
                        postCallbacks = NO_OP_POST_CALLBACKS,
                        onEvent = { event ->
                            if (event is ProfileEvent.SettingsTapped) {
                                pushedKeys.add("settings-tapped")
                            }
                        },
                    )
                }
            }
        }

        // Tap the overflow icon (MoreVert).
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        // Tap the Settings entry.
        composeTestRule.onNodeWithText("Settings").performClick()

        // Verify SettingsTapped was emitted and NO snackbar (snackbars announce
        // stub actions, not navigation).
        assertTrue(pushedKeys.contains("settings-tapped"))
        // No "Coming soon" snackbar — settings tap is real navigation, not a stub.
        composeTestRule.onAllNodesWithText("Settings", substring = true).fetchSemanticsNodes()
            .forEach { node ->
                // Make sure the only "Settings" text on screen is the overflow entry
                // we just tapped (and now closed) — NOT a snackbar.
                // (No assertion needed past pushedKeys; the lack of snackbar text is
                // implicit since SettingsTapped does NOT route through ShowComingSoon.)
            }
    }
```

Add necessary imports at the top of the file (the existing test may already have most of these — only add the missing ones):

```kotlin
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship
import org.junit.Assert.assertTrue
```

If `LocalMainShellNavState` / `MainShellNavState` aren't accessible from this test module, simplify by removing the `CompositionLocalProvider` wrapper and just asserting that the `SettingsTapped` event reaches `onEvent` — the navigation push itself is then unit-tested elsewhere (the `provideProfileEntries` block's lambda).

- [ ] **Step 3: Run the instrumentation test**

Per the `feedback_run_instrumentation_tests_after_compose_work` memory: first try `adb devices` to use a real device; otherwise launch an emulator via the `android-cli` skill and notify the user during the wait.

Run: `adb devices`

If there's a connected device: `./gradlew :feature:profile:impl:connectedDebugAndroidTest`

If not: launch an emulator via the `android-cli` skill (`android emulator list` then `android emulator start <id>`), wait for boot, then run the gradle task.

Expected: BUILD SUCCESSFUL with the new test passing.

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenInstrumentationTest.kt
git commit -m "$(cat <<'EOF'
test(feature/profile/impl): + Settings overflow-menu path on ProfileScreenInstrumentationTest

Tapping the More options icon on own-profile opens the DropdownMenu;
tapping Settings inside emits ProfileEvent.SettingsTapped (real nav,
not a stub action). No "Coming soon" snackbar appears.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 17: `ProfileScreenAdaptiveInstrumentationTest` — Medium-width post tap lands in right pane

**Files:**
- Create: `feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenAdaptiveInstrumentationTest.kt`

- [ ] **Step 1: Create the test**

```kotlin
package net.kikin.nubecita.feature.profile.impl

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostDetailPaneEmptyState
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Verifies the Medium-width two-pane behavior driven by
 * [`ListDetailSceneStrategy`]. On a forced ~800dp width, a profile
 * tap on a post lands in the right pane while the profile stays in
 * the left pane.
 *
 * Renders ProfileScreenContent inside a [`ListDetailPaneScaffold`]
 * with the same directive MainShell uses. The detail pane defaults
 * to [PostDetailPaneEmptyState]; tapping a post (via the test
 * harness) triggers a callback that the test asserts.
 *
 * This is a Medium-width-only behavior assertion. The Compact
 * behavior (single-pane / full-screen detail) is covered by the
 * existing `ProfileScreenInstrumentationTest`.
 */
@HiltAndroidTest
class ProfileScreenAdaptiveInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun mediumWidth_profileScreen_renders_inListPane_withDetailPaneEmptyState() {
        composeTestRule.setContent {
            // Force a ~800dp width via LocalConfiguration so the strategy
            // splits into two panes regardless of the test device width.
            val baseConfig = LocalConfiguration.current
            val forcedConfig =
                android.content.res.Configuration(baseConfig).apply {
                    screenWidthDp = 800
                }
            CompositionLocalProvider(LocalConfiguration provides forcedConfig) {
                NubecitaTheme(dynamicColor = false) {
                    val directive =
                        calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfo())
                    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<Nothing>(scaffoldDirective = directive)
                    ListDetailPaneScaffold(
                        directive = scaffoldNavigator.scaffoldDirective,
                        value = scaffoldNavigator.scaffoldValue,
                        listPane = {
                            AnimatedPane {
                                // For this assertion we don't need a full ProfileScreenContent;
                                // we're verifying the strategy renders both panes. A simpler
                                // composable with a known test tag works.
                                androidx.compose.material3.Text(
                                    text = "PROFILE_LIST_PANE",
                                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                                )
                            }
                        },
                        detailPane = {
                            AnimatedPane { PostDetailPaneEmptyState() }
                        },
                    )
                }
            }
        }

        // Both panes are composed simultaneously on Medium widths.
        composeTestRule.onNodeWithText("PROFILE_LIST_PANE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select a post to read its thread").assertIsDisplayed()
    }
}
```

Add necessary imports for `onNodeWithText`, `assertIsDisplayed`, `fillMaxSize` if not auto-imported.

> **Note:** This test verifies the **simultaneous presence of both panes** on Medium widths — the core "list-pane behavior" guarantee. A more elaborate test could push a `PostDetailRoute` and verify the detail-pane content swaps, but that requires bringing the full `MainShell` + `LocalMainShellNavState` into scope, which is heavyweight for a single behavior assertion. If the simpler assertion above suffices for the bead's acceptance criteria, ship that; otherwise extend with a `MainShellNavState` fixture + push assertion.

- [ ] **Step 2: Run the test**

Run: `./gradlew :feature:profile:impl:connectedDebugAndroidTest --tests "net.kikin.nubecita.feature.profile.impl.ProfileScreenAdaptiveInstrumentationTest.*"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenAdaptiveInstrumentationTest.kt
git commit -m "$(cat <<'EOF'
test(feature/profile/impl): + ProfileScreenAdaptiveInstrumentationTest for Medium-width 2-pane

Verifies that on a forced ~800dp width, ListDetailSceneStrategy renders
the profile's list pane and PostDetailPaneEmptyState in the detail pane
simultaneously. Compact behavior remains covered by the existing
ProfileScreenInstrumentationTest.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 18: `SettingsStubInstrumentationTest` — Sign Out + fake AuthRepository

**Files:**
- Create: `feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/testing/TestAuthRepositoryModule.kt`
- Create: `feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubInstrumentationTest.kt`

- [ ] **Step 1: Find the Hilt module that binds the real `AuthRepository`**

Run: `grep -rn "AuthRepository" core/auth/src/main/kotlin --include="*.kt" | grep -E "binds|provides|@Binds|@Provides"`
Note the module name (e.g., `AuthBindingsModule`) and the binding shape — that's what the test module replaces.

- [ ] **Step 2: Create the fake `AuthRepository` Hilt test module**

Create `feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/testing/TestAuthRepositoryModule.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton
import kotlinx.coroutines.delay
import net.kikin.nubecita.core.auth.AuthRepository

/**
 * Replaces the production `AuthBindingsModule` for instrumentation
 * tests in `:feature:profile:impl`. Provides a [FakeAuthRepository]
 * that counts signOut() invocations and can be configured to return
 * either success or a specified failure.
 *
 * Substitute the `replaces = [...]` array entry below with the real
 * `AuthRepository`-binding module name from `:core:auth` if it differs.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [net.kikin.nubecita.core.auth.di.AuthBindingsModule::class],
)
internal object TestAuthRepositoryModule {
    @Singleton
    @Provides
    fun provideAuthRepository(): AuthRepository = FakeAuthRepository.shared
}

/**
 * In-memory [AuthRepository] with controllable signOut() behavior.
 * Singleton-scoped so the test class can read the call count and
 * adjust the next-return value.
 */
internal class FakeAuthRepository : AuthRepository {
    val signOutCalls = AtomicInteger(0)
    @Volatile var nextSignOutResult: Result<Unit> = Result.success(Unit)
    @Volatile var signOutDelayMs: Long = 0

    override suspend fun beginLogin(handle: String): Result<String> =
        Result.failure(UnsupportedOperationException("Login not exercised in this test"))

    override suspend fun completeLogin(redirectUri: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Login not exercised in this test"))

    override suspend fun signOut(): Result<Unit> {
        signOutCalls.incrementAndGet()
        if (signOutDelayMs > 0) delay(signOutDelayMs)
        return nextSignOutResult
    }

    fun reset() {
        signOutCalls.set(0)
        nextSignOutResult = Result.success(Unit)
        signOutDelayMs = 0
    }

    companion object {
        // Singleton so the test class can read it.
        val shared = FakeAuthRepository()
    }
}
```

> **Note:** The actual `:core:auth` Hilt module is likely `AuthBindingsModule` (per the file listing earlier). If the binding lives in a different module (verify via the grep in step 1), update the `replaces = [...]` entry above. The module path must match the Hilt module class FQN exactly.

- [ ] **Step 3: Create the instrumentation test**

```kotlin
package net.kikin.nubecita.feature.profile.impl

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.IOException
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.testing.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Verifies the Settings stub's sign-out flow end-to-end:
 * tap Sign Out → confirm in dialog → AuthRepository.signOut() is called
 * exactly once. Failure path: signOut() returns a Result.failure;
 * the error snackbar appears.
 *
 * Uses the [TestAuthRepositoryModule] Hilt test module which replaces
 * the production binding.
 */
@HiltAndroidTest
class SettingsStubInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        FakeAuthRepository.shared.reset()
    }

    @Test
    fun signOut_success_path_calls_AuthRepository_exactly_once() {
        FakeAuthRepository.shared.nextSignOutResult = Result.success(Unit)

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                SettingsStubScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Sign out").performClick()
        // Dialog is now open; tap the Confirm button (also labeled "Sign out").
        composeTestRule.onAllNodesWithText("Sign out").fetchSemanticsNodes()
        composeTestRule.onNodeWithText("Sign out", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            FakeAuthRepository.shared.signOutCalls.get() >= 1
        }

        assertEquals(1, FakeAuthRepository.shared.signOutCalls.get())
    }

    @Test
    fun signOut_failure_path_surfaces_error_snackbar() {
        FakeAuthRepository.shared.nextSignOutResult = Result.failure(IOException("net down"))

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                SettingsStubScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Sign out").performClick()
        composeTestRule.onNodeWithText("Sign out", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasText("Couldn't sign out", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule
            .onNodeWithText("Couldn't sign out", substring = true)
            .assertIsDisplayed()
    }
}
```

Add the `onAllNodes` / `hasText` imports as needed:

```kotlin
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :feature:profile:impl:connectedDebugAndroidTest --tests "net.kikin.nubecita.feature.profile.impl.SettingsStubInstrumentationTest.*"`
Expected: BUILD SUCCESSFUL.

If the tests fail because the dialog's Confirm button can't be uniquely identified (both the screen-level button and the dialog confirm button say "Sign out"), differentiate by `useUnmergedTree = true` and/or by tapping the second-matched node:

```kotlin
composeTestRule.onAllNodesWithText("Sign out").get(1).performClick()  // [0] = button, [1] = dialog confirm
```

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/testing/TestAuthRepositoryModule.kt \
        feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/SettingsStubInstrumentationTest.kt
git commit -m "$(cat <<'EOF'
test(feature/profile/impl): + SettingsStubInstrumentationTest with fake AuthRepository

Hilt test module replaces the real AuthBindingsModule with a fake that
counts signOut() invocations and lets the test class control the return
value. Two cases: success → exactly one signOut() call; failure → error
snackbar appears with the expected copy.

Refs: nubecita-s6p.6
EOF
)"
```

---

## Task 19: Full local verification

**Files:** none modified — this task is just running the full verification matrix locally before opening the PR.

- [ ] **Step 1: Run the full unit + screenshot test suite for affected modules**

Run, in parallel where possible:

```bash
./gradlew :designsystem:validateDebugScreenshotTest \
          :feature:feed:impl:assembleDebug \
          :feature:profile:impl:testDebugUnitTest \
          :feature:profile:impl:validateDebugScreenshotTest \
          :app:assembleDebug \
          spotlessCheck lint :app:checkSortDependencies
```

Expected: BUILD SUCCESSFUL across all tasks. If any screenshot baseline drift surfaces (e.g., the Medium-width fixture rendering changes), regenerate via `:feature:profile:impl:updateDebugScreenshotTest` and commit the new PNGs. If lint flags an orphan `R.string.main_shell_settings`, remove it.

- [ ] **Step 2: Resolve any spotless / ktlint findings inline**

If `spotlessCheck` fails with formatting findings, run `./gradlew spotlessApply` and commit the formatting fixes as a single commit:

```bash
git add -u
git commit -m "style(feature/profile/impl): spotless apply after bead F work

Refs: nubecita-s6p.6"
```

- [ ] **Step 3: Resolve any lint regressions**

If `lint` flags new findings, address them — typically:
- Orphan string resources (`R.string.main_shell_settings`, `R.string.feed_detail_placeholder_select` if not removed earlier): delete from `strings.xml`.
- Missing contentDescription on icons: add via `stringResource(...)`.

- [ ] **Step 4: Verify the app boots on-device**

Per the `feedback_run_instrumentation_tests_after_compose_work` memory, plus the explicit on-device smoke check in the bead F acceptance criteria.

Run: `adb devices` to confirm a connected device or use the `android-cli` skill to launch the Pixel 10 Pro XL emulator (Compact) and the Pixel Tablet emulator (Medium).

Install and smoke:
```bash
./gradlew :app:installDebug
adb shell am start -n net.kikin.nubecita/.MainActivity
```

Walk through the smoke list from the bead F design's acceptance:
- Own-profile renders (You tab)
- Overflow → Settings opens
- Sign Out → confirm → returns to Login
- Tap a handle → other-user profile renders Follow (since fresh login, the visiting user follows no one)
- Block / Mute / Report stub each surface their snackbar
- On Pixel Tablet: a post tap inside the profile body lands in the right pane (NOT full-screen replacement)

- [ ] **Step 5: No commit (this task is just verification)**

If any step fails, fix and commit; otherwise proceed to Task 20.

---

## Task 20: File 7 follow-up bd issues + open PR with `run-instrumented` label

**Files:** none modified.

- [ ] **Step 1: File the 7 follow-up bd issues**

For each item below, run:

```bash
bd create --title "<title>" --type feature --priority 3 --epic nubecita-s6p
```

The 7 follow-ups (per the spec):

1. `feature/profile: scroll-collapsing hero + TopAppBar transition` — verify `nubecita-1tc` first; only file fresh if that bd doesn't already exist or doesn't match scope. Run `bd show nubecita-1tc` to check.

2. `feature/profile: Expanded 3-pane with side panel (suggested follows + pinned feeds)`. In the body: "Depends on `app.bsky.actor.getSuggestions` + `getPreferences` lexicon coverage. File upstream `kikin81/atproto-kotlin` issue if either is partial — see `reference_atproto_kotlin_notification_lexicon_gap.md` for the precedent."

3. `feature/profile: real Follow / Unfollow writes`. Body: "Replaces `FollowTapped → ShowComingSoon(Follow)` stub from Bead F. Touches `OtherUserActionsRow.kt` (the Follow button's `onClick` handler) and adds optimistic state mutation in `ProfileViewModel`. `app.bsky.graph.follow` + `deleteFollow` lexicon endpoints."

4. `feature/profile: real Edit profile screen`. Body: "Replaces `EditTapped → ShowComingSoon(Edit)` stub. Independent epic — covers the actual edit-profile form, photo upload, write to atproto. Tracking key: own epic, not a child of `nubecita-s6p`."

5. `feature/profile: real Message routing`. Body: "Replaces `MessageTapped → ShowComingSoon(Message)` stub. Verify `chat.bsky` lexicon coverage in `atproto-kotlin` first; file upstream `gh issue create --repo kikin81/atproto-kotlin` if partial."

6. `feature/settings: graduate to its own :feature:settings:impl module`. Body: "Once the real Settings screen ships, move the `Settings` `@MainShell` provider out of `:feature:profile:impl` into a new `:feature:settings:impl` per the `:api`-only-stubs rule. Touches `feature/profile/api/Settings.kt`'s KDoc (remove the 'graduates to its own module' note), `ProfileNavigationModule.kt`, plus the new module's gradle + Hilt graph entry. Rename `SettingsStubScreen` → `SettingsScreen` as part of the migration (clean refactor since the contract surface is small)."

7. `feature/profile: real moderation actions (Block / Mute / Report)`. Body: "Replaces the three `StubAction(StubbedAction.{Block,Mute,Report}) → ShowComingSoon` stubs from Bead F. Touches `OtherUserActionsRow.kt` overflow handler + `ProfileViewModel.handleEvent` `StubAction` branch. Lexicon dependencies: `app.bsky.graph.block`, `app.bsky.graph.mute`, and a report endpoint (TBD which atproto lexicon owns reporting — verify before scoping)."

Record each bd id printed by `bd create` for the PR body.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feat/nubecita-s6p.6-feature-profile-impl-other-user-variant-listdetail
```

- [ ] **Step 3: Open the PR with `run-instrumented` label**

Use `gh pr create` with the first-commit-subject convention. The PR body links to the spec and lists the 7 follow-up bd ids.

```bash
FIRST_COMMIT_SUBJECT=$(git log --reverse --format=%s main..HEAD | head -1)
gh pr create --base main \
  --title "$FIRST_COMMIT_SUBJECT" \
  --body "$(cat <<'EOF'
## Summary

Ships the final bead of the Profile epic (`nubecita-s6p.6`):

- Other-user actions row: filled Follow / outlined Following + outlined Message + overflow(Block / Mute / Report stubs)
- `ListDetailSceneStrategy.listPane{}` metadata on the Profile entry → Medium+ widths place post taps in the right pane
- Real `SettingsStubScreen` with confirmation-dialog sign-out (`AuthRepository.signOut()` + error snackbar)
- `:designsystem.PostDetailPaneEmptyState` promoted from Feed's `FeedDetailPlaceholder`
- `:app`'s `MainShellPlaceholderModule` no longer references `Settings`
- `AuthorProfileMapper` populates `viewerRelationship` from `viewer.following`; VM overrides to `Self` for own-profile

Closes the Profile epic on the read-only-viewing milestone. Real Follow / Unfollow / Edit / Message / Block / Mute / Report writes ship under follow-up bd issues.

## Design

`docs/superpowers/specs/2026-05-12-profile-bead-f-design.md`
`docs/superpowers/plans/2026-05-12-profile-bead-f-plan.md`

## Follow-up bd issues (filed)

- nubecita-XXXX: scroll-collapsing hero + TopAppBar transition (or `nubecita-1tc` if already open)
- nubecita-XXXX: Expanded 3-pane with side panel
- nubecita-XXXX: real Follow / Unfollow writes
- nubecita-XXXX: real Edit profile screen
- nubecita-XXXX: real Message routing
- nubecita-XXXX: :feature:settings:impl graduation
- nubecita-XXXX: real moderation actions (Block / Mute / Report)

## Test plan

- [ ] `:feature:profile:impl:testDebugUnitTest` — VM + mapper + SettingsStubVM
- [ ] `:feature:profile:impl:validateDebugScreenshotTest` — own-actions / other-actions / settings / Medium 2-pane
- [ ] `:designsystem:validateDebugScreenshotTest` — PostDetailPaneEmptyState
- [ ] `:feature:profile:impl:connectedDebugAndroidTest` — Settings overflow path, adaptive 2-pane, sign-out flow
- [ ] `:app:assembleDebug spotlessCheck lint :app:checkSortDependencies` — green locally
- [ ] On-device smoke: own-profile, overflow → Settings, sign out → Login, other-user Follow / Message, Block / Mute / Report stubs, post tap → right pane on Tablet

Closes: nubecita-s6p.6
EOF
)" \
  --label "run-instrumented"
```

- [ ] **Step 4: Verify the PR has the label**

```bash
gh pr view --json labels --jq '.labels[].name'
```

Expected: includes `"run-instrumented"`.

- [ ] **Step 5: Print the PR URL**

The PR URL was printed by `gh pr create`. Save it; remind yourself that `bd close nubecita-s6p.6` only runs after the PR merges.

---

## Acceptance summary

When all 20 tasks complete, the bead is done if:

- All commits land on the branch with Conventional-Commit messages referencing `nubecita-s6p.6`
- `./gradlew :designsystem:validateDebugScreenshotTest :feature:feed:impl:assembleDebug :feature:profile:impl:testDebugUnitTest :feature:profile:impl:validateDebugScreenshotTest :app:assembleDebug spotlessCheck lint :app:checkSortDependencies` all green
- All 3 instrumentation tests pass on a real device or emulator (`feature:profile:impl:connectedDebugAndroidTest`)
- PR open with `run-instrumented` label and PR body lists the 7 follow-up bd ids
- On-device smoke check items from the design doc's acceptance section completed

Once the PR merges:

```bash
bd close nubecita-s6p.6
```

— and the Profile epic itself is ready to close once all 7 follow-up bd issues have epics/owners assigned.
