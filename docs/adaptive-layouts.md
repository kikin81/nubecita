# Adaptive layouts: phone full-screen / tablet dialog

> One screen, two presentations, **one route**. The canonical "this is
> full-screen on a phone but a modal on a tablet" pattern is a **Navigation 3
> scene strategy** + a metadata tag — not a per-feature launcher/overlay.

## The principle

Don't think in terms of a "phone layout" and a separate "tablet layout" wired by
hand. Following the Google I/O 2026 adaptive-layouts direction (and the
`/navigation-3` + `/adaptive` Claude CLI skills), a destination declares **how it
wants to be presented**, and a shared **`SceneStrategy`** decides the container
per window size. `MainShell`'s inner `NavDisplay` already does this for
list-detail (`ListDetailSceneStrategy`); the dialog case is the same mechanism.

There is **no** built-in Compose/M3 component that flips full-screen↔dialog on
width (verified against `material3.adaptive`, the canonical layouts, and
`BasicAlertDialog`). Google's own guidance is "branch on the window size class
and reuse the content," which is exactly what a scene strategy centralizes —
once, for every feature.

## The opt-in (all a feature does)

Tag the route's `entry` with `adaptiveDialog()` (module `:core:common`, package
`net.kikin.nubecita.core.common.navigation` —
`core/common/src/main/kotlin/.../navigation/AdaptiveDialogMetadata.kt`):

```kotlin
import net.kikin.nubecita.core.common.navigation.adaptiveDialog

entry<EditProfile>(metadata = adaptiveDialog()) { route ->
    // the ordinary, full-screen-capable screen — unchanged
    val vm = hiltViewModel<EditProfileViewModel, EditProfileViewModel.Factory>(
        creationCallback = { it.create(route) },
    )
    EditProfileScreen(viewModel = vm, onNavigateBack = { navState.removeLast() })
}
```

The push site stays a plain `navState.add(EditProfile(...))` at **every** width.
That's it — no launcher, no overlay state, no `CompositionLocal`, no Dialog host,
and the screen/ViewModel stay feature-`internal`.

## How it works

`AdaptiveDialogSceneStrategy` (module `:app` —
`app/src/main/java/net/kikin/nubecita/shell/adaptive/AdaptiveDialogSceneStrategy.kt`)
is a width-gated `OverlayScene` strategy:

- **Compact (`< 600dp`)** → `calculateScene` returns `null`, so the entry falls
  through to the default single-pane scene = **full-screen**.
- **Medium / Expanded (`>= 600dp`)** → if the entry carries the `adaptiveDialog()`
  metadata, it renders inside a `Dialog` (`usePlatformDefaultWidth = false`,
  `decorFitsSystemWindows = false`) with a manual scrim + a centered **640dp**
  `Surface` card. Because it's an `OverlayScene`, the **previous destination
  stays composed underneath** (`overlaidEntries`) — so a screen behind the
  dialog keeps its state and observers alive (e.g. the profile re-fetches via its
  `ownProfileUpdates` signal while the editor is open).

It's wired into `MainShell` first in the strategy list (overlay strategies must
precede non-overlay ones):

```kotlin
val dialogStrategy = rememberAdaptiveDialogSceneStrategy<NavKey>()
NavDisplay(
    sceneStrategies = listOf(dialogStrategy, listDetailStrategy), // overlay FIRST
    // …
)
```

The strategy is a near-verbatim clone of navigation3's built-in
`DialogSceneStrategy` (which always dialogs) plus `ListDetailSceneStrategy`'s
Compact width gate, with the scrim/card chrome the built-in lacks.

## Do / don't

- **Do**: add `metadata = adaptiveDialog()` to the route's `entry`.
- **Don't**: hand-roll a per-feature `FooLauncherState` + `rememberFooLauncher` +
  `FooOverlay` + `FooOverlayState` + `LocalFooLauncher` + `MainShell` wiring. That
  was ~10 files per surface and is the exact thing this convention replaces.
- **Don't**: special-case the route in a generic `onNavigateTo`/`navState.add`;
  the strategy handles presentation, so the push stays generic.

## Where things live

| Piece | Module | File |
|---|---|---|
| `adaptiveDialog()` + `AdaptiveDialogKey` (pure metadata opt-in feature modules reference) | `:core:common` | `core/common/src/main/kotlin/net/kikin/nubecita/core/common/navigation/AdaptiveDialogMetadata.kt` |
| `AdaptiveDialogSceneStrategy` / `AdaptiveDialogScene` / `rememberAdaptiveDialogSceneStrategy` (the `OverlayScene` + width gate + Dialog chrome) | `:app` | `app/src/main/java/net/kikin/nubecita/shell/adaptive/AdaptiveDialogSceneStrategy.kt` |
| `MainShell` `sceneStrategies` wiring (registers the strategy on the inner `NavDisplay`) | `:app` | `app/src/main/java/net/kikin/nubecita/shell/MainShell.kt` |
| Reference opt-in (one metadata tag) | `:feature:profile:impl` | `.../feature/profile/impl/di/EditProfileNavigationModule.kt` |

