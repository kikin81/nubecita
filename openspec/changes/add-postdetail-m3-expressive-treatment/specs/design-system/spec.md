## ADDED Requirements

### Requirement: PostCard renders multi-image embeds via `HorizontalMultiBrowseCarousel`

The `PostCard` composable in `:designsystem` SHALL conditionally swap its image-embed rendering branch based on the count of images in the embed. When `EmbedUi.Images.images.size > 1`, PostCard MUST delegate to `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` from the M3 carousel API. When `images.size == 1`, the existing single-image rendering path MUST be preserved byte-for-byte — the swap MUST NOT change the rendering of single-image posts in any consuming feature (feed, post detail, future profile / search / notifications surfaces).

The carousel's preferred-item-width MUST be sized to the same target as PostCard's existing single-image preferred width so that single-image and first-slide-of-multi-image rendering are visually consistent in the feed. Carousel slide aspect ratios MUST be per-slide (the carousel's default behavior); mixed-aspect-ratio posts (portrait + landscape in the same embed) MUST NOT be normalized via letterboxing.

The swap MUST live inside PostCard's existing image-embed branch — no new public composable, no new public API. PostCard's caller-facing signature is unchanged.

#### Scenario: Multi-image post renders the carousel

- **WHEN** `PostCard` is composed with an `EmbedUi.Images` value whose `images.size == 3`
- **THEN** the resulting layout contains a `HorizontalMultiBrowseCarousel` rendering three slides, each loaded via the existing Coil image pipeline, and the carousel's snap and spring behavior is the M3 default

#### Scenario: Single-image post path is unchanged

- **WHEN** `PostCard` is composed with an `EmbedUi.Images` value whose `images.size == 1`
- **THEN** the rendered output matches the pre-change single-image PostCard byte-for-byte at the screenshot level — no carousel container, no slide chrome, no preferred-item-width sizing logic

#### Scenario: Caller signature unchanged

- **WHEN** any consumer of `PostCard` (FeedScreen, PostDetailScreen, future surfaces) is inspected
- **THEN** the call site is unmodified by this change — the carousel swap is internal to PostCard, never visible at the call boundary

#### Scenario: Mixed-aspect carousel does not letterbox

- **WHEN** `PostCard` renders a multi-image embed whose three images include one portrait and two landscape
- **THEN** the carousel slides size per-slide using the carousel's default sizing — no slide is letterboxed to match a tallest-or-widest target
