> **Tracked in beads under epic `nubecita-6rdb` (Fullscreen video player revamp).**
>
> | Group | Owned by |
> |---|---|
> | 1. Baseline measurement | `nubecita-6rdb.15` |
> | 2. Design-system components | `nubecita-6rdb.16` |
> | 3. Adopt in the fullscreen player | `nubecita-6rdb.17` |
> | 4. Adopt in the trending feed **and** 5. Measure and confirm | `nubecita-6rdb.18` |
> | 6. Gate and land | every child — this is the per-PR gate, not a separate unit of work |
> | 7. Hand off the blur follow-up | done; filed as `nubecita-e8at` |
>
> `.15` → `.16` → `.17` → `.18` are wired as a dependency chain so the baseline is captured
> before any visual change lands. This docs change is `nubecita-6rdb.19`, and it supersedes
> `nubecita-6rdb.12`, now closed.

## 1. Baseline measurement (before any visual change)

- [x] 1.1 Add a video-overlay Macrobenchmark to `:benchmark` covering the trending feed with chrome visible: video playing, overlay controls shown, swiping between pages. Model it on the existing `VideoFeedScrollBenchmark` (Satisfied by enhancing the existing `VideoFeedScrollBenchmark`).
- [x] 1.2 Verify the benchmark actually exercises the overlay — assert the controls are on screen during the measured section, so a chrome-hidden run cannot silently report a clean number (Satisfied by enhancing the existing `VideoFeedScrollBenchmark`).
- [x] 1.3 Reference device decided: **the emulator**, for availability. Recorded in design.md Open Question 1 along with the limitation — emulator frame timings are a *relative* instrument (fine for "did the scrim move it"), not a device-accurate 120 Hz budget gate. The deferred blur change needs real hardware.
- [x] 1.4 Baseline captured on the emulator and recorded in `baseline-measurements.md`. The rail assertion was mutation-verified: pointing `VIDEO_FEED_RAIL_LIKE_RES_ID` at an absent tag turned the run red with the intended diagnostic (`tests=1 failures=1`), and restoring it turned it green — pass → fail → pass, so the guard is real rather than decorative.

## 2. Design-system components

- [x] 2.1 Create the icon-only overlay control in `:designsystem` (fullscreen player shape: back / skip / play-pause / mute / PiP).
- [x] 2.2 Create the icon-with-count overlay control (trending feed rail cell: icon above an optional compact count).
- [x] 2.3 Carry `VideoRailAction`'s accessibility contract over **verbatim** — `toggleable` + `Role.Switch` with the label as `contentDescription` for **like, repost, bookmark and mute**; `clickable` + `Role.Button` + `onClickLabel` with a decorative icon for **reply, share and overflow**; labels stay plain nouns, never inverse verbs. All **seven** rail cells must be covered — bookmark and overflow are easy to miss. This contract is already correct; move it, do not redesign it.
- [x] 2.4 No tuning needed — `:designsystem` already had the right tokens. `videoOverlayScrim` (black @ 80%) over a white frame is sRGB 0.2 / luminance 0.033, giving **12.6:1** against pure-white `onVideoOverlay`, well past the 4.5:1 floor. Recorded in the file header with the arithmetic so a token change forces a re-measure. Reused rather than invented: `MediaPlayBadge` already paints with this pair, so the two overlay families cannot drift.
- [x] 2.5 Verify the public API exposes **no** treatment parameter — no scrim color, alpha, blur, quality mode, or backdrop-source handle (design.md D2). This is what makes the follow-up blur change additive; getting it wrong now costs a refactor later.
- [x] 2.6 Source every color from `MaterialTheme` — no `Color(0xFF…)` literals.
- [x] 2.7 Previews added over white, black and mid-tone. They earned their keep: they exposed that fill alone fails over black (80%-black scrim on black is invisible, so the shape boundary vanishes while the icon stays legible). Open Question 2 settled — a 1dp hairline at 16% alpha was added. Note the swept axis is the **backdrop**, not light/dark: these tokens are theme-invariant, so light and dark renders would be byte-identical and prove nothing.
- [x] 2.8 Three baselines committed (white / black / mid-tone). Regeneration also rewrote two unrelated `NubecitaLogomark` baselines both times — discarded via `git checkout --`, exactly the macOS antialiasing noise the guidance warns about.

