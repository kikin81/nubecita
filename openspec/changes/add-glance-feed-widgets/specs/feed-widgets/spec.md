## ADDED Requirements

### Requirement: Glance widgets render the offline feed cache head

The system SHALL provide Jetpack Glance home-screen widgets that render the most recent posts of a feed by reading `head(feedKey, n)` from `:core:feed-cache`, performing **no network request** on the render path. The MVP SHALL ship a **Following** widget (free) and a fixed **Discover** widget (free). Widgets SHALL obtain dependencies via a Hilt `@EntryPoint` resolved with `EntryPointAccessors.fromApplication` (Glance has no Hilt composition), and any cache/disk read SHALL run off the Glance main thread.

#### Scenario: Following widget renders cached posts
- **WHEN** the Following widget composes and the signed-in account's Following partition has cached posts
- **THEN** it renders up to `n` most-recent posts from `head(FeedKey.following(did), n)` without issuing any network call

#### Scenario: Discover widget renders the fixed feed
- **WHEN** the Discover widget composes
- **THEN** it renders up to `n` most-recent posts from the fixed Discover (`whats-hot`) partition's head

#### Scenario: Render reads off the main thread
- **WHEN** a widget reads the cache head or a prefetched thumbnail during `provideGlance`
- **THEN** the read executes on a background dispatcher, never blocking Glance's main-thread composition with network or uncontrolled I/O

### Requirement: Widgets show intentional loading, empty, and signed-out states

A widget SHALL NEVER render blank. It SHALL show a distinct **loading** state before the first cache read resolves, an **empty** state when the partition has no cached posts, and a **signed-out** state when there is no signed-in account, each with an affordance to open the app.

#### Scenario: Signed-out state
- **WHEN** a widget composes while the user is signed out
- **THEN** it renders a signed-out prompt that opens the app, not a blank surface

#### Scenario: Empty state
- **WHEN** a widget composes while signed in but the feed partition has no cached posts
- **THEN** it renders an empty state (not blank) inviting a refresh / open

### Requirement: Manual refresh affordance enqueues the on-demand worker

Each widget SHALL expose a manual-refresh affordance that enqueues `:core:widget-sync`'s on-demand `OneTimeWorkRequest` (`ExistingWorkPolicy.KEEP`), and adding a widget instance SHALL likewise enqueue an on-demand refresh so the widget populates without waiting for the periodic run. The widget render path SHALL NOT fetch from the network itself.

#### Scenario: Manual refresh triggers background work
- **WHEN** the user taps the widget's refresh affordance
- **THEN** the on-demand widget-refresh work is enqueued (deduplicated via `KEEP`) and no network request is made on the widget's own render path

#### Scenario: Widget add populates the cache
- **WHEN** a widget instance is added to the home screen
- **THEN** an on-demand refresh is enqueued so the widget shows content shortly after placement

### Requirement: Per-post media is a single thumbnail with an overflow count

A post with media SHALL render at most **one** image — the first image's thumbnail (or the video poster with a play overlay) from the pre-decoded bitmap cache — overlaid with a **"+N" badge** when the post has more than one image. The widget SHALL NOT reproduce the app's multi-image layouts or carousel. Quote-post and external-link embeds SHALL render text-only for the MVP. The overflow count SHALL be derived from the cached embed without decoding additional images. Media SHALL carry an accessibility description conveying the total count.

#### Scenario: Multi-image post shows first thumb + count
- **WHEN** a cached post has four images and its first thumbnail is prefetched
- **THEN** the widget renders the single first thumbnail with a "+3" (or "4 images") overflow badge and an accessibility description naming the total

#### Scenario: Video post shows poster + play overlay
- **WHEN** a cached post has a video embed with a prefetched poster
- **THEN** the widget renders the poster thumbnail with a play-icon overlay

#### Scenario: Thumbnail missing falls back gracefully
- **WHEN** a post has media but no prefetched thumbnail is available yet
- **THEN** the widget renders the post text (and an optional media indicator) without breaking layout

### Requirement: Image-prefetch pipeline with bounded eviction

The system SHALL provide a `WidgetImagePrefetcher` implementation that, for each post in a feed's current `head(n)`, decodes **only the first image's thumbnail** (or video poster) off the UI/scroll path to a bounded bitmap (a single fixed bounding box, since the background context cannot know the active responsive cell size), and persists it to a dedicated cache directory with the path/URI stored in widget state keyed by `postUri`. The pipeline SHALL **evict** thumbnails: on each run it SHALL delete the file and key for any `postUri` no longer in the current `head(n)` set, and it SHALL clear an account's thumbnails when that account's cache is cleared (logout). This bounds the image cache to approximately `n` thumbnails per feed.

