# data-models

## MODIFIED Requirements

### Requirement: PostUi captures everything needed to render a single post

The module MUST define `@Stable data class PostUi` with fields: `id: String`, `cid: String`, `author: AuthorUi`, `createdAt: Instant`, `text: String`, `facets: ImmutableList<Facet>`, `embed: EmbedUi`, `stats: PostStatsUi`, `viewer: ViewerStateUi`, `repostedBy: String?`, `threadRootUri: String?`, and `langs: ImmutableList<String>`. Supporting types `AuthorUi`, `EmbedUi`, `ImageUi`, `PostStatsUi`, and `ViewerStateUi` MUST reside in `:data:models`.

`langs` carries the record's declared BCP-47 language tags from `app.bsky.feed.post`. It MUST default to empty, because the field is optional on the wire and frequently absent. Consumers MUST treat it as a hint and MUST NOT gate behaviour on its presence — an absent `langs` means "unknown", never "the reader's language".

#### Scenario: Constructing a minimal PostUi for a preview

- **WHEN** test code calls `PostUi(id = "p1", cid = "bafy…", author = AuthorUi(...), createdAt = Clock.System.now(), text = "hello", facets = persistentListOf(), embed = EmbedUi.Empty, stats = PostStatsUi(), viewer = ViewerStateUi(), repostedBy = null)`
- **THEN** the construction compiles without supplying `langs`, which defaults to empty

#### Scenario: A post that declares its language carries it through

- **WHEN** a wire record carries `"langs": ["ja"]`
- **THEN** the mapped `PostUi.langs` is `["ja"]`, and it survives the feed cache round-trip

#### Scenario: The embed slot dispatches on a sealed type

- **WHEN** PostCard's body executes `when (post.embed)`
- **THEN** the compiler enforces exhaustiveness across `EmbedUi.Empty`, `EmbedUi.Images`, `EmbedUi.Unsupported`. Adding a new variant in a future change (e.g. `EmbedUi.External`) surfaces as a compile error at every dispatch site, naming the work needed.
