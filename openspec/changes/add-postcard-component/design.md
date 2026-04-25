## Context

Nubecita's feed is the highest-frequency screen in the product. PostCard is what users see hundreds of times per session; its API surface is also what every other screen that lists posts (profile timeline, search results, notifications, thread view) reuses. Five technical prerequisites have landed in the last two weeks (Material 3 theme, Coil image loading, relative-time formatter, embed-scope decision, atproto-kotlin's `rememberBlueskyAnnotatedString` upstream helper), so this change can ship purely as an additive composition layer over those primitives.

The project established two unit-of-work conventions in earlier changes that this design honors:
- **MVI**: ViewModels expose flat `StateFlow<S>` of UI-ready state; composables read it via `collectAsStateWithLifecycle()`. PostCard is a stateless composable — it doesn't own a ViewModel, it consumes one frame's worth of state passed by its host.
- **Module convention plugins**: every Android module applies one of `nubecita.android.{library, library.compose, hilt, feature, application}`. New modules slot in via the same plugins; this change uses `nubecita.android.library` for `:data:models` (no Compose, no Hilt — pure Kotlin types).

The pre-flight architectural decisions (Option A primitives vs Option B UI model, and the `Facet` leak policy) were settled in conversation before this change opened. This document records them alongside the alternatives so the rationale survives.

## Goals / Non-Goals

**Goals:**
- Ship a PostCard composable that can be the keystone of every post-listing screen in the app.
- Establish `:data:models` as the conventional location for UI model types, with a clear rule about what may and may not leak from atproto-kotlin.
- Keep PostCard fully stateless and trivially previewable — every state branch (empty, typed, with image, with unsupported embed, reposted-by) renders correctly under `@Preview` without a real network or VM.
- Make `LazyColumn` skip behavior correct from day one — every `PostUi` field is stable, every collection is `ImmutableList`, the composable carries `@Stable` semantics where needed.

**Non-Goals:**
- The atproto → PostUi mapper. Lives with `nubecita-4u5` (FeedViewModel work).
- The feed screen scaffolding (LazyColumn, pagination, pull-to-refresh). Lives with `nubecita-1d5`.
- Action mutations (like / repost / reply persistence). PostCard fires callbacks; the host VM handles the network round-trip.
- Deferred embed types beyond Images (external, record, video, recordWithMedia). Each has its own bd ticket; PostCard renders `PostCardUnsupportedEmbed` for them in v1.
- Click-through navigation. Host screens own destinations.

## Decisions

### 1. New `:data:models` module rather than colocating types in `:designsystem`

PostUi (and its support types AuthorUi, EmbedUi, ImageUi, ViewerStateUi) live in a brand-new `:data:models` Android library module instead of in `:designsystem` next to PostCard.

**Why:** UI models are not a `:designsystem` concern. `:designsystem` is for visual primitives — themes, components, tokens. UI models are the *contract between* visual primitives and the data layer. Colocating them in `:designsystem` creates an awkward dependency direction once the mapper module (`:feature:feed:impl`) needs to construct PostUi instances — `:feature:feed:impl` would have to depend on `:designsystem` purely for a data class, dragging Compose UI onto a non-UI module's compile classpath. Putting models in their own pure-Kotlin module breaks the cycle.

**Alternatives considered:**
- *Colocate in `:designsystem`.* Simpler today (one fewer module), but creates the dependency-direction problem above. Rejected.
- *Per-feature UI models inside `:feature:feed:impl`.* Acceptable for one-off models, but PostUi is shared across multiple features (feed, profile, notifications, search results). Local would mean duplication or arbitrary "first feature wins" ownership. Rejected.

### 2. AT Protocol wire-data primitives are explicitly allowed inside `:data:models`

`PostUi.text: String`, `PostUi.facets: ImmutableList<Facet>` — `Facet` is imported from `atproto:models` directly. This is the only `atproto:*` dep on `:data:models`. Identifier wrappers from `atproto:runtime` (`Did`, `Handle`, `AtUri`) are deliberately NOT used; UI rendering doesn't need wire-level type safety on identifiers, and pulling in `:runtime` would expose service abstractions (`XrpcClient`, `AuthProvider`) on `:data:models`'s classpath. Mappers unwrap typed identifiers to `String` at the protocol boundary before constructing UI models.