#### Scenario: Prefetch decodes one thumbnail per shown post
- **WHEN** the prefetcher runs for a feed whose head contains posts with media
- **THEN** it decodes exactly one thumbnail (first image or video poster) per such post to a bounded bitmap and stores its path keyed by `postUri`

#### Scenario: Eviction prunes posts that left the head
- **WHEN** the prefetcher runs and a previously-cached `postUri` is no longer in the current `head(n)` set
- **THEN** that thumbnail's file and its widget-state key are deleted

#### Scenario: Logout clears the account's thumbnails
- **WHEN** an account's feed cache is cleared (logout / account removal)
- **THEN** that account's thumbnail directory is cleared

### Requirement: Real Glance-backed widget updater

The system SHALL provide a Glance-backed `WidgetUpdater` implementation that re-renders the feed widgets via `GlanceAppWidgetManager` / `updateAll`, replacing `:core:widget-sync`'s no-op default in the production flavor. Non-production (bench) flavors SHALL retain the no-op so they remain Glance-free and issue no widget work.

#### Scenario: Background refresh re-renders the widgets
- **WHEN** the background worker completes a successful refresh and invokes `WidgetUpdater.updateFeedWidgets()` in a production build
- **THEN** the Glance widgets re-render from the freshly-refreshed cache

#### Scenario: Bench flavor stays Glance-free
- **WHEN** the app is built in the bench flavor
- **THEN** the no-op updater is bound and no Glance/AppWidget calls are issued

### Requirement: Tapping a post deep-links into the thread

Tapping a post in a widget SHALL open that post's thread in the app via `actionStartActivity` carrying the existing `nubecita://` / `app.nubecita` deep-link intent routed by the app's `NavKeyDeepLinkMatcher` / `DeepLinkRouter`. The widget SHALL NOT use a `clickable {}` lambda to launch an Activity, to comply with the Android-12 background-activity-launch (trampoline) restriction.

#### Scenario: Post tap opens the thread
- **WHEN** the user taps a post row in a widget
- **THEN** the app opens to that post's thread via the existing deep-link routing, launched through `actionStartActivity`

#### Scenario: No trampoline
- **WHEN** a widget element launches the app
- **THEN** it does so through `actionStartActivity` (not a runtime lambda that starts an Activity), satisfying the Android-12 trampoline ban

### Requirement: Configurable widget with a configuration activity

The system SHALL provide a configurable widget plus a configuration activity (the receiver's `APPWIDGET_CONFIGURE` target) that lists the user's saved/pinned feeds (via `:core:feeds`), persists the chosen `FeedKey` to that widget instance's Glance state, and returns `RESULT_OK` with the `appWidgetId`. If configuration is dismissed, the widget SHALL default to the Following feed so it is never left blank. The entitlement decision for this widget SHALL sit behind an injectable gate seam; in this change the gate SHALL be **always-allowed** (the `isPro` gate + paywall upsell are added by a later change).

#### Scenario: Choosing a feed configures the widget
- **WHEN** the user picks a saved feed in the configuration activity and confirms
- **THEN** that `FeedKey` is written to the widget instance's state, the activity returns `RESULT_OK` with the `appWidgetId`, and the widget renders that feed's head

#### Scenario: Dismissed configuration defaults to Following
- **WHEN** the user dismisses the configuration activity without choosing a feed
- **THEN** the widget defaults to the Following feed rather than rendering blank

#### Scenario: Gate seam is present but ungated
- **WHEN** any user configures the widget in this change
- **THEN** the entitlement gate allows it (always-allowed), while exposing a seam a later change can bind to an `isPro`-backed decision without restructuring

### Requirement: Widgets meet platform widget-quality requirements

Widgets SHALL satisfy Google's widget-quality requirements: fill the widget grid, declare Android-12 `targetCellWidth/Height` plus legacy `minWidth/Height` and `resizeMode` with sensible min/max bounds, use `SizeMode.Responsive` breakpoints, keep every interactive element at a minimum 48×48 dp touch target at the smallest declared size, use the system widget background and inner corner radii (no custom radius), avoid double-padding, support light and dark with dynamic color and WCAG-contrasting colors, and declare a `previewLayout` with real sample content and a non-generic provider `description`.

#### Scenario: Touch targets honored at minimum size
- **WHEN** a widget is rendered at its smallest declared responsive size
- **THEN** every interactive element remains at least 48×48 dp and content is not cropped

#### Scenario: System radii and fill-grid
- **WHEN** a widget renders
- **THEN** its root uses the system app-widget background radius with `.appWidgetBackground()` and fills the allocated grid, with inner elements using the system inner radius

#### Scenario: Light/dark + dynamic color
- **WHEN** the device theme is light, dark, or dynamic-color
- **THEN** the widget renders with the corresponding theme and WCAG-contrasting colors
