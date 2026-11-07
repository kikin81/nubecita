# Followers and following lists Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping the follower or following count on a profile opens a paged list with a Follow button on each row.

**Architecture:** A new `@MainShell` route in `:feature:profile`, backed by a small repository over `getFollowers` / `getFollows` and Paging 3, with a per-DID follow overlay in the ViewModel.

**Tech Stack:** Kotlin, Compose M3 Expressive, Paging 3, Hilt, atproto-kotlin, JUnit 5, MockK, Turbine.

**Spec:** `docs/superpowers/specs/2026-11-06-followers-following-lists-design.md`

## Global Constraints

- No search, no Mutual tab, no offline cache, no deep link.
- Tabs use `ProfilePillTabs`. Each `PillTab` needs a `NubecitaIconName`.
- Unfollow is immediate, with no confirmation.
- New strings ship in `values`, `values-b+es+419` and `values-pt-rBR` in the same commit.
- `:feature:profile:impl` is flavored: `testProductionDebugUnitTest`, `lintProductionDebug`. Compile the bench variant too.
- Never commit local screenshot baselines; use the `update-baselines` label.
- Every `Scaffold` sets `containerColor = MaterialTheme.colorScheme.surface`.
- ViewModels never touch navigation state; navigate through `UiEffect`.

## Review Focus

1. A page reload after a follow, while the AppView still reports the old state: the row must keep the state the viewer chose.
2. The same account in both tabs: following in one must show in the other.
3. A rapid double tap on Follow, or tapping **Following** while the follow is still pending: one request, and no unfollow without a URI.
4. A failed follow or unfollow: the row returns to its prior state, and the error shows once.
5. Long labels: "1.2M Seguidores" in `ProfilePillTabs` at large font sizes, and a `missing.invalid` account in a list.

---

### Task 1: Route, repository and bench data

**Files:**
- Create: `feature/profile/api/src/main/kotlin/net/kikin/nubecita/feature/profile/api/ProfileConnections.kt`
- Create in `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/`: `ProfileConnectionsRepository.kt`, `DefaultProfileConnectionsRepository.kt`, `ConnectionMapper.kt`
- Create: `feature/profile/impl/src/bench/kotlin/net/kikin/nubecita/feature/profile/impl/data/BenchFakeProfileConnectionsRepository.kt`
- Modify: both flavors' `di/ProfileRepositoryModule.kt`
- Test: `feature/profile/impl/src/test/.../data/ConnectionMapperTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable enum class ProfileConnectionsTab { Followers, Following }`
  - `@Serializable data class ProfileConnections(val actor: String, val initialTab: ProfileConnectionsTab, val title: String, val followersCount: Long, val followsCount: Long) : NavKey`
  - `@Immutable data class ConnectionRowUi(val actor: ActorUi, val followUri: String?, val canFollow: Boolean)`
  - `data class ConnectionPage(val rows: ImmutableList<ConnectionRowUi>, val cursor: String?)`
  - `interface ProfileConnectionsRepository { suspend fun getFollowers(actor: String, cursor: String?): Result<ConnectionPage>; suspend fun getFollows(actor: String, cursor: String?): Result<ConnectionPage> }`
  - `internal fun ProfileView.toConnectionRowUi(viewerDid: String?): ConnectionRowUi`

- [ ] **Step 1: Write failing mapper tests**

`maps viewer following to followUri`; `no viewer state means not following`; `the viewer's own row cannot be followed` (`canFollow == false`); `blocking or blockedBy means canFollow is false`; `verification maps to the badge`.

- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest --tests '*ConnectionMapperTest'`
Expected: FAIL, unresolved `toConnectionRowUi`.

- [ ] **Step 3: Implement the route, mapper and `DefaultProfileConnectionsRepository`**

Page limit 50. Same `XrpcClientProvider` and `runCatchingCancellable` shape as `DefaultProfileRepository`. Viewer DID from `SessionStateProvider`. Follow `DefaultActorRepository.kt` for `ProfileView` mapping.

- [ ] **Step 4: Add the bench fake and bind both flavors**

Serves two pages per tab from fixed rows, including one row already followed, the viewer's own row, and one `canFollow = false` row.

- [ ] **Step 5: Verify**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest :feature:profile:impl:compileBenchDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit** as `feat(profile): add followers and following data layer`.

### Task 2: The connections screen

**Files:**
- Modify: `core/analytics/.../AnalyticsEvent.kt` (add `PostSurface.ProfileConnections("profile_connections")`)
- Modify: `designsystem/.../component/NubecitaActorRow.kt` (add `trailingContent: @Composable (() -> Unit)? = null`, default keeps existing rendering)
- Modify: `feature/profile/impl/build.gradle.kts` (`androidx.paging.compose`, `androidx.paging.runtime`, test `androidx.paging.testing`)
- Create in `feature/profile/impl/.../impl/`: `ProfileConnectionsContract.kt`, `ProfileConnectionsViewModel.kt`, `ProfileConnectionsScreen.kt`, `ProfileConnectionsPagingSource.kt`, `di/ProfileConnectionsNavigationModule.kt`
- Modify: `res/values*/strings.xml` in `:feature:profile:impl`
- Test: `ProfileConnectionsViewModelTest.kt`; `src/screenshotTest/.../ProfileConnectionsScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 1, `FollowRepository.follow(subjectDid): Result<String>`, `FollowRepository.unfollow(followUri): Result<Unit>`
- Produces:
  - `ProfileConnectionsState(val selectedTab: ProfileConnectionsTab, val title: String, val followersCount: Long, val followsCount: Long, val overlay: ImmutableMap<String, ConnectionFollowState>)`
  - `sealed interface ConnectionFollowState { Pending(following: Boolean); Committed(followUri: String?) }`
  - `sealed interface ProfileConnectionsEvent { TabSelected(tab); FollowTapped(row: ConnectionRowUi); RowTapped(row: ConnectionRowUi) }`
  - `sealed interface ProfileConnectionsEffect { NavigateToProfile(handle: String); ShowError }`
  - `val followers: Flow<PagingData<ConnectionRowUi>>`, `val following: Flow<PagingData<ConnectionRowUi>>`, both `cachedIn(viewModelScope)`

