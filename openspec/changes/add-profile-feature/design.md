## Context

The Profile screen is the missing leaf of MainShell's four top-level destinations. Today:

- `:feature:profile:api` ships a single hybrid `NavKey`: `Profile(handle: String? = null)`. Same key serves both the "You" top-level tab (`handle = null`) and any cross-tab navigation to another user's profile (`handle = "alice.bsky.social"` pushed onto the active tab's back stack from a feed handle tap). The `Settings` `NavKey` lives alongside it because Settings is a sub-route of the You tab, per `add-adaptive-navigation-shell/design.md` Decision D6.
- `:app`'s `MainShellPlaceholderModule` registers a `@MainShell`-qualified `EntryProviderInstaller` for `Profile(handle = null)` and another for `Settings`. Both render "coming soon" placeholders. The `app-navigation-shell` spec captures this as the canonical pattern (`Empty tabs render :app-side placeholder Composables`).
- Feed handle taps already navigate via `FeedEffect.NavigateToAuthor(handle) → LocalMainShellNavState.current.add(Profile(handle))`, which currently routes to the same placeholder.
- `MainShell.kt`'s inner `NavDisplay` already attaches `rememberListDetailSceneStrategy(directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(...))`. Any list-pane-tagged entry pushed from the profile body lands in the detail pane on Medium / Expanded widths and full-screen on Compact — no new scaffolding needed.

The data layer is also partially in place:

- `:core:feed-mapping` was extracted by `add-postdetail-m3-expressive-treatment` and owns the atproto-wire-type → UI-model conversion for posts, authors, viewer state, and embeds. The profile's author-feed mapper consumes it directly (same shape as the postdetail mapper).
- The atproto Kotlin SDK's lexicon ships `app.bsky.actor.getProfile` and `app.bsky.feed.getAuthorFeed` (the latter with the `filter` parameter that drives Posts / Replies / Media bodies). Notification / chat lexicons are partially missing (per `reference_atproto_kotlin_notification_lexicon_gap.md`) — verified that the two endpoints we need are NOT in the gap.

The challenge is breadth, not depth. The wireframes show three render modes, three tab bodies, three adaptive layouts, four interactive actions, and a scroll-collapsing header — most of which is shippable later. The goal of this design is to pick the cuts that let the You tab become real today and leave clean seams for everything deferred.

## Goals / Non-Goals

**Goals:**

- Replace the `:app`-side placeholder for `Profile(handle = null)` and `Settings` with a real `:feature:profile:impl` module — the You tab and feed handle taps both reach a rendered profile.
- Cover both `Profile(handle = null)` (own) and `Profile(handle = "...")` (other) in this epic; the only branch is the actions-row content.
- Render the full hero (avatar + name + handle + bio + meta + inline stats), all three pill tabs, all three tab bodies (Posts / Replies / Media), in light and dark themes, at Compact and Medium widths.
- Anchor on the "Bold Expressive" hero variant from the wireframes but preserve the user's banner palette via `androidx.palette.graphics.Palette` so the bold treatment still carries identity.
- Plug post taps inside the profile body into the existing `ListDetailSceneStrategy` — Medium+ widths see thread detail in the right pane with no profile-side scaffolding.
- Land the design-system additions (Fraunces + JetBrains Mono fonts, `BoldHeroGradient`, `ProfilePillTabs`) behind clean `:designsystem` APIs so the feature module never touches Palette or font-loading machinery directly.
- Establish a screenshot-test contract that catches hero / tabs / actions-row regressions across Compact + Medium and light + dark.

**Non-Goals:**

- Custom UI primitives. Everything routes through M3 / Compose / our existing design system. (Established convention from `add-postdetail-m3-expressive-treatment`.)
- Scroll-collapsing hero, `TopAppBarScrollBehavior` integration, backdrop blur on the bar. Sticky tabs only — collapse is a separate follow-up.
- Expanded 3-pane with side panel (suggested follows + pinned feeds). Expanded falls back to the Medium 2-pane.
- Any real write — Follow / Unfollow, Edit profile, Message routing. All three ship as stubbed affordances that surface a `ProfileEffect.ShowMessage("Coming soon")` snackbar.
- A real Settings screen. The Settings sub-route ships as a one-screen stub with Sign Out only.
- Building a separate `:feature:settings:impl` module. Settings rides inside `:feature:profile:impl` until a future epic justifies a split (see the `feature/profile/api/Settings.kt` KDoc — established precedent).
- Custom shape morphing, custom motion graphs, hand-rolled UI primitives — same prohibition as `add-postdetail-m3-expressive-treatment`.