**Why:** These types are wire-data primitives, not service abstractions. Mirroring `Facet` as our own `FacetSpec` would be pure copy-paste — every field would be identical, every change to the upstream lexicon would need to be mirrored manually. The protective intent of "no atproto leak" is to keep DTOs out (`PostView` response envelopes, paginated `getTimeline` cursors, error envelopes) — not to reject the protocol's vocabulary.

**Alternatives considered:**
- *Use the `Did` / `Handle` typed wrappers from `atproto:runtime` for `AuthorUi`'s identifier fields.* Considered and rejected — these wrappers live in `:runtime`, which also exposes `XrpcClient` / `AuthProvider` (real service abstractions). Adding the dep for two value-class wrappers isn't worth the surface-area inflation, and UI code doesn't validate DIDs / handles anyway. Mappers do the unwrap at construction time.
- *Mirror Facet as a nubecita-defined `FacetSpec`.* Pure maintenance cost. Rejected.
- *Pre-build `AnnotatedString` in the mapper and pass it on PostUi.* Mapper is non-Composable, so it would have to use the lower-level `buildBlueskyAnnotatedString` (Tier 2 in atproto-compose) and inject a `SpanStyle` from outside the Compose theme — losing dynamic-color and theme-switch reactivity. Rejected.

### 3. PostCard takes `PostUi` (Option B), not flat primitives (Option A)

PostCard's signature is `fun PostCard(post: PostUi, callbacks: PostCallbacks, modifier: Modifier = Modifier)`.

**Why:** PostCard's natural surface area is 15+ data fields plus 5+ callbacks. A flat-parameter signature is unreadable at the call site, churn-prone (adding a field touches every caller), and makes test fixtures painful. A single model object plus a single callbacks bag matches the reference UI kit's pattern and what every mature Compose-based social client converges on (cf. ComposeBluesky, deer.social).

**Alternatives considered:**
- *Flat primitives (Option A).* Discussed and rejected in pre-design conversation. The argument for it is "easy to fixture in tests" — but a `PostUi` data class with a default constructor is just as easy.
- *Slot-based PostCard with `header: @Composable () -> Unit`, `body: @Composable () -> Unit`, etc.* Maximum flexibility, much harder to reason about, and adds slot-API complexity without a current consumer who wants to override slots. Rejected; revisit if a profile-specific PostCard variant emerges.

### 4. Embed dispatch via sealed `EmbedUi` with explicit `Unsupported` variant

```kotlin
sealed interface EmbedUi {
    data class Images(val items: ImmutableList<ImageUi>) : EmbedUi
    data class Unsupported(val typeUri: String) : EmbedUi
}
```

PostCard's embed slot does an exhaustive `when (post.embed)` — Images renders `PostCardImageEmbed`; Unsupported renders the deliberate-degradation chip. New variants land alongside their respective bd tickets (`Embed.External`, `Embed.Record`, `Embed.Video`, `Embed.RecordWithMedia`).

**Why:** Matches the embed-scope decision in `docs/superpowers/specs/2026-04-25-postcard-embed-scope-v1.md`. Sealed type makes `when` exhaustive — adding a new variant in the future is a compile error at every dispatch site, surfacing the work needed. The `Unsupported(typeUri)` variant carries the lexicon URI so debug builds can show "Unsupported embed: app.bsky.embed.video" while release shows a generic label.

**Alternatives considered:**
- *Nullable `embed: EmbedUi?`.* Loses the typed `Unsupported` signal — host would have to map "embed exists but we don't support it" to null, conflating "no embed" with "unsupported embed." Rejected.
- *Render unsupported as an error chip with error styling.* The decision doc explicitly calls out this is *not* an error — it's deliberate degradation. Rejected.

### 5. PostCard owns the `rememberBlueskyAnnotatedString` call

