## Context

The Bluesky AT Protocol exposes a centralized notification index: each interaction (like / repost / follow / reply / mention / quote / verified / etc.) is indexed by the AppView and exposed via three XRPC operations — `app.bsky.notification.listNotifications` (paginated list), `app.bsky.notification.getUnreadCount` (badge counter), and `app.bsky.notification.updateSeen` (mark-read sentinel). All three are already generated in atproto-kotlin 9.0.1's `NotificationService`, so this change has **no library blocker**.

Nubecita's existing `MainShell` is a four-tab `NavigationSuiteScaffold` (Feed | Search | Chats | Profile). Tab-internal navigation flows through `LocalMainShellNavState`'s `add(NavKey)` API; ViewModels never inject the navigator and emit `UiEffect.NavigateTo(target)` instead. A Hilt-singleton `DeepLinkRouter` already exists for Activity→Shell handoffs (used by push payloads via `AtUriToDeepLink`). The composer overlay establishes a precedent for app-scoped state holders read by Composables via `CompositionLocal`.

The meta-epic [[nubecita-1fy]] is staged in four slices. This change is **slice 1** — the polling-based in-app surface. Slices 2 (FCM delivery), 3 (notification channels), and 4 (pro tier features) ship under separate epics and are explicitly excluded here. The in-app surface is, however, the **foreground-fallback target** for slice 2: when an FCM payload arrives while the app is foregrounded, the `FirebaseMessagingService` suppresses the OS notification and the in-app surface's next 60-second poll picks up the new item naturally. No coupling code lives in this change.

## Goals / Non-Goals

**Goals:**

- Ship a new 5th top-level destination "Notifications" between Search and Chats, with an unread-count badge on the bottom-nav icon.
- Paginate `listNotifications` via the SDK's `paginate` Flow helper. Support pull-to-refresh and tail append.
- Aggregate same-reason notifications into multi-actor rows (Bluesky-style avatar stacks).
- Render a mini preview of the subject post (text + thumbnail grid) for content-bearing reasons (like / repost / reply / quote / mention), batched via a single `getPosts(uris[])` per page.
- Single-select filter chips (All | Mentions | Reposts | Follows | Likes) above the list.
- Mark-read on tab exit (preserves visual unread distinction during the visit).
- Deep-link routing for every known `reason` value; safe no-op for `Unknown` (forward-compat).
- App-foregrounded 60-second polling of `getUnreadCount`, lifecycle-aware and single-flight.
- Standard test coverage per the repo convention: unit tests, previews, screenshot baselines.

**Non-Goals:**

