# PostCard external link card embed — design

- **Date:** 2026-04-27
- **bd:** nubecita-aku (feature)
- **Implements roadmap tier:** v1.1 of [2026-04-25-postcard-embed-scope-v1.md](2026-04-25-postcard-embed-scope-v1.md)
- **Status:** Accepted

## Goal

Render `app.bsky.embed.external` posts in `PostCard` as a native Material 3 link-preview card. Tapping the card opens the linked URI in a Chrome Custom Tab (CCT), inheriting the app's theme and returning the user to the feed without a task switch.

No WebView, no browser handoff, no inline player detection. The lexicon already carries `title`, `description`, `thumb`, and `uri`; the renderer is pure Compose + `NubecitaAsyncImage`.

## Non-goals

- Tap-to-open analytics hook (no telemetry layer exists in nubecita yet).
- Long-press context menu (share / copy / open externally).
- YouTube / Vimeo URI detection → inline player. Explicitly deferred per bd notes.
- External embeds nested inside `recordWithMedia` (that path is owned by nubecita-umn).

## Design

### Components and locations

| File | Module | Action |
|---|---|---|
| `EmbedUi.kt` | `:data:models` | Add `External(uri, title, description, thumbUrl?)` variant |
| `PostCardExternalEmbed.kt` (new) | `:designsystem` | Stateless leaf composable rendering the card |
| `PostCard.kt` | `:designsystem` | Extend embed-slot `when` with `EmbedUi.External` branch |
| `PostCallbacks.kt` | `:data:models` | Add `onExternalEmbedTap: (uri: String) -> Unit = {}` |
| `FeedViewPostMapper.kt` | `:feature:feed:impl` | Replace `ExternalView -> Unsupported(...)` with `External(...)` mapping |
| `FeedScreen.kt` | `:feature:feed:impl` | Wire callback → `CustomTabsIntent` launcher |
| `PostCardExternalEmbedScreenshotTest.kt` (new) | `:designsystem/screenshotTest` | 4 shots: with-thumb / no-thumb × light / dark |
| `FeedViewPostMapperTest.kt` | `:feature:feed:impl/test` | Add `external` mapping cases |
| `PostUiFixtures.kt` | `:data:models/test` | Add an `externalEmbed*` fixture |

External lives in `:designsystem` (no heavy dep) — same module as `PostCardImageEmbed`. The `PostCardVideoEmbed`-in-`:feature:feed:impl` exception exists only because Media3 / ExoPlayer is too heavy for `:designsystem`. External has no such constraint.

### `EmbedUi.External` shape

```kotlin
public data class External(
    val uri: String,            // raw URI, full
    val title: String,          // may be blank — render row only if non-blank
    val description: String,    // may be blank — render row only if non-blank
    val thumbUrl: String?,      // null → omit thumb section (text-only card)
) : EmbedUi
```

The lexicon makes `title` / `description` non-null `String`, but Bluesky permits empty strings. The renderer treats empty as "skip the row" rather than rendering an empty `Text` composable, preserving vertical rhythm.

### Rendering

`PostCardExternalEmbed(uri, title, description, thumbUrl, onTap, modifier)` — fully stateless.

Surface: `Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp))` with `Modifier.clickable { onTap(uri) }` covering the whole card (uniform tap target — taps anywhere on the card open the URL). `surfaceContainer` was chosen over `surface`/`surfaceContainerLow` because the link card needs to read as a distinct embedded element against the feed background, not a borderless extension of the post body.

Layout (top to bottom):

```
┌────────────────────────────────────┐
│ [thumb @ 1.91:1, ContentScale.Crop]│  ← only if thumbUrl != null
├────────────────────────────────────┤
│ Title (titleMedium, max 2 lines, ellipsis)
│ Description (bodyMedium, max 2 lines, ellipsis)
│ ◐ host.example.com (labelSmall, onSurfaceVariant)
└────────────────────────────────────┘
```

- **Thumb**: `NubecitaAsyncImage` with `Modifier.fillMaxWidth().aspectRatio(1.91f)`, `ContentScale.Crop`, top-corner-clipped (`RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)`). 1.91:1 chosen because (a) it's the OG-image standard and (b) a fixed ratio keeps `LazyColumn` measurement deterministic, which matters for the 120 Hz scroll requirement.
- **Text block padding**: `16.dp` horizontal, `12.dp` vertical.
- **Inter-row spacing**: `4.dp` title→description, `8.dp` description→footer.
- **Domain footer**: `Row` with `Icons.Outlined.Public` (`16.dp`, tinted `onSurfaceVariant`) and the host string. Domain extraction: `Uri.parse(uri).host?.removePrefix("www.")`; falls back to the full URI if `host` is null (opaque/malformed URIs).
- **Missing thumb**: no placeholder, no gradient — the entire thumb section is omitted and the card collapses to a text-only layout.