## 3. Adopt in the fullscreen player

> Supersedes `nubecita-6rdb.12`, which proposed hand-tuning `translucentSkipColors()` from `Color.White @ 0.16` to ~0.28 alpha. That issue's rejected alternative is worth keeping: `secondaryContainer` was prototyped and rejected because the brand's is peach — loud, and a warm/cool clash with the blue play button over a dark scrim.

- [ ] 3.1 Replace `VideoPlayerChrome`'s controls with the icon-only overlay control. Two grades of problem: back / mute / pop-out are bare `IconButton`s tinted `Color.White` with **no** backing, while skip ±10s already carry `translucentSkipColors()` = `Color.White @ 0.16 alpha` that is merely too faint. Both become one measured treatment.
- [ ] 3.2 Preserve the existing `IconButtonShapes` play/pause morph and its remembered-instance stability note — that `remember` exists to protect 120 Hz skipping, so do not inline it.
- [ ] 3.3 Update `:feature:videoplayer:impl` screenshot baselines. Check flavoring first: `git grep -l nubecita.android.flavors -- feature/videoplayer/impl` (output = use `updateProductionDebugScreenshotTest`; no output = `updateDebugScreenshotTest`).
- [ ] 3.4 Device pass: controls legible over a bright video; PiP entry, seek bar and the 3s auto-hide unaffected.

## 4. Adopt in the trending video feed

- [ ] 4.1 Replace `VideoRailAction`'s internals with the icon-with-count overlay control, keeping its existing public parameters so `VideoFeedPage` call sites are unchanged.
- [ ] 4.2 Confirm the accessibility contract is preserved exactly — `VideoFeedTestTagsTest` and the existing rail tests must pass unchanged, and TalkBack output must not change. A change here is a defect, not an improvement.
- [ ] 4.3 Update `:feature:videos:impl` screenshot baselines, same selective-commit discipline as 2.8.
- [ ] 4.4 Device pass on a bright video: rail legible, and `LikeBurst`'s double-tap animation still correct over the new backing.

## 5. Measure and confirm

- [ ] 5.1 Re-run the video-overlay benchmark on the reference device and compare against the 1.4 baseline.
- [ ] 5.2 Confirm frame timing is unchanged within run-to-run noise. The scrim adds one draw per control and should not move the number — an actual regression here means something other than a scrim was introduced, so investigate rather than accept it.
- [ ] 5.3 Record both numbers in the change folder so the follow-up blur change inherits a real before-number.

## 6. Gate and land

- [ ] 6.1 `./gradlew :app:assembleDebug`.
- [ ] 6.2 Lint every touched module, using the flavored task names where the module applies `nubecita.android.flavors` (`git grep -l nubecita.android.flavors -- <module-dir>`; no output = plain `lintDebug`).
- [ ] 6.3 `./gradlew jacocoTestReportAggregated` — the root `testDebugUnitTest` skips flavored modules.
- [ ] 6.4 Run the **compose-expert** skill in Review Mode over the cumulative diff — this change adds `@Composable` lines, so the gate applies.
- [ ] 6.5 Add `:feature:videos:impl` to CLAUDE.md's module map; it is missing today, which is why the trending feed was easy to overlook.

## 7. Hand off the blur follow-up

- [x] 7.1 Filed as `nubecita-e8at` — carries design.md D1 (why deferred, what each route costs), D6 (the fullscreen player's `SurfaceView` makes blur impossible there regardless of library) and D7 (the 120 Hz gate and the `TextureView` battery coupling).
- [x] 7.2 Trigger condition recorded on the issue: Haze 2.0 reaching stable, or a decision to hand-roll. The snapshot-blur alternative from D1 is included as a cheaper per-surface option for the auto-hiding fullscreen chrome.
