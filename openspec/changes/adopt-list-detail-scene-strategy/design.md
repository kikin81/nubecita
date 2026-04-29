# Design — adopt-list-detail-scene-strategy

## Problem framing

`MainShell` adapts chrome (bar↔rail) but renders content single-pane at every width. Tap a post on a tablet → push full-screen → wasted half-window during and after the transition. We want true two-pane content on medium/expanded with the same back stack and back-press semantics on compact, without per-host scaffold duplication across Feed and (future) Profile.

## Approach: scene strategy at the nav layer, not scaffold at the screen layer

Two viable Material adaptive primitives could deliver list-detail; we chose the navigation-layer one.

### Approach A (rejected) — per-host `NavigableListDetailPaneScaffold`

Wrap each list host (Feed, future Profile) in `NavigableListDetailPaneScaffold` from `material3-adaptive-navigation`. The list pane is the lazy column; the detail pane renders `PostDetailRoot(uri)`. Internal `ThreePaneScaffoldNavigator` owns its own back stack.

**Why rejected:**
1. **Two parallel back stacks.** The host's pane navigator and `MainShellNavState` both track navigation, with neither being authoritative. System-back delegation, process-death restoration, and tab-switch back-stack preservation all need bridging code per host.
2. **Detail entry is duplicated.** `PostDetailRoot(uri)` would be referenced as a Composable in two host modules instead of being a single `EntryProviderInstaller @MainShell` registration. Doesn't fit the two-shell qualifier convention codified in `add-adaptive-navigation-shell`.
3. **No transparent path to "tap a reply inside detail to drill deeper."** Drill-down requires pushing onto the pane navigator, which a tab-internal `UiEffect` cannot reach without a new CompositionLocal.

### Approach B (chosen) — `ListDetailSceneStrategy` on the inner `NavDisplay`

Add `sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>())` to `MainShell`'s inner `NavDisplay`. Mark each list-pane entry with `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = …)` and each detail-pane entry with `metadata = ListDetailSceneStrategy.detailPane()`. The strategy walks the back stack, sees consecutive list+detail entries, and composes them as a `TwoPaneScene` on medium/expanded; on compact it falls through to single-pane (top entry only).

**Why chosen:**
1. **One source of truth.** `MainShellNavState` remains the only back stack. Process-death + saved-state already wired through the existing nav3 decorators apply transparently to whatever the strategy renders.
2. **Native fit with the two-shell qualifier convention.** PostDetail (Phase 2) becomes a single `EntryProviderInstaller @MainShell` registration with `detailPane()` metadata. No bridging types, no second navigator.
3. **Drill-down is a normal `UiEffect.Navigate`.** Tapping a reply inside `PostDetail` emits `Navigate(PostDetail(replyUri))`, which `LocalMainShellNavState.current.add(...)` consumes — the strategy handles whether the new entry stacks on top within the detail pane (medium/expanded) or pushes a new full-screen entry (compact).
4. **Symmetric across feeds.** Feed (this change) and the future Profile feed both decorate their entry with `listPane{}` metadata — no scaffolding code duplicated, no Composable duplicated. Each just supplies its own placeholder.

## Topology

The `ListDetailSceneStrategy` lives **only on the inner `NavDisplay`** inside `MainShell`. The outer `NavDisplay` in `app/Navigation.kt` (Splash → Login → Main) does not get a strategy — none of its entries are list-pane or detail-pane candidates. PostDetail will be a `@MainShell`-qualified entry, not `@OuterShell`.

```
app/Navigation.kt
  NavDisplay [outer]                     ← no scene strategy
    Splash, Main(MainShell), <login flow>

shell/MainShell.kt
  NavigationSuiteScaffold (bar/rail)
    NavDisplay [inner]                   ← + ListDetailSceneStrategy
      Feed         (listPane { FeedDetailPlaceholder() })
      Search       (no metadata)
      Chats        (no metadata)
      Profile      (no metadata yet — added in profile:impl epic)
      <PostDetail> (no entry yet — added in Phase 2/3)
      <Settings>   (no metadata; full-screen sub-route)
```

Entries with no metadata are rendered single-pane regardless of window class. Entries with `listPane{}` metadata render single-pane on compact, and as the left pane on medium/expanded *when the next entry on the stack is `detailPane()` — otherwise the placeholder fills the right pane*.

## Detail placeholder

When the back stack is `[Feed]` (no detail) on medium/expanded, the strategy needs to render *something* in the detail pane. The list-pane metadata supplies this via the `detailPlaceholder: @Composable () -> Unit` parameter.

`FeedDetailPlaceholder` is a centered, `surfaceContainerLow`-tinted Composable showing:

- A Material Symbol (decorative `null` content description) — `Icons.AutoMirrored.Outlined.Article` or similar.
- One `bodyLarge` line: "Select a post to read".

It is defined in `:feature:feed:impl` (not `:designsystem`) until a second list-pane host needs the same shape. This follows the project's "three similar lines is better than a premature abstraction" rule. When `:feature:profile:impl` adds its own `listPane{}` metadata, we promote.

