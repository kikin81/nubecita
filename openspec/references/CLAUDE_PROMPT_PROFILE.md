# Implementation Prompt — Nubecita Profile (Bold variant)

> ⚠️ **This is reference input, not the locked design.**
>
> This file is the original prompt that came out of `claude.ai/design` — preserved verbatim for provenance. The actual implementation contract for the Profile screen is in [`openspec/changes/add-profile-feature/`](../changes/add-profile-feature/) and the requirements specs under [`specs/feature-profile/`](../changes/add-profile-feature/specs/feature-profile/spec.md) and [`specs/design-system/`](../changes/add-profile-feature/specs/design-system/spec.md).
>
> Known divergences (locked decisions in `design.md`):
>
> - **Hero treatment** — the prompt below describes a "Classic banner" hero (banner image as backdrop with a 0.55 black scrim and Palette-driven adjustment against the *avatar*). The locked design (`design.md` Decision 1) is the **Bold-derived** variant: the banner image is NOT rendered; only its palette is extracted (off-main, cached) and used to derive a gradient. Avatar-hue fallback when no banner. The black scrim against the banner is irrelevant to the locked design.
> - **State shape** — the prompt names the state `ProfileUiState`. The locked design uses `ProfileScreenViewState` with three independent `TabLoadStatus` fields (one per tab). See `feature-profile/spec.md` for the canonical contract.
> - **Adaptive Expanded layout** — the prompt describes a 3-pane layout with a 280 dp side panel (suggested follows + pinned feeds). The locked scope (`design.md` non-goals) defers the side panel to a follow-up epic; Expanded falls back to the Medium 2-pane.
> - **Writes** — Follow / Unfollow / Edit / Message in this prompt are write actions. The locked scope ships them as **stubs** that emit `ProfileEffect.ShowMessage("Coming soon")`; the real network is its own follow-up epic.
>
> When in doubt, the spec wins. This prompt is here so the diff between "what was asked for" and "what was scoped" stays auditable.

---

Paste this into Claude Code at the root of the `android-app` repo. The HTML bundle in `design_handoff_profile_page/` is the source of truth for layout, tokens, and behavior.

---

## Prompt