## List-detail pane scoping (`ListDetailSceneStrategy`)

The same inner `NavDisplay` also hosts the tablet/foldable **list-detail** panes
(Feed/Search/Chats lists with a detail pane). Two rules keep panes spatially
sane on Medium/Expanded; both are mechanical and need no per-feature code beyond
the metadata tag:

1. **Panes are scoped to the active tab's segment.** `MainShellNavState.backStack`
   concatenates the start tab's full stack with the active tab's stack (so
   system/predictive back can "exit through home"). Handing that whole
   concatenation to Material3's `ListDetailSceneStrategy` made a tab switch render
   the *previous* tab's detail next to the *new* tab's list (e.g. `Chats list |
   Feed's PostDetail`), because the strategy picks the last detail entry across
   **all** tabs. `ActiveTabScopedSceneStrategy` wraps the list-detail strategy and
   slices the entries to the active tab's segment — found by **key-matching** the
   active tab's root (`MainShellNavState.topLevelKey`) inside the entries actually
   passed, not an absolute index, so it's immune to the size skew between
   `backStack` and the synthetic `previousEntries` NavDisplay hands the strategy
   during predictive back — before delegating. The sliced-off entries stay in the
   back stack and keep serving back. (nubecita-xqp7 / nubecita-s1f3.)

2. **A sub-route opened from a detail pane stacks in the detail pane** — it must
   not re-anchor the list. The trap: a route that is *also* a top-level tab (e.g.
   `Profile`) carries `listPane()` metadata, so pushing it from a detail pane
   re-anchored the list to it, evicting the list you were browsing. Fix: give such
   a route **instance-dependent** metadata via the `entry(metadata = { key -> … })`
   DSL form — `Profile(handle = null)` (the tab root) is `listPane()`, while
   `Profile(handle = "…")` (an author sub-route) is `detailPane()` so it stacks in
   the detail pane and the list stays put. See `profilePaneMetadata`.

> **North star (not yet built):** the Nav3 three-pane "push" — the list pane
> slides out and detail + the new pane slide in side-by-side — is the eventual
> ideal for "open a profile from a post". v1 keeps the list in place and swaps the
> detail-pane content (the MVP above). Tracked with the list-detail epic.

Where the list-detail pieces live:

| Piece | Module | File |
|---|---|---|
| `ActiveTabScopedSceneStrategy` (active-tab slice wrapper) | `:app` | `app/src/main/java/net/kikin/nubecita/shell/adaptive/ActiveTabScopedSceneStrategy.kt` |
| `topLevelKey` (the active tab the wrapper key-matches on) | `:core:common` | `core/common/.../navigation/MainShellNavState.kt` |
| `profilePaneMetadata` (instance-dependent list/detail role) | `:feature:profile:impl` | `.../feature/profile/impl/di/ProfileNavigationModule.kt` |

## Entry state retention

Every entry on the inner `NavDisplay` keeps its ViewModels and `rememberSaveable`
state for as long as it is on its tab's stack — across tab switches, fold /
unfold and rotation, and (for saved state) process death. Three rules make that
hold; breaking any of them brings back a bug the `nubecita-owmt` epic fixed:

1. **Decorate every tab's stack, not the displayed one.** Navigation 3 clears an
   entry's `ViewModelStore` and saved state once its key leaves the list being
   decorated. `MainShellNavState.backStack` only holds the start tab's stack plus
   the active tab's, so decorating it wiped a tab each time the user left it.
   `MainShell` therefore passes `NavDisplay(entries = …)` the result of
   `MainShellNavState.rememberDecoratedEntries`, which runs
   `rememberDecoratedNavEntries` once per tab (the Navigation 3
   multiple-back-stacks recipe shape).
2. **Never wrap the `NavDisplay` or its decorators in a `key(...)` on window
   size.** The ViewModel decorator clears all of its stores when it leaves
   composition, so re-keying on the pane count rebuilt every screen on each fold.
   `MainActivity` handles size changes via `configChanges`, so state simply
   carries across the one-pane ↔ two-pane crossing.
3. **Sub-routes get a scoped `contentKey`.** Navigation 3 treats entries with the
   same `contentKey` as one piece of content. `scopedEntries` (in
   `MainShellNavState.kt`) re-keys every sub-route by tab and by occurrence, so
   the same post open on two tabs, or twice on one, is two screens. Tab roots
   keep their registered key — `ActiveTabScopedSceneStrategy` matches on it.

The expected behaviour is written down as android-cli journeys:
`app/src/journeysTest/tab_state_retention.journey.xml` and
`pane_boundary_state_retention.journey.xml`. Run both on a foldable after
touching `MainShell`, `MainShellNavState` or a scene strategy.
`MainShellEntryStateRetentionTest` (`:app` androidTest) covers rules 1 and 3 by
reading entry state, not stack keys.

## Foldable tabletop posture (`Grid` + `mediaQuery`)

