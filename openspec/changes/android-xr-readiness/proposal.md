# Proposal: Android XR Spatial Readiness

## Why

As Android XR devices and headsets emerge, Nubecita users expect to comfortably browse, watch videos, read threads, and interact with the Bluesky network on resizable spatial panels in their virtual environments. While Nubecita already leverages Material 3 Adaptive layouts and Navigation 3, standard mobile touch targets and embedded navigation rails crowd the canvas and are difficult to target accurately via distant eye gaze, pinch gestures, or controller rays. Optimizing navigation chrome into spatial Orbiters, expanding multi-pane scenes for ultra-wide canvases, increasing interaction target sizes, and introducing spatial video capabilities will make Nubecita a premier social client for Android XR.

## What Changes

- **Spatial Navigation & Orbiters:** Adapt the main navigation chrome so that on XR-capable runtime environments, top-level navigation can float outside the main window as an `Orbiter` (`androidx.xr.compose.material3`), providing 100% full-bleed window canvas for content feeds.
- **Multi-Pane Expansion via Navigation 3:** Expand Navigation 3 `SceneStrategy` implementations across ultra-wide spatial window bounds (feed + thread detail, search + profile, video feed + comments) without modal takeovers.
- **Spatial Freeform Window State Preservation:** Ensure scroll offsets, composer drafts, and active video playback state persist across continuous, dynamic window resizing in 3D space.
- **XR Input Ergonomics & Target Sizing:** Expand primary interaction touch targets (like, repost, reply, compose) to 56–64dp when running on XR / pointer modes; add distinct focus and hover affordance styling to support eye-gaze and controller raycasting.
- **Hardware Keyboard Shortcuts:** Provide standard desktop/XR hotkeys (`j`/`k` to navigate posts, `r` to reply, `Space` to toggle video playback, `/` to focus search) for physical Bluetooth keyboard productivity.
- **Spatial Media & Video Immersion:** Detach video player controls into floating Orbiters, position video audio in 3D space using Media3 spatial audio APIs, and add a Full Space / Theater mode toggle for immersive video consumption.

## Capabilities

### New Capabilities
- `xr-spatial-panels`: Manages spatial panel configurations, dynamic freeform resizing state preservation, and multi-pane scene strategy expansion on wide virtual displays.
- `xr-input-ergonomics`: Defines XR interaction targets (56–64dp minimum hit boxes), visual hover/focus states for eye-gaze/raycasting, and physical keyboard shortcut handlers.
- `xr-spatial-media`: Delivers floating media control Orbiters, spatial audio positioning for Media3 video playback, and Full Space virtual theater immersion.

### Modified Capabilities
- `app-navigation-shell`: Adapts `MainShell` to project top-level navigation into a spatial `Orbiter` when running in an Android XR session, falling back gracefully to `NavigationSuiteScaffold` (NavigationBar / NavigationRail) on standard devices.

## Non-Goals

- Building custom 3D mesh environments, VR avatars, or full 3D world navigation (Nubecita remains a native, 120Hz content-focused client).
- Making Android XR libraries a hard hardware requirement (the app must compile and run seamlessly on standard phones, foldables, tablets, and Wear OS).
- Rewriting the core UI into Jetpack Compose Glimmer (Glimmer is strictly reserved for optional companion projected eyewear, not the primary application).

## Impact

- **Baseline Architecture:** Follows existing MVI (`UiState` / `UiEvent` / `UiEffect`), Jetpack Compose, and Navigation 3 patterns without deviation.
- **Dependencies:** Adds optional compile-time dependencies on Jetpack XR (`androidx.xr.compose:compose`, `androidx.xr.compose.material3:material3`, `androidx.xr.scenecore:scenecore`) behind runtime capability checks.
- **Modules Affected:**
  - `:app` (`MainShell`, navigation orchestrator, manifest feature declarations)
  - `:designsystem` (hit target tokens, focus/hover highlight modifiers)
  - `:feature:videoplayer` (spatial controls orbiter, Media3 spatial audio)
  - `:core:common` (hardware keyboard key event modifiers)
