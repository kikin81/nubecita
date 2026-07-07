# Tasks — Add Manage Pinned Feeds

> Beads epic `nubecita-ydfn`. Each group maps to one child task; the bd id is noted in the heading. Dependency order matches the bd graph: `.1`,`.2` are independent starts; `.3` needs `.2`; `.4` needs `.1`+`.2`; `.5` needs `.3`+`.4`; `.6` needs `.4`.

## 1. Module scaffold (bd nubecita-ydfn.1)

- [ ] 1.1 Create `:feature:feeds:impl` (`nubecita.android.feature` plugin); add `implementation(libs.reorderable)`, `:core:feeds`, `:data:models`, `:feature:feeds:api`; declare namespace + empty `consumer-rules.pro`.
- [ ] 1.2 Register `@Provides @IntoSet @MainShell EntryProviderInstaller` for the `Feeds` `NavKey`, pointing at the new screen.
- [ ] 1.3 Delete `app/src/main/java/.../navigation/FeedsPlaceholderModule.kt` and confirm the `Feeds` route resolves to the new module.
- [ ] 1.4 Register the module in `settings.gradle.kts`; `:app:assembleDebug` green.

## 2. Reorder repository operation (bd nubecita-ydfn.2)

- [ ] 2.1 Add `suspend fun reorderPinnedFeeds(orderedPinnedUris: List<String>): Result<Unit>` to `PinnedFeedsRepository` (interface + `DefaultPinnedFeedsRepository`).
- [ ] 2.2 Implement the array-rebuild rule: re-read fresh prefs at commit; `items` = reordered pinned + (server-pinned URIs absent from the list, appended in server order — never dropped) + unpinned in prior order; preserve foreign prefs via `mergeSavedFeedsPrefs`; map `FOLLOWING_FEED_URI` back to its `type="timeline"` entry.
- [ ] 2.3 Add a `@Transaction` batch-position write on `SavedFeedDao` (no schema/column change) and update Room positions inside the op.
- [ ] 2.4 Unit-test: reorder produces correct `SavedFeedsPrefV2.items`; a server-pinned URI absent from `orderedPinnedUris` is appended (not dropped).

## 3. Durable commit-on-exit (bd nubecita-ydfn.3, needs §2)

- [ ] 3.1 Inject `@ApplicationScope CoroutineScope` into `DefaultPinnedFeedsRepository`; add a `Mutex` + `reorderPending` flag.
- [ ] 3.2 Guard `reorderPinnedFeeds` and `refresh()`'s cache-reconciliation section with the `Mutex`; skip saved-feed reconciliation in `refresh()` while `reorderPending` (read inside the lock).
- [ ] 3.3 Roll back Room positions if `putPreferences` fails, before clearing `reorderPending` (mirror `pinFeed`/`unpinFeed`).
- [ ] 3.4 VM side: commit on `onCleared` and lifecycle `ON_STOP`, dirty-checked (`currentOrder != lastCommittedOrder`), launched on `@ApplicationScope`.
- [ ] 3.5 Unit-test: `refresh()` skips reconciliation while pending; `Mutex` serializes concurrent commits; failed commit rolls back positions.

## 4. ViewModel + screen (bd nubecita-ydfn.4, needs §1 + §2)

- [ ] 4.1 `ManageFeedsViewModel : MviViewModel<State, Event, Effect>`; `State.status: sealed ManageFeedsLoadStatus (Loading / Content(feeds: ImmutableList<PinnedFeedUi>))` with local drag-mutable `feeds`; `Effect = ShowError` only. Read from `observePinnedFeeds()`.
- [ ] 4.2 `ManageFeedsScreen`: `reorderable` `LazyColumn` with per-row leading drag handle (`Modifier.draggableHandle`, long-press lift, tonal-elevation raise + haptic on pickup/drop); content avatar+name; trailing remove icon → `unpinFeed`, omitted when `kind == FeedKind.Following`. No swipe.
- [ ] 4.3 Wire local reorder to VM state; wire commit triggers (`onCleared` via VM, `ON_STOP` via a `LifecycleEventObserver` in the screen). Set `Scaffold(containerColor = surface)`.
- [ ] 4.4 Smoke-test on bench flavor: open the screen, reorder, confirm persisted order after re-open; no FATAL.

## 5. Accessibility + tests (bd nubecita-ydfn.5, needs §3 + §4)

- [ ] 5.1 Add custom semantics actions per row: `Move up`, `Move down`, `Remove` (omit `Remove` for Following).
- [ ] 5.2 VM unit tests: seed→reorder mutates state; `onCleared` dirty calls `reorderPinnedFeeds` once, unchanged does NOT; `onRemove` calls `unpinFeed`; Following exposes no `Remove`.
- [ ] 5.3 Screenshot tests + `@Preview`s: loading, populated list, mid-drag raised row, Following-without-remove. Commit baselines.

## 6. Translations (bd nubecita-ydfn.6, needs §4)

- [ ] 6.1 Add every new `:feature:feeds:impl` `<string>` to `values-b+es+419` and `values-pt-rBR` in the same change; run the module's own `lint` (`:app` lint misses `MissingTranslation`).