## Decisions

### Decision 1: Bold-derived hero — Palette extraction from the banner, banner pixels dropped

**Choice:** Render the hero as the wireframe's "Bold Expressive" variant: an M3 surface with a derived gradient backdrop, an 80–96dp circular avatar on a 4dp `surface` ring at elevation 2, the user's name in Fraunces 600 with the variable `SOFT` axis at 70, and the handle in JetBrains Mono. The user's `profile.banner` blob is **not rendered as the backdrop**. Instead, on first display, the banner image is decoded once, `androidx.palette.graphics.Palette` extracts a swatch off the main thread, and the gradient is derived from the swatch (specifically: vibrant + dark-muted as the two anchor stops, falling back to muted + light-muted if vibrant is absent). When the user has no banner, the gradient is derived from `avatarHue` (a deterministic hue computed from `did` + first character of `handle`).

The Palette result is cached per banner blob `cid` so navigating away and back doesn't re-decode. The cache lives behind the `BoldHeroGradient` composable in `:designsystem` — the feature module never imports `androidx.palette.*` directly.

**Why this over alternatives:**

- *A — Classic banner (literal banner pixels as the hero backdrop with a 0.55 black scrim)* — the most Bluesky-native look and what the prompt actually describes by default. Rejected because (1) contrast is unpredictable against light/busy banners and demands a Palette-driven scrim adjustment anyway, and (2) the design lead's stated preference is the Bold variant for visual coherence with the rest of the M3 Expressive surface vocabulary. Palette-extraction work would be needed for the scrim regardless; doing it once for color derivation is cheaper than doing it twice (color + scrim) plus carrying the full banner image.
- *B — Pure bold ignoring the banner entirely (gradient = `primaryContainer` everywhere)* — simplest, but throws away the user's only customization signal. A user who curates their banner gets zero return in the new profile screen. Rejected on identity grounds.
- *C — Show the banner AND apply the bold treatment (banner as background + bold card overlaid)* — visually busy, doubles the assets loaded per profile, and undermines the "Bold Expressive" aesthetic the wireframe is committing to. Rejected.

The Bold-derived approach lets the user's banner influence the screen's color story without ever rendering the banner literally — preserves identity at the palette level, keeps the Bold visual contract clean.

### Decision 2: Per-tab `TabLoadStatus` sealed sum, three independent lifecycles inside one `ProfileScreenViewState`

**Choice:** Define a single `ProfileScreenViewState` that owns three independent `TabLoadStatus` fields — one each for Posts, Replies, Media — plus a `selectedTab: ProfileTab` flat field. The state shape is:

```kotlin
data class ProfileScreenViewState(
    val header: ProfileHeaderUi?,                // null = header still loading
    val ownProfile: Boolean,
    val viewerRelationship: ViewerRelationship,  // None | Self | Following | NotFollowing
    val selectedTab: ProfileTab = Posts,
    val postsStatus: TabLoadStatus,
    val repliesStatus: TabLoadStatus,
    val mediaStatus: TabLoadStatus,
)

sealed interface TabLoadStatus {
    data object Idle : TabLoadStatus
    data object InitialLoading : TabLoadStatus
    data class Loaded(
        val items: ImmutableList<TabItemUi>,
        val isAppending: Boolean,
        val isRefreshing: Boolean,
        val hasMore: Boolean,
        val cursor: String?,
    ) : TabLoadStatus
    data class InitialError(val error: ProfileError) : TabLoadStatus
}
```

This matches the CLAUDE.md MVI rule for mutually-exclusive lifecycles (idle / initial-loading / refreshing / appending / initial-error are not coexistable), and the header's load lifecycle stays as a simple nullable (it's a single one-shot load with no append).

