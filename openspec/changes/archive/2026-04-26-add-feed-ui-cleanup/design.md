## Context

Edge-to-edge is opt-in for the project — `MainActivity.onCreate` calls `enableEdgeToEdge()` and the manifest declares `android:windowSoftInputMode="adjustResize"` globally. The `FeedScreen` was the first screen to ship with content under both system bars, but its inset propagation regressed during the four-state-variant rewrite (PR #48): the `Scaffold { padding -> when (viewState) { ... } }` block consumes `padding` only inside the `InitialLoading` branch's LazyColumn. The other three branches (`Empty`, `InitialError`, `Loaded`) ignore it entirely, drawing under the status bar (and, on gesture-nav devices, the bottom system bar).

The typography regression is older. `RobotoFlexFontFamily` was declared with one `Font(resId = R.font.roboto_flex)` entry. Roboto Flex is a variable font with a `wght` axis spanning 100-1000; the asset has no embedded fallback, so without per-weight `FontVariation.Settings`, the Compose text engine renders all weights at the variable's default position (the file's `fvar` defaultValue, which on this asset appears heavy/~700). Result: `bodyLarge` (declared `FontWeight.Normal`) renders identically to `titleMedium` (declared `FontWeight.SemiBold`) and `headlineSmall` (`FontWeight.Bold`).

The `AuthorLine` row was lifted from an early prototype where handles were short fixture strings. Real-world `*.bsky.social` handles routinely run 25-35 characters, which exceeds the screen-width budget after subtracting avatar (40dp) + gutters (32dp) + display name. The composite `Text(stringResource(R.string.postcard_handle_and_timestamp, handle, timestamp))` has no `maxLines` or `weight` constraint, so the timestamp wraps to a second visual line.

The reference design at `openspec/references/design-system/compose/ui/components/ThreadPost.kt:94-105` is the canonical fix for the row layout. The reference also shows the self-thread connector pattern, but that's a *new capability* tracked separately under `nubecita-m28.2` and explicitly out of scope here.

## Goals / Non-Goals

**Goals:**
- Eliminate the four edge-to-edge inset gaps so feed content respects safe areas while system bars stay translucent.
- Restore the typography scale by honoring Roboto Flex's wght variable axis.
- Make `AuthorLine` render correctly for the long-handle common case.
- Maintain the killer-feature edge-to-edge look — system bars do NOT get an opaque scrim; content scrolls under them via `contentPadding`-based insets.
- Zero behavior change to data flow, MVI contracts, or atproto plumbing.

**Non-Goals:**
- TopAppBar / bottom nav / FAB placement and their inset handling — owned by the app-shell epic (`nubecita-cif`).
- Status-bar translucent scrim. The reference design adds a `Spacer` with a vertical gradient to keep system-bar icons legible over busy content; that ships with the TopAppBar in the app shell, not here. Without a TopAppBar today, the inset-padded content never reaches the status bar visually anyway.
- Self-thread connector line through the avatar gutter (`nubecita-m28.2`).
- Link card and quoted-post embed renders (`nubecita-aku` / `nubecita-6vq`). Currently these still render as the `Unsupported` chip — visual cleanup of the chip itself is in scope only insofar as the type fix improves it for free.
- Bottom-nav inset handling — there is no bottom nav today.
- Light-theme polish. The screenshot diff is dark-only because the device is in dark mode; both themes get rebaselined, but no per-theme color decisions are revisited.

## Decisions

### Decision 1: Pad via `Scaffold` `innerPadding` propagation, not `Modifier.safeDrawingPadding()` on the screen root

**Choice**: `FeedScreen`'s `Scaffold { padding -> ... }` already provides the correct inset values. Each state branch consumes `padding` either as `LazyColumn(contentPadding = padding)` (scrollable surfaces) or `Modifier.padding(padding)` (full-screen state composables) — but in the LazyColumn case we ALSO add `Modifier.consumeWindowInsets(padding)` on the parent so a future nested scrollable doesn't double-inset.

