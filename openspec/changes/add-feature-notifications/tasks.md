## 1. Design system: icons + reason renderer

- [ ] 1.1 Add `AlternateEmail` (``), `ExpandMore` (``), `FormatQuote` (``), and `Verified` (``) entries to `NubecitaIconName` (alphabetical insertion). Update `Notifications` codepoint from `` to ``. Adds: `NubecitaIconNameTest.every_codepoint_isASingleScalar` continues to pass.
- [ ] 1.2 Run `./scripts/update_material_symbols.sh`; commit the regenerated subset font under `designsystem/src/main/res/font/`. Adds: existing `NubecitaIconInstrumentationTest` runs against the new font.
- [ ] 1.3 Update `NubecitaIconShowcaseScreenshotTestKt` to include the new icons in the showcase; re-baseline the showcase screenshots (PR label `update-baselines`).
- [ ] 1.4 Add `likeAccent` and `repostAccent` extended-color-scheme tokens to `:designsystem/Theme.kt` (or document fallback to `colorScheme.error` / `colorScheme.tertiary` if token bandwidth is constrained). Adds: a unit test asserting both tokens resolve in light and dark schemes.
- [ ] 1.5 Add `NotificationReasonIcon(reason, modifier)` composable in `:designsystem` with an exhaustive `when` over `NotificationReason`. Adds: `NotificationReasonIconPreviews` (one row per reason) + `NotificationReasonIconScreenshotTest`. Baselines committed.

## 2. Data models

- [ ] 2.1 Add `NotificationReason` enum (14 entries including `Unknown`) to `:data:models`. Adds: `NotificationReasonTest.unknownIsFallback`.
- [ ] 2.2 Add `NotificationFilter` enum (`All`, `Mentions`, `Reposts`, `Follows`, `Likes`) with internal `reasons: List<String>?` property. Adds: `NotificationFilterTest` asserting each filter's reasons mapping.
- [ ] 2.3 Add `NotificationItemUi` sealed interface (`Single` / `Aggregated`) with required fields. Both variants implement `@Stable`; `actors` is `ImmutableList<AuthorUi>`; `subjectPost` is `PostUi?`. Adds: stability annotation enforced by Compose compiler.
- [ ] 2.4 Add `NotificationItemUiFixtures` object with factories: `singleLike`, `aggregatedLikes(actorCount)`, `singleFollow`, `aggregatedFollows(actorCount)`, `singleReply`, `singleQuote`, `singleMention`, plus one fixture per remaining known reason. Adds: `NotificationItemUiFixturesTest` smoke-checking each factory returns the expected variant.

## 3. Feature module scaffolding

- [ ] 3.1 Register `:feature:notifications:api` and `:feature:notifications:impl` in `settings.gradle.kts`. Add empty `build.gradle.kts` files applying `nubecita.android.library` (`:api`) and `nubecita.android.feature` (`:impl`).
- [ ] 3.2 In `:feature:notifications:api`: add `@Serializable data object NotificationsTab : NavKey`. Adds: a JVM unit test asserting the serializer round-trips.
- [ ] 3.3 In `:feature:notifications:impl`: create the package layout (`NotificationsScreen.kt`, `NotificationsViewModel.kt`, `NotificationsContract.kt`, `NotificationsScreenViewState.kt`, `data/`, `di/`, `ui/`, `NotificationsTestTags.kt`).
- [ ] 3.4 Add a `NotificationsNavigationModule` Hilt `@Module` providing the `@IntoSet @MainShell EntryProviderInstaller` that registers `NotificationsTab` rendering `NotificationsScreen()`.

## 4. Repository layer

- [ ] 4.1 Define `NotificationsRepository` interface in `:feature:notifications:impl/data/`: `fetchPage(filter, cursor)`, `markSeen(seenAt)`, `unreadCount()`. Each returns `Result<…, DataError>` via the shared error wrapper.
- [ ] 4.2 Implement `DefaultNotificationsRepository`:
   - Calls `NotificationService.listNotifications(limit = 50, cursor, reasons)`.
   - Collects unique URIs (from `reasonSubject` for like/repost/like-via-repost/repost-via-repost/subscribed-post and from `uri` for reply/quote/mention).
   - Batches a single `FeedService.getPosts(uris)` (split into ≤25-URI batches if needed).
   - Calls `NotificationsMapper` with both responses.
   - Adds: `DefaultNotificationsRepositoryTest` covering empty page, single-batch hydration, multi-batch hydration, missing-URI graceful null.
- [ ] 4.3 Add `markSeen(seenAt)` calling `NotificationService.updateSeen(seenAt)`. Errors silently logged (return `Result.success(Unit)` regardless — next poll corrects).
- [ ] 4.4 Add `unreadCount()` calling `NotificationService.getUnreadCount()`. Adds: a repository test covering error paths.
- [ ] 4.5 Register repository binding in `NotificationsRepositoryModule` (`@Binds DefaultNotificationsRepository -> NotificationsRepository`).