**Why this over alternatives:**

- *Flat booleans (`isLoadingPosts`, `isLoadingReplies`, `isRefreshingMedia`, `errorPosts`, …)* — explicitly forbidden by the CLAUDE.md MVI rule for mutually-exclusive states. Would also produce an exponential combinatorics of legal-but-invalid states (`isLoadingPosts = true AND errorPosts != null`) that the reducer would have to keep coherent by hand. Rejected.
- *One sealed `ProfileLoadStatus` for the whole screen* — would force "Posts is loading" to mean "the whole profile is loading" and blank the header during a tab refresh. The wireframes explicitly show the header staying put while a tab body loads. Rejected.
- *Three nested ViewModels (one per tab) feeding a parent `ProfileViewModel`* — over-engineered for the per-tab pagination shape, which is identical across all three tabs (cursor-paged `getAuthorFeed` with a different `filter`). Single VM owning three lifecycles is the right primitive. Rejected.

The three lifecycles are genuinely independent (each tab paginates on its own; `Refresh` on one tab doesn't reset the others). Keeping them as three named sealed fields makes invalid combinations unrepresentable while keeping the screen-level state flat.

### Decision 3: Three eager `getAuthorFeed` calls on profile open, not lazy-on-tab-select

**Choice:** When the screen opens (or when the user pulls to refresh), `ProfileViewModel` issues three concurrent `getAuthorFeed` calls — one per `filter` (`posts_no_replies`, `posts_with_replies`, `posts_with_media`) — plus one `getProfile` call for the header. Each tab transitions from `Idle` → `InitialLoading` → `Loaded`/`InitialError` independently. Switching tabs is then instant (the body is already there).

**Why this over alternatives:**

- *Lazy-on-tab-select (only fetch a tab body when the user activates the tab)* — saves two requests on profile open. Rejected because (1) the wireframes show the active-tab body fully rendered before the user interacts — eager fetch matches the visual model, (2) Bluesky's tab-switch latency is the most-complained-about pattern in third-party clients, (3) one of the four concurrent requests is already required (Posts is the default tab; the header is required regardless), so the marginal cost is two extra background requests on a low-frequency surface.
- *Sequential — fetch the active tab + header first, then prefetch the other two after the screen is interactive* — adds complexity (cancellation on tab switch mid-prefetch, ordering rules for what to fetch first if the user starts on a non-default tab) for marginal request-budget savings. Rejected.

The atproto-kotlin SDK runs each `getAuthorFeed` independently — the four concurrent coroutines launch from `viewModelScope` and converge on the state via independent `setState` updates. Per-tab pagination (`LoadMore`) is lazy and per-tab as expected.

### Decision 4: `AuthorProfileMapper` inline in `:feature:profile:impl` — no `:core:profile-mapping` extraction

**Choice:** The atproto `app.bsky.actor.defs#profileViewDetailed` → `ProfileHeaderUi` mapping lives inline in `:feature:profile:impl/data/`. No new `:core:profile-mapping` module. Author-feed body mapping reuses `:core:feed-mapping` directly (same shape as `:feature:postdetail:impl/data/PostThreadMapper`).

**Why this over alternatives:**

- *Pre-emptively extract `:core:profile-mapping` for future consumers (e.g., a hover card, a notification author chip)* — speculative. YAGNI rule from CLAUDE.md ("don't add features, refactor, or introduce abstractions beyond what the task requires... three similar lines is better than a premature abstraction"). Rejected until a second consumer surfaces.
- *Put the profile mapping in `:core:feed-mapping`* — wrong-module fit; `:core:feed-mapping` is scoped to the feed-view-post graph, not user profiles. Rejected.

The extraction precedent set by `add-postdetail-m3-expressive-treatment` (which extracted `:core:feed-mapping` only after a second consumer appeared) is the model here. The profile mapper stays internal until the same pressure exists.

### Decision 5: Stubbed actions row via `ProfileEffect.ShowMessage` — same shape as the eventual real wiring

**Choice:** The actions row renders the design's three buttons (own: Edit; other: Follow + Message; both: overflow) at full visual fidelity but tapping any of them emits a `ProfileEffect.ShowMessage("Coming soon")`. The screen Composable's effect collector surfaces the message via a `SnackbarHostState`. The ViewModel's event handlers (`FollowTapped`, `EditTapped`, `MessageTapped`) exist but each handler currently just emits `ShowMessage`.

When real writes ship in follow-up epics, the handler bodies replace `ShowMessage` with the real call (network + optimistic state update + error-snackbar fallback) — the effect channel and the event surface stay the same. No screen Composable changes required.

**Why this over alternatives:**

- *Render the actions row with no on-click (visually disabled / greyed out)* — confusing UX (looks broken), and screenshot-test fixtures would lose the visual identity of the row. Rejected.
- *Don't render the actions row at all until writes land* — leaves a noticeable hole in the hero and makes the hero geometry change between this epic and the follow-up. Rejected.
- *Render the actions row but route taps to a placeholder dialog instead of a snackbar* — blocking modal for a "coming soon" stub is hostile UX. Rejected.

The snackbar-stubbed pattern matches the precedent set by `add-postdetail-m3-expressive-treatment`'s "fullscreen viewer coming soon" snackbar — non-blocking, dismissible, doesn't visually claim the screen.

### Decision 6: Settings stub lives inside `:feature:profile:impl`, no `:feature:settings:impl` module yet

**Choice:** The Settings sub-route's stub Composable + its `@MainShell @IntoSet` `EntryProviderInstaller` live inside `:feature:profile:impl`. The stub is a one-screen "Settings — coming soon" with a Sign Out button that clears the OAuthSession via `:core:auth`. When a real Settings epic lands, `:feature:settings:impl` graduates to its own module and the placeholder migrates per the same `:api`-only-stubs rule from CLAUDE.md.

**Why this over alternatives:**

- *Land a separate `:feature:settings:impl` skeleton module now* — premature for a one-screen stub; the `feature/profile/api/Settings.kt` KDoc explicitly says "Settings... shares the profile feature's epic... will graduate to its own module if and when growth justifies a split." Rejected.
- *Leave the Settings placeholder in `:app`'s `MainShellPlaceholderModule`* — fine in isolation but creates the inconsistency that `Profile(handle = null)` resolves to `:feature:profile:impl` while `Settings` still goes through `:app`. Cleaner to bundle.

This decision drives the `app-navigation-shell` spec delta: both `Profile` and `Settings` placeholders disappear from `:app` in lockstep with this epic.

### Decision 7: Post taps inside the body emit `NavigateToPost` and rely on the existing `ListDetailSceneStrategy`

**Choice:** The post list inside each tab body is registered with `ListDetailSceneStrategy.listPane{}` metadata on the entry provider, so the strategy treats the profile screen itself as the list pane on Medium / Expanded widths. Tapping a post inside the body emits `ProfileEffect.NavigateToPost(postUri)`. The screen's effect collector calls `LocalMainShellNavState.current.add(PostDetailRoute(postUri))`. The strategy decides the rest: on Compact the detail entry renders full-screen via the existing `PostDetail` provider; on Medium+ it lands in the right pane next to the profile.

**Why this over alternatives:**

- *Build a Profile-specific list-detail scaffolding (e.g., a `ListDetailPaneScaffold` instance owned by the profile screen)* — duplicates infrastructure that MainShell already provides at the inner-NavDisplay level. Rejected; the strategy is shell-scoped, not feature-scoped.
- *Push a Profile-specific detail key (e.g., `ProfileDetailPlaceholder`)* — would require the profile screen to own the detail pane's empty-state Composable. We already have a global empty-state contract for the detail pane (`Pick a post to read its thread` per the `app-navigation-shell` spec). Reusing it is correct. Rejected.

This decision also covers cross-tab handle taps: when the user taps an author handle inside a profile post, the screen emits `NavigateToProfile(handle)` and the collector calls `mainShellNavState.add(Profile(handle = ...))`. The active tab's back stack accumulates the second profile on top of the first — same affordance as the feed, no new code path.

### Decision 8: Sticky tabs — no scroll-collapsing hero in v1

**Choice:** The hero renders inside the body's `LazyColumn` as a non-sticky item (or as a Column above the LazyColumn — both shapes are explored in implementation). The pill tabs render as a sticky `item` inside the LazyColumn (`stickyHeader { ProfilePillTabs(...) }`), so they remain visible as the user scrolls the body. No `TopAppBarScrollBehavior`, no surface-container backdrop blur, no title-slot compression of name + handle.

**Why this over alternatives:**

- *Full scroll-collapsing hero per the prompt (`TopAppBarScrollBehavior` + custom collapsing modifier + backdrop blur)* — moderately complex, requires a custom motion graph for the collapse curve, intersects with the `ListDetailSceneStrategy`'s pane sizing on Medium widths in nontrivial ways. Defer to a follow-up epic.
- *No sticky tabs (tabs scroll away with the hero)* — confusing UX once the user has scrolled past the hero; the active tab is no longer visible. Rejected.

Sticky tabs without collapse is the minimum that keeps the screen usable. The scroll-collapsing pass is its own bd issue under the Profile epic.

## Risks / Trade-offs

- **Palette extraction off the main thread.** Risk: a slow Palette extraction blocks the gradient render and the hero shows the avatarHue fallback briefly before swapping. → Mitigation: extract on `Dispatchers.Default` from `BoldHeroGradient`'s `LaunchedEffect`; the fallback IS the initial state, so the swap is one-directional and never flickers back. Cache per `banner.cid` so repeat opens are instant.
- **Three concurrent `getAuthorFeed` calls on profile open.** Risk: four-way request fanout (header + three tabs) hits rate limits or blocks lower-priority traffic. → Mitigation: launch all four from `viewModelScope` but tolerate independent failure (per-tab `InitialError` doesn't fail the header or sibling tabs). If telemetry shows rate-limit pressure, downgrade to lazy-tab-load in a follow-up.
- **`SOFT` axis on Fraunces.** Risk: not every Compose `Text` rendering path honors variable font axes consistently; Layoutlib in particular may stub variable axes (memory note from `feedback_compose_glyph_iteration_workflow.md` — Layoutlib silently ignores some font features). → Mitigation: real-device verify the `SOFT = 70` render before claiming the bead is done; if Layoutlib doesn't reflect the axis, accept the baseline regen mismatch and update fixtures via real-device screenshots.
- **The actions row stubbed with `ShowMessage`.** Risk: a future write-implementation bead has to migrate the effect channel away from `ShowMessage` to a real `NavigateToFollowConfirmation` or similar — breaking the stub's pattern. → Mitigation: the contract documented in Decision 5 — the event-handler body replaces, the event surface stays. Easy migration.
- **Settings stub clearing the OAuthSession.** Risk: the auth integration is the only "real" interaction in this epic (everything else is a Snackbar); a bug here could lock the user out. → Mitigation: route through `:core:auth`'s existing logout pathway (used by the splash/login flow today). Same call site, same test coverage. No new auth surface.
- **Bold-derived hero invisible at very-light banners.** Risk: a banner with a pastel palette produces a low-contrast gradient and the white name text fails WCAG. → Mitigation: `BoldHeroGradient` enforces a minimum luminance delta between the gradient and the text overlay; if Palette returns swatches that violate the delta, the helper darkens the dominant stop until contrast clears. Captured as part of the `BoldHeroGradient` requirement.
- **Cross-tab handle-tap loop.** Risk: tapping the profile's own handle (e.g., the @handle inside the hero) pushes another `Profile(handle)` on top, creating a loop. → Mitigation: the screen Composable intercepts `NavigateToProfile(handle)` and no-ops when the target equals the current profile's handle. Documented as a scenario in the `feature-profile` spec.

## Migration Plan

This change rolls out in 6 child beads under one Profile epic. The first two (designsystem-only) are independent of the rest; the next four (`:feature:profile:impl`) are sequential.

1. **Bead A** — `designsystem: Fraunces + JetBrains Mono variable fonts`. Adds the two font assets to `:designsystem/src/main/res/font/`, exposes Typography tokens (`displayLarge` family swapped to Fraunces, a new `monoSmall` style for JetBrains Mono with appropriate sizing). No feature consumer yet. Verified via existing typography preview fixtures + new previews for the two added styles.
2. **Bead B** — `designsystem: BoldHeroGradient + ProfilePillTabs`. The two reusable composables behind clean APIs (Palette extraction lives entirely inside `BoldHeroGradient`; `ProfilePillTabs` is a thin wrapper over `PrimaryTabRow`). Previews + screenshot tests in `:designsystem` cover both components in light + dark. No profile consumer yet.
3. **Bead C** — `feature/profile/impl: data layer + ViewModel scaffolding`. New `:feature:profile:impl` module applying `nubecita.android.feature`. `ProfileRepository`, `AuthorProfileMapper` (inline; new), `AuthorFeedMapper` (delegates to `:core:feed-mapping`), `ProfileViewModel`, `ProfileContract` (state + events + effects). No UI yet. Verified via VM unit tests with Turbine. Settings/Profile placeholders in `:app` stay put.
4. **Bead D** — `feature/profile/impl: own-profile screen, Posts tab only`. Adds the screen Composable, hero rendering, meta row, inline stats, actions row (stubbed), sticky `ProfilePillTabs`, Posts body via `LazyColumn` of `PostCard`s. `Profile(handle = null)` is now resolved by `:feature:profile:impl`'s `@MainShell` provider; the corresponding `:app` placeholder for `Profile(handle = null)` is removed.
5. **Bead E** — `feature/profile/impl: Replies tab + Media tab + 3-col media grid`. Adds the two remaining tab bodies (Replies uses the same `PostCard` shape as Posts; Media uses a new `ProfileMediaGrid` composable — `LazyVerticalGrid(GridCells.Fixed(3))`).
6. **Bead F** — `feature/profile/impl: other-user variant + ListDetailSceneStrategy integration + Settings stub`. `handle != null` branch in the screen (Follow/Message stubs, no Edit). Marks the entry provider with `ListDetailSceneStrategy.listPane{}` metadata. Adds the Settings stub Composable + its `@MainShell` provider. Removes the remaining `:app` placeholders (the `Profile(handle = null)`-only placeholder was already gone in Bead D; this finishes Settings).

**Rollback strategy.** Each bead is independently revertable. Beads A and B are token / design-system additions with no behavioral consumer — reverting either is a no-op for the app. Beads C-F each register their own `:app`-side wiring or remove a placeholder; reverting any of D / E / F restores the `:app` placeholder for the corresponding NavKey.

**Sequencing constraint.** Bead C MUST land before Beads D/E/F (the screen depends on the ViewModel). Beads A and B SHOULD land before Beads D/E/F (the design system additions are consumed by the screen) but the dependency is at the type level, not the runtime level — if A/B miss the merge window, the screen Composable can stub the missing tokens temporarily and a follow-up commit wires them in.

## Open Questions

- **`SOFT` axis fallback.** If Layoutlib doesn't honor the Fraunces `SOFT = 70` variable axis (per the existing `feedback_compose_glyph_iteration_workflow.md` precedent that Layoutlib silently ignores some font features), do we (a) accept the Layoutlib render as the screenshot baseline knowing real device differs, (b) regenerate screenshot baselines from real device, or (c) abandon the `SOFT` axis and pin to a static Fraunces SemiBold? Defer to bead implementation; flag if Layoutlib mismatch shows up.
- **Cross-tab self-handle no-op vs. visual pulse.** When the user taps their own handle inside their own profile, the screen no-ops (Decision 5 + Mitigation). Should it produce a visible pulse / shake / haptic to acknowledge the tap, or is silent no-op fine? Defer to Bead F implementation review.
- **Sign Out destination.** The Settings stub's Sign Out button clears the OAuthSession; the splash/login flow then routes the user back to login. Is there a confirmation dialog before sign-out, or does the button fire immediately? Established pattern from the existing logout pathway likely answers this — verify during Bead F.
- **Empty-state copy for each tab.** Posts / Replies / Media each have an empty state (`This user hasn't posted yet`, etc.) and an initial-error state. Final copy can be drafted during implementation; not load-bearing for the design.
