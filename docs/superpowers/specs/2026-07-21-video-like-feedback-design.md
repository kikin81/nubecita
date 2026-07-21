# Video feed like feedback: haptics + heart burst

**bd:** nubecita (child of epic nubecita-zdv8)
**Date:** 2026-07-21
**Status:** approved
**Depends on:** PR #775 (nubecita-zdv8.12) — the bookmark rail cell it adds haptics to.

## Context

The vertical video feed's interactions (like/repost/bookmark toggles, double-tap-to-like) currently fire with no tactile or celebratory feedback. Every other post surface routes through `PostHaptics`; the video feed does not. And the double-tap-to-like gesture (shipped in PR3) has no visual acknowledgement beyond the rail heart filling.

This change adds two independent pieces of view-layer feedback:

1. **Haptics** on the rail toggles and the double-tap, mirroring the Feed's established `PostHaptics` wrapping.
2. **A heart burst** at the finger on every double-tap, TikTok/Instagram style.

Both are pure view feedback — no ViewModel or playback involvement.

## Decisions

### D1 — Haptics mirror the Feed's callback-wrapping pattern

`PostHaptics` (`:core:common:haptic`) already exposes `likeOn/likeOff`, `repostOn/repostOff`, `lightTap`, `rejected`, backed by `HapticFeedbackConstants.CONFIRM`/`TOGGLE_OFF`/`REJECT` with pre-API-34 fallbacks. `FeedInteractions` wraps its interaction callbacks to fire the state-appropriate haptic before delegating. The video feed will do the same in `rememberVideoFeedInteractions`:

- `onLike` → `if (post.viewer.isLikedByViewer) haptics.likeOff() else haptics.likeOn()`, then delegate
- `onRepost` → `if (post.viewer.isRepostedByViewer) haptics.repostOff() else haptics.repostOn()`, then delegate
- `onBookmark` → `if (post.viewer.isBookmarked) haptics.bookmarkOff() else haptics.bookmarkOn()`, then delegate

`bookmarkOn()`/`bookmarkOff()` are **new** on `PostHaptics` — 4 lines using the same `CONFIRM`/`TOGGLE_OFF` constants as like (bookmark is a save-type confirmation). No surface fires a bookmark haptic today, so this is additive and reusable.

Haptics stay a UI concern: `PostHaptics` needs a `View` (`LocalView`), so it is constructed at the screen and the ViewModel is untouched.

### D2 — The double-tap haptic fires only on the unliked→liked transition

Double-tap never unlikes (affirmative-only, from PR3). The `likeOn()` haptic fires only when the double-tapped post was not already liked; double-tapping an already-liked post produces the burst but no buzz, because nothing changed. The screen has the post's like state, so it decides:

```
onDoubleTapLike = {
    if (!item.post.viewer.isLikedByViewer) haptics.likeOn()
    viewModel.handleEvent(VideoFeedEvent.DoubleTapLike(item.post))
}
```

### D3 — The heart burst is videos-local and page-scoped

`detectTapGestures(onDoubleTap = { offset -> … })` now captures the touch `Offset`. Each double-tap appends a `HeartBurst(id, position)` to a `remember { mutableStateListOf<HeartBurst>() }` in `VideoFeedPage`; each renders a self-removing `LikeBurstHeart` overlay positioned at that offset. Rapid taps stack (each heart has a unique id from a monotonic counter). The burst lives in the **page's** coordinate space, so it sits exactly where the finger touched regardless of pager scroll — no screen-level plumbing, no cross-composable coordinate translation.

Rejected: a screen-level overlay (needs page↔screen coordinate translation while the pager moves) and a shared `:designsystem` component (YAGNI — one surface uses it; extract if a second ever does).

### D4 — The burst transform is a pure function

`heartBurstTransform(progress: Float, id: Int): BurstTransform` returns `(scale, alpha, translationYDp, rotationDegrees)`, unit-tested at key progresses — the same pure-math-plus-thin-composable seam used for the surface slide. `LikeBurstHeart` drives `progress` 0→1 via an `Animatable`, reads the transform each frame, and removes itself on completion.

Per-heart tilt is deterministic from the id: `((id % 5) - 2) × 6°` → {−12, −6, 0, 6, 12}. Organic variety without `Math.random`, which is banned in this environment and non-deterministic for tests.

## Motion spec (starting values; tunable on device)

- **100dp white filled heart** (`NubecitaIconName.Favorite`), centered on the touch point. White reads over any video frame.
- **~700ms total.**
  - **Pop in:** scale 0 → 1.2 (overshoot) over the first ~140ms, settle to 1.0 by ~250ms; alpha 0 → 1 over the first ~100ms.
  - **Drift + fade:** over the back half, alpha 1 → 0 and `translationY` 0 → −48dp (rises as it fades).
- **Tilt:** the fixed per-id rotation above, applied for the whole animation.

## Architecture

| File | Responsibility |
|---|---|
| `core/common/.../haptic/PostHaptics.kt` | **Modify.** Add `bookmarkOn()`/`bookmarkOff()`. |
| `core/common/src/test/.../PostHapticsTest.kt` | **Modify** (if it exists) or rely on existing coverage — bookmark mirrors like. |
| `feature/videos/impl/.../ui/VideoFeedInteractions.kt` | **Modify.** Take `PostHaptics`; wrap `onLike`/`onRepost`/`onBookmark`. |
| `feature/videos/impl/.../ui/LikeBurst.kt` | **Create.** `HeartBurst` data class, pure `heartBurstTransform`, `LikeBurstHeart` composable. |
| `feature/videos/impl/.../ui/VideoFeedPage.kt` | **Modify.** Capture the double-tap offset; host the burst list + overlay. |
| `feature/videos/impl/.../VideoFeedScreen.kt` | **Modify.** Construct `PostHaptics`; pass it to `rememberVideoFeedInteractions`; fire the double-tap `likeOn` on transition. |
| `feature/videos/impl/src/test/.../ui/LikeBurstTest.kt` | **Create.** Unit-test `heartBurstTransform`. |
| `.../screenshotTest/.../VideoFeedPageScreenshotTest.kt` | **Modify.** A static `LikeBurstHeart` at one progress, to pin the look. |

## Error handling / edge cases

- **Haptics are best-effort.** No-op if system haptics are off or the device has no vibrator; texture varies by OEM. Inherent, matches the rest of the app — nothing to handle.
- **Rapid taps** stack hearts; each self-removes, so the list drains to empty. A hard cap is unnecessary — a human can't spawn enough to matter in 700ms — but the list must remove finished hearts or it grows unbounded.
- **The gesture still doesn't consume drags** (unchanged from PR3), so capturing the offset must not change the pager's swipe.

## Testing

- **Unit:** `heartBurstTransform` at progress 0 / 0.14 / 0.25 / 0.5 / 1.0 (scale overshoot, alpha in/out, drift); deterministic tilt per id.
- **Screenshot:** one `LikeBurstHeart` at `progress = 0.4f` pins the heart's look; the motion itself is device-only.
- **Device (Pixel Fold, required):** double-tap → a heart pops at the touch point (captured mid-animation — screencap's ~300ms window catches the 700ms burst); rapid taps stack; the rail heart also fills. Haptics can't be screenshotted — verified by the faithful mirror of the proven Feed path plus manual feel.

## Delivery

One PR (small, cohesive). **Rebased onto `main` after PR #775 merges**, so the bookmark cell exists to attach a haptic to.
