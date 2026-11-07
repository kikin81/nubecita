# Post details and actions in the image gallery

Epic: `nubecita-ge3s`.

## Why

The gallery shows only the image. A reader who opens a photo from
Profile > Media loses all context and can't reach the post it came from. There
is also no way to like, repost or read comments, which the video feed and X's
gallery both offer.

## Goals

- A bottom overlay shows the author, the caption and an action row.
- Tapping the author opens their profile. Tapping the caption opens the post.
- Like, repost, bookmark and share work from the gallery.
- The reply button opens the comments sheet the video screens use.
- The overlay fades with the top controls.

## Non-goals (v1)

- A Follow button. A later improvement.
- An inline reply field. The comments sheet's pill opens the composer.
- Video in the gallery, and any change to `MediaViewerRoute`.

## Anti-goals

- Breaking the feed-to-gallery shared image transition. It depends on the
  gallery being an `@OuterShell` entry, so the gallery stays there.
- Unreadable text on bright photos.
- An overlay that gets in the way of pinch-zoom, paging or swipe-down.

## How it behaves

**Overlay.** Author row, caption and action row, over a gradient, inside the
existing `isChromeVisible` block. It fades in place with no slide, on a fast M3
effects token. Measured from X: about 160ms each way, and a gradient starting
about 55% down the screen that reaches roughly 35% black. X also has a solid
band under its reply field, which we don't need. Ours will likely end darker,
and is tuned against a bright image rather than copied.

**Caption.** Three lines at most, ellipsized. The tap opens the full post. A
post with no text hides the caption, and the author row is the way in.

**Opening the post.** If that post is already on top, as when the gallery was
opened from its detail screen, `MainShell`'s single-top add drops the route, so
no second copy is stacked. A quoted post's image opens that post.

**Loading.** The viewer already waits for the post before it shows images, so
the overlay arrives with them and nothing moves.

**Actions.** Reply, repost and like on `NubecitaOverlayStatButton`; bookmark
and share on `NubecitaOverlayIconButton`. Repost opens the existing sheet.

**Comments.** The reply button opens the shared sheet. The auto-fade pauses
while it is open. Back closes the sheet before the gallery. The sheet's comment
pill opens the composer above the gallery, so the reader returns to it.

## How it's built

These are the builder's defaults unless stated above.

- **Rename.** `VideoComments*` becomes `PostComments*` first, as its own PR
  (the user's request). No behaviour change. Screenshot baselines named after
  the previews move with it.
- **Post data.** The ViewModel already fetches the post with `getPost` and
  discards everything but the images. It keeps the post instead, so the route
  stays primitive.
- **Interactions.** The ViewModel delegates to `PostInteractionHandler`, as the
  video feed does. `rememberPostInteractions` reads `LocalMainShellNavState`,
  which throws outside `MainShell`, so it gains an `onNavigate` parameter that
  defaults to the shell's stack.
- **Composer.** `ComposerRoute` is registered on both shells, so the gallery
  pushes it on the outer stack, as `VideoPlayerScreen` does.
- **Profile and post detail.** These are `@MainShell` only. The gallery closes
  and the route opens on the inner stack through the existing `DeepLinkRouter`,
  which holds a route until `MainShell` collects it, via a new
  `Navigator.closeAndOpenInMainShell`.
- **Video player.** `VideoPlayerScreen` pushed `Profile` and `PostDetailRoute`
  onto the outer stack, where neither is registered, so it moves to the same
  call.

## Left to the builder

The gradient's exact stops, the row spacing, and how the overlay lays out on a
wide window.

## Testing

Unit tests for the ViewModel's post state, actions and the opened-from-post
rule, and for the pending route. Screenshot tests of the overlay on bright and
dark images, with and without a caption. A bench device pass: fade, like,
comments, Back order, and the shared transition in both directions. Bench needs
comments for the gallery's post.

## Delivery

One `gh stack` of four PRs: rename, navigation, overlay, comments sheet.
