## ADDED Requirements

### Requirement: PostCard renders multi-image embeds via `HorizontalMultiBrowseCarousel`

The `PostCard` composable in `:designsystem` SHALL conditionally swap its image-embed rendering branch based on the count of images in the embed. When `EmbedUi.Images.images.size > 1`, PostCard MUST delegate to `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` from the M3 carousel API. When `images.size == 1`, the existing single-image rendering path MUST be preserved byte-for-byte — the swap MUST NOT change the rendering of single-image posts in any consuming feature (feed, post detail, future profile / search / notifications surfaces).

The carousel's preferred-item-width MUST be sized to the same target as PostCard's existing single-image preferred width so that single-image and first-slide-of-multi-image rendering are visually consistent in the feed. Carousel slide aspect ratios MUST be per-slide (the carousel's default behavior); mixed-aspect-ratio posts (portrait + landscape in the same embed) MUST NOT be normalized via letterboxing.

The swap MUST live inside PostCard's existing image-embed branch — no new public composable, no new public API. PostCard's caller-facing signature MAY be extended with **additive, backwards-compatible** parameters (e.g. `onImageClick: (imageIndex: Int) -> Unit = {}` with a default no-op so existing call sites compile unchanged), but MUST NOT remove or change the meaning of any existing parameter. The post-detail feature requires the per-image-index click callback to dispatch its `NavigateToMediaViewer(imageIndex)` effect; consumers that don't pass the callback (the feed, future profile / search surfaces) keep their current behavior — the default no-op makes the swap a no-op for them.

#### Scenario: Multi-image post renders the carousel

- **WHEN** `PostCard` is composed with an `EmbedUi.Images` value whose `images.size == 3`
- **THEN** the resulting layout contains a `HorizontalMultiBrowseCarousel` rendering three slides, each loaded via the existing Coil image pipeline, and the carousel's snap and spring behavior is the M3 default

#### Scenario: Single-image post path is unchanged

- **WHEN** `PostCard` is composed with an `EmbedUi.Images` value whose `images.size == 1`
- **THEN** the rendered output matches the pre-change single-image PostCard byte-for-byte at the screenshot level — no carousel container, no slide chrome, no preferred-item-width sizing logic

#### Scenario: Existing call sites compile unchanged

- **WHEN** any consumer that did NOT pass `onImageClick` (FeedScreen, future profile / search surfaces) is recompiled against the updated PostCard
- **THEN** the call site compiles without modification — the new parameter has a default no-op value, and no behavioral change is observable in single-image OR multi-image posts in those surfaces (multi-image posts get the carousel rendering but tapping a slide is a no-op when no callback was passed)

#### Scenario: Post-detail wires the per-index callback

- **WHEN** `PostDetailScreen` composes the Focus PostCard
- **THEN** the call passes `onImageClick = { index -> /* dispatch NavigateToMediaViewer */ }`, and tapping a slide invokes the callback with the slide's index

#### Scenario: Mixed-aspect carousel does not letterbox

- **WHEN** `PostCard` renders a multi-image embed whose three images include one portrait and two landscape
- **THEN** the carousel slides size per-slide using the carousel's default sizing — no slide is letterboxed to match a tallest-or-widest target
