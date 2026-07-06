# Manage Pinned Feeds — Design

**Date:** 2026-07-05
**Status:** Proposed — under review (brainstorm output); pending sign-off + implementation plan
**Scope:** New `:feature:feeds:impl` screen that lists the user's pinned feeds, lets them reorder via drag-and-drop, and remove (unpin) feeds. Reachable from the existing `[＋]` button on the Feeds home pill row.

## Goal

Replace the `Manage feeds — coming soon` placeholder with a real feed-management screen. The screen lists pinned feeds in server order, supports drag-to-reorder, and lets the user unpin a feed (keeping it saved). The heavy lifting — reading and writing AT Protocol `savedFeedsPref` — already exists in `:core:feeds`; this feature adds the missing UI module plus one new reorder operation.

## Scope decision: pinned-only, additive-later

The AT Proto `savedFeedsPref` v2 models each entry as `{ id, type: timeline|feed|list, value: <uri>, pinned: Boolean }`. A feed can be **saved + pinned** (a pill on the Feeds home), **saved but not pinned** (in the library, off the pill row), or not saved.

This screen manages **only the pinned subset**. Order is meaningful only for pinned feeds (it is the pill order); unpinned saved feeds have no user-facing order. A "saved but unpinned" section is explicitly out of scope for this epic, but the data layer already carries `pinned` and `position` columns, so adding that section later is additive — no rewrite. (Decision: Option 3 from brainstorm.)

## What already exists (no work required)

- **Entry point.** The Feeds home pill row's trailing `[＋]` `IconButton` (`FeedChipRow.kt:223`) already calls `onManageFeedsClick → onNavigateTo(Feeds)`. The `Feeds` `NavKey` lives in `:feature:feeds:api`. The destination is currently an `:app`-side placeholder (`app/.../navigation/FeedsPlaceholderModule.kt`) whose KDoc says to delete it when `:feature:feeds:impl` ships.
- **Read path.** `PinnedFeedsRepository.observePinnedFeeds(): Flow<PinnedFeedsResult>` (`:core:feeds`) emits pinned `PinnedFeedUi`s in `position ASC` order (falls back to Following + Discover when empty).
- **Remove path.** `PinnedFeedsRepository.unpinFeed(uri)` is already **non-destructive**: it sets `pinned=false` in the AT Proto array and `dao.setPinned(uri, false)` — it never deletes the entry, so feeds stay saved and survive cross-client sync (`PinnedFeedsRepository.kt:100, 350`). "Remove = unpin, stay saved" needs **zero** repo changes.
- **Following identity.** The Following timeline is a synthesized `type="timeline"` entry with sentinel `FOLLOWING_FEED_URI = "following"` and `FeedKind.Following` on `PinnedFeedUi.kind`. Rows identify it via `kind == FeedKind.Following` (no URI string-matching).
- **Drag library.** `sh.calvin.reorderable:reorderable` 3.1.0 is already in `libs.versions.toml` (`libs.reorderable`), with a working reference in `:feature:composer:impl/ComposerScreen.kt` (media-attachment reorder).
- **App scope.** `@ApplicationScope CoroutineScope` (`core/common/.../coroutines/ApplicationScope.kt`) already exists and is injected into singletons (`DefaultPostInteractionsCache`, `DefaultKlipyRepository`). Reused here for the durable commit.
- **Repo is a singleton.** The `PinnedFeedsRepository` binding is `@Singleton` in `FeedsModule.kt:17` (the `@Binds` for `DefaultPinnedFeedsRepository`), so the implementation is a process-wide single instance — an instance-level commit token is genuinely app-wide and shared with the `refresh()` caller.

## New work

### 1. `:feature:feeds:impl` module

New Navigation-3 feature-impl module (`nubecita.android.feature` plugin). Registers a `@Provides @IntoSet @MainShell EntryProviderInstaller` for the `Feeds` `NavKey`. Delete `FeedsPlaceholderModule` and wire this in. Add `implementation(libs.reorderable)`.

Standard MVI: `ManageFeedsViewModel : MviViewModel<ManageFeedsState, ManageFeedsEvent, ManageFeedsEffect>`.

- `ManageFeedsState`: `status: ManageFeedsLoadStatus` (sealed: `Loading` / `Content(feeds: ImmutableList<PinnedFeedUi>)`), where `feeds` is the **local, drag-mutable** order.
- `ManageFeedsEffect`: `ShowError(message)` only (snackbar for a failed unpin while on-screen). Reorder does **not** use effects — it commits on exit.
- No empty state: Following is non-removable and the repo falls back to Following + Discover when the pinned set is empty, so the list is never blank. States are just **Loading → Content**.

### 2. Reorder domain operation (`:core:feeds`)

New method on `PinnedFeedsRepository`:

```kotlin
suspend fun reorderPinnedFeeds(orderedPinnedUris: List<String>): Result<Unit>
```

and a `SavedFeedDao` batch-position write (single `@Transaction`).

