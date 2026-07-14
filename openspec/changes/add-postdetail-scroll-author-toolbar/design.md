# Design — post-detail scroll-reactive author toolbar

## Context

`PostDetailScreenContent` renders an M3 `TopAppBar` with a static "Post" title and a back arrow. Below it, a `LazyColumn` renders the flattened thread: root-most ancestor → … → focus → replies. The list opens at index 0 (the root ancestor), **not** at the focus — there is no `scrollToItem` on entry.

That last fact drives most of this design. The screen is *about* the focus post, but the focus post is not necessarily the first thing on screen.

## Decision 1 — the bar tracks the focus post's author, anchored to the focus card

The toolbar shows exactly one author for the screen's lifetime: `state.focusPost?.author`. It swaps in when **the focus card itself** scrolls up under the bar, not when the list scrolls at all.

On a thread with ancestors, this means the toolbar still reads "Post" through the first stretch of scrolling — the user is reading *up*-thread context, and the page's subject hasn't passed yet. The author appears exactly when the identity the user just scrolled past leaves the viewport. That is the behavior worth having; "any scroll → show author" would fire while the focus post is still below the fold, naming a post the user cannot see.

Rejected: **tracking the topmost visible post's author.** The name would churn through every ancestor and reply author during a fling. It is more "alive" and strictly worse.

Rejected: **a dumb scroll threshold** (`firstVisibleItemIndex > 0`, the `ProfileTopBar.computeBarAlpha` shape). Profile can do this because its hero *is* item 0 — the threshold and the semantic anchor coincide. In post-detail they do not.

## Decision 2 — a boolean threshold, not a scroll-linked continuous value

`ProfileTopBar` interpolates a `Float` alpha from `firstVisibleItemScrollOffset`, so its bar's composition invalidates on essentially every scroll frame. That is affordable there (it drives one `alpha` on an already-simple bar) but it is the wrong default, and 120hz scrolling is a hard project requirement.

Here the derived value is a `Boolean`. Wrapped in `derivedStateOf`, it invalidates the top bar **only when it flips** — once or twice per scroll session, not 120 times per second. The motion itself is then owned by `AnimatedContent` and runs on the animation clock, decoupled from scroll velocity. The bar animates at a consistent speed whether the user flicks or creeps.

The cost is that the animation is not finger-linked (you cannot half-scrub the author in). Threads' transition is not finger-linked either — it springs in once you cross the line. This matches.

## Decision 3 — hysteresis on the threshold

A single crossing point flickers: a user slow-dragging with the focus card's edge parked exactly on the threshold flips the boolean back and forth, re-firing the spring each time.

`shouldShowAuthorInBar` therefore takes **two** thresholds and the current state:

| | value | meaning |
|---|---|---|
| `enterThresholdPx` | 56dp | how far the focus card must be tucked under the bar before the author appears |
| `exitThresholdPx` | 40dp | it must come back out this far before the author leaves |

The 16dp dead band is wide enough to absorb a slow drag and narrow enough to be invisible. 56dp is chosen to approximate the height of the focus card's own author row — the author appears in the bar at roughly the moment the name it duplicates leaves the screen, which is what makes the transition read as a hand-off rather than an arbitrary event.

Keeping both thresholds and `currentlyShown` as *parameters* means the function stays pure and every branch is a table-driven JVM test.

## Decision 4 — the pure function's contract

```kotlin
internal fun shouldShowAuthorInBar(
    focusIndex: Int,              // -1 when no focus row has resolved
    firstVisibleItemIndex: Int,
    focusItemTopPx: Int?,         // focus card's top edge relative to the bar's bottom;
                                  // null when the focus card is not in visibleItemsInfo
    enterThresholdPx: Int,
    exitThresholdPx: Int,
    currentlyShown: Boolean,
): Boolean
```

Resolution order:

1. `focusIndex < 0` → `false`. No focus has loaded (initial load, error, or a thread whose focus row never resolved). The bar reads "Post".
2. `focusItemTopPx == null` → the focus card is off-screen. Disambiguate by index:
   - `focusIndex < firstVisibleItemIndex` → it is scrolled off the **top** → `true`.
   - otherwise → it is still below the fold → `false`.