PostCard internally calls `rememberBlueskyAnnotatedString(post.text, post.facets)` and renders `Text(annotated, ...)`. Hosts pass `text: String` + `facets: ImmutableList<Facet>` via `PostUi`; they don't pre-build the AnnotatedString.

**Why:** The Material 3 wrapper reads `MaterialTheme.colorScheme.primary` for link styling. Building it inside PostCard means dynamic-color theme switches and Material You re-themings just work — no manual invalidation by the host. Hosts also stay simpler (no Compose-ception where a non-Composable mapper produces a Composable type).

**Alternatives considered:**
- *Host pre-builds AnnotatedString and passes it as a separate parameter alongside PostUi.* Couples host screens to atproto's compose helper directly, adds a parameter to PostCard's signature, and loses the "PostUi is the single source of post truth" property. Rejected.
- *Mapper builds AnnotatedString via the non-Composable `buildBlueskyAnnotatedString` (Tier 2).* Loses theme reactivity per above. Rejected.

### 6. `@Stable` data classes + `ImmutableList` collections

Every `*Ui` data class is annotated `@Stable`. Every collection field uses `kotlinx.collections.immutable.ImmutableList<T>` (already on the classpath via `:core:common`).

**Why:** Compose's stability inference treats `List<*>` and any data class with non-stable fields as unstable, defeating `LazyColumn`'s skip optimization. For a 50-card visible feed at 120Hz, that's the difference between smooth scroll and jank.

**Alternatives considered:**
- *Plain `List<T>` and trust ImmutableList's transitive marking.* Less explicit; future maintainers might construct a `mutableListOf(...)` and pass it. Rejected for explicitness.
- *Custom `@Immutable` annotations on the data classes.* `@Immutable` is stricter than `@Stable` — it promises *no* observable change. Our viewer state can change (isLikedByViewer flips), so `@Stable` is the correct annotation. Use `@Immutable` only for truly frozen sub-types if any appear.

### 7. Single `PostCallbacks` data class for interactions, not flat lambdas

```kotlin
data class PostCallbacks(
    val onTap: (PostUi) -> Unit = {},
    val onAuthorTap: (AuthorUi) -> Unit = {},
    val onLike: (PostUi) -> Unit = {},
    val onRepost: (PostUi) -> Unit = {},
    val onReply: (PostUi) -> Unit = {},
    val onShare: (PostUi) -> Unit = {},
)
```

**Why:** Five-plus lambdas in a composable signature is harder to read than one bag. Defaults to no-op for each, so previews can pass `PostCallbacks()`. Hosts construct one shared `remember`-ed instance per screen.

**Alternatives considered:**
- *Flat lambda parameters.* Readable for a 2-3 callback case; awkward at 6+. Rejected.
- *Single `onAction: (PostAction) -> Unit` with sealed `PostAction` events.* MVI-style; nice for VM dispatch but boilerplate at the PostCard call site. Rejected for ergonomics.

### 8. Shimmer ships as a `Modifier` extension paired with a high-level `PostCardShimmer` composable

The loading state is expressed as two artifacts that work together:

```kotlin
// :designsystem/component/Shimmer.kt
@Composable
fun Modifier.shimmer(durationMillis: Int = 1500): Modifier { ... }

// :designsystem/component/PostCardShimmer.kt
@Composable
fun PostCardShimmer(modifier: Modifier = Modifier) { ... }  // post-shaped skeleton
```

`Modifier.shimmer()` is a `@Composable` extension that paints an animated horizontal linear-gradient brush via `drawWithCache { onDrawBehind { ... } }`. The gradient cycles through three theme-derived colors (`surfaceContainerHighest` → `surfaceContainerHigh` → `surfaceContainerHighest`) driven by `rememberInfiniteTransition`. Because it reads `MaterialTheme.colorScheme`, it follows light/dark/dynamic-color theme switches without consumer wiring.

`PostCardShimmer()` assembles a PostCard-shaped skeleton: a 40dp circular Box for the avatar, two staggered text bars (titleMedium-height, bodyMedium-height ×2), an optional image-shaped rectangle, and four small action-row dots — every shape has `.shimmer()` applied, with sensible default heights matching `PostCard`'s rendered geometry.

