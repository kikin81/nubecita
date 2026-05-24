# Surface token role contract

Nubecita maps each Material 3 `surface*` token to exactly one **depth role**. Code should reason about depth by role and pick the token from this table — never the other way around.

Reading order top→bottom is back→front in the visual stack.

| Depth role | M3 token | What lives there |
|---|---|---|
| **Screen canvas** | `surface` | Scaffold root, modal root, full-screen routes. Every screen paints exactly one of these. |
| **Item card** | `surfaceContainer` | Discrete content objects sitting on the canvas: post cards, settings section cards, chat convo rows, post-position placeholders (`BlockedPostCard`, `NotFoundPostCard`). |
| **Recessed inset** | `surfaceContainerLow` | Anything nested *inside* an item card: quoted posts, external link embeds, "record unavailable" / "unsupported embed" placeholders inside a post body. In dark mode this token is darker than `surfaceContainer`, producing the carved-in feel. |
| **Raised affordance** | `surfaceContainerHigh` | Things sitting on top of an item card: chat message bubbles, day-separator chips, video-poster top gradient stop. |
| **Strong fill** | `surfaceContainerHighest` | Thumbnail placeholders, shimmer base, image-load placeholders, character-counter track, disabled fills. Independent of nesting depth. |
| **Reserved** | `surfaceDim`, `surfaceBright`, `surfaceContainerLowest` | Documented unused in v1. The detekt rule rejects external usage. |

## Structural rule

> **Anything nested directly inside an item card uses the recessed-inset role**, with one exception: thumbnail / shimmer / placeholder fills always use the strong-fill role regardless of nesting depth.

This is the rule the lint reviewer applies. Code review enforces the nesting check; detekt enforces the reserved-token check and the `Scaffold` `containerColor` check.

## `background` is a synonym for `surface`

The brand color scheme defines `background` and `surface` to identical values in both light and dark modes. The contract picks `surface` for all canvas paints. Detekt forbids `colorScheme.background` outside design-system internals so the canonical name is the only one in code.

## Tonal elevation: windowed surfaces only

Material 3's tonal elevation lift (`Surface(tonalElevation = …)`) is allowed **only** on `surface` tokens representing windowed bounds — `Dialog`, `BottomSheet`, modal `Surface`. It is the depth cue for "this is a floating window."

For in-layout content hierarchy (cards, embeds, rows), tonal elevation is **not** used. Pick the explicit `surfaceContainer*` token for the depth role and skip the elevation knob. This removes the ambiguity of "use a container token or use tonal elevation?" and keeps every in-layout depth step traceable in code.

## Scaffold canvas contract

Every `Scaffold(` call sets `containerColor = MaterialTheme.colorScheme.surface` explicitly. This codifies that Scaffolds own the screen-canvas paint and prevents the silent `background` → `surface` slippage from M3's default. The detekt rule fails the build if a `Scaffold(` omits `containerColor`.

## Where this lives

- **Source of truth (this page)**: `docs/design-system/surface-roles.md`
- **Design spec (decisions and rationale)**: `docs/superpowers/specs/2026-05-23-color-token-cascade-design.md`
- **In code**: KDoc on `nubecitaLightColorScheme()` / `nubecitaDarkColorScheme()` in `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Color.kt` points back here.
- **Preview / screenshot wrapper**: `NubecitaScreenPreviewTheme` in `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/` paints the screen-canvas role for previews and screenshot fixtures (workstream 2).
