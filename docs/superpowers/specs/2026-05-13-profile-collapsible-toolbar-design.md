# Profile collapsible toolbar (nubecita-1tc)

**Status:** Design approved · ready to plan
**bd:** nubecita-1tc (epic nubecita-s6p)
**Scope:** `:feature:profile:impl` only · Compact width only
**Date:** 2026-05-13

## Problem

`ProfileScreen` is the only screen in the app with no top app bar. The hero scrolls away with the body, leaving the sticky pill tabs floating naked at the top of the viewport with nothing anchoring them. On Pixel 10 Pro XL the stuck pills also draw into the status bar / camera-cutout area because `LazyColumn.stickyHeader` doesn't honor `contentPadding.top` for the stuck position.

## Goal

Add a `TopAppBar` that:

1. Reserves the status-bar inset at all times so the pills (when stuck) sit below the bar without conditional padding.
2. Cross-fades from invisible → fully visible as the user scrolls past the hero.
3. Once visible, shows the user's display name + handle as a two-line title over a backdrop sampled from the hero's bold gradient.
4. Carries a back-arrow navigation icon when the route was pushed onto a backstack (other-user profiles), no nav icon when sitting at the Profile-tab root (own profile).
5. Gives the sticky pills a continuous gradient-sampled backdrop so the bar + pills read as a single two-row header once the hero is gone.

## Decisions

1. **Profile-only, inline.** The composable lives in `:feature:profile:impl/ui/ProfileTopBar.kt`. No `:designsystem` extraction. PostDetail and Composer use plain `TopAppBar` and don't need the collapse behavior; promotion happens when a second consumer asks for it.
2. **Threshold fade, not M3 scroll behavior.** The bar's structural slot is reserved at all times; only the backdrop + title alpha animate from 0 → 1. We don't use `MediumTopAppBar` / `TopAppBarScrollBehavior.exitUntilCollapsed` — the bd explicitly wants the bar invisible while the hero is in view, and M3's compression always renders the title in some form.
3. **Backdrop = `rememberBoldHeroGradient(...).top`.** The existing `:designsystem/hero/BoldHeroGradient` already exposes a `rememberBoldHeroGradient(banner, avatarHue): BoldHeroGradientStops` API with shared Palette caching. The bar calls the same composable the hero calls; cache hit returns the same `top` color the hero is painting at its top edge. No new sampling API; no extra Palette work.
4. **Pills' stuck backdrop = same `gradient.top`, no separator.** The pills row paints the same color as the bar, so bar + pills read as one continuous surface. A 1dp shadow at the pills' bottom edge separates the header block from the scrolling body.
5. **Navigation icon: `MainShellNavState.canPop`.** Other-user profiles get a back arrow; own-profile root does not. Mirrors `PostDetailScreen`'s back-arrow behavior. Wired through the existing `LocalMainShellNavState` plumbing — the bar reads `canPop` from composition, no new ViewModel surface.
6. **Compact-only.** Medium-width / two-pane adaptive variants are explicitly out of scope (locked by the bd). The bar lives inside the existing single-pane Compose tree; no `NavigationSuiteScaffold` interactions.

## Architecture

`ProfileScreenContent` already owns a `Scaffold` (snackbarHost + content), and `listState` is already hoisted by `ProfileScreen` and threaded through. We add a `topBar` slot to the existing Scaffold — no new Scaffold wrapper.

```
ProfileScreen
  ├─ reads LocalMainShellNavState → derives onBack: (() -> Unit)?
  └─ ProfileScreenContent(... onBack = onBack ...)
       └─ Scaffold(snackbarHost, topBar = { ProfileTopBar(...) })  // topBar slot is new
            └─ PullToRefreshBox
                 └─ LazyColumn(contentPadding = paddingValues, modifier = Modifier.consumeWindowInsets(paddingValues))
                      ├─ item("hero")                                                 // unchanged
                      ├─ stickyHeader("tabs") → Box(gradient backdrop) { ProfilePillTabs }  // backdrop wrap is new
                      └─ items(tab body)                                              // unchanged
```

One new file, three existing files touched:

