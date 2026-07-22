# Profile Videos Entry (Slice 6) — Design

**bd:** nubecita-zdv8.7 (child of epic nubecita-zdv8 — Vertical video feed; depends on the shipped Slice 3 vertical feed screen).

## Goal

Tapping a video on a user's profile opens the existing immersive vertical video
feed showing **that author's** videos, positioned at the tapped video — a
"TikTok on a profile." The feed is sourced from
`getAuthorFeed(actor, filter="posts_with_video")`.

Scope: the entry point is the profile's **Media tab** video cells only. Image
cells keep opening the image lightbox; inline video posts in the Posts/Replies
tabs keep their current single-fullscreen-player behavior (out of scope).

## Architecture

The vertical feed already exists and is driven by a single `VideoFeedSource`
(`suspend fun loadPage(cursor: String?): Result<VideoFeedPage>`, where
`VideoFeedPage(items: List<PostUi>, cursor: String?)`). Today there is exactly
one production implementation — `DefaultTrendingVideoSource`, which pulls
Bluesky's "thevids" custom feed — bound with a single unqualified `@Binds`. The
DI module's own KDoc names this slice as the reason to add per-entry-point source
selection. Slice 6 adds a second source and a factory that picks between them
based on the route.

### 1. `VideoFeed` route gains an author

`:feature:videos:api/VideoFeed.kt`:

```kotlin
@Serializable
data class VideoFeed(
    val startPostUri: String? = null,
    val authorDid: String? = null,
) : NavKey
```

- `authorDid == null` → the existing trending feed (the Discover carousel push
  `VideoFeed(postUri)` is unchanged and stays trending — fully backward
  compatible).
- `authorDid != null` → that author's videos.
- `startPostUri` resolves to an initial index **by identity** in the ViewModel
  (`indexOfFirst { it.post.id == startPostUri }`). This is why the route carries a
  URI, not an index: the Media grid shows images + videos interleaved while the
  author feed is videos-only, so positions differ — identity resolves the tapped
  video to the correct slot in the video-only feed.

`authorDid` holds whatever actor identifier the profile already has for the
viewed user (a DID; `getAuthorFeed` accepts a DID as its `actor`).

#### Opening at the tapped video — page-until-found

Identity resolution only searches what is **loaded**, and the feed paginates
(30 posts/page). The current single-page load is correct only when the tapped
video falls in page 1; a video deep in a prolific profile would not be found and
`indexOfFirst` → `-1` would silently open the newest video instead — the exact
"it just plays the first video" failure this slice must avoid.

So when a `startPostUri` is present, the ViewModel **loads pages from the top and
accumulates until the tapped URI appears**, then opens the pool at that absolute
index:

The seek reuses the ViewModel's existing per-page handling — `page.items` →
`it.toVideoFeedItemOrNull()`, dedup via `distinctBy { it.post.id }` (the appview
can return a post twice), accumulate into `loaded` — and only wraps it in a loop:

```
// loaded: the existing MutableList<VideoFeedItem>
var cursor: String? = null
var pages = 0
do {
    val page = source.loadPage(cursor).getOrElse { /* onFailure → status = Error; return */ }
    loaded += page.items.mapNotNull { it.toVideoFeedItemOrNull() }
    // dedup across pages too, not just within one
    val deduped = loaded.distinctBy { it.post.id }; loaded.clear(); loaded += deduped
    cursor = page.cursor
    pages++
} while (
    route.startPostUri != null &&
    loaded.none { it.post.id == route.startPostUri } &&
    cursor != null &&
    pages < MAX_SEEK_PAGES
)

if (loaded.isEmpty()) { status = Error } else {
    val initialIndex = loaded.indexOfFirst { it.post.id == route.startPostUri }.coerceAtLeast(0)
    // unchanged: applyInteractions → setState(Content, activeIndex) → pool.bind(startIndex) → cache.seed
}
```

- The tapped video is **guaranteed to exist** in the `posts_with_video` feed — it
  is a video post by this author, and both feeds are the same reverse-chronological
  author posts under different filters — so the loop terminates on the right item
  for any real tap.
- Because the seek loads from the **top**, every video **newer** than the tapped one
  is already in the list, above the open index: the user can swipe up to reach the
  author's first (newest) video, and swipe down to paginate through older ones. The
  tapped video is *not* item 0 — it sits at its true position with its predecessors
  loaded above it. The feed is **videos-only**, so "the first item" here means the
  first *video*, not the first Media-grid cell (the grid's photos are filtered out —
  this is a video player, not the media grid).
