## 1. Data layer — resolve full `PostUi`

- [ ] 1.1 Switch `VideoPlayerViewModel` to resolve via `:core:posts` `PostRepository.getPost(uri)`; map `EmbedUi.Video` for playback as today. Unit test: `VideoPlayerViewModelTest` — Resolving → Ready hydrates playlist/poster/aspect from `getPost` (MockK `PostRepository`).
- [ ] 1.2 Widen `VideoPlayerState` to carry author (`AuthorUi`), `PostStatsUi` counts, and `ViewerStateUi`; populate on Ready. Unit test: `VideoPlayerViewModelTest` — state exposes counts + viewer like/repost after resolve.
- [ ] 1.3 Remove `VideoPostResolver` / `ResolvedVideoPost` / `DefaultVideoPostResolver` / `VideoPostResolverModule` and their tests; route the no-video-embed case to `Error`. Unit test: `VideoPlayerViewModelTest` — post without `EmbedUi.Video` → `Error`.

## 2. `:core:posts` — lift `PostThreadRepository`

- [ ] 2.1 Move `PostThreadRepository` interface + default impl + DI module from `:feature:postdetail:impl/data` into `:core:posts`; keep the interface signature identical. Unit test: relocate `PostThreadRepository` tests into `:core:posts` (`getPostThread` success/failure mapping).
- [ ] 2.2 Update `:feature:postdetail:impl` to consume the relocated interface; delete the local copy. Verify all implementors repo-wide + run root `testDebugUnitTest` so post-detail fakes/tests stay green. Unit test: existing `PostDetailViewModelTest` passes unchanged against the relocated type.

## 3. Chrome — morphing play/pause + skip ±10s

- [ ] 3.1 Replace the transport row's play/pause with an M3 Expressive large filled button that morphs round→squircle on press (`MaterialShapes`). Screenshot test: `VideoPlayerContentScreenshotTest` — play + pause states (round at rest).
- [ ] 3.2 Add replay-10s / forward-10s skip buttons flanking play/pause; wire `SkipBack`/`SkipForward` events that seek ±10s clamped to `[0, duration]`. Unit test: `VideoPlayerViewModelTest` — skip clamps at 0 and at duration. Screenshot test: transport cluster.

## 4. Wavy seek bar

- [ ] 4.1 Replace the plain `Slider` track with a custom track slot drawing `LinearWavyProgressIndicator` (wave behind thumb, flat ahead; amplitude → ~0 while dragging); keep drag-to-commit single-`seekTo`. Unit test: keep the existing drag-commit assertion (one seek per gesture) via the seek-bar logic; Screenshot test: `VideoPlayerContentScreenshotTest` — wavy bar at a mid position.

## 5. Mute + PiP utility row

- [ ] 5.1 Move mute + the pop-out/PiP affordance out of the transport row into a secondary utility row under play/pause; preserve `resolvePopOut` (Pro→PiP / non-Pro→paywall) and the device-support gate (mute-only when unsupported). Unit test: `ResolvePopOutTest` unchanged. Screenshot test: utility row with PiP shown and PiP hidden.

## 6. Scrim auto-hide ladder + author chip

- [ ] 6.1 Replace `chromeVisible: Boolean` with sealed `ChromeVisibility { Shown, Peeking, Hidden }`; drive Shown→Peeking→Hidden idle timers only while playing, tap→Shown, pause pins Shown. Unit test: `VideoPlayerViewModelTest` — timer advances while playing, tap resets, pause pins (virtual time).
- [ ] 6.2 Render the three scrim/control states (full / peeking-without-counts / hidden tap-to-play + progress hairline). Screenshot test: `VideoPlayerContentScreenshotTest` — Shown, Peeking, Hidden.
- [ ] 6.3 Add the top-bar author chip (avatar + name/handle) from `AuthorUi`; tap emits a navigate-to-Profile effect. Unit test: `VideoPlayerViewModelTest` — author-chip tap emits the Profile nav effect. Screenshot test: top bar with author chip.

## 7. Action group — Like · Repost · Comment · Share

- [ ] 7.1 Render the M3 `ButtonGroup` (like/repost/comment/share) bound to counts + viewer state. Screenshot test: `VideoPlayerContentScreenshotTest` — action group liked vs not-liked.
- [ ] 7.2 Wire Like to optimistic `LikeRepostRepository` + `PostInteractionsCache` (shared with feed). Unit test: `VideoPlayerViewModelTest` — like toggles optimistic state + calls repository; undo reverts.
- [ ] 7.3 Wire Repost → Repost/Quote menu (plain repost optimistic; Quote opens composer in quote mode) and Share → `PostShareLauncher`. Unit test: `VideoPlayerViewModelTest` — repost toggles; quote/share emit the expected effects.

## 8. Comments sheet — docked shell

- [ ] 8.1 Add a non-modal two-pane layout (video pane + comments pane) driven by `AnchoredDraggableState` with `Dismissed`/`Half`/`Full` anchors; video keeps playing + poster layered under the surface; Comment opens it, drag-down dismisses, entering PiP collapses it. Unit test: VM `commentsOpen`/`PiP collapses sheet` logic. Screenshot test: new `VideoPlayerCommentsScreenshotTest` — Half and Full detents.
- [ ] 8.2 Validate IME + non-modal sheet insets (reply bar owns the IME inset, chats single-owner pattern); confirm no jank on device. Add `run-instrumented` PR label for the docked-resize instrumented check if warranted.

## 9. Comments content + inline reply + like-a-comment

- [ ] 9.1 Populate the sheet with the post's direct replies via the relocated `PostThreadRepository`; tap a reply → post-detail thread. Unit test: comments presenter — replies load + tap emits navigate-to-thread.
- [ ] 9.2 Add the inline reply bar (editor `TextFieldState` exception; grapheme-limit gate) posting a reply to the video's post via `:core:posting`; like-a-reply via `LikeRepostRepository`. Unit test: over-limit disables submit; submit posts reply; like-reply toggles optimistic. Screenshot test: reply bar empty / over-limit.
