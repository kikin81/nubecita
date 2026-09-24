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

- [ ] 1.1 Add a video-overlay Macrobenchmark to `:benchmark` covering the trending feed with chrome visible: video playing, overlay controls shown, swiping between pages. Model it on the existing `VideoFeedScrollBenchmark`.
- [ ] 1.2 Verify the benchmark actually exercises the overlay — assert the controls are on screen during the measured section, so a chrome-hidden run cannot silently report a clean number.
- [ ] 1.3 Decide and write down the reference device for the frame-budget comparison (design.md Open Question 1).
- [ ] 1.4 Run it on the reference device on the **pre-change** build and record the numbers in the change folder. This is the only chance to capture a before-number on a build with no overlay treatment at all — the follow-up blur change depends on it existing.

## 2. Design-system components

- [ ] 2.1 Create the icon-only overlay control in `:designsystem` (fullscreen player shape: back / skip / play-pause / mute / PiP).
- [ ] 2.2 Create the icon-with-count overlay control (trending feed rail cell: icon above an optional compact count).
- [ ] 2.3 Carry `VideoRailAction`'s accessibility contract over **verbatim** — `toggleable` + `Role.Switch` with the label as `contentDescription` for **like, repost, bookmark and mute**; `clickable` + `Role.Button` + `onClickLabel` with a decorative icon for **reply, share and overflow**; labels stay plain nouns, never inverse verbs. All **seven** rail cells must be covered — bookmark and overflow are easy to miss. This contract is already correct; move it, do not redesign it.
- [ ] 2.4 Tune the scrim to hold **4.5:1** contrast over a white frame and record the measured ratio in a code comment, so a later visual tweak cannot quietly drop below it.
- [ ] 2.5 Verify the public API exposes **no** treatment parameter — no scrim color, alpha, blur, quality mode, or backdrop-source handle (design.md D2). This is what makes the follow-up blur change additive; getting it wrong now costs a refactor later.
- [ ] 2.6 Source every color from `MaterialTheme` — no `Color(0xFF…)` literals.
- [ ] 2.7 Add `@Preview` variants for both controls over white, black and mid-tone backdrops. Use these to settle design.md Open Question 2 (whether the scrim needs a hairline border for shape definition).
- [ ] 2.8 Add screenshot tests and generate baselines with `./gradlew :designsystem:updateDebugScreenshotTest`. Commit **only** the images your change actually altered — regeneration rewrites every baseline and macOS adds 1/255 antialiasing noise; `git checkout --` the rest (see `scripts/triage-screenshot-failures.py`).

## 3. Adopt in the fullscreen player

> Supersedes `nubecita-6rdb.12`, which proposed hand-tuning `translucentSkipColors()` from `Color.White @ 0.16` to ~0.28 alpha. That issue's rejected alternative is worth keeping: `secondaryContainer` was prototyped and rejected because the brand's is peach — loud, and a warm/cool clash with the blue play button over a dark scrim.

- [ ] 3.1 Replace `VideoPlayerChrome`'s controls with the icon-only overlay control. Two grades of problem: back / mute / pop-out are bare `IconButton`s tinted `Color.White` with **no** backing, while skip ±10s already carry `translucentSkipColors()` = `Color.White @ 0.16 alpha` that is merely too faint. Both become one measured treatment.
- [ ] 3.2 Preserve the existing `IconButtonShapes` play/pause morph and its remembered-instance stability note — that `remember` exists to protect 120 Hz skipping, so do not inline it.
- [ ] 3.3 Update `:feature:videoplayer:impl` screenshot baselines. Check flavouring first: `git grep -l nubecita.android.flavors -- feature/videoplayer/impl` (output = use `updateProductionDebugScreenshotTest`; no output = `updateDebugScreenshotTest`).
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
