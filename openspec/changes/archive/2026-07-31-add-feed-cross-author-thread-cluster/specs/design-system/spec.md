## ADDED Requirements

### Requirement: `PostCard` accepts `connectAbove` / `connectBelow` parameters

`PostCard` SHALL accept two new `Boolean` parameters: `connectAbove: Boolean = false` and `connectBelow: Boolean = false`, defaulted to `false`. When either flag is `true`, `PostCard`'s root `Modifier` chain SHALL apply `Modifier.threadConnector(connectAbove, connectBelow, color = MaterialTheme.colorScheme.outlineVariant)` from `:designsystem/component/ThreadConnector.kt`. When both flags are `false` (the default), no `threadConnector` modifier SHALL be applied.

This unblocks `nubecita-m28.2` Section A's "PostCard integration" sub-scope which was deferred from PR #77's primitives-only landing.

#### Scenario: PostCard with both flags false is unchanged

- **WHEN** `PostCard(post = ..., connectAbove = false, connectBelow = false)` is composed
- **THEN** the rendered output is pixel-identical to the pre-change `PostCard(post = ...)` — no threadConnector applied

#### Scenario: PostCard with connectAbove + connectBelow draws full connector

- **WHEN** `PostCard(post = ..., connectAbove = true, connectBelow = true)` is composed
- **THEN** the rendered output applies `Modifier.threadConnector(connectAbove = true, connectBelow = true, color = MaterialTheme.colorScheme.outlineVariant)` to the post's outer container, drawing connector lines above and below the avatar

### Requirement: `:designsystem` provides a `ThreadCluster` composable

`:designsystem/component/ThreadCluster.kt` SHALL expose a public `@Composable fun ThreadCluster(...)` that renders a feed-level reply cluster: root post on top, optional `ThreadFold` between root and parent, parent post, leaf post — joined by avatar-gutter connector lines.

The signature SHALL be (parameter order per the project's Compose convention used by `PostCard` and `ThreadFold` — required params first, then `modifier`, then other defaulted params, then trailing lambdas):

```kotlin
@Composable
fun ThreadCluster(
    root: PostUi,
    parent: PostUi,
    leaf: PostUi,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks.None,
    hasEllipsis: Boolean = false,
    leafVideoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)? = null,
    leafQuotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
    onFoldTap: () -> Unit = {},
)
```

Internal layout SHALL be a `Column`:

| Position | Composable | `connectAbove` | `connectBelow` | `videoEmbedSlot` |
|---|---|---|---|---|
| top | `PostCard(root)` | false | true | null |
| (when `hasEllipsis`) | `ThreadFold(onClick = onFoldTap)` | — | — | — |
| middle | `PostCard(parent)` | true | true | null |
| bottom | `PostCard(leaf)` | true | false | `leafVideoEmbedSlot` |

`PostCallbacks` SHALL be passed through to all three `PostCard`s. Tap targets on root, parent, leaf, and fold MAY all be no-ops in v1 (post-detail navigation lands later); the `onFoldTap` parameter exists so callers can wire it when post-detail is available without an API change.

#### Scenario: ThreadCluster without ellipsis renders three PostCards in a Column

- **WHEN** `ThreadCluster(root, parent, leaf, callbacks, hasEllipsis = false)` is composed
- **THEN** the rendered output is a `Column` containing `PostCard(root, connectBelow = true)` + `PostCard(parent, connectAbove = true, connectBelow = true)` + `PostCard(leaf, connectAbove = true)`
- **AND** no `ThreadFold` is rendered

#### Scenario: ThreadCluster with ellipsis inserts a ThreadFold between root and parent

- **WHEN** `ThreadCluster(root, parent, leaf, callbacks, hasEllipsis = true)` is composed
- **THEN** the rendered output is a `Column` containing `PostCard(root, connectBelow = true)` + `ThreadFold(onClick = onFoldTap)` + `PostCard(parent, connectAbove = true, connectBelow = true)` + `PostCard(leaf, connectAbove = true)`

#### Scenario: ThreadCluster collapses the parent slot when parent equals root

- **WHEN** `ThreadCluster(root, parent, leaf, callbacks, hasEllipsis = false)` is composed AND `parent.id == root.id` (i.e., the leaf is a direct reply to the root post — common for self-threads or any direct reply)
- **THEN** the rendered output is a `Column` containing `PostCard(root, connectBelow = true)` + `PostCard(leaf, connectAbove = true)` only — the `parent` slot is NOT rendered (rendering it would visually duplicate the root post)
- **AND** no `ThreadFold` is rendered

#### Scenario: ThreadCluster passes leaf-only video slot

- **WHEN** the caller supplies a non-null `leafVideoEmbedSlot`
- **THEN** the leaf `PostCard` receives the slot
- **AND** root + parent `PostCard` receive `videoEmbedSlot = null` (their video embeds, if any, render via the static-poster fallback in PostCard)
