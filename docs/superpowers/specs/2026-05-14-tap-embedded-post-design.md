# Tap an embedded (quoted) post → open PostDetail

**bd issues:** `nubecita-7z2` (designsystem + feed/profile/postdetail), `nubecita-nn3.8` (chats)

**Status:** designed 2026-05-14, awaiting implementation plan.

## Goal

The Bluesky Android client gives the quoted-card region inside a post its own
ripple + click target: tapping the inner card routes to the quoted post's
detail screen, tapping the outer body of the parent post still routes to the
parent's detail screen. Today nubecita's `PostCard` wraps the whole card in
one `Modifier.clickable` that always routes to the parent — the inner quoted
region is effectively dead. Add the inner click target and wire it through
every call site that renders a quoted embed: feed, profile, post-detail, and
chats.

Single PR closes both bd issues. `Closes: nubecita-7z2` and
`Closes: nubecita-nn3.8` in the body.

## Non-goals

- Tapping the `RecordUnavailable` chip — the target is gone, no destination.
- Tapping the `QuotedThreadChip` ("View thread" recursion placeholder) — no
  affordance until a separate bd surfaces a use case.
- Long-press on the quoted card (no spec; consider later if needed).
- Image taps **inside** a quoted post opening MediaViewer — for now the whole
  quoted card is one tap target. Inner images render but only the outer card
  is interactive.
- Recursive embeds inside a quoted post — already bounded at the type system
  via `QuotedEmbedUi` excluding a `Record` variant. The wire format produces
  `QuotedThreadChip` at the mapper boundary.

## Architecture

### Click capture (the load-bearing invariant)

Compose's `Modifier.clickable` natively consumes pointer events, so attaching
`clickable` to the inner `Surface` (in `PostCardQuotedPost`) prevents the
gesture from bubbling to the outer `Modifier.clickable` on `PostCard`'s
`Column`. No manual `pointerInput` or gesture-suppression plumbing required.

For `PostCardRecordWithMediaEmbed` the inner clickable goes on the
**quoted-card portion only**, not the media portion. The media branch keeps
its own taps unchanged (image gallery, external Custom Tab, video controls).

### API shape

```kotlin
// :designsystem/PostCardQuotedPost
@Composable
public fun PostCardQuotedPost(
    quotedPost: QuotedPostUi,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,                              // ← new
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
)
```

Null `onTap` preserves the current screenshot/preview path: no
`Modifier.clickable`, no ripple, genuinely non-interactive. Non-null →
`Surface(modifier = … .clickable(onClick = onTap))`.

```kotlin
// :designsystem/PostCardRecordWithMediaEmbed
@Composable
public fun PostCardRecordWithMediaEmbed(
    record: EmbedUi.RecordOrUnavailable,
    media: EmbedUi.MediaEmbed,
    modifier: Modifier = Modifier,
    onQuotedPostTap: (() -> Unit)? = null,                    // ← new
    onExternalMediaTap: ((uri: String) -> Unit)? = null,
    videoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)? = null,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
)
```

The `onQuotedPostTap` is forwarded only when `record is EmbedUi.Record` —
`RecordUnavailable` stays a no-op chip.

```kotlin
// :designsystem/PostCallbacks
@Stable
data class PostCallbacks(
    val onTap: (PostUi) -> Unit = {},
    val onAuthorTap: (AuthorUi) -> Unit = {},
    val onLike: (PostUi) -> Unit = {},
    val onRepost: (PostUi) -> Unit = {},
    val onReply: (PostUi) -> Unit = {},
    val onShare: (PostUi) -> Unit = {},
    val onShareLongPress: ((PostUi) -> Unit)? = null,
    val onExternalEmbedTap: (uri: String) -> Unit = {},
    val onQuotedPostTap: (QuotedPostUi) -> Unit = {},         // ← new
)
```

Lives on the callback bag (not as a top-level `PostCard` parameter like
`onImageClick`). Rationale: `QuotedPostUi` is self-contained — the callback
needs nothing the leaf doesn't already have. The image-click outlier exists
because the host has to curry the `post` into the leaf-level
`(Int) -> Unit`; that doesn't apply here.