**Array-rebuild rule.** `putPreferences` rewrites the whole `SavedFeedsPrefV2.items` array. To avoid clobbering changes made on another client while this screen was open (e.g. the user pins a feed on desktop), `reorderPinnedFeeds` **re-reads the current preferences at commit time** (a fresh `getPreferences`, done inside the commit's `Mutex`) rather than rebuilding from the phone's possibly-stale snapshot. The rebuild is:

```
serverPinned = pinned entries from the freshly-read prefs
ordered      = serverPinned sorted by orderedPinnedUris, with any
               serverPinned URI NOT in orderedPinnedUris appended
               (in server order) — never dropped
newItems     = [ordered pinned entries]
             + [unpinned saved entries in their prior relative order]
```

So a feed pinned on another client after the screen opened survives (appended), rather than being silently unpinned. Foreign preferences are preserved by the existing `mergeSavedFeedsPrefs`. Unpinned entries keep their relative order (they have no user-facing order to disturb). The `orderedPinnedUris` list may include the `FOLLOWING_FEED_URI = "following"` sentinel (Following is reorderable); the rebuild maps it back to its `type="timeline"` entry rather than treating it as a feed URI.

### 3. Durable commit-on-exit (ported from the G3 leave-with-undo pattern, `nubecita-kc17.4`)

The Compose screen owns the temporary drag order in `ManageFeedsState.feeds`; reordering mutates local state and feels instant. The write is deferred to screen exit and made durable:

1. **Triggers — screen-exit *and* app-background, only if dirty.** Commit on `ViewModel.onCleared()` (fires once when the screen is popped; retained across rotation, so no premature/duplicate commit — **not** `DisposableEffect.onDispose`, which fires on config changes too) **and** on app backgrounding (lifecycle `ON_STOP`, observed via `LifecycleEventObserver` in the screen). `onCleared` alone is not guaranteed to run before process death when the user swipes the app from Recents; the `ON_STOP` trigger closes that gap by committing while the app is still alive. Both paths are idempotent (dirty-checked + `Mutex`-serialized), so firing both is safe. Guard: commit only if `currentOrder != lastCommittedOrder`. Skipping a no-op `putPreferences` protects the battery (a stated project priority).
2. **App-scoped execution.** The trigger launches the commit on the injected `@ApplicationScope` scope — `applicationScope.launch { repository.reorderPinnedFeeds(order) }` — because `reorderPinnedFeeds` is a `suspend fun` and cannot be called directly from the non-coroutine `onCleared()`, and because the work must outlive the cancelled `viewModelScope` or the `putPreferences` call dies in flight.
3. **`Mutex`, not a bare `AtomicBoolean`.** A boolean checked outside a lock has a check-then-act race: `refresh()` can pass an `isInFlight==false` guard, then `commit` flips it and writes Room, then refresh overwrites — the exact stomp we are preventing. Instead, a repo-level `Mutex` guards **both** `reorderPinnedFeeds()` and the cache-reconciliation section of `refresh()`; a `reorderPending` flag is read/written *inside* the lock. The Mutex also serializes back-to-back commits (reorder → exit → reopen → reorder → exit before the first settles): last write wins, no interleave.
4. **Stomp protection.** While `reorderPending` is set (checked inside the Mutex), `refresh()` skips saved-feed reconciliation, preserving the local Room order until the network write settles and clears the flag. A cross-client membership change made during the sub-second in-flight window is reconciled on the next `refresh()` after the token clears — an acceptable simplification given the window size.
5. **Rollback on failure (matches `pinFeed`/`unpinFeed`).** `reorderPinnedFeeds` writes the new Room `position` values first, then `putPreferences`. If the network write fails, it **restores the prior positions in Room** (captured before the write) before clearing `reorderPending`, so the local cache never lingers ahead of the server's canonical order. This mirrors the existing optimistic-write-then-roll-back shape in `pinFeed`/`unpinFeed` (`PinnedFeedsRepository.kt:320-326, 358-363`). Because the commit happens after the screen is gone, the failure is not surfaced as a snackbar — the rollback + next-`refresh` reconciliation is the recovery path, not a user prompt.

### 4. Row UI + interaction

Each row (`M3` list item, `surfaceContainer` card role) is:

- **Leading:** drag handle (six-dot icon), `Modifier.draggableHandle()` — long-press to lift. Tonal-elevation raise + haptic on pickup and on drop (per M3 motion polish).
- **Content:** feed avatar + display name.
- **Trailing:** remove (unpin) icon → `viewModel.onRemove(uri)` → `unpinFeed`. **Omitted** when `kind == FeedKind.Following` so the user can never orphan themselves from the main network view.

**No swipe-to-dismiss.** Horizontal swipe fights the vertical drag gesture on the same surface — an off-axis drag would trigger an accidental unpin. Explicit affordances solve discoverability and gesture conflict together.

**Accessibility (non-negotiable).** Drag is invisible to TalkBack, so each row carries custom semantics actions wired to the row modifier: **Move up**, **Move down**, and (when not Following) **Remove**. Screen-reader users reorder and unpin with zero drag interaction.

## Testing

- **VM unit tests** (`:core:testing`, JUnit Jupiter + Turbine + MockK): seed → drag-reorder mutates state; `onCleared` with a dirty order calls `reorderPinnedFeeds` once; `onCleared` with an unchanged order does **not**; `onRemove` calls `unpinFeed`; Following row exposes no Remove.
- **Repository tests** (`:core:feeds` JVM): `reorderPinnedFeeds` produces the correct `SavedFeedsPrefV2.items` array (pinned reordered + unpinned preserved + foreign prefs intact); a feed pinned server-side but absent from `orderedPinnedUris` is **appended, not dropped** (cross-client merge); `putPreferences` failure **rolls back** Room positions; `refresh()` skips reconciliation while `reorderPending`; Mutex serializes concurrent commits.
- **Screenshot tests** (`:feature:feeds:impl`): loading state; populated list; a mid-drag row (raised/elevated); Following row without a remove icon. Add `@Preview`s alongside.
- **Strings:** any new user-facing strings need `values-b+es+419` + `values-pt-rBR` entries in the same change (module lint catches `MissingTranslation`, `:app` lint does not).

## Out of scope (YAGNI)

- Saved-but-unpinned library section (additive later; columns already exist).
- Empty state (structurally impossible here).
- Swipe-to-dismiss.
- On-drop / debounced network writes (rejected in favor of durable commit-on-exit).
- Making the Following timeline removable (it is reorderable, but never removable).
