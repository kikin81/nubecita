# Surface token role contract

Nubecita maps each Material 3 `surface*` token to exactly one **depth role**. Code should reason about depth by role and pick the token from this table — never the other way around.

Reading order top→bottom is back→front in the visual stack.

| Depth role | M3 token | What lives there |
|---|---|---|
| **Screen canvas** | `surface` | Scaffold root, modal root, full-screen routes. Every screen paints exactly one of these. |
| **Item card** | `surfaceContainer` | Discrete content objects sitting on the canvas: post cards, chat convo rows, post-position placeholders (`BlockedPostCard`, `NotFoundPostCard`). |
| **Recessed inset** | `surfaceContainerLow` | Anything nested *inside* an item card: quoted posts, external link embeds, "record unavailable" / "unsupported embed" placeholders inside a post body. In dark mode this token is darker than `surfaceContainer`, producing the carved-in feel. |
| **Raised affordance** | `surfaceContainerHigh` | Things sitting on top of an item card: chat message bubbles, day-separator chips, video-poster top gradient stop. Also the **segments of a connected/segmented list** (`NubecitaListGroup` / M3 `SegmentedListItem`, e.g. Settings sections): the 2dp gaps expose the canvas between segments, so the raised tone keeps each segment legible on dark where `surfaceContainer` sits too close to the near-black canvas. |
| **Strong fill** | `surfaceContainerHighest` | Thumbnail placeholders, shimmer base, image-load placeholders, character-counter track, disabled fills. Independent of nesting depth. |
| **Reserved** | `surfaceDim`, `surfaceBright`, `surfaceContainerLowest` | Documented unused in v1. Code review enforces — a custom lint rule was considered (workstream 4) and deferred indefinitely per the nubecita-zw4k decision. |

## Structural rule

> **Anything nested directly inside an item card uses the recessed-inset role**, with one exception: thumbnail / shimmer / placeholder fills always use the strong-fill role regardless of nesting depth.

This is the rule reviewers apply. Code review enforces the nesting check, the reserved-token check, and the `Scaffold` `containerColor` check. A custom lint rule for the mechanizable parts was considered (workstream 4) and deferred indefinitely per the nubecita-zw4k decision — the cascade is fully in place across the codebase and review discipline is sufficient until drift becomes a real problem.

## `background` is a synonym for `surface`

The brand color scheme defines `background` and `surface` to identical values in both light and dark modes. The contract picks `surface` for all canvas paints. Code review enforces `colorScheme.surface` as the canonical name; `colorScheme.background` should not appear in new code outside design-system internals.

## Tonal elevation: windowed surfaces only

Material 3's tonal elevation lift (`Surface(tonalElevation = …)`) is allowed **only** on `surface` tokens representing windowed bounds — `Dialog`, `BottomSheet`, modal `Surface`. It is the depth cue for "this is a floating window."

For in-layout content hierarchy (cards, embeds, rows), tonal elevation is **not** used. Pick the explicit `surfaceContainer*` token for the depth role and skip the elevation knob. This removes the ambiguity of "use a container token or use tonal elevation?" and keeps every in-layout depth step traceable in code.

## Scaffold canvas contract

Every `Scaffold(` call must set `containerColor = MaterialTheme.colorScheme.surface` explicitly. This codifies that Scaffolds own the screen-canvas paint and prevents the silent `background` → `surface` slippage from M3's default. Every existing Scaffold was migrated as part of workstream 3; code review enforces the rule for new ones.

## Where this lives

- **Source of truth (this page)**: `docs/design-system/surface-roles.md`
- **Design spec (decisions and rationale)**: `docs/superpowers/specs/2026-05-23-color-token-cascade-design.md`
- **In code**: KDoc on `nubecitaLightColorScheme()` / `nubecitaDarkColorScheme()` in `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Color.kt` points back here.
- **Preview / screenshot wrappers**: see the next section.

## Preview / screenshot wrappers

Three wrappers cover the preview/screenshot space, each pinned to a different depth role + sizing contract. Pick the right one per fixture; never re-implement the theme + Surface boilerplate inline.

| Wrapper | API shape | Surface paint | Sizing | Use case |
|---|---|---|---|---|
| `@PreviewNubecitaScreenPreviews` | Multi-preview annotation | (handled by callee) | Phone / Foldable / Tablet × Light / Dark sweep | Full-screen Composables that need device-size coverage. Apply on the stateful screen wrapper's preview fixture. |
| `NubecitaCanvasPreviewTheme { … }` | Composable wrapper | `surface` (screen canvas) | `Modifier.fillMaxSize()` | Screen-level, dialog, or pane-level fixtures whose intent is to render against a full-bleed canvas. |
| `@PreviewWrapper(NubecitaComponentPreview::class)` | Annotation on the fixture function (no inline wrapper body) | `surfaceContainer` (item card) | Wraps content's natural bounds (no `fillMaxSize`) | Component-level fixtures: atoms (avatars, buttons), single rows, isolated post cards. Resolves `LocalContentColor` to `onSurface` so dark-mode text doesn't fall back to `Color.Black` against a missing Surface ancestor. |

`NubecitaTheme` directly (no extra wrapper) remains the escape hatch for fixtures that intentionally render without a Surface ancestor — rare, document the reason in a comment if you reach for it.

**Why three:** they map one-to-one to the depth roles a Compose tree expects at render time. Screen-level fixtures want the canvas role; component fixtures want the item-card role; full-screen `Screen.kt` previews want the device-size sweep that the canvas wrapper supplies underneath each preview variant. Sharing a single wrapper across all three would force either the atom-balloon regression (everything stretches to the viewport) or the missing-Surface bug (atoms render without an ancestor, dark-mode text falls back to `Color.Black`).

The `@PreviewWrapper` annotation pattern works under the AGP screenshot test plugin starting Compose BoM `2026.05.01` + plugin `0.0.1-alpha15` — the annotation is reflected at render time and the provider's `Wrap()` Composable surrounds the previewed content. Verified end-to-end in PR #299 (the workstream that introduced `NubecitaComponentPreview`). All three wrapper sources live in `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/`.
