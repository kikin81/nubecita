## 1. Dependency wiring

- [ ] 1.1 Pick the latest stable-alpha `androidx.compose.material3.adaptive:adaptive-navigation3` whose metadata-key surface is aligned with `nav3Core = "1.1.1"` (cross-check Google AndroidX release notes; if no aligned version exists, bump `nav3Core` in a separate task and surface that in the PR). Pin explicitly — no `latest.release`.
- [ ] 1.2 Add `androidx-compose-material3-adaptive-navigation3` library alias to `gradle/libs.versions.toml` referencing the picked version.
- [ ] 1.3 Add `implementation(libs.androidx.compose.material3.adaptive.navigation3)` to `app/build.gradle.kts`. Do NOT add to any convention plugin — this is a single-consumer dep until Phase 2.
- [ ] 1.4 Run `./gradlew :app:dependencies | grep adaptive-navigation3` — confirm the artifact resolves at the pinned version with no surprise transitives.

## 2. ListDetailSceneStrategy in MainShell

- [ ] 2.1 In `app/src/main/java/net/kikin/nubecita/shell/MainShell.kt`, import `androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy` and `ListDetailSceneStrategy`. Construct `val sceneStrategy = rememberListDetailSceneStrategy<NavKey>()` inside `MainShell`'s body.
- [ ] 2.2 Pass `sceneStrategies = listOf(sceneStrategy)` to the inner `NavDisplay` invocation. Other parameters (`backStack`, `onBack`, `entryDecorators`, `entryProvider`) unchanged.
- [ ] 2.3 Update `MainShell`'s KDoc — add a paragraph after the existing "Per-tab back-stack state lives in `MainShellNavState`…" block describing the scene strategy's role and pointing readers at the spec delta.
- [ ] 2.4 Run `./gradlew :app:assembleDebug` — confirm the build succeeds with no new lint warnings from the alpha artifact.

## 3. Feed entry list-pane metadata + placeholder

- [ ] 3.1 Add string resource `feed_detail_placeholder_select` to `feature/feed/impl/src/main/res/values/strings.xml` with the value `Select a post to read`.
- [ ] 3.2 Create `feature/feed/impl/src/main/.../ui/FeedDetailPlaceholder.kt` — a `@Composable` `internal fun FeedDetailPlaceholder(modifier: Modifier = Modifier)` rendering a centered column with a decorative `Icons.AutoMirrored.Outlined.Article` icon and a `bodyLarge` `Text(stringResource(R.string.feed_detail_placeholder_select))`. Background `MaterialTheme.colorScheme.surfaceContainerLow`, fills size.
- [ ] 3.3 Add a Compose `@Preview` for `FeedDetailPlaceholder` in the same file (or a sibling `*Previews.kt`).
- [ ] 3.4 In `feature/feed/impl`'s `EntryProviderInstaller` for the Feed `NavKey` (the `@Provides @IntoSet @MainShell` function), wrap the existing `entry<Feed> { … }` builder with `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { FeedDetailPlaceholder() })`. Import `androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy`.
- [ ] 3.5 Run `./gradlew :feature:feed:impl:assembleDebug` and `./gradlew :app:assembleDebug` — green.

## 4. Tests

- [ ] 4.1 Add `feature/feed/impl/src/test/.../ui/FeedDetailPlaceholderTest.kt` — JUnit 5 + ComposeTestRule, single test asserting the placeholder string from `strings.xml` appears (`hasText(...)`).
- [ ] 4.2 Add `app/src/test/java/net/kikin/nubecita/shell/MainShellSceneStrategyTest.kt` — JUnit 5 unit test that constructs `MainShellChrome` with a fake list-pane installer and asserts (via the chrome's exposed test seam, or via inspection of the rendered semantics tree) that the inner `NavDisplay` is configured with a non-empty `sceneStrategies`. If no clean seam exists, instead add the assertion as part of 4.3's screenshot test setup verification step.
- [ ] 4.3 Add `app/src/screenshotTest/java/net/kikin/nubecita/shell/MainShellListDetailScreenshotTest.kt` with three `@Preview` functions × three explicit window-size devices (`spec:width=360dp,height=800dp`, `spec:width=700dp,height=1024dp`, `spec:width=1200dp,height=800dp`) — each rendering `MainShellChrome` with a fake `@MainShell` installer that registers Feed with real `listPane{}` metadata + the placeholder.
- [ ] 4.4 Add a scenario to `app/src/androidTest/java/net/kikin/nubecita/shell/MainShellPersistenceTest.kt`: place `[Feed]` on the back stack at medium width, rotate to compact, rotate back to medium — assert the `FeedDetailPlaceholder` is visible in the right pane after the round-trip.
- [ ] 4.5 Run `./gradlew :app:validateDebugScreenshotTest` — diffs reviewed and committed.
- [ ] 4.6 Run `./gradlew testDebugUnitTest` repo-wide — green.

## 5. Validation

- [ ] 5.1 Run `./gradlew spotlessCheck lint` — clean.
- [ ] 5.2 Run `openspec validate adopt-list-detail-scene-strategy --strict` — passes.
- [ ] 5.3 Manual smoke on a foldable / tablet emulator (Pixel Tablet AVD): launch app, navigate to Feed, confirm placeholder visible in right pane, tap a tab to ensure Search/Chats/Profile do not engage the strategy (full-screen on all widths). Capture a screenshot for the PR description.
- [ ] 5.4 Confirm on a Pixel 6 (compact) emulator: launch app, navigate to Feed, confirm no placeholder is composed and behavior is identical to pre-change (no regression in the bar-mode chrome).