```kotlin
// :designsystem/PostCard.EmbedSlot
is EmbedUi.Record -> {
    Spacer(Modifier.height(10.dp))
    PostCardQuotedPost(
        quotedPost = embed.quotedPost,
        onTap = { callbacks.onQuotedPostTap(embed.quotedPost) },
        quotedVideoEmbedSlot = quotedVideoEmbedSlot,
    )
}
is EmbedUi.RecordWithMedia -> {
    Spacer(Modifier.height(10.dp))
    PostCardRecordWithMediaEmbed(
        record = embed.record,
        media = embed.media,
        onQuotedPostTap = when (val r = embed.record) {
            is EmbedUi.Record -> { { callbacks.onQuotedPostTap(r.quotedPost) } }
            is EmbedUi.RecordUnavailable -> null
        },
        onExternalMediaTap = callbacks.onExternalEmbedTap,
        videoEmbedSlot = videoEmbedSlot,
        quotedVideoEmbedSlot = quotedVideoEmbedSlot,
    )
}
```

### Per-feature wiring

All four hosts route to `PostDetailRoute(uri = quotedPost.uri)` via
`LocalMainShellNavState.current.add(...)`. This mirrors the
just-shipped image-tap → MediaViewer effect plumbing (which uses the outer
Navigator) — but here the destination is in-shell, so it's MainShellNavState.

| Module | Event | Effect |
|---|---|---|
| `:feature:feed:impl` | `FeedEvent.OnQuotedPostTapped(uri: String)` | reuse `FeedEffect.NavigateToPostDetail(uri)` if its shape already matches; else add a sibling effect. |
| `:feature:profile:impl` | `ProfileEvent.QuotedPostTapped(uri: String)` | route via the same nav-effect channel Profile already uses for `PostTapped`. |
| `:feature:postdetail:impl` | `PostDetailEvent.OnQuotedPostTapped(uri: String)` | push another `PostDetailRoute(uri)` onto MainShellNavState. Recursion is bounded only by the back-stack — matches Bluesky Android. |
| `:feature:chats:impl` | `ChatEvent.QuotedPostTapped(uri: String)` | `ChatEffect.NavigateToPost(uri)` collected via `ObserveAsEvents`, calls `LocalMainShellNavState.current.add(PostDetailRoute(uri))`. New module dep: `implementation(project(":feature:postdetail:api"))`. |

The `PostCallbacks` instance constructed in each screen's
`remember { PostCallbacks(...) }` block wires `onQuotedPostTap = { qp ->
onEvent(FooEvent.QuotedPostTapped(qp.uri)) }`.

For the chats screen, `PostCardQuotedPost` is invoked directly (not via
`PostCard`), so the wiring goes on `MessageBubble`:
`PostCardQuotedPost(onTap = { onEvent(ChatEvent.QuotedPostTapped(quotedPost.uri)) }, ...)`.

### Why the ViewModel handles navigation rather than the screen calling
`LocalMainShellNavState` directly

