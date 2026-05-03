## ADDED Requirements

### Requirement: `FeedItemUi` exposes a `SelfThreadChain` sealed variant for same-author chains

The system SHALL extend the `net.kikin.nubecita.data.models.FeedItemUi` sealed interface with a third variant `SelfThreadChain` carrying an `ImmutableList<PostUi>` of same-author posts in chronological order. The variant MUST satisfy:

- `posts.size >= 2` — a single-post chain is a `Single`, not a `SelfThreadChain`. Construction sites that produce `posts.size < 2` are programmer errors.
- All elements of `posts` MUST share the same `author.did`. Mixed-author "chains" are not legal `SelfThreadChain` instances; the producer (the feed mapper) is responsible for upholding this.
- Posts MUST be ordered root-most first. `posts[0]` is the chain's root post; `posts.last()` is the leaf the user thinks of as "this entry in my timeline".
- `key: String` MUST equal `posts.last().id` — leaf-anchored, matching `ReplyCluster`'s pagination contract.
- `SelfThreadChain` MUST NOT carry per-feed metadata (no `repostedBy` field on the chain itself). Per-feed metadata stays on individual `PostUi` elements where applicable; for chains, the `repostedBy` field on every `PostUi` is `null` because reposted entries are excluded from chain links by the producer (see `feature-feed` spec).

#### Scenario: A 3-post chain has size 3 and ends on the leaf

- **WHEN** the feed mapper produces a `SelfThreadChain` from three consecutive same-author self-replies whose URIs form `root.uri → reply1.uri → reply2.uri`
- **THEN** the resulting `SelfThreadChain.posts` has size 3, `posts[0].id == root.uri`, `posts[1].id == reply1.uri`, `posts[2].id == reply2.uri`, and `key == reply2.uri`.

#### Scenario: All chain posts share the same author DID

- **WHEN** any `SelfThreadChain` value is inspected
- **THEN** `posts.distinctBy { it.author.did }.size == 1` — the chain MUST be pure same-author by construction.

#### Scenario: Sealed-interface exhaustiveness propagates to consumers

- **WHEN** any consumer of `FeedItemUi` is recompiled after `SelfThreadChain` is introduced
- **THEN** every `when (item: FeedItemUi)` block without an `is FeedItemUi.SelfThreadChain ->` branch SHALL fail compilation with a non-exhaustive-when warning treated as an error (Kotlin sealed-interface contract).

### Requirement: `SelfThreadChain` MUST NOT introduce new module-level dependencies

The addition of the `SelfThreadChain` variant SHALL NOT change the dependency graph of `:data:models`. The variant uses only types already exposed by the module: `PostUi`, `kotlinx.collections.immutable.ImmutableList`, `androidx.compose.runtime.Stable`. No new `atproto-*` dependencies, no new Compose dependencies, no Hilt.

#### Scenario: No new dependency lines

- **WHEN** `:data:models/build.gradle.kts` is diffed before/after this change
- **THEN** the `dependencies { ... }` block SHALL be unchanged.