The placeholder strings are i18n-ready: `R.string.feed_detail_placeholder_select`. No icon localization needed (decorative).

## System back behavior

`ListDetailSceneStrategy`'s default back semantics on medium/expanded with both panes visible: back pops the detail entry, returning the list pane to "show placeholder" state — *without* popping the list entry. On compact with the same back stack, back pops the detail entry, returning to the full-screen list. Both match Bluesky/X convention.

We adopt the strategy default. No override required. The existing `MainShellNavState.removeLast()` `onBack` handler does the right thing in both cases because `removeLast()` on the back stack just pops the top entry; the strategy interprets the resulting `[Feed]` stack as "list pane with placeholder" on expanded and "Feed full-screen" on compact.

The other existing back behaviors (`add-adaptive-navigation-shell` requirements 7–9: pop sub-route within tab; non-Feed top-level → switch to Feed; Feed top-level + empty stack → exit) are unaffected — they apply to non-pane stacks and the strategy passes those through.

## Screenshot test strategy

The change adds three screenshot tests at three window-size classes, exercising the **only** stack state observable in this change (since no PostDetail exists yet): `[Feed]`.

| Window class | Width | Expected layout |
|---|---|---|
| Compact | 360dp | Bar at bottom; Feed fills content area; placeholder NOT composed |
| Medium | 700dp | Rail at start; Feed in left pane; `FeedDetailPlaceholder` in right pane |
| Expanded | 1200dp | Rail at start; Feed in left pane; `FeedDetailPlaceholder` in right pane |

Tests live in `:app/src/screenshotTest/.../shell/MainShellListDetailScreenshotTest.kt` alongside `MainShellChromeScreenshotTest`. Each test uses `@PreviewScreenSizes` or an explicit `@Preview(device = "spec:width=…dp,height=…dp")` to fix the window class, hands a fake `EntryProviderInstaller` set into `MainShellChrome` (the existing Hilt-free wrapper), and captures the rendered tree. The fake installers register a Feed entry with the real `listPane{}` metadata; this validates the metadata is wired correctly without requiring a fully-stood-up Hilt graph.

A separate non-screenshot unit test asserts that `MainShell`'s `NavDisplay` invocation includes `sceneStrategies` — guards against accidental regression to single-pane during refactor.

## Risks & mitigations

### R1 — Alpha artifact: `adaptive-navigation3`

The dependency is in alpha. Sonatype audit (`pkg:maven/androidx.compose.material3.adaptive/adaptive-navigation3`) returns: not malicious, not end-of-life, license unspecified at this maturity. No specific-version Trust Score available — typical for new alpha lines. The base `androidx.navigation3:navigation3-{runtime,ui}` artifacts are already alpha (`1.1.1`) and in production use, so the marginal supply-chain delta is small.

**Mitigation:**
- Pin the version explicitly in the catalog (no version range).
- During implementation, prefer the latest alpha that is *aligned* with our `nav3Core = "1.1.1"`. Per the AndroidX adaptive release notes, mismatched alphas can produce metadata-key drift. Re-check the alignment in task 1.1.
- After this change ships, watch for `adaptive-navigation3` graduation to `beta`/stable — re-run `sonatype-guide` and re-evaluate any newly-disclosed CVEs at that point. Tracked informally; no scheduled task.

### R2 — `listPane{}` metadata depends on Feed entry being top-of-stack-or-followed-by-detailPane

If a future change inadvertently pushes a non-`detailPane()` entry on top of Feed (e.g. Settings full-screen pushed via Feed's UiEffect), the strategy's behavior on medium/expanded is to render Feed as left pane and Settings as the "detail" — visually wrong.

**Mitigation:** Settings (and any analogous full-screen sub-route) must NOT carry `detailPane()` metadata. Without metadata, the strategy treats the entry as a single-pane scene that obscures both list and detail. This is the correct behavior. We document the rule in the spec delta ("Entries that should fully obscure the list pane SHALL NOT supply `detailPane()` metadata") so reviewers catch any mistaken metadata.

### R3 — Saved state interplay with the strategy

`ListDetailSceneStrategy` ships its own `rememberSaveable` keying for pane state (focus, divider position). Process-death restoration must put the user back in the same pane configuration.

**Mitigation:** verified during implementation via the existing `MainShellPersistenceTest` (instrumented). Add one new scenario: rotate from medium → compact → medium with `[Feed]` on the stack and assert the placeholder reappears post-rotation.

## Sonatype / supply-chain audit

Performed pre-design, results captured in proposal.md "Dependencies / system" and R1 above. Recheck planned at non-alpha promotion.

## Open questions

None blocking implementation. Two non-blocking notes for follow-on phases:

- **Phase 2** (PostDetail `:api` stub): the `detailPlaceholder` lambda will continue to render the Phase-1 placeholder until Phase 3 ships real content. No change to this design.
- **Phase 3** (PostDetail `:impl`): drill-down (tap reply → push `PostDetail(replyUri)`) needs to confirm that consecutive `detailPane()` entries on the stack stack-within-pane on medium/expanded rather than push a new full-screen scene. Evidence from Google's JetCaster sample suggests this works; verify during Phase 3 implementation.
