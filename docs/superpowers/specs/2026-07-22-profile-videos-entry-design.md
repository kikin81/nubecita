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
- `startPostUri` continues to resolve to an initial index **by identity** in the
  ViewModel (`indexOfFirst { it.post.id == startPostUri }.coerceAtLeast(0)`). This
  is why the route carries a URI, not an index: the Media grid shows images +
  videos interleaved while the author feed is videos-only, so positions differ —
  identity resolves the tapped video to the correct slot in the video-only feed,
  and falls back to the top if it aged out.

`authorDid` holds whatever actor identifier the profile already has for the
viewed user (a DID; `getAuthorFeed` accepts a DID as its `actor`).

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
  → loadPage(null) → getAuthorFeed(actor, filter="posts_with_video")
  → startPostUri resolves to the tapped video's index; pool binds at that index
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