| File | Change |
|---|---|
| `ProfileScreen.kt` | Read `LocalMainShellNavState.current`; compute `onBack: (() -> Unit)? = if (navState.canPop) { { navState.pop() } } else null`. Pass `onBack` into `ProfileScreenContent`. |
| `ProfileScreenContent.kt` | Add `onBack: (() -> Unit)?` parameter. Add `topBar = { ProfileTopBar(header = state.header, listState = listState, onBack = onBack) }` to the existing Scaffold. Add `Modifier.consumeWindowInsets(padding)` on the LazyColumn so the existing `contentPadding = padding` flow doesn't double-count the bar's reserved inset. Wrap the existing `ProfilePillTabs` call inside `stickyHeader` in a `Box` whose background color is `rememberBoldHeroGradient(state.header?.bannerUrl, state.header?.avatarHue ?: 0).top` (transparent fallback when header is null). |
| `ui/ProfileTopBar.kt` (new) | The collapsing bar. ~100 lines. |
| `ProfileScreenContentScreenshotTest.kt` | Add `listStateSeed: LazyListState?` parameter on the screenshot host so new "scrolled" fixtures can pre-seed `LazyListState(firstVisibleItemIndex = 1)`. |

## Component contract: `ProfileTopBar`

```kotlin
@Composable
internal fun ProfileTopBar(
    header: ProfileHeaderUi?,           // null → no title content, transparent backdrop
    listState: LazyListState,           // scroll source
    onBack: (() -> Unit)?,              // null → no navigation icon
    modifier: Modifier = Modifier,
)
```

Internals:

- Computes alpha via `derivedStateOf { computeBarAlpha(listState, fadeWindowPx) }` — the `derivedStateOf` wrapper means the bar only recomposes when alpha actually crosses to a new discrete value, not on every scroll frame. (See "Compose performance" section.)
- Title slot: `Column { Text(header.displayName ?: header.handle, titleMedium); Text("@${header.handle}", labelSmall) }` wrapped in `Modifier.alpha(alpha)`. Falls back to displayName-or-handle so the bar mirrors what the hero shows.
- Backdrop: `TopAppBar(colors = topAppBarColors(containerColor = gradient.top.copy(alpha = alpha)))`. The container color is dynamic; the rest of `TopAppBarColors` defaults apply.
- Navigation icon: when `onBack != null`, `IconButton(onClick = onBack) { NubecitaIcon(ArrowBack) }`. Wrapped in `Modifier.alpha(alpha)` so it cross-fades with the rest of the bar.

## Threshold heuristic

```kotlin
internal fun computeBarAlpha(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    fadeWindowPx: Int,                  // tunable; default = ~half the hero height in px
): Float =
    when {
        firstVisibleItemIndex > 0 -> 1f
        fadeWindowPx <= 0 -> 0f         // hero hasn't measured yet
        else -> (firstVisibleItemScrollOffset.toFloat() / fadeWindowPx).coerceIn(0f, 1f)
    }
```

`fadeWindowPx` source: derived from `listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.let { (it * fadeMultiplier).toInt() }` inside the same `derivedStateOf` block, with `0` (alpha clamped to 0) as the seed value until the hero has measured. Wrapping in `derivedStateOf` (not `remember`) is deliberate: the hero can re-measure when the banner image loads or the bio expands, and we want the threshold to track the new height. `fadeMultiplier = 0.5f` so the bar is fully visible by the time the hero is half-scrolled — empirically this matches "avatar leaves viewport." The constant is a private `const val` in `ProfileTopBar.kt`, easy to tune during on-device verification.

## Compose performance

- `ViewerRelationship` precedent: the new `ProfileTopBar` parameter `header: ProfileHeaderUi?` is already `@Stable` (data class with String/Long/Int fields, all `val`).
- `derivedStateOf` for the alpha computation — without it, every scroll frame would mark `ProfileTopBar` as needing recomposition even when alpha is unchanged.
- `rememberBoldHeroGradient(...)` already memoizes its `BoldHeroGradientStops` and the cache is keyed on banner URL — bar + hero hit the same cache entry, single Palette extraction per profile.
- Backdrop color flows through `TopAppBarDefaults.topAppBarColors(containerColor = ...)`. Hoist the colors object via `remember(stops, alpha) { TopAppBarDefaults.topAppBarColors(containerColor = stops.top.copy(alpha = alpha)) }` so it's only re-allocated when alpha or the gradient stops actually change, not on every recomposition triggered by an unrelated parent.

## Status-bar inset

