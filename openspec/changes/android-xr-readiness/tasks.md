# Implementation Tasks: Android XR Spatial Readiness

## Acceptance & Delivery Criteria

Every layer and feature in this plan MUST meet the following three universal gates:
1. **GitHub Stacked PR Workflow (`gh-stack`):** Work MUST be split into reviewable layers using `gh stack` branched from `origin/main`. PRs will be submitted as a stack so that reviewers inspect individual layer diffs, and the stack merges atomically (`gh stack merge --squash --yes`) into `main` to cut a single consolidated release.
2. **Handheld & Foldable Regression Testing:** Every layer MUST pass regression tests on standard Android Virtual Devices (`Pixel_10a` phone and `Pixel_10_Pro_Fold` foldable in folded/unfolded states) ensuring zero visual or interaction regressions.
3. **Android XR Emulator Verification:** Every spatial capability MUST be verified in the Android XR emulator (`google-xr`), checking spatial panel rendering, Orbiter placement, gaze/pinch hit areas, and smooth freeform resizing.

---

## 1. Project Setup & Android Manifest Configuration
*Stack Branch:* `feat/nubecita-h6kf.1-xr-manifest-and-deps` (Base: `main`)

- [ ] 1.1 Declare Jetpack XR dependencies in `gradle/libs.versions.toml` (`androidx.xr.compose`, `androidx.xr.compose.material3`, `androidx.xr.scenecore`) and add them to `:core:common` and `:app`. Update `:app`'s `AndroidManifest.xml` with `android:resizeableActivity="true"` and `android.software.xr.api` feature with `android:required="false"`. Add unit test in `:app` verifying manifest feature attributes.
- [ ] 1.2 Implement a runtime spatial capability provider (`SpatialSessionEnvironment`) in `:core:common` wrapping `LocalSpatialCapabilities` to safely detect XR spatial support without crashing on standard devices. Add unit test `SpatialSessionEnvironmentTest`.
- [ ] 1.3 Verify build passes and regression test on `Pixel_10a` and `Pixel_10_Pro_Fold` emulators.

## 2. Spatial Navigation Chrome & Orbiters
*Stack Branch:* `feat/nubecita-h6kf.2-xr-spatial-navigation-orbiter` (Base: `feat/nubecita-h6kf.1-xr-manifest-and-deps`)

- [ ] 2.1 Create `SpatialScaffold` in `:designsystem:adaptive` that renders top-level navigation inside a leading `Orbiter` when spatial UI is enabled, and falls back to `NavigationSuiteScaffold` (NavigationBar / NavigationRail) when disabled. Add screenshot preview test `SpatialScaffoldScreenshotTest`.
- [ ] 2.2 Integrate `SpatialScaffold` into `MainShell` in `:app`. Ensure the inner `NavDisplay` expands to 100% full-bleed canvas when the navigation Orbiter is active. Add unit test `MainShellNavStateTest` and screenshot test `MainShellSpatialTest`.
- [ ] 2.3 Verify navigation chrome adapts correctly across Phone (`Pixel_10a` bottom bar), Foldable (`Pixel_10_Pro_Fold` navigation rail), and XR emulator (floating leading Orbiter).

## 3. Spatial Panels & Multi-Pane Scenes
*Stack Branch:* `feat/nubecita-h6kf.3-xr-multipane-scene-strategy` (Base: `feat/nubecita-h6kf.2-xr-spatial-navigation-orbiter`)

- [ ] 3.1 Configure Navigation 3 `ListDetailSceneStrategy` for expanded spatial panel widths (>= 840dp) across the Feed and Search tabs so post details and profiles render concurrently alongside lists without modal takeover. Add unit test `MainSceneStrategyTest` and screenshot test `SpatialListDetailScreenshotTest`.
- [ ] 3.2 Wire `SupportingPaneSceneStrategy` for the Video Feed so comments and author details render in a side-by-side supporting panel on wide spatial windows instead of an obscuring bottom sheet. Update `VideoCommentsSheetScaffoldTest`.
- [ ] 3.3 Verify freeform resizing continuity: add UI state retention tests verifying feed scroll offset, draft composition state, and video playback position persist across rapid window constraint changes. Add `SpatialWindowResizeStateTest`.
- [ ] 3.4 Test multi-pane responsiveness on `Pixel_10_Pro_Fold` (unfolded split) and dynamically on the XR emulator during window corner drag resizing.

## 4. Input Ergonomics & Target Sizing
*Stack Branch:* `feat/nubecita-h6kf.4-xr-input-ergonomics` (Base: `feat/nubecita-h6kf.3-xr-multipane-scene-strategy`)

- [ ] 4.1 Update `PostActionRow` and interaction icon buttons in `:core:post-interactions-ui` to support 56dp minimum touch/pinch target bounds via `minimumInteractiveComponentSize` when running in XR or pointer environments, preserving 24dp optical graphic sizing. Add screenshot test `PostActionRowTargetBoundsTest`.
- [ ] 4.2 Implement accessible hover and raycast focus styling in `:designsystem` (tonal highlight overlay and high-visibility focus ring) for post cards and buttons. Add screenshot test `PostCardFocusStateScreenshotTest`.
- [ ] 4.3 Implement hardware keyboard key event handler (`Modifier.onSpatialKeyEvent`) in `:core:common` mapping `j`/`k` (feed navigation), `r` (reply to focused post), `Space` (toggle video playback), and `/` (focus search), guarded against active text fields. Add unit test `SpatialKeyNavigationTest`.
- [ ] 4.4 Test input ergonomics on `Pixel_10a` (normal touch targets), `Pixel_10_Pro_Fold`, and XR emulator (gaze targeting and keyboard shortcuts).

## 5. Spatial Media & Video Immersion
*Stack Branch:* `feat/nubecita-h6kf.5-xr-spatial-media` (Base: `feat/nubecita-h6kf.4-xr-input-ergonomics`)

- [ ] 5.1 Refactor video player controls in `:feature:videoplayer` to project into a floating bottom `Orbiter` when running in an active XR spatial session, keeping video playback 100% unobscured. Add screenshot test `VideoPlayerOrbiterScreenshotTest`.
- [ ] 5.2 Configure Media3 audio attributes in `:core:video` engine to enable spatialized audio output anchored to the 3D window panel coordinate. Add unit test `SpatialAudioConfigurationTest`.
- [ ] 5.3 Implement Theater Mode action in `:feature:videoplayer` that requests Android XR Full Space mode, presenting the video on a curved virtual surface with dimmed environment passthrough. Add unit test `VideoPlayerTheaterModeTest`.
- [ ] 5.4 Test video playback regression on `Pixel_10a` and `Pixel_10_Pro_Fold`, and verify spatial controls/spatial audio on XR emulator.
