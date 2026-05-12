<!--
This change is split into six child beads under the Profile epic (file the
bd epic + child issues before starting Bead A — each "##" group below maps
1:1 to a single bd issue and a single PR).

Beads A and B (design-system additions) are independent of each other and
of the feature module. Bead C is the foundation for D/E/F. Beads D, E, F
are sequential.

Each numbered task here is one step inside its bead (≤ ~30 min); each bead
ships as one PR. Tests called out per task are the minimum bar; reviewers
may request more.
-->

## 1. Bead A — designsystem: Fraunces + JetBrains Mono variable fonts

- [ ] 1.1 Add `androidx.palette:palette-ktx` to `gradle/libs.versions.toml` (used in Bead B but lands here with the other design-system deps so Bead B doesn't need a separate version-catalog PR)
- [ ] 1.2 Bundle the Fraunces variable font into `:designsystem/src/main/res/font/fraunces.ttf` (download from the Google Fonts Variable distribution; do not write a Gradle download task — file is committed as a binary asset)
- [ ] 1.3 Bundle the JetBrains Mono variable font into `:designsystem/src/main/res/font/jetbrains_mono.ttf` (same approach)
- [ ] 1.4 In `:designsystem`'s typography file, expose a `displayNameStyle` `TextStyle` that uses Fraunces with `FontVariation.Settings(FontVariation.Setting("SOFT", 70f), FontVariation.weight(600))`, plus a `handleStyle` `TextStyle` using JetBrains Mono at 13 sp
- [ ] 1.5 Update existing `TypographyPreviews.kt` (or equivalent fixture) to render both new styles in light and dark; regenerate baselines via `:designsystem:updateDebugScreenshotTest`. NOTE: Layoutlib may stub `SOFT` axis per `feedback_compose_glyph_iteration_workflow.md` — accept whatever Layoutlib renders and real-device verify
- [ ] 1.6 Unit test: `TypographyTest` asserts that the resolved `FontFamily` for `displayNameStyle` contains a `Font` with `FontVariation.Setting("SOFT", 70f)` and that `handleStyle.fontSize == 13.sp`

## 2. Bead B — designsystem: `BoldHeroGradient` + `ProfilePillTabs`

- [ ] 2.1 Implement `BoldHeroGradient(banner: Url?, avatarHue: Int, modifier: Modifier, content: @Composable () -> Unit)` in `:designsystem/src/main/kotlin/.../hero/BoldHeroGradient.kt`. Decode the banner via Coil's `ImageLoader.execute()` on `Dispatchers.Default`; pass the bitmap to `Palette.from(...).generate()`; cache the result in a private LRU keyed on the banner URL or blob `cid`; expose state as a `remember`'d gradient that swaps from the avatarHue fallback to the palette-derived gradient when extraction completes
- [ ] 2.2 In `BoldHeroGradient`, implement the WCAG AA contrast guard — if the dominant palette swatch's luminance exceeds the AA threshold for white text, darken the swatch until it clears
- [ ] 2.3 Implement `ProfilePillTabs(tabs: List<PillTab>, selectedTab: PillTab, onTabSelect: (PillTab) -> Unit, modifier: Modifier = Modifier)` in `:designsystem/src/main/kotlin/.../tabs/ProfilePillTabs.kt` as a thin wrapper over `androidx.compose.material3.PrimaryTabRow`; render each tab with the M3 `Tab` primitive; use `NubecitaIcon(name = ..., filled = isSelected)` for the icon slot so the FILL axis toggles 0→1 on active
- [ ] 2.4 Add a `PillTab` data class to `:designsystem` (label, icon name, value identifier — does NOT depend on `:feature:profile:*` types)
- [ ] 2.5 Previews + screenshot tests for `BoldHeroGradient` covering: with banner / without banner / very-light banner (contrast guard fires) / very-dark banner. Light and dark themes. Reference baselines committed.
- [ ] 2.6 Previews + screenshot tests for `ProfilePillTabs` covering: 3 tabs with each tab active, light + dark. Reference baselines committed.
- [ ] 2.7 Unit test: `BoldHeroGradientCacheTest` asserts that a second composition with the same `banner` URL retrieves the cached palette without re-invoking the decode pipeline (verify via a fake `ImageLoader` that counts `execute` calls)
- [ ] 2.8 Unit test: `BoldHeroGradientContrastTest` asserts that a synthetic palette whose dominant swatch is `Color.White` is darkened to meet WCAG AA before being returned

## 3. Bead C — feature/profile/impl: data layer + ViewModel scaffolding

- [ ] 3.1 Create the `:feature:profile:impl` module: `feature/profile/impl/build.gradle.kts` applying `alias(libs.plugins.nubecita.android.feature)`, `namespace = "net.kikin.nubecita.feature.profile.impl"`; add to `settings.gradle.kts`
- [ ] 3.2 Wire dependencies in the module: `api(project(":feature:profile:api"))`, `implementation(project(":core:auth"))`, `implementation(project(":core:common"))`, `implementation(project(":core:feed-mapping"))`, `implementation(project(":data:models"))`, `implementation(project(":designsystem"))`, `implementation(libs.atproto.models)`, `implementation(libs.atproto.runtime)`, `implementation(libs.kotlinx.collections.immutable)`, `implementation(libs.timber); testImplementation(project(":core:testing"))` + `mockk` + `turbine` + `kotlinx.coroutines.test`
- [ ] 3.3 Define `ProfileContract.kt`: `ProfileScreenViewState`, `ProfileEvent`, `ProfileEffect`, `TabLoadStatus` sealed sum, `ProfileTab` enum, `ProfileHeaderUi`, `ViewerRelationship` enum, `ProfileError` sealed sum, `TabItemUi` (covers `PostCard`-row + media-grid-cell shapes)
- [ ] 3.4 Implement `AuthorProfileMapper` (inline in `:impl/data/`): `app.bsky.actor.defs#profileViewDetailed` → `ProfileHeaderUi`. No extraction to `:core:*` (single consumer; YAGNI)
- [ ] 3.5 Implement `AuthorFeedMapper` in `:impl/data/`: delegates embed + post-core mapping to `:core:feed-mapping` (same shape as `:feature:postdetail:impl/data/PostThreadMapper`); produces `TabItemUi` rows
- [ ] 3.6 Implement `ProfileRepository` in `:impl/data/`: `fun fetchHeader(handle: String?): Flow<Result<ProfileHeaderUi>>`, `fun fetchTab(handle: String?, tab: ProfileTab, cursor: String?): Flow<Result<TabPage>>`. Calls `app.bsky.actor.getProfile` and `app.bsky.feed.getAuthorFeed` with the per-tab filter mapping; resolves `handle = null` to the authenticated user's DID via `:core:auth`
- [ ] 3.7 Implement `ProfileViewModel` extending `MviViewModel<ProfileScreenViewState, ProfileEvent, ProfileEffect>`: `init` block launches four concurrent loads (header + 3 tabs); event handlers for `TabSelected`, `PostTapped`, `HandleTapped` (with self-tap no-op guard), `Refresh`, `LoadMore`, `FollowTapped`, `EditTapped`, `MessageTapped` (last three emit `ShowMessage("Coming soon")`)
- [ ] 3.8 Hilt module `ProfileNavigationModule.kt` with placeholder `@Provides @IntoSet @MainShell` for the screen Composable that prints a `Timber.d("ProfileScreen TODO")` and renders an empty Box. This unblocks DI graph + module wiring without shipping UI — replaced in Bead D
- [ ] 3.9 Unit tests via Turbine: `ProfileViewModelTest` covering (a) initial open emits four concurrent loads, (b) per-tab independent failure, (c) self-handle tap is silent no-op, (d) `FollowTapped` emits `ShowMessage` and never touches the repository, (e) `TabSelected` does not re-fetch when the target tab is already `Loaded`, (f) `LoadMore` issues a `getAuthorFeed` call with the correct per-tab cursor
- [ ] 3.10 Unit test: `AuthorProfileMapperTest` asserts the atproto-wire-type → `ProfileHeaderUi` mapping including the banner CID / URL handling and the avatarHue derivation
- [ ] 3.11 Unit test: `AuthorFeedMapperTest` asserts that the per-tab filter dispatches to the right `getAuthorFeed` filter value and that the embed-mapping output is identical to `:core:feed-mapping`'s output (regression guard)

## 4. Bead D — feature/profile/impl: own-profile screen, Posts tab only

- [ ] 4.1 Implement `ProfileScreen.kt` (stateful) and `ProfileScreenContent.kt` (stateless) split, following the `FeedScreen` / `FeedScreenContent` pattern; the stateful wrapper consumes `hiltViewModel<ProfileViewModel>()` and collects effects via a single `LaunchedEffect`
- [ ] 4.2 Implement the hero card: `BoldHeroGradient` backdrop, avatar (Coil-loaded) on a 4dp surface ring at elevation 2, name with the `displayNameStyle`, handle with `handleStyle`, bio with `textWrap = Pretty`
- [ ] 4.3 Implement the inline stats row (`<n> Posts · <n> Followers · <n> Following`) with locale-aware short-scale abbreviation; meta row with link / location / joined date rendered as `NubecitaIcon` + label rows, hiding rows whose data is null
- [ ] 4.4 Implement the actions row for `ownProfile = true`: Edit button + overflow only. Tap dispatches `ProfileEvent.EditTapped` → ViewModel emits `ShowMessage` → snackbar
- [ ] 4.5 Implement the `ProfilePillTabs` rendering — sticky inside the body's `LazyColumn` via `stickyHeader { ProfilePillTabs(...) }`; only the Posts tab is selectable / rendered in this bead (Replies and Media render a `"Coming next" placeholder Composable` body that is replaced in Bead E)
- [ ] 4.6 Implement the Posts tab body: `LazyColumn` of `:designsystem.PostCard`s consuming `postsStatus.items`; PostCard `onPostClick` dispatches `ProfileEvent.PostTapped`; PostCard `onAuthorClick` dispatches `ProfileEvent.HandleTapped`; `LoadMore` triggered by `LazyListState` end-prefetch
- [ ] 4.7 Empty-state, loading-state, and initial-error-state Composables for Posts: per-tab copy distinct from feed; rendered when `postsStatus` is the corresponding variant
- [ ] 4.8 Hilt: update `ProfileNavigationModule.kt` — replace the placeholder provider from Bead C with the real screen Composable wired to nav callbacks via `LocalMainShellNavState.current` in the entry block (matches `:feature:postdetail:impl`'s `PostDetailNavigationModule` pattern)
- [ ] 4.9 `:app`: remove the `Profile(handle = null)` placeholder from `MainShellPlaceholderModule` (the placeholder Composable + its `@MainShell @IntoSet` `@Provides` function). Do NOT yet touch the `Settings` placeholder (lands in Bead F)
- [ ] 4.10 Screenshot tests: own-profile-hero-with-banner, own-profile-hero-without-banner, posts-tab-loaded, posts-tab-initial-loading, posts-tab-initial-error, posts-tab-empty, actions-row-own. Light + dark for each. Compact width only in this bead.
- [ ] 4.11 Instrumentation test (`src/androidTest/`) at minimum: `ProfileScreenInstrumentationTest` verifies the actions-row Edit-tap surfaces the Coming Soon snackbar on a real `ComponentActivity` (use the established `createAndroidComposeRule<ComponentActivity>()` pattern from PR #150's `clearAndSetSemantics` test). Add the `run-instrumented` label to the PR per the `feedback_run_instrumented_label_on_androidtest_prs` memory.

## 5. Bead E — feature/profile/impl: Replies tab + Media tab + 3-col media grid

- [ ] 5.1 Implement the Replies tab body: identical structure to Posts tab body (LazyColumn of `PostCard`s) but consuming `repliesStatus.items` and per-tab empty / loading / error states
- [ ] 5.2 Implement `ProfileMediaGrid.kt` in `:feature:profile:impl/ui/`: `LazyVerticalGrid(GridCells.Fixed(3))` rendering `TabItemUi.MediaCell` items with Coil-loaded thumbs; cell tap dispatches `ProfileEvent.PostTapped(postUri)`
- [ ] 5.3 Update `ProfileScreenContent` so all three pill tabs are selectable; tab body dispatch routes Posts → existing PostsTabBody, Replies → new RepliesTabBody, Media → new ProfileMediaGrid
- [ ] 5.4 Update `AuthorFeedMapper` (if not already covering all three filters in Bead C) to produce `TabItemUi.MediaCell` for `posts_with_media` results — first image of each post becomes the cell content; videos use their thumbnail; posts without media are filtered out (defensive — Bluesky's filter should already do this server-side)
- [ ] 5.5 Screenshot tests: replies-tab-loaded, replies-tab-empty, replies-tab-initial-error, media-tab-loaded (3×N grid), media-tab-empty, media-tab-initial-error. Light + dark.
- [ ] 5.6 Unit test: `ProfileViewModelTest` adds scenarios covering (a) Replies-tab `LoadMore` doesn't touch Posts or Media cursors, (b) Media-tab `PostTapped` emits the same `NavigateToPost` effect shape as Posts-tab `PostTapped`

## 6. Bead F — feature/profile/impl: other-user variant + ListDetailSceneStrategy integration + Settings stub

- [ ] 6.1 In `ProfileScreenContent`, implement the actions-row branch for `ownProfile = false`: Follow + Message + overflow. `FollowTapped` and `MessageTapped` event handlers both already emit `ShowMessage("Coming soon")` (from Bead C); just route the buttons
- [ ] 6.2 Add `ListDetailSceneStrategy.listPane { detailPlaceholder { … } }` metadata to the `Profile` `@MainShell` entry provider in `ProfileNavigationModule.kt`. The detail-placeholder reuses the global "Pick a post to read its thread" Composable from `:core:common:navigation` (or wherever the existing list-detail placeholder lives — per the `app-navigation-shell` spec)
- [ ] 6.3 Wire post-tap routing: the screen's effect collector calls `LocalMainShellNavState.current.add(PostDetailRoute(postUri))` on `ProfileEffect.NavigateToPost`; cross-handle routing calls `LocalMainShellNavState.current.add(Profile(handle = ...))` on `ProfileEffect.NavigateToProfile` (with the self-tap no-op already enforced in the ViewModel from Bead C)
- [ ] 6.4 Implement the Settings stub: a one-screen Composable in `:feature:profile:impl/ui/SettingsStubScreen.kt` rendering "Settings — coming soon" identifying text + a Sign Out button. Sign Out invokes the existing logout pathway in `:core:auth` (clears the persisted `OAuthSession`); the outer `Navigator`'s `replaceTo(Login)` is triggered downstream (same pathway used by existing logout in the splash/login flow)
- [ ] 6.5 Hilt: add a `@Provides @IntoSet @MainShell` provider for the `Settings` `NavKey` resolving to `SettingsStubScreen` in `ProfileNavigationModule.kt`
- [ ] 6.6 `:app`: remove the `Settings` placeholder from `MainShellPlaceholderModule`. After this bead, `:app`'s `MainShellPlaceholderModule` should contain ONLY the Search and Chats placeholders (those `:impl` modules still don't exist)
- [ ] 6.7 Add an overflow-menu entry on the own-profile actions row that pushes `Settings` onto `mainShellNavState` (gives the Settings sub-route a real entry point; the Settings NavKey is otherwise unreachable in this epic)
- [ ] 6.8 Screenshot tests: other-user-hero-with-banner, other-user-hero-without-banner, actions-row-other (Follow + Message + overflow), settings-stub-screen. Light + dark. Add a Medium-width fixture for the profile screen demonstrating list-pane placement (the right pane shows the global detail placeholder).
- [ ] 6.9 Instrumentation test: `ProfileScreenAdaptiveInstrumentationTest` verifies that on a Medium-width device a post tap inside the profile body lands in the right pane (not full-screen) by asserting that the profile-screen test tag remains in the composition after the tap. Add the `run-instrumented` label to the PR.
- [ ] 6.10 Instrumentation test: `SettingsStubInstrumentationTest` verifies that tapping Sign Out invokes the auth logout pathway (use a fake `AuthRepository` injected via a Hilt test module; assert `logout()` is called)
- [ ] 6.11 Verify on a real device (per the `feedback_run_instrumentation_tests_after_compose_work` memory) that the post-tap-into-right-pane behavior renders correctly on the Pixel 10 Pro XL; Layoutlib's behavior here is non-load-bearing — real device is the source of truth

## 7. Follow-up bd issues (file at the end of Bead F)

- [ ] 7.1 File bd issue: `feature/profile: scroll-collapsing hero + TopAppBar transition` (the deferred polish from the prompt)
- [ ] 7.2 File bd issue: `feature/profile: Expanded 3-pane with side panel (suggested follows + pinned feeds)` — depends on data sources we don't have yet (`app.bsky.actor.getSuggestions`, `app.bsky.actor.getPreferences`); flag the data dependency
- [ ] 7.3 File bd issue: `feature/profile: real Follow / Unfollow writes` (`app.bsky.graph.follow` + `deleteFollow` + optimistic UI). This is what replaces the `FollowTapped → ShowMessage` stub
- [ ] 7.4 File bd issue: `feature/profile: real Edit profile screen` — independent epic
- [ ] 7.5 File bd issue: `feature/profile: real Message routing` — verify `chat.bsky` lexicon coverage in the atproto-kotlin SDK first (precedent in `reference_atproto_kotlin_notification_lexicon_gap.md`); file an upstream `kikin81/atproto-kotlin` issue if the lexicon is partial
- [ ] 7.6 File bd issue: `feature/settings: graduate to its own :feature:settings:impl module` — when the real Settings screen is ready, move the `Settings` provider out of `:feature:profile:impl`
