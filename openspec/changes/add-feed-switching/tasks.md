# Implementation tasks

Each numbered group is a self-contained bd child / PR (epic **`nubecita-a580`**), ordered by dependency. Land them in
order; every group ends green (`./gradlew spotlessCheck lint testDebugUnitTest` +
`openspec validate add-feed-switching`).

## 1. Design-system glyph (standalone PR — lands first)  ·  `nubecita-a580.1`

- [ ] 1.1 Add `LocalFireDepartment("")` to `NubecitaIconName` (alphabetical, between `Language` and `LockPerson`).
- [ ] 1.2 Run `./scripts/update_material_symbols.sh` to regenerate the subset font; commit the updated `material_symbols_rounded.ttf` with the enum change.
- [ ] 1.3 Run `./gradlew :designsystem:testDebugUnitTest` (`NubecitaIconNameTest` validates the codepoint) and regenerate the `:designsystem` icon-showcase screenshot baselines.
- [ ] 1.4 Verify no other module's baselines drifted; keep this PR scoped to the glyph only.

## 2. `:data:models` — feed-chip model  ·  `nubecita-a580.2`

- [ ] 2.1 Add `enum class FeedKind { Following, Generator, List }`.
- [ ] 2.2 Add `@Stable data class PinnedFeedUi(id, uri, kind, displayName, avatarUrl)` under `net.kikin.nubecita.data.models`.
- [ ] 2.3 Add fixture factories (one per `FeedKind`) mirroring `PostUiFixtures`.
- [ ] 2.4 Unit-test the fixtures; confirm `:data:models` gains no Compose/Hilt/atproto-runtime deps.

## 3. `:core:preferences` — last-selected feed  ·  `nubecita-a580.3`

- [ ] 3.1 Add `lastSelectedFeedUri: Flow<String?>` read + `suspend fun setLastSelectedFeedUri(uri: String)` write to `UserPreferencesRepository` (+ impl, DataStore key).
- [ ] 3.2 Unit-test read/write round-trip and default (`null`).

## 4. `:core:feeds` — pinned-feeds directory  ·  `nubecita-a580.4`

- [ ] 4.1 Create `:core:feeds` module (`nubecita.android.library` + `nubecita.android.hilt`); wire into `settings.gradle.kts` and `checkSortDependencies`.
- [ ] 4.2 Add the Discover (`whats-hot`) generator-URI constant and the Following sentinel.
- [ ] 4.3 Implement `PinnedFeedsRepository`: read `getPreferences` → `SavedFeedsPrefV2`, filter `pinned`, preserve order, split by `type`, hydrate `type="feed"` via `getFeedGenerators`, map to `PinnedFeedUi`.
- [ ] 4.4 Implement the `[Following, Discover]` fallback for no-prefs / empty / failure, surfacing a non-fatal error signal.
- [ ] 4.5 Expose helpers for restore-last-selected validation against the live pinned set.
- [ ] 4.6 Unit-test: order preservation, pinned filter, type split, generator hydration (mocked), all fallback paths.

## 5. `:feature:feeds:api` stub + `:app` placeholder  ·  `nubecita-a580.5`

- [ ] 5.1 Create `:feature:feeds:api` exposing `@Serializable data object Feeds : NavKey`.
- [ ] 5.2 Register a `@MainShell @IntoSet EntryProviderInstaller` in `:app` rendering a "Manage feeds — coming soon" placeholder for `Feeds`.
- [ ] 5.3 Verify navigation: pushing `Feeds` renders the placeholder in the inner `NavDisplay`.

## 6. `:feature:feed:impl` — repository + VM dispatch  ·  `nubecita-a580.6`

- [ ] 6.1 Extend `FeedRepository` with `getFeed(feedUri, …)` and `getListFeed(listUri, …)`, each returning `Result<TimelinePage>` via the shared `toFeedItemsUi()` mapper.
- [ ] 6.2 Implement them in `DefaultFeedRepository` (`FeedService.getFeed` / `getListFeed`); keep it the only `FeedService` importer.
- [ ] 6.3 Add `FeedEvent.Bind(feedUri, kind)`; make `load`/`refresh`/`loadMore` dispatch by `kind` (Following→getTimeline, Generator→getFeed, List→getListFeed).
- [ ] 6.4 Unit-test the three dispatch branches and that pagination semantics are unchanged; keep existing Following tests green.

## 7. `:feature:feed:impl` — host + retention  ·  `nubecita-a580.7`

- [ ] 7.1 Add `FeedHostViewModel : MviViewModel<FeedHostState, FeedHostEvent, FeedHostEffect>` (chips, lists, `selectedFeedUri`, `FeedHostStatus`), injecting `PinnedFeedsRepository` + `UserPreferencesRepository`.
- [ ] 7.2 Implement `Load` (pinned load → `Ready`; failure → `ErrorFallback` + `ShowError`), restore-last-selected, and `SelectFeed`/`SelectList` persisting `lastSelectedFeedUri`.
- [ ] 7.3 Add `FeedHost` composable: render the active `FeedPane` via `hiltViewModel(key = feedUri)` wrapped in `rememberSaveableStateHolder().SaveableStateProvider(feedUri)`; bind `(feedUri, kind)` once.
- [ ] 7.4 Repoint the `@MainShell` Feed entry (`FeedNavigationModule`) to host `FeedHost` instead of `FeedScreen` directly.
- [ ] 7.5 Unit-test `FeedHostViewModel`: load/ready/fallback, restore (valid + stale URI), selection persistence, list-vs-feed selection.
- [ ] 7.6 Verify retention: switch away/back restores posts + scroll with no re-fetch; only one pane composed.

## 8. `:feature:feed:impl` — chip row UI  ·  `nubecita-a580.8`

- [x] 8.1 Build `FeedChipRow` (`LazyRow` of `FilterChip`s, 8dp spacing, 16dp content padding); avatar `leadingIcon` for generators, `Home`/`LocalFireDepartment` glyphs for defaults; selected = filled container with avatar kept (no checkmark); `Role.Tab` semantics.
- [x] 8.2 Add the trailing `[＋]` button → `LocalMainShellNavState.current.add(Feeds)`.
- [x] 8.3 Add the `[ Lists ⌄ ]` disclosure chip (shown only if ≥1 list) + `PinnedListsSheet` (`ModalBottomSheet` single-select radio); selecting relabels the chip and activates the list pane.
- [x] 8.4 Add scroll-away behavior tied to the active pane's `LazyListState`, reset to shown on switch.
- [x] 8.5 Screenshot tests: `FeedChipRow` (selected/unselected, avatar vs glyph, with/without disclosure chip), `PinnedListsSheet`, scrolled-away state.

## 9. Adaptive + integration polish  ·  `nubecita-a580.9`

- [ ] 9.1 Confirm list-pane-width rendering on medium/expanded; extend `MainShellListDetailScreenshotTest` baselines to include the chip row.
- [ ] 9.2 Validate 120 Hz scroll on a real device (no jank on header collapse / feed switch); run the macrobench if warranted.
- [ ] 9.3 Final pass: `./gradlew spotlessCheck lint testDebugUnitTest :app:checkSortDependencies`, `openspec validate add-feed-switching`, and update each spec `## Purpose` if archiving.
