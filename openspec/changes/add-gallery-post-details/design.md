## Context

The gallery is an `@OuterShell` entry so the shared image transition runs in the outer `NavDisplay`. It can't read `LocalMainShellNavState`, and `Profile` and `PostDetailRoute` exist only on the inner shell. `ComposerRoute` is registered on both.

## Decisions

- **The gallery stays on the outer shell.** Moving it would break the shared image transition.
- **`rememberPostInteractions` takes `onNavigate`**, defaulting to the shell's stack, so an outer-shell screen can pass its own.
- **Composer opens on the outer stack**, above the gallery.
- **Inner routes go through the existing `DeepLinkRouter`**, via `Navigator.closeAndOpenInMainShell`. `MainShell` may leave composition while the gallery is up, so a direct call can't reach it, and the router already holds a route until the shell collects it.
- **The ViewModel keeps the fetched post.** The route stays primitive.

## Risks

- The overlay's gesture surface could compete with pinch-zoom and swipe-down. Covered by a device pass.
- White text on a bright photo. The gradient is tuned against a bright image.

Full design: `docs/superpowers/specs/2026-11-06-gallery-post-details-design.md`.