**Alternatives considered:**
- *`Modifier.safeDrawingPadding()` on screen root.* Rejected: the Scaffold also reserves space for its own children (snackbar host today, TopAppBar tomorrow). Bypassing the Scaffold's `innerPadding` and using `safeDrawingPadding()` would either clip the snackbar or double-pad the future TopAppBar. The skill explicitly says "Choose only one method to avoid double padding" and recommends Scaffold + innerPadding when available.
- *`Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` on the LazyColumn.* Same problem. Sidesteps Scaffold's coordination.

### Decision 2: `LoadedFeedContent`'s `LazyColumn` uses `contentPadding`, not `Modifier.padding`

**Choice**: Inset values flow into the list via `contentPadding`. The list's parent `PullToRefreshBox` and `LazyColumn` itself stay `fillMaxSize()` so they EXTEND under the system bars; only the list's first/last items are pushed inward by `contentPadding`. This is the canonical pattern for edge-to-edge scrollable surfaces.

**Alternatives considered:**
- *Apply `Modifier.padding(padding)` to the parent of the LazyColumn.* The skill explicitly warns against this for lists: "this clips the content and prevents it from scrolling behind the system bars." We want the bsky-app feel where post content scrolls behind a translucent status bar, not abruptly stops at it.

### Decision 3: `PullToRefreshBox` indicator is inset by the status bar via `state` parameter, not via wrapping the box

**Choice**: Material 3's `PullToRefreshBox` accepts a `state` parameter; the indicator's vertical offset can be biased by passing the top inset to the indicator's `position` parameter (or on Material3 ≥ 1.4 by using the `indicator` slot with a wrapping `Modifier.offset`). For now: rely on the LazyColumn's `contentPadding.top` — the indicator anchors to the list's content top, which gets pushed below the status bar, so the indicator naturally sits below as well. Verify experimentally on a 120Hz device and adjust with an explicit indicator slot only if needed.

**Alternatives considered:**
- *Wrap `PullToRefreshBox` in `Modifier.padding(top = ...)`.* Cuts off the box at the status bar — kills the edge-to-edge look and clips the dragging indicator's overshoot.

### Decision 4: `RobotoFlexFontFamily` declares 4 weight Fonts (Normal=400, Medium=500, SemiBold=600, Bold=700); skip the unused 100/200/300/800/900

**Choice**: `Type.kt` only references `FontWeight.Normal`, `FontWeight.SemiBold`, and `FontWeight.Bold`; we add `Medium=500` defensively because Material 3 internals (e.g., button labels) sometimes resolve to it. Each Font passes `variationSettings = FontVariation.Settings(FontVariation.weight(N.toFloat()))`. The same physical .ttf is referenced four times.

**Alternatives considered:**
- *Declare all 9 standard weights.* Wastes Compose's per-weight font cache slots for weights that no `Type.kt` style requests. Easy to add later if a weight becomes needed.
- *Declare per-weight extracted .ttf files.* Defeats the purpose of using a variable font; bloats the APK.

### Decision 5: `AuthorLine` uses three Texts in a Row with `weight(1f, fill = false)` on the displayName + handle pair

**Choice**: Mirror the reference's structure but with sturdier truncation:

```kotlin
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(displayName, fontWeight = FontWeight.SemiBold, style = ...titleMedium, maxLines = 1, overflow = Ellipsis)
    Text("@$handle", style = ...bodySmall, color = onSurfaceVariant, maxLines = 1, overflow = Ellipsis,
         modifier = Modifier.weight(1f, fill = false))
    Spacer(Modifier.weight(1f))                              // pushes timestamp right
    Text(timestamp, style = ...bodySmall, color = onSurfaceVariant, maxLines = 1)
}
```

The handle gets `weight(1f, fill = false)` so it shrinks first when space is constrained, while the displayName stays at its intrinsic width. The `Spacer(Modifier.weight(1f))` between handle and timestamp absorbs all remaining horizontal space, pinning the timestamp to the row's right edge regardless of how the displayName + handle pair lay out.

**Alternatives considered:**
- *Reference's structure verbatim.* The reference uses `Spacer(Modifier.width(6.dp))` between displayName and handle and a free-floating `Spacer(Modifier.weight(1f))` before timestamp, with no truncation on either Text. That works for short handles in static screenshots but doesn't gracefully degrade.
- *Single composite Text with `BulletSpan`-style separators.* Can't truncate the handle independently without a custom `Layout`.

