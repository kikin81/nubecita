## Why

Users opening Nubecita see only a placeholder "Hello, Android!" screen — there is no way to read a Bluesky post yet. PostCard is the keystone composable for the entire feed experience: every screen that lists posts (home feed, profile, search results, notifications, thread view) renders the same `PostCard`, so its API shape and visual treatment dictate the rest of the product. All five technical prerequisites (Material 3 theme, Coil, relative-time formatter, embed scope decision, atproto facet helper) have landed, so this is the next critical-path block.

## What Changes

- **NEW Gradle module `:data:models`** — a plain-Kotlin Android library that hosts UI model types (`PostUi`, `AuthorUi`, `EmbedUi`, `ImageUi`, `ViewerStateUi`). This module is the authoritative location for "what does the UI need to render an entity," kept free of service abstractions (no DTOs, no response envelopes, no paginated cursors) but allowed to use AT Protocol wire-data primitives (`Facet`, `Did`, `Handle`) where translation would be lossy. Establishes the convention for future UI models (e.g. `ProfileUi`, `NotificationUi`).
- **NEW `PostCard` composable in `:designsystem`** — accepts a single `PostUi` parameter plus interaction callbacks. Renders author avatar (via `NubecitaAvatar`), display name + handle + relative timestamp (via `formatRelativeTime` from `:core:common`), facet-styled body text (via `rememberBlueskyAnnotatedString` from `atproto-kotlin :compose-material3`), an embed slot, and an action row (reply / repost / like / share).
- **NEW supporting primitives in `:designsystem`** — `PostCardImageEmbed` (1–4 images grid, `app.bsky.embed.images` shape), `PostCardUnsupportedEmbed` (the deliberate-degradation chip per `nubecita-6rh`), and `PostStat` (the icon + count interactive cell used in the action row).
- **NEW `Modifier.shimmer()` + `PostCardShimmer` in `:designsystem`** — a reusable animated placeholder modifier (linear-gradient brush over `rememberInfiniteTransition`, theme-color-aware) and a PostCard-shaped skeleton composable that the feed screen's `LazyColumn` renders while data is loading. Replaces the role of the deprecated Accompanist `placeholder-material` library; rolls our own per current Compose guidance.
- **Embed dispatch via sealed `EmbedUi`** — `Images(items)` and `Unsupported(typeUri)` variants in v1, matching the embed-scope decision recorded in `docs/superpowers/specs/2026-04-25-postcard-embed-scope-v1.md`. Future embed types (`external`, `record`, `video`, `recordWithMedia`) extend this sealed type when their respective bd tickets land.
- **`@Stable` annotations and `ImmutableList` collection types** — every `PostUi` field carries either a stable scalar or `kotlinx.collections.immutable.ImmutableList`, so `LazyColumn` skips recomposition on identity-equal posts.
- **Five `@Preview` variants** — empty / typed-no-image / with-image / with-unsupported-embed / reposted-by, plus a Material 3 dynamic-color variant.

## Non-goals

- **The atproto → PostUi mapper.** Lives in `:feature:feed:impl` alongside `FeedViewModel`; tracked under `nubecita-4u5`. This change ships only the target type and the consuming composable.
- **The feed screen.** `LazyColumn` + pagination + pull-to-refresh is `nubecita-1d5`. PostCard's only consumer in this change is `@Preview` composables (and possibly a one-off scaffold in the placeholder `MainScreen` to exercise the full graph in `:app`).
- **Click-through navigation.** PostCard exposes callbacks (`onTap`, `onAuthorTap`, `onLike`, `onRepost`, `onReply`, `onShare`) but does not own routing. Host screens own destination decisions.
- **Deferred embed types.** `external` (`nubecita-aku`), `record` (`nubecita-6vq`), `video` (`nubecita-xsu`), `recordWithMedia` (`nubecita-umn`) — each lands under its own ticket. PostCard renders `PostCardUnsupportedEmbed` for these in v1.
- **Action mutations.** Tapping like / repost fires the callback only; the actual `com.atproto.repo.createRecord` call is the host VM's job and lives with the action-bar follow-up work.
- **Custom theme stack support.** PostCard reads `MaterialTheme.colorScheme` directly (consistent with the rest of `:designsystem`). No `:compose` (non-material3) variant.
- **Material 3's experimental `Modifier.placeholder()`.** That API never stabilized (the relevant artifact was scoped to alpha for several years, then dropped). We do NOT depend on it; the new `Modifier.shimmer()` is built on stable Compose UI primitives (`rememberInfiniteTransition`, `drawWithCache`, `Brush.linearGradient`) so it has no deprecation tail.

## Capabilities

### New Capabilities
- `data-models`: Plain-Kotlin UI model types consumed by `:designsystem` composables and produced by feature-module mappers. Hosts `PostUi` (and supporting `AuthorUi` / `EmbedUi` / `ImageUi` / `ViewerStateUi`) in v1; future entries (`ProfileUi`, `NotificationUi`, `ThreadUi`) extend the same module under the same convention.

### Modified Capabilities
- `design-system`: Adds the `PostCard` composable + its supporting primitives (`PostCardImageEmbed`, `PostCardUnsupportedEmbed`, `PostStat`), plus the reusable `Modifier.shimmer()` and `PostCardShimmer` skeleton. Adds a dependency on `:data:models` for UI model types.

## Impact

**New module.** `:data:models` (Android library, no Compose, no Hilt). Settings.gradle.kts include + `nubecita.android.library` convention plugin.

**Affected modules.**
- `:designsystem` — gains `PostCard.kt`, `PostCardImageEmbed.kt`, `PostCardUnsupportedEmbed.kt`, `PostStat.kt`. New `implementation(project(":data:models"))` dep.
- `:app` — optional placeholder consumption of PostCard in `MainScreen` to exercise the dependency graph end-to-end. New `implementation(project(":data:models"))` dep if the mapper-side ticket ships in parallel.

**New dependencies.**
- `:data:models` depends on `atproto:models` (for `Facet`, `Did`, `Handle`) and `kotlinx-collections-immutable`. No Compose dep — this stays pure Kotlin.
- `:designsystem` already depends on `:core:common` (for `formatRelativeTime` + `rememberRelativeTimeText`) and `atproto:compose-material3` (for `rememberBlueskyAnnotatedString`). No new Maven deps.

**Deviations from baseline.**
- `:data:models` is the project's first non-`:core:*` non-`:feature:*` non-`:app` non-`:designsystem` module. Establishes a third top-level module category. Documented in this change's `design.md` and the resulting `data-models` capability spec.
- Per the `:data:models` design, AT Protocol wire-data primitives (`Facet`, `Did`, `Handle`) are explicitly allowed inside UI models. This relaxes the strict "no atproto leak" rule for primitives where translation would be lossy. DTOs (response envelopes, paginated cursors, `PostView`) remain prohibited.

**Bd graph.**
- Closes `nubecita-w0d` on merge. Embed scope decision (`nubecita-6rh`, closed) and the four deferred-embed follow-ons (`aku`, `6vq`, `xsu`, `umn`, all open) reference this ticket as the foundation they extend.