**Why ship both:**
- The `Modifier` is the reusable primitive. Future skeletons (`ProfileHeaderShimmer`, `NotificationCardShimmer`, `ThreadPostShimmer`) reuse it without re-implementing the animation. Avatar / image / button / chip placeholders inside non-PostCard screens compose `.shimmer()` on whatever shape they need.
- `PostCardShimmer()` is the high-level convenience. The feed screen's `LazyColumn` doesn't want to assemble a skeleton by hand — it just renders `PostCardShimmer()` for each placeholder slot during the initial load.

**Why a `@Composable` extension on `Modifier` rather than `Modifier.composed { ... }`:**
- `composed { ... }` carries a deprecation soft-warning in Compose UI 1.7+ (it allocates per-call-site, defeats some node-tree optimizations).
- `@Composable Modifier` extensions are the current canonical pattern (cf. `Modifier.basicMarquee()`, `Modifier.hoverable()`).
- A custom `ModifierNodeElement` + `Modifier.Node` would be the absolute most-performant path, but it's ~3× the code and shimmer's perf budget on a 50-card LazyColumn isn't the bottleneck (the gradient brush is GPU-bound, not CPU).

**Why not depend on a third-party library:**
- Accompanist's `accompanist-placeholder-material` is deprecated (the entire Accompanist project is sunset; Material 3 absorbed parts of it but not placeholder).
- Material 3's experimental `Modifier.placeholder(visible)` was added then removed during the M3 alpha churn. Several other community libraries exist but each adds a dep we'd need to maintain. ~50 lines of our own code is the right trade-off.

**Alternatives considered:**
- *Add `isLoading: Boolean` parameter to `PostCard` itself.* Conflates the loaded-state component with the loading-state visual. Rejected — they have different geometric needs and PostCard's signature stays focused.
- *Skeleton via static gray boxes (no animation).* Loses the "this is loading, not broken" affordance. Rejected — shimmer animation is the established convention users recognize.
- *Use Material 3's experimental `LoadingIndicator` for the whole list.* That's a single spinner, not a list-shaped skeleton — different perceived-performance characteristic. Compatible (a host could show a spinner above and shimmers below) but not a substitute.

### 9. PostCard is loaded-state-only — loading / error / empty are host concerns

PostCard MUST NOT carry any kind of `status` parameter, `state` enum, or `isLoading: Boolean`. It always renders one post that has data. The other states a post-listing screen needs are owned at the screen + VM layer:

| Conceptual state | Where it lives | Composable rendered |
|---|---|---|
| Loading (initial / pagination) | Feed VM's `FeedState.isLoading` | `PostCardShimmer()` (separate composable, not a PostCard variant) |
| Refreshing (pull-to-refresh) | Feed VM's `FeedState.isRefreshing` | `PullToRefreshBox` indicator above the list, PostCards below |
| Error (whole-list failure) | Feed VM's `FeedState.errorBanner` | `FeedErrorState(message, onRetry)` composable above or instead of the list |
| Empty (no posts to show) | `FeedState.posts.isEmpty() && !isLoading && errorBanner == null` | `FeedEmptyState()` composable |
| Per-action transient error (like / repost / reply failed) | MVI Effect emitted by VM, collected in screen | Snackbar via `SnackbarHostState` |
| Per-image failure | Already handled by `NubecitaAsyncImage`'s error painter | (sub-component, never reaches PostCard) |
| Special post types (deleted / blocked) | Sealed `FeedItemUi` introduced in `nubecita-4u5` | Different composable (`BlockedPostPlaceholder`, `NotFoundPostPlaceholder`) — NOT PostCard with a state flag |

