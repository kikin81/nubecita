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

## Versioning note

Built against **`androidx.navigation3` `1.2.0-alpha03`** — `OverlayScene`,
`SceneStrategyScope.onBack`, and the built-in `DialogSceneStrategy` are present.
The scene-strategy APIs are alpha; when bumping, re-check against the in-artifact
`androidx/navigation3/scene/DialogScene.kt` and `SceneStrategySamples.kt`
(authoritative source for the exact signatures) rather than the docs.

## References

- Claude CLI skills: `/navigation-3` (nav3 scenes recipes — **Dialog**,
  **BottomSheet** `OverlayScene`, list-detail) and `/adaptive` (M3 adaptive,
  Navigation 3 Scenes).
- [nav3-recipes](https://github.com/android/nav3-recipes) — `dialog/`,
  `bottomsheet/BottomSheetSceneStrategy.kt` (the `OverlayScene` template),
  `scenes/listdetail/` (width-gated strategy).
- [Navigation 3 — custom layouts with Scenes](https://developer.android.com/guide/navigation/navigation-3/scenes)

## Migration

The composer (`:app/shell/composer/*`) predates this and still uses the
hand-rolled launcher/overlay/`CompositionLocal`. Tracked by **`nubecita-11st`**:
tag `ComposerRoute` with `adaptiveDialog()`, delete the composer launcher/overlay
files, and keep its submit-events bus (it lives in `:core:common` and is reached
from the composer content, unaffected by presentation). New adaptive surfaces
should mirror the **scene-strategy** approach, not the composer.
