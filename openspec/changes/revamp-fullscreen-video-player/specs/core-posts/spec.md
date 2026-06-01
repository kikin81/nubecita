## ADDED Requirements

### Requirement: `:core:posts` exposes `PostThreadRepository` for thread fetches

The system SHALL expose `net.kikin.nubecita.core.posts.PostThreadRepository` as
a public interface in `:core:posts` for resolving a post's thread (focus post +
ancestors + direct replies/folds) via `app.bsky.feed.getPostThread`. The
interface MUST live in `:core:posts` (relocated from
`:feature:postdetail:impl/data`) so that both `:feature:postdetail:impl` and the
fullscreen player's comments sheet (`:feature:videoplayer:impl`) consume one
shared read surface rather than duplicating the fetch or coupling feature impl
modules to each other. The method SHALL accept a plain `String` URI (consistent
with `PostRepository.getPost`) and return a `Result` whose success value carries
the focus post and its replies projected to `:data:models` types via
`:core:feed-mapping`'s shared helpers — never a divergent local projection.

#### Scenario: Consumers inject the interface, not the implementation

- **WHEN** any consumer of thread data (`PostDetailViewModel`, the video
  player's comments presenter) is inspected
- **THEN** it MUST declare a constructor parameter typed `PostThreadRepository`
  and MUST NOT declare the concrete default class or the atproto-kotlin client
  surface directly

#### Scenario: Thread fetch returns mapped replies

- **WHEN** `getPostThread(uri)` resolves successfully against an existing post
- **THEN** the `Result.success` value carries the focus `PostUi` and its direct
  replies projected via `:core:feed-mapping`, so the same wire reply renders
  byte-identically in post-detail and in the comments sheet

#### Scenario: Single import of the getPostThread service

- **WHEN** the source tree is searched for imports of the atproto-kotlin client
  surface carrying `getPostThread`
- **THEN** the only matching import is in the default `PostThreadRepository`
  implementation in `:core:posts`

## MODIFIED Requirements

### Requirement: Single import of `app.bsky.feed.getPosts`

The default implementation of `PostRepository` MUST be the only file in the project source tree that imports the atproto-kotlin client surface for `app.bsky.feed.getPosts`. Other consumers MUST go through the `PostRepository` interface. This mirrors the single-import discipline `:core:posts`'s `PostThreadRepository` enforces for `getPostThread`.

#### Scenario: Single import of the getPosts service

- **WHEN** the source tree is searched for imports of the atproto-kotlin client surface carrying `getPosts`
- **THEN** the only matching import is in the default `PostRepository` implementation in `:core:posts`