**Why:** Encoding any of these as PostCard parameters either creates the "20 cards saying 'something went wrong'" anti-pattern (per-item error), or shoves orchestration into the leaf composable (PostCard would need to know whether to render itself, a shimmer, an error chip, etc.). Both fight Compose's compositional model. Keeping PostCard a pure renderer of `PostUi` lets the host orchestrate dispatch with a simple `when` and lets every consumer (feed, profile timeline, search results, notifications, thread view) reuse the same component without each one negotiating with its state machinery.

**This aligns with the project's MVI flat-state convention** (per CLAUDE.md): "State is flat and UI-ready: concrete fields (`isLoading: Boolean`, `items: ImmutableList<T>`, etc.), never a VM-layer sum type like `Async<T>`." PostCard reads only what's in `post: PostUi`; orchestration fields like `isLoading` live on the VM's screen-level state, not on the post itself.

**Alternatives considered:**
- *`PostCard(post: PostUi?, isLoading: Boolean = false)` with internal shimmer fallback.* PostCard becomes a state machine. Composes worse with `LazyColumn`, harder to test, conflates two visual concerns. Rejected.
- *`sealed PostCardState { Loading; Loaded(PostUi); Error(String) }`.* Forces every consumer to dispatch on a sum type at every call site even when 99% of them never produce Loading or Error (those are list-level). Rejected as wrong-layer abstraction.

## Risks / Trade-offs

**Risk:** PostCard's @Composable `rememberBlueskyAnnotatedString` call allocates per recomposition if the upstream helper isn't `@Stable`-annotated correctly. → Mitigation: filed upstream as `kikin81/atproto-kotlin#23` (ImmutableList stability for `compose-material3`); meanwhile PostUi keeps `facets: ImmutableList<Facet>` so identity equality holds at our boundary.

**Risk:** AT Protocol wire types in `:data:models` could grow into accidental DTO leakage if the convention isn't clear. → Mitigation: this design's Decision 2 is the canonical statement; the resulting `data-models` capability spec records it as a SHALL. Code review enforces.

**Risk:** Five `@Preview` variants × dynamic color × dark mode = 10+ preview functions, slow to render in Studio. → Mitigation: use `@PreviewParameter` with a single sealed `PostCardPreviewState` to reduce duplication; ship Roborazzi screenshot tests under the existing `android.experimental.enableScreenshotTest` infra (pre-commit hook already runs `:app:compileDebugScreenshotTestKotlin`).

**Risk:** Future PostCard consumers (PostDetail, NotificationCard, ThreadView) may want different action-row variants (no like in NotificationCard, no repost in ThreadView's quoted card). → Mitigation: out of scope for this change, but DocC: a future change introduces a slot-based PostCard or a `PostCardActions` enum to gate which actions render.

**Risk:** Bluesky API rate limits — every recomposition of `rememberBlueskyAnnotatedString` could in theory re-render text many times per second on a fast scroll. → Mitigation: not a real issue (the helper is local computation, not network), but flagged for awareness.

## Migration Plan

Pure additive change. No migration steps:
1. Land `:data:models` module + UI model types.
2. Land PostCard composable + supporting primitives.
3. Optional: hook one PostCard preview into `MainScreen` placeholder so the dependency graph is exercised in `:app`.
4. Merge.

Rollback: revert the merge commit; no data, no schema, no user-visible feature gate.

## Open Questions

- **Q: Should PostCard's tap target on the body text be the whole card or just the body?** Twitter / Bluesky web both make the whole card tappable (excluding the action row). Defaulting to whole-card tap; the Embed `PostCardImageEmbed` will need its own tap handler if image lightbox is in scope (deferred to a separate ticket).
- ~~**Q: Do we ship a `PostCardLoading` skeleton variant in this change, or wait for the feed work?**~~ Resolved (Decision 8): we ship `Modifier.shimmer()` + `PostCardShimmer()` together with PostCard. The feed screen consumes `PostCardShimmer()` for its loading list state, and the underlying `Modifier.shimmer()` becomes a reusable primitive for every future skeleton in the app.
- **Q: Action-row icon set — Material Icons Extended vs custom SVGs?** Material Icons Extended is on the classpath via the Compose BOM and is consistent with the rest of the app. Going with Material Icons Extended unless a brand decision overrides.
