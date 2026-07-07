# Add Manage Pinned Feeds

> Beads epic: `nubecita-ydfn` (tasks `.1`–`.6`). Source design: `docs/superpowers/specs/2026-07-05-manage-pinned-feeds-design.md` (merged PR #685).

## Why

Users can pin custom feeds from Search, but there is no way to reorder or remove them — the Feeds-home pill row's `[＋]` button navigates to a `Manage feeds — coming soon` placeholder. This change replaces that placeholder with a real management screen, closing the last gap in the pin lifecycle (add → reorder → remove).

## What Changes

- New `:feature:feeds:impl` module hosting a `ManageFeedsScreen` + `ManageFeedsViewModel`, registered as the `@MainShell` destination for the existing `Feeds` `NavKey`. The `:app`-side `FeedsPlaceholderModule` is deleted.
- The screen lists the user's pinned feeds in server order and supports **drag-to-reorder** (via the already-present `sh.calvin.reorderable` library) with a drag handle, tonal-elevation lift, and haptics.
- Each row has a trailing **remove** action that **unpins non-destructively** (feed stays saved in AT Proto preferences). The **Following** timeline is reorderable but never removable (its remove affordance is omitted).
- New `reorderPinnedFeeds(orderedPinnedUris)` operation on `PinnedFeedsRepository` (`:core:feeds`) plus a batch-position write on `SavedFeedDao`, with a defined array-rebuild rule that **merges concurrent cross-client pins** (never drops a server-pinned feed).
- **Durable commit-on-exit**: the reorder is committed on screen-exit *and* app-background via an `@ApplicationScope` coroutine, guarded by a repository `Mutex` + `reorderPending` token so a concurrent `refresh()` cannot stomp the local order, with Room-position **rollback** on network failure.
- Accessibility: custom `Move up` / `Move down` / `Remove` semantics actions so TalkBack users can reorder and unpin without dragging.
- New user-facing strings with `es-419` + `pt-BR` translations.

Scope is **pinned-only**; a saved-but-unpinned library section is explicitly out of scope (additive later — the `pinned`/`position` columns already exist). No breaking changes.

## Capabilities

### New Capabilities
- `feature-feeds-management`: the pinned-feeds management screen — list in server order, drag-to-reorder interaction, non-destructive remove, Following-locked rule, empty-state-free lifecycle, and TalkBack reorder/remove semantics.
- `core-feeds-reordering`: the `:core:feeds` domain contract for reordering pinned feeds — `reorderPinnedFeeds` array-rebuild + cross-client merge, durable app-scoped commit-on-exit, `Mutex`/`reorderPending` stomp-guard against `refresh()`, and rollback on `putPreferences` failure.

### Modified Capabilities
<!-- None: the entry point and read/unpin paths already exist; no existing spec's requirements change. -->

## Impact

- **New module**: `:feature:feeds:impl` (Navigation 3 feature-impl; depends on `libs.reorderable`, `:core:feeds`, `:data:models`, `:feature:feeds:api`).
- **Modified**: `:core:feeds` `PinnedFeedsRepository` + `DefaultPinnedFeedsRepository` (add `reorderPinnedFeeds`, `Mutex`/`reorderPending`, `@ApplicationScope` injection); `:core:database` `SavedFeedDao` (batch-position write, schema-neutral — no new columns).
- **Deleted**: `app/src/main/java/.../navigation/FeedsPlaceholderModule.kt`.
- **AT Proto**: reorder writes via existing `putPreferences`/`getPreferences` (`SavedFeedsPrefV2`); no new XRPC surface.
- **Tests**: VM unit tests, `:core:feeds` repository tests (merge/rollback/stomp-guard), screenshot tests; new strings gated by module `MissingTranslation` lint.
