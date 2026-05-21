# Design Spec: Refactor Profile screen to match expressive design

## Context
The current Profile screen uses a generated gradient as the header background, derived from the user's banner image via `BoldHeroGradient`. While functional, it hides the actual banner image. The layout also uses a complex `Modifier.layout` shift and manual inset calculations to achieve a "draw behind top bar" effect. We want to modernize this screen to match the Material 3 Expressive design patterns seen in the Google Contacts app.

## Goals
*   Display the actual banner image in the profile header.
*   Overlap the user's avatar with the banner and content area.
*   Implement a sticky action button group (Edit/Follow, Message) using M3 Expressive `ButtonGroup`.
*   Simplify the layout logic by using standard M3 Expressive components and removing hacky shifts.
*   Ensure the button group sticks with the top app bar as the user scrolls.

## Decisions

### Decision 1: Render actual banner image
We will replace `BoldHeroGradient` with a layout that renders the actual `bannerUrl`.
*   Use `NubecitaAsyncImage` for the banner.
*   Apply a bottom-aligned scrim (gradient to transparent) to ensure that text or elements rendered over the banner remain legible.
*   The banner will remain edge-to-edge at the top of the screen.

### Decision 2: Expressive "Overlapping Avatar" Layout
We will adopt the Material 3 Expressive "overlapping avatar" pattern:
*   The avatar will be centered horizontally (or start-aligned, matching the Contacts app's expressive treatment).
*   The avatar will sit on the boundary between the banner and the profile info (bio, stats).
*   Avatar size: 96dp (increased from 88dp for better expressive presence).
*   Avatar ring: A thicker surface-colored ring to detach it from the background.

### Decision 3: Sticky "Verbs" Button Group
We will introduce a `ButtonGroup` (M3 Expressive) for the primary profile actions, referred to as "Verbs" in the Contacts app.
*   **Actions**:
    *   Own Profile: `Edit profile`, `Settings` (icon), `...` (overflow).
    *   Other Profile: `Follow` (Primary), `Message` (Tonal), `...` (Overflow).
*   **Sticky Behavior**: This button group will be placed in a `stickyHeader` in the `LazyColumn`. It will dock just below the `TopAppBar` when scrolled.
*   **Styling**: Use `ButtonGroup` with `tonalItem` or `toggleableItem` as appropriate.

### Decision 4: Simplify Layout and Top Bar
We will remove the `Modifier.layout` hack.
*   Use `Scaffold` with a `TopAppBar` that transitions its alpha based on the scroll position.
*   The `LazyColumn` will use `WindowInsets.statusBars` to properly handle the top area.
*   The header content (banner + avatar + info) will be part of the first items in the `LazyColumn`.

### Decision 5: Combine Verbs and Tabs in Sticky Header
To avoid multiple competing sticky headers, we will explore combining the "Verbs" (Actions) and the "Pill Tabs" into a single sticky structure if necessary, or ensure they stack cleanly.
*   Initial approach: Actions (Verbs) row sticks, then Tabs row sticks below it.

## Risks / Trade-offs
*   **Occlusion**: Multiple sticky headers can consume significant screen real estate on smaller devices. We will monitor the "sticky budget" during implementation.
*   **Contrast**: Showing the real banner image makes text legibility harder. The scrim must be robust.
*   **Performance**: Loading a large banner image + avatar + feed can be heavy. Ensure Coil's memory management is efficient here.

## Open Questions
*   Should the buttons be circular (like Contacts) or pill-shaped (standard M3)? The user mentioned "material expressive button group", which usually implies the pill-shaped segmented controls, but the Contacts app uses circles.
    *   *Resolution*: Use pill-shaped `ButtonGroup` for "Edit profile" and "Follow", but consider circular tonal buttons for secondary actions like "Message" to match the Contacts vibe.
