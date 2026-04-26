## 1. Test stack overhaul (subsumes nubecita-e1a)

- [x] 1.1 Add `junit-jupiter`, `junit-jupiter-engine`, `turbine`, `mockk` aliases to `gradle/libs.versions.toml` (versions chosen against the latest stable as of branch date — pinned, not `latest.release`)
- [x] 1.2 Update `build-logic/src/main/kotlin/.../AndroidLibraryConventionPlugin.kt` to call `tasks.withType<Test>().configureEach { useJUnitPlatform() }` and add `junit-jupiter-api` + `junit-jupiter-engine` as default `testImplementation` deps for every consuming module
- [x] 1.3 Verify `AndroidFeatureConventionPlugin` and `AndroidLibraryComposeConventionPlugin` inherit the JUnit 5 wiring (or apply it explicitly if they don't extend the library plugin)
- [x] 1.4 Add `:core:testing` module (or equivalent shared-test-fixture surface) hosting `MainDispatcherExtension` (JUnit 5 `BeforeEachCallback` + `AfterEachCallback` replacing `MainDispatcherRule`) — depends only on `kotlinx-coroutines-test` + `junit-jupiter-api`. Add module include to `settings.gradle.kts`. Unit-test the extension itself (assert `Dispatchers.Main` swap + restore).
- [x] 1.5 Migrate `feature/login/impl/src/test/.../LoginViewModelTest.kt` from JUnit 4 (`@Rule MainDispatcherRule`) to JUnit 5 (`@ExtendWith(MainDispatcherExtension::class)`). All assertions stay; runner change only.
- [x] 1.6 Delete `feature/login/impl/src/test/.../MainDispatcherRule.kt` once `LoginViewModelTest` is on the new extension
- [x] 1.7 Migrate `data/models/src/test/.../PostUiTest.kt` to JUnit 5 (annotation swap; no assertion changes)
- [x] 1.8 Migrate `core/common/src/test/.../time/RelativeTimeTest.kt` (and any sibling test files in `:core:common`) to JUnit 5
- [x] 1.9 Run `./gradlew testDebugUnitTest` repo-wide — all tests green on JUnit 5
- [x] 1.10 Run `./gradlew :app:compileDebugScreenshotTestKotlin` and `./gradlew :designsystem:validateDebugScreenshotTest` — screenshot tests still compile and run (screenshotTest source set keeps JUnit 4 deps explicit if AGP requires it)
- [x] 1.11 Run `./gradlew spotlessCheck` — clean

## 2. MVI convention amendment

- [ ] 2.1 Update `CLAUDE.md` MVI section to add the sealed-status-sum-for-mutually-exclusive-modes paragraph (mirroring the spec delta language). Pure prose change.
- [ ] 2.2 Run `openspec validate add-feature-feed-foundation --strict` — passes (sanity check; spec delta already authored)

## 3. `:core:auth` — XrpcClientProvider

- [ ] 3.1 Add `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/XrpcClientProvider.kt` — public `interface XrpcClientProvider { suspend fun authenticated(): XrpcClient }` and public `class NoSessionException(message: String = "No authenticated session") : IllegalStateException(message)`
- [ ] 3.2 Add `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/DefaultXrpcClientProvider.kt` — `internal class` with `Mutex`-guarded cache keyed by session DID, suspend `authenticated()` calling `atOAuth.createClient()` on cache miss, `internal fun invalidate()` for session-change hooks
- [ ] 3.3 If `SessionStateProvider` lacks a `currentDid()` (or equivalent) accessor, add the smallest possible read-only helper returning `OAuthSession.did?.raw` (or null when no session). Update the binding module if needed.
- [ ] 3.4 Wire session-change cache invalidation: `DefaultXrpcClientProvider` collects `SessionStateProvider`'s state flow on a Singleton-scoped `CoroutineScope` (existing in `:core:auth` graph; if not, add the binding) and calls `invalidate()` whenever the DID transitions to a different value or to null
- [ ] 3.5 Add `@Binds` for `XrpcClientProvider → DefaultXrpcClientProvider` in the appropriate `:core:auth` module file (probably `AuthBindingsModule`); no new module file needed if existing fits
- [ ] 3.6 Add `core/auth/src/test/kotlin/.../DefaultXrpcClientProviderTest.kt` with JUnit 5 + Turbine + MockK or hand-rolled fakes, covering: cache hit, cache miss after DID change, NoSessionException when no session, concurrent callers do not double-create (use `runTest` + `launch` x N + assert `mockAtOAuth.createClient` invoked exactly once), `invalidate()` forces fresh creation on next call
- [ ] 3.7 Run `./gradlew :core:auth:testDebugUnitTest` — green

## 4. `:feature:feed:api` + `:feature:feed:impl` module scaffold

- [ ] 4.1 Create `feature/feed/api/build.gradle.kts` applying `nubecita.android.library` + `kotlin.serialization`, namespace `net.kikin.nubecita.feature.feed.api`, deps: `api(libs.androidx.navigation3.runtime)`, `api(libs.kotlinx.serialization.json)`. Mirror `feature/login/api/build.gradle.kts`.
- [ ] 4.2 Create `feature/feed/impl/build.gradle.kts` applying `nubecita.android.feature` (the meta plugin), namespace `net.kikin.nubecita.feature.feed.impl`, deps: `api(project(":feature:feed:api"))`, `implementation(project(":core:auth"))`, `implementation(project(":core:common"))`, `implementation(project(":data:models"))`, `implementation(project(":designsystem"))`, plus the atproto runtime + models libs needed for `FeedService`
- [ ] 4.3 Add both modules to `settings.gradle.kts` (after `:feature:login:impl`)
- [ ] 4.4 Create `feature/feed/api/src/main/kotlin/net/kikin/nubecita/feature/feed/api/Feed.kt` — `@Serializable data object Feed : NavKey` with KDoc mirroring the `Login.kt` style
- [ ] 4.5 Create `feature/feed/impl/src/main/AndroidManifest.xml` (empty manifest with package only)
- [ ] 4.6 Verify `./gradlew :feature:feed:api:assembleDebug` and `:feature:feed:impl:assembleDebug` succeed with no source files beyond the NavKey

## 5. FeedViewPostMapper (pure functions)

- [ ] 5.1 Add `feature/feed/impl/src/main/kotlin/.../data/FeedViewPostMapper.kt` — top-level `internal` functions: `internal fun FeedViewPost.toPostUiOrNull(): PostUi?`, `internal fun PostViewEmbedUnion?.toEmbedUi(): EmbedUi`, `internal fun ProfileViewBasic.toAuthorUi(): AuthorUi`, `internal fun ViewerState?.toViewerStateUi(): ViewerStateUi`
- [ ] 5.2 Add helper `internal fun JsonObject.extractText(): String` and `internal fun JsonObject.extractFacets(): ImmutableList<Facet>` for unpacking the `record: JsonObject` (decode with the project's `Json` instance)
- [ ] 5.3 Capture real `GetTimelineResponse` JSON fixtures from `https://public.api.bsky.app/xrpc/app.bsky.feed.getTimeline` (anonymous endpoint — no auth needed) and commit under `feature/feed/impl/src/test/resources/fixtures/`: at minimum `timeline_typical.json` (mixed posts), `timeline_with_images_embed.json`, `timeline_with_external_embed.json`, `timeline_with_video_embed.json`, `timeline_with_repost.json`, `timeline_malformed_record.json` (synthetic, hand-edited)
- [ ] 5.4 Add `feature/feed/impl/src/test/kotlin/.../data/FeedViewPostMapperTest.kt` with JUnit 5: happy-path mapping, reposted-by populates `repostedBy`, empty embed → `EmbedUi.Empty`, images embed (1/2/3/4 image fixtures) → `EmbedUi.Images` with correct count, external/record/video/recordWithMedia/Unknown → `EmbedUi.Unsupported(typeUri)`, missing optional counts default to 0, malformed `record` returns `null` (drops the post), facets extracted correctly (mention + link cases)
- [ ] 5.5 Run `./gradlew :feature:feed:impl:testDebugUnitTest` for the mapper tests — green

## 6. FeedRepository

- [ ] 6.1 Add `feature/feed/impl/src/main/kotlin/.../data/FeedRepository.kt` — `internal interface FeedRepository { suspend fun getTimeline(cursor: String?, limit: Int = TIMELINE_PAGE_LIMIT): Result<TimelinePage> }`, `internal data class TimelinePage(val posts: ImmutableList<PostUi>, val nextCursor: String?)`, `internal const val TIMELINE_PAGE_LIMIT = 30`
- [ ] 6.2 Add `feature/feed/impl/src/main/kotlin/.../data/DefaultFeedRepository.kt` — `internal class @Inject constructor(xrpcClientProvider, @IoDispatcher dispatcher)` implementing `getTimeline` via `withContext(io) { FeedService(xrpcClientProvider.authenticated()).getTimeline(GetTimelineRequest(cursor, limit.toLong())) }` + `runCatching` + mapper
- [ ] 6.3 Verify `:core:common` exposes a `@IoDispatcher`-qualified `CoroutineDispatcher` binding; if not, add it (small addition, scoped to `:core:common`'s DI module)
- [ ] 6.4 Add `feature/feed/impl/src/main/kotlin/.../di/FeedRepositoryModule.kt` — `@Module @InstallIn(SingletonComponent::class) internal interface FeedRepositoryModule { @Binds fun bindFeedRepository(impl: DefaultFeedRepository): FeedRepository }`
- [ ] 6.5 Add `feature/feed/impl/src/test/kotlin/.../data/DefaultFeedRepositoryTest.kt` with JUnit 5 + a fake `XrpcClientProvider` (returns a `XrpcClient` whose `httpClient` is a `MockEngine`-backed Ktor client serving the fixture JSON). Cover: success returns `TimelinePage` with mapped `PostUi`s, malformed posts dropped, network failure surfaces as `Result.failure`, `NoSessionException` propagates via `Result.failure`
- [ ] 6.6 Run `./gradlew :feature:feed:impl:testDebugUnitTest` — green

## 7. FeedContract + FeedViewModel

- [ ] 7.1 Add `feature/feed/impl/src/main/kotlin/.../FeedContract.kt` — `@Immutable data class FeedState(...)`, `sealed interface FeedLoadStatus`, `sealed interface FeedError`, `sealed interface FeedEvent : UiEvent`, `sealed interface FeedEffect : UiEffect` exactly per design.md decision matrix
- [ ] 7.2 Add `feature/feed/impl/src/main/kotlin/.../FeedViewModel.kt` — `@HiltViewModel class FeedViewModel @Inject constructor(feedRepository: FeedRepository) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState())`. `handleEvent` dispatches: `Load` → check idempotency → `loadStatus = InitialLoading` → repo call → set posts + cursor + Idle, OR `loadStatus = InitialError(error)` on failure. `Refresh` → `loadStatus = Refreshing`, `cursor = null` → repo call → replace posts + advance cursor on success, OR preserve posts + emit `ShowError` effect on failure. `LoadMore` → respect `endReached`, `loadStatus = Appending` → repo call with current cursor → append posts (de-dupe by id) + advance cursor on success, OR preserve cursor + emit `ShowError` effect on failure. `Retry` → re-run initial-load. `ClearError` → no-op for now (effects model). `OnPostTapped` → emit `NavigateToPost`. `OnAuthorTapped` → emit `NavigateToAuthor`. `OnLikeClicked`, `OnRepostClicked`, `OnReplyClicked`, `OnShareClicked` → no-op (stub).
- [ ] 7.3 Map repository `Throwable` → `FeedError`: `IOException` → `FeedError.Network`, `NoSessionException` → `FeedError.Unauthenticated`, anything else → `FeedError.Unknown(cause = throwable.message)`
- [ ] 7.4 Add `feature/feed/impl/src/test/kotlin/.../FeedViewModelTest.kt` with JUnit 5 + Turbine + MockK (or hand-rolled fakes if simpler) covering ALL of: initial load success, initial load empty page (`endReached = true`), initial load failure → `InitialError(Network)`, initial-load failure then Retry success, refresh success replaces posts, refresh failure preserves posts + emits `ShowError`, LoadMore success appends + advances cursor + de-dupes by id, LoadMore failure preserves cursor + emits `ShowError`, LoadMore at end (`endReached`) is no-op, Load idempotency (Load while InitialLoading → no second repo call), Unauthenticated → `InitialError(Unauthenticated)`, all four interaction-stub events (`OnLikeClicked`, etc.) are no-ops, OnPostTapped emits `NavigateToPost`, OnAuthorTapped emits `NavigateToAuthor`
- [ ] 7.5 Run `./gradlew :feature:feed:impl:testDebugUnitTest` — green

## 8. Hilt navigation wiring + placeholder screen

- [ ] 8.1 Add `feature/feed/impl/src/main/kotlin/.../FeedScreen.kt` — `@Composable internal fun FeedScreen(viewModel: FeedViewModel = hiltViewModel())` rendering a minimal `Column { Text("FeedState: ${state.toString()}") }` so the Hilt graph compiles end-to-end. KDoc explicitly: "Placeholder; nubecita-1d5 replaces this with the real LazyColumn + PullToRefreshBox screen + the preview/screenshot-test trio. Do NOT add @Preview annotations here."
- [ ] 8.2 Add `feature/feed/impl/src/main/kotlin/.../di/FeedNavigationModule.kt` — `@Module @InstallIn(SingletonComponent::class) internal object FeedNavigationModule { @Provides @IntoSet fun provideFeedEntries(): EntryProviderInstaller = { entry<Feed> { FeedScreen() } } }`. Mirror `LoginNavigationModule`.
- [ ] 8.3 Wire the new `:feature:feed:api` + `:feature:feed:impl` deps into `:app/build.gradle.kts` (`implementation(project(":feature:feed:api"))`, `implementation(project(":feature:feed:impl"))`)
- [ ] 8.4 Verify the Nav3 graph in `:app` resolves the `Feed` entry without runtime errors. The placeholder is reachable from the existing nav graph if convenient (e.g., as a debug menu item) but NOT required — the `EntryProviderInstaller` multibinding is enough to prove the wiring.
- [ ] 8.5 Run `./gradlew :app:assembleDebug` — full app builds with the new modules

## 9. Final verification

- [ ] 9.1 `./gradlew testDebugUnitTest` repo-wide — all tests green
- [ ] 9.2 `./gradlew :app:lintDebug` — no new lint errors
- [ ] 9.3 `./gradlew spotlessCheck` — clean
- [ ] 9.4 `./gradlew :app:compileDebugScreenshotTestKotlin` — screenshot tests still compile (no new screenshot tests added in this change; just verify nothing regressed)
- [ ] 9.5 `openspec validate add-feature-feed-foundation --strict` — passes
- [ ] 9.6 Run `pre-commit run --all-files` — all hooks green
- [ ] 9.7 Manual smoke (optional): launch the app, navigate to `Feed` (if wired into a debug menu), confirm the placeholder renders without crashing

## 10. Documentation + bd

- [ ] 10.1 Update `bd update nubecita-4u5 --notes` with a pointer to this openspec change
- [ ] 10.2 Update `bd update nubecita-e1a --notes` noting it's subsumed by this change (closed alongside `nubecita-4u5` on PR merge)
- [ ] 10.3 PR body MUST include both `Closes: nubecita-4u5` AND `Closes: nubecita-e1a`
- [ ] 10.4 After merge: `bd close nubecita-4u5 nubecita-e1a` with reasons referencing the PR + this openspec change name
