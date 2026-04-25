## 1. New `:data:models` module scaffold

- [x] 1.1 Create `data/models/` directory; add `data/models/build.gradle.kts` applying `nubecita.android.library`; namespace `net.kikin.nubecita.data.models`
- [x] 1.2 Add `:data:models` to `settings.gradle.kts` (alongside the existing `:core:*` and `:feature:*` includes)
- [x] 1.3 Add deps in `data/models/build.gradle.kts`: `api(libs.atproto.models)` (Facet/Did/Handle), `api(libs.kotlinx.collections.immutable)`, `api(libs.kotlinx.datetime)` (for Instant), `api(libs.androidx.compose.runtime)` (for `@Stable`)
- [x] 1.4 Add `consumer-rules.pro` placeholder + `.gitkeep` so the empty module is git-tracked
- [x] 1.5 Verify `./gradlew :data:models:assembleDebug` succeeds with no source files

## 2. UI model types in `:data:models`

- [x] 2.1 `AuthorUi.kt` — `@Stable data class AuthorUi(val did: Did, val handle: Handle, val displayName: String, val avatarUrl: String?)`. Unit test on equality + `@Stable` round-trip; covers DID typed-wrapper preservation.
- [x] 2.2 `ImageUi.kt` — `@Stable data class ImageUi(val url: String, val altText: String?, val aspectRatio: Float?)`. Unit test for null-altText handling.
- [x] 2.3 `EmbedUi.kt` — `@Immutable sealed interface EmbedUi { data object Empty; data class Images(val items: ImmutableList<ImageUi>); data class Unsupported(val typeUri: String) }`. Unit test exhaustive `when` enforcement (compile-time check via a small fixture).
- [x] 2.4 `PostStatsUi.kt` — `@Stable data class PostStatsUi(val replyCount: Int = 0, val repostCount: Int = 0, val likeCount: Int = 0, val quoteCount: Int = 0)`. Defaults verified.
- [x] 2.5 `ViewerStateUi.kt` — `@Stable data class ViewerStateUi(val isLikedByViewer: Boolean = false, val isRepostedByViewer: Boolean = false, val isFollowingAuthor: Boolean = false)`. Defaults verified.
- [x] 2.6 `PostUi.kt` — `@Stable data class PostUi(val id: String, val author: AuthorUi, val createdAt: Instant, val text: String, val facets: ImmutableList<Facet>, val embed: EmbedUi, val stats: PostStatsUi, val viewer: ViewerStateUi, val repostedBy: String?)`. Unit test: structural equality with two structurally-equal `PostUi` instances; KDoc references the data-models capability spec.
- [x] 2.7 `PostUiFixtures.kt` (in `src/test`) — fixture factory `fun fakePostUi(...)` with sensible defaults. Used by both `:data:models` tests and downstream `:designsystem` previews.

## 3. `:designsystem` dep wiring

- [x] 3.1 Add `implementation(project(":data:models"))` to `designsystem/build.gradle.kts`
- [x] 3.2 Verify `./gradlew :designsystem:assembleDebug` resolves cleanly

## 4. Embed sub-components in `:designsystem`

