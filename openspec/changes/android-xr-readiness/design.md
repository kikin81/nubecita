# Design: Android XR Spatial Readiness

## Context

Nubecita is an ultra-fast, native Android client for Bluesky and the AT Protocol built with Jetpack Compose, Material 3 Expressive, Navigation 3, and MVI on ViewModel.

On Android XR headsets, standard Android applications execute within 2D spatial panels placed in the user's 3D environment. Because Nubecita already adheres to Material 3 Adaptive guidelines (utilizing `NavigationSuiteScaffold` with `NavigationBar` at compact widths and `NavigationRail` at medium/expanded widths) and Jetpack Navigation 3, the application is fundamentally well-suited for large virtual displays.

However, running as a standard flat tablet window in XR encounters distinct friction points:
1. **Screen Crowding:** Internal navigation rails consume valuable window real estate that could otherwise be dedicated to full-bleed content.
2. **Input Inaccuracy:** Mobile-oriented touch targets (40–48dp) are difficult to strike reliably via distant eye gaze and pinch gestures or raycasts.
3. **Lack of Spatial Feedback:** Standard touch UIs lack the pronounced hover and focus depth cues necessary for gaze targeting.
4. **Flat Media:** Videos lack spatial audio anchoring and immersive cinema sizing.

## Goals / Non-Goals

**Goals:**
- Deliver a native, polished Android XR spatial experience while maintaining a unified single APK across phone, foldable, tablet, desktop, and XR.
- Unload navigation and media controls into floating spatial **Orbiters** (`androidx.xr.compose.material3`) when running in an XR spatial session.
- Expand multi-pane layouts via Navigation 3 `ListDetailSceneStrategy` and `SupportingPaneSceneStrategy` across wide spatial bounds with seamless state preservation during freeform resizing.
- Optimize interaction ergonomics with 56–64dp minimum hit targets for primary post actions, distinct visual hover/focus affordances, and physical keyboard shortcut support.
- Support spatial audio and one-tap Full Space / Theater mode for video playback.

**Non-Goals:**
- Creating 3D volumetric meshes, VR environments, or 3D avatars (Nubecita remains a fast, 120Hz content-focused client).
- Forking build variants or creating a separate APK for XR.
- Rewriting core screens into Jetpack Compose Glimmer (Glimmer is strictly reserved for optional companion display glasses).

## Decisions

### 1. Jetpack XR Material 3 Orbiters with Runtime Degradation
- **Decision:** Use `androidx.xr.compose.material3.Orbiter` to project top-level navigation and video controls outside the primary window panel. Wrap this in a shared `SpatialScaffold` abstraction.
- **Rationale:** Orbiters grant 100% of the window surface to the feed and content. By checking `LocalSpatialCapabilities.current.isSpatialUiEnabled`, `SpatialScaffold` automatically falls back to standard `NavigationSuiteScaffold` on non-XR devices.
- **Alternatives Considered:**
  - *Keep NavigationRail inside window:* Wastes 80dp+ of horizontal canvas on wide screens.
  - *Custom multi-window activities:* Heavyweight, causes complex IPC and backstack synchronization issues.

### 2. Navigation 3 Adaptive Multi-Pane on Dynamic Virtual Canvases
- **Decision:** Leverage Navigation 3 `ListDetailSceneStrategy` (Feed + Post Detail, Search + Profile) and `SupportingPaneSceneStrategy` (Video Feed + Comments) configured with dynamic width breakpoints (>= 840dp).
- **Rationale:** XR users can scale floating panels freely from phone-sized portrait cards to ultra-wide panoramic monitors. SceneStrategy handles seamless fluid transitions between single-pane and dual-pane configurations without reloading screens or destroying viewmodels.
- **Alternatives Considered:**
  - *Fixed layout splitting via custom Row:* Misses canonical backstack integration and predictive back navigation.

