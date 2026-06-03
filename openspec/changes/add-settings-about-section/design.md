## Context

The Settings feature (`:feature:settings`) currently has no "About" surface and no OpenSpec capability of its own. It already exposes a few patterns this change reuses: a `SettingsRow` sealed type rendered through `SettingsSection`, a "Follow Developer" row that deep-links to a developer DID via the `Profile` NavKey, an `Info` row showing the app version, and a `SettingsEffect.LaunchUri` handler that opens external URLs in a `CustomTabsIntent`. Navigation for Settings sub-routes already flows through an `onNavigateTo: (NavKey) -> Unit` callback wired in `SettingsNavigationModule` (`@MainShell`), with `LocalMainShellNavState` read only in the Composable layer.

This change adds an About sub-screen, a Special Thanks section with live contributor data, and an open-source licenses screen. The contributor DIDs are already resolved (see proposal / bd `nubecita-jkg8`).

## Goals / Non-Goals

**Goals:**
- A discoverable About sub-screen reachable from Settings, consistent with existing Settings styling and navigation.
- A "Source on GitHub" link to the public repository.
- A Special Thanks section that renders *live* contributor profile data (avatar, display name, handle) and deep-links to each profile, reusing existing profile-fetch + navigation infrastructure.
- An open-source licenses screen generated from the build graph, styled with the design system's own rows.

**Non-Goals:**
- No new networking layer — reuse `ActorRepository`.
- No changes to the navigation-shell contract — only new `@MainShell` entries via the established pattern.
- No license-text editing/curation UI; the aboutlibraries-generated metadata is the source of truth.
- No offline persistence/caching of contributor profiles beyond what `ActorRepository` already provides.

## Decisions

**D1 — Dedicated About sub-screen, not inline in Settings.**
The libraries list and Special Thanks would bloat the main Settings list. A dedicated `About` route keeps Settings tidy and gives licenses its own space. *Alternative considered:* inline rows/sections in Settings — rejected for length and mixed concerns.

**D2 — Live Special Thanks rows via `ActorRepository.getActor(did)`.**
The app is a Bluesky client; showing real avatars/handles is on-brand and reuses `ActorRepository` (`Flow<ActorUi?>`). A small `AboutViewModel` hydrates a static config list (DID + blurb) into `ThanksRowUi`. *Alternative:* static hardcoded rows (no fetch) — rejected as off-brand, though the per-row fallback effectively degrades to it on failure. The static config (DIDs + blurbs) lives in `:feature:settings:impl`; only avatar/display-name/handle are fetched.

**D3 — Deep-link via the existing `Profile(handle = did)` NavKey.**
`Profile` accepts a handle *or* DID opaquely; the "Follow Developer" row already does this. The thanks rows emit `NavigateToProfile(did)` collected in the Composable → `onNavigateTo(Profile(handle = did))`. No new navigation type.

**D4 — Licenses via the aboutlibraries plugin + `libraryRow` slot, custom-rendered.**
The `com.mikepenz.aboutlibraries` Gradle plugin generates the dependency list at build time. `aboutlibraries-compose`'s `LibrariesContainer` handles loading + scrolling; its `libraryRow` slot (added in `15.0.0-b03`, PR #1386) lets us render each entry with our own design-system row instead of the library's default UI. *Alternatives:* (a) the plugin's stock UI — rejected, doesn't match the design system; (b) parsing `aboutlibraries-core` `Libs` by hand and building the list ourselves — rejected as more code with no benefit now that `libraryRow` exists.

**D5 — External links reuse `SettingsEffect.LaunchUri` / `CustomTabsIntent`.**
Both the GitHub link and per-library URLs route through the existing handler (Custom Tabs, `ActivityNotFoundException` caught). No new URL-opening code.

**D6 — MVI shape per CLAUDE.md.** Flat, UI-ready `AboutState { thanks: ImmutableList<ThanksRowUi>, isLoadingThanks: Boolean }`; effects `NavigateTo` / `NavigateToProfile` / `LaunchUri` collected once in the screen Composable. The licenses screen is effectively stateless (data loaded by `LibrariesContainer`), so it needs no VM beyond emitting `LaunchUri`.

## Risks / Trade-offs

- **aboutlibraries `15.0.0-b03` is a major-version *beta*.** It may not be compatible with the current AGP / Kotlin / Compose, and `libraryRow` is brand-new. → Mitigation: verify it builds (`:app:assembleDebug`) as the *first* implementation step; if incompatible, fall back to the latest stable aboutlibraries and render via `aboutlibraries-core` (D4 alternative b) or pin a compatible version.
- **Live avatar fetch can fail / be slow on the About screen.** → Mitigation: per-row graceful fallback (handle + blurb + placeholder avatar); `isLoadingThanks` drives a lightweight placeholder; never block the screen.
- **`:feature:settings:impl` gains a `:core:actors` dependency.** → Low risk; it's an existing `:core` repository module already used by search/feed.
- **Contributor data is hardcoded (DIDs + blurbs).** → Acceptable; it's a curated credits list, not user data. DIDs (not handles) are stored so a handle change doesn't break the link.

## Migration Plan

Additive only — no migration. New routes/screens/deps; nothing removed or changed in existing behavior. Rollback = revert the PR.

## Open Questions

- None blocking. The only unknown is the aboutlibraries `15.0.0-b03` build compatibility, resolved empirically in the first task (with a documented fallback).