- The stop condition already covers the no-`startPostUri` case: the `do` body runs
  once and the `while` short-circuits on `route.startPostUri != null`, so a null
  start (today's trending/carousel entry with the video in page 1) loads exactly one
  page and opens at 0 — behavior unchanged. When a `startPostUri` is set, the loop
  strengthens **every** entry (trending and profile alike).
- `MAX_SEEK_PAGES` (e.g. 6 ≈ 180 videos) is a safety bound for pathological depth
  and bug cases only; hitting it (or a genuinely aged-out post) falls back to index
  0, matching today's behavior. Real taps resolve in one page for recent videos and
  a handful for deep ones, behind the existing initial-load indicator.

`loaded`, `VideoFeedItem`, `it.source`, `it.post`, and `toVideoFeedItemOrNull()`
above are the ViewModel's existing internals; the change is the seek loop plus
cross-page dedup, not new plumbing.

### 2. `AuthorVideoSource` (`:core:video-feed`)

A `VideoFeedSource` bound to one actor, mirroring `DefaultTrendingVideoSource`
but calling `getAuthorFeed` instead of `getFeed`:

```kotlin
class AuthorVideoSource @AssistedInject constructor(
    @Assisted private val actor: String,
    private val xrpcClientProvider: XrpcClientProvider,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : VideoFeedSource {
    override suspend fun loadPage(cursor: String?): Result<VideoFeedPage> =
        withContext(dispatcher) {
            runCatching {
                val service = FeedService(xrpcClientProvider.authenticated())
                val response = service.getAuthorFeed(
                    GetAuthorFeedRequest(
                        actor = AtIdentifier(actor),
                        filter = "posts_with_video",
                        cursor = cursor,
                        limit = PAGE_LIMIT,
                    ),
                )
                VideoFeedPage(items = toVideoPosts(response.feed), cursor = response.cursor)
            }.onFailure { if (it is CancellationException) throw it }
        }

    @AssistedFactory
    interface Factory { fun create(actor: String): AuthorVideoSource }
}
```

- `toVideoPosts` is the same shared mapper `DefaultTrendingVideoSource` uses
  (`feed.mapNotNull { it.post.toPostUiCore() }.videoPostsOnly()`), so image-only
  or non-video posts that slip through the filter are still dropped, and every
  emitted `PostUi` is guaranteed to carry an `EmbedUi.Video`.
- `CancellationException` is rethrown out of `runCatching` (matches the trending
  source; avoids swallowing coroutine cancellation).
- `PAGE_LIMIT` matches the existing sources (30).

### 3. `VideoFeedSourceFactory`

Injected into `VideoFeedViewModel` **in place of** the direct `VideoFeedSource`:

```kotlin
interface VideoFeedSourceFactory {
    fun create(route: VideoFeed): VideoFeedSource
}

class DefaultVideoFeedSourceFactory @Inject constructor(
    private val trending: Provider<DefaultTrendingVideoSource>,
    private val authorSourceFactory: AuthorVideoSource.Factory,
) : VideoFeedSourceFactory {
    override fun create(route: VideoFeed): VideoFeedSource =
        route.authorDid
            ?.let { authorSourceFactory.create(it) }
            ?: trending.get()
}
```

`VideoFeedViewModel.init` (or wherever `source` is first used) resolves the
source once: `private val source = sourceFactory.create(route)`. Everything
downstream (paging, pool binding, the `startPostUri`→index resolution) is
unchanged.

**DI change** (`:core:video-feed` production module): replace the
`@Binds DefaultTrendingVideoSource -> VideoFeedSource` binding with
`@Binds DefaultVideoFeedSourceFactory -> VideoFeedSourceFactory`. The trending
source is `@Inject`-constructable and reached via `Provider`; the author source
is reached via its `@AssistedFactory`. No other code injects `VideoFeedSource`
directly (only the ViewModel), so the removed binding has no other consumers.

### 4. Profile entry point

`ProfileViewModel`'s Media-cell tap handling currently emits
`ProfileEffect.NavigateToVideoPlayer(postUri)` for a video cell (routing to the
single `@OuterShell` fullscreen `VideoPlayerRoute`). Redirect **only** that
video-cell branch to the generic, already-wired effect:

```kotlin
sendEffect(ProfileEffect.NavigateTo(VideoFeed(startPostUri = postUri, authorDid = actor)))
```

- `actor` is the profile's actor identifier, already held by the ViewModel.
- `ProfileEffect.NavigateTo(key: NavKey)` is the existing canonical "push any
  MainShell sub-route" effect; the screen already maps it to
  `navState.add(key)`. `VideoFeed` is a `@MainShell` route (same shell as
  Profile), so no new host callback, no `@OuterShell` involvement.
- Image cells (`isVideo == false`) are untouched — they keep routing to the
  image viewer.
- The `NavigateToVideoPlayer` effect + its host callback stay in place for any
  other caller (e.g. inline post-card videos); only the Media-cell video branch
  changes.

**Module dependency:** `:feature:profile:impl` gains a dependency on
`:feature:videos:api` (NavKey only — it already depends on other feature `:api`
modules). No dependency on `:feature:videos:impl`.

## Data flow

```
Profile Media tab: tap a video cell (MediaCell.isVideo == true)
  → ProfileViewModel emits NavigateTo(VideoFeed(startPostUri = <uri>, authorDid = <profile actor>))
  → ProfileScreen collector → navState.add(route)
  → VideosNavigationModule entry<VideoFeed> builds VideoFeedViewModel(route)
  → sourceFactory.create(route): authorDid non-null → AuthorVideoSource(actor)
  → seek: loadPage from the top, accumulating pages until post.id == startPostUri
    (getAuthorFeed(actor, filter="posts_with_video") per page)
  → pool binds all accumulated videos, startIndex = the tapped video's index
  → immersive vertical feed, that author's videos, opened at the tapped video
```

## Testing

- **`AuthorVideoSource`** (`:core:video-feed` unit): mock `FeedService`; assert
  the request carries `filter = "posts_with_video"` and the actor; assert wire →
  `PostUi` mapping drops non-video posts and threads `response.cursor`; assert
  `CancellationException` propagates rather than being wrapped in a failed
  `Result`.
- **`VideoFeedSourceFactory`** (unit): `authorDid == null` returns the trending
  source; `authorDid != null` returns an `AuthorVideoSource` built for that
  actor.
- **`VideoFeedViewModel`** (unit): constructed with a fake factory; a route with
  an `authorDid` drives the author source; the existing trending path (null
  `authorDid`) still works. Existing VM tests updated for the factory injection.
  **Page-until-found is the key case to cover:** a `startPostUri` whose post is on
  page 1 opens at its index; a `startPostUri` on page 2+ triggers additional
  `loadPage` calls and opens at the correct **absolute** index (not 0); a
  `startPostUri` never present (aged out / past `MAX_SEEK_PAGES`) falls back to 0;
  no `startPostUri` loads exactly one page. Use a fake source that serves a
  multi-page video list so the seek loop is actually exercised.
- **`ProfileViewModel`** (unit): tapping a Media video cell emits
  `NavigateTo(VideoFeed(startPostUri = <uri>, authorDid = <actor>))`; tapping an
  image cell still emits the image-viewer effect.
- **Bench:** a bench-flavor `VideoFeedSourceFactory` that returns the existing
  `FakeVideoFeedSource` for any route (keeps the profile-videos path offline);
  ensure the bench profile's Media grid exposes at least one tappable video cell
  (extend the bench profile fixture if it does not — pre-authorized).
- **Device (Pixel Fold, required):** open a profile → Media tab → tap a video
  cell → the immersive vertical feed opens at that video and swipes through the
  author's videos. Verify an image cell still opens the image viewer.

## Non-goals (this slice)

- No dedicated profile "Videos" tab (the Media grid is the entry).
- Inline video posts in the Posts/Replies tabs keep their current
  single-fullscreen-player behavior.
- No change to the trending/Discover carousel path.
- No new UI on the profile — only the destination of an existing tap changes.

## Files

- Modify: `feature/videos/api/.../VideoFeed.kt` (add `authorDid`).
- Create: `core/video-feed/.../AuthorVideoSource.kt` (source + `@AssistedFactory`).
- Create: `core/video-feed/.../VideoFeedSourceFactory.kt` (+ `DefaultVideoFeedSourceFactory`).
- Modify: `core/video-feed/src/production/.../di/VideoFeedModule.kt` (bind the factory instead of the source).
- Create/modify: bench `di/VideoFeedModule.kt` + a bench `VideoFeedSourceFactory`.
- Modify: `feature/videos/impl/.../VideoFeedViewModel.kt` (inject factory, resolve source from route).
- Modify: `feature/profile/impl/.../ProfileViewModel.kt` (redirect the Media video-cell branch).
- Modify: `feature/profile/impl/build.gradle.kts` (add `:feature:videos:api`).
- Modify: bench profile fixture if it lacks a video Media cell.
- Tests: `AuthorVideoSourceTest`, `VideoFeedSourceFactoryTest`, updated `VideoFeedViewModelTest`, updated `ProfileViewModelTest`.
