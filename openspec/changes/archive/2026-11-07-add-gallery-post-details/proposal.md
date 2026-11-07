## Why

The gallery shows only the image, so a reader who opens a photo from a profile's Media tab loses the post's context and has no way to reach it. Likes, reposts and comments are also missing, though the video screens have them.

## What Changes

- The gallery shows the author, caption and an action row in a bottom overlay that fades with the top controls.
- Tapping the author opens their profile, and tapping the caption opens the post.
- Like, repost, bookmark and share work from the gallery.
- The reply button opens the comments sheet the video screens use.
- `VideoComments*` is renamed `PostComments*`.

Not in this change: a Follow button, an inline reply field, or any change to `MediaViewerRoute`.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `feature-mediaviewer`: adds the post details overlay, its actions and the comments sheet.

## Impact

`:feature:mediaviewer:impl`, `:core:post-interactions-ui`, `:core:common`, `:designsystem`, `:feature:videos:impl`, `:feature:videoplayer:impl`, `:benchmark`, and `MainShell` in `:app`.