Scene strategies answer "which screens are on screen". Posture is a different
question: one screen, the same children, a different geometry. When a foldable
is half-opened with the hinge horizontal, the fullscreen video player puts the
video on the upright half and the controls on the flat half. It does that
without a second layout composable.

**The pattern** (`:feature:videoplayer:impl`, `ui/VideoPlayerGrid.kt`):

- One `Grid` with named areas. The children are tagged once with
  `Modifier.gridItem(areaId = ...)`; only the `GridConfigurationScope.() -> Unit`
  that maps area ids to cells changes. Flat maps both areas to one cell (the
  controller overlays the player by declaration order); tabletop maps them to
  two `0.5f` rows.
- `Grid` reads its config in the **measure** pass, so a fold is a relayout, not
  a recomposition. An `if (tabletop) A() else B()` branch would dispose and
  recreate the `PlayerSurface`; this never does. Verified on device: same
  SurfaceFlinger layer, no activity lifecycle events, position keeps advancing.
- Posture comes from `mediaQuery { windowPosture == UiMediaScope.Posture.Tabletop }`,
  read **in composition** by the stateful screen (`isTabletopPosture()`) and
  passed down as a `Boolean`. Never call `mediaQuery` inside the
  `Grid(config = { ... })` block, and keep it out of stateless `*Content`
  composables so previews and screenshot fixtures need no `LocalUiMediaScope`.

**Rules that are easy to get wrong:**

1. **The flag.** `LocalUiMediaScope` only exists when
   `ComposeUiFlags.isMediaQueryIntegrationEnabled` is set before the first
   composition (`NubecitaApplication.onCreate`). Reading it otherwise throws.
   Instrumented tests boot `HiltTestApplication`, which does not set it, so any
   `mediaQuery` call reachable from a journey must be guarded on the flag
   (`isTabletopPosture()` is; tabletop simply never activates there).
2. **The grid spans the whole window.** Its 50/50 split only lands on the fold
   if nothing pads it unevenly. Take the host `Scaffold`'s `innerPadding` as a
   `contentPadding` parameter and apply it per area (the edge on the fold gets
   none), rather than `Modifier.padding(innerPadding)` on the grid. Padding the
   grid moved the split 12dp off the fold on a Pixel Fold.
3. **Aspect-fit under loose constraints.** `fillMaxSize().aspectRatio(r)` only
   ever fits the width and overflows a cell that is wider than the content. In a
   grid cell use `aspectRatio(r)` alone.
4. **Controls get their own stacked config.** A half-height panel is too short
   for edge-pinned bands that share one cell, so `VideoPlayerChrome` is a nested
   grid: one cell when overlaid, three rows when `stacked`.
5. **Pin what would auto-hide.** Controls that fade out over content have
   nothing to uncover when they own half the screen.

**Known limit:** the split assumes the fold is the window's horizontal midline.
That holds for a fullscreen or side-by-side window, not for a freeform window
that the fold crosses off-center (`nubecita-275s`).

Journey: `app/src/journeysTest/video_player_tabletop.journey.xml`, driven on a
foldable emulator by `scripts/tabletop-video-journey.sh` (posture via
`adb emu posture 2|3`), which asserts the bounds against the fold, the playback
position and the surface identity.

## Versioning note

`Grid` and `mediaQuery` are **experimental in Compose 1.12** (`ExperimentalGridApi`,
`ExperimentalMediaQueryApi`, and the `ComposeUiFlags` opt-in above). On a Compose
BOM bump, re-check `VideoPlayerGrid.kt` against the in-artifact `Grid.kt` /
`MediaQuery.kt`, drop the opt-ins and the flag once they are no longer needed,
and re-run the tabletop journey.


Built against **`androidx.navigation3` `1.2.1`** and
**`androidx.compose.material3.adaptive` `1.3.0`** (`nav3Core` /
`nav3AdaptiveMaterial3` in `gradle/libs.versions.toml`). When bumping either,
re-check the custom scenes against the in-artifact
`androidx/navigation3/scene/DialogScene.kt` and `SceneStrategySamples.kt`
(authoritative source for the exact signatures) rather than the docs, and re-run
the two journeys above.

## References

- Claude CLI skills: `/navigation-3` (nav3 scenes recipes — **Dialog**,
  **BottomSheet** `OverlayScene`, list-detail) and `/adaptive` (M3 adaptive,
  Navigation 3 Scenes).
- [nav3-recipes](https://github.com/android/nav3-recipes) — `dialog/`,
  `bottomsheet/BottomSheetSceneStrategy.kt` (the `OverlayScene` template),
  `scenes/listdetail/` (width-gated strategy).
- [Navigation 3 — custom layouts with Scenes](https://developer.android.com/guide/navigation/navigation-3/scenes)

## Migration

Done. The composer was the last surface on the hand-rolled
launcher/overlay/`CompositionLocal` pattern; it moved to `adaptiveDialog()` in
**`nubecita-11st`**. New adaptive surfaces mirror the scene-strategy approach.