`Scaffold` consumes `WindowInsets.statusBars` for its topBar slot automatically. `TopAppBar` already inserts `windowInsetsTopHeight` padding internally. Net effect:

- Bar's top edge starts under the cutout; visible bar content starts below the status bar.
- `Scaffold.content`'s `paddingValues.top` includes the bar's full height (status-bar inset + 64dp).
- Pills sit inside that padded content area, so when stuck they snap to `paddingValues.top` — flush against the bar's bottom, never under the cutout.
- LazyColumn's `Modifier.consumeWindowInsets(paddingValues)` prevents double-counting when the existing `contentPadding` is applied.

The bd's quick-fix paths (`Modifier.windowInsetsPadding(statusBars)` on the pills, `StatusBarProtection`) are explicitly **not** added — the Scaffold structure makes them unnecessary, exactly as the bd predicts.

## Error handling

| `state.header` | `state.headerError` | Bar title | Bar backdrop |
|---|---|---|---|
| `null` | `null` | empty | transparent (alpha = 0) |
| `null` | non-null | empty | transparent |
| non-null | * | `displayName` + `@handle`, alpha-modulated | `gradient.top.copy(alpha = α)` |

The bar is always laid out so the inset reservation is unconditional. No new `ProfileEffect` cases; the existing `ShowError` snackbar flow is unchanged.

## Testing

### Screenshot tests

1. **Regen the existing baselines.** Adding `Scaffold` + `TopAppBar` shifts the hero down by `statusBarInset + 64dp`. Every existing `ProfileScreenContent*Screenshot` baseline regenerates in one pass via `:feature:profile:impl:updateDebugScreenshotTest`. The PR description **must** explicitly call this out so the reviewer reads the diff as expected layout shift, not regression.
2. **`ProfileTopBarScreenshotTest.kt` (new)** — bar in isolation, light + dark, four α stops:
   - α = 0.0, no nav icon (transparent inset reservation)
   - α = 0.5, no nav icon (mid cross-fade)
   - α = 1.0, no nav icon (own-profile root, fully visible)
   - α = 1.0, with nav icon (other-user pushed, fully visible)
3. **New `ProfileScreenContent` "scrolled" fixtures** — expose `listStateSeed: LazyListState?` parameter on the host. Two new compact-only baselines: hero-scrolled-away light + dark, showing bar fully faded in over gradient backdrop, pills sitting flush below with continuous gradient surface.

### Unit tests

`ProfileTopBarTest.kt` (or under an existing test file): pure-function test on `computeBarAlpha(firstVisibleItemIndex, firstVisibleItemScrollOffset, fadeWindowPx)`:

- `(0, 0, 100) → 0f` (no scroll)
- `(0, 50, 100) → 0.5f` (mid-window lerp)
- `(0, 100, 100) → 1f` (window full, alpha clamp)
- `(0, 200, 100) → 1f` (overshoot clamp)
- `(1, 0, 100) → 1f` (hero scrolled away)
- `(0, 0, 0) → 0f` (hero hasn't measured)

### Out of scope

- **Instrumentation tests on the actual scroll-driven fade.** ScrollGesture + Palette extraction in androidTest is flaky; the unit test on `computeBarAlpha` + the four α-stop screenshot baselines together cover the contract.
- **Medium-width adaptive variants.** The bd locks Compact-only. Bead F's ListDetail wiring stays untouched.
- **Animation easing.** Linear lerp on alpha is fine; no Easing curve needed for a fade. If on-device verification surfaces a "snap" feel, a follow-up bd can swap in `Easing.FastOutSlowIn`.

## Migration / rollout

Single PR. No feature flag — the change is visible-by-construction (the bar appears, the hero shifts down). The screenshot regen + new fixtures land in the same PR so review can compare expected vs actual visually.

## Open questions

None. All design decisions resolved through brainstorm before this spec was written.

## References

- bd nubecita-1tc — original ticket with detailed scope notes and on-device verification findings.
- PR #158 (Bead D) — the non-collapsing hero this bd builds on.
- `openspec/changes/add-profile-feature/design.md` Decision 8 — the explicit deferral this bd carves out.
- `:designsystem/hero/BoldHeroGradient.kt` — the gradient + Palette-cached `rememberBoldHeroGradient` API the bar reuses.
- `:feature:postdetail:impl/PostDetailScreen.kt` — the navigation-icon + `Scaffold(topBar = TopAppBar(...))` precedent.