- [ ] **Step 1: Write failing ViewModel tests**

`opens on the route's tab`; `following a row updates it immediately and commits the uri`; `a failed follow restores the row and shows one error`; `unfollow uses the row's uri`; `a follow committed here can be unfollowed`; `a second tap while pending sends nothing`; `the overlay outranks a page reporting old state`; `one overlay entry covers both tabs`; `a row tap emits NavigateToProfile`.

- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest --tests '*ProfileConnectionsViewModelTest'`
Expected: FAIL, unresolved types.

- [ ] **Step 3: Implement the paging source and ViewModel**

Cursor keyed like `JoinRequestPagingSource`, `getRefreshKey` returns null. Overlay is keyed by DID and read at render time, not merged into `PagingData`, so one tap doesn't rebuild the pages. `@AssistedInject` with the route, as `ProfileViewModel` does. Log `InteractActor(ActorAction.Follow / Unfollow, PostSurface.ProfileConnections)` on each accepted tap.

- [ ] **Step 4: Run the tests**

Same command. Expected: PASS.

- [ ] **Step 5: Build the screen and nav entry**

`Scaffold` with a back arrow and title, `ProfilePillTabs` with `rememberCompactCount` labels using plurals, one `LazyColumn` per tab keyed by DID, `NubecitaActorRow` with a trailing Follow / Following button. Hide the button when `canFollow` is false. Load, empty and error states per tab, and an append retry row. A row for a `missing.invalid` account has no tap target and no button. Register under `@MainShell` with `ListDetailSceneStrategy.detailPane()` metadata, as a non-root profile is. Preview-driven stateless `ProfileConnectionsContent`.

- [ ] **Step 6: Screenshot tests**

Previews with visibly different data: followers list, following list, empty, error, long es-419 label. Compile only:
Run: `./gradlew :feature:profile:impl:compileProductionDebugScreenshotTestKotlin`

- [ ] **Step 7: Verify**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest :feature:profile:impl:lintProductionDebug :feature:profile:impl:compileBenchDebugKotlin :designsystem:lintDebug`
Expected: PASS.

- [ ] **Step 8: Commit** as `feat(profile): add the followers and following screen`.

### Task 3: Open it from the profile

**Files:**
- Modify: `.../impl/ui/ProfileStatsRow.kt`, `ui/ProfileHero.kt`, `ProfileContract.kt`, `ProfileViewModel.kt`, `ProfileScreen.kt`, `ProfileScreenContent.kt`, `di/ProfileNavigationModule.kt`, `res/values*/strings.xml`
- Test: `ProfileViewModelTest.kt`, `.../ui/ProfileStatsRowScreenshotTest.kt`

**Interfaces:**
- Consumes: `ProfileConnections`, `ProfileConnectionsTab` from Task 1
- Produces: `ProfileEvent.ConnectionsTapped(tab: ProfileConnectionsTab)`, `ProfileEffect.NavigateToConnections(route: ProfileConnections)`

- [ ] **Step 1: Write failing tests**

`tapping followers emits a route with the header's did, name and counts`; `tapping following opens that tab`; `the title falls back to the handle without a display name`; `nothing is emitted before the header loads`.

- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest --tests '*ProfileViewModelTest'`
Expected: FAIL.

- [ ] **Step 3: Implement**

`ProfileStatsRow` gains `onFollowersClick: () -> Unit` and `onFollowsClick: () -> Unit`. Followers and following become separate clickable text with `onClickLabel`, keeping the "·" layout and a usable touch size. Use plurals for all three counts, which also closes `nubecita-181n`. `ProfileHero` takes the callbacks with `= {}` defaults, placed after `modifier`.

- [ ] **Step 4: Verify**

Run: `./gradlew :feature:profile:impl:testProductionDebugUnitTest :feature:profile:impl:lintProductionDebug :feature:profile:impl:compileProductionDebugScreenshotTestKotlin :app:assembleProductionDebug :app:assembleBenchDebug`
Expected: PASS.

- [ ] **Step 5: Device pass on bench**

On the Samsung only (`R5GL75P24LL`): open a profile, tap each count, scroll to page two, switch tabs and back, follow and unfollow, confirm by screenshot. Launch `.MainActivity` explicitly, since `monkey` opens LeakCanary. Screenshot with the physical display id.

- [ ] **Step 6: Commit** as `feat(profile): open the lists from the follower counts`.
