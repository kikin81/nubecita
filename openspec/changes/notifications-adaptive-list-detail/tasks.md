## 1. Resources and Dependencies

- [x] 1.1 Add `nubecita_detail_pane_select_notification` string resource to `:designsystem` in `values/strings.xml`, `values-b+es+419/strings.xml`, and `values-pt-rBR/strings.xml`
- [x] 1.2 Add `libs.androidx.compose.material3.adaptive.navigation3` dependency to `:feature:notifications:impl/build.gradle.kts`

## 2. Navigation Module and Empty Placeholder

- [x] 2.1 Implement internal `NotificationsDetailPlaceholder` composable in `:feature:notifications:impl`
- [x] 2.2 Register `entry<NotificationsTab>` with `ListDetailSceneStrategy.listPane(detailPlaceholder = ...)` in `NotificationsNavigationModule`

## 3. Backstack Scoping and Selection Highlighting

- [x] 3.1 Implement `findSelectedPostUri` and `findSelectedProfileHandle` in `SelectedNotificationTarget.kt` scoped to the `NotificationsTab` backstack segment
- [x] 3.2 Add unit tests in `SelectedNotificationTargetTest.kt` covering single-item stacks, stacked subroutes, and cross-tab isolation from Feed
- [x] 3.3 Update `NotificationRow` to support `selected: Boolean` with `colorScheme.secondaryContainer` background tint
- [x] 3.4 Wire selection detection and `mainShellNavState.replaceTop(...)` detail navigation in `NotificationsScreen`

## 4. Quality Gates and Verification

- [x] 4.1 Run unit tests `./gradlew :feature:notifications:impl:testBenchDebugUnitTest`
- [x] 4.2 Run Spotless check `./gradlew spotlessCheck`
- [x] 4.3 Verify adaptive behavior on Compact phone (`emulator-5554`) and Expanded foldable (`emulator-5556`)
