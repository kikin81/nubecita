package net.kikin.nubecita.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether inline animated GIFs may animate on their own.
 *
 * An ambient rather than a `PostCard` parameter: the GIF embed is an internal
 * leaf of `PostCard`, so a parameter would have to be threaded through every
 * post-rendering surface in the app (feed, post detail, profile, search,
 * quoted posts) to reach one leaf that every one of them treats identically.
 *
 * `staticCompositionLocalOf`, not `compositionLocalOf`: the value changes only
 * when the user changes a setting, so paying a whole-subtree invalidation then
 * is cheaper than tracking reads on every post card forever.
 *
 * Defaults to `true` so previews, screenshot fixtures and any surface that
 * never provides it keep describing the ordinary animating feed.
 *
 * Provided once in `:app`'s `MainShell` from `AutoplayPolicy.gifAutoplayEnabled`.
 *
 * Registered in `compose_allowed_composition_locals` in `.editorconfig`, like
 * every other local in the app.
 */
val LocalGifAutoplayEnabled = staticCompositionLocalOf { true }