3. Focus card visible → let `tucked = -focusItemTopPx` (positive once its top edge is above the bar's bottom):
   - `currentlyShown` → stay shown while `tucked > exitThresholdPx`.
   - otherwise → become shown once `tucked >= enterThresholdPx`.

Step 2 is the case that only exists because of the ancestors, and it is the one a naive implementation gets wrong — a `visibleItemsInfo.firstOrNull { it.index == focusIndex }` that returns `null` means *either* "scrolled past" *or* "not reached yet", and those want opposite answers.

## Decision 5 — asymmetric `AnimatedContent`, fixed slide distance, explicit RTL

```kotlin
AnimatedContent(
    targetState = showAuthor && author != null,
    transitionSpec = {
        if (targetState) {
            (slideInHorizontally(spatialSpec) { startOffsetPx } + fadeIn(effectsSpec)) togetherWith
                fadeOut(effectsSpec)
        } else {
            fadeIn(effectsSpec) togetherWith
                (slideOutHorizontally(spatialSpec) { startOffsetPx } + fadeOut(effectsSpec))
        }
    } using SizeTransform(clip = false),
) { showing -> /* author block or "Post" */ }
```

Three things that are easy to get wrong:

- **Only the incoming element moves.** "Post" fades out in place. Two elements travelling simultaneously in a 56dp bar reads busy and they briefly overlap.
- **The slide distance is a fixed 24dp, not a fraction of the content width.** `slideInHorizontally`'s lambda receives the content width, and the obvious `{ -it / 4 }` makes a long display name slide farther — and therefore *faster*, for the same duration — than a short one. Motion speed must not depend on how long someone's name is.
- **`slideInHorizontally` takes raw pixels and does not mirror for layout direction.** The offset's sign is derived from `LocalLayoutDirection`: negative (from the left) in LTR, positive (from the right) in RTL. This is the same class of bug `Modifier.mirror()` exists to prevent for the icons; without it the author slides in from the wrong edge in Arabic/Hebrew.
- `SizeTransform(clip = false)` keeps the bar from animating its width as the title changes size, and keeps the avatar from being clipped mid-slide.

Specs come from `MaterialTheme.motionScheme` — `defaultSpatialSpec()` for the slide, `defaultEffectsSpec()` for the fades. `NubecitaMotionScheme` already collapses to short linear tweens when `isReduced` is set, so the system reduce-motion preference is honoured for free. **Do not hand-roll a `spring()`/`tween()` here**; that would silently opt this surface out of reduce-motion.

## Decision 6 — no ViewModel or contract change

Scroll position is a Compose-runtime concern. It terminates at the screen composable, exactly as the tab-re-tap convention requires (`CLAUDE.md` → "ViewModels do not observe this signal"). Nothing new enters `PostDetailState`; no new `PostDetailEvent`; no new `PostDetailEffect`.

Both inputs the bar needs are already derivable from existing state:

- author → the existing `PostDetailState.focusPost` extension → `.author`
- focus index → `items.indexOfFirst { it is ThreadItem.Focus }`, memoized in a `remember(state.items)`. The `LazyColumn`'s `items(items = items)` maps 1:1 onto `items`, so this index *is* the lazy-list index.

The one structural edit to `PostDetailScreen.kt` is hoisting `rememberLazyListState()` from the private `LoadedThread` up into `PostDetailScreenContent`, so the `topBar` slot and the `LazyColumn` share it.

## Decision 7 — the author block is a label, not a button

No `clickable`. Rationale in `proposal.md` Non-goals. For accessibility the block merges into a single semantics node reading the display name; the avatar carries `contentDescription = null` because the name is immediately adjacent and a "Jane Appleseed, avatar. Jane Appleseed" double-read is worse than silence.

## Risks

- **`LazyListItemInfo.offset`'s origin.** The pure function's `focusItemTopPx` is specified as *relative to the bottom of the top bar*. Whether `LazyListItemInfo.offset` is already measured from there, or from the top of the `LazyColumn` (requiring `- layoutInfo.beforeContentPadding`), must be **verified on device**, not assumed. Because the normalization happens at the call site and the pure function consumes an already-normalized value, getting this wrong is a one-line fix and not a redesign. A wrong origin manifests as the swap firing ~64dp (the app-bar height) too early or too late — visible, and caught by the on-device check in the tasks.
- **Screenshot determinism.** Fixtures drive the **stateless** overload only, so `AnimatedContent` composes with its target state already settled and there is no mid-animation frame to race. Do not add a fixture that drives the stateful overload with a pre-scrolled `LazyListState` — that would capture an in-flight spring.
- **Existing baselines.** Every current post-detail fixture renders at scroll position 0 → `showAuthor = false` → the identical "Post" `Text`. Baselines should not move. If they do, something changed in the bar's layout that shouldn't have.
