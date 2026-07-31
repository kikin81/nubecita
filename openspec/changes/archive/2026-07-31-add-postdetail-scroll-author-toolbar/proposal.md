## Why

The post-detail toolbar currently reads a static "Post" at every scroll position. That title is doing useful work at the top of the screen — it names the surface you just navigated into — but it stops earning its keep the moment the focus post scrolls out of view. Once you're deep in a long reply thread, the one piece of context you've lost is the one the toolbar could cheapest replace: *whose post am I reading replies to?*

Threads solves this by morphing the toolbar title into the post author's avatar + display name as you scroll past the post itself, and morphing it back when you return to the top. The author identity slides in from the start edge; the static title fades away. It reads as the toolbar "catching" the identity the content just dropped.

This change ports that behavior to `:feature:postdetail:impl`. It is a screen-layer visual affordance only — no ViewModel, contract, or navigation surface changes.

## What Changes

### `:feature:postdetail:impl` — new `PostDetailTopBar`

A new file `ui/PostDetailTopBar.kt` mirroring the two-overload shape `:feature:profile:impl`'s `ProfileTopBar` already established (a stateful overload that derives scroll state, delegating to a stateless overload that previews and screenshot tests drive directly):

- **Stateless** `PostDetailTopBar(author: AuthorUi?, showAuthor: Boolean, onBack: () -> Unit)` — renders an M3 `TopAppBar` whose `title` slot is an `AnimatedContent` keyed on `showAuthor`. When `false`, the existing `Text(stringResource(R.string.postdetail_title))` ("Post"). When `true`, an author block: `NubecitaAvatar` (28dp) + display name (falling back to `@handle` when the account has no display name), single line, ellipsized.
- **Stateful** `PostDetailTopBar(author: AuthorUi?, listState: LazyListState, focusIndex: Int, onBack: () -> Unit)` — folds `showAuthor` from a `snapshotFlow` over `listState.layoutInfo` into a `MutableState<Boolean>` (NOT `derivedStateOf` — the hysteresis is a fold over the prior output; see design.md Decision 2) and delegates.
- **Pure** `internal fun shouldShowAuthorInBar(...)` — all threshold/hysteresis math, extracted so it is unit-testable on the JVM with no device and no Compose runtime. Same rationale as `ProfileTopBar.computeBarAlpha`.

### `:feature:postdetail:impl` — `PostDetailScreenContent`

- Hoist `rememberLazyListState()` out of the private `LoadedThread` composable up into `PostDetailScreenContent` so the `topBar` slot can read the same scroll state the `LazyColumn` writes. Pass it down to `LoadedThread`.
- Replace the inline `TopAppBar { Text(postdetail_title) }` in the `topBar` slot with the new stateful `PostDetailTopBar`, fed `state.focusPost?.author` (the existing `focusPost` extension on `PostDetailState`) and the focus row's index.

### Motion

The swap is asymmetric and driven by an `AnimatedContent` `transitionSpec`:

- **Entering the author block:** `slideInHorizontally` from the start edge + `fadeIn`.
- **Exiting the "Post" title:** `fadeOut` only — it does not move. Only the incoming element carries motion, which keeps a 56dp bar from reading busy.
- **Returning to top:** the exact reverse (author slides back out toward the start and fades; "Post" fades in).
- `SizeTransform(clip = false)` so the bar does not animate its width between the two titles and the sliding avatar is not clipped.

Animation specs are read from `MaterialTheme.motionScheme` (spatial for the slide, effects for the fades). Because `NubecitaMotionScheme` already branches on `isReduced`, the system-level reduce-motion preference is honoured with no separate accessibility branch in this feature.

### Tests

- New JVM unit test `PostDetailTopBarTest` over `shouldShowAuthorInBar`.
- New screenshot fixtures over the **stateless** `PostDetailTopBar` covering `showAuthor = false` / `true`, a long display name (truncation), and an avatarless author (the `NubecitaAvatar` initial fallback).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feature-postdetail`: `PostDetailScreenContent` gains a scroll-reactive top bar. `PostDetailViewModel` / `PostDetailState` / `PostDetailEvent` / `PostDetailEffect` are unchanged.

## Impact

- **Affected modules**: `:feature:postdetail:impl` only (one new file, one modified file, new screenshot fixtures, one new unit test). No new library dependencies — `androidx.compose.animation` is already on the Compose BOM, and `NubecitaAvatar` already lives in `:designsystem`.
- **Affected specs**: `feature-postdetail` (delta — new requirement on the scroll-reactive toolbar).
- **New string resources**: none. The author block's text comes from `AuthorUi`; the avatar is decorative (`contentDescription = null`) because the display name sits immediately beside it. The existing `postdetail_title` and `postdetail_back_content_description` are reused unchanged. **This means no `values-b+es+419` / `values-pt-rBR` backfill is required for this change.**
- **Backwards compatibility**: additive at the screen layer. Existing `PostDetailViewModel` unit tests are untouched. Existing post-detail screenshot fixtures render at scroll position 0, where `showAuthor` is `false` and the bar renders the semantically identical "Post" title — but wrapping that title in `AnimatedContent` sub-pixel-shifts the Scaffold body, so 5 focus-bearing LIGHT baselines move by a few (imperceptible) shadow pixels and are regenerated with this change. See `design.md` Risks.

## Non-goals

- **A tappable toolbar author.** The block is a label, not a second route into the profile. Tapping the author on the focus card already does that.
- **A `@handle` line under the display name.** `ProfileTopBar` stacks name-over-handle, but that is dense next to a 28dp avatar in a 56dp bar, and the handle is redundant with the focus card sitting directly below.
- **Tracking whichever author is topmost as you scroll.** The bar tracks exactly one author for the lifetime of the screen: the focus post's. A bar whose name churns through every reply author during a fling is noise.
- **A scroll-linked continuous alpha/offset** (the `ProfileTopBar.computeBarAlpha` approach). That recomposes the bar on every scroll frame; 120hz scrolling is a hard project requirement. A boolean threshold recomposes only at the crossing and hands the motion to the animation clock. See `design.md` Decision 2.
- **Any change to the focus-card `surfaceContainerHigh` container, the reply FAB, or the pull-to-refresh wiring.**