## 5. Mapper

- [ ] 5.1 Implement `NotificationsMapper.toUiPage(response, hydratedPosts): NotificationsPage`. Groups by `(reason, reasonSubject)` for post-bearing reasons, `(reason, sameCalendarDay)` for follows, single-event otherwise. Adds: `NotificationsMapperTest` with cases — three same-subject likes collapse, two follows same day collapse, one reply stays Single, unknown reason maps to `NotificationReason.Unknown`, deleted subject post → `subjectPost = null` but row still emitted.
- [ ] 5.2 Mapper caps `actors` at the visual stack limit (5) but preserves the full list in the `Aggregated` data class — the chevron sheet renders all. Adds: `aggregationCapTest` with 10 actors verifying overflow handling.
- [ ] 5.3 Sort the output list by `indexedAt` descending (preserving the lexicon's order — lexicon already sorts). Adds: a test asserting two aggregated groups stay in the order their newest event arrived.

## 6. ViewModel

- [ ] 6.1 Implement `NotificationsViewModel : MviViewModel<NotificationsState, NotificationsEvent, NotificationsEffect>`. Initial state `loadStatus = InitialLoading`, `activeFilter = All`. Adds: an init test verifying first listNotifications call fires on construction.
- [ ] 6.2 Handle `Refresh`: reset cursor null, set status to `Refreshing`, refetch. Adds: `NotificationsViewModelTest.refreshReplacesItems`.
- [ ] 6.3 Handle `LoadMore`: gate on `loadStatus == Idle && hasMore`; set status to `Appending`; append on success. Adds: `loadMore` tests.
- [ ] 6.4 Handle `FilterSelected(filter)`: only refetch if `filter != activeFilter`. Reset cursor, items, status to `InitialLoading`, then fetch. Adds: `filterSelectionRefetches` test.
- [ ] 6.5 Handle `RowTapped(item)`: derive target `NavKey` per reason (see design D8) and emit `NavigateTo(target)`. For Unknown reason emit nothing. Adds: `rowTapEmitsCorrectNavKey` test parameterized over each reason.
- [ ] 6.6 Handle `AvatarStackTapped(item)`: emit `ShowActorList(item.actors)`. Adds: a test.
- [ ] 6.7 Handle `TabExited`: call `repository.markSeen(now)` and optimistically zero the unread-count store. Errors swallowed. Adds: `tabExitedCallsMarkSeenAndZerosBadge` test verifying the store StateFlow drops to 0 even if markSeen succeeds asynchronously.
- [ ] 6.8 Error handling: any listNotifications failure with non-empty items emits `ShowError(...)`; with empty items transitions to `InitialError(error)`. Adds: tests for both branches.

## 7. Unread-count store and polling lifecycle

- [ ] 7.1 Implement `NotificationsUnreadCountStore` (`@Singleton`): exposes `unreadCount: StateFlow<Int>`. Internal `refresh()` suspends to call `repository.unreadCount()`. Single-flight via `Mutex.withLock { … }`-pattern (`tryLock` to skip overlapping ticks).
- [ ] 7.2 Add a `NotificationsPollingObserver : DefaultLifecycleObserver` (or equivalent) wired in `NubecitaApplication.onCreate` against `ProcessLifecycleOwner.lifecycle`. Runs `repeatOnLifecycle(STARTED) { while (isActive) { store.refresh(); delay(60.seconds) } }`. Adds: an instrumented or robolectric test using `TestLifecycleOwner` verifying polls stop on STOP and restart on START.
- [ ] 7.3 Implement exponential backoff (60 → 120 → 240 → cap 300, reset on success) inside the observer. Adds: a test using a `TestDispatcher` virtual clock asserting the delay sequence on consecutive failures.
- [ ] 7.4 Clear the store (emit `0`) on logout. Hook via the existing logout pipeline (`:core:auth`'s session-end signal). Adds: a test simulating logout.

## 8. Screen composable

- [ ] 8.1 Implement `NotificationsScreen` Root composable: hoists VM via `hiltViewModel()`, collects state with `collectAsStateWithLifecycle`, drains effects in one `LaunchedEffect`. Routes `NavigateTo` via `LocalMainShellNavState.current.add(target)`, `ShowError` via Snackbar, `ShowActorList` opens the `ActorListSheet`.
- [ ] 8.2 Implement `NotificationsContent(state, onEvent)` stateless composable: `Scaffold(containerColor = colorScheme.surface)`, `TopAppBar`, `FilterChipRow`, `PullToRefreshBox { LazyColumn { items(…) } }`. Adds: `NotificationsScreenPreviews` with `@PreviewNubecitaScreenPreviews` covering InitialLoading / Empty / InitialError / Loaded (single + aggregated mix). Adds: `NotificationsScreenScreenshotTest`.
- [ ] 8.3 Implement `NotificationsScreenViewState` (sealed; InitialLoading / Empty / InitialError / Loaded) + `NotificationsState.toViewState()` extension. Adds: `NotificationsScreenViewStateTest` covering the full dispatch matrix.
- [ ] 8.4 Implement `FilterChipRow(filters, activeFilter, onSelect)` in `:feature:notifications:impl/ui/`. Horizontal `LazyRow`. Adds: `@Preview` + screenshot test for all-states (each filter active in turn).
- [ ] 8.5 Implement `NotificationRow` composable with two specializations (`Single`, `Aggregated`) — actor row, reason icon, stacked avatars, chevron, subject preview slot. Adds: `@Preview` matrix (each reason × Single/Aggregated × isRead true/false × subjectPost null/non-null) + screenshot tests.
- [ ] 8.6 Implement `StackedAvatarRow(actors, maxVisible = 5)` in `:designsystem` (or `:feature:notifications:impl/ui/` if private to this surface). Adds: `@Preview` covering 1, 2, 5, 8 actors.
- [ ] 8.7 Implement `ActorListSheet(actors, onActorClick, onDismiss)` as a `ModalBottomSheet` listing actors as profile rows. Adds: `@Preview` + screenshot test.
- [ ] 8.8 Implement empty state composable and InitialError retry composable in `:feature:notifications:impl/ui/`. Reuse `:designsystem` empty-state primitives where possible. Adds: screenshot tests.
- [ ] 8.9 Wire `LocalTabReTapSignal` collection + `lazyListState.animateScrollToItem(0)`. Adds: an instrumented or unit test using a fake SharedFlow asserting the scroll call.
- [ ] 8.10 Wire the tab-exit detection: `LaunchedEffect` keyed on `mainShellNavState.topLevelKey` that fires `TabExited` when transitioning away from `NotificationsTab`. Adds: unit test (with a fake nav state) asserting the event fires exactly on tab-away.

## 9. MainShell integration

- [ ] 9.1 Add `Notifications` to `TopLevelDestinations` in `MainShell.kt`, third position (between Search and Chats). Use `NubecitaIconName.Notifications`.
- [ ] 9.2 Add `R.string.main_shell_tab_notifications` string resource.
- [ ] 9.3 Inject `NotificationsUnreadCountStore` into `MainShell` via the `NavigationEntryPoint` (or add a `MainShellEntryPoint`). Collect `unreadCount` via `collectAsStateWithLifecycle` and pass `Int` to `MainShellChrome`.
- [ ] 9.4 Update `MainShellChrome` to accept `unreadCount: Int` and wrap the Notifications nav item's icon in a `BadgedBox` rendering the count only when `> 0`. Adds: `@Preview`s for `MainShellChrome` covering count = 0, 1, 9, 99+ (use `99+` overflow per M3 badge convention).
- [ ] 9.5 Re-baseline `MainShell` screenshot tests (`MainShellChromePreview*`, any existing layout fixtures). PR label `update-baselines`.

## 10. Mock-engine fixtures and androidTest harness

- [ ] 10.1 Add JSON fixtures under `:feature:notifications:impl/src/androidTest/assets/notifications/`: `listNotifications-page1.json`, `listNotifications-page2.json`, `listNotifications-empty.json`, `listNotifications-mixed-reasons.json`, `getUnreadCount-7.json`, `updateSeen-ok.json`, plus a `getPosts-page1.json` covering the URIs in page1.
- [ ] 10.2 Wire the fixtures into `MockEngineModule`'s response router so `:feature:notifications:impl` androidTest can use them.

## 11. Deep-link follow-up

- [ ] 11.1 Update `AtUriToDeepLink.toNubecitaDeepLink`'s `else -> null` branch to return `"nubecita://notifications"` (per [[nubecita-8487]]). Adds: a new test case in `AtUriToDeepLinkTest` covering the fallback.
- [ ] 11.2 Verify the `nubecita://notifications` scheme is registered in `:app/AndroidManifest.xml`'s intent-filter set and routes through `MainActivity` to `DeepLinkRouter` → `NotificationsTab`. Adds: a manifest sanity test if absent.

## 12. Final validation

- [ ] 12.1 Run `./gradlew spotlessCheck lint :app:checkSortDependencies testDebugUnitTest`. All pass.
- [ ] 12.2 Run `./gradlew :designsystem:validateDebugScreenshotTest :feature:notifications:impl:validateDebugScreenshotTest :app:validateDebugScreenshotTest`. Re-baseline any intentional changes.
- [ ] 12.3 Sanity-check the 5-tab layout on a real device or emulator: scroll-to-top via re-tap, filter chips switch the list, polling badge updates within ~60s, pull-to-refresh works, mark-read clears badge after tab exit, deep-link routing lands each reason on the right target.
- [ ] 12.4 Update `bd` issue [[nubecita-1fy.1]] status to in-progress when work starts; close on PR merge.
- [ ] 12.5 Open the PR with `Closes: nubecita-1fy.1` in the body and `update-baselines` label.