### Decision 6: `FeedEmptyState` and `FeedErrorState` accept a `contentPadding: PaddingValues = PaddingValues()` parameter

**Choice**: New optional parameter. Default `PaddingValues()` keeps preview / screenshot-test callers untouched. `FeedScreen` passes `padding` from the Scaffold lambda. Inside the composables, `Modifier.padding(contentPadding)` is applied to the root Column.

**Alternatives considered:**
- *Wrap the dispatch site in `Box(Modifier.padding(padding)) { FeedEmptyState(...) }`.* Adds a Box layer per dispatch — cheap but extra. The parameter approach makes the inset contract explicit at the composable boundary.

### Decision 7: `LoginScreen` wraps in `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`

**Choice**: Per the skill's "RIGHT" pattern for IME-on-Scaffold, `contentWindowInsets = WindowInsets.safeDrawing` includes IME insets, and the content lambda's `innerPadding` is applied via `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)`. The existing Column inside takes the inner padding and continues with its `verticalArrangement = Arrangement.spacedBy(..., Alignment.CenterVertically)`.

**Alternatives considered:**
- *Plain `Modifier.imePadding()` on the Column.* Works for IME but doesn't handle status / nav bars. The Scaffold approach handles both in one call.
- *`Modifier.safeDrawingPadding()` on the root Column.* Equivalent, but commits to non-Scaffold structure — the login screen will likely grow auxiliary elements (sign-in branding, OAuth button row) that benefit from a Scaffold's TopAppBar slot later.

## Risks / Trade-offs

- **Existing screenshot baselines invalidate.** Every screenshot test that exercises a Composable using Roboto Flex re-renders with the corrected weights. Expected churn: ~30+ baseline images regenerated across `:designsystem` and `:feature:feed:impl`. → Mitigation: audit the new baselines visually before committing; the corrected typography is the goal, but a malformed `FontVariation.weight()` value would silently fall back and the test wouldn't fail.
- **Per-weight Font declarations may not strip from APK.** If R8 / proguard doesn't dedupe the same `R.font.roboto_flex` resource referenced 4× in the FontFamily, no harm — the resource itself is one file. The Font INSTANCES are 4 small wrappers, ~tens of bytes each. → Mitigation: spot-check `:app:bundleRelease` size before/after.
- **`FontVariation.weight(N)` requires API 26+ for variable-axis support.** Project minSdk is 24. → Mitigation: AndroidX Compose's `FontVariation.weight()` falls back gracefully on pre-26 — the platform ignores `variationSettings` and renders the asset at its default weight, which is what's happening today. Net regression: zero on API 24-25 (same broken state); fix on 26+.
- **`LoadedFeedContent`'s `consumeWindowInsets(padding)` interacts with `PullToRefreshBox`'s drag overlay.** If the box itself doesn't propagate the consumed insets, the indicator's drag offset could double-inset. → Mitigation: empirically verify on a Pixel 8 (gesture nav) and a Pixel 4 (3-button); fall back to passing `contentPadding.top` explicitly to the box's indicator slot if the indicator drifts.
- **`weight(1f, fill = false)` on the handle with ellipsis depends on Row measuring the displayName at its intrinsic width first.** Compose Row's measurement for weighted children is well-defined for this case, but a future migration to a custom `Layout` would need to preserve the precedence. → Mitigation: screenshot test specifically for the long-handle case (a 35-char handle with a short display name and a 50-char display name).
- **No `LoginScreen` screenshot tests exist today.** This change adds the Scaffold wrapper but has nothing to baseline against. → Mitigation: add minimal screenshot tests for `LoginScreen` (empty + typed states, light + dark) as part of this change.

## Migration Plan

Single PR, no flag. The bugs are user-visible from the very first commit on the branch; staged rollout would only delay the fix. Rollback is `git revert` of the squash-merge if regression appears.

## Open Questions

- Does `Modifier.consumeWindowInsets(padding)` on a `PullToRefreshBox` parent interfere with the indicator's anchor? Tested empirically on the smoke build — adjust Decision 3 if needed.
- Should the `postcard_relative_time` resource also gain a content-description for talkback (e.g., "posted 5 hours ago" instead of "5h")? Filed a follow-up consideration; not blocking this change.