- [x] 4.1 `PostCardImageEmbed.kt` — composable rendering 1/2/3/4-image grids with deterministic geometry per the design-system spec; uses `NubecitaAsyncImage`; clips to `RoundedCornerShape(16.dp)`. `@Preview` covering each item-count.
- [x] 4.2 `PostCardUnsupportedEmbed.kt` — small `surfaceContainerHighest` chip composable with secondary-text label; debug build shows `typeUri`, release shows generic label (gated via `BuildConfig.DEBUG`). `@Preview` for both build configurations (or one preview if `BuildConfig` isn't preview-aware).
- [x] 4.3 `PostStat.kt` — internal action-row icon + count cell composable, replicating the reference UI kit's `PostStat`. Active-state coloring via `MaterialTheme.colorScheme.{secondary, tertiary}` for like/repost. `@Preview` for active + inactive.

## 5. PostCard composable in `:designsystem`

- [x] 5.1 `PostCallbacks.kt` — `data class PostCallbacks(...)` with the six callbacks defaulted to no-op per the design-system spec.
- [x] 5.2 `PostCard.kt` — main composable; assembles author row (avatar + name/handle/timestamp), body text via `rememberBlueskyAnnotatedString(post.text, post.facets)`, embed slot dispatch on `post.embed`, action row via `PostStat`. Uses `formatRelativeTime` + `rememberRelativeTimeText` from `:core:common` for the timestamp.
- [x] 5.3 KDoc on PostCard explicitly lists supported embed types (Images + Unsupported) and points to the embed-scope decision doc + the four follow-on tickets for deferred embeds.
- [x] 5.4 KDoc on PostCard documents that callers retain ownership of viewer state — toggling like/repost requires the host VM to emit a new `PostUi`, PostCard does not flip locally.

## 6. PostCard previews

- [x] 6.1 `@Preview(name = "Empty body, no embed")`
- [x] 6.2 `@Preview(name = "Typical post — short body, no embed")`
- [x] 6.3 `@Preview(name = "With single image")`
- [x] 6.4 `@Preview(name = "With unsupported embed (video)")`
- [x] 6.5 `@Preview(name = "Reposted by Alice Chen")`
- [x] 6.6 `@Preview(name = "With mention + link facets")` — verifies the `rememberBlueskyAnnotatedString` integration shows styled spans
- [~] 6.7 Optional: dynamic-color variant — SKIPPED (out of scope; Studio's preview-pane Material You toggle covers this without a dedicated `@Preview` and the dark-mode shimmer preview already validates theme reactivity)

## 7. Shimmer (Modifier + PostCardShimmer)

- [x] 7.1 `Shimmer.kt` — `@Composable fun Modifier.shimmer(durationMillis: Int = 1500): Modifier`. Implementation: `rememberInfiniteTransition` driving a `Float` translate, `drawWithCache { onDrawBehind { drawRect(brush = Brush.linearGradient(theme-colors at translated stops)) } }`. Reads `MaterialTheme.colorScheme` so theme switches propagate.
- [x] 7.2 `PostCardShimmer.kt` — composable assembling avatar circle (40dp) + display-name bar + handle bar + 2 body-text bars + optional 180dp image rectangle + 4 action-row dots. Each shape uses `Modifier.shimmer()`. Geometry mirrors `PostCard`'s rendered layout.
- [x] 7.3 KDoc on `Modifier.shimmer` documents that it must be applied AFTER any `clip()` modifier (so the brush is clipped to the shape) and inside a sized composable.
- [x] 7.4 `@Preview(name = "Shimmer modifier — circle + rectangle")` showcasing the modifier on standalone shapes.
- [x] 7.5 `@Preview(name = "PostCardShimmer — with image")` and `@Preview(name = "PostCardShimmer — no image")` so reviewers can compare both shapes against PostCard's loaded variants.
- [x] 7.6 `@Preview(uiMode = UI_MODE_NIGHT_YES, name = "PostCardShimmer — dark")` to verify theme color reactivity in the skeleton.

## 8. Optional: `:app` smoke consumption

- [~] 8.1 SKIPPED — placeholder MainScreen smoke deferred; `nubecita-1d5` (feed screen) will be the real consumer + visual verification In `MainScreen.kt`, replace one of the placeholder Greeting items with a single `PostCard(post = fakePostUi())` call to exercise the dependency graph end-to-end. Keep behind a `// TODO: remove when nubecita-1d5 lands` marker. Optionally add a `PostCardShimmer()` next to it for visual sanity-check.
- [~] 8.2 SKIPPED with 8.1 If 8.1 done: add `implementation(project(":data:models"))` to `app/build.gradle.kts`

## 9. Verification

- [x] 9.1 `./gradlew :data:models:testDebugUnitTest` — all unit tests green
- [x] 9.2 `./gradlew :designsystem:testDebugUnitTest` — design-system tests green (no PostCard runtime tests yet — covered by previews)
- [x] 9.3 `./gradlew :app:assembleDebug` — full app builds with the new module
- [x] 9.4 `./gradlew :app:lintDebug` — no new lint errors (especially watch for `LocalContextGetResourceValueCall` and slack-compose-lint warnings on the new composables)
- [x] 9.5 `./gradlew spotlessCheck` — clean
- [x] 9.6 `./gradlew :app:compileDebugScreenshotTestKotlin` — screenshot test compile clean (pre-push hook gates on this)
- [~] 9.7 SKIPPED in this session — manual Studio preview verification deferred to PR review (CI screenshot tests via :app:compileDebugScreenshotTestKotlin already pass at 9.6) Manual: open `PostCard.kt` and `PostCardShimmer.kt` in Studio, confirm all previews (6 PostCard + 4 shimmer) render without exceptions

## 10. Documentation + bd

- [x] 10.1 Update `bd update nubecita-w0d --notes` with a pointer to the openspec change
- [x] 10.2 Run `openspec validate add-postcard-component --strict` — passes
- [x] 10.3 Add an entry to `:data:models/MODULE.md` (or a brief README) explaining the data-models capability rules so future contributors discover the convention
