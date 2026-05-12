## Why

Nubecita's "You" tab and every cross-tab handle tap currently land on the `:app`-side placeholder Composable registered for the `Profile` NavKey — there is no real profile screen. Without one, the app fails the basic "who am I, who is this person" test that every other Bluesky / Twitter-style client passes, and any future write surface (follow, edit, message) has nowhere to anchor. The screen also has to cover both `Profile(handle = null)` (the authenticated user's own profile) and `Profile(handle = "alice.bsky.social")` (another user, pushed onto the active tab's back stack), since both share a single hybrid NavKey by design (see `:feature:profile:api/Profile.kt`).

This change lands the Profile screen — read-only viewing, full breadth (hero card + meta + stats + three tabs: Posts / Replies / Media) — so the You tab stops being a placeholder and feed-side handle taps reach a real destination. Writes are explicitly deferred: Follow / Edit / Message ship as stubbed affordances that surface a "Coming soon" snackbar, leaving the network and optimistic-UI work to follow-up epics.

The hero treatment anchors on the "Bold Expressive" variant from the wireframes (`openspec/references/{compact,medium,expanded}.jpg`, variant **B**), with one sanctioned departure: the user's banner image isn't rendered, but its palette IS preserved — `androidx.palette.graphics.Palette` extracts a swatch off the main thread and the hero gradient is derived from it (avatarHue fallback when no banner). This keeps the bold M3 Expressive look without throwing away the user's identity signal.

## What Changes

### Module structure

- **NEW** `:feature:profile:impl` module, applying `nubecita.android.feature`. Owns the screen, ViewModel, repository, mappers, and Hilt navigation module.
- `:feature:profile:api` (already exists) is unchanged — `Profile(handle: String? = null)` + `Settings` NavKeys stay the same.
- `:app`'s `MainShellPlaceholderModule` loses its `Profile` placeholder when `:feature:profile:impl` graduates from `:api` stub to real implementation (per the feature-module sequencing rule in `CLAUDE.md`). The `Settings` placeholder also gets replaced — `:feature:profile:impl` ships a one-screen "Settings — coming soon" stub with a Sign Out button until a real Settings epic lands.

### Data layer

- `ProfileRepository` (in `:feature:profile:impl/data/`) fetches profile headers via `app.bsky.actor.getProfile` and tab bodies via `app.bsky.feed.getAuthorFeed` (three calls per profile load, one per tab, with `filter = posts_no_replies` / `posts_with_replies` / `posts_with_media`).
- **NEW** `AuthorProfileMapper` (inline in `:impl` — single consumer, per YAGNI): atproto `actor.defs#profileViewDetailed` → `ProfileHeaderUi`. No extraction to `:core:*` until a second consumer surfaces.
- `AuthorFeedMapper` delegates embed + post-core mapping to `:core:feed-mapping` (the helpers already extracted by `add-postdetail-m3-expressive-treatment`). Filter-specific glue stays inline.
- ViewModel-layer pagination per tab mirrors `:feature:feed:impl`'s pattern: `cursor`-based, append-on-scroll, pull-to-refresh resets the cursor.

### UI layer

- `ProfileScreen` + `ProfileScreenContent` split (per the established `FeedScreen` / `FeedScreenContent` pattern), so previews and screenshot tests can render content without booting the ViewModel.
- Hero card (collapsing later — sticky for v1): bold-derived gradient backdrop (Palette-from-banner, avatarHue fallback), 80–96dp avatar on a 4dp surface ring, name in **Fraunces 600** with the variable `SOFT` axis at 70, handle in **JetBrains Mono** 13sp, bio at 14.5sp / 21sp `lineHeight` with `textWrap = Pretty`.
- Inline stats row (`412 Posts · 2.1k Followers · 342 Following`) — the chip variant from the prompt is explicitly dropped.
- Meta row: link / location / joined with 14dp icons via the existing `NubecitaIcon`.
- Actions row: primary action (Follow if other / Edit if own) + Message (if other) + overflow. All three ship as **stubs** in this epic — tapping any surfaces a `ProfileEffect.ShowMessage("Coming soon")` snackbar.
- **NEW** `ProfilePillTabs` design-system composable (3 pill tabs, 36dp, primary fill on active, FILL-axis 1 on the active icon — the existing `NubecitaIcon` already supports the FILL axis toggle).
- Tab bodies: `LazyColumn` of `PostCard`s for Posts/Replies, `LazyVerticalGrid` (3 columns) of media thumbs for Media. Each tab tracks its own `TabLoadStatus` so loading one doesn't blank the others.

### Design system additions (Beads A + B — land first, risk-isolated)

- **NEW** Fraunces variable font + `SOFT` axis exposure on the `Typography` tokens (the prompt requires `SOFT = 70` on the display name).
- **NEW** JetBrains Mono variable font + typography tokens for the handle.
- **NEW** `BoldHeroGradient` composable + `rememberBoldHeroGradient(banner: String?, avatarHue: Int)` — single owner of the Palette extraction (off-main, cached per banner blob) + avatarHue fallback. `banner` is a nullable URL string — matches what `app.bsky.actor.defs#profileViewDetailed.banner` returns from the atproto SDK and what the existing `NubecitaAsyncImage(model: Any?)` Coil wrapper accepts. The feature module never imports `androidx.palette.*` directly.
- **NEW** `ProfilePillTabs` composable (M3 `PrimaryTabRow` + 36dp pill indicator + FILL-axis icon toggle).

### MVI contract (per `CLAUDE.md`)

- `ProfileScreenViewState` (flat fields for independent flags + per-tab sealed `TabLoadStatus` for mutually-exclusive lifecycles), `ProfileEvent`, `ProfileEffect`.
- `ProfileEffect.NavigateToPost(uri)` / `NavigateToProfile(handle)` / `NavigateToSettings` are collected once in the outermost Composable and pushed onto `LocalMainShellNavState.current` — the ViewModel never sees the nav state holder. Same pattern as `:feature:postdetail:impl`.
- `ProfileEffect.ShowMessage` surfaces a Snackbar via the screen's `SnackbarHostState` — backs the "Coming soon" actions and any non-sticky error reporting.

### Adaptive layout integration

- The profile body's post-tap path emits `NavigateToPost` → push onto `mainShellNavState` → the existing `rememberListDetailSceneStrategy` in `MainShell.kt` handles the rest. On Compact the detail is full-screen via the existing `PostDetail` route; on Medium+ it lands in the right pane. Post list items are marked with `ListDetailSceneStrategy.listPane{}` metadata so the strategy knows where to render them.
- **Expanded falls back to Medium 2-pane.** No side panel (suggested follows + pinned feeds) in v1 — those data sources aren't built yet.

## Capabilities

### New Capabilities

- `feature-profile`: The user Profile screen capability. Covers both own (`Profile(handle = null)`) and other-user (`Profile(handle = "...")`) variants, the three tab bodies (Posts / Replies / Media), the stubbed actions row, and the Settings sub-route stub. Captures the data + VM + UI + screenshot contracts. Mirrors `feature-feed` and `feature-postdetail` in shape.

### Modified Capabilities

- `design-system`: New variable fonts (Fraunces with `SOFT` axis, JetBrains Mono), new `BoldHeroGradient` composable + `rememberBoldHeroGradient(banner, avatarHue)` helper, new `ProfilePillTabs` composable. All four additions are independently reusable beyond profile and live behind clean APIs (the feature module never imports `androidx.palette.*` directly).
- `app-navigation-shell`: The placeholder providers in `:app`'s `MainShellPlaceholderModule` for `Profile(handle = null)` and `Settings` are removed when `:feature:profile:impl` lands. Per the `:api`-only-stubs rule in `CLAUDE.md`, this is the canonical migration: delete the `:app`-side placeholder provider, add the new module's `@MainShell` provider — no bridging artifacts. Captured as a spec delta so the navigation-shell contract stays in sync.

## Impact

- **Affected modules**: NEW `:feature:profile:impl`; `:designsystem` (fonts + 2 composables); `:app` (placeholder removal in `MainShellPlaceholderModule`).
- **Affected specs**: `feature-profile` (new), `design-system` (delta), `app-navigation-shell` (delta).
- **Dependencies**: adds `androidx.palette:palette-ktx` to the version catalog (used inside `:designsystem`, not the feature). Fraunces + JetBrains Mono ship as bundled font assets in `:designsystem`. No new atproto endpoints (`actor.getProfile` + `feed.getAuthorFeed` are already in the generated lexicon).
- **Out of scope for this change**: scroll-collapsing hero, Expanded 3-pane side panel, real Follow / Unfollow writes, real Edit profile, Message routing (blocked on `chat.bsky` lexicon), real Settings screen, avatar-derived hash for initial fallback.
- **Backwards compatibility**: no breaking changes. `:feature:profile:api`'s NavKeys are unchanged; the `:app`-side placeholder removal is invisible to callers because the `Profile` and `Settings` NavKeys keep resolving — just to richer Composables. Feed handle-tap behavior is unchanged at the call site; the destination it pushes to is now real.
- **Behavior under feature flags / build variants**: none. No flag gates; the Bold-derived hero is the only render path.

## Non-goals

- **Scroll-collapsing hero / TopAppBar transition.** Sticky tabs only in v1; collapse ships in its own follow-up bd issue.
- **Expanded 3-pane with side panel.** Suggested follows + pinned feeds need data sources we haven't built (`getSuggestions` + actor preferences). Expanded falls back to the Medium 2-pane.
- **Real Follow / Unfollow writes.** `app.bsky.graph.follow` + `deleteFollow` + optimistic UI is its own epic. Follow button ships as a "Coming soon" snackbar stub.
- **Real Edit profile screen.** Own profile, no Edit destination — stubbed snackbar.
- **Message routing into chats.** The `chat.bsky` lexicon may be partial in the generated atproto SDK (see the precedent in `reference_atproto_kotlin_notification_lexicon_gap.md` — only a subset of notification endpoints are generated today). Verify and file a follow-up before scoping DMs.
- **Real Settings screen.** The Settings sub-route ships as a one-screen stub with Sign Out only. Real settings (theme, language, notifications, account) is its own epic.
- **Avatar-derived hash for the no-avatar initial fallback.** Use the first character of `handle` for v1; revisit if telemetry shows ugly collisions on common letters.
- **Custom UI primitives.** Everything routes through M3 / Compose / our existing design system. If a flourish needs reaching outside, drop the flourish — don't build a primitive. (Established convention from `add-postdetail-m3-expressive-treatment`.)