Matches the existing convention (see CLAUDE.md "Tab-internal navigation also
flows through `UiEffect`"): ViewModels can't access `CompositionLocal`, and
keeping the MVI boundary clean means every navigation goes event → effect →
screen-side collector → nav-state mutation. Analytics, race-handling, and
tests all hang off the effect channel.

## Data flow

```
User taps inner quoted-card region on PostCard (in feed)
    └─ Surface.Modifier.clickable in PostCardQuotedPost fires
        └─ onTap lambda from PostCard.EmbedSlot
            └─ callbacks.onQuotedPostTap(quotedPost)
                └─ FeedScreen's PostCallbacks instance
                    └─ onEvent(FeedEvent.OnQuotedPostTapped(quotedPost.uri))
                        └─ FeedViewModel.handleEvent
                            └─ sendEffect(FeedEffect.NavigateToPostDetail(uri))
                                └─ FeedScreen.ObserveAsEvents
                                    └─ LocalMainShellNavState.current.add(PostDetailRoute(uri))
```

Chats and PostDetail follow the same chain with their own event/effect
names. Inside `PostCard`'s outer `Column.clickable`, Compose's gesture
system never receives the event because the inner clickable consumed it —
no parent `onTap` fires.

## Error handling

PostDetail's existing loader handles "not found" / "blocked" / network
failure on the destination URI. No new error path is introduced by this
change; the click just initiates navigation.

## Testing

Per nubecita-7z2 and nubecita-nn3.8:

### Unit (host VMs)

- `FeedViewModelTest`: `OnQuotedPostTapped(uri)` emits the nav effect with
  the same URI.
- `ProfileViewModelTest`: `QuotedPostTapped(uri)` emits the nav effect.
- `PostDetailViewModelTest`: `OnQuotedPostTapped(uri)` emits the nav effect
  (the destination is **another** PostDetail).
- `ChatViewModelTest`: `QuotedPostTapped(uri)` emits
  `NavigateToPost(uri)`.

### Screenshot (designsystem)

- Extend `PostCardQuotedPostScreenshotTest` with an `onTap`-supplied variant.
  Rendering should be byte-identical — clickable doesn't change static
  layout. The variant exists to guard against accidental Modifier-ordering
  regressions (e.g. someone wrapping inside vs outside the Surface and
  clipping the shape).
- Same for `PostCardRecordWithMediaEmbedScreenshotTest`.

### androidTest (designsystem PostCard fixture)

This is the load-bearing assertion that the click model works:

1. Render a `PostCard` with `EmbedUi.Record` and instrumented callbacks.
2. Tap inside the quoted-card region → `onQuotedPostTap` fires once,
   `onTap` does NOT fire.
3. Tap inside the outer body / avatar → `onTap` fires, `onQuotedPostTap`
   does NOT fire.
4. Repeat for `EmbedUi.RecordWithMedia`:
   - Tapping the quoted-card region → `onQuotedPostTap` fires, `onTap` does NOT.
   - Tapping the media region (images, external link) → `onQuotedPostTap`
     does NOT fire. Whether `onTap` (parent) fires depends on the media leaf:
     image gallery in this context has no `onImageClick` wired today (out of
     scope), so the gesture bubbles to the outer parent `onTap`; external
     link leaf consumes via its own `Modifier.clickable`. The test should
     pin **only the negative assertion on `onQuotedPostTap`** for the media
     branch — leaving parent-tap behavior to whatever the leaves already do.

Per the project's testing memory (`feedback_run_instrumented_label_on_androidtest_prs.md`):
the PR must get the `run-instrumented` label after opening.

## Risks / open questions

- **Exact event/effect names in Profile and PostDetail** — inferred from the
  same-shape `OnImageTapped`/`PostTapped` precedent. Will read the contracts
  when implementing; rename to match house style if needed.
- **`FeedEffect.NavigateToPostDetail(uri)` may already exist** — if so,
  reuse. If it takes a `PostUi` instead of a `String` URI, add a sibling
  effect for the quoted-post case rather than forcing the call site to
  fabricate a `PostUi`.
- **Recursive PostDetail back-stack depth** — pushing `PostDetailRoute`
  repeatedly is fine for the Navigation 3 backstack; chains like
  A→quotes→B→quotes→C stack as three back-able entries. Acceptable —
  Bluesky Android does the same.

## Refs

- `:designsystem/PostCardQuotedPost.kt`
- `:designsystem/PostCardRecordWithMediaEmbed.kt`
- `:designsystem/PostCard.kt` (the wrapping clickable)
- `:designsystem/PostCallbacks.kt`
- `feature/feed/impl/.../FeedNavigationModule.kt` — reference nav effect plumbing
- CLAUDE.md "Tab-internal navigation also flows through `UiEffect`"
- bd nubecita-7z2 description (designsystem + feed/profile/postdetail scope)
- bd nubecita-nn3.8 description (chats scope, new postdetail:api dep)
