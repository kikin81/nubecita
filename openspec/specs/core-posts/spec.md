# core-posts Specification

## Purpose
`:core:posts` — the single-post *read* surface: `PostRepository.getPost(uri)` projecting the wire `PostView` to `PostUi` through `:core:feed-mapping`'s shared helper so feed and post-detail renderings stay identical, and the single-import discipline that keeps `app.bsky.feed.getPosts` confined to its default implementation. (`:core:posting` owns the write surface.)
## Requirements
### Requirement: `:core:posts` exposes `PostRepository` for single-post fetches

The system SHALL expose `net.kikin.nubecita.core.posts.PostRepository` in `:core:posts` with method `suspend fun getPost(uri: String): Result<PostUi>`. The interface MUST live in `:core:posts` to own the read surface for resolving posts by URI. The default implementation MUST be the only project class calling `app.bsky.feed.getPosts` with a single URI, and MUST project wire `PostView` to `PostUi` via `:core:feed-mapping`'s shared `toPostUiCore` helper.

#### Scenario: ViewModel injects the interface

- **WHEN** any consumer (today, `MediaViewerViewModel`) is inspected
- **THEN** it MUST declare a constructor parameter typed `PostRepository` and MUST NOT declare the concrete default class or the atproto-kotlin client surface directly

#### Scenario: Successful fetch returns mapped PostUi

- **WHEN** `getPost(uri)` resolves successfully against an existing post
- **THEN** the `Result.success` value is a `PostUi` whose `embed` slot is populated via `:core:feed-mapping`'s `toEmbedUi` dispatch — `EmbedUi.Images` for image posts, `EmbedUi.Video` for video posts, etc.

#### Scenario: Not-found surfaces as Result.failure

- **WHEN** `getPost(uri)` is called for a URI that has been deleted or is not visible to the current viewer
- **THEN** the wire response's empty `posts` array is converted to `Result.failure` with an error type the consumer can map to a user-visible error message — never `Result.success(null)` and never an exception thrown to the caller

### Requirement: Single import of `app.bsky.feed.getPosts`

The default implementation of `PostRepository` MUST be the only file in the project source tree that imports the atproto-kotlin client surface for `app.bsky.feed.getPosts`. Other consumers MUST go through the `PostRepository` interface. This mirrors the single-import discipline `:feature:postdetail:impl/data/PostThreadRepository`'s spec already enforces for `getPostThread`.

#### Scenario: Single import of the getPosts service

- **WHEN** the source tree is searched for imports of the atproto-kotlin client surface carrying `getPosts`
- **THEN** the only matching import is in the default `PostRepository` implementation in `:core:posts`
