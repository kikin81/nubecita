# Nubecita Android UI Kit

A high-fidelity Material 3 Expressive UI kit for Nubecita — a native Android Bluesky client with adaptive layouts.

## Files

- [`index.html`](./index.html) — the full click-through prototype. Open this.
- `Primitives.jsx` — `Icon`, `Avatar`, `Button`, `IconButton`, `FAB`, `Chip`, `TopAppBar`, `BottomNav`, `PostCard`, `PostAction`.
- `Screens.jsx` — `FeedScreen`, `PostDetailScreen`, `ComposerScreen`, `ProfileScreen`, `NotificationsScreen`, `SearchScreen`, `SettingsScreen`, `OnboardingScreen`, `MediaViewerScreen`.
- `Adaptive.jsx` — `NavRail`, `TabletLayout`, `FoldableLayout`.
- `android-frame.jsx` — device bezel (status bar, gesture-nav pill).
- `data.jsx` — fake posts, replies, notifications, suggested users.

## What's interactive

The top phone frame is fully click-through:
- Tap any post → opens post detail with replies
- Tap the FAB or a reply box → composer
- Tap the bottom nav → switch between Home / Search / Alerts / You
- Toggle light / dark at the page top

## Surfaces covered

| Surface | Where |
|---|---|
| Feed (compact) | Interactive phone, top |
| Post detail + thread | Interactive + reference |
| Composer (new + reply) | Interactive + reference |
| Profile | Interactive + reference |
| Notifications | Interactive + reference |
| Search / discover | Interactive + reference |
| Settings (with dark toggle) | Reference |
| Onboarding (3-step) | Reference |
| Media viewer (full-bleed) | Reference |
| Tablet dual-pane | Reference (900 × 650) |
| Foldable three-pane | Reference (1100 × 700) |

## Design notes

- **Feed body is 17 / 26 px** — the one deliberate deviation from M3's 16 / 24 default. Primary reading surface.
- **Buttons are fully pill-shaped** — this is the M3 Expressive tell.
- **Nav-rail pill grows horizontally** on active selection. Expanded rail shows the FAB as a labeled Extended FAB.
- **Liked state flips the icon to filled** (via Material Symbols' `FILL` variable axis) and tints peach — the warm counterweight to the blue primary.
- **Adaptive breakpoints** follow M3 canonical: < 600 dp bottom nav, 600–839 dp nav rail, ≥ 840 dp nav rail expanded + side panel.