- FCM push delivery (slice 2). No `FirebaseMessagingService`, no `registerPush` call, no notification channels. The foreground-fallback behavior is realized through this slice's existing polling — no coupling code is added here.
- Notification channels and per-reason OS-level routing (slice 3).
- The pro-tier feature surface: quiet hours, keyword muting, smart priority, digest mode (slice 4).
- The `priority: bool` toggle on `listNotifications` (Bluesky's "only people you follow" filter). Filed as a follow-up bd issue.
- Activity subscriptions (`putActivitySubscription` / `listActivitySubscriptions`). Separate feature.
- Per-reason in-app settings toggles ([[nubecita-1fy.2]], separate change).
- Multi-account routing ([[nubecita-1fy.3]], separate change). Today we assume the auth layer holds one session.
- Persistent caching of notification list to disk (Room). Slice 1 is in-memory only — refresh-from-network on each tab open. A future change can layer Room caching atop the repository interface without changing the VM contract.

## Decisions

### D1. Module split: separate `:feature:notifications:api` and `:feature:notifications:impl`

**Decision**: Two new Gradle modules following the existing repo convention. `:api` exposes only `NotificationsTab : NavKey` + the qualifier-tagged `@MainShell EntryProviderInstaller` `@Provides`. `:impl` holds `NotificationsScreen`, `NotificationsViewModel`, repository, mapper, and the Hilt module providing the installer.

**Alternatives considered**:

- Single `:feature:notifications` module — simpler scaffolding. Rejected: cross-feature modules that need to push the Notifications tab onto the back stack would have to depend on the impl module, breaking the api/impl isolation that the rest of the codebase (feed, search, chats, profile, postdetail) follows. Consistency wins.
- Folding into `:feature:profile` as a sub-route — rejected by the meta-epic's tab-placement decision (5-tab layout, not drawer/sub-route).

### D2. MVI shape: sealed `NotificationsLoadStatus` lifecycle, flat `items` / `activeFilter` / `cursor`

**Decision**: `NotificationsState` carries a flat `ImmutableList<NotificationItemUi> items`, a `NotificationFilter activeFilter`, a `String? cursor`, a `Boolean hasMore`, and a `NotificationsLoadStatus` sealed sum (`Idle | InitialLoading | Refreshing | Appending | InitialError`). The screen projects state to a `NotificationsScreenViewState` (Empty / InitialLoading / InitialError / Loaded) via a private `toViewState()` extension — identical pattern to `FeedScreenViewState`.

**Alternatives considered**:

- Wrap remote data in `Async<NotificationsPage>` — rejected by the project's MVI convention (no `Async<T>` at the VM→UI boundary).
- Flat-only boolean flags (`isLoading: Boolean`, `isRefreshing: Boolean`, `isAppending: Boolean`) — rejected because these states are mutually exclusive and the sealed sum prevents invalid combinations (`isRefreshing=true` AND `isAppending=true`) by construction. The convention permits a sealed status sum exactly for this case.

### D3. Aggregation: client-side, in the mapper, reason-conditional

**Decision**: The lexicon returns a flat per-event list. The mapper groups **engagement-style** events (`like`, `like-via-repost`, `repost`, `repost-via-repost`) by `(reason, reasonSubject)` into one `NotificationItemUi.Aggregated` row. `follow` (where `reasonSubject` is null) aggregates by `(reason, sameCalendarDay)`. **Content-bearing** events (`reply`, `quote`, `mention`, `subscribed-post`) and **rare per-actor** events (`starterpack-joined`, `verified`, `unverified`, `contact-match`, `Unknown`) always render as `NotificationItemUi.Single` — even when their `reasonSubject` would collide — because the row's load-bearing content is the *actor's new post* (the `uri` field, which is unique per event) rather than the `reasonSubject` (which would be the user's own post for reply/quote/mention and thus collide unhelpfully). The UI's avatar-stack renderer caps visible avatars at 5; the chevron sheet shows the full actor list. Aggregation runs per-page (no cross-page merging) — simpler and matches Bluesky's behavior.

**Alternatives considered**:

- Flat 1:1 row-per-event — rejected per the brainstorm decision (popular posts flood the list).
- Cross-page merging (re-aggregate when a new page lands and contains same-reason events bleeding into the prior page) — rejected as overkill for slice 1. The visual seam between pages is acceptable; if it becomes a complaint, revisit.
- Server-side aggregated endpoint — does not exist in the lexicon.

### D4. Subject-post hydration: batched `getPosts(uris[])` per page, populated lazily into the mapper

**Decision**: After `listNotifications` returns a page, the repository collects all unique post URIs that appear as `reasonSubject` (for like / repost / like-via-repost / repost-via-repost) and as `uri` for the new content (reply / quote / mention). It issues one `getPosts(uris = […])` call (cap 25 per call — the lexicon's max), maps the response into a `Map<AtUri, PostUi>`, then passes that to the mapper. The mapper attaches the hydrated `PostUi?` to each `NotificationItemUi`. URIs that fail to resolve (deleted, hidden, etc.) get a `null` subject — the row still renders, just without the preview block.

**Alternatives considered**:

- Render preview from the notification's `record` JsonObject field directly (it contains the new post's record for reply/quote/mention) — rejected because `record` is just the raw record (text + facets) without embed hydration. Image embeds need a separate fetch through the blob CDN regardless. Better to take one batched `getPosts` round-trip and get fully-hydrated `PostUi` (text + embeds + facets) for every preview slot.
- Per-row lazy fetch (defer `getPosts` until the row scrolls into view) — rejected for now. The page-size cap of 50 fits within one `getPosts` call (capped at 25, so worst case is 2 calls per page). Adding lazy-fetch complexity for marginal bandwidth savings isn't worth it in slice 1.

### D5. Polling: app-foreground, 60s, single-flight, exponential backoff on errors

**Decision**: A Hilt-singleton `NotificationsUnreadCountStore` exposes a `StateFlow<Int>`. A `ProcessLifecycleOwner`-scoped observer (registered in `NubecitaApplication.onCreate`) runs `lifecycle.repeatOnLifecycle(STARTED) { while (isActive) { fetch(); delay(60.seconds) } }`. Single-flight: if a previous request hasn't returned when the next tick fires, the new tick is skipped (via `Mutex.tryWithLock`). Errors trigger exponential backoff (60s → 120s → 240s → cap 300s, reset on success). The bottom-nav `BadgedBox` reads from the store directly via Hilt injection at the `MainShell` layer (no VM intermediary — the count is shell chrome, not feature state).

**Alternatives considered**:

- Tab-active-only polling — rejected by the brainstorm (badge goes stale across other tabs).
- 30s cadence — overruled by the user; 60s is more conservative on cellular and battery.
- WorkManager-driven background polling — wrong tool. Background is push's job. Foreground polling stays in `viewModelScope` / `LifecycleScope`, no WorkManager.

### D6. Mark-read on tab exit, not tab open

**Decision**: The screen Composable fires `NotificationsEvent.TabExited` to the VM when:

1. `LocalMainShellNavState.current.topLevelKey` transitions away from `NotificationsTab` (observed via `snapshotFlow { mainShellNavState.topLevelKey }`).
2. The screen leaves composition entirely (process death, logout).

The VM handles `TabExited` by calling `repository.markSeen(now)`, then optimistically zeroes the badge via the store. Failure is silently swallowed — the next 60-second poll corrects the count if `updateSeen` failed.

**Alternatives considered**:

- Mark-read on tab open — rejected by the brainstorm: badge clears instantly but the unread visual distinction (tonal-tint background + "New" divider) is lost from the user's perspective.
- Per-row mark-read — rejected: `updateSeen` is a single global sentinel timestamp (no per-row API in the lexicon). Per-row tracking would require client-side bookkeeping that the next refresh would overwrite anyway.

### D7. Filter chips: 5 single-select chips above the list, lexicon `reasons[]` param mapping

**Decision**: An M3 `FilterChip` row (horizontal-scrolling `LazyRow` with horizontal contentPadding, non-sticky above the LazyColumn). Chips: All, Mentions, Reposts, Follows, Likes. Single-select, with selection lifted to `NotificationsState.activeFilter: NotificationFilter`. The repository maps each filter to its `reasons[]` array:

```kotlin
enum class NotificationFilter(val reasons: ImmutableList<String>?) {
    All(reasons = null),
    Mentions(reasons = persistentListOf("mention", "reply", "quote")),
    Reposts(reasons = persistentListOf("repost", "repost-via-repost")),
    Follows(reasons = persistentListOf("follow")),
    Likes(reasons = persistentListOf("like", "like-via-repost")),
}
```

`reasons` is `public` (not `internal`) because cross-module consumers in `:feature:notifications:impl` read it; Kotlin `internal` is module-private and would not be visible there. `ImmutableList<String>?` matches the immutable-collections convention in `:data:models`.

Switching filters resets cursor to null and re-fetches.

**Alternatives considered**:

- 7 chips (one per reason category) — rejected by the user on screen-real-estate grounds.
- M3 segmented buttons (All / Mentions only) — rejected as too narrow; loses Reposts / Follows / Likes filtering.
- Sticky chip row (pins under the TopAppBar even while the list scrolls) — defer; non-sticky is simpler and the page is short enough that the chips never scroll out of practical reach.

### D8. Deep-link routing inside the VM, emitted as `UiEffect.NavigateTo(NavKey)`

**Decision**: `NotificationsViewModel`'s `RowTapped` reducer resolves the tap target by `reason`:

| Reason | Target NavKey |
|---|---|
| `like`, `repost`, `like-via-repost`, `repost-via-repost` | `PostDetail` derived from `reasonSubject` (AT-URI → DID + rkey) |
| `reply`, `quote`, `mention` | `PostDetail` derived from the notification's `uri` (the actor's new post) |
| `follow`, `contact-match` | `Profile(handle = actor.did)` |
| `starterpack-joined` | `Profile(handle = actor.did)` |
| `verified`, `unverified` | `Profile(handle = self.did)` |
| `subscribed-post` | `PostDetail` from `reasonSubject` |
| `Unknown` | No-op effect (debug-log only) |

Emits `NotificationsEffect.NavigateTo(target)`. The screen Composable collects effects in a single outer `LaunchedEffect` and calls `LocalMainShellNavState.current.add(target)` — matching the established cross-feature navigation pattern.

**Alternatives considered**:

- VM injects `Navigator` directly — rejected by the project's MVI convention (VMs cannot reach `MainShellNavState`, only Composables can via the `CompositionLocal`).
- Defer routing to the screen by passing a sealed `NotificationTapIntent` to the screen via state — rejected because the AT-URI → NavKey translation needs the same logic the VM already uses for state mapping. Centralizing in the VM keeps the screen dumb.

### D9. Design system: 4 new icons + codepoint correction for `Notifications`

**Decision**: Add the following `NubecitaIconName` entries (alphabetical insertion per the file's convention):

- `AlternateEmail` (``) — Material Symbol `alternate_email`, for mention reason.
- `ExpandMore` (``) — Material Symbol `expand_more`, for the aggregated-row chevron.
- `FormatQuote` (``) — Material Symbol `format_quote`, for quote reason.
- `Verified` (``) — Material Symbol `verified`, for verified / unverified reasons.

Correct the existing `Notifications` codepoint from `` (`notifications_none`) to `` (`notifications`) — the canonical glyph that responds to the FILL axis with the activity dot. Re-run `./scripts/update_material_symbols.sh` after the enum edits.

Add a new `NotificationReasonIcon(reason: NotificationReason, modifier: Modifier = Modifier)` composable to `:designsystem` that maps reason → icon enum + tint color. Tints use the M3 color scheme tokens:

| Reason group | Tint token |
|---|---|
| Like family | `colorScheme.error` (close to magenta in our scheme) or a dedicated `likeAccent` |
| Repost family | A dedicated `repostAccent` (green) |
| Follow / starterpack / contact-match | `colorScheme.primary` |
| Mentions / replies / quotes | `colorScheme.onSurfaceVariant` |
| Verified | `colorScheme.primary` |
| Subscribed-post / unknown | `colorScheme.onSurfaceVariant` |

If the brand scheme doesn't yet expose `likeAccent` / `repostAccent` tokens, add them as `extendedColorScheme` extensions (existing pattern in `:designsystem`).

**Alternatives considered**:

- Re-use `Favorite` for all the like-family reasons without adding `AlternateEmail` / `FormatQuote` — rejected: mentions and quotes are semantically distinct from likes and deserve their own glyphs. The brainstorm screenshot shows them as distinct icons.
- Inline `Icon(painter = ImageVector.vectorResource(...))` in the screen — rejected: the design system already vendors all icons via `NubecitaIconName` + `NubecitaIcon(name = ...)`. New icons go through the same channel.

### D10. Data model: `NotificationItemUi` as a sealed `Single | Aggregated` in `:data:models`

**Decision**: Add `NotificationItemUi` (sealed Single | Aggregated), `NotificationReason` (enum with `Unknown` fallback), and `NotificationFilter` (enum) to `:data:models`. Both `Single` and `Aggregated` carry `actors: ImmutableList<AuthorUi>` (length 1 for Single, length ≥ 2 for Aggregated). `subjectPost: PostUi?` is null when the reason has no associated post (follow / verified). Provide a `NotificationItemUiFixtures` object mirroring `PostUiFixtures.kt` for preview / test data.

**Alternatives considered**:

- One concrete `NotificationItemUi` data class with a `nullable singleActor` field — rejected because Single and Aggregated have distinct UI affordances (chevron only on Aggregated). Sealed types make the rendering branch type-safe.
- Put the model inside `:feature:notifications:impl` — rejected because previews in `:designsystem` (e.g. for `NotificationReasonIcon`) and downstream test code benefit from a stable `:data:models` location.

### D11. Tab re-tap = scroll to top (consistent with Feed / Profile)

**Decision**: The screen reads `LocalTabReTapSignal` and collects emissions via `LaunchedEffect`, calling `lazyListState.animateScrollToItem(0)` on each. Same hook-up Feed and Profile use. ViewModels do not subscribe.

**Alternatives considered**: None — this is established convention.

## Risks / Trade-offs

- **[Aggregation seams between pages] → Mitigation**: Document as known limitation. If two same-reason events for the same subject land on opposite sides of a page boundary, they render as two adjacent multi-actor rows instead of one merged row. Visually identical-looking; not a correctness bug. A future change can layer cross-page merging if the seam is reported.
- **[Subject-post hydration cost] → Mitigation**: One batched `getPosts(≤25 uris)` per page is well within the 100 req/s ratelimit. If page-size grows past 25 in the future (the lexicon allows up to 100), split into two parallel `getPosts` calls.
- **[Polling drift if 60s `delay` is suspended during deep sleep] → Mitigation**: `repeatOnLifecycle(STARTED)` cancels and restarts the coroutine on lifecycle transitions, so post-resume the loop kicks off immediately (fresh tick) before settling into 60s cadence again. No special handling needed.
- **[Mark-read race with concurrent push] → Mitigation**: `updateSeen` is idempotent (a sentinel timestamp). If a push arrives between the user opening the tab and the tab-exit `updateSeen` call, the user has already seen it visually (it's rendered in the list); marking-seen is correct. If a push arrives *after* `updateSeen` is sent, the next 60s poll picks up the new unread count and re-badges.
- **[Mock-engine fixtures need updating for tests] → Mitigation**: Add fixtures under `androidTest/assets/notifications/` covering each reason, an aggregated page, an empty page, and an error response. Mirrors how Feed and Postdetail already do it.
- **[New 5-tab layout pushes screenshot baselines] → Mitigation**: Re-baseline `MainShell` screenshots in this PR. Use the `update-baselines` label on the PR per the project convention.

## Migration Plan

No production migration — this is a net-new feature surface. Rollout is gated by Play Store release cadence. If the polling load proves problematic (unlikely at 60s), the `NotificationsUnreadCountStore` can be feature-flagged off via a build-config constant — falling back to badge-on-tab-open. Not building a flag in initial ship; revisit only if telemetry shows pain.

## Open Questions

1. **Repost vs. like accent colors in the M3 Expressive scheme.** The brand scheme currently doesn't expose `repostAccent` / `likeAccent` tokens. Decision needed during implementation: (a) add them as extended colorScheme tokens (Material 3 supports `dynamicColorScheme` extensions), or (b) hardcode the two accents in `NotificationReasonIcon` as literal `Color(0xFF...)` constants with a TODO. Default to (a) unless it expands token-management work beyond what slice 1 can absorb.

2. **Empty-state and error-state illustrations.** The design system has existing empty-state assets for Feed and Profile; reusing one (e.g. the "no posts" illustration with a notifications-flavored caption) is the path of least resistance. Decide during the UI task whether to commission a notification-specific illustration or reuse.

3. **Verbiage for the "and N others" pluralization in aggregated rows.** Android string resources support quantity plurals via `<plurals>`. Decide between (a) "alice and 1 other liked your post" / "alice and 3 others liked your post" (simple), (b) "alice, bob and 1 other liked your post" / "alice, bob and 3 others …" (show 2 then collapse), or (c) all-collapse "alice and 3 others …" regardless of actor count. Bluesky uses pattern (a) per the screenshot; defaulting to (a) unless brand voice argues otherwise.
