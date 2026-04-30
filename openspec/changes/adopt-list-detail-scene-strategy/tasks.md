## 1. Dependency wiring

- [x] 1.1 Pick the latest stable-alpha `androidx.compose.material3.adaptive:adaptive-navigation3` whose metadata-key surface is aligned with `nav3Core = "1.1.1"` (cross-check Google AndroidX release notes; if no aligned version exists, bump `nav3Core` in a separate task and surface that in the PR). Pin explicitly — no `latest.release`.
- [x] 1.2 Add `androidx-compose-material3-adaptive-navigation3` library alias to `gradle/libs.versions.toml` referencing the picked version.
- [x] 1.3 Add `implementation(libs.androidx.compose.material3.adaptive.navigation3)` to `app/build.gradle.kts`. Do NOT add to any convention plugin — this is a single-consumer dep until Phase 2.
- [x] 1.4 Run `./gradlew :app:dependencies | grep adaptive-navigation3` — confirm the artifact resolves at the pinned version with no surprise transitives.

## 2. ListDetailSceneStrategy in MainShell

- [x] 2.1 In `app/src/main/java/net/kikin/nubecita/shell/MainShell.kt`, import `androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy` and `ListDetailSceneStrategy`. Construct `val sceneStrategy = rememberListDetailSceneStrategy<NavKey>()` inside `MainShell`'s body.
- [x] 2.2 Pass `sceneStrategies = listOf(sceneStrategy)` to the inner `NavDisplay` invocation. Other parameters (`backStack`, `onBack`, `entryDecorators`, `entryProvider`) unchanged.
- [x] 2.3 Update `MainShell`'s KDoc — add a paragraph after the existing "Per-tab back-stack state lives in `MainShellNavState`…" block describing the scene strategy's role and pointing readers at the spec delta.
- [x] 2.4 Run `./gradlew :app:assembleDebug` — confirm the build succeeds with no new lint warnings from the alpha artifact.

## 3. Feed entry list-pane metadata + placeholder

- [x] 3.1 Add string resource `feed_detail_placeholder_select` to `feature/feed/impl/src/main/res/values/strings.xml` with the value `Select a post to read`.
- [x] 3.2 Create `feature/feed/impl/src/main/.../ui/FeedDetailPlaceholder.kt` — a `@Composable` `internal fun FeedDetailPlaceholder(modifier: Modifier = Modifier)` rendering a centered column with a decorative `Icons.AutoMirrored.Outlined.Article` icon and a `bodyLarge` `Text(stringResource(R.string.feed_detail_placeholder_select))`. Background `MaterialTheme.colorScheme.surfaceContainerLow`, fills size.
- [x] 3.3 Add a Compose `@Preview` for `FeedDetailPlaceholder` in the same file (or a sibling `*Previews.kt`).
- [x] 3.4 In `feature/feed/impl`'s `EntryProviderInstaller` for the Feed `NavKey` (the `@Provides @IntoSet @MainShell` function), wrap the existing `entry<Feed> { … }` builder with `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { FeedDetailPlaceholder() })`. Import `androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy`.
- [x] 3.5 Run `./gradlew :feature:feed:impl:assembleDebug` and `./gradlew :app:assembleDebug` — green.

## 4. Tests

- [x] 4.1 Add `feature/feed/impl/src/screenshotTest/.../ui/FeedDetailPlaceholderScreenshotTest.kt` — single composable annotated with `@PreviewTest` (from `com.android.tools.screenshot.PreviewTest`, required for the screenshot runner to pick it up) paired with `@Preview(name = "feed-detail-placeholder-light", showBackground = true)` (and a sibling `…-dark` preview with `uiMode = UI_MODE_NIGHT_YES`). Renders `FeedDetailPlaceholder()`. The visual baseline is the assertion that the icon + localized prompt render correctly. Mirrors the structure of `feature/feed/impl/src/screenshotTest/.../ui/PostCardQuotedPostWithVideoScreenshotTest.kt`.
- [x] 4.2 Add `app/src/screenshotTest/java/net/kikin/nubecita/shell/MainShellListDetailScreenshotTest.kt` alongside `MainShellChromeScreenshotTest`. Use the same file-private `COMPACT_WIDTH_DP = 360`, `MEDIUM_WIDTH_DP = 600`, `EXPANDED_WIDTH_DP = 840` constants (re-declared file-private to match the existing pattern). Three composables, each annotated with both `@PreviewTest` and `@Preview(name = ..., widthDp = ..., heightDp = 800)` at the corresponding width — each rendering `MainShellChrome` with a fake `@MainShell` installer that registers Feed with real `listPane{}` metadata + the placeholder. The compact baseline asserts no placeholder is composed; the medium and expanded baselines assert the placeholder is composed adjacent to the Feed list pane. These baselines also serve as the regression guard against accidental "drops `sceneStrategies`" refactors — no separate JVM unit test is added (Compose rendered-tree assertions require Robolectric, which the repo doesn't ship).
- [x] 4.3 Add a scenario to `app/src/androidTest/java/net/kikin/nubecita/shell/MainShellPersistenceTest.kt`: place `[Feed]` on the back stack at medium width, rotate to compact, rotate back to medium — assert the `FeedDetailPlaceholder` is visible in the right pane after the round-trip.
- [x] 4.4 Run `./gradlew :app:validateDebugScreenshotTest :feature:feed:impl:validateDebugScreenshotTest` — diffs reviewed and committed.
- [x] 4.5 Run `./gradlew testDebugUnitTest` repo-wide — green.

## 5. Validation

- [x] 5.1 Run `./gradlew spotlessCheck lint` — clean.
- [x] 5.2 Run `openspec validate adopt-list-detail-scene-strategy --strict` — passes.
- [ ] 5.3 Manual smoke on a foldable / tablet emulator (Pixel Tablet AVD): launch app, navigate to Feed, confirm placeholder visible in right pane, tap a tab to ensure Search/Chats/Profile do not engage the strategy (full-screen on all widths). Capture a screenshot for the PR description.
- [ ] 5.4 Confirm on a Pixel 6 (compact) emulator: launch app, navigate to Feed, confirm no placeholder is composed and behavior is identical to pre-change (no regression in the bar-mode chrome).