> I'm implementing the **user Profile screen** for Nubecita (native Android, Jetpack Compose + Material 3 + Material 3 Adaptive). The design reference lives in `design_handoff_profile_page/` — open `README.md` first, then `Profile-Bold.html` in a browser to interact with the canvas.
>
> **The HTML is a reference, not code to copy.** Recreate it in Compose using our existing modules and conventions. Do NOT translate JSX 1:1.
>
> ### Before you write any UI
> 1. Read `design_handoff_profile_page/README.md` end to end.
> 2. Map the existing repo: identify the **design-system module** (tokens, theme, typography, shapes, motion specs) and the **profile feature module** (or create one following the pattern used by feed/composer). Tell me what you find before generating code.
> 3. Confirm we're using `androidx.compose.material3` + `androidx.compose.material3.adaptive.*` and `androidx.window.core.layout.WindowSizeClass`. If versions are pinned, list them.
> 4. Check whether tokens for `--primary-container`, `--surface-container-low/high`, `--outline-variant`, the **Fraunces** display family (with the `SOFT` axis), **Roboto Flex**, **JetBrains Mono**, and the spring-motion specs already exist. If any are missing, add them to the design-system module first — don't hard-code values inside the feature.
>
> ### Then build, in this order
> 1. **State + data layer** — `ProfileViewModel` exposing a `ProfileUiState` (profile, posts/replies/media flows, selected post id, follow state, tab state). Posts come from `app.bsky.feed.getAuthorFeed`, profile from `app.bsky.actor.getProfile`. Use whatever pattern the feed module already uses for AT-Proto access.
> 2. **`ProfilePane`** composable — the actual profile UI; takes a `dense: Boolean` and an optional `onOpenPost`. This is the unit reused across all three size classes.
>    - Hero card: **user banner as background** (`profile.banner` blob), gradient-from-`avatarHue` fallback (use `androidx.palette.graphics.Palette` against the avatar bitmap in production), 0.55 black scrim bottom-up, 28 dp corner radius.
>    - Avatar: 80–96 dp on a 4 dp `surface` ring with elevation 2.
>    - Name in Fraunces 600, `SOFT` axis = 70, letter-spacing −0.02 em, white over scrim.
>    - Handle in JetBrains Mono 13 sp.
>    - Bio: 14.5 sp / 21 sp line-height, `textWrap = Pretty`.
>    - Actions row: primary (Follow / Following / Edit), Message (when other), overflow.
>    - Stats: **inline** `412 Posts · 2.1k Followers · 342 Following` (we explicitly dropped the chip variant).
>    - Meta row: link / location / joined with 14 dp icons.
>    - Tabs: M3E **pill tabs** (Posts / Replies / Media), 36 dp, `--primary` fill on active + `FILL` axis 1 on the icon.
>    - Body: post list / replies list / 3-col media grid.
> 3. **Adaptive scaffolding** — use `androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold` for the nav rail/bottom-bar swap, and `ListDetailPaneScaffold` for Medium/Expanded.
>    - **Compact (<600 dp):** single pane + bottom nav + FAB anchored 96 dp from bottom right.
>    - **Medium (600–839 dp):** nav rail (88 dp) + 380 dp profile pane + flex detail pane.
>    - **Expanded (840–1199 dp):** extended nav rail (220 dp) + 440 dp profile pane + flex detail pane + 280 dp side panel (suggested follows + pinned feeds).
> 4. **Motion** — every pill button uses the `--ease-spring-fast` analog (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMediumLow`); press scales to 0.96 for ~100 ms. Honor reduced-motion settings — fall back to standard easing, halved duration, no overshoot.
> 5. **Scroll-collapsing header** — the hero card collapses on scroll into the top app bar; the bar gains `surfaceContainer` at 80% opacity with backdrop blur, name+handle compress into the title slot, pill tabs pin under the bar. Use `TopAppBarScrollBehavior` + a custom collapsing modifier.
>
> ### What to look for / get right
> - **Token discipline.** Every color, radius, type style, and motion spec must come from the design-system module. If you find yourself typing a hex, stop and add a token.
> - **Window size class is *runtime*, not static breakpoints.** Use `currentWindowAdaptiveInfo()` so it reacts to foldable posture changes and split-screen.
> - **Banner contrast.** The scrim has to keep WCAG AA legibility against any real banner image — write a Palette-driven adjustment if a banner is unusually light.
> - **Material Symbols Rounded** as a variable font. The `FILL` axis toggles 0→1 on active states (matches the JSX `fill={active ? 1 : 0}`); don't ship two icon sets.
> - **Avatar fallback.** No avatar → colored initial, generated from a stable hash of the handle (not the display name).
> - **List-detail empty state.** Detail pane with no `selectedPostId` shows the cloud illustration + "Pick a post to read its thread" — copy verbatim.
> - **Compact opens detail as a full screen.** Don't try to cram list-detail into a phone; navigate to a separate `PostDetailScreen`.
> - **A11y.** `Modifier.semantics` on the hero (`heading`, `liveRegion = Polite` for follow toggle), 48 dp min hit targets for every icon button, content descriptions for the avatar and stats row, focus order: back → share → overflow → primary action → tabs → list.
> - **Theming.** Wire the colors block in `README.md` into a Material 3 color scheme; verify both light and dark by toggling `isSystemInDarkTheme()`.
> - **Preview annotations.** Add `@PreviewScreenSizes` (or explicit `widthDp` previews at 412 / 720 / 1024) for every public composable so the team can keep verifying.
>
> ### Before declaring done
> - Run lint + spotless / ktlint, whatever the repo uses.
> - Snapshot test or Compose UI test the three size classes at light & dark.
> - Make sure scrolling the post list does NOT re-fetch the profile — viewmodel state must be stable across recompositions.
> - Verify back-press in the list-detail empty state behaves correctly (closes detail, doesn't pop the profile).
>
> Tell me what you found in the repo before writing code. If the design-system module is missing any token, propose the token additions in a small PR before the feature PR.

---

## Things I (the designer) would specifically grep the diff for

- `Color(0xFF...)` literals inside the profile module → should be tokens
- `dp` constants outside `Spacing` / `Sizes` objects → should be tokens
- Hard-coded `WindowWidthSizeClass.Compact` instead of `currentWindowAdaptiveInfo()`
- Any reference to a static `Color` for banner overlay instead of the gradient scrim
- A `LazyColumn` inside the hero card area (it should be one outer scroll container so the hero collapses correctly)
- Duplicate motion specs — there should be ~3 named springs reused everywhere
- Missing `Modifier.semantics { heading() }` on the name
- Imperative `if (windowSize == ...) { ... } else { ... }` ladders that should be `NavigationSuiteScaffold` or `ListDetailPaneScaffold`