### Tap wiring

`PostCallbacks` (in `:data:models`) gains:

```kotlin
val onExternalEmbedTap: (uri: String) -> Unit = {},
```

Default no-op so existing call sites remain source-compatible. `PostCard` threads the callback to its embed slot, which passes it to `PostCardExternalEmbed`'s `onTap`.

`FeedScreen` provides the implementation by building a `CustomTabsIntent` and invoking `launchUrl(context, Uri.parse(uri))`. The toolbar `colorSchemes` is matched to the current `MaterialTheme.colorScheme.surface` so the article opens looking like a continuation of the feed, not a Chrome-blue interruption. Pattern factored as `rememberCustomTabsLauncher(): (String) -> Unit` if a second call site appears; for v1 a single inline `remember` block is sufficient.

`launchUrl` is wrapped in `runCatching`. The `ActivityNotFoundException` path (no browser installed — extremely rare) is a silent no-op for v1; surfacing it as a Snackbar via the screen's existing `UiEffect` channel is a follow-on if real usage hits it.

### Mapper

`FeedViewPostMapper.toEmbedUi()` replaces the current `Unsupported` route for `ExternalView`:

```kotlin
is ExternalView -> EmbedUi.External(
    uri = external.uri.raw,
    title = external.title,
    description = external.description,
    thumbUrl = external.thumb?.raw,
)
```

`RecordView` and `RecordWithMediaView` continue to route to `EmbedUi.Unsupported(...)` — those are nubecita-6vq and nubecita-umn respectively. The `Unknown` and structurally-unreachable `else` branches are unchanged.

The atproto-kotlin lib already produces a fetchable URL via `Uri.raw` for both `external.uri` and `external.thumb`. No blob-ref → CDN URL construction is required on the nubecita side. (`Uri` here is the lib's typed wrapper, not `android.net.Uri`.)

### Testing

**Unit tests** (`FeedViewPostMapperTest.kt`):
- `external embed with thumb maps to External(thumbUrl != null)`
- `external embed without thumb maps to External(thumbUrl = null)`
- `external embed with empty title still maps successfully` (lexicon allows empty strings)

**Screenshot tests** (`PostCardExternalEmbedScreenshotTest.kt`, mirrors `PostCardImageEmbedScreenshotTest`):
- with-thumb × light
- with-thumb × dark
- no-thumb × light
- no-thumb × dark

**Compose previews** in `PostCardExternalEmbed.kt`:
- `@Preview` with-thumb (real-world-shaped title + description for visual review)
- `@Preview` no-thumb

A new `external embed` preview in `PostCard.kt` shows the variant inside the full PostCard frame for visual integration review. The full-PostCard screenshot test (`PostCardScreenshotTest`) is not extended for this variant — the leaf composable is shot in isolation, which is enough.

### Edge cases

| Case | Behavior |
|---|---|
| `Uri.parse(uri).host == null` | Render full URI string as domain footer. Card still tappable. |
| `launchUrl` throws (`ActivityNotFoundException`) | Silent no-op (wrapped in `runCatching`). Snackbar path is a follow-on. |
| Title length > 2 lines | `TextOverflow.Ellipsis`, capped at 2 `maxLines`. |
| Description length > 2 lines | Same — 2 lines, ellipsis. |
| Domain length > 1 line (rare) | 1 line, ellipsis. Globe icon does not clip. |
| Empty title / empty description | Skip the `Text` row entirely (do not render an empty box). |
| Tap on thumb vs text vs footer | Uniform — `Modifier.clickable` on the surface, single tap target. |

## Risks

- **CCT toolbar-color matching** is a small Color/Argb conversion that's easy to get wrong (alpha, sRGB vs display-P3). Verify on a Pixel emulator with both light and dark themes before merging.
- **`Uri.parse` for the `host` extraction** in the renderer mixes `android.net.Uri` (Android-only) with the lib's typed `Uri.raw` string. The renderer takes `String`, not the lib type, so this is contained — but worth a one-line comment in the composable to avoid future confusion.
- **120 Hz scroll regression**: any rendering change in PostCard's children is a candidate for a scroll-perf regression. Mitigation: 1.91:1 fixed ratio (no measurement-after-load), `@Stable` data class, `ImmutableList` not used here (no list field). Verify by scrolling the with-thumb fixture in the live feed before merging.

## Roadmap link

This is v1.1 of the embed-scope roadmap defined in [2026-04-25-postcard-embed-scope-v1.md](2026-04-25-postcard-embed-scope-v1.md). Successor tiers: v2 (`record`, nubecita-6vq), v3 (`recordWithMedia`, nubecita-umn). v3 (`video`, nubecita-xsu) shipped ahead of this tier.