### 3. Hit-Target Expansion without Graphic Distortion
- **Decision:** Expand interactive bounds to 56–64dp using `Modifier.minimumInteractiveComponentSize()` and layout padding modifiers in `:designsystem` components (`PostActionRow`, `PostCard`), keeping visual icon vector assets at their canonical 24dp optical size.
- **Rationale:** Ensures gaze + pinch and raycast interactions are effortless from a distance without making the visual design look clumsy or oversized.
- **Alternatives Considered:**
  - *Scaling up icons to 56dp:* Destroys visual hierarchy and crowds typography.

### 4. Media3 Spatial Audio & Full Space Immersion
- **Decision:** Configure `:core:video` Player surface to register with Android XR spatial audio, and provide a Theater Mode button that calls `Session.requestFullSpaceMode()`.
- **Rationale:** Aligns audio origin directly with the visual placement of the video player in the room, and allows cinema viewing with dimmed ambient passthrough for deep focus.
- **Alternatives Considered:**
  - *Standard stereo audio only:* Disorienting in XR when turning your head away from the window.

## Risks / Trade-offs

- **[Risk] Freeform resizing causing recomposition thrash or scroll offset jumps**
  → *Mitigation:* Ensure all list items utilize stable keys (`item.uri`), back states with `rememberSaveable`, and hoist video playback progress into the ViewModel.
- **[Risk] Keyboard shortcut conflicts with text inputs (e.g. composer or search)**
  → *Mitigation:* Attach `Modifier.onKeyEvent` at the shell level, guarded by a focus check that disables global hotkeys whenever a `TextField` has active focus.
- **[Risk] Missing XR runtime dependencies on standard devices**
  → *Mitigation:* Declare `android.software.xr.api` with `required="false"` in the manifest and ensure Jetpack XR Compose calls are guarded behind capability checks.

## Migration Plan

1. **Phase 1 (Navigation & Shell):** Add XR dependencies to version catalog; implement `SpatialScaffold` in `:app` with conditional `Orbiter` projection for top-level navigation.
2. **Phase 2 (Panels & Scene Strategy):** Audit and wire `ListDetailSceneStrategy` / `SupportingPaneSceneStrategy` for dynamic multi-pane rendering across wide spatial panels.
3. **Phase 3 (Input Ergonomics):** Update post interaction hit targets in `:designsystem` to 56dp; add hover/focus rings and wire hardware keyboard shortcuts (`j`/`k`, `r`, `Space`).
4. **Phase 4 (Media Immersion):** Implement video player floating controls Orbiter, spatial audio attributes in Media3, and Theater Mode toggle.
5. **Phase 5 (Testing & Quality Gates):** Add Compose Preview screenshot tests for expanded spatial form factors and run unit tests.

## Stacked PR Architecture (`gh-stack`)

To enable granular, focused code reviews while cutting exactly one release upon merge, the implementation is partitioned into an ordered chain of branches rooted on `main`:

```
(main)
  └── feat/nubecita-h6kf.1-xr-manifest-and-deps
        └── feat/nubecita-h6kf.2-xr-spatial-navigation-orbiter
              └── feat/nubecita-h6kf.3-xr-multipane-scene-strategy
                    └── feat/nubecita-h6kf.4-xr-input-ergonomics
                          └── feat/nubecita-h6kf.5-xr-spatial-media
```

- Each layer maps directly to a child bead issue (`nubecita-h6kf.1` through `.5`).
- The entire stack is submitted via `gh stack submit --auto`.
- When all layers pass CI and review, the stack is merged atomically via `gh stack merge --squash --yes`.

## Emulator Testing Strategy

- **Phone Regression:** Validate on `Pixel_10a` AVD (compact screen, NavigationBar at bottom, standard touch targets).
- **Foldable Regression:** Validate on `Pixel_10_Pro_Fold` AVD across closed state (phone screen) and unfolded state (NavigationRail, wide dual-pane layout).
- **Spatial XR Validation:** Validate on `google-xr` AVD running Android XR Developer Preview (floating Orbiter positioning, gaze/pinch hit targets, freeform window resizing, spatial audio).
