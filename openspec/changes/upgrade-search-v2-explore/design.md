# Design

The full design, with the mockup decisions and the spike results, is `docs/superpowers/specs/2026-11-22-search-v2-explore-design.md`. The task-level plan is `docs/superpowers/plans/2026-11-22-search-v2-explore.md`. This file records the decisions that shape the code.

## Context

- Search is the list pane of `MainShell`'s `ListDetailSceneStrategy`; its entry supplies a `detailPlaceholder`. The strategy composes that placeholder at the scene level, outside the Search entry.
- A throwaway probe on the unfolded fold emulator (2026-11-22) showed that a ViewModel created in the placeholder is scoped to the logged-in shell entry and survives tab switches, theme changes, fold/unfold and opening then closing a detail, while `rememberSaveable` state there is lost whenever the placeholder leaves composition.
- `ActiveTabScopedSceneStrategy` already limits the list-detail strategy to the active tab's slice of the back stack, so Search's placeholder is never composed on another tab.
- `searchPostsV2` and `searchStarterPacksV2` are in atproto-kotlin 10.0.0. The unspecced discovery endpoints have no generated service; `DefaultSearchFeedsRepository` already calls one with hand-written serializers.
- Free-text v2 search was not exercised live (the public endpoint returns 403 without auth).

## Goals / Non-Goals

**Goals:**
- One source of truth for what was searched, readable from inside and outside the Search entry.
- No duplicate network calls between the result tabs and the Summary.
- Pane state that survives the placeholder leaving composition.

**Non-Goals:**
- A new scene strategy or any change to `MainShell`.
- Persisting a submitted search across process death.
- Caching result pages beyond the first.

## Decisions

1. **Explore and Summary render in the existing `detailPlaceholder` slot.** Alternatives: a `SupportingPaneSceneStrategy` (needs its own back-stack entry and competes for the Search entry) and Search owning both panes (a pushed post could no longer replace only the right half). The slot already has the right back behaviour.
2. **A singleton `SubmittedSearchStore` holds only what was searched**: query, filters (including sort), selected tab, and whether the filter sheet is open, exposed as one `StateFlow`. `SearchViewModel` is the only writer of the query and resets the store on clear, on creation and in `onCleared`. Filters, tab and the sheet flag are changed through the store's methods by whichever surface the user touched. Alternatives: a mirror store with four writers (stale-state risk, and empty Summary sections until a tab is visited) and lending the pane the Search entry's ViewModel store (holds a navigation-library owner outside its entry). There is no event channel: "open the filter sheet" is a boolean in state.
3. **The filter sheet is hosted by the Search screen, not the Posts tab**, so it can open from the Summary whichever tab is selected.
4. **`SearchFirstPages` shares first-page requests.** One entry per result type, keyed on the full search tuple, holding the in-flight `Deferred` (started in the application scope) so two callers arriving together await one call. Failures are not cached. A refresh replaces the entry and bumps a `version` state that the pane observes. Later pages go straight to the repositories.
5. **`SearchPaneViewModel` (shell-scoped) holds pane-only state**: the mode (`Explore` or `Summary`) and one scroll position per mode. The pane saves its scroll position to the ViewModel on dispose and restores it on entry.
6. **Explore content lives in a singleton `ExploreRepository`** exposing one `StateFlow` and the follow / pin / dismiss mutations. `DiscoverViewModel` becomes a projection of it and is instantiated twice: in the Search entry (phone landing, list-pane Trending) and in the pane. Follow state therefore agrees across a fold. This replaces `SuggestionsRepository`.
7. **Unspecced endpoints use hand-written `@Serializable` request and response classes** that reuse generated view types and declare required fields without defaults, so schema drift fails one section loudly instead of rendering empty.
8. **All in-memory search and Explore data is dropped on session change** (sign-out or a different DID).
9. **Date filter.** "All time" is the default and sends `allTime=true`; presets send `since`; a custom range sends ordered `since`/`until` in the device time zone. The "Recent" window is not offered because its length is undefined.
10. **Back handling.** One handler in the Search screen, enabled only when Search is the top entry of its tab's stack, results are showing and the bar is collapsed. It reads the back stack, not the pane layout.
11. **Starter packs get their own `:feature:starterpack:{api,impl}` module** and are the last feature slice, so they can be dropped without touching the rest.
12. **`FeedView` takes the detail-pane role**, as `Hashtag` does. Its only push sites today are in Search.

## Risks / Trade-offs

- [v2 plain-query relevance could be worse than v1] → A signed-in live comparison is the first step of the migration task; if clearly worse, stop and decide before continuing.
- [`allTime=true` on every search may be slower] → Measured in the same live check; the fallback is a bounded default with a visible Date chip.
- [Unspecced endpoints can change without notice] → Strict decoding fails only the affected section, which is then hidden; the rest of the surface keeps working.
- [Two extra requests per search on wide windows (feeds, packs)] → Accepted; they are reused if the tabs are visited, and phones make none.
- [A second writer for filters and tab weakens the single-writer rule] → Limited to state mutations through the store's own methods; the query keeps one writer.
- [ViewModel teardown on logout is unverified on a device] → Bench sign-out is fixed first and used to confirm `onCleared` runs.
- [`FeedView` in the detail pane could surprise a future push site] → Push sites are listed in the PR and re-checked at implementation.

## Migration Plan

The change lands as one `gh stack` (bd epic `nubecita-7d66`), one PR per task group in `tasks.md`, merged atomically so `main` never holds a half-built Search. Rollback is reverting the squash commits of the stack. No data migration.
